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
 *   dir/recovery/<id>   one who asks back an account whose devices are lost: the new device's key, when
 *   dir/listing/<id>    an offer or a want of a member: what, for how much, what share of it in units
 *   dir/card/<id>       a shop's stamp card: its title, how many stamps make a reward, the reward
 *   dir/stamps/<shop>/<customer>  a customer's card at a shop: the stamps on it, the rewards taken
 *   dir/elect/<key>/<member>  a member's word that <key> should manage the community (its managers' keys lost)
 *   dir/rules           the community's rules: the text its members accept to join, and its hash
 *   dir/holders/<id>    the accounts that hold the account <id> (a family's, a team's): their ids
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
  private val recoveries = s"$scope/recovery/"
  private val listings = s"$scope/listing/"
  private val holders = s"$scope/holders/"
  private val cards = s"$scope/card/"
  private val stamps = s"$scope/stamps/"
  private val elect = s"$scope/elect/"

  type Live[X] = (InActor + Signer)[X]
  private type Step[X] = (Live + Durable)[X]
  private def step[A](p: A ! Step): A ! Live = Durable.step[Step, Live, A](scope)(p)

  def fetch(n: String, r: Request): Response ! InActor = SignerHere.run(
    if r.method == "POST" && r.path == "/_dir/env" then created(r)
    else if r.method == "POST" && r.path == "/_dir/import" then imported(r)
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

  /** A COMMUNITY BROUGHT FROM A FILE: what its directory kept, and the node as it is here — once, where nothing is */
  private def imported(r: Request): Response ! Live = (NodeEnv.read(Http.body(r)("env")),
    Http.body(r).field("entries")) match
    case (Some(env), Some(entries: Json.Obj)) =>
      val kept = entries.record.filter((k, _) => k.startsWith(s"$scope/") && k != envKey &&
        !k.startsWith(s"$scope/command/"))
      step(Durable.get[Json, Step](envKey).flatMap {
        case Some(_) => pure(false)
        case None => kept.foldLeft(unit[Step])((acc, kv) => acc.andThen(Durable.put[Json, Step](kv._1, kv._2)))
            .andThen(Durable.put[Json, Step](envKey, NodeEnv.write(env))).as(true)
      }).map(made => if made then Http.json(Json.obj()) else Http.refused("taken", 409))
    case _ => pure(Http.refused("body"))

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
    // everything the directory keeps for a community's file: not the node itself, not the commands taken
    case ("GET", "/_dir/export") => Http.all[Live](s"$scope/").map(all => Http.json(Json.Obj(all.filter((k, _) =>
        k != envKey && !k.startsWith(s"$scope/command/")))))
    // the community moved: from now on this node changes nothing and says where it went
    case ("POST", "/_dir/moved") =>
      val to = scala.util.Try(Http.body(r)("address").string).getOrElse("")
      step(Durable.put[Json, Step](envKey, NodeEnv.write(env.copy(moved = to)))).as(Http.json(Json.obj()))
    // the community's policy changed: what it allows from now on — never its key, its place, its unit
    case ("POST", "/_dir/policy") => NodeEnv.changed(env, Http.body(r)) match
        case None => pure(Http.refused("policy"))
        case Some(next) => step(Durable.put[Json, Step](envKey, NodeEnv.write(next))).as(Http.json(Json.Obj(
            NodeEnv.policy(next))))
    case ("POST", "/_dir/head") =>
      val j = Http.body(r)
      val held: Unit ! Step = j.field("holders") match
        case Some(Json.Arr(hs)) if hs.isEmpty => Durable.delete[Step](holders + j("account").string)
        case Some(hs) => Durable.put[Json, Step](holders + j("account").string, hs)
        case None => unit[Step]
      step(Durable.get[Json, Step](number).flatMap(n => Durable.put[Json, Step](heads + j("account").string,
        j("head")).andThen(Durable.put[Json, Step](number, Json.num(n.fold(0L)(_.long) + 1))).andThen(held)))
        .as(Http.json(Json.obj()))
    // the accounts an account holds
    case ("GET", "/_dir/held") =>
      val by = Json.str(r.param("by").getOrElse(""))
      Http.all[Live](holders).map(all => Http.json(Json.obj("accounts" -> Json.Arr(all.collect {
        case (k, Json.Arr(hs)) if hs.contains(by) => Json.str(k.stripPrefix(holders)) }))))
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
    // ---- the board (specs/protocol.md "The board"): members' offers and wants, beside the chains
    case ("GET", "/_dir/listings") => Http.all[Live](listings).map(ls => Http.json(Json.obj("listings" ->
        Json.Arr(ls.map(_._2).sortBy(l => -l("at").long)))))
    // kept — while its member has room for one more (`Directory.own`) and the board for it (`Directory.board`)
    case ("POST", "/_dir/listing") =>
      val j = Http.body(r)
      step(Durable.keys[Step](listings).flatMap(ks => ks.foldLeft(pure[Int, Step](0))((acc, k) => acc.flatMap(n =>
        Durable.get[Json, Step](k).map(l => if l.exists(_("account") == j("account")) then n + 1 else n))).flatMap(
        mine => if mine >= own || ks.size >= board then pure(false)
          else Durable.put[Json, Step](listings + j("id").string, j).as(true))))
        .map(kept => if kept then Http.json(j) else Http.refused("full", 429))
    // taken off by its own member
    case ("POST", "/_dir/listing/remove") =>
      val j = Http.body(r)
      step(Durable.get[Json, Step](listings + j("id").string).flatMap {
        case Some(l) if l("account") == j("account") => Durable.delete[Step](listings + j("id").string).as(true)
        case _ => pure(false)
      }).map(gone => if gone then Http.json(Json.obj()) else Http.refused("unknown", 404))
    // ---- stamp cards (specs/protocol.md "Stamp cards"): beside the chains — a stamp is no unit
    // the shops' cards, and with `of` that customer's stamps at each
    case ("GET", "/_dir/cards") =>
      val of = r.param("of").getOrElse("")
      Http.all[Live](cards).flatMap(cs => (if of.isEmpty then pure[Vector[(String, Json)], Live](Vector.empty)
        else Http.all[Live](stamps).map(_.filter(_._1.endsWith("/" + of)))).map(mine => Http.json(Json.obj(
        "cards" -> Json.Arr(cs.map(_._2).sortBy(_("title").string.toLowerCase)),
        "stamps" -> Json.Arr(mine.map((k, v) => Json.Obj(("shop" -> Json.str(k.stripPrefix(stamps).takeWhile(
          _ != '/'))) +: v.record)))))))
    case ("POST", "/_dir/card") =>
      val j = Http.body(r)
      step(Durable.put[Json, Step](cards + j("account").string, j)).as(Http.json(j))
    case ("POST", "/_dir/card/remove") =>
      step(Durable.delete[Step](cards + Http.body(r)("account").string)).as(Http.json(Json.obj()))
    // a stamp put on a customer's card, or a reward taken off it: against the shop's card as it is now
    case ("POST", "/_dir/stamp") =>
      val j = Http.body(r)
      val (shop, customer, kind) = (j("shop").string, j("customer").string, j("kind").string)
      val at = stamps + shop + "/" + customer
      step(Durable.get[Json, Step](cards + shop).flatMap {
        case None => pure(Left("no card" -> 404))
        case Some(card) => Durable.get[Json, Step](at).flatMap { kept =>
            val (have, used) = kept.fold((0L, 0L))(k => (k("stamps").long, k("used").long))
            val need = card("need").long
            val next = if kind == "redeem" then (have - need, used + 1) else (have + 1, used)
            if kind == "redeem" && have < need then pure(Left("short" -> 409))
            else if next._1 > need * Directory.cardsKept then pure(Left("full" -> 409))
            else Durable.put[Json, Step](at, Json.obj("stamps" -> Json.num(next._1), "used" -> Json.num(next._2)))
              .as(Right(Json.obj("kind" -> Json.str(kind), "shop" -> Json.str(shop), "title" -> card("title"),
                "reward" -> card("reward"), "need" -> Json.num(need), "stamps" -> Json.num(next._1),
                "used" -> Json.num(next._2))))
          }
      }).map(_.fold((why, status) => Http.refused(why, status), Http.json(_)))
    // ---- a manager elected by the members (specs/protocol.md "A community outlives its founder's phone")
    // a member's word for a key (one a member: its earlier word for another key is taken off); who says what now
    case ("POST", "/_dir/elect") =>
      val j = Http.body(r)
      val (key, member) = (j("key").string, j("member").string)
      step(Durable.keys[Step](elect).flatMap(ks => ks.filter(_.endsWith("/" + member)).foldLeft(unit[Step])(
        (acc, k) => acc.andThen(Durable.delete[Step](k))).andThen(Durable.put[Json, Step](
        elect + key + "/" + member, Json.bool(true))))).as(Http.json(Json.obj()))
    case ("GET", "/_dir/election") => Http.all[Live](elect).map(all => Http.json(Json.obj("said" -> Json.Arr(
        all.map((k, _) => k.stripPrefix(elect)).map(k => Json.obj("key" -> Json.str(k.takeWhile(_ != '/')),
          "member" -> Json.str(k.dropWhile(_ != '/').drop(1))))))))
    case ("POST", "/_dir/election/clear") => step(Durable.keys[Step](elect).flatMap(ks =>
        ks.foldLeft(unit[Step])((acc, k) => acc.andThen(Durable.delete[Step](k))))).as(Http.json(Json.obj()))
    // ---- recovery asked (specs/protocol.md "Keys"): kept until the community confirms or drops it
    case ("POST", "/_dir/recovery") =>
      val j = Http.body(r)
      step(Durable.put[Json, Step](recoveries + j("account").string, j)).as(Http.json(Json.obj()))
    case ("GET", "/_dir/recoveries") => Http.all[Live](recoveries).map(rs => Http.json(Json.obj("recoveries" ->
        Json.Arr(rs.map(_._2).sortBy(_("at").long)))))
    case ("GET", "/_dir/recovery") => Http.read[Live](recoveries + r.param("account").getOrElse("")).map(
        _.fold(Http.refused("unknown", 404))(Http.json(_)))
    case ("POST", "/_dir/recovery/done") =>
      step(Durable.delete[Step](recoveries + Http.body(r)("account").string)).as(Http.json(Json.obj()))
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

  /** how many full cards a customer may hold unspent at a shop */
  val cardsKept = 5
  /** how many entries a member may have on the board, and the board in all */
  val own = 20
  val board = 2000
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
    case ("POST", "/_hub/import") => HubEnv.read[Live].flatMap(imported(_, Http.body(r)))
    case _ => pure(Response.notFound()))

  /** the policy a node is asked to be made with: what is not said is the default — a person's line as a
   * business's where people have lines */
  private def policy(place: String, name: String, unit: String, open: Boolean, credit: Long, ceiling: Long, j: Json)
    : Option[NodeEnv] = NodeEnv.changed(NodeEnv(place, "", "", unit.toUpperCase, name, open, credit, ceiling, "",
      personalCredit = credit), Json.Obj(j.record.filter(f => Set("only", "personalCredit", "personalMax", "after",
      "shared", "wait")(f._1))))

  /** a place taken for a node: free, within what an address may make a day and the hub in all; none, or why not */
  private def reserve(hub: HubEnv, place: String, name: String, unit: String, open: Boolean, who: String)
    : Option[String] ! Live = Clock.now[Live].flatMap { now =>
      val made = s"$scope/made/$who/${now / 86400000L}"
      step(Durable.get[Json, Step](nodes + place).flatMap {
        case Some(_) => pure(Some("taken"))
        case None => Durable.keys[Step](nodes).flatMap(all => Durable.get[Json, Step](made).flatMap { n =>
            if all.size >= hub.max then pure(Some("full"))
            else if n.fold(0L)(_.long) >= hub.perDay then pure(Some("limit"))
            else Durable.put[Json, Step](nodes + place, Json.obj("slug" -> Json.str(place), "name" -> Json.str(name),
              "unit" -> Json.str(unit.toUpperCase), "join" -> Json.str(if open then "open" else "admitted"),
              "at" -> Json.num(now))).andThen(Durable.put[Json, Step](made, Json.num(n.fold(0L)(_.long) + 1)))
              .as(None)
          })
      })
    }

  /** the chains of a community's file, each folded again from its first record and checked by its signatures —
   * an act for a holder account against that account's own chain in the file: the first account that does not
   * make the state the file says, or none */
  private def unsound(env: NodeEnv, accounts: Vector[(String, Map[String, Json])]): Option[String] ! Live =
    val byId = accounts.toMap
    def records(m: Map[String, Json]): Vector[Signed] =
      m.toVector.filter(_._1.startsWith("acct/rec/")).sortBy(_._1).flatMap(x => Signed.parse(x._2))
    def held(a: Acting): Boolean ! Live = byId.get(a.account).fold(pure(false))(m => Check.holds[Live](records(m), a))
    accounts.foldLeft(pure[Option[String], Live](None))((acc, am) => acc.flatMap {
      case found @ Some(_) => pure(found)
      case None => Check.chain[Live](records(am._2), held).map {
          // the state the file says, whole, is the one the chain makes — not its head alone
          case Right(s) if s.id == am._1 && s.node == env.pub &&
            am._2.get("acct/state").flatMap(StateJson.read).contains(s) => None
          case _ => Some(am._1)
        }
    })

  /**
   * A COMMUNITY BROUGHT FROM ITS FILE (specs/protocol.md "A community's data out and in"): its node as it was —
   * its key pair, its policy (within what this hub allows) —, at a place of this hub; every chain checked first;
   * refused where any of its accounts is here already (the community has not left the hub it is at); then its
   * accounts, then its directory
   */
  private def imported(hub: HubEnv, j: Json): Response ! Live =
    val file = j.field("file").getOrElse(Json.Null)
    val place = scala.util.Try(j("slug").string.trim.toLowerCase).getOrElse("")
    val accounts = scala.util.Try(file("accounts").record.map((id, m) => id -> m.record.toMap)).getOrElse(Vector.empty)
    (scala.util.Try(file("kind").string == "okay-mutual-node" && file("v").long == Record.version).getOrElse(false),
      NodeEnv.read(file("env")).map(_.copy(slug = place, moved = "")), file.field("directory")) match
      case (true, Some(env), Some(directory: Json.Obj)) if slug(place) && accounts.nonEmpty &&
        accounts.forall(_._1.matches("[0-9a-f]{64}")) =>
        env.allowed(hub) match
          case Some(why) => pure(Http.refused("policy", 409, "detail" -> Json.str(why)))
          case None => unsound(env, accounts).flatMap {
              case Some(id) => pure(Http.refused("chain", 409, "detail" -> Json.str(id)))
              case None => accounts.foldLeft(pure[Boolean, Live](false))((acc, am) => acc.flatMap(t =>
                  if t then pure(true) else okay.serverless.cloud.Objects.call[Live](AccountObject.namespace, am._1,
                    Request.get("/_acct/taken")).map(r => scala.util.Try(Http.answer(r)("taken").boolean)
                    .getOrElse(true)))).flatMap { taken =>
                  if taken then pure(Http.refused("here", 409))
                  else reserve(hub, place, env.name, env.unit, env.open, j("who").string).flatMap {
                    case Some(why) => pure(Http.refused(why, if why == "taken" then 409 else 429))
                    case None => accounts.foldLeft(unit[Live])((acc, am) => acc.flatMap(_ =>
                        okay.serverless.cloud.Objects.call[Live](AccountObject.namespace, am._1, Http.post(
                          "/_acct/import", Json.obj("env" -> NodeEnv.write(env), "entries" -> Json.Obj(am._2
                            .toVector))))).as(())).flatMap(_ => okay.serverless.cloud.Objects.call[Live](
                        Directory.namespace, place, Http.post("/_dir/import", Json.obj("env" -> NodeEnv.write(env),
                          "entries" -> directory)))).map(made =>
                        if made.status != 200 then Http.refused("taken", 409)
                        else Http.json(Json.obj("slug" -> Json.str(place), "key" -> Json.str(env.pub),
                          "token" -> Json.str(env.setup))))
                  }
                }
            }
      case _ => pure(Http.refused("file"))

  private def create(hub: HubEnv, j: Json): Response ! Live =
    scala.util.Try((j("slug").string.trim.toLowerCase, j("name").string.replaceAll("\\s+", " ").trim.take(80),
      j("unit").string.trim.take(8), j("join").string == "open", j("credit").long, j("ceiling").long,
      j("who").string)).toOption match
      case Some((place, name, unit, open, credit, ceiling, who)) if slug(place) && name.nonEmpty &&
        unit.matches("[A-Za-z]{2,8}") && policy(place, name, unit, open, credit, ceiling, j).exists(
          _.allowed(hub).isEmpty) =>
        // its place and the limits taken in one step; then the node made
        reserve(hub, place, name, unit, open, who).flatMap {
            case Some(why) => pure(Http.refused(why, if why == "taken" then 409 else 429))
            case None => Signer.generate[Live].flatMap { pair =>
                val token = Sha256.hex(pair.secret + ":setup").take(32)
                val env = policy(place, name, unit, open, credit, ceiling, j).get.copy(pub = pair.pub,
                  secret = pair.secret, setup = token)
                val made = okay.serverless.cloud.Objects.call[Live](Directory.namespace, place,
                  Http.post("/_dir/env", NodeEnv.write(env)))
                // its rules, when its maker wrote some
                made.flatMap(kept => if kept.status != 200 then pure(Http.refused("taken", 409)) else
                  j.field("rules").collect { case Json.Str(t) if t.trim.nonEmpty => t }.fold(
                    pure[Unit, Live](()))(text =>
                    okay.serverless.cloud.Objects.call[Live](Directory.namespace, place, Http.post("/_dir/rules",
                      Json.obj("text" -> Json.str(text)))).as(())).as(Http.json(Json.obj("slug" -> Json.str(place),
                    "key" -> Json.str(pair.pub), "token" -> Json.str(token)))))
              }
          }
      case _ => pure(Http.refused("body"))
