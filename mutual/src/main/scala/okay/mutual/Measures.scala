package okay.mutual

/**
 * HOW THE NETWORK LIVES (specs/mutual.md "Measures"): numbers that registrations and listings cannot flatter.
 *
 *  - `velocity`: the last 30 days' turnover over the sum of the positive balances — how many times a unit worked
 *  - `both`: the share of accounts that both sold and bought in the last 30 days
 *  - `stale`: the share of the positive balances held by accounts with no line in the last 60 days
 *  - `firstSale`: the median days from joining to the first sale, of those who sold
 *  - `repeat`: the share of the last 30 days' deals between a pair that had dealt before
 *  - `sum`: the balances' sum — zero, or the ledger is broken
 *
 * A share with nothing to divide by is 0
 */
final case class Measures(accounts: Int, turnover: Long, positive: Long, velocity: Double, both: Double,
                          stale: Double, firstSale: Double, repeat: Double, sum: Long)

object Measures:
  val day = 86400000L

  /** the deals of the journal: neither a reversal nor a line reversed */
  def deals(journal: Vector[Transfer]): Vector[Transfer] =
    val undone = journal.flatMap(_.reverses).toSet
    journal.filter(t => t.reverses.isEmpty && !undone(t.seq))

  /** an account's sales in the three months before `now` (the base of its suggested credit line) */
  def sales(id: String, journal: Vector[Transfer], now: Long): Long =
    deals(journal).filter(t => t.to == id && t.at > now - 90 * day).map(_.amount).sum

  def of(accounts: Vector[Account], journal: Vector[Transfer], now: Long): Measures =
    val all = deals(journal)
    val recent = all.filter(_.at > now - 30 * day)
    val turnover = recent.map(_.amount).sum
    val positive = accounts.map(_.balance).filter(_ > 0).sum
    def share(part: Double, whole: Double): Double = if whole == 0 then 0.0 else part / whole
    val sellers = recent.map(_.to).toSet
    val buyers = recent.map(_.from).toSet
    val active = journal.filter(_.at > now - 60 * day).flatMap(t => Vector(t.from, t.to)).toSet
    val idle = accounts.filter(a => a.balance > 0 && !active(a.id)).map(_.balance).sum
    val joined = accounts.map(a => a.id -> a.joined).toMap
    val waits = all.groupBy(_.to).toVector.flatMap((id, ts) =>
      joined.get(id).map(j => (ts.map(_.at).min - j).max(0L).toDouble / day)).sorted
    val median = if waits.isEmpty then 0.0 else
      if waits.size % 2 == 1 then waits(waits.size / 2) else (waits(waits.size / 2 - 1) + waits(waits.size / 2)) / 2
    def pair(t: Transfer): Set[String] = Set(t.from, t.to)
    val again = recent.count(t => all.exists(o => o.seq < t.seq && pair(o) == pair(t)))
    Measures(accounts.size, turnover, positive, share(turnover.toDouble, positive.toDouble),
      share(sellers.intersect(buyers).size.toDouble, accounts.size.toDouble), share(idle.toDouble, positive.toDouble),
      median, share(again.toDouble, recent.size.toDouble), accounts.map(_.balance).sum)
