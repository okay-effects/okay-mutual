package okay.mutual

import okay.scala3.*

/**
 * THE LEDGER OF A NETWORK (specs/mutual.md): accounts and the journal of transfers between them. The balances always
 * sum to zero: a unit is born when a member buys and gone when it sells. Every change is one step — both accounts and
 * the journal's line written together, or nothing
 */
enum Ledger[X]:
  /** a new account, at zero */
  case Open(account: Account) extends Ledger[Either[Refusal, Account]]
  case Find(id: String) extends Ledger[Option[Account]]
  case Accounts() extends Ledger[Vector[Account]]
  case Limit(id: String, credit: Long, ceiling: Long) extends Ledger[Either[Refusal, Account]]
  /** who may act for the account (empty: nobody but the operator) */
  case Hold(id: String, holder: String) extends Ledger[Either[Refusal, Account]]
  /** `amount` from the buyer to the seller, within the buyer's floor and the seller's ceiling. `key` (not empty)
   * names the payment: asked again under the same key, it answers the line it made and pays nothing more */
  case Pay(from: String, to: String, amount: Long, memo: String, at: Long, key: String = "")
    extends Ledger[Either[Refusal, Transfer]]
  /** a line undone by its opposite, whatever the limits say (a correction, not a deal); once */
  case Reverse(seq: Long, at: Long) extends Ledger[Either[Refusal, Transfer]]
  /** `amount` of the voucher's line lent to the newcomer: the newcomer as it is after */
  case Vouch(voucher: String, newcomer: String, amount: Long) extends Ledger[Either[Refusal, Account]]
  /** the lent line returned to the newcomer's voucher */
  case Release(newcomer: String) extends Ledger[Either[Refusal, Account]]
  /** the journal, oldest first: an account's lines, or all */
  case Journal(account: Option[String]) extends Ledger[Vector[Transfer]]

object Ledger:
  def open[R[_]](a: Account)(using In[Ledger, R]): Either[Refusal, Account] ! R = perform[Ledger](Open(a))
  def find[R[_]](id: String)(using In[Ledger, R]): Option[Account] ! R = perform[Ledger](Find(id))
  def accounts[R[_]](using In[Ledger, R]): Vector[Account] ! R = perform[Ledger](Accounts())
  def limit[R[_]](id: String, credit: Long, ceiling: Long)(using In[Ledger, R]): Either[Refusal, Account] ! R =
    perform[Ledger](Limit(id, credit, ceiling))
  def hold[R[_]](id: String, holder: String)(using In[Ledger, R]): Either[Refusal, Account] ! R =
    perform[Ledger](Hold(id, holder))
  def pay[R[_]](from: String, to: String, amount: Long, memo: String, at: Long, key: String = "")(
    using In[Ledger, R]): Either[Refusal, Transfer] ! R = perform[Ledger](Pay(from, to, amount, memo, at, key))
  def reverse[R[_]](seq: Long, at: Long)(using In[Ledger, R]): Either[Refusal, Transfer] ! R =
    perform[Ledger](Reverse(seq, at))
  def vouch[R[_]](voucher: String, newcomer: String, amount: Long)(using In[Ledger, R])
    : Either[Refusal, Account] ! R = perform[Ledger](Vouch(voucher, newcomer, amount))
  def release[R[_]](newcomer: String)(using In[Ledger, R]): Either[Refusal, Account] ! R =
    perform[Ledger](Release(newcomer))
  def journal[R[_]](account: Option[String] = None)(using In[Ledger, R]): Vector[Transfer] ! R =
    perform[Ledger](Journal(account))
