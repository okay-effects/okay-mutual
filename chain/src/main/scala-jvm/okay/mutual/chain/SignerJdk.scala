package okay.mutual.chain

import okay.scala3.*

import java.math.BigInteger
import java.security.{AlgorithmParameters, KeyFactory, KeyPairGenerator, Signature}
import java.security.interfaces.ECPublicKey
import java.security.spec.{ECGenParameterSpec, ECParameterSpec, ECPoint, ECPublicKeySpec, PKCS8EncodedKeySpec}

/** `Signer` BY THE JDK: P-256, signatures as `r` and `s` (IEEE P1363), the public key as its uncompressed point */
object SignerJdk:
  private val curve: ECParameterSpec =
    val params = AlgorithmParameters.getInstance("EC")
    params.init(ECGenParameterSpec("secp256r1"))
    params.getParameterSpec(classOf[ECParameterSpec])

  /** 32 bytes of a coordinate, big-endian */
  private def fixed(n: BigInteger): Array[Byte] =
    val raw = n.toByteArray.dropWhile(_ == 0)
    Array.fill[Byte](32 - raw.length)(0) ++ raw

  private def point(key: ECPublicKey): String =
    B64.encode(4.toByte +: (fixed(key.getW.getAffineX) ++ fixed(key.getW.getAffineY)))

  private def publicKey(pub: String): Option[java.security.PublicKey] = B64.decode(pub).filter(b =>
    b.length == 65 && b(0) == 4).flatMap(b => scala.util.Try(KeyFactory.getInstance("EC").generatePublic(
      ECPublicKeySpec(ECPoint(BigInteger(1, b.slice(1, 33)), BigInteger(1, b.slice(33, 65))), curve))).toOption)

  def generate(): Pair =
    val gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(ECGenParameterSpec("secp256r1"))
    val pair = gen.generateKeyPair()
    Pair(point(pair.getPublic.asInstanceOf[ECPublicKey]), B64.encode(pair.getPrivate.getEncoded))

  def sign(secret: String, text: String): String =
    val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(B64.decode(secret).get))
    val s = Signature.getInstance("SHA256withECDSAinP1363Format")
    s.initSign(key)
    s.update(text.getBytes("UTF-8"))
    B64.encode(s.sign())

  def verify(pub: String, text: String, sig: String): Boolean =
    (publicKey(pub), B64.decode(sig).filter(_.length == 64)) match
      case (Some(key), Some(bytes)) => scala.util.Try {
          val s = Signature.getInstance("SHA256withECDSAinP1363Format")
          s.initVerify(key)
          s.update(text.getBytes("UTF-8"))
          s.verify(bytes)
        }.getOrElse(false)
      case _ => false

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Signer, R, Rest]): A ! Rest =
    handle(p, Handler.general[Signer, Rest, A, A](pure)([X] => (op: Signer[X], k: X => A ! Rest) => op match
      case Signer.Generate() => k(generate())
      case Signer.Sign(secret, text) => k(sign(secret, text))
      case Signer.Verify(pub, text, sig) => k(verify(pub, text, sig))))
