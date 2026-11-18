package okay.mutual.node

import okay.effects.json.{Descent, Json}
import okay.mutual.chain.*
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.cloud.{Actor, App, Cloud, Objects, Request, Response}

/**
 * A NODE (specs/protocol.md "A node"): a community's computer. What it answers, to any wallet and any other node:
 *
 *   GET  /.well-known/okay-mutual.json      what it is: its key, its unit, its name, whether anyone may join
 *   GET  /api/members                       its members: id, name, kind
 *   GET  /api/head                          its signed head
 *   POST /api/accounts                      {record, name}: an account opened by its first, signed record
 *   GET  /api/accounts/<id>                 the account as its chain makes it
 *   GET  /api/accounts/<id>/chain?from=     its records, to check
 *   GET  /api/accounts/<id>/proof           the proof that its head is under the node's head
 *   POST /api/accounts/<id>/records         {record}: a holder's signed record — `send`, `vouch`, `grant`, `revoke`
 *   POST /api/setup                         {record, name}: the community's own account, once (`x-setup-token`)
 *   POST /api/command                       {command, key, sig}: a command of one who may manage the community
 *
 * Nobody is known by a cookie or a password: a record is its holder's by its signature, a command the community's by
 * the signature of a key its own account's chain says may manage. THE COMMUNITY GOVERNS ITSELF AS ANY ACCOUNT DOES —
 * its holders are added and taken off by the same records as a person's devices; there is no other administrator.
 *
 * Commands (`{cmd, at, …}`, signed as their text, good for ten minutes, taken once): `limits {account, credit,
 * ceiling}`; `admit {record, name, credit, ceiling}` — an account opened where joining is not open
 */
object NodeApp extends App:
  val actors: Map[String, Actor] = Map(AccountObject.namespace -> AccountObject, Directory.namespace -> Directory)
  type Live[X] = (Cloud + Signer)[X]
  val fresh: Long = 10 * 60 * 1000L

  override def fetch(r: Request): Response ! Cloud =
    if r.method == "OPTIONS" then pure(Response(204, Http.cors, ""))
    else SignerHere.run(NodeEnv.read[Live].flatMap(route(_, r)))

  private def account(id: String, r: Request): Response ! Live = Objects.call[Live](AccountObject.namespace, id, r)
  private def directory(r: Request): Response ! Live = Objects.call[Live](Directory.namespace, Directory.name, r)
  private def open(id: String, record: Json, credit: Long, ceiling: Long): Response ! Live =
    account(id, Http.post("/_acct/open", Json.obj("record" -> record, "credit" -> Json.num(credit),
      "ceiling" -> Json.num(ceiling))))
  /** an id is a record's hash: nothing else names an object */
  private def id(s: String): Boolean = s.matches("[0-9a-f]{64}")
  private def answered(r: Response): Response = r.copy(headers = r.headers ++ Http.cors)

  private def route(env: NodeEnv, r: Request): Response ! Live =
    (r.method, r.path.split('/').toList.filter(_.nonEmpty)) match
      case ("GET", List(".well-known", "okay-mutual.json")) => pure(Http.json(Json.obj(
          "v" -> Json.num(Record.version), "key" -> Json.str(env.pub), "unit" -> Json.str(env.unit),
          "name" -> Json.str(env.name), "join" -> Json.str(if env.open then "open" else "admitted"),
          "credit" -> Json.num(env.credit), "ceiling" -> Json.num(env.ceiling))))
      case ("GET", List("api", "members")) => directory(Request.get("/_dir/members")).map(answered)
      case ("GET", List("api", "head")) => directory(Request.get("/_dir/head")).map(answered)
      case ("POST", List("api", "accounts")) =>
        if !env.open then pure(Http.refused("admitted", 403))
        else joined(env, Http.body(r), env.credit, env.ceiling)
      case ("GET", List("api", "accounts", a)) if id(a) => account(a, Request.get("/_acct/state")).map(answered)
      case ("GET", List("api", "accounts", a, "chain")) if id(a) =>
        account(a, Request.get(s"/_acct/chain?from=${r.param("from").flatMap(_.toLongOption).getOrElse(1L)}"))
          .map(answered)
      case ("GET", List("api", "accounts", a, "proof")) if id(a) =>
        directory(Request.get(s"/_dir/proof?account=$a")).map(answered)
      case ("POST", List("api", "accounts", a, "records")) if id(a) =>
        account(a, Http.post("/_acct/append", Json.obj("record" -> Http.body(r)("record")))).map(answered)
      case ("POST", List("api", "setup")) => setup(env, r)
      case ("POST", List("api", "command")) => command(env, Http.body(r))
      case _ => pure(Response.notFound())

  /** an account opened by its first record, its member listed */
  private def joined(env: NodeEnv, j: Json, credit: Long, ceiling: Long): Response ! Live =
    val name = j.field("name").collect { case Json.Str(s) => s.replaceAll("\\s+", " ").trim.take(80) }.getOrElse("")
    Signed.parse(j("record")).flatMap(s => Record.parse(s.text).map(s -> _)) match
      case Some((signed, Record(_, _, _, at, Body.Open(_, of, _, _)))) if name.nonEmpty =>
        open(signed.hash, j("record"), credit, ceiling).flatMap { made =>
          if made.status != 200 then pure(answered(made))
          else directory(Http.post("/_dir/member", Json.obj("id" -> Json.str(signed.hash), "name" -> Json.str(name),
            "of" -> Json.str(of), "at" -> Json.num(at)))).as(answered(made))
        }
      case _ => pure(Http.refused("record"))

  /** THE COMMUNITY'S OWN ACCOUNT, once: opened by the founder's key, behind the setup token — from then on the
   * community is governed by that account's holders, and the token opens nothing */
  private def setup(env: NodeEnv, r: Request): Response ! Live =
    if env.setup.isEmpty || !r.header("x-setup-token").contains(env.setup) then pure(Http.refused("token", 403))
    else directory(Request.get("/_dir/community")).flatMap { c =>
      if Http.answer(c).field("community").isDefined then pure(Http.refused("set", 409))
      else Signed.parse(Http.body(r)("record")) match
        case None => pure(Http.refused("record"))
        case Some(signed) => joined(env, Http.body(r), 0, 0).flatMap { made =>
            if made.status != 200 then pure(made)
            else directory(Http.post("/_dir/community", Json.obj("id" -> Json.str(signed.hash)))).as(made)
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
              directory(Request.get("/_dir/community")).flatMap { com =>
                Http.answer(com).field("community").map(_.string) match
                  case None => pure(Http.refused("no community", 409))
                  case Some(community) => account(community, Request.get("/_acct/state")).flatMap { st =>
                      val may = StateJson.read(Http.answer(st)).flatMap(_.grant(key)).exists(_.manage)
                      val at = scala.util.Try(c("at").long).getOrElse(0L)
                      if !may then pure(Http.refused("forbidden", 403))
                      else if math.abs(at - now) > fresh then pure(Http.refused("time", 409))
                      else Signer.verify[Live](key, text, sig).flatMap { signed =>
                        if !signed then pure(Http.refused("unsigned", 403))
                        else directory(Http.post("/_dir/command", Json.obj("hash" -> Json.str(Sha256.hex(text)))))
                          .flatMap(taken => if taken.status != 200 then pure(Http.refused("used", 409))
                            else commanded(env, c))
                      }
                    }
              }
            }

  private def commanded(env: NodeEnv, c: Json): Response ! Live =
    scala.util.Try(c("cmd").string).getOrElse("") match
      case "limits" => scala.util.Try((c("account").string, c("credit").long, c("ceiling").long)).toOption match
          case Some((a, credit, ceiling)) if id(a) => account(a, Http.post("/_acct/limits", Json.obj(
              "credit" -> Json.num(credit), "ceiling" -> Json.num(ceiling)))).map(answered)
          case _ => pure(Http.refused("command"))
      case "admit" => scala.util.Try((c("credit").long, c("ceiling").long)).toOption match
          case Some((credit, ceiling)) => joined(env, c, credit, ceiling)
          case None => pure(Http.refused("command"))
      case _ => pure(Http.refused("command"))
