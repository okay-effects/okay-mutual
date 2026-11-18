package okay.mutual.chain

import okay.scala3.*

/** a key pair: the public half as a record names it (the uncompressed P-256 point, base64url), the secret half
 * (PKCS#8, base64url) — a node's, a test's; a wallet's secret never leaves its device and is no text at all */
final case class Pair(pub: String, secret: String)

/**
 * SIGNATURES (specs/protocol.md "Keys"): ECDSA on P-256 with SHA-256, a signature its `r` and `s`, 64 bytes,
 * base64url — what Web Crypto and a phone's secure hardware make. An effect, because a browser signs and checks
 * asynchronously; its handlers: the JDK's (`SignerJdk`), Web Crypto's (`SignerWeb`)
 */
enum Signer[X]:
  case Generate() extends Signer[Pair]
  case Sign(secret: String, text: String) extends Signer[String]
  /** whether `sig` is `pub`'s signature of the text; false for anything malformed */
  case Verify(pub: String, text: String, sig: String) extends Signer[Boolean]

object Signer:
  def generate[R[_]](using In[Signer, R]): Pair ! R = perform[Signer](Generate())
  def sign[R[_]](secret: String, text: String)(using In[Signer, R]): String ! R = perform[Signer](Sign(secret, text))
  def verify[R[_]](pub: String, text: String, sig: String)(using In[Signer, R]): Boolean ! R =
    perform[Signer](Verify(pub, text, sig))

/** THE CHECKS THAT NEED SIGNATURES: a record's signers, a whole chain, a fork's proof */
object Check:
  /** a record signed by each of the pairs, as it travels */
  def signed[R[_]](r: Record, by: Pair*)(using In[Signer, R]): Signed ! R =
    val text = Record.text(r)
    by.foldLeft(pure[Vector[(String, String)], R](Vector.empty))((acc, p) => acc.flatMap(sigs =>
      Signer.sign[R](p.secret, text).map(sig => sigs :+ (p.pub -> sig)))).map(Signed(text, _))

  /** the keys whose signatures over the record's text hold */
  def signers[R[_]](s: Signed)(using In[Signer, R]): Set[String] ! R =
    s.sigs.foldLeft(pure[Set[String], R](Set.empty))((acc, ks) => acc.flatMap(ok =>
      Signer.verify[R](ks._1, s.text, ks._2).map(v => if v then ok + ks._1 else ok)))

  /** the next record of an account, its signatures checked */
  def step[R[_]](state: Option[State], s: Signed)(using In[Signer, R]): Either[Fault, State] ! R =
    signers[R](s).map(Chain.step(state, s.text, _))

  /** a whole chain from its opening record: the account it makes, or the first fault and where */
  def chain[R[_]](records: Vector[Signed])(using In[Signer, R]): Either[(Int, Fault), State] ! R =
    records.zipWithIndex.foldLeft(pure[Either[(Int, Fault), Option[State]], R](Right(None)))((acc, si) =>
      acc.flatMap {
        case Left(f) => pure(Left(f))
        case Right(state) => step[R](state, si._1).map(_.fold(f => Left(si._2 -> f), s => Right(Some(s))))
      }).map(_.flatMap(_.toRight(0 -> Fault.Malformed)))

  /**
   * A FORK'S PROOF: two different records in the same place of the same account. Answered: the keys that signed
   * both. The node's key among them proves the node ordered two histories; a holder's alone, that the holder signed
   * two and the node took at most one. Empty: the two prove nothing. Nobody needs the chain to check it
   */
  def fork[R[_]](a: Signed, b: Signed)(using In[Signer, R]): Set[String] ! R =
    (Record.parse(a.text), Record.parse(b.text)) match
      case (Some(x), Some(y)) if a.text != b.text && x.account.nonEmpty && x.account == y.account && x.seq == y.seq =>
        signers[R](a).flatMap(sa => signers[R](b).map(sa.intersect))
      case _ => pure(Set.empty)
