package okay.mutual.chain

import okay.scala3.*

/** RECORDS SIGNED ELSEWHERE, kept: one by the JDK, one by Web Crypto (Node). Each platform's handler must take
 * both — the proof that a node on a server and a wallet in a page mean the same by a signature */
object Vectors:
  val jdk: Signed = Signed("""{"v":1,"account":"","seq":1,"prev":"","at":1795000000000,"kind":"open","body":""" +
    """{"node":"BHvcVgqCeAaR9gEzAkSmoFIF7dycoPPU5RxtH9dJzxyBYxbttxSdhxhhLWWivo_-J5nkunm-_JKoCb_2v45bX40",""" +
    """"of":"personal","key":"BHvcVgqCeAaR9gEzAkSmoFIF7dycoPPU5RxtH9dJzxyBYxbttxSdhxhhLWWivo_-J5nkunm-_JKoCb""" +
    """_2v45bX40","unit":"PLN"}}""", Vector(
    "BHvcVgqCeAaR9gEzAkSmoFIF7dycoPPU5RxtH9dJzxyBYxbttxSdhxhhLWWivo_-J5nkunm-_JKoCb_2v45bX40" ->
      "bmMUwARJ79fwhZ3-KD37dfQf4GcoAbVj-L5UqLWI2VkqTuf-vi4Trfuvpt-9WVjZUE_ZcV60H7bsQ60yAR7j0g"))
  val web: Signed = Signed("""{"v":1,"account":"","seq":1,"prev":"","at":1795000000001,"kind":"open","body":""" +
    """{"node":"BKkRiyPbXHH61og7hwnMs4MIFc2L1ghuzYbja8TmDsURJGd5BNE0q6nLUXMnoISJdMtd_yac0XbdYmPGYo2woXs",""" +
    """"of":"business","key":"BKkRiyPbXHH61og7hwnMs4MIFc2L1ghuzYbja8TmDsURJGd5BNE0q6nLUXMnoISJdMtd_yac0XbdYmPGYo2""" +
    """woXs","unit":"PLN"}}""", Vector(
    "BKkRiyPbXHH61og7hwnMs4MIFc2L1ghuzYbja8TmDsURJGd5BNE0q6nLUXMnoISJdMtd_yac0XbdYmPGYo2woXs" ->
      "h_qfxl7v1ddsFMxytTGVzH1gLzPSQxFHBh_yNzEBv7jR3LwdAe7oij6R3k0t62fFX_GOZbJ-bsNkSOxdoNVGOA"))

/** WHAT EVERY `Signer` MUST DO, as one program: each platform's suite runs it by its own handler and wants every
 * line true */
object Laws:
  def program[R[_]](using In[Signer, R]): Vector[(String, Boolean)] ! R =
    val (jdkKey, jdkSig) = Vectors.jdk.sigs.head
    val (webKey, webSig) = Vectors.web.sigs.head
    for
      a <- Signer.verify[R](jdkKey, Vectors.jdk.text, jdkSig)
      b <- Signer.verify[R](webKey, Vectors.web.text, webSig)
      c <- Signer.verify[R](jdkKey, Vectors.jdk.text + " ", jdkSig)
      d <- Signer.verify[R](webKey, Vectors.jdk.text, jdkSig)
      e <- Signer.verify[R](jdkKey, Vectors.jdk.text, webSig)
      f <- Signer.verify[R]("not a key", Vectors.jdk.text, jdkSig)
      g <- Signer.verify[R](jdkKey, Vectors.jdk.text, "AAAA")
      // a record signed elsewhere opens an account here: its id the hash of its text
      opened <- Check.step[R](None, Vectors.web)
      node <- Signer.generate[R]
      phone <- Signer.generate[R]
      other <- Signer.generate[R]
      sig <- Signer.sign[R](phone.secret, "zażółć gęślą jaźń")
      h <- Signer.verify[R](phone.pub, "zażółć gęślą jaźń", sig)
      i <- Signer.verify[R](phone.pub, "zazolc gesla jazn", sig)
      j <- Signer.verify[R](other.pub, "zażółć gęślą jaźń", sig)
      // a chain with real signatures: opened, given a line, a payment sent
      open <- Check.signed[R](Record("", 1, "", 10, Body.Open(node.pub, "personal", phone.pub, "PLN")), node, phone)
      limits <- Check.signed[R](Record(open.hash, 2, open.hash, 11, Body.Limits(10000, 30000)), node)
      send = Record(open.hash, 3, limits.hash, 12, Body.Send("b", 4000, "", "k1"))
      sent <- Check.signed[R](send, node, phone)
      whole <- Check.chain[R](Vector(open, limits, sent))
      // the same payment signed by a key that holds nothing: no holder
      stolen <- Check.signed[R](send, node, other)
      thief <- Check.chain[R](Vector(open, limits, stolen))
      // the node orders another record in the same place: a fork, its key in the proof
      twin <- Check.signed[R](send.copy(body = Body.Send("c", 9000, "", "k2")), node, phone)
      fork <- Check.fork[R](sent, twin)
    yield Vector("the JDK's signature holds" -> a, "Web Crypto's signature holds" -> b,
      "a changed text is not signed" -> !c, "another key did not sign it" -> !d, "another signature is not its" -> !e,
      "a malformed key signs nothing" -> !f, "a malformed signature is none" -> !g,
      "a record signed elsewhere opens an account" -> opened.exists(s => s.id == Vectors.web.hash && s.seq == 1),
      "what was signed here holds here" -> h, "not for a text that only looks the same" -> !i,
      "not for another key" -> !j,
      "a chain of real signatures makes its account" -> whole.exists(s => s.balance == -4000 && s.seq == 3),
      "a payment by a key that holds nothing is no payment" -> (thief == Left(2 -> Fault.Unsigned("holder"))),
      "a fork is proved against the node and the holder" -> (fork == Set(node.pub, phone.pub)))
