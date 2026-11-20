//> using scala 3.3.8
//> using repository https://okay-effects.github.io/okay-simple/maven
//> using dep io.github.okay-effects::okay-effects-json:0.1.1

/**
 * THE HUB'S BACKUP, its operator's (docs/run-a-node.md "Backups"): every community of the hub as its file, from
 * `GET /api/backup` with the hub's token, written to `<dir>/<hub's host>-<UTC time>.json` and read back — every
 * community a file another hub takes (`POST /api/nodes {slug, import}`).
 *
 *   scala-cli run scripts/backup.scala -- [hub address] [dir]
 *
 * The hub's address: the first argument, else `HUB` (default https://okay-mutual.okay-effects.workers.dev); the
 * token: `HUB_TOKEN` in the environment or in `.env`; the directory: the second argument, else `backups/` (ignored
 * by git). A backup holds every community's key pair: kept as a key is, never committed, never sent anywhere.
 */
import okay.effects.json.{Descent, Json}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path, Paths}
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

def fromDotEnv(name: String): Option[String] =
  Seq(Paths.get(".env"), Paths.get("../.env")).find(Files.isRegularFile(_)).flatMap(p =>
    Files.readAllLines(p).toArray.map(_.toString).collectFirst {
      case line if line.trim.startsWith(name + "=") =>
        line.trim.drop(name.length + 1).stripPrefix("\"").stripSuffix("\"")
    })

@main def backup(args: String*): Unit =
  val hub = args.headOption.orElse(sys.env.get("HUB")).getOrElse("https://okay-mutual.okay-effects.workers.dev")
    .stripSuffix("/")
  val token = sys.env.get("HUB_TOKEN").orElse(fromDotEnv("HUB_TOKEN")).filter(_.nonEmpty).getOrElse(
    sys.error("no HUB_TOKEN: in the environment or in .env"))
  val dir = Paths.get(args.lift(1).getOrElse("backups"))
  val answer = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(s"$hub/api/backup"))
    .header("x-hub-token", token).GET().build(), HttpResponse.BodyHandlers.ofString())
  if answer.statusCode != 200 then sys.error(s"the hub answered ${answer.statusCode}: ${answer.body.take(200)}")
  val read = Descent.parse(answer.body).toOption.getOrElse(sys.error("the answer is no JSON"))
  val nodes = read("nodes").list
  // read back before it is kept: each a community's file with its key pair and its accounts
  val sound = nodes.forall(n => scala.util.Try(n("kind").string == "okay-mutual-node" && n("env")("secret").string
    .nonEmpty && n("accounts").record.nonEmpty).getOrElse(false))
  if !sound then sys.error("a community in the answer is not a whole file: nothing written")
  Files.createDirectories(dir)
  val at = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now)
  val file = dir.resolve(s"${URI.create(hub).getHost}-$at.json")
  Files.writeString(file, answer.body)
  try java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString(
    "rw-------")) catch case _: UnsupportedOperationException => ()
  val again = Descent.parse(Files.readString(file)).toOption.map(_("nodes").list.size)
  if !again.contains(nodes.size) then sys.error(s"$file does not read back")
  println(s"$file: ${nodes.size} communities, ${nodes.map(_("accounts").record.size).sum} accounts")
  nodes.foreach(n => println(s"  ${n("env")("slug").string}: ${n("env")("name").string}, " +
    s"${n("accounts").record.size} accounts"))
