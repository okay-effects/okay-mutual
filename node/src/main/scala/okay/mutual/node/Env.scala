package okay.mutual.node

import okay.effects.json.{Descent, Json}
import okay.scala3.*
import okay.scala3.std.Env
import okay.serverless.Storage
import okay.serverless.cloud.{Request, Response}

/**
 * A NODE'S OWN (specs/protocol.md "A node"): its key pair (`NODE_PUB`, the secret `NODE_KEY`), its unit and name,
 * and its policy of joining — `JOIN=open`: whoever asks is opened an account with the default limits; anything
 * else: only those the community's holders admit
 */
final case class NodeEnv(pub: String, secret: String, unit: String, name: String, open: Boolean, credit: Long,
                         ceiling: Long, setup: String)

object NodeEnv:
  def read[R[_]](using In[Env, R]): NodeEnv ! R =
    def v(name: String) = Env.variable[R](name).map(_.getOrElse("").trim)
    def s(name: String) = Env.secret[R](name).map(_.getOrElse("").trim)
    def n(text: String) = text.toLongOption.getOrElse(0L)
    for
      pub <- v("NODE_PUB"); secret <- s("NODE_KEY"); unit <- v("NODE_UNIT"); name <- v("NODE_NAME")
      join <- v("JOIN"); credit <- v("DEFAULT_CREDIT"); ceiling <- v("DEFAULT_CEILING"); setup <- s("SETUP_TOKEN")
    yield NodeEnv(pub, secret, if unit.isEmpty then "PLN" else unit, name, join == "open", n(credit), n(ceiling),
      setup)

/** answers and stored values, the node's way */
object Http:
  /** a node is asked from any page — a wallet works with every node — and nothing here trusts a cookie: who asks is
   * known by a signature alone, so every origin may ask */
  val cors: Map[String, String] = Map("Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Headers" -> "content-type, x-setup-token", "Access-Control-Allow-Methods" -> "GET, POST")
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
