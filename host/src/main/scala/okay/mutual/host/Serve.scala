package okay.mutual.host

import okay.mutual.node.NodeApp
import okay.serverless.host.{Host, HostConfig}

import java.nio.file.{Files, Paths}

/**
 * A HUB ON ONE MACHINE (docs/run-a-node.md): one JVM process, one directory — okay-serverless' host. The same app
 * and objects as on Cloudflare, the site served beside them: open the app it serves and create a community. Every
 * community's key pair is made with it and kept in the directory's database, for the machine's owner alone. What the
 * environment may say:
 *
 *   NODE_DIR     where everything is kept (default `node-data`)     PORT, HOST   where it listens (8787, 127.0.0.1)
 *   HUB_OPEN     `1` (the default here): whoever reaches this machine makes a community; anything else: only who
 *                has HUB_TOKEN
 *   HUB_PER_DAY  how many communities an address may make a day (default 20 here)    HUB_MAX   in all (500)
 *   SITE         the site's directory to serve (default `target/site`: `sbt site`)
 */
object Serve:
  def start(env: Map[String, String]): Host =
    val dir = Paths.get(env.getOrElse("NODE_DIR", "node-data")).toAbsolutePath
    val site = Paths.get(env.getOrElse("SITE", "target/site")).toAbsolutePath
    val port = env.get("PORT").flatMap(_.toIntOption).getOrElse(8787)
    val vars = Map("HUB_OPEN" -> env.getOrElse("HUB_OPEN", "1"), "HUB_TOKEN" -> env.getOrElse("HUB_TOKEN", ""),
      "HUB_PER_DAY" -> env.getOrElse("HUB_PER_DAY", "20"), "HUB_MAX" -> env.getOrElse("HUB_MAX", "500"))
    val host = Host.start(NodeApp, NodeApp.actors, HostConfig(dir = dir, port = port,
      host = env.getOrElse("HOST", "127.0.0.1"), static = Option.when(Files.isDirectory(site))(site), vars = vars))
    System.err.println(s"the hub is up; its data: $dir")
    System.err.println(if Files.isDirectory(site) then s"open http://127.0.0.1:$port/app/ and create a community"
      else s"no site at $site (sbt site): the hub answers its calls only")
    host

  def main(args: Array[String]): Unit =
    start(sys.env)
    Thread.currentThread.join()
