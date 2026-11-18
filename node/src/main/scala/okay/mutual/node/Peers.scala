package okay.mutual.node

import okay.effects.json.Json
import okay.mutual.chain.*
import okay.scala3.*
import okay.serverless.cloud.{Fetch, Objects, Request, Response}

/** what a node says of itself to another: its key, its unit, its name */
final case class PeerAbout(pub: String, unit: String, name: String)

/**
 * ANOTHER NODE, AS THIS ONE REACHES IT (specs/protocol.md "Trust lines between nodes"): by its address — a place at
 * this same hub (`/n/<slug>`: reached through its objects, a deployment does not call itself over the network) or a
 * whole address of a node elsewhere (`https://…`: its public calls). What one node asks of another is the same
 * either way, and nothing a stranger could not ask: who it is, the account it keeps for a node, that account's
 * state, a holder's signed record appended, the opening record of its own account at the asker signed
 */
object Peers:
  /** a line's ceiling: owing is no risk to the one who owes, so nothing stops it */
  val unbounded: Long = 100000000000000L
  def local(address: String): Option[String] = Option(address).filter(_.matches("/n/[a-z0-9-]+")).map(_.drop(3))
  def valid(address: String): Boolean = local(address).exists(Hub.slug) || address.matches("https://[^\\s\"'<>]+")

  /** the record by which `holder`'s account at the node `at` is opened: the same text whoever writes it */
  def opening(at: String, holder: String, unit: String): Record =
    Record("", 1, "", 0L, Body.Open(at, "line", holder, unit))

  private def json(r: Response): Json = Http.answer(r)
  private def get[R[_]](address: String, path: String)(using In[Fetch, R]): Response ! R =
    Fetch[R](address, Request.get(path))
  private def post[R[_]](address: String, path: String, body: Json)(using In[Fetch, R]): Response ! R =
    Fetch[R](address, Http.post(path, body).copy(headers = Map("content-type" -> List("application/json"))))
  private def at[R[_]](pub: String, id: String, r: Request)(using In[Objects, R]): Response ! R =
    Objects.call[R](AccountObject.namespace, id, r.copy(headers = r.headers + ("x-node" -> List(pub))))

  def about[R[_]](address: String)(using In[Fetch, R], In[Objects, R]): Option[PeerAbout] ! R = local(address) match
    case Some(slug) => Objects.call[R](Directory.namespace, slug, Request.get("/_dir/env")).map(r =>
        NodeEnv.read(json(r)).filter(_ => r.status == 200).map(e => PeerAbout(e.pub, e.unit, e.name)))
    case None => get[R](address, "/.well-known/okay-mutual.json").map(r => if r.status != 200 then None else
        scala.util.Try(PeerAbout(json(r)("key").string, json(r)("unit").string, json(r)("name").string)).toOption)

  /** the id of the account the peer keeps for the node `asker` */
  def line[R[_]](address: String, asker: String)(using In[Fetch, R], In[Objects, R]): Option[String] ! R =
    (local(address) match
      case Some(slug) => Objects.call[R](Directory.namespace, slug, Request.get(s"/_dir/line?node=$asker"))
      case None => get[R](address, s"/api/line?node=$asker")
    ).map(r => if r.status != 200 then None else scala.util.Try(json(r)("id").string).toOption)

  /** the opening record of the peer's account at `asker`, signed by the peer's key */
  def signedOpening[R[_]](address: String, asker: String)(using In[Fetch, R], In[Objects, R]): Option[Signed] ! R =
    (local(address) match
      case Some(slug) => Objects.call[R](Directory.namespace, slug, Http.post("/_dir/line/sign",
          Json.obj("node" -> Json.str(asker))))
      case None => post[R](address, "/api/line/open", Json.obj("node" -> Json.str(asker)))
    ).map(r => if r.status != 200 then None else Signed.parse(json(r)))

  def state[R[_]](address: String, pub: String, id: String)(using In[Fetch, R], In[Objects, R]): Option[State] ! R =
    (local(address) match
      case Some(_) => at[R](pub, id, Request.get("/_acct/state"))
      case None => get[R](address, s"/api/accounts/$id")
    ).map(r => if r.status == 200 then StateJson.read(json(r)) else None)

  /** a holder's record given to the peer: its answer's status and what it says */
  def append[R[_]](address: String, pub: String, id: String, record: Signed, memo: String)(using In[Fetch, R],
    In[Objects, R]): (Int, Json) ! R =
    val body = Json.obj("record" -> Signed.json(record), "memo" -> Json.str(memo))
    (local(address) match
      case Some(_) => at[R](pub, id, Http.post("/_acct/append", body))
      case None => post[R](address, s"/api/accounts/$id/records", body)
    ).map(r => (r.status, json(r)))
