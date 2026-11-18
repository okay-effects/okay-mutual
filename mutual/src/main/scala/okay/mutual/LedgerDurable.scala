package okay.mutual

import okay.effects.json.{Descent, Json}
import okay.scala3.*
import okay.serverless.{Durable, Storage}

/**
 * `Ledger` ON okay-serverless' DURABLE STATE — in the network's object, its SQLite on Cloudflare, memory in tests,
 * the same keys:
 *
 *   mutual/account/<id>     an account as it is now
 *   mutual/line/<seq>       a line of the journal (its number, twelve digits)
 *   mutual/seq              the last number given
 *   mutual/undone/<seq>     the number of the line that reversed this one
 *
 * A change is ONE `Durable.step`: the accounts it moves and the journal's line written together or not at all; a
 * reading is a read of what is committed, no step
 */
object LedgerDurable:
  val scope = "mutual"
  private def accountKey(id: String) = s"$scope/account/$id"
  private def lineKey(seq: Long) = f"$scope/line/$seq%012d"
  private val seqKey = s"$scope/seq"
  private def undoneKey(seq: Long) = s"$scope/undone/$seq"

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Ledger, R, Rest], In[Storage, Rest]): A ! Rest =
    handle(p, Handler.general[Ledger, Rest, A, A](pure)([X] => (op: Ledger[X], k: X => A ! Rest) =>
      answer[Rest, X](op).flatMap(k)))

  // ---- what is committed, read

  private def readAll[R[_]](prefix: String)(using In[Storage, R]): Vector[Json] ! R =
    perform[Storage](Storage.Keys(prefix)).flatMap(keys => perform[Storage](Storage.Read(keys)).map(m =>
      keys.sorted.toVector.flatMap(k => m.get(k).flatMap(v => Descent.parse(v.value).toOption))))

  // ---- a change, one step

  private def account[D[_]: Has[Durable]](id: String): Either[Refusal, Account] ! D =
    Durable.get[Json, D](accountKey(id)).map(_.map(Wire.account).toRight(Refusal.Unknown(id)))

  private def keep[D[_]: Has[Durable]](a: Account): Unit ! D = Durable.put[Json, D](accountKey(a.id), Wire.account(a))

  /** two accounts read, changed by `rule`, kept: the second as it is after */
  private def both[D[_]: Has[Durable]](first: String, second: String)(
    rule: (Account, Account) => Either[Refusal, (Account, Account)]): Either[Refusal, (Account, Account)] ! D =
    account[D](first).flatMap {
      case Left(r) => pure(Left(r))
      case Right(a) => account[D](second).flatMap {
          case Left(r) => pure(Left(r))
          case Right(b) => rule(a, b) match
              case Left(r) => pure(Left(r))
              case Right((a1, b1)) => keep[D](a1).andThen(keep[D](b1)).as(Right((a1, b1)))
        }
    }

  /** a line appended under the next number */
  private def line[D[_]: Has[Durable]](make: Long => Transfer): Transfer ! D =
    Durable.get[Long, D](seqKey).flatMap { last =>
      val t = make(last.getOrElse(0L) + 1)
      Durable.put[Long, D](seqKey, t.seq).andThen(Durable.put[Json, D](lineKey(t.seq), Wire.transfer(t))).as(t)
    }

  private def answer[R[_], X](op: Ledger[X])(using In[Storage, R]): X ! R =
    type D[Y] = (R + Durable)[Y]
    def step[A](p: A ! D): A ! R = Durable.step[D, R, A](scope)(p)
    op match
      case Ledger.Open(a) => Rules.opened(a) match
          case Left(r) => pure(Left(r))
          case Right(fresh) => step[Either[Refusal, Account]](Durable.get[Json, D](accountKey(fresh.id)).flatMap {
              case Some(_) => pure(Left(Refusal.Taken(fresh.id)))
              case None => keep[D](fresh).as(Right(fresh))
            })
      case Ledger.Find(id) => perform[Storage](Storage.Read(List(accountKey(id)))).map(_.get(accountKey(id))
          .flatMap(v => Descent.parse(v.value).toOption).map(Wire.account))
      case Ledger.Accounts() => readAll[R](s"$scope/account/").map(_.map(Wire.account))
      case Ledger.Limit(id, credit, ceiling) => step[Either[Refusal, Account]](account[D](id).flatMap {
          case Left(r) => pure(Left(r))
          case Right(a) => Rules.limited(a, credit, ceiling) match
              case Left(r) => pure(Left(r))
              case Right(next) => keep[D](next).as(Right(next))
        })
      case Ledger.Pay(from, to, amount, memo, at) => step[Either[Refusal, Transfer]](
          both[D](from, to)(Rules.paid(_, _, amount)).flatMap {
            case Left(r) => pure(Left(r))
            case Right(_) => line[D](Transfer(_, from, to, amount, memo, at)).map(Right(_))
          })
      case Ledger.Reverse(seq, at) => step[Either[Refusal, Transfer]](
          Durable.get[Json, D](lineKey(seq)).map(_.map(Wire.transfer)).flatMap {
            case None => pure(Left(Refusal.Invalid("seq")))
            case Some(t) if t.reverses.isDefined => pure(Left(Refusal.Reversed(seq)))
            case Some(t) => Durable.get[Long, D](undoneKey(seq)).flatMap {
                case Some(_) => pure(Left(Refusal.Reversed(seq)))
                // the amount back, whatever the limits say: a correction, not a deal
                case None => both[D](t.to, t.from)((a, b) => Right((a.copy(balance = a.balance - t.amount),
                    b.copy(balance = b.balance + t.amount)))).flatMap {
                    case Left(r) => pure(Left(r))
                    case Right(_) => line[D](Transfer(_, t.to, t.from, t.amount, s"reverses #$seq", at, Some(seq)))
                        .flatMap(back => Durable.put[Long, D](undoneKey(seq), back.seq).as(Right(back)))
                  }
              }
          })
      case Ledger.Vouch(voucher, newcomer, amount) => step[Either[Refusal, Account]](
          both[D](voucher, newcomer)(Rules.vouched(_, _, amount)).map(_.map(_._2)))
      case Ledger.Release(newcomer) => step[Either[Refusal, Account]](account[D](newcomer).flatMap {
          case Left(r) => pure(Left(r))
          case Right(n) => n.voucher match
              case None => pure(Left(Refusal.Vouched(newcomer)))
              case Some(v) => both[D](v, newcomer)(Rules.released).map(_.map(_._2))
        })
      case Ledger.Journal(of) => readAll[R](s"$scope/line/").map(_.map(Wire.transfer)
          .filter(t => of.forall(id => t.from == id || t.to == id)))
