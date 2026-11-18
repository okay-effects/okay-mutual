package okay.mutual.wallet

import okay.business.scala3.*
import okay.effects.json.Json
import okay.mutual.chain.*

/** WHAT A WALLET ASKS OF THE NETWORK: a call to the node in use (the one `Settings` keeps as `node`), a call to the
 * hub the wallet is at (where communities are listed and made), and the link by which others come to the node in
 * use. Answered by the browser's `fetch` in a page, by the hub itself in memory in a test. No answer: status 0 */
enum Net[X]:
  case Call(method: String, path: String, body: Option[Json], token: String) extends Net[(Int, Json)]
  case AtHub(method: String, path: String, body: Option[Json]) extends Net[(Int, Json)]
  /** a call to another node, by its address (a place at this hub, or a whole address): what anyone may ask it */
  case Elsewhere(address: String, path: String) extends Net[(Int, Json)]
  /** the wallet's address with the node in use in it: what a member sends to one it invites */
  case Invite() extends Net[String]

/** what a hub allows its communities: the ways with private people, the most a business's default line, the most a
 * person may owe (0: any) */
final case class Bounds(personal: Set[String] = Set("business", "earned", "vouched", "record", ""),
                        maxCredit: Long = 0L, maxPersonal: Long = 0L)
/** what a node says it is (`/.well-known/okay-mutual.json`): its key, its unit, its name — and its policy */
final case class About(key: String, unit: String, name: String, open: Boolean, credit: Long, ceiling: Long,
                       only: String = "", personalCredit: Long = 0L, personalMax: Long = 0L, after: Long = 0L,
                       shared: Boolean = true, days: Int = 7, hub: Bounds = Bounds())
/** a community as its hub lists it */
final case class Listed(slug: String, name: String, unit: String, open: Boolean)
/** a node this one keeps an account for (a trust line): its key, its address, its name, the account's id here */
final case class TrustLine(node: String, address: String, name: String, id: String)
/** one who asks an account back: the account, its member's name, the new device's key */
final case class Lost(account: String, name: String, key: String)
/** an entry of the board: a member's offer or want, its price (0: by agreement), the share of it taken in units */
final case class Listing(id: String, account: String, kind: String, title: String, text: String, price: Long,
                         share: Long)
/** one who waits to be admitted */
final case class Asked(id: String, name: String, of: String)
/** a member as the others see it */
final case class Member(id: String, name: String, of: String)
/** a record of a chain with what was kept beside it */
final case class Line(signed: Signed, record: Record, memo: String)
/** the node's answer when it is no: its word and what it is about */
final case class Refusal(status: Int, why: String, detail: String)
/** the proof that an account's head is under the node's signed head */
final case class Proved(head: Signed, number: Long, root: String, accountHead: String, steps: Vector[(String, Boolean)])

/** THE NODE'S CALLS, in a wallet's words (specs/protocol.md "A node") */
object Node:
  private def call[R[_]: Has[Net]](method: String, path: String, body: Option[Json] = None, token: String = "")
    : (Int, Json) ! R = perform[Net](Net.Call(method, path, body, token))

  private def refusal(status: Int, j: Json): Refusal =
    Refusal(status, j.field("error").collect { case Json.Str(s) => s }.getOrElse(if status == 0 then "offline" else
      "refused"), j.field("detail").fold("")(d => d match
        case Json.Str(s) => s
        case Json.Num(n) => n.toString
        case _ => ""))
  private def stated(answer: (Int, Json)): Either[Refusal, State] =
    if answer._1 == 200 then StateJson.read(answer._2("state")).toRight(Refusal(200, "answer", "")) else
      Left(refusal(answer._1, answer._2))

  def about[R[_]: Has[Net]]: Option[About] ! R = call[R]("GET", "/.well-known/okay-mutual.json").map((status, j) =>
    if status != 200 then None else scala.util.Try(About(j("key").string, j("unit").string, j("name").string,
      j("join").string == "open", j("credit").long, j("ceiling").long,
      j.field("only").collect { case Json.Str(s) => s }.getOrElse(""),
      j.field("personalCredit").fold(j("credit").long)(_.long), j.field("personalMax").fold(0L)(_.long),
      j.field("after").fold(0L)(_.long), j.field("shared").forall(_.boolean), j.field("wait").fold(7)(_.long.toInt),
      j.field("hub").fold(Bounds())(bounded))).toOption)

  private def bounded(j: Json): Bounds = scala.util.Try(Bounds(j("personal").list.map(_.string).toSet,
    j("maxCredit").long, j("maxPersonal").long)).getOrElse(Bounds())
  /** what the hub this app is at allows a community made there */
  def bounds[R[_]: Has[Net]]: Bounds ! R =
    perform[Net](Net.AtHub("GET", "/.well-known/okay-mutual-hub.json", None)).map((_, j) => bounded(j))

  def members[R[_]: Has[Net]]: Vector[Member] ! R = call[R]("GET", "/api/members").map((_, j) =>
    scala.util.Try(j("members").list.map(m => Member(m("id").string, m("name").string, m("of").string)))
      .getOrElse(Vector.empty))

  def community[R[_]: Has[Net]]: Option[String] ! R = call[R]("GET", "/api/community").map((_, j) =>
    j.field("community").collect { case Json.Str(s) => s })

  def account[R[_]: Has[Net]](id: String): Option[State] ! R = call[R]("GET", s"/api/accounts/$id").map((status, j) =>
    if status == 200 then StateJson.read(j) else None)

  def chain[R[_]: Has[Net]](id: String): Vector[Line] ! R = call[R]("GET", s"/api/accounts/$id/chain").map((_, j) =>
    scala.util.Try(j("records").list).getOrElse(Vector.empty).flatMap(x => Signed.parse(x).flatMap(s =>
      Record.parse(s.text).map(r => Line(s, r, x.field("memo").collect { case Json.Str(m) => m }.getOrElse(""))))))

  /** an account opened: the account — or none, where joining is not open and the record was kept as a request */
  def open[R[_]: Has[Net]](record: Signed, name: String, rules: String = ""): Either[Refusal, Option[State]] ! R =
    call[R]("POST", "/api/accounts", Some(Json.obj("record" -> Signed.json(record), "name" -> Json.str(name),
      "rules" -> Json.str(rules))))
      .map(answer => if answer._1 == 202 then Right(None) else stated(answer).map(Some(_)))

  /** the accounts that the account `by` holds: a family's, a team's */
  def heldBy[R[_]: Has[Net]](by: String): Vector[String] ! R = call[R]("GET", s"/api/held?by=$by").map((_, j) =>
    scala.util.Try(j("accounts").list.map(_.string)).getOrElse(Vector.empty))

  def requests[R[_]: Has[Net]]: Vector[Asked] ! R = call[R]("GET", "/api/requests").map((_, j) =>
    scala.util.Try(j("requests").list.map(q => Asked(q("id").string, q("name").string, q("of").string)))
      .getOrElse(Vector.empty))

  def invite[R[_]: Has[Net]]: String ! R = perform[Net](Net.Invite())

  /** the community's board, newest first */
  def board[R[_]: Has[Net]]: Vector[Listing] ! R = call[R]("GET", "/api/board").map((_, j) =>
    scala.util.Try(j("listings").list.map(l => Listing(l("id").string, l("account").string, l("kind").string,
      l("title").string, l("text").string, l("price").long, l("share").long))).getOrElse(Vector.empty))
  /** a member's own signed word on the board (`list`, `unlist`) */
  def post[R[_]: Has[Net]](text: String, key: String, sig: String): Either[Refusal, Unit] ! R =
    call[R]("POST", "/api/board", Some(Json.obj("command" -> Json.str(text), "key" -> Json.str(key),
      "sig" -> Json.str(sig)))).map((status, j) => if status == 200 then Right(()) else Left(refusal(status, j)))

  /** an account asked back by this device: `sig` its signature of `recover:<account>:<key>` */
  def askBack[R[_]: Has[Net]](account: String, key: String, sig: String): Either[Refusal, Unit] ! R =
    call[R]("POST", s"/api/accounts/$account/recovery", Some(Json.obj("key" -> Json.str(key), "sig" -> Json.str(sig))))
      .map((status, j) => if status == 202 then Right(()) else Left(refusal(status, j)))
  /** a recovery announced and due, made so (refused until its time) */
  def recovered[R[_]: Has[Net]](account: String): Boolean ! R =
    call[R]("POST", s"/api/accounts/$account/recovered", Some(Json.obj())).map(_._1 == 200)
  def recoveries[R[_]: Has[Net]]: Vector[Lost] ! R = call[R]("GET", "/api/recoveries").map((_, j) =>
    scala.util.Try(j("recoveries").list.map(q => Lost(q("account").string, q("name").string, q("key").string)))
      .getOrElse(Vector.empty))

  /** the community's rules — the text a newcomer accepts — and their hash (both empty: it has none) */
  def rules[R[_]: Has[Net]]: (String, String) ! R = call[R]("GET", "/api/rules").map((_, j) =>
    scala.util.Try((j("text").string, j("hash").string)).getOrElse(("", "")))

  /** the communities this one has a trust line to */
  def lines[R[_]: Has[Net]]: Vector[TrustLine] ! R = call[R]("GET", "/api/lines").map((_, j) =>
    scala.util.Try(j("lines").list.map(l => TrustLine(l("node").string, l("address").string, l("name").string,
      l("id").string))).getOrElse(Vector.empty))

  /** the members of another community, asked of its own node */
  def membersAt[R[_]: Has[Net]](address: String): Vector[Member] ! R =
    perform[Net](Net.Elsewhere(address, "/api/members")).map((_, j) => scala.util.Try(j("members").list.map(m =>
      Member(m("id").string, m("name").string, m("of").string))).getOrElse(Vector.empty))

  /** the communities of the hub this wallet is at */
  def listed[R[_]: Has[Net]]: Vector[Listed] ! R = perform[Net](Net.AtHub("GET", "/api/nodes", None)).map((_, j) =>
    scala.util.Try(j("nodes").list.map(n => Listed(n("slug").string, n("name").string, n("unit").string,
      n("join").string == "open"))).getOrElse(Vector.empty))

  /** a community made at the hub: its address there and its setup token */
  def make[R[_]: Has[Net]](slug: String, name: String, unit: String, open: Boolean, credit: Long, ceiling: Long,
                           only: String = "", rules: String = "", personalCredit: Long = 0L,
                           personalMax: Long = 0L): Either[Refusal, (String, String)] ! R =
    perform[Net](Net.AtHub("POST", "/api/nodes", Some(Json.obj("slug" -> Json.str(slug), "name" -> Json.str(name),
      "unit" -> Json.str(unit), "join" -> Json.str(if open then "open" else "admitted"),
      "credit" -> Json.num(credit), "ceiling" -> Json.num(ceiling), "only" -> Json.str(only),
      "personalCredit" -> Json.num(personalCredit), "personalMax" -> Json.num(personalMax),
      "rules" -> Json.str(rules))))).map((status, j) =>
      if status == 200 then scala.util.Try((j("address").string, j("token").string)).toOption
        .toRight(Refusal(200, "answer", "")) else Left(refusal(status, j)))

  def setup[R[_]: Has[Net]](record: Signed, name: String, token: String): Either[Refusal, State] ! R =
    call[R]("POST", "/api/setup", Some(Json.obj("record" -> Signed.json(record), "name" -> Json.str(name))), token)
      .map(stated)

  def append[R[_]: Has[Net]](id: String, record: Signed, memo: String = ""): Either[Refusal, State] ! R =
    call[R]("POST", s"/api/accounts/$id/records", Some(Json.obj("record" -> Signed.json(record),
      "memo" -> Json.str(memo)))).map(stated)

  def command[R[_]: Has[Net]](text: String, key: String, sig: String): Either[Refusal, Unit] ! R =
    call[R]("POST", "/api/command", Some(Json.obj("command" -> Json.str(text), "key" -> Json.str(key),
      "sig" -> Json.str(sig)))).map((status, j) => if status == 200 then Right(()) else Left(refusal(status, j)))

  def proof[R[_]: Has[Net]](id: String): Option[Proved] ! R = call[R]("GET", s"/api/accounts/$id/proof").map(
    (status, j) => if status != 200 then None else scala.util.Try {
      val head = Signed.parse(j("head")).get
      val said = okay.effects.json.Descent.parse(head.text).toOption.get
      Proved(head, said("number").long, said("root").string, j("accountHead").string,
        j("proof").list.map(s => s("hash").string -> s("left").boolean))
    }.toOption)

/** THIS DEVICE'S KEY (specs/protocol.md "Keys, devices and who may act"): made here, never leaving — a program can
 * ask for its public half and for a signature, and for nothing else. In a page: Web Crypto's unextractable key in
 * the browser's own store */
enum Device[X]:
  /** the public half, when a key was made */
  case Key() extends Device[Option[String]]
  /** a key made (kept from then on): its public half */
  case Make() extends Device[String]
  case Sign(text: String) extends Device[String]

object Device:
  def key[R[_]: Has[Device]]: Option[String] ! R = perform[Device](Key())
  /** this device's key, made when there is none */
  def ready[R[_]: Has[Device]]: String ! R = key[R].flatMap(_.fold(perform[Device](Make()))(pure(_)))
  def sign[R[_]: Has[Device]](text: String): String ! R = perform[Device](Sign(text))
  /** a record signed by this device, as it travels to the node */
  def signed[R[_]: Has[Device]](r: Record): Signed ! R =
    val text = Record.text(r)
    ready[R].flatMap(pub => sign[R](text).map(sig => Signed(text, Vector(pub -> sig))))
