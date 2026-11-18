package okay.mutual.host

import okay.mutual.chain.{Pair, SignerJdk}
import okay.mutual.node.NodeApp
import okay.serverless.host.{Host, HostConfig}

import java.nio.file.{Files, Path, Paths}

/**
 * A NODE ON ONE MACHINE (docs/run-a-node.md): one JVM process, one directory — okay-serverless' host. The same app
 * and objects as on Cloudflare. Started the first time it makes the node's key (kept in the directory, `node.key`,
 * for the machine's owner alone) and a setup token (`setup.token`), said once: with it the founder's wallet opens
 * the community's own account. What the environment may say:
 *
 *   NODE_DIR   where everything is kept (default `node-data`)      PORT, HOST   where it listens (8787, 127.0.0.1)
 *   NODE_NAME  the community's name                                NODE_UNIT    its unit (default `PLN`)
 *   JOIN       `open`: anyone may open an account                  DEFAULT_CREDIT, DEFAULT_CEILING  a new member's
 *   SITE       the site's directory to serve (default `target/site`: `sbt site`)       limits, in hundredths
 */
object Serve:
  private def kept(file: Path)(make: => String): String =
    if Files.exists(file) then Files.readString(file).trim else
      val made = make
      Files.createDirectories(file.getParent)
      Files.writeString(file, made + "\n")
      scala.util.Try(Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString(
        "rw-------")))
      made

  def start(env: Map[String, String]): Host =
    val dir = Paths.get(env.getOrElse("NODE_DIR", "node-data")).toAbsolutePath
    // the pair as one line: the public half, a space, the secret half
    val pair = kept(dir.resolve("node.key")) { val p = SignerJdk.generate(); s"${p.pub} ${p.secret}" }.split(' ') match
      case Array(pub, secret) => Pair(pub, secret)
      case _ => throw IllegalStateException(s"${dir.resolve("node.key")} is not a node's key")
    val fresh = !Files.exists(dir.resolve("setup.token"))
    val token = kept(dir.resolve("setup.token")) {
      val bytes = new Array[Byte](18)
      java.security.SecureRandom().nextBytes(bytes)
      okay.mutual.chain.B64.encode(bytes)
    }
    val site = Paths.get(env.getOrElse("SITE", "target/site")).toAbsolutePath
    val vars = Map("NODE_PUB" -> pair.pub, "NODE_KEY" -> pair.secret, "SETUP_TOKEN" -> token,
      "NODE_NAME" -> env.getOrElse("NODE_NAME", "A community"), "NODE_UNIT" -> env.getOrElse("NODE_UNIT", "PLN"),
      "JOIN" -> env.getOrElse("JOIN", ""), "DEFAULT_CREDIT" -> env.getOrElse("DEFAULT_CREDIT", "0"),
      "DEFAULT_CEILING" -> env.getOrElse("DEFAULT_CEILING", "0"))
    val host = Host.start(NodeApp, NodeApp.actors, HostConfig(dir = dir,
      port = env.get("PORT").flatMap(_.toIntOption).getOrElse(8787), host = env.getOrElse("HOST", "127.0.0.1"),
      static = Option.when(Files.isDirectory(site))(site), vars = vars))
    System.err.println(s"the node '${vars("NODE_NAME")}' is up; its data: $dir; its key: ${pair.pub}")
    if fresh then System.err.println(s"its setup token, for the founder's wallet (Set up this community): $token")
    host

  def main(args: Array[String]): Unit =
    start(sys.env)
    Thread.currentThread.join()
