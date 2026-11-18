package okay.mutual.chain

import okay.scala3.*

/** SIGNATURES IN A TEST OF THE RULES: no curve — a key `P<name>`, its secret `<name>`, a signature the hash of the
 * secret and the text. The same on every platform, and synchronous */
object Fake:
  def pair(name: String): Pair = Pair("P" + name, name)
  private def sig(secret: String, text: String): String = Sha256.hex(secret + "\n" + text)

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Signer, R, Rest]): A ! Rest =
    var made = 0
    handle(p, Handler.general[Signer, Rest, A, A](pure)([X] => (op: Signer[X], k: X => A ! Rest) => op match
      case Signer.Generate() =>
        made += 1
        k(pair(s"made$made"))
      case Signer.Sign(secret, text) => k(sig(secret, text))
      case Signer.Verify(pub, text, s) => k(pub.startsWith("P") && s == sig(pub.drop(1), text))))

  type W[X] = (Pure + Signer)[X]
  def apply[A](p: A ! W): A = runIsolated(run(p))

/** AN ACCOUNT BEING WRITTEN in a test: its records so far, the next one signed by whom the test says */
final class Writer(val node: Pair, first: Pair, of: String = "business", start: Long = 1000L):
  var records: Vector[Signed] = Vector.empty
  var state: Option[State] = None
  var now: Long = start
  add(Record("", 1, "", now, Body.Open(node.pub, of, first.pub, "PLN")), node, first)

  def id: String = state.get.id
  def next(body: Body): Record = Record(id, state.get.seq + 1, state.get.head, now, body)

  /** the record signed by `by`, checked as the chain's next: kept when it is, the fault answered when not */
  def add(r: Record, by: Pair*): Either[Fault, State] =
    val signed = Fake(Check.signed[Fake.W](r, by*))
    val stepped = Fake(Check.step[Fake.W](state, signed))
    stepped.foreach { s =>
      records :+= signed
      state = Some(s)
    }
    stepped
  /** a record of the node alone */
  def node(body: Body): Either[Fault, State] = add(next(body), node)
  /** a record of a holder, co-signed by the node */
  def by(holder: Pair)(body: Body): Either[Fault, State] = add(next(body), node, holder)
