package okay.mutual.node

import okay.effects.json.Json
import okay.mutual.chain.*
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.Durable
import okay.serverless.cloud.{Actor, InActor, Request, Response}

/**
 * THE NODE'S DIRECTORY (specs/protocol.md "A node"): one object, `NODE`/`node` — who its members are, every
 * account's head, and what follows from them: the node's signed head.
 *
 *   dir/member/<id>     a member as the others see it: its id, name, kind, when it joined
 *   dir/head/<id>       the hash of the account's last record
 *   dir/number          how many times the heads changed: the head's number
 *   dir/community       the id of the community's own account — its holders that may manage govern the node
 *   dir/command/<hash>  a command taken (never twice)
 *
 * THE HEAD: `{v, node, number, at, root, count}` signed by the node, `root` the Merkle root over all heads. A wallet
 * asks a proof that its chain's head is under the root. Two heads of one number with different roots prove the node
 * showed two histories. Only the node's app calls it (`/_dir/…`)
 */
object Directory extends Actor:
  val namespace = "NODE"
  val name = "node"
  private val scope = "dir"
  private val members = s"$scope/member/"
  private val heads = s"$scope/head/"
  private val number = s"$scope/number"
  private val community = s"$scope/community"

  type Live[X] = (InActor + Signer)[X]
  private type Step[X] = (Live + Durable)[X]
  private def step[A](p: A ! Step): A ! Live = Durable.step[Step, Live, A](scope)(p)

  def fetch(n: String, r: Request): Response ! InActor = SignerHere.run(NodeEnv.read[Live].flatMap(answer(_, r)))

  private def allHeads: Map[String, String] ! Live =
    Http.all[Live](heads).map(_.map((k, v) => k.stripPrefix(heads) -> v.string).toMap)

  /** the head as it is now, signed: its text and the signature */
  private def head(env: NodeEnv): (Signed, Map[String, String]) ! Live =
    allHeads.flatMap(hs => Http.read[Live](number).flatMap(n => Clock.now[Live].flatMap { now =>
      val text = Json.print(Json.obj("v" -> Json.num(Record.version), "node" -> Json.str(env.pub),
        "number" -> Json.num(n.fold(0L)(_.long)), "at" -> Json.num(now), "root" -> Json.str(Merkle.root(hs)),
        "count" -> Json.num(hs.size)))
      Signer.sign[Live](env.secret, text).map(sig => (Signed(text, Vector(env.pub -> sig)), hs))
    }))

  private def answer(env: NodeEnv, r: Request): Response ! Live = (r.method, r.path) match
    case ("POST", "/_dir/head") =>
      val j = Http.body(r)
      step(Durable.get[Json, Step](number).flatMap(n => Durable.put[Json, Step](heads + j("account").string,
        j("head")).andThen(Durable.put[Json, Step](number, Json.num(n.fold(0L)(_.long) + 1)))))
        .as(Http.json(Json.obj()))
    case ("GET", "/_dir/head") => head(env).map((signed, _) => Http.json(Signed.json(signed)))
    case ("GET", "/_dir/proof") =>
      val account = r.param("account").getOrElse("")
      head(env).map((signed, hs) => Merkle.proof(hs, account) match
        case None => Http.refused("unknown", 404)
        case Some(proof) => Http.json(Json.obj("head" -> Signed.json(signed), "account" -> Json.str(account),
            "accountHead" -> Json.str(hs(account)), "proof" -> Json.Arr(proof.map((hash, left) =>
              Json.obj("hash" -> Json.str(hash), "left" -> Json.bool(left))))))
      )
    case ("POST", "/_dir/member") =>
      val j = Http.body(r)
      step(Durable.put[Json, Step](members + j("id").string, j)).as(Http.json(Json.obj()))
    case ("GET", "/_dir/members") => Http.all[Live](members).map(ms => Http.json(Json.obj("members" ->
        Json.Arr(ms.map(_._2).sortBy(m => m("name").string.toLowerCase)))))
    case ("GET", "/_dir/community") => Http.read[Live](community).map(c => Http.json(Json.obj("community" ->
        c.getOrElse(Json.Null))))
    // the community's account named — once
    case ("POST", "/_dir/community") => step(Durable.get[Json, Step](community).flatMap {
        case Some(_) => pure(false)
        case None => Durable.put[Json, Step](community, Http.body(r)("id")).as(true)
      }).map(set => if set then Http.json(Json.obj()) else Http.refused("set", 409))
    // a command's hash kept: false when it was there
    case ("POST", "/_dir/command") =>
      val key = s"$scope/command/${Http.body(r)("hash").string}"
      step(Durable.get[Json, Step](key).flatMap {
        case Some(_) => pure(false)
        case None => Durable.put[Json, Step](key, Json.bool(true)).as(true)
      }).map(fresh => if fresh then Http.json(Json.obj()) else Http.refused("used", 409))
    case _ => pure(Response.notFound())
