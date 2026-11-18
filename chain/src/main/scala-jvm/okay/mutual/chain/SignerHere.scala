package okay.mutual.chain

import okay.scala3.*
import okay.scala3.std.Async

/** `Signer` BY THE RUNTIME THE CODE IS IN: here the JDK's (the same call as Scala.js's, where it is Web Crypto's) */
object SignerHere:
  def run[R[_], Rest[_], A](p: A ! R)(using Split[Signer, R, Rest], In[Async, Rest]): A ! Rest = SignerJdk.run(p)
