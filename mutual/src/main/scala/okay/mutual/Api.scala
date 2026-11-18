package okay.mutual

import okay.effects.json.{Descent, Json}
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.cloud.{Request, Response}

/**
 * THE LEDGER'S CALLS over HTTP, under a prefix the host chooses. Who may call is the host's question: these answer
 * whoever reaches them. Amounts are whole numbers of hundredths (`4000` is 40.00 units).
 *
 *   GET  <prefix>/accounts            every account
 *   GET  <prefix>/accounts/<id>       an account, its journal, the credit line its sales suggest
 *   PUT  <prefix>/accounts/<id>       {kind, name, credit, ceiling}: opened; there already: {credit, ceiling} set
 *   POST <prefix>/pay                 {from, to, amount, memo}
 *   POST <prefix>/reverse             {seq}
 *   POST <prefix>/vouch               {voucher, newcomer, amount}
 *   POST <prefix>/release             {newcomer}
 *   GET  <prefix>/journal             every line
 *   GET  <prefix>/measures            how the network lives (`Measures`)
 */
object Api:
  /** the months of average sales a suggested credit line is, and its cap (specs/mutual.md "Limits") */
  val months = 2.0
  val cap = 500000L

  private val headers = Map("Content-Type" -> "application/json; charset=utf-8", "Cache-Control" -> "no-store")
  def json(j: Json, status: Int = 200): Response = Response(status, headers, Json.print(j))
  def refused(r: Refusal): Response = json(Wire.refusal(r), r match
    case Refusal.Unknown(_) => 404
    case Refusal.Invalid(_) => 400
    case _ => 409)
  private def bad(what: String): Response = refused(Refusal.Invalid(what))

  private def answered[A](e: Either[Refusal, A])(f: A => Json): Response = e.fold(refused, a => json(f(a)))

  /** the body's fields read by `read`, then answered by `then`: a body that is not what `read` asks is refused */
  private def body[A, R[_]](r: Request)(read: Json => A)(next: A => Response ! R): Response ! R =
    Descent.parse(r.body).toOption.flatMap(j => scala.util.Try(read(j)).toOption).fold(pure(bad("body")))(next)

  def answer[R[_]](prefix: String, r: Request)(using In[Ledger, R], In[Clock, R]): Response ! R =
    (r.method, r.path.stripPrefix(prefix).stripSuffix("/")) match
      case ("GET", "/accounts") => Ledger.accounts[R].map(as =>
          json(Json.obj("accounts" -> Json.Arr(as.sortBy(_.id).map(Wire.account)))))
      case ("GET", p) if p.startsWith("/accounts/") =>
        val id = p.stripPrefix("/accounts/")
        Ledger.find[R](id).flatMap {
          case None => pure(refused(Refusal.Unknown(id)))
          case Some(a) => Ledger.journal[R]().flatMap(all => Clock.now[R].map { now =>
              val own = all.filter(t => t.from == id || t.to == id)
              json(Json.obj("account" -> Wire.account(a), "journal" -> Json.Arr(own.map(Wire.transfer)),
                "suggested" -> Json.num(Rules.suggested(Measures.sales(id, all, now), months, cap))))
            })
        }
      case ("PUT", p) if p.startsWith("/accounts/") =>
        val id = p.stripPrefix("/accounts/")
        Ledger.find[R](id).flatMap {
          case Some(_) => body(r)(j => (j("credit").long, j("ceiling").long))((credit, ceiling) =>
              Ledger.limit[R](id, credit, ceiling).map(answered(_)(Wire.account)))
          case None => body(r)(j => Account(id, Wire.kind(j("kind").string), j("name").string, j("credit").long,
              j("ceiling").long))(a => Clock.now[R].flatMap(now => Ledger.open[R](a.copy(joined = now)))
              .map(answered(_)(Wire.account)))
        }
      case ("POST", "/pay") => body(r)(j => (j("from").string, j("to").string, j("amount").long,
          j.field("memo").fold("")(_.string)))((from, to, amount, memo) => Clock.now[R].flatMap(now =>
          Ledger.pay[R](from, to, amount, memo, now)).map(answered(_)(Wire.transfer)))
      case ("POST", "/reverse") => body(r)(_("seq").long)(seq => Clock.now[R].flatMap(now =>
          Ledger.reverse[R](seq, now)).map(answered(_)(Wire.transfer)))
      case ("POST", "/vouch") => body(r)(j => (j("voucher").string, j("newcomer").string, j("amount").long))(
          (voucher, newcomer, amount) => Ledger.vouch[R](voucher, newcomer, amount).map(answered(_)(Wire.account)))
      case ("POST", "/release") => body(r)(_("newcomer").string)(newcomer =>
          Ledger.release[R](newcomer).map(answered(_)(Wire.account)))
      case ("GET", "/journal") => Ledger.journal[R]().map(ts =>
          json(Json.obj("journal" -> Json.Arr(ts.map(Wire.transfer)))))
      case ("GET", "/measures") => Ledger.accounts[R].flatMap(as => Ledger.journal[R]().flatMap(ts =>
          Clock.now[R].map(now => json(Wire.measures(Measures.of(as, ts, now))))))
      case _ => pure(Response.notFound())

/**
 * THE NETWORK'S OBJECT: one instance a network (its name), the ledger its durable state; its requests one at a time
 * where the cloud can, so a payment never races another. A host binds the namespace and sends it what it allows
 */
object LedgerObject extends okay.serverless.cloud.Actor:
  val namespace = "MUTUAL"
  val prefix = "/_mutual"
  import okay.serverless.cloud.InActor
  def fetch(name: String, r: Request): Response ! InActor =
    LedgerDurable.run(Api.answer[InActor + Ledger](prefix, r))
