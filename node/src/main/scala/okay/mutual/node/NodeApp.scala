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
 *   POST /api/accounts                      {record, name}: an account opened by its first, signed record — or,
 *                                           where joining is not open, kept as a request (202)
 *   GET  /api/requests                      who waits to be admitted: id, name, kind
 *   GET  /api/accounts/<id>                 the account as its chain makes it
 *   GET  /api/accounts/<id>/chain?from=     its records, to check
 *   GET  /api/accounts/<id>/proof           the proof that its head is under the node's head
 *   POST /api/accounts/<id>/records         {record, memo?}: a holder's signed record — `send`, `vouch`, `grant`,
 *                                           `revoke`; a payment's memo, when its hash is the record's
 *   POST /api/setup                         {record, name}: the community's own account, once (`x-setup-token`)
 *   POST /api/command                       {command, key, sig}: a command of one who may manage the community
 *
 * Nobody is known by a cookie or a password: a record is its holder's by its signature, a command the community's by
 * the signature of a key its own account's chain says may manage. THE COMMUNITY GOVERNS ITSELF AS ANY ACCOUNT DOES —
 * its holders are added and taken off by the same records as a person's devices; there is no other administrator,
 * of a node or of the hub's nodes: the hub's operator keeps the machine, not the communities.
 *
 * Commands (`{cmd, at, …}`, signed as their text, good for ten minutes, taken once): `limits {account, credit,
 * ceiling}`; `admit {id, credit, ceiling}` — one who asked is opened an account; `reject {id}`
 */
object NodeApp extends App:
  val actors: Map[String, Actor] = Map(AccountObject.namespace -> AccountObject, Directory.namespace -> Directory,
    Hub.namespace -> Hub)
  type Live[X] = (Cloud + Signer)[X]
  val fresh: Long = 10 * 60 * 1000L

  override def fetch(r: Request): Response ! Cloud =
    if r.method == "OPTIONS" then pure(Response(204, Http.cors, ""))
    else SignerHere.run((r.method, r.path.split('/').toList.filter(_.nonEmpty)) match
      case ("GET", List(".well-known", "okay-mutual-hub.json")) => HubEnv.read[Live].map(hub => Http.json(Json.obj(
          "v" -> Json.num(Record.version), "making" -> Json.str(if hub.open then "open" else "token"))))
      case ("GET", List("api", "nodes")) => hub(Request.get("/_hub/nodes")).map(answered)
      case ("POST", List("api", "nodes")) => HubEnv.read[Live].flatMap { h =>
          if !h.open && (h.token.isEmpty || !r.header("x-hub-token").contains(h.token)) then
            pure(Http.refused("token", 403))
          else hub(Http.post("/_hub/create", Json.Obj(Http.body(r).record.filterNot(_._1 == "who") :+
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
    (method, path) match
      case ("GET", List(".well-known", "okay-mutual.json")) => pure(Http.json(Json.obj(
          "v" -> Json.num(Record.version), "key" -> Json.str(env.pub), "unit" -> Json.str(env.unit),
          "name" -> Json.str(env.name), "join" -> Json.str(if env.open then "open" else "admitted"),
          "credit" -> Json.num(env.credit), "ceiling" -> Json.num(env.ceiling))))
      case ("GET", List("api", "members")) => directory(env, Request.get("/_dir/members")).map(answered)
      case ("GET", List("api", "head")) => directory(env, Request.get("/_dir/head")).map(answered)
      case ("GET", List("api", "community")) => directory(env, Request.get("/_dir/community")).map(answered)
      case ("GET", List("api", "requests")) => directory(env, Request.get("/_dir/requests")).map(l =>
          // who waits, never their records
          Http.json(Json.obj("requests" -> Json.Arr(scala.util.Try(Http.answer(l)("requests").list).getOrElse(
            Vector.empty).map(q => Json.obj("id" -> q("id"), "name" -> q("name"), "of" -> q("of"), "at" -> q("at")))))))
      case ("POST", List("api", "accounts")) =>
        if env.open then joined(env, Http.body(r), env.credit, env.ceiling) else requested(env, Http.body(r))
      case ("GET", List("api", "accounts", a)) if id(a) => account(env, a, Request.get("/_acct/state")).map(answered)
      case ("GET", List("api", "accounts", a, "chain")) if id(a) => account(env, a,
          Request.get(s"/_acct/chain?from=${r.param("from").flatMap(_.toLongOption).getOrElse(1L)}")).map(answered)
      case ("GET", List("api", "accounts", a, "proof")) if id(a) =>
        directory(env, Request.get(s"/_dir/proof?account=$a")).map(answered)
      case ("POST", List("api", "accounts", a, "records")) if id(a) =>
        account(env, a, Http.post("/_acct/append", Json.obj("record" -> Http.body(r)("record"),
          "memo" -> Http.body(r).field("memo").getOrElse(Json.str(""))))).map(answered)
      case ("POST", List("api", "setup")) => setup(env, r)
      case ("POST", List("api", "command")) => command(env, Http.body(r))
      case _ => pure(Response.notFound())

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
        else directory(env, Http.post("/_dir/member", Json.obj("id" -> Json.str(signed.hash),
          "name" -> Json.str(name), "of" -> Json.str(of), "at" -> Json.num(at)))).as(answered(made))
      }
    case None => pure(Http.refused("record"))

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
          case Some((a, credit, ceiling)) if id(a) => account(env, a, Http.post("/_acct/limits", Json.obj(
              "credit" -> Json.num(credit), "ceiling" -> Json.num(ceiling)))).map(answered)
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
