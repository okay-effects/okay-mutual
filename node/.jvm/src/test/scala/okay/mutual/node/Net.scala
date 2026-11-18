package okay.mutual.node

import okay.effects.json.{Descent, Json}
import okay.mutual.chain.*
import okay.scala3.*
import okay.scala3.std.Async
import okay.serverless.cloud.{MemoryCloud, Request, Response}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

/** A HUB IN A TEST: its app and objects on okay-serverless' `MemoryCloud`, a clock that moves only when told */
final class Hubs(vars: (String, String)*):
  given ExecutionContext = Async.defaultContext
  var now: Long = 1795000000000L
  val cloud = MemoryCloud(() => now)
  cloud.vars ++= Map("HUB_OPEN" -> "1") ++ vars
  cloud.actors ++= NodeApp.actors
  def ask(r: Request): Response = Await.result(cloud.serve(NodeApp)(r), 10.seconds)
  def get(path: String): Response = ask(Request.get(path))
  def post(path: String, j: Json, headers: (String, String)*): Response =
    ask(Request.post(path, Json.print(j)).copy(headers = headers.map((k, v) => k -> List(v)).toMap +
      ("content-type" -> List("application/json"))))
  def json(r: Response): Json = Descent.parse(r.body).toOption.getOrElse(Json.Null)
  /** a node made here, as anyone makes one */
  def make(slug: String, name: String, join: String = "open", from: String = "1.2.3.4"): Response =
    post("/api/nodes", Json.obj("slug" -> Json.str(slug), "name" -> Json.str(name), "unit" -> Json.str("PLN"),
      "join" -> Json.str(join), "credit" -> Json.num(10000), "ceiling" -> Json.num(30000)),
      "cf-connecting-ip" -> from)

/** A NODE IN A TEST: made on a hub (its own unless one is given), its calls under its place; its key the hub's
 * making — a real one, by the JDK */
final class Net(join: String = "open", val hub: Hubs = Hubs(), slug: String = "town", name: String = "Test Town"):
  private val made = hub.json(hub.make(slug, name, join))
  val prefix = s"/n/$slug"
  /** the node's key (its secret half is the node's alone) */
  val node: Pair = Pair(made("key").string, "")
  val token: String = made("token").string
  def cloud: MemoryCloud = hub.cloud
  def now: Long = hub.now
  def now_=(t: Long): Unit = hub.now = t
  given ExecutionContext = Async.defaultContext

  def ask(r: Request): Response = hub.ask(r.copy(path = prefix + r.path))
  def get(path: String): Response = ask(Request.get(path))
  def post(path: String, j: Json, headers: (String, String)*): Response =
    ask(Request.post(path, Json.print(j)).copy(headers = headers.map((k, v) => k -> List(v)).toMap +
      ("content-type" -> List("application/json"))))
  def json(r: Response): Json = Descent.parse(r.body).toOption.getOrElse(Json.Null)
  def alarms(): Int = Await.result(cloud.fireAlarms(), 10.seconds)

  /** a chain as the node gives it, checked from its first record by real signatures */
  def checked(id: String): Either[(Int, Fault), State] =
    val records = json(get(s"/api/accounts/$id/chain"))("records").list.flatMap(Signed.parse)
    runIsolated(SignerJdk.run(Check.chain[Pure + Signer](records)))

  /** a command signed by `by` */
  def command(by: Pair, fields: (String, Json)*): Response =
    val text = Json.print(Json.Obj(fields.toVector))
    post("/api/command", Json.obj("command" -> Json.str(text), "key" -> Json.str(by.pub),
      "sig" -> Json.str(SignerJdk.sign(by.secret, text))))

/** A MEMBER'S WALLET IN A TEST: a device's key, the records it signs */
final class Wallet(net: Net, val key: Pair = SignerJdk.generate()):
  var id = ""
  def signed(r: Record, by: Pair = key): Json =
    val text = Record.text(r)
    Signed.json(Signed(text, Vector(by.pub -> SignerJdk.sign(by.secret, text))))
  def opening(of: String = "personal"): Record = Record("", 1, "", net.now, Body.Open(net.node.pub, of, key.pub, "PLN"))

  def join(name: String, of: String = "personal"): Response =
    val record = signed(opening(of))
    val r = net.post("/api/accounts", Json.obj("record" -> record, "name" -> Json.str(name)))
    if r.status == 200 then id = net.json(r)("state")("id").string
    r
  def state: Json = net.json(net.get(s"/api/accounts/$id"))
  def balance: Long = state("balance").long
  /** the chain's next record saying `body`, as the account is now */
  def next(body: Body): Record = Record(id, state("seq").long + 1, state("head").string, net.now, body)
  def send(r: Record, by: Pair = key): Response =
    net.post(s"/api/accounts/$id/records", Json.obj("record" -> signed(r, by)))
  def say(body: Body, by: Pair = key): Response = send(next(body), by)
  def pay(to: Wallet, amount: Long, name: String = ""): Response = say(Body.Send(to.id, amount, "", name))
