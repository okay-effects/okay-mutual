package okay.mutual.chain

import okay.scala3.*

/** `Signer` BY THE JDK: the laws every handler keeps */
class SignerJdkSuite extends munit.FunSuite:
  test("the JDK signs and checks as the protocol says, and takes what Web Crypto signed") {
    for (law, holds) <- runIsolated(SignerJdk.run(Laws.program[Pure + Signer])) do assert(holds, law)
  }
