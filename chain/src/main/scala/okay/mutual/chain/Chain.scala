package okay.mutual.chain

/** what a holder's key spent, and when: the base of its limit a period */
final case class Spent(key: String, at: Long, amount: Long)

/** a payment one holder proposed, waiting for another's `send` of the same: the hash of its record, who, what */
final case class Proposal(hash: String, by: String, to: String, amount: Long, memo: String, name: String, at: Long)

/**
 * AN ACCOUNT AS ITS CHAIN MAKES IT (specs/protocol.md "An account's chain"): never stored as the truth — the fold of
 * the records, the same in a node, in a wallet, in an auditor's script. `id`: the hash of its opening record;
 * `head`: the hash of its last. `sent`: its sends not bounced, by their hash; `lends`: the lines it lent, by the
 * hash of their `vouch`; `spent`: the holders' spending still inside a limit's window; `recovering`: a recovery
 * announced and not yet in force — the key and the time from which it holds the account; `two`: above this amount a
 * payment needs two holders (0: one); `proposed`: the payments that wait for their second
 */
final case class State(id: String, node: String, of: String, unit: String, seq: Long, head: String, at: Long,
                       credit: Long = 0L, ceiling: Long = 0L, balance: Long = 0L, vouched: Long = 0L,
                       voucher: Option[String] = None, lent: Long = 0L, grants: Vector[Grant] = Vector.empty,
                       sent: Map[String, Long] = Map.empty, names: Set[String] = Set.empty,
                       lends: Map[String, Long] = Map.empty, spent: Vector[Spent] = Vector.empty,
                       recovering: Option[(String, Long)] = None, two: Long = 0L,
                       proposed: Vector[Proposal] = Vector.empty):
  /** the lowest balance it may reach: its own line, what was lent to it, less what it lent */
  def floor: Long = -(credit + vouched - lent)
  def grant(key: String): Option[Grant] = grants.find(_.key == key)

/** why a record is not the next of a chain */
enum Fault:
  /** not a record of this version */
  case Malformed
  /** not this account's, not the next number, not after the head, or earlier than the record before */
  case Place(what: String)
  /** no valid signature of the node, or of a holder that may */
  case Unsigned(who: String)
  /** a holder's key that may not do this */
  case Forbidden(what: String)
  case Amount
  /** below the floor by `short` */
  case Credit(short: Long)
  /** above the ceiling by `over` */
  case Ceiling(over: Long)
  /** over the holder's limit: `payment` or `period` */
  case Limit(what: String)
  /** a payment of this name is in the chain */
  case Named
  /** no such record to bounce or return */
  case Unknown(ref: String)

/**
 * THE FOLD AND ITS RULES, pure. `step` takes the account so far, a record's text and the keys whose signatures over
 * that text were checked (`Check` does that, by `Signer`), and answers the account after it — or why not.
 *
 * Every record needs the node's signature: the node orders the chain. A debit and a change of holders need a
 * holder's too (`send`; `vouch`, `grant`, `revoke` by one that may manage): nobody moves a member's credit
 * without its key. A credit and a
 * rule are the node's alone (`receive`, `bounce`, `limits`, `vouched`, `release`, `released`, `recover`,
 * `recovered`)
 */
object Chain:
  /** how long a proposed payment waits for its second holder, and how many may wait at a time */
  val life: Long = 7L * 86400000L
  val most = 20
  private def waiting(s: State, now: Long): Vector[Proposal] = s.proposed.filter(_.at > now - life)

  def step(state: Option[State], text: String, signers: Set[String]): Either[Fault, State] =
    Record.parse(text).toRight(Fault.Malformed).flatMap(r => state match
      case None => opened(r, Sha256.hex(text), signers)
      case Some(s) => placed(s, r, signers).flatMap(_ => said(s, r, Sha256.hex(text), signers))
        .map(next => next.copy(seq = r.seq, head = Sha256.hex(text), at = r.at)))

  private def opened(r: Record, hash: String, signers: Set[String]): Either[Fault, State] = r.body match
    case Body.Open(node, of, key, unit) =>
      if r.account.nonEmpty || r.prev.nonEmpty || r.seq != 1 then Left(Fault.Place("open"))
      else if !signers(node) then Left(Fault.Unsigned("node"))
      else if !signers(key) then Left(Fault.Unsigned("holder"))
      else Right(State(hash, node, of, unit, 1, hash, r.at, grants = Vector(Grant(key, spend = true, manage = true))))
    case _ => Left(Fault.Place("open"))

  private def placed(s: State, r: Record, signers: Set[String]): Either[Fault, Unit] =
    if r.account != s.id then Left(Fault.Place("account"))
    else if r.seq != s.seq + 1 then Left(Fault.Place("seq"))
    else if r.prev != s.head then Left(Fault.Place("prev"))
    else if r.at < s.at then Left(Fault.Place("time"))
    else if !signers(s.node) then Left(Fault.Unsigned("node"))
    else Right(())

  /** the holder that signed: the first of the account's grants whose key is among the signers */
  private def holder(s: State, signers: Set[String]): Either[Fault, Grant] =
    s.grants.find(g => signers(g.key)).toRight(Fault.Unsigned("holder"))

  /** the spending still inside some limit's window at `now` */
  private def kept(s: State, now: Long): Vector[Spent] =
    val longest = s.grants.map(_.per).maxOption.getOrElse(0L)
    s.spent.filter(_.at > now - longest)

  /** a debit of `amount` by the holder `g` at `at`: within its limits and the account's floor */
  private def debit(s: State, g: Grant, amount: Long, at: Long): Either[Fault, State] =
    if !g.spend then Left(Fault.Forbidden("spend"))
    else if amount <= 0 then Left(Fault.Amount)
    else if g.payment > 0 && amount > g.payment then Left(Fault.Limit("payment"))
    else if g.period > 0 && s.spent.filter(x => x.key == g.key && x.at > at - g.per).map(_.amount).sum + amount >
      g.period then Left(Fault.Limit("period"))
    else Right(s.copy(spent = kept(s, at) :+ Spent(g.key, at, amount)))

  private def said(s: State, r: Record, hash: String, signers: Set[String]): Either[Fault, State] = r.body match
    case _: Body.Open => Left(Fault.Place("open"))

    case Body.Send(to, amount, memo, name) => holder(s, signers).flatMap(g => debit(s, g, amount, r.at)).flatMap { d =>
        if to == s.id then Left(Fault.Forbidden("same"))
        else if name.nonEmpty && s.names(name) then Left(Fault.Named)
        else if s.balance - amount < s.floor then Left(Fault.Credit(s.floor - (s.balance - amount)))
        else
          val paid = d.copy(balance = s.balance - amount, sent = s.sent + (hash -> amount),
            names = if name.isEmpty then s.names else s.names + name)
          // above the account's threshold: only as the second holder of the same payment, proposed by ANOTHER
          if s.two <= 0 || amount <= s.two then Right(paid)
          else holder(s, signers).flatMap(g => waiting(s, r.at).find(p => p.by != g.key && p.to == to &&
            p.amount == amount && p.memo == memo && p.name == name)
            .toRight(Fault.Unsigned("second"))).map(p => paid.copy(proposed = waiting(s, r.at).filterNot(_ == p)))
      }

    // the first holder's half of a payment that needs two: nothing moves, it waits (a week) for another's `send`
    case Body.Propose(to, amount, memo, name) => holder(s, signers).flatMap { g =>
        if !g.spend then Left(Fault.Forbidden("spend"))
        else if amount <= 0 then Left(Fault.Amount)
        else if to == s.id then Left(Fault.Forbidden("same"))
        else if waiting(s, r.at).size >= most then Left(Fault.Forbidden("full"))
        else Right(s.copy(proposed = waiting(s, r.at) :+ Proposal(hash, g.key, to, amount, memo, name, r.at)))
      }

    // the threshold of two holders: by one that may manage — as every rule of an account is its managers' to set
    case Body.Two(above) => holder(s, signers).flatMap { g =>
        if !g.manage then Left(Fault.Forbidden("manage"))
        else if above < 0 then Left(Fault.Amount)
        else Right(s.copy(two = above))
      }

    case Body.Receive(_, _, amount) =>
      if amount <= 0 then Left(Fault.Amount)
      else if s.balance + amount > s.ceiling then Left(Fault.Ceiling(s.balance + amount - s.ceiling))
      else Right(s.copy(balance = s.balance + amount))

    case Body.Bounce(ref, amount) => s.sent.get(ref) match
        case Some(a) if a == amount => Right(s.copy(balance = s.balance + amount, sent = s.sent - ref))
        case _ => Left(Fault.Unknown(ref))

    case Body.Limits(credit, ceiling) =>
      val next = s.copy(credit = credit, ceiling = ceiling)
      if credit < 0 || ceiling < 0 then Left(Fault.Amount)
      else if s.balance < next.floor then Left(Fault.Credit(next.floor - s.balance))
      else Right(next)

    // a grant: by a holder that may manage, never more than the giver has
    case Body.Granted(n) => holder(s, signers).flatMap { g =>
        if !g.manage then Left(Fault.Forbidden("manage"))
        else if s.grant(n.key).isDefined then Left(Fault.Forbidden("held"))
        else if n.payment < 0 || n.period < 0 || n.per < 0 || (n.period > 0) != (n.per > 0) then Left(Fault.Amount)
        else if g.payment > 0 && (n.payment <= 0 || n.payment > g.payment) then Left(Fault.Limit("payment"))
        else if g.period > 0 && (n.period <= 0 || n.period > g.period || n.per < g.per) then
          Left(Fault.Limit("period"))
        else Right(s.copy(grants = s.grants :+ n.copy(under = Some(g.key))))
      }

    // a key taken off with all given under it — but the holders that may manage are equals: one of them given
    // under the key stays, on its own (the first phone is lost, the laptop it once signed in takes it off and
    // goes on), and one always stays
    case Body.Revoke(key) => holder(s, signers).flatMap { g =>
        def under(k: String): Set[String] = s.grants.filter(x => x.under.contains(k) && !x.manage).map(_.key)
          .foldLeft(Set(k))((all, c) => all ++ under(c))
        val gone = under(key)
        val left = s.grants.filterNot(x => gone(x.key)).map(x =>
          if x.under.exists(gone) then x.copy(under = None) else x)
        if !g.manage then Left(Fault.Forbidden("manage"))
        // a recovery announced for this key: cancelled — the account's own holder says it is not theirs
        else if s.recovering.exists(_._1 == key) then Right(s.copy(recovering = None))
        else if s.grant(key).isEmpty then Left(Fault.Unknown(key))
        else if !left.exists(_.manage) then Left(Fault.Forbidden("last"))
        else Right(s.copy(grants = left))
      }

    // RECOVERY (specs/protocol.md "Keys"): announced by the node — one at a time, for a key that holds nothing yet
    // —, in force by a second record no earlier than announced; between the two, any holder that may manage can
    // cancel it by taking the key off
    case Body.Recover(key, after) =>
      if s.recovering.isDefined then Left(Fault.Forbidden("recovering"))
      else if s.grant(key).isDefined then Left(Fault.Forbidden("held"))
      else if after < r.at then Left(Fault.Place("time"))
      else Right(s.copy(recovering = Some(key -> after)))

    case Body.Recovered(key) => s.recovering match
        case Some((k, after)) if k == key && r.at >= after =>
          Right(s.copy(recovering = None, grants = s.grants :+ Grant(key, spend = true, manage = true)))
        case Some((k, _)) if k == key => Left(Fault.Place("time"))
        case _ => Left(Fault.Unknown(key))

    // a line lent: a holder that may manage (it is no payment: no holder's spending limit counts it)
    case Body.Vouch(to, amount) => holder(s, signers).flatMap { g =>
        val lender = s.copy(lent = s.lent + amount, lends = s.lends + (hash -> amount))
        if !g.manage then Left(Fault.Forbidden("manage"))
        else if amount <= 0 then Left(Fault.Amount)
        else if to == s.id then Left(Fault.Forbidden("same"))
        else if s.balance < lender.floor then Left(Fault.Credit(lender.floor - s.balance))
        else Right(lender)
      }

    case Body.Vouched(from, _, amount) =>
      if amount <= 0 then Left(Fault.Amount)
      else if s.voucher.exists(_ != from) then Left(Fault.Forbidden("vouched"))
      else Right(s.copy(voucher = Some(from), vouched = s.vouched + amount))

    case Body.Release(amount) =>
      val free = s.copy(vouched = s.vouched - amount, voucher = if amount == s.vouched then None else s.voucher)
      if amount <= 0 || amount > s.vouched then Left(Fault.Amount)
      else if s.balance < free.floor then Left(Fault.Credit(free.floor - s.balance))
      else Right(free)

    case Body.Released(ref, amount) => s.lends.get(ref) match
        case Some(a) if amount > 0 && amount <= a => Right(s.copy(lent = s.lent - amount,
            lends = if amount == a then s.lends - ref else s.lends + (ref -> (a - amount))))
        case _ => Left(Fault.Unknown(ref))
