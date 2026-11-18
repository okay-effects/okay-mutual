package okay.mutual.chain

import okay.scala3.*
import okay.scala3.std.Async

/** `Signer` BY THE RUNTIME THE CODE IS IN: here Web Crypto's (the same call as the JVM's, where it is the JDK's) */
object SignerHere:
  def run[R[_], Rest[_], A](p: A ! R)(using Split[Signer, R, Rest], In[Async, Rest]): A ! Rest = SignerWeb.run(p)
