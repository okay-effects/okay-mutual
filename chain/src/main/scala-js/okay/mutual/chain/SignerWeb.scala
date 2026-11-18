package okay.mutual.chain

import okay.scala3.*
import okay.scala3.std.Async

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

/** `Signer` BY WEB CRYPTO (a browser, a phone's browser, a Worker, Node): P-256, signatures as `r` and `s`, the
 * public key as its raw uncompressed point — the same bytes as the JDK's handler makes and takes */
object SignerWeb:
  private def subtle: js.Dynamic = js.Dynamic.global.crypto.subtle
  private val ecdsa = js.Dynamic.literal(name = "ECDSA", namedCurve = "P-256")
  private val sha256 = js.Dynamic.literal(name = "ECDSA", hash = "SHA-256")

  private def view(bytes: Array[Byte]): Uint8Array =
    val a = new Uint8Array(bytes.length)
    for i <- bytes.indices do a(i) = (bytes(i) & 0xff).toShort
    a
  private def bytes(buffer: js.Any): Array[Byte] =
    val a = new Uint8Array(buffer.asInstanceOf[ArrayBuffer])
    Array.tabulate(a.length)(i => a(i).toByte)
  private def await[T](promise: js.Dynamic): Future[T] = promise.asInstanceOf[js.Promise[T]].toFuture

  def generate()(using ExecutionContext): Future[Pair] =
    for
      pair <- await[js.Dynamic](subtle.generateKey(ecdsa, true, js.Array("sign", "verify")))
      pub <- await[js.Any](subtle.exportKey("raw", pair.publicKey))
      secret <- await[js.Any](subtle.exportKey("pkcs8", pair.privateKey))
    yield Pair(B64.encode(bytes(pub)), B64.encode(bytes(secret)))

  def sign(secret: String, text: String)(using ExecutionContext): Future[String] =
    for
      key <- await[js.Any](subtle.importKey("pkcs8", view(B64.decode(secret).get), ecdsa, false, js.Array("sign")))
      sig <- await[js.Any](subtle.sign(sha256, key, view(text.getBytes("UTF-8"))))
    yield B64.encode(bytes(sig))

  def verify(pub: String, text: String, sig: String)(using ExecutionContext): Future[Boolean] =
    (B64.decode(pub).filter(b => b.length == 65 && b(0) == 4), B64.decode(sig).filter(_.length == 64)) match
      case (Some(point), Some(signature)) =>
        (for
          key <- await[js.Any](subtle.importKey("raw", view(point), ecdsa, false, js.Array("verify")))
          ok <- await[Boolean](subtle.verify(sha256, key, view(signature), view(text.getBytes("UTF-8"))))
        yield ok).recover { case _ => false }
      case _ => Future.successful(false)

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Signer, R, Rest], In[Async, Rest]): A ! Rest =
    given ExecutionContext = Async.defaultContext
    handle(p, Handler.general[Signer, Rest, A, A](pure)([X] => (op: Signer[X], k: X => A ! Rest) => op match
      case Signer.Generate() => Async.fromFuture[Pair, Rest](generate()).flatMap(k)
      case Signer.Sign(secret, text) => Async.fromFuture[String, Rest](sign(secret, text)).flatMap(k)
      case Signer.Verify(pub, text, sig) => Async.fromFuture[Boolean, Rest](verify(pub, text, sig)).flatMap(k)))
