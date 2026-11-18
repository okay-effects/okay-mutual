package okay.mutual

/** who holds an account: a business (its credit follows its sales) or a person (little or none until earned) */
enum Kind:
  case Business, Personal

/**
 * AN ACCOUNT of the network (specs/mutual.md "Accounts"). Amounts are in hundredths of a unit; a unit is priced as
 * one unit of the national currency and is never exchanged for it.
 *
 * `credit`: how far below zero its own line lets it go; `ceiling`: how far above zero it may rise — who reaches it
 * must spend before selling more. `vouched`: credit lent to it by `voucher`; `lent`: credit it lent to others
 */
final case class Account(id: String, kind: Kind, name: String, credit: Long, ceiling: Long, balance: Long = 0L,
                         joined: Long = 0L, voucher: Option[String] = None, vouched: Long = 0L, lent: Long = 0L):
  /** the lowest balance it may reach: its own line, what was lent to it, less what it lent */
  def floor: Long = -(credit + vouched - lent)

/** A TRANSFER, a line of the journal: `amount` from the buyer to the seller. Never changed; a mistake is undone by
 * another line (`reverses` its number) */
final case class Transfer(seq: Long, from: String, to: String, amount: Long, memo: String, at: Long,
                          reverses: Option[Long] = None)

/** why the ledger refused */
enum Refusal(val code: String):
  /** no account of this id */
  case Unknown(id: String) extends Refusal("unknown")
  /** an account of this id is there */
  case Taken(id: String) extends Refusal("taken")
  /** not an id, not a positive amount, a negative limit, the same account twice */
  case Invalid(what: String) extends Refusal("invalid")
  /** the buyer would go below its floor by `short` */
  case Credit(short: Long) extends Refusal("credit")
  /** the seller would rise above its ceiling by `over` */
  case Ceiling(over: Long) extends Refusal("ceiling")
  /** a line reversed already, or itself a reversal */
  case Reversed(seq: Long) extends Refusal("reversed")
  /** a newcomer vouched for by another, or still below its own line */
  case Vouched(id: String) extends Refusal("vouched")

/** THE RULES, pure: what a change does to the accounts it touches, or why not */
object Rules:
  /** an account's id: a lowercase letter or digit, then letters, digits, `-`, `.`, `_`, `@`; at most 64 */
  def id(s: String): Boolean = s.matches("[a-z0-9][a-z0-9._@-]{1,63}")

  def opened(a: Account): Either[Refusal, Account] =
    if !id(a.id) then Left(Refusal.Invalid("id"))
    else if a.name.trim.isEmpty then Left(Refusal.Invalid("name"))
    else if a.credit < 0 || a.ceiling < 0 then Left(Refusal.Invalid("limits"))
    else Right(a.copy(balance = 0L, voucher = None, vouched = 0L, lent = 0L))

  /** new limits: never a floor above the balance it has (its debt stays within its line) */
  def limited(a: Account, credit: Long, ceiling: Long): Either[Refusal, Account] =
    val next = a.copy(credit = credit, ceiling = ceiling)
    if credit < 0 || ceiling < 0 then Left(Refusal.Invalid("limits"))
    else if a.balance < next.floor then Left(Refusal.Credit(next.floor - a.balance))
    else Right(next)

  /** a payment: the buyer within its floor, the seller within its ceiling — both, or nothing */
  def paid(from: Account, to: Account, amount: Long): Either[Refusal, (Account, Account)] =
    if from.id == to.id then Left(Refusal.Invalid("same"))
    else if amount <= 0 then Left(Refusal.Invalid("amount"))
    else if from.balance - amount < from.floor then Left(Refusal.Credit(from.floor - (from.balance - amount)))
    else if to.balance + amount > to.ceiling then Left(Refusal.Ceiling(to.balance + amount - to.ceiling))
    else Right((from.copy(balance = from.balance - amount), to.copy(balance = to.balance + amount)))

  /** VOUCHING: `voucher` lends `amount` of its own line to `newcomer` — it answers for the one it brought with its
   * own room. One voucher a newcomer; the voucher's debt must fit the line it keeps */
  def vouched(voucher: Account, newcomer: Account, amount: Long): Either[Refusal, (Account, Account)] =
    val lender = voucher.copy(lent = voucher.lent + amount)
    if voucher.id == newcomer.id then Left(Refusal.Invalid("same"))
    else if amount <= 0 then Left(Refusal.Invalid("amount"))
    else if newcomer.voucher.exists(_ != voucher.id) then Left(Refusal.Vouched(newcomer.id))
    else if voucher.balance < lender.floor then Left(Refusal.Credit(lender.floor - voucher.balance))
    else Right((lender, newcomer.copy(voucher = Some(voucher.id), vouched = newcomer.vouched + amount)))

  /** the lent line returned to the voucher: when the newcomer's balance fits its own line without it */
  def released(voucher: Account, newcomer: Account): Either[Refusal, (Account, Account)] =
    val free = newcomer.copy(voucher = None, vouched = 0L)
    if !newcomer.voucher.contains(voucher.id) then Left(Refusal.Vouched(newcomer.id))
    else if newcomer.balance < free.floor then Left(Refusal.Credit(free.floor - newcomer.balance))
    else Right((voucher.copy(lent = voucher.lent - newcomer.vouched), free))

  /** the credit line a business's sales suggest (specs/mutual.md "Limits"): `months` of its average monthly sales
   * over the last three months, at most `cap` */
  def suggested(sales: Long, months: Double, cap: Long): Long =
    math.min(cap, math.round(sales / 3.0 * months))
