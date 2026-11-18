package okay.mutual.node

import okay.effects.json.Json
import okay.mutual.chain.*
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.Durable
import okay.serverless.cloud.{Actor, InActor, Request, Response}

/**
 * A NODE'S DIRECTORY (specs/protocol.md "A node"): one object a node, `NODE`/`<slug>` — what the node is, who its
 * members are, every account's head, and what follows from them: the node's signed head.
 *
 *   dir/env             the node itself: its key pair, its name, its unit, how one joins (set once, at its creation)
 *   dir/member/<id>     a member as the others see it: its id, name, kind, when it joined
 *   dir/request/<id>    one who asked to join where joining is not open: the signed first record, the name
 *   dir/head/<id>       the hash of the account's last record
 *   dir/number          how many times the heads changed: the head's number
 *   dir/line/<peer key> the account this node keeps for another node: its id, the peer's address and name
 *   dir/rules           the community's rules: the text its members accept to join, and its hash
 *   dir/community       the id of the community's own account — its holders that may manage govern the node
 *   dir/command/<hash>  a command taken (never twice)
 *
 * THE HEAD: `{v, node, number, at, root, count}` signed by the node, `root` the Merkle root over all heads. A wallet
 * asks a proof that its chain's head is under the root. Two heads of one number with different roots prove the node
 * showed two histories. Only the node's app and its accounts call it (`/_dir/…`)
 */
object Directory extends Actor:
  val namespace = "NODE"
  private val scope = "dir"
  private val envKey = s"$scope/env"
  private val members = s"$scope/member/"
  private val requests = s"$scope/request/"
  private val heads = s"$scope/head/"
  private val number = s"$scope/number"
  private val community = s"$scope/community"
  private val lines = s"$scope/line/"
  private val rules = s"$scope/rules"

  type Live[X] = (InActor + Signer)[X]
  private type Step[X] = (Live + Durable)[X]
  private def step[A](p: A ! Step): A ! Live = Durable.step[Step, Live, A](scope)(p)

  def fetch(n: String, r: Request): Response ! InActor = SignerHere.run(
    if r.method == "POST" && r.path == "/_dir/env" then created(r)
    else Http.read[Live](envKey).map(_.flatMap(NodeEnv.read)).flatMap {
      case None => pure(Http.refused("unknown", 404))
      case Some(env) => answer(env, r)
    })

  /** the node made: what it is, kept — once */
  private def created(r: Request): Response ! Live = NodeEnv.read(Http.body(r)) match
    case None => pure(Http.refused("env"))
    case Some(env) => step(Durable.get[Json, Step](envKey).flatMap {
        case Some(_) => pure(false)
        case None => Durable.put[Json, Step](envKey, NodeEnv.write(env)).as(true)
      }).map(made => if made then Http.json(Json.obj()) else Http.refused("taken", 409))

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
    case ("GET", "/_dir/env") => pure(Http.json(NodeEnv.write(env)))
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
    // a member listed: its request, if it asked, is over
    case ("POST", "/_dir/member") =>
      val j = Http.body(r)
      step(Durable.put[Json, Step](members + j("id").string, j).andThen(Durable.delete[Step](requests +
        j("id").string))).as(Http.json(Json.obj()))
    case ("GET", "/_dir/members") => Http.all[Live](members).map(ms => Http.json(Json.obj("members" ->
        Json.Arr(ms.map(_._2).sortBy(m => m("name").string.toLowerCase)))))
    // one who asks to join: kept until admitted or rejected (at most `Directory.waiting` at a time)
    case ("POST", "/_dir/request") =>
      val j = Http.body(r)
      step(Durable.keys[Step](requests).flatMap(ks =>
        if ks.size >= waiting && !ks.contains(requests + j("id").string) then pure(false)
        else Durable.put[Json, Step](requests + j("id").string, j).as(true)))
        .map(kept => if kept then Http.json(Json.obj()) else Http.refused("full", 429))
    case ("GET", "/_dir/requests") => Http.all[Live](requests).map(rs => Http.json(Json.obj("requests" ->
        Json.Arr(rs.map(_._2).sortBy(_("at").long)))))
    case ("GET", "/_dir/request") => Http.read[Live](requests + r.param("id").getOrElse("")).map(
        _.fold(Http.refused("unknown", 404))(Http.json(_)))
    case ("POST", "/_dir/reject") =>
      step(Durable.delete[Step](requests + Http.body(r)("id").string)).as(Http.json(Json.obj()))
    // ---- the community's rules (docs/legal.md): what a newcomer accepts, named by its hash
    case ("GET", "/_dir/rules") => Http.read[Live](rules).map(r => Http.json(r.getOrElse(Json.obj(
        "text" -> Json.str(""), "hash" -> Json.str("")))))
    case ("POST", "/_dir/rules") =>
      val text = scala.util.Try(Http.body(r)("text").string).getOrElse("").trim.take(Directory.longest)
      step(Durable.put[Json, Step](rules, Json.obj("text" -> Json.str(text),
        "hash" -> Json.str(if text.isEmpty then "" else Sha256.hex(text))))).as(Http.json(Json.obj()))
    // ---- trust lines (specs/protocol.md "Trust lines between nodes")
    case ("GET", "/_dir/lines") => Http.all[Live](lines).map(ls => Http.json(Json.obj("lines" ->
        Json.Arr(ls.map(_._2).sortBy(_("name").string.toLowerCase)))))
    case ("GET", "/_dir/line") => Http.read[Live](lines + r.param("node").getOrElse("")).map(
        _.fold(Http.refused("unknown", 404))(Http.json(_)))
    case ("POST", "/_dir/line") =>
      val j = Http.body(r)
      step(Durable.put[Json, Step](lines + j("node").string, j)).as(Http.json(Json.obj()))
    // the opening record of this node's own account at the node that asks, signed: nothing but this node's word
    // that the key is its own — the account is the asker's to open, and only this node's key will debit it
    case ("POST", "/_dir/line/sign") => scala.util.Try(Http.body(r)("node").string).toOption.filter(_.nonEmpty) match
        case None => pure(Http.refused("body"))
        case Some(asker) => Check.signed[Live](Peers.opening(asker, env.pub, env.unit), Pair(env.pub, env.secret))
            .map(signed => Http.json(Signed.json(signed)))
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

  /** how long a community's rules may be */
  val longest = 8000
  /** how many may wait to be admitted at a time */
  val waiting = 200

/**
 * THE HUB (specs/protocol.md "A node", "Anyone has a node"): one object a deployment, `HUB`/`hub` — the nodes made
 * here. A node is made by a call: its key pair generated, its setup token derived from the secret, what it is given
 * to its directory; the answer is its place, its key and the token — said once, to whoever made it.
 *
 *   hub/node/<slug>         a community as the hub lists it: its slug, name, unit, how one joins, when it was made
 *   hub/made/<who>/<day>    how many nodes an address made in a day
 */
object Hub extends Actor:
  val namespace = "HUB"
  val name = "hub"
  private val scope = "hub"
  private val nodes = s"$scope/node/"
  /** a node's place: a lowercase letter or digit, then letters, digits and hyphens; 3 to 40 */
  def slug(s: String): Boolean = s.matches("[a-z0-9][a-z0-9-]{1,38}[a-z0-9]") && !reserved(s)
  val reserved: Set[String] = Set("api", "hub", "node", "nodes", "wallet", "admin", "www", "new", "okay-mutual")

  type Live[X] = (InActor + Signer)[X]
  private type Step[X] = (Live + Durable)[X]
  private def step[A](p: A ! Step): A ! Live = Durable.step[Step, Live, A](scope)(p)

  def fetch(n: String, r: Request): Response ! InActor = SignerHere.run((r.method, r.path) match
    case ("GET", "/_hub/nodes") => Http.all[Live](nodes).map(ns => Http.json(Json.obj("nodes" ->
        Json.Arr(ns.map(_._2).sortBy(_("name").string.toLowerCase)))))
    case ("POST", "/_hub/create") => HubEnv.read[Live].flatMap(create(_, Http.body(r)))
    case _ => pure(Response.notFound()))

  private def create(hub: HubEnv, j: Json): Response ! Live =
    scala.util.Try((j("slug").string.trim.toLowerCase, j("name").string.replaceAll("\\s+", " ").trim.take(80),
      j("unit").string.trim.take(8), j("join").string == "open", j("credit").long, j("ceiling").long,
      j("who").string)).toOption match
      case Some((place, name, unit, open, credit, ceiling, who)) if slug(place) && name.nonEmpty &&
        j.field("only").forall(o => Set[Json](Json.str("business"), Json.str("earned"), Json.str(""))(o)) &&
        unit.matches("[A-Za-z]{2,8}") && credit >= 0 && ceiling >= 0 =>
        Clock.now[Live].flatMap { now =>
          val made = s"$scope/made/$who/${now / 86400000L}"
          // its place and the limits taken in one step; then the node made
          step(Durable.get[Json, Step](nodes + place).flatMap {
            case Some(_) => pure(Some("taken"))
            case None => Durable.keys[Step](nodes).flatMap(all => Durable.get[Json, Step](made).flatMap { n =>
                if all.size >= hub.max then pure(Some("full"))
                else if n.fold(0L)(_.long) >= hub.perDay then pure(Some("limit"))
                else Durable.put[Json, Step](nodes + place, Json.obj("slug" -> Json.str(place),
                  "name" -> Json.str(name), "unit" -> Json.str(unit.toUpperCase),
                  "join" -> Json.str(if open then "open" else "admitted"), "at" -> Json.num(now)))
                  .andThen(Durable.put[Json, Step](made, Json.num(n.fold(0L)(_.long) + 1))).as(None)
              })
          }).flatMap {
            case Some(why) => pure(Http.refused(why, if why == "taken" then 409 else 429))
            case None => Signer.generate[Live].flatMap { pair =>
                val token = Sha256.hex(pair.secret + ":setup").take(32)
                val env = NodeEnv(place, pair.pub, pair.secret, unit.toUpperCase, name, open, credit, ceiling, token,
                  j.field("only").collect { case Json.Str(o) => o }.getOrElse(""))
                val made = okay.serverless.cloud.Objects.call[Live](Directory.namespace, place,
                  Http.post("/_dir/env", NodeEnv.write(env)))
                // its rules, when its maker wrote some
                made.flatMap(kept => if kept.status != 200 then pure(Http.refused("taken", 409)) else
                  j.field("rules").collect { case Json.Str(t) if t.trim.nonEmpty => t }.fold(pure[Unit, Live](()))(text =>
                    okay.serverless.cloud.Objects.call[Live](Directory.namespace, place, Http.post("/_dir/rules",
                      Json.obj("text" -> Json.str(text)))).as(())).as(Http.json(Json.obj("slug" -> Json.str(place),
                    "key" -> Json.str(pair.pub), "token" -> Json.str(token)))))
              }
          }
        }
      case _ => pure(Http.refused("body"))
