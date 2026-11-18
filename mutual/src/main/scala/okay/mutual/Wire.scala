package okay.mutual

import okay.effects.json.Json

/** THE LEDGER'S DATA AS JSON: what is stored and what the calls answer, the same */
object Wire:
  def account(a: Account): Json.Obj = Json.obj("id" -> Json.str(a.id), "kind" -> Json.str(kind(a.kind)),
    "name" -> Json.str(a.name), "credit" -> Json.num(a.credit), "ceiling" -> Json.num(a.ceiling),
    "balance" -> Json.num(a.balance), "joined" -> Json.num(a.joined),
    "voucher" -> a.voucher.fold[Json](Json.Null)(Json.str), "vouched" -> Json.num(a.vouched),
    "lent" -> Json.num(a.lent), "floor" -> Json.num(a.floor))

  def account(j: Json): Account = Account(j("id").string, kind(j("kind").string), j("name").string,
    j("credit").long, j("ceiling").long, j("balance").long, j("joined").long, j.field("voucher").map(_.string),
    j("vouched").long, j("lent").long)

  def transfer(t: Transfer): Json.Obj = Json.obj("seq" -> Json.num(t.seq), "from" -> Json.str(t.from),
    "to" -> Json.str(t.to), "amount" -> Json.num(t.amount), "memo" -> Json.str(t.memo), "at" -> Json.num(t.at),
    "reverses" -> t.reverses.fold[Json](Json.Null)(Json.num))

  def transfer(j: Json): Transfer = Transfer(j("seq").long, j("from").string, j("to").string, j("amount").long,
    j("memo").string, j("at").long, j.field("reverses").map(_.long))

  def kind(k: Kind): String = k match
    case Kind.Business => "business"
    case Kind.Personal => "personal"
  def kind(s: String): Kind = if s == "business" then Kind.Business else Kind.Personal

  /** a refusal: its code, and what it is about */
  def refusal(r: Refusal): Json.Obj = Json.obj("error" -> Json.str(r.code), "detail" -> (r match
    case Refusal.Unknown(id) => Json.str(id)
    case Refusal.Taken(id) => Json.str(id)
    case Refusal.Invalid(what) => Json.str(what)
    case Refusal.Credit(short) => Json.num(short)
    case Refusal.Ceiling(over) => Json.num(over)
    case Refusal.Reversed(seq) => Json.num(seq)
    case Refusal.Vouched(id) => Json.str(id)))

  def measures(m: Measures): Json.Obj = Json.obj("accounts" -> Json.num(m.accounts),
    "turnover" -> Json.num(m.turnover), "positive" -> Json.num(m.positive), "velocity" -> Json.num(m.velocity),
    "both" -> Json.num(m.both), "stale" -> Json.num(m.stale), "firstSale" -> Json.num(m.firstSale),
    "repeat" -> Json.num(m.repeat), "sum" -> Json.num(m.sum))
