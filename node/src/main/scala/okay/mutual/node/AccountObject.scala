package okay.mutual.node

import okay.effects.json.Json
import okay.mutual.chain.*
import okay.scala3.*
import okay.scala3.std.Clock
import okay.serverless.Durable
import okay.serverless.cloud.{Actor, InActor, Objects, Request, Response}

/**
 * AN ACCOUNT'S OBJECT (specs/protocol.md "A payment"): one an account, named by the account's id, holding its chain
 * and nothing of any other account — so a node grows by accounts, never by one bigger ledger.
 *
 *   acct/env                the account's node, as it was when the account was opened (its key pair, its place)
 *   acct/state              the account as its chain makes it (kept so as not to fold again; the chain is the truth)
 *   acct/rec/<seq>          a record as it travels: its text and signatures
 *   acct/name/<name>        the number of the record a named payment made
 *   acct/flight/<hash>      a `send` or a `vouch` of this account not yet taken or refused by its receiver
 *   acct/got/<ref>          a record of another account delivered here (once)
 *   acct/memo/<hash>        what a payment is for, beside the chain (its record carries the memo's hash only):
 *                           the sender's words, kept at both ends, erasable without breaking either chain
 *
 * The node's side of every record is its signature: it orders the chain and says its rules allowed the record. A
 * record is the next only if it names the head before it, so two requests that race cannot both be appended.
 *
 * Only the node's app calls it (`/_acct/…`); what a member's wallet asks goes through the app
 */
object AccountObject extends Actor:
  val namespace = "ACCOUNTS"
  private val scope = "acct"
  private val stateKey = s"$scope/state"
  private val envKey = s"$scope/env"
  private def recKey(seq: Long) = f"$scope/rec/$seq%012d"
  private def nameKey(name: String) = s"$scope/name/$name"
  private def flightKey(hash: String) = s"$scope/flight/$hash"
  private def gotKey(ref: String) = s"$scope/got/$ref"
  private def memoKey(hash: String) = s"$scope/memo/$hash"
  /** how far a member's record may say its time is from the node's */
  val skew: Long = 10 * 60 * 1000L
  /** when a delivery not answered is tried again */
  val retry: Long = 30 * 1000L

  type Live[X] = (InActor + Signer)[X]
  private type Step[X] = (Live + Durable)[X]

  def fetch(name: String, r: Request): Response ! InActor = SignerHere.run(answer(name, r))
  override def alarm(name: String): Unit ! InActor = SignerHere.run(node.flatMap(_.fold(unit[Live])(flights)))

  /** the account's node: kept when the account was opened */
  private def node: Option[NodeEnv] ! Live = Http.read[Live](envKey).map(_.flatMap(NodeEnv.read))

  private def state: Option[State] ! Live = Http.read[Live](stateKey).map(_.flatMap(StateJson.read))

  /** a call: the first record brings the node with it; every other is the account's own node's — and is refused
   * (as an account that is not there) when it comes by another node (`x-node`, the caller's node's key): accounts of
   * different nodes meet only by trust lines, and there are none yet */
  private def answer(name: String, r: Request): Response ! Live =
    if r.method == "POST" && r.path == "/_acct/open" then NodeEnv.read(Http.body(r)("env")) match
      case Some(env) => open(env, name, Http.body(r))
      case None => pure(Http.refused("env"))
    else node.flatMap {
      case Some(env) if r.header("x-node").forall(_ == env.pub) => called(env, r)
      case _ => pure(Http.refused("unknown", 404))
    }

  private def called(env: NodeEnv, r: Request): Response ! Live =
    (r.method, r.path) match
      case ("GET", "/_acct/state") =>
        state.map(_.fold(Http.refused("unknown", 404))(s => Http.json(StateJson.write(s))))
      case ("GET", "/_acct/chain") =>
        val from = r.param("from").flatMap(_.toLongOption).getOrElse(1L)
        Http.all[Live](s"$scope/rec/").flatMap(rs => Http.all[Live](s"$scope/memo/").map { ms =>
          val memos = ms.map((k, v) => k.stripPrefix(s"$scope/memo/") -> v).toMap
          // each record with its memo beside it, when there is one
          Http.json(Json.obj("records" -> Json.Arr(rs.map(_._2).drop((from - 1).toInt.max(0)).map(j =>
            Signed.parse(j).flatMap(s => memos.get(s.hash)).fold(j)(m => Json.Obj(j.record :+ ("memo" -> m)))))))
        })
      case ("POST", "/_acct/append") => Signed.parse(Http.body(r)("record")) match
          case None => pure(Http.refused("record"))
          case Some(signed) => append(env, signed, Http.body(r).field("memo").collect { case Json.Str(m) =>
              m.replaceAll("\\s+", " ").trim.take(200) }.getOrElse(""))
      case ("POST", "/_acct/deliver") => deliver(env, Http.body(r))
      case ("POST", "/_acct/limits") => scala.util.Try((Http.body(r)("credit").long, Http.body(r)("ceiling").long))
          .toOption match
          case None => pure(Http.refused("body"))
          case Some((credit, ceiling)) => byNode(env, Body.Limits(credit, ceiling)).map(done)
      case _ => pure(Response.notFound())

  private def done(result: Either[Fault, (Signed, State)]): Response = result match
    case Right((signed, s)) => Http.json(Json.obj("record" -> Signed.json(signed), "state" -> StateJson.write(s)))
    case Left(fault) => faulted(fault)

  def faulted(fault: Fault): Response =
    val (why, detail): (String, Json) = fault match
      case Fault.Malformed => ("malformed", Json.Null)
      case Fault.Place(what) => ("place", Json.str(what))
      case Fault.Unsigned(who) => ("unsigned", Json.str(who))
      case Fault.Forbidden(what) => ("forbidden", Json.str(what))
      case Fault.Amount => ("amount", Json.Null)
      case Fault.Credit(short) => ("credit", Json.num(short))
      case Fault.Ceiling(over) => ("ceiling", Json.num(over))
      case Fault.Limit(what) => ("limit", Json.str(what))
      case Fault.Named => ("named", Json.Null)
      case Fault.Unknown(ref) => ("unknown", Json.str(ref))
    Http.refused(why, 409, "detail" -> detail)

  /** a record with the node's signature, kept as the chain's next — if the chain's head is still the one the record
   * names: one step, the record, the state and what goes with them together or not at all */
  private def commit(env: NodeEnv, before: Option[State], signed: Signed, next: State, now: Long,
                     more: Unit ! Step = unit[Step]): Either[Fault, (Signed, State)] ! Live =
    Signer.sign[Live](env.secret, signed.text).flatMap { sig =>
      val full = signed.copy(sigs = signed.sigs.filterNot(_._1 == env.pub) :+ (env.pub -> sig))
      Durable.step[Step, Live, Either[Fault, (Signed, State)]](scope)(
        Durable.get[Json, Step](stateKey).map(_.flatMap(StateJson.read)).flatMap { stored =>
          if stored.map(_.head) != before.map(_.head) then pure(Left(Fault.Place("prev")))
          else Durable.put[Json, Step](recKey(next.seq), Signed.json(full))
            .andThen(Durable.put[Json, Step](stateKey, StateJson.write(next))).andThen(more).as(Right((full, next)))
        })
    }.flatMap {
      case Right(kept) => told(env, kept._2).as(Right(kept))
      case left => pure(left)
    }

  /** the node's directory told the account's new head (its signed head covers every account) */
  private def told(env: NodeEnv, s: State): Unit ! Live =
    Objects.call[Live](Directory.namespace, env.slug, Http.post("/_dir/head", Json.obj(
      "account" -> Json.str(s.id), "head" -> Json.str(s.head)))).as(())

  /** THE FIRST RECORD: signed by the member's first key, for this node and its unit; the object's name is its hash */
  private def open(env: NodeEnv, name: String, j: Json): Response ! Live =
    (Signed.parse(j("record")), scala.util.Try((j("credit").long, j("ceiling").long)).toOption) match
      case (Some(signed), Some((credit, ceiling))) => Record.parse(signed.text).map(_.body) match
          case Some(Body.Open(node, _, _, unit)) if node == env.pub && unit == env.unit && signed.hash == name =>
            state.flatMap {
              case Some(_) => pure(Http.refused("taken", 409))
              case None => Clock.now[Live].flatMap(now => Check.signers[Live](signed).flatMap { signers =>
                  Chain.step(None, signed.text, signers + env.pub) match
                    case Left(fault) => pure(faulted(fault))
                    case Right(s) => commit(env, None, signed, s, now,
                      Durable.put[Json, Step](envKey, NodeEnv.write(env))).flatMap {
                        case Left(fault) => pure(faulted(fault))
                        case Right(_) if credit == 0 && ceiling == 0 => state.map(s => done(Right((signed, s.get))))
                        case Right(_) => byNode(env, Body.Limits(credit, ceiling)).map(done)
                      }
                })
            }
          case _ => pure(Http.refused("open"))
      case _ => pure(Http.refused("body"))

  /** A MEMBER'S RECORD (`send`, `vouch`, `grant`, `revoke`): its holder's signature checked, the rules, the node's
   * signature, the chain's next; a `send` or a `vouch` then delivered to its receiver. A named payment sent again:
   * the record it made */
  private def append(env: NodeEnv, signed: Signed, memo: String): Response ! Live = state.flatMap {
    case None => pure(Http.refused("unknown", 404))
    case Some(s) => Record.parse(signed.text) match
        case None => pure(faulted(Fault.Malformed))
        case Some(r) =>
          val name = r.body match
            case Body.Send(_, _, _, n) if n.nonEmpty => Some(n)
            case _ => None
          name.fold(pure[Option[Json], Live](None))(n => Http.read[Live](nameKey(n))).flatMap {
            case Some(seq) => Http.read[Live](recKey(seq.long)).map {
                // the same payment again: its record; another payment under its name: refused
                case Some(kept) if Signed.parse(kept).map(_.text).flatMap(Record.parse).map(_.body) == Some(r.body) =>
                  Http.json(Json.obj("record" -> kept, "state" -> StateJson.write(s)))
                case _ => faulted(Fault.Named)
              }
            case None => Clock.now[Live].flatMap { now =>
                val members = r.body match
                  case _: Body.Send | _: Body.Vouch | _: Body.Granted | _: Body.Revoke => true
                  case _ => false
                if !members then pure(faulted(Fault.Forbidden("node")))
                else if math.abs(r.at - now) > skew then pure(faulted(Fault.Place("time")))
                else Check.signers[Live](signed).flatMap { signers =>
                  Chain.step(Some(s), signed.text, signers + env.pub) match
                    case Left(fault) => pure(faulted(fault))
                    case Right(next) =>
                      val flight: Option[Json] = r.body match
                        // the memo goes with the payment only when it is the one the record's hash names
                        case Body.Send(to, amount, hash, _) => Some(Json.obj("kind" -> Json.str("receive"),
                            "to" -> Json.str(to), "amount" -> Json.num(amount),
                            "memo" -> Json.str(if memo.nonEmpty && Sha256.hex(memo) == hash then memo else "")))
                        case Body.Vouch(to, amount) => Some(Json.obj("kind" -> Json.str("vouched"),
                            "to" -> Json.str(to), "amount" -> Json.num(amount)))
                        case _ => None
                      val more: Unit ! Step = name.fold(unit[Step])(n => Durable.put[Json, Step](nameKey(n),
                        Json.num(next.seq))).andThen(flight.fold(unit[Step])(f => Durable.put[Json, Step](
                        flightKey(next.head), f).andThen(Durable.wakeAt[Step](now + retry)).andThen(
                        f.field("memo").collect { case m @ Json.Str(text) if text.nonEmpty => m }.fold(unit[Step])(m =>
                          Durable.put[Json, Step](memoKey(next.head), m)))))
                      commit(env, Some(s), signed, next, now, more).flatMap {
                        case Left(fault) => pure(faulted(fault))
                        case Right((full, _)) => flight.fold(unit[Live])(f => fly(env, next.head, f)).flatMap(_ =>
                            state.map(after => done(Right((full, after.getOrElse(next))))))
                      }
                }
              }
          }
  }

  /** A RECORD OF THE NODE ALONE, the chain's next whatever it is by then: tried again when another record came
   * between (a few times; requests of one account are few) */
  private def byNode(env: NodeEnv, body: Body, tries: Int = 5): Either[Fault, (Signed, State)] ! Live =
    state.flatMap {
      case None => pure(Left(Fault.Unknown("account")))
      case Some(s) => Clock.now[Live].flatMap { now =>
          val text = Record.text(Record(s.id, s.seq + 1, s.head, now.max(s.at), body))
          Chain.step(Some(s), text, Set(env.pub)) match
            case Left(fault) => pure(Left(fault))
            case Right(next) => commit(env, Some(s), Signed(text, Vector.empty), next, now).flatMap {
                case Left(Fault.Place("prev")) if tries > 1 => byNode(env, body, tries - 1)
                case result => pure(result)
              }
        }
    }

  /** ANOTHER ACCOUNT'S `send` OR `vouch` DELIVERED HERE: taken once (`got`), by this account's rules — a refusal is
   * the answer its sender bounces on */
  private def deliver(env: NodeEnv, j: Json): Response ! Live =
    scala.util.Try((j("kind").string, j("from").string, j("ref").string, j("amount").long)).toOption match
      case None => pure(Http.refused("body"))
      case Some((kind, from, ref, amount)) => Http.read[Live](gotKey(ref)).flatMap {
          case Some(_) => pure(Http.json(Json.obj("taken" -> Json.bool(true))))
          case None =>
            val body = if kind == "vouched" then Body.Vouched(from, ref, amount) else Body.Receive(from, ref, amount)
            byNode(env, body).flatMap {
              case Left(Fault.Unknown("account")) => pure(Http.refused("unknown", 404))
              case Left(fault) => pure(faulted(fault))
              case Right((taken, _)) => Durable.step[Step, Live, Unit](scope)(Durable.put[Json, Step](gotKey(ref),
                  Json.bool(true)).andThen(j.field("memo").collect { case m @ Json.Str(text) if text.nonEmpty => m }
                  .fold(unit[Step])(m => Durable.put[Json, Step](memoKey(taken.hash), m))))
                  .as(Http.json(Json.obj("taken" -> Json.bool(true))))
            }
        }

  /** a flight carried to its receiver: taken — the flight is over; refused, or no such account — the amount back
   * (`bounce`, or `released` for a line); no answer — it stays, for the alarm */
  private def fly(env: NodeEnv, hash: String, f: Json): Unit ! Live = state.flatMap {
    case None => unit[Live]
    case Some(s) =>
      val (kind, to, amount) = (f("kind").string, f("to").string, f("amount").long)
      Objects.call[Live](namespace, to, Http.post("/_acct/deliver", Json.obj("kind" -> Json.str(kind),
        "from" -> Json.str(s.id), "ref" -> Json.str(hash), "amount" -> Json.num(amount),
        "memo" -> f.field("memo").getOrElse(Json.str("")))).copy(headers = Map("x-node" -> List(env.pub),
        "content-type" -> List("application/json")))).flatMap { answer =>
        def landed: Unit ! Live = Durable.step[Step, Live, Unit](scope)(Durable.delete[Step](
          flightKey(hash)))
        if answer.status == 200 then landed
        else if answer.status == 409 || answer.status == 404 then
          byNode(env, if kind == "vouched" then Body.Released(hash, amount) else Body.Bounce(hash, amount))
            .flatMap(_ => landed)
        else unit[Live]
      }
  }

  /** the alarm: every flight still here tried again; another alarm while any is left */
  private def flights(env: NodeEnv): Unit ! Live = Http.all[Live](s"$scope/flight/").flatMap { all =>
    all.foldLeft(unit[Live])((acc, kf) => acc.flatMap(_ => fly(env, kf._1.stripPrefix(s"$scope/flight/"), kf._2)))
      .flatMap(_ => Http.all[Live](s"$scope/flight/")).flatMap(left =>
        if left.isEmpty then unit[Live] else Clock.now[Live].flatMap(now =>
          Durable.step[Step, Live, Unit](scope)(Durable.wakeAt[Step](now + retry))))
  }
