package okay.mutual.wallet

import okay.business.scala3.*
import okay.effects.json.Json
import okay.mutual.chain.*

/** A CALL TO THE NODE: the one thing a wallet asks of the network. Answered by the browser's `fetch` in a page, by
 * the node itself in memory in a test. A node that does not answer: status 0 */
enum Net[X]:
  case Call(method: String, path: String, body: Option[Json], token: String) extends Net[(Int, Json)]

/** what a node says it is (`/.well-known/okay-mutual.json`) */
final case class About(key: String, unit: String, name: String, open: Boolean, credit: Long, ceiling: Long)
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
      j("join").string == "open", j("credit").long, j("ceiling").long)).toOption)

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

  def open[R[_]: Has[Net]](record: Signed, name: String): Either[Refusal, State] ! R =
    call[R]("POST", "/api/accounts", Some(Json.obj("record" -> Signed.json(record), "name" -> Json.str(name))))
      .map(stated)

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
