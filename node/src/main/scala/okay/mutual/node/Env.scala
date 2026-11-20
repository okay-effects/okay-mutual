package okay.mutual.node

import okay.effects.json.{Descent, Json}
import okay.scala3.*
import okay.scala3.std.Env
import okay.serverless.Storage
import okay.serverless.cloud.{Request, Response}

/**
 * A NODE'S OWN (specs/protocol.md "A node", "Policies"): its place in its hub (`slug`), its key pair, its unit and
 * name — set once — and ITS POLICY, which its community changes by its own command, inside what its hub allows:
 *
 *   open            whoever asks is opened an account with the default limits; else only those the community admits
 *   only            private people here — `business`: none, businesses and the self-employed only; `earned`: they
 *                   have no line and are lent none (they earn, then spend: no credit is given to a consumer —
 *                   docs/legal.md); `vouched`: no line of the community's, but a member may lend them one;
 *                   `record`: a line the community gives once the person has earned `after`; empty: a line as anyone's
 *   credit, ceiling the limits a new business opens with; `ceiling` a person's too
 *   personalCredit  the line a new person opens with, where people have lines from the start (`only` empty)
 *   personalMax     the most a person may owe in all — own line and lent (0: no bound of the community's)
 *   after           what a person has to have earned before the community gives a line (`record`)
 *   shared          whether members may open accounts of a family or a team
 *   wait            how many days a recovery is announced before it is in force
 *
 * Kept in the node's directory: a deployment holds as many nodes as people make. An account of a family is people's:
 * what holds for a person holds for it
 */
final case class NodeEnv(slug: String, pub: String, secret: String, unit: String, name: String, open: Boolean,
                         credit: Long, ceiling: Long, setup: String, only: String = "", personalCredit: Long = 0L,
                         personalMax: Long = 0L, after: Long = 0L, shared: Boolean = true, days: Int = 7,
                         // where the community went (an address), once it moved: from then on nothing changes here
                         moved: String = ""):
  private def people(of: String): Boolean = of == "personal" || of == "family"
  /** the line an account of this kind opens with */
  def opening(of: String): Long =
    if of == "family" || of == "team" then 0 else if !people(of) then credit else if only == "" then personalCredit
    else 0
  /** whether the community may give this account this line of its own */
  def line(s: okay.mutual.chain.State, credit: Long): Boolean = credit == 0 || !people(s.of) ||
    ((only == "" || (only == "record" && s.earned >= after)) && (personalMax == 0 || credit <= personalMax))
  /** whether a member may lend this account this much more */
  def lent(s: okay.mutual.chain.State, amount: Long): Boolean = !people(s.of) ||
    (Set("", "vouched", "record")(only) && (personalMax == 0 || s.credit + s.vouched + amount <= personalMax))
  /** WHETHER THIS POLICY IS ONE ITS HUB ALLOWS, and a policy at all: none when it is; else what is wrong */
  def allowed(hub: HubEnv): Option[String] =
    if !NodeEnv.modes(only) then Some("only")
    else if !hub.personal(only) then Some("hub:only")
    else if credit < 0 || ceiling < 0 || personalCredit < 0 || personalMax < 0 || after < 0 then Some("amount")
    else if days < 1 || days > 90 then Some("wait")
    else if hub.maxCredit > 0 && credit > hub.maxCredit then Some("hub:credit")
    // where people may owe at all, the hub's bound on it is the community's too; the line a person opens with
    // counts only where people open with one
    else if hub.maxPersonal > 0 && Set("", "vouched", "record")(only) && (personalMax == 0 ||
      personalMax > hub.maxPersonal || (only == "" && personalCredit > hub.maxPersonal)) then Some("hub:personal")
    else if only == "" && personalMax > 0 && personalCredit > personalMax then Some("amount")
    else None

object NodeEnv:
  /** what `only` may say */
  val modes: Set[String] = Set("business", "earned", "vouched", "record", "")
  def write(e: NodeEnv): Json.Obj = Json.obj("slug" -> Json.str(e.slug), "pub" -> Json.str(e.pub),
    "secret" -> Json.str(e.secret), "unit" -> Json.str(e.unit), "name" -> Json.str(e.name),
    "open" -> Json.bool(e.open), "credit" -> Json.num(e.credit), "ceiling" -> Json.num(e.ceiling),
    "setup" -> Json.str(e.setup), "only" -> Json.str(e.only), "personalCredit" -> Json.num(e.personalCredit),
    "personalMax" -> Json.num(e.personalMax), "after" -> Json.num(e.after), "shared" -> Json.bool(e.shared),
    "wait" -> Json.num(e.days), "moved" -> Json.str(e.moved))
  /** a node kept before its policy had these: a person's line as anyone's, no bound, shared accounts, a week */
  def read(j: Json): Option[NodeEnv] = scala.util.Try(NodeEnv(j("slug").string, j("pub").string, j("secret").string,
    j("unit").string, j("name").string, j("open").boolean, j("credit").long, j("ceiling").long, j("setup").string,
    j.field("only").collect { case Json.Str(s) => s }.getOrElse(""),
    j.field("personalCredit").fold(j("credit").long)(_.long), j.field("personalMax").fold(0L)(_.long),
    j.field("after").fold(0L)(_.long), j.field("shared").forall(_.boolean), j.field("wait").fold(7)(_.long.toInt),
    j.field("moved").collect { case Json.Str(m) => m }.getOrElse("")))
    .toOption
  /** THE POLICY OF A NODE, as anyone may read it (never its secret, never its setup token) */
  def policy(e: NodeEnv): Vector[(String, Json)] = Vector("join" -> Json.str(if e.open then "open" else "admitted"),
    "credit" -> Json.num(e.credit), "ceiling" -> Json.num(e.ceiling), "only" -> Json.str(e.only),
    "personalCredit" -> Json.num(e.personalCredit), "personalMax" -> Json.num(e.personalMax),
    "after" -> Json.num(e.after), "shared" -> Json.bool(e.shared), "wait" -> Json.num(e.days),
    "moved" -> Json.str(e.moved))
  /** a policy said (`join`, `only`, …: what is not said stays) over the one in force */
  def changed(e: NodeEnv, j: Json): Option[NodeEnv] = scala.util.Try {
    def num(name: String, was: Long): Long = j.field(name).fold(was)(_.long)
    e.copy(open = j.field("join").fold(e.open)(_.string == "open"), credit = num("credit", e.credit),
      ceiling = num("ceiling", e.ceiling), only = j.field("only").fold(e.only)(_.string),
      personalCredit = num("personalCredit", e.personalCredit), personalMax = num("personalMax", e.personalMax),
      after = num("after", e.after), shared = j.field("shared").fold(e.shared)(_.boolean),
      days = num("wait", e.days).toInt)
  }.toOption

object Shared:
  /** the kinds of an account held by its members' accounts: a family's (people's), a team's (a business's) */
  val kinds: Set[String] = Set("family", "team")

/** A HUB'S OWN — ITS OPERATOR'S POLICY, set where the hub is deployed: whether anyone may make a node here
 * (`HUB_OPEN=1`) or only who has the hub's token (`HUB_TOKEN`), how many one address may make a day (`HUB_PER_DAY`,
 * 3) and how many there may be in all (`HUB_MAX`, 500) — and WHAT ITS COMMUNITIES MAY BE: which ways with private
 * people (`HUB_PERSONAL`: `business,earned,vouched,record,line` — all when not said), the most a business's
 * default line (`HUB_MAX_CREDIT`, 0: any), the most a person may owe (`HUB_MAX_PERSONAL`, 0: any). A community's
 * policy is its own inside these, at its making and at every change */
final case class HubEnv(open: Boolean, token: String, perDay: Int, max: Int,
                        personal: Set[String] = NodeEnv.modes, maxCredit: Long = 0L, maxPersonal: Long = 0L)

object HubEnv:
  def read[R[_]](using In[Env, R]): HubEnv ! R =
    def v(name: String) = Env.variable[R](name).map(_.getOrElse("").trim)
    for
      open <- v("HUB_OPEN"); token <- Env.secret[R]("HUB_TOKEN").map(_.getOrElse("").trim)
      perDay <- v("HUB_PER_DAY"); max <- v("HUB_MAX"); personal <- v("HUB_PERSONAL")
      maxCredit <- v("HUB_MAX_CREDIT"); maxPersonal <- v("HUB_MAX_PERSONAL")
    yield HubEnv(open == "1", token, perDay.toIntOption.getOrElse(3), max.toIntOption.getOrElse(500),
      // `line` names the way that is written as the empty word
      if personal.isEmpty then NodeEnv.modes else personal.split(',').map(_.trim).map(m =>
        if m == "line" then "" else m).toSet.intersect(NodeEnv.modes),
      maxCredit.toLongOption.getOrElse(0L), maxPersonal.toLongOption.getOrElse(0L))
  /** what a hub allows its communities, as anyone may read it */
  def bounds(h: HubEnv): Vector[(String, Json)] = Vector(
    "personal" -> Json.Arr(NodeEnv.modes.toVector.sorted.filter(h.personal).map(Json.str)),
    "maxCredit" -> Json.num(h.maxCredit), "maxPersonal" -> Json.num(h.maxPersonal))

/** answers and stored values, the node's way */
object Http:
  /** a node is asked from any page — a wallet works with every node — and nothing here trusts a cookie: who asks is
   * known by a signature alone, so every origin may ask */
  val cors: Map[String, String] = Map("Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Headers" -> "content-type, x-setup-token, x-hub-token",
    "Access-Control-Allow-Methods" -> "GET, POST")
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
