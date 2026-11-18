package okay.mutual.chain

import okay.effects.json.Json

/** AN ACCOUNT'S STATE AS JSON: what a node keeps beside the chain so as not to fold it again, and what it answers.
 * Never the truth — the chain is; anyone can fold the records and compare */
object StateJson:
  private def grant(g: Grant): Json = Json.obj("key" -> Json.str(g.key), "spend" -> Json.bool(g.spend),
    "manage" -> Json.bool(g.manage), "payment" -> Json.num(g.payment), "period" -> Json.num(g.period),
    "per" -> Json.num(g.per), "under" -> g.under.fold[Json](Json.Null)(Json.str))
  private def amounts(m: Map[String, Long]): Json = Json.Obj(m.toVector.sortBy(_._1).map((k, v) => k -> Json.num(v)))

  def write(s: State): Json.Obj = Json.obj("id" -> Json.str(s.id), "node" -> Json.str(s.node), "of" -> Json.str(s.of),
    "unit" -> Json.str(s.unit), "seq" -> Json.num(s.seq), "head" -> Json.str(s.head), "at" -> Json.num(s.at),
    "credit" -> Json.num(s.credit), "ceiling" -> Json.num(s.ceiling), "balance" -> Json.num(s.balance),
    "floor" -> Json.num(s.floor), "vouched" -> Json.num(s.vouched),
    "voucher" -> s.voucher.fold[Json](Json.Null)(Json.str), "lent" -> Json.num(s.lent),
    "grants" -> Json.Arr(s.grants.map(grant)), "sent" -> amounts(s.sent),
    "names" -> Json.Arr(s.names.toVector.sorted.map(Json.str)), "lends" -> amounts(s.lends),
    "spent" -> Json.Arr(s.spent.map(x => Json.obj("key" -> Json.str(x.key), "at" -> Json.num(x.at),
      "amount" -> Json.num(x.amount)))),
    "recovering" -> s.recovering.fold[Json](Json.Null)((k, after) => Json.obj("key" -> Json.str(k),
      "after" -> Json.num(after))), "two" -> Json.num(s.two),
    "proposed" -> Json.Arr(s.proposed.map(p => Json.obj("hash" -> Json.str(p.hash), "by" -> Json.str(p.by),
      "to" -> Json.str(p.to), "amount" -> Json.num(p.amount), "memo" -> Json.str(p.memo),
      "name" -> Json.str(p.name), "at" -> Json.num(p.at)))), "earned" -> Json.num(s.earned))

  def read(j: Json): Option[State] = scala.util.Try {
    def map(name: String): Map[String, Long] = j(name).record.map((k, v) => k -> v.long).toMap
    State(j("id").string, j("node").string, j("of").string, j("unit").string, j("seq").long, j("head").string,
      j("at").long, j("credit").long, j("ceiling").long, j("balance").long, j("vouched").long,
      j.field("voucher").map(_.string), j("lent").long,
      j("grants").list.map(g => Grant(g("key").string, g("spend").boolean, g("manage").boolean, g("payment").long,
        g("period").long, g("per").long, g.field("under").map(_.string))),
      map("sent"), j("names").list.map(_.string).toSet, map("lends"),
      j("spent").list.map(x => Spent(x("key").string, x("at").long, x("amount").long)),
      j.field("recovering").map(x => x("key").string -> x("after").long),
      // a state kept before these were: none of either
      j.field("two").fold(0L)(_.long), j.field("proposed").fold(Vector.empty)(_.list.map(p => Proposal(
        p("hash").string, p("by").string, p("to").string, p("amount").long, p("memo").string, p("name").string,
        p("at").long))), j.field("earned").fold(0L)(_.long))
  }.toOption
