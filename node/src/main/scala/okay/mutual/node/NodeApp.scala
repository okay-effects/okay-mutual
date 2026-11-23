package okay.mutual.node

import okay.effects.json.{Descent, Json}
import okay.mutual.chain.*
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.cloud.{Actor, App, Cloud, Objects, Request, Response}

/**
 * A HUB OF NODES (specs/protocol.md "A node", "Anyone has a node"): a deployment — a Cloudflare Worker, one JVM
 * process — holding as many communities' nodes as people make. What the hub answers:
 *
 *   GET  /.well-known/okay-mutual-hub.json  what it is: whether anyone may make a node here
 *   GET  /api/nodes                         the communities here: slug, name, unit, how one joins
 *   GET  /api/backup                        every community of the hub as its file (`x-hub-token`): the operator's
 *   POST /api/nodes                         {slug, import: <a community's file>}: a community brought here, as it
 *                                           was — its key, its chains (each checked), its policy (within the hub's)
 *   POST /api/nodes                         {slug, name, unit, join, credit, ceiling}: a node made — its address
 *                                           (`/n/<slug>`), its key and its setup token, said once (`x-hub-token`
 *                                           where making is not open)
 *
 * and each node, under `/n/<slug>`, to any wallet and any other node:
 *
 *   GET  /.well-known/okay-mutual.json      what it is: its key, its unit, its name, whether anyone may join
 *   GET  /api/members                       its members: id, name, kind
 *   GET  /api/head                          its signed head
 *   GET  /api/community                     the id of the community's own account (null: not set up)
 *   GET  /api/rules                         the community's rules — the text a newcomer accepts — and their hash
 *   POST /api/accounts                      {record, name, rules}: an account opened by its first, signed record
 *                                           (`rules`: the hash of the rules accepted — the ones in force) — or,
 *                                           where joining is not open, kept as a request (202)
 *                                           an account a family or a team shares (`family`, `team`) is opened
 *                                           by a member's ACCOUNT, at once, with no credit line of its own
 *   GET  /api/held?by=<id>                  the accounts that account holds
 *   GET  /api/requests                      who waits to be admitted: id, name, kind
 *   GET  /api/accounts/<id>                 the account as its chain makes it
 *   GET  /api/accounts/<id>/chain?from=     its records, to check
 *   GET  /api/accounts/<id>/proof           the proof that its head is under the node's head
 *   POST /api/accounts/<id>/records         {record, memo?}: a holder's signed record — `send`, `vouch`, `grant`,
 *                                           `revoke`, `two`, `propose`; a payment's memo, when its hash is the record's
 *   GET  /api/lines                         the nodes this one keeps an account for: key, address, name, account
 *   GET  /api/line?node=<key>               the account it keeps for that node
 *   POST /api/line/open                     {node}: the opening record of its own account at that node, signed
 *   POST /api/accounts/<id>/recovery        {key, sig}: asked back by a new device (`sig`: its signature of
 *                                           `recover:<id>:<key>`) — kept for the community to confirm
 *   POST /api/accounts/<id>/recovered       a recovery announced and due, made so
 *   GET  /api/recoveries                    who asks an account back: account, name, key
 *   GET  /api/board                         the members' offers and wants
 *   POST /api/board                         {command, key, sig}: a member's own word, signed by a key that holds
 *                                           its account — `list {account, kind, title, text, price, share}`,
 *                                           `unlist {account, id}`
 *   GET  /api/cards[?of=<account>]          the shops' stamp cards; with `of`, that customer's stamps at each
 *   POST /api/cards                         {command, key, sig}: a shop's own word — `card {account, title, need,
 *                                           reward}`, `uncard {account}`
 *   POST /api/cards/claim                   {token: {command, key, sig}, account, key, sig}: a stamp or a reward
 *                                           the shop's device signed (`stamp` / `redeem {account, at, n}`, good ten
 *                                           minutes, taken once), claimed by a key that holds `account` (`sig`: its
 *                                           signature of `claim:<the command's hash>:<account>`)
 *   GET  /api/election                      a manager being elected: how many must say a key, who says which
 *   POST /api/election                      {account, key, sig, candidate}: a member's word that `candidate` (a
 *                                           device's key) should manage the community — `sig` the signature of
 *                                           `elect:<the community's account>:<candidate>` by a key that manages
 *                                           the member's own account
 *   POST /api/setup                         {record, name}: the community's own account, once (`x-setup-token`)
 *   POST /api/command                       {command, key, sig}: a command of one who may manage the community
 *
 * Nobody is known by a cookie or a password: a record is its holder's by its signature, a command the community's by
 * the signature of a key its own account's chain says may manage. THE COMMUNITY GOVERNS ITSELF AS ANY ACCOUNT DOES —
 * its holders are added and taken off by the same records as a person's devices; there is no other administrator,
 * of a node or of the hub's nodes: the hub's operator keeps the machine, not the communities.
 *
 * Commands (`{cmd, at, …}`, signed as their text, good for ten minutes, taken once): `limits {account, credit,
 * ceiling}`; `admit {id, credit, ceiling}` — one who asked is opened an account; `reject {id}`; `line {address,
 * credit}` — an account kept for another node (a trust line): how far that community may owe this one; `rules
 * {text}` — the community's rules, from then on the ones a newcomer accepts; `policy {join, only, credit, ceiling,
 * personalCredit, personalMax, after, shared, wait}` — the community's policy (what is not said stays), inside what
 * its hub allows: in force for every account from then on, the lines given before left as they are;
 * `recover {account}` — the community
 * `close {account}` — the account of one who left closed by the community (at a zero balance, nothing lent),
 * what is beside its chain erased; `export {}` — the community as one file, its key pair in it (to bring it to another hub, or keep a copy);
 * `moved {address}` — it went there: from then on this node refuses every change and says where; one that
 * knows who asks that account back: the recovery announced on its chain, in force after the waiting period
 * (`NodeApp.waiting`) unless a device that still holds the account cancels it; `forget {account}` — the asking dropped
 */
object NodeApp extends App:
  val actors: Map[String, Actor] = Map(AccountObject.namespace -> AccountObject, Directory.namespace -> Directory,
    Hub.namespace -> Hub)
  type Live[X] = (Cloud + Signer)[X]
  val fresh: Long = 10 * 60 * 1000L
  private val day: Long = 24L * 60 * 60 * 1000
  /** how long one has to have been a member to have a say in electing a manager */
  val seasoned: Long = 30 * day
  /** how long a recovery is announced before it is in force where a community's policy says nothing else: time for
   * the account's own holder to say no */
  val waiting: Long = 7 * day

  override def fetch(r: Request): Response ! Cloud =
    if r.method == "OPTIONS" then pure(Response(204, Http.cors, ""))
    else SignerHere.run((r.method, r.path.split('/').toList.filter(_.nonEmpty)) match
      case ("GET", List(".well-known", "okay-mutual-hub.json")) => HubEnv.read[Live].map(hub => Http.json(
          Json.Obj(Vector(
          "v" -> Json.num(Record.version), "making" -> Json.str(if hub.open then "open" else "token")) ++
          HubEnv.bounds(hub))))
      case ("GET", List("api", "nodes")) => hub(Request.get("/_hub/nodes")).map(answered)
      // the operator's backup: every community as its file
      case ("GET", List("api", "backup")) => HubEnv.read[Live].flatMap { h =>
          if h.token.isEmpty || !r.header("x-hub-token").contains(h.token) then pure(Http.refused("token", 403))
          else hub(Request.get("/_hub/nodes")).flatMap(ns => scala.util.Try(Http.answer(ns)("nodes").list
            .map(_("slug").string)).getOrElse(Vector.empty).foldLeft(pure[Vector[Json], Live](Vector.empty))(
            (acc, place) => acc.flatMap(all => Objects.call[Live](Directory.namespace, place, Request.get(
              "/_dir/env")).flatMap(e => NodeEnv.read(Http.answer(e)).filter(_ => e.status == 200)
              .fold(pure[Vector[Json], Live](all))(env => exportOf(env).map(all :+ _))))))
            .flatMap(all => Clock.now[Live].map(now => Http.json(Json.obj("at" -> Json.num(now),
              "nodes" -> Json.Arr(all)))))
        }
      case ("POST", List("api", "nodes")) => HubEnv.read[Live].flatMap { h =>
          if !h.open && (h.token.isEmpty || !r.header("x-hub-token").contains(h.token)) then
            pure(Http.refused("token", 403))
          else hub(Http.post(if Http.body(r).field("import").isDefined then "/_hub/import" else "/_hub/create",
            Json.Obj(Http.body(r).record.filterNot(_._1 == "who").map((k, v) => if k == "import" then "file" -> v
              else k -> v) :+
            ("who" -> Json.str(Sha256.hex(r.header("cf-connecting-ip").getOrElse("")).take(16)))))).map { made =>
            if made.status != 200 then answered(made)
            else Http.json(Json.Obj(Http.answer(made).record :+
              ("address" -> Json.str(s"/n/${Http.answer(made)("slug").string}"))))
          }
        }
      case (method, "n" :: place :: rest) if Hub.slug(place) =>
        Objects.call[Live](Directory.namespace, place, Request.get("/_dir/env")).flatMap(e =>
          NodeEnv.read(Http.answer(e)) match
            case Some(env) if e.status == 200 => route(env, r, method, rest)
            case _ => pure(Http.refused("no such node", 404)))
      case _ => pure(Response.notFound()))

  private def hub(r: Request): Response ! Live = Objects.call[Live](Hub.namespace, Hub.name, r)
  /** a call to an account's object, by this node (an account of another node is not there for it) */
  private def account(env: NodeEnv, id: String, r: Request): Response ! Live =
    Objects.call[Live](AccountObject.namespace, id, r.copy(headers = r.headers + ("x-node" -> List(env.pub))))
  private def directory(env: NodeEnv, r: Request): Response ! Live =
    Objects.call[Live](Directory.namespace, env.slug, r)
  private def open(env: NodeEnv, id: String, record: Json, credit: Long, ceiling: Long): Response ! Live =
    Objects.call[Live](AccountObject.namespace, id, Http.post("/_acct/open", Json.obj("record" -> record,
      "credit" -> Json.num(credit), "ceiling" -> Json.num(ceiling), "env" -> NodeEnv.write(env))))
  /** an id is a record's hash: nothing else names an object */
  private def id(s: String): Boolean = s.matches("[0-9a-f]{64}")
  private def answered(r: Response): Response = Http.open(r)

  private def route(env: NodeEnv, r: Request, method: String, path: List[String]): Response ! Live =
    // a community that moved changes nothing here: it says where it went
    if env.moved.nonEmpty && method == "POST" then pure(Http.refused("moved", 410, "detail" -> Json.str(env.moved)))
    else (method, path) match
      // what it is, its policy, and what the hub it is at allows a community: the room its policy has
      case ("GET", List(".well-known", "okay-mutual.json")) => HubEnv.read[Live].map(hub => Http.json(Json.Obj(Vector(
          "v" -> Json.num(Record.version), "key" -> Json.str(env.pub), "unit" -> Json.str(env.unit),
          "name" -> Json.str(env.name)) ++ NodeEnv.policy(env) :+ ("hub" -> Json.Obj(HubEnv.bounds(hub))))))
      case ("GET", List("api", "members")) => directory(env, Request.get("/_dir/members")).map(answered)
      case ("GET", List("api", "head")) => directory(env, Request.get("/_dir/head")).map(answered)
      case ("GET", List("api", "community")) => directory(env, Request.get("/_dir/community")).map(answered)
      case ("GET", List("api", "rules")) => directory(env, Request.get("/_dir/rules")).map(answered)
      case ("GET", List("api", "requests")) => directory(env, Request.get("/_dir/requests")).map(l =>
          // who waits, never their records
          Http.json(Json.obj("requests" -> Json.Arr(scala.util.Try(Http.answer(l)("requests").list).getOrElse(
            Vector.empty).map(q => Json.obj("id" -> q("id"), "name" -> q("name"), "of" -> q("of"), "at" -> q("at")))))))
      // an account asked for: by one the community admits at all, who accepted its rules as they are now
      case ("POST", List("api", "accounts")) => directory(env, Request.get("/_dir/rules")).flatMap { rules =>
          val (body, hash) = (Http.body(r), Http.answer(rules).field("hash").fold("")(_.string))
          if asking(body).exists(a => Shared.kinds(a._3)) then shared(env, body)
          else if env.only == "business" && asking(body).exists(_._3 != "business") then
            pure(Http.refused("business", 409))
          else if hash.nonEmpty && !body.field("rules").contains(Json.str(hash)) then pure(Http.refused("rules", 409))
          else if env.open then joined(env, body, asking(body).fold(0L)(a => env.opening(a._3)), env.ceiling)
          else requested(env, body)
        }
      case ("GET", List("api", "accounts", a)) if id(a) => account(env, a, Request.get("/_acct/state")).map(answered)
      case ("GET", List("api", "accounts", a, "chain")) if id(a) => account(env, a,
          Request.get(s"/_acct/chain?from=${r.param("from").flatMap(_.toLongOption).getOrElse(1L)}")).map(answered)
      case ("GET", List("api", "accounts", a, "proof")) if id(a) =>
        directory(env, Request.get(s"/_dir/proof?account=$a")).map(answered)
      case ("POST", List("api", "accounts", a, "records")) if id(a) =>
        account(env, a, Http.post("/_acct/append", Json.obj("record" -> Http.body(r)("record"),
          "memo" -> Http.body(r).field("memo").getOrElse(Json.str(""))))).map(answered)
      case ("GET", List("api", "held")) => directory(env, Request.get(s"/_dir/held?by=${r.param("by")
          .filter(id).getOrElse("")}")).map(answered)
      case ("GET", List("api", "lines")) => directory(env, Request.get("/_dir/lines")).map(answered)
      case ("GET", List("api", "line")) => directory(env, Request.get(s"/_dir/line?node=${r.param("node")
          .getOrElse("")}")).map(l => if l.status != 200 then answered(l) else
          Http.json(Json.obj("id" -> Http.answer(l)("id"))))
      case ("POST", List("api", "line", "open")) =>
        directory(env, Http.post("/_dir/line/sign", Json.obj("node" -> Http.body(r)("node")))).map(answered)
      // an account asked back by a device that proves it holds the key it names
      case ("POST", List("api", "accounts", a, "recovery")) if id(a) =>
        scala.util.Try((Http.body(r)("key").string, Http.body(r)("sig").string)).toOption match
          case None => pure(Http.refused("body"))
          case Some((key, sig)) => Signer.verify[Live](key, s"recover:$a:$key", sig).flatMap { signed =>
              if !signed then pure(Http.refused("unsigned", 403))
              else account(env, a, Request.get("/_acct/state")).flatMap { st =>
                StateJson.read(Http.answer(st)) match
                  case None => pure(Http.refused("unknown", 404))
                  case Some(s) if s.grant(key).isDefined => pure(Http.refused("held", 409))
                  case Some(_) => directory(env, Request.get("/_dir/members")).flatMap { ms =>
                      val name = scala.util.Try(Http.answer(ms)("members").list.find(_("id").string == a)
                        .map(_("name").string)).toOption.flatten.getOrElse("")
                      Clock.now[Live].flatMap(now => directory(env, Http.post("/_dir/recovery", Json.obj(
                        "account" -> Json.str(a), "key" -> Json.str(key), "name" -> Json.str(name),
                        "at" -> Json.num(now))))).as(Http.json(Json.obj("asked" -> Json.str(a)), 202))
                    }
              }
            }
      case ("POST", List("api", "accounts", a, "recovered")) if id(a) =>
        account(env, a, Http.post("/_acct/recovered", Json.obj())).map(answered)
      case ("GET", List("api", "recoveries")) => directory(env, Request.get("/_dir/recoveries")).map(answered)
      case ("GET", List("api", "board")) => directory(env, Request.get("/_dir/listings")).map(answered)
      case ("POST", List("api", "board")) => posted(env, Http.body(r))
      case ("GET", List("api", "cards")) => directory(env, Request.get(s"/_dir/cards?of=${r.param("of").filter(id)
          .getOrElse("")}")).map(answered)
      case ("POST", List("api", "cards")) => posted(env, Http.body(r))
      case ("POST", List("api", "cards", "claim")) => claimed(env, Http.body(r))
      case ("GET", List("api", "election")) => election(env).map((need, said, _) => Http.json(Json.obj(
          "open" -> Json.bool(env.open), "need" -> Json.num(need), "votes" -> Json.Arr(said.groupBy(_._1).toVector
            .sortBy(-_._2.size).map((k, vs) => Json.obj("key" -> Json.str(k), "count" -> Json.num(vs.size)))))))
      case ("POST", List("api", "election")) => elected(env, Http.body(r))
      case ("POST", List("api", "setup")) => setup(env, r)
      case ("POST", List("api", "command")) => command(env, Http.body(r))
      case _ => pure(Response.notFound())

  /** A COMMUNITY AS ONE FILE (specs/protocol.md "A community's data out and in"): its node — its key pair with it:
   * whoever has the file can be this node —, what its directory keeps, and every account's storage */
  private def exportOf(env: NodeEnv): Json ! Live =
    directory(env, Request.get("/_dir/export")).flatMap { d =>
      val entries = Http.answer(d)
      val ids = scala.util.Try(entries.record.map(_._1)).getOrElse(Vector.empty).collect {
        case k if k.startsWith("dir/head/") => k.stripPrefix("dir/head/")
        case k if k.startsWith("dir/member/") => k.stripPrefix("dir/member/")
      }.distinct.filter(id)
      ids.foldLeft(pure[Vector[(String, Json)], Live](Vector.empty))((acc, a) => acc.flatMap(all =>
        account(env, a, Request.get("/_acct/export")).map(x => if x.status == 200 then all :+ (a -> Http.answer(x))
          else all))).flatMap(accounts => Clock.now[Live].map(now => Json.obj("v" -> Json.num(Record.version),
        "kind" -> Json.str("okay-mutual-node"), "at" -> Json.num(now), "env" -> NodeEnv.write(env),
        "directory" -> entries, "accounts" -> Json.Obj(accounts))))
    }

  /** a first record and its name, read: the record as it travels, its hash, what it opens, when */
  private def asking(j: Json): Option[(Signed, String, String, Long)] =
    val name = j.field("name").collect { case Json.Str(s) => s.replaceAll("\\s+", " ").trim.take(80) }.getOrElse("")
    Signed.parse(j("record")).flatMap(s => Record.parse(s.text).map(s -> _)).collect {
      case (signed, Record(_, _, _, at, Body.Open(_, of, _, _))) if name.nonEmpty => (signed, name, of, at)
    }

  /** an account opened by its first record, its member listed */
  private def joined(env: NodeEnv, j: Json, credit: Long, ceiling: Long): Response ! Live = asking(j) match
    case Some((signed, name, of, at)) => open(env, signed.hash, j("record"), credit, ceiling).flatMap { made =>
        if made.status != 200 then pure(answered(made))
        // a member since the node's own clock says so: the time its first record says is the member's word
        else Clock.now[Live].flatMap(now => directory(env, Http.post("/_dir/member", Json.obj(
          "id" -> Json.str(signed.hash), "name" -> Json.str(name), "of" -> Json.str(of),
          "at" -> Json.num(now.max(at)))))).as(answered(made))
      }
    case None => pure(Http.refused("record"))

  /**
   * A MANAGER ELECTED BY THE MEMBERS (specs/protocol.md "A community outlives its founder's phone"). The members
   * with a say: people and businesses that have been members for `seasoned`. How many must say the same key: more
   * than half of them, two at least. Answered: that number, who says which key now (those still with a say), and
   * who has a say
   */
  private def election(env: NodeEnv): (Int, Vector[(String, String)], Set[String]) ! Live =
    Clock.now[Live].flatMap(now => directory(env, Request.get("/_dir/members")).flatMap(ms =>
      directory(env, Request.get("/_dir/election")).map { said =>
        val voters = scala.util.Try(Http.answer(ms)("members").list).getOrElse(Vector.empty).filter(m =>
          Set("personal", "business")(m("of").string) && m("at").long <= now - seasoned).map(_("id").string).toSet
        val words = scala.util.Try(Http.answer(said)("said").list.map(s => s("key").string -> s("member").string))
          .getOrElse(Vector.empty).filter(w => voters(w._2))
        ((voters.size / 2 + 1).max(2), words, voters)
      }))

  /** a member's word for a key; when enough say the same, the key is announced on the community's own chain as
   * a recovery — in force after the community's wait, cancelled by any manager that still holds a key. Only
   * where joining is by admission: where anyone joins, anyone could vote themselves in */
  private def elected(env: NodeEnv, j: Json): Response ! Live =
    scala.util.Try((j("account").string, j("key").string, j("sig").string, j("candidate").string)).toOption match
      case Some((member, key, sig, candidate)) if id(member) && candidate.matches("[A-Za-z0-9_-]{40,200}") =>
        if env.open then pure(Http.refused("open", 409))
        else directory(env, Request.get("/_dir/community")).flatMap { com =>
          Http.answer(com).field("community").map(_.string) match
            case None => pure(Http.refused("no community", 409))
            case Some(community) => Signer.verify[Live](key, s"elect:$community:$candidate", sig).flatMap { signed =>
                if !signed then pure(Http.refused("unsigned", 403))
                else account(env, member, Request.get("/_acct/state")).flatMap { st =>
                  if !StateJson.read(Http.answer(st)).flatMap(_.grant(key)).exists(_.manage) then
                    pure(Http.refused("forbidden", 403))
                  else election(env).flatMap { (_, _, voters) =>
                    if !voters(member) then pure(Http.refused("young", 403))
                    else directory(env, Http.post("/_dir/elect", Json.obj("key" -> Json.str(candidate),
                      "member" -> Json.str(member)))).flatMap(_ => election(env)).flatMap { (need, said, _) =>
                      val count = said.count(_._1 == candidate)
                      if count < need then pure(Http.json(Json.obj("count" -> Json.num(count),
                        "need" -> Json.num(need))))
                      else Clock.now[Live].flatMap(now => account(env, community, Http.post("/_acct/recover",
                        Json.obj("key" -> Json.str(candidate), "after" -> Json.num(now + env.days * day)))))
                        .flatMap(done => if done.status != 200 then pure(answered(done))
                          else directory(env, Http.post("/_dir/election/clear", Json.obj())).as(Http.json(
                            Json.obj("count" -> Json.num(count), "need" -> Json.num(need),
                              "announced" -> Json.str(candidate)))))
                    }
                  }
                }
              }
        }
      case _ => pure(Http.refused("body"))

  /** AN ACCOUNT A FAMILY OR A TEAM SHARES: opened by an account that is a member here already — so at once,
   * whatever the joining —, with no credit line of its own: what it may owe is lent to it (`vouch`) or given by the
   * community. A team's is a business's to open; whether the opener's device signed is the account object's check */
  private def shared(env: NodeEnv, j: Json): Response ! Live =
    asking(j).flatMap(a => Record.parse(a._1.text).map(_.body)) match
      case Some(_) if !env.shared => pure(Http.refused("shared", 409))
      case Some(Body.Open(_, of, opener, _)) if id(opener) =>
        account(env, opener, Request.get("/_acct/state")).flatMap { st =>
          StateJson.read(Http.answer(st)).filter(_ => st.status == 200) match
            case None => pure(Http.refused("member", 409))
            case Some(p) if Shared.kinds(p.of) || p.of == "line" => pure(Http.refused("member", 409))
            case Some(p) if of == "team" && p.of == "personal" => pure(Http.refused("business", 409))
            case Some(_) if of == "family" && env.only == "business" => pure(Http.refused("business", 409))
            case Some(_) => joined(env, j, 0, env.ceiling)
        }
      case _ => pure(Http.refused("record"))

  /** WHERE JOINING IS NOT OPEN: the first record kept as a request — it must be this node's and signed by its own
   * key, so nobody asks in another's name — until a holder of the community admits or rejects it */
  private def requested(env: NodeEnv, j: Json): Response ! Live = asking(j) match
    case Some((signed, name, of, at)) => Record.parse(signed.text).map(_.body) match
        case Some(Body.Open(node, _, key, unit)) if node == env.pub && unit == env.unit && of != "community" =>
          Check.signers[Live](signed).flatMap { signers =>
            if !signers(key) then pure(Http.refused("unsigned", 409))
            else directory(env, Http.post("/_dir/request", Json.obj("id" -> Json.str(signed.hash),
              "record" -> j("record"), "name" -> Json.str(name), "of" -> Json.str(of), "at" -> Json.num(at))))
              .map(kept => if kept.status != 200 then answered(kept) else
                Http.json(Json.obj("requested" -> Json.str(signed.hash)), 202))
          }
        case _ => pure(Http.refused("open"))
    case None => pure(Http.refused("record"))

  /** THE COMMUNITY'S OWN ACCOUNT, once: opened by the founder's key, behind the setup token — from then on the
   * community is governed by that account's holders, and the token opens nothing */
  private def setup(env: NodeEnv, r: Request): Response ! Live =
    if env.setup.isEmpty || !r.header("x-setup-token").contains(env.setup) then pure(Http.refused("token", 403))
    else directory(env, Request.get("/_dir/community")).flatMap { c =>
      if Http.answer(c).field("community").isDefined then pure(Http.refused("set", 409))
      else Signed.parse(Http.body(r)("record")) match
        case None => pure(Http.refused("record"))
        case Some(signed) => joined(env, Http.body(r), 0, 0).flatMap { made =>
            if made.status != 200 then pure(made)
            else directory(env, Http.post("/_dir/community", Json.obj("id" -> Json.str(signed.hash)))).as(made)
          }
    }

  /** A COMMAND: its text signed by a key the community's account says may manage, its time near now, never taken
   * before */
  private def command(env: NodeEnv, j: Json): Response ! Live =
    scala.util.Try((j("command").string, j("key").string, j("sig").string)).toOption match
      case None => pure(Http.refused("body"))
      case Some((text, key, sig)) => Descent.parse(text).toOption match
          case None => pure(Http.refused("command"))
          case Some(c) => Clock.now[Live].flatMap { now =>
              directory(env, Request.get("/_dir/community")).flatMap { com =>
                Http.answer(com).field("community").map(_.string) match
                  case None => pure(Http.refused("no community", 409))
                  case Some(community) => account(env, community, Request.get("/_acct/state")).flatMap { st =>
                      val may = StateJson.read(Http.answer(st)).flatMap(_.grant(key)).exists(_.manage)
                      val at = scala.util.Try(c("at").long).getOrElse(0L)
                      if !may then pure(Http.refused("forbidden", 403))
                      else if math.abs(at - now) > fresh then pure(Http.refused("time", 409))
                      else Signer.verify[Live](key, text, sig).flatMap { signed =>
                        if !signed then pure(Http.refused("unsigned", 403))
                        else directory(env, Http.post("/_dir/command", Json.obj("hash" ->
                          Json.str(Sha256.hex(text))))).flatMap(taken =>
                          if taken.status != 200 then pure(Http.refused("used", 409)) else commanded(env, c))
                      }
                    }
              }
            }

  private def commanded(env: NodeEnv, c: Json): Response ! Live =
    scala.util.Try(c("cmd").string).getOrElse("") match
      case "limits" => scala.util.Try((c("account").string, c("credit").long, c("ceiling").long)).toOption match
          // a line's ceiling stays unbounded, whatever is asked: only how far the peer may owe is this node's to say
          case Some((a, credit, ceiling)) if id(a) => account(env, a, Request.get("/_acct/state")).flatMap { st =>
              val line = StateJson.read(Http.answer(st)).exists(_.of == "line")
              account(env, a, Http.post("/_acct/limits", Json.obj("credit" -> Json.num(credit),
                "ceiling" -> Json.num(if line then Peers.unbounded else ceiling)))).map(answered)
            }
          case _ => pure(Http.refused("command"))
      // the community knows who asks: the recovery announced on the account's chain, the asking taken off
      case "recover" => scala.util.Try(c("account").string).toOption.filter(id) match
          case None => pure(Http.refused("command"))
          case Some(a) => directory(env, Request.get(s"/_dir/recovery?account=$a")).flatMap { q =>
              if q.status != 200 then pure(Http.refused("no such request", 404))
              else Clock.now[Live].flatMap(now => account(env, a, Http.post("/_acct/recover", Json.obj(
                "key" -> Http.answer(q)("key"), "after" -> Json.num(now + env.days * day))))).flatMap(done =>
                if done.status != 200 then pure(answered(done))
                else directory(env, Http.post("/_dir/recovery/done", Json.obj("account" -> Json.str(a))))
                  .as(answered(done)))
            }
      case "forget" => scala.util.Try(c("account").string).toOption.filter(id) match
          case None => pure(Http.refused("command"))
          case Some(a) => directory(env, Http.post("/_dir/recovery/done", Json.obj("account" -> Json.str(a))))
              .map(answered)
      // the community's policy: its own to change, inside what the hub it is at allows
      case "policy" => HubEnv.read[Live].flatMap(hub => NodeEnv.changed(env, c) match
          case None => pure(Http.refused("policy"))
          case Some(next) => next.allowed(hub) match
              case Some(why) => pure(Http.refused("policy", 409, "detail" -> Json.str(why)))
              case None => directory(env, Http.post("/_dir/policy", c)).map(answered))
      case "export" => exportOf(env).map(Http.json(_))
      case "close" => scala.util.Try(c("account").string).toOption.filter(id) match
          case None => pure(Http.refused("command"))
          case Some(a) => directory(env, Request.get("/_dir/community")).flatMap(com =>
              account(env, a, Request.get("/_acct/state")).flatMap { st =>
                val of = StateJson.read(Http.answer(st)).map(_.of)
                // the community's own account and a line to another community are not a member's to close
                if Http.answer(com).field("community").contains(Json.str(a)) || of.contains("line") then
                  pure(Http.refused("command"))
                else if of.isEmpty then pure(Http.refused("unknown", 404))
                else account(env, a, Http.post("/_acct/close", Json.obj())).map(answered)
              })
      case "moved" => scala.util.Try(c("address").string.trim.stripSuffix("/")).toOption match
          case Some(to) if Peers.valid(to) || to.matches("/n/[a-z0-9-]+") =>
            directory(env, Http.post("/_dir/moved", Json.obj("address" -> Json.str(to)))).map(answered)
          case _ => pure(Http.refused("command"))
      case "rules" => scala.util.Try(c("text").string).toOption match
          case Some(text) => directory(env, Http.post("/_dir/rules", Json.obj("text" -> Json.str(text)))).map(answered)
          case None => pure(Http.refused("command"))
      case "line" => scala.util.Try((c("address").string.trim.stripSuffix("/"), c("credit").long)).toOption match
          case Some((address, credit)) if Peers.valid(address) && credit >= 0 => lined(env, address, credit)
          case _ => pure(Http.refused("command"))
      // one who asked, opened an account with the limits the community gives
      case "admit" => scala.util.Try((c("id").string, c("credit").long, c("ceiling").long)).toOption match
          case Some((asked, credit, ceiling)) if id(asked) =>
            directory(env, Request.get(s"/_dir/request?id=$asked")).flatMap { q =>
              if q.status != 200 then pure(Http.refused("no such request", 404))
              else joined(env, Http.answer(q), credit, ceiling)
            }
          case _ => pure(Http.refused("command"))
      case "reject" => scala.util.Try(c("id").string).toOption match
          case Some(asked) if id(asked) => directory(env, Http.post("/_dir/reject", Json.obj("id" -> Json.str(asked))))
              .map(answered)
          case _ => pure(Http.refused("command"))
      case _ => pure(Http.refused("command"))

  /** A TRUST LINE OPENED (or its credit changed): the peer asked who it is and to sign the opening record of its
   * account here — of the same unit only —, the account opened with the credit this community gives, its ceiling
   * unbounded; listed among the members under the peer's name, and kept as this node's line to that peer */
  private def lined(env: NodeEnv, address: String, credit: Long): Response ! Live =
    Peers.about[Live](address).flatMap {
      case None => pure(Http.refused("no such node", 404))
      case Some(peer) if peer.pub == env.pub => pure(Http.refused("same", 409))
      case Some(peer) if peer.unit != env.unit => pure(Http.refused("unit", 409, "detail" -> Json.str(peer.unit)))
      case Some(peer) => Peers.signedOpening[Live](address, env.pub).flatMap {
          case Some(signed) if signed.text == Record.text(Peers.opening(env.pub, peer.pub, env.unit)) =>
            open(env, signed.hash, Signed.json(signed), credit, Peers.unbounded).flatMap { made =>
              // there already: its credit set
              (if made.status == 409 then account(env, signed.hash, Http.post("/_acct/limits", Json.obj(
                "credit" -> Json.num(credit), "ceiling" -> Json.num(Peers.unbounded)))) else pure(made)).flatMap {
                done =>
                  if done.status != 200 then pure(answered(done))
                  else directory(env, Http.post("/_dir/line", Json.obj("node" -> Json.str(peer.pub),
                    "id" -> Json.str(signed.hash), "address" -> Json.str(address), "name" -> Json.str(peer.name))))
                    .flatMap(_ => directory(env, Http.post("/_dir/member", Json.obj("id" -> Json.str(signed.hash),
                      "name" -> Json.str(peer.name), "of" -> Json.str("line"), "at" -> Json.num(0)))))
                    .as(answered(done))
              }
            }
          case _ => pure(Http.refused("peer", 502))
        }
    }

  /**
   * A STAMP OR A REWARD CLAIMED (specs/protocol.md "Stamp cards"). The shop's device signs a token — `stamp` or
   * `redeem`, its account, the time, a number of its own — and shows it as a code; the customer's device brings it
   * with its own signature over the token's hash and the customer's account. Checked: the token fresh, signed by a
   * key that holds the shop's account and may spend from it; the claim signed by a key that holds the customer's;
   * the token never taken before. A stamp is no unit: nothing of any chain changes
   */
  private def claimed(env: NodeEnv, j: Json): Response ! Live =
    scala.util.Try((j("token")("command").string, j("token")("key").string, j("token")("sig").string,
      j("account").string, j("key").string, j("sig").string)).toOption.flatMap(t =>
      Descent.parse(t._1).toOption.flatMap(c => scala.util.Try((c("cmd").string, c("account").string, c("at").long))
        .toOption).map(t -> _)) match
      case Some(((text, shopKey, shopSig, customer, key, sig), (cmd, shop, at)))
        if id(shop) && id(customer) && Set("stamp", "redeem")(cmd) =>
        def holds(account: String, k: String, spend: Boolean): Boolean ! Live =
          this.account(env, account, Request.get("/_acct/state")).map(st => StateJson.read(Http.answer(st))
            .flatMap(_.grant(k)).exists(g => !spend || g.spend))
        Clock.now[Live].flatMap { now =>
          if shop == customer then pure(Http.refused("same", 409))
          else if math.abs(at - now) > fresh then pure(Http.refused("time", 409))
          else Signer.verify[Live](shopKey, text, shopSig).flatMap(a => Signer.verify[Live](key,
            s"claim:${Sha256.hex(text)}:$customer", sig).map(a && _)).flatMap { signed =>
            if !signed then pure(Http.refused("unsigned", 403))
            else holds(shop, shopKey, spend = true).flatMap(s => holds(customer, key, spend = false).map(s && _))
              .flatMap { may =>
                if !may then pure(Http.refused("forbidden", 403))
                else directory(env, Http.post("/_dir/command", Json.obj("hash" -> Json.str(Sha256.hex(text)))))
                  .flatMap(taken => if taken.status != 200 then pure(Http.refused("used", 409))
                    else directory(env, Http.post("/_dir/stamp", Json.obj("shop" -> Json.str(shop),
                      "customer" -> Json.str(customer), "kind" -> Json.str(cmd)))).map(answered))
              }
          }
        }
      case _ => pure(Http.refused("body"))

  /** A MEMBER'S OWN WORD ON THE BOARD: its text signed by a key that holds the account it names and may spend from
   * it, its time near now, taken once. Kept beside the chains: an entry is no record, it is taken off without a
   * trace. `list`: what is offered or wanted, its price (0: by agreement), the share of it taken in units */
  private def posted(env: NodeEnv, j: Json): Response ! Live =
    scala.util.Try((j("command").string, j("key").string, j("sig").string)).toOption.flatMap((text, key, sig) =>
      Descent.parse(text).toOption.flatMap(c => scala.util.Try((c, c("cmd").string, c("account").string,
        c("at").long)).toOption).map((text, key, sig, _))) match
      case None => pure(Http.refused("body"))
      case Some((text, key, sig, (c, cmd, a, at))) if id(a) => Clock.now[Live].flatMap { now =>
          account(env, a, Request.get("/_acct/state")).flatMap { st =>
            val may = StateJson.read(Http.answer(st)).flatMap(_.grant(key)).exists(_.spend)
            if !may then pure(Http.refused("forbidden", 403))
            else if math.abs(at - now) > fresh then pure(Http.refused("time", 409))
            else Signer.verify[Live](key, text, sig).flatMap { signed =>
              if !signed then pure(Http.refused("unsigned", 403))
              else directory(env, Http.post("/_dir/command", Json.obj("hash" -> Json.str(Sha256.hex(text)))))
                .flatMap(taken => if taken.status != 200 then pure(Http.refused("used", 409)) else cmd match
                  case "list" => scala.util.Try((c("kind").string, c("title").string.replaceAll("\\s+", " ").trim
                      .take(120), c.field("text").fold("")(_.string).trim.take(1000), c("price").long,
                      c("share").long)).toOption match
                      case Some((kind, title, more, price, share)) if Set("offer", "want")(kind) && title.nonEmpty &&
                        price >= 0 && share >= 0 && share <= 100 =>
                        directory(env, Http.post("/_dir/listing", Json.obj("id" -> Json.str(Sha256.hex(text).take(16)),
                          "account" -> Json.str(a), "kind" -> Json.str(kind), "title" -> Json.str(title),
                          "text" -> Json.str(more), "price" -> Json.num(price), "share" -> Json.num(share),
                          "at" -> Json.num(now)))).map(answered)
                      case _ => pure(Http.refused("listing"))
                  // a shop's stamp card: so many stamps, then the reward it names
                  case "card" => scala.util.Try((c("title").string.replaceAll("\\s+", " ").trim.take(80),
                      c("need").long, c("reward").string.replaceAll("\\s+", " ").trim.take(120))).toOption match
                      case Some((title, need, reward)) if title.nonEmpty && reward.nonEmpty && need >= 2 &&
                        need <= 50 => directory(env, Http.post("/_dir/card", Json.obj("account" -> Json.str(a),
                          "title" -> Json.str(title), "need" -> Json.num(need), "reward" -> Json.str(reward),
                          "at" -> Json.num(now)))).map(answered)
                      case _ => pure(Http.refused("card"))
                  case "uncard" => directory(env, Http.post("/_dir/card/remove", Json.obj("account" -> Json.str(a))))
                      .map(answered)
                  case "unlist" => scala.util.Try(c("id").string).toOption match
                      case Some(entry) => directory(env, Http.post("/_dir/listing/remove", Json.obj(
                          "id" -> Json.str(entry), "account" -> Json.str(a)))).map(answered)
                      case None => pure(Http.refused("listing"))
                  case _ => pure(Http.refused("command")))
            }
          }
        }
      case _ => pure(Http.refused("body"))
