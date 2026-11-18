package okay.mutual

import okay.effects.json.{Descent, Json}
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.{Memory, Storage}
import okay.serverless.cloud.{Request, Response}

/** A NETWORK IN A TEST: the ledger on okay-serverless' `Memory` (which keeps `Storage`'s contract as an object's
 * SQLite does), a clock that moves only when told */
final class World:
  val storage = Memory()
  var now: Long = 1795000000000L
  val day = Measures.day
  type W[X] = (Pure + Storage + Ledger + Clock)[X]

  def run[A](p: A ! W): A = runIsolated(storage.handler(LedgerDurable.run(Clock.virtual(now)(p))))._2

  def open(id: String, kind: Kind = Kind.Business, credit: Long = 10000, ceiling: Long = 30000)
    : Either[Refusal, Account] = run(Ledger.open[W](Account(id, kind, id.capitalize, credit, ceiling, joined = now)))
  def pay(from: String, to: String, amount: Long, memo: String = ""): Either[Refusal, Transfer] =
    run(Ledger.pay[W](from, to, amount, memo, now))
  def account(id: String): Account = run(Ledger.find[W](id)).get
  def balances: Map[String, Long] = run(Ledger.accounts[W]).map(a => a.id -> a.balance).toMap
  def measures: Measures = run(Ledger.accounts[W].flatMap(as => Ledger.journal[W]().map(Measures.of(as, _, now))))

  // ---- over HTTP, as a host sends it
  def ask(method: String, path: String, body: String = ""): Response =
    run(Api.answer[W]("/_mutual", Request(method, "/_mutual" + path).copy(body = body)))
  def json(r: Response): Json = Descent.parse(r.body).toOption.getOrElse(Json.Null)
