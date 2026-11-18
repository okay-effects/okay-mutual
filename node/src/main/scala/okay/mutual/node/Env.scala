package okay.mutual.node

import okay.effects.json.{Descent, Json}
import okay.scala3.*
import okay.scala3.std.Env
import okay.serverless.Storage
import okay.serverless.cloud.{Request, Response}

/**
 * A NODE'S OWN (specs/protocol.md "A node"): its place in its hub (`slug`), its key pair, its unit and name, and its
 * policy of joining — `open`: whoever asks is opened an account with the default limits; else only those the
 * community's holders admit. Data, made when the node is created and kept in the node's own directory: a deployment
 * holds as many nodes as people make
 */
final case class NodeEnv(slug: String, pub: String, secret: String, unit: String, name: String, open: Boolean,
                         credit: Long, ceiling: Long, setup: String)

object NodeEnv:
  def write(e: NodeEnv): Json.Obj = Json.obj("slug" -> Json.str(e.slug), "pub" -> Json.str(e.pub),
    "secret" -> Json.str(e.secret), "unit" -> Json.str(e.unit), "name" -> Json.str(e.name),
    "open" -> Json.bool(e.open), "credit" -> Json.num(e.credit), "ceiling" -> Json.num(e.ceiling),
    "setup" -> Json.str(e.setup))
  def read(j: Json): Option[NodeEnv] = scala.util.Try(NodeEnv(j("slug").string, j("pub").string, j("secret").string,
    j("unit").string, j("name").string, j("open").boolean, j("credit").long, j("ceiling").long, j("setup").string))
    .toOption

/** A HUB'S OWN: whether anyone may make a node here (`HUB_OPEN=1`) or only who has the hub's token (`HUB_TOKEN`), how
 * many one address may make a day (`HUB_PER_DAY`, 3) and how many there may be in all (`HUB_MAX`, 500) */
final case class HubEnv(open: Boolean, token: String, perDay: Int, max: Int)

object HubEnv:
  def read[R[_]](using In[Env, R]): HubEnv ! R =
    def v(name: String) = Env.variable[R](name).map(_.getOrElse("").trim)
    for
      open <- v("HUB_OPEN"); token <- Env.secret[R]("HUB_TOKEN").map(_.getOrElse("").trim)
      perDay <- v("HUB_PER_DAY"); max <- v("HUB_MAX")
    yield HubEnv(open == "1", token, perDay.toIntOption.getOrElse(3), max.toIntOption.getOrElse(500))

/** answers and stored values, the node's way */
object Http:
  /** a node is asked from any page — a wallet works with every node — and nothing here trusts a cookie: who asks is
   * known by a signature alone, so every origin may ask */
  val cors: Map[String, String] = Map("Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Headers" -> "content-type, x-setup-token, x-hub-token", "Access-Control-Allow-Methods" -> "GET, POST")
  /** an object's answer given to whoever asked: the headers that let any origin ask, ONCE — an answer that came
   * through an object carries them already, in whatever case the platform gave them back, and a browser refuses
   * `Access-Control-Allow-Origin: *, *` */
  def open(r: Response): Response = r.copy(headers =
    r.headers.filterNot((k, _) => k.toLowerCase.startsWith("access-control-")) ++ cors)
  private val headers = Map("Content-Type" -> "application/json; charset=utf-8", "Cache-Control" -> "no-store") ++ cors
  def json(j: Json, status: Int = 200): Response = Response(status, headers, Json.print(j))
  def refused(why: String, status: Int = 400, more: (String, Json)*): Response =
    json(Json.Obj(("error" -> Json.str(why)) +: more.toVector), status)
  def body(r: Request): Json = Descent.parse(r.body).toOption.getOrElse(Json.Null)
  /** an answer's JSON (null when it is none) */
  def answer(r: Response): Json = Descent.parse(r.body).toOption.getOrElse(Json.Null)
  def post(path: String, j: Json): Request = Request.post(path, Json.print(j))

  def read[R[_]](key: String)(using In[Storage, R]): Option[Json] ! R =
    perform[Storage](Storage.Read(List(key))).map(_.get(key).flatMap(v => Descent.parse(v.value).toOption))
  /** the values under a prefix, in the order of their keys */
  def all[R[_]](prefix: String)(using In[Storage, R]): Vector[(String, Json)] ! R =
    perform[Storage](Storage.Keys(prefix)).flatMap(keys => perform[Storage](Storage.Read(keys)).map(m =>
      keys.sorted.toVector.flatMap(k => m.get(k).flatMap(v => Descent.parse(v.value).toOption).map(k -> _))))
