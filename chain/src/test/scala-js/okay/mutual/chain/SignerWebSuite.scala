package okay.mutual.chain

import okay.scala3.*
import okay.scala3.std.Async

import scala.concurrent.ExecutionContext

/** `Signer` BY WEB CRYPTO (here Node's, the same interface as a browser's): the laws every handler keeps */
class SignerWebSuite extends munit.FunSuite:
  given ExecutionContext = Async.defaultContext
  test("Web Crypto signs and checks as the protocol says, and takes what the JDK signed") {
    Async.toFuture(SignerWeb.run(Laws.program[Async + Signer])).map(laws =>
      for (law, holds) <- laws do assert(holds, law))
  }
