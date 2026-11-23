package okay.mutual.chain

import okay.effects.json.{Descent, Json}

/**
 * WHAT A HOLDER MAY DO with an account (specs/protocol.md "Keys, devices and who may act"): a device's key — or,
 * for an account a family or a team shares, the id of a member's ACCOUNT, whose own devices then act for it —, what it
 * may (`spend`, `manage`), at most `payment` a payment and `period` in any `per` milliseconds (0: no such limit), and
 * `under`: the key that gave the grant (none: the key the account was opened with)
 */
final case class Grant(key: String, spend: Boolean, manage: Boolean, payment: Long = 0L, period: Long = 0L,
                       per: Long = 0L, under: Option[String] = None)

/** WHAT A RECORD SAYS. A key is its public half (P-256, the uncompressed point, base64url); an amount is in
 * hundredths of the node's unit; a `ref` is the hash of another record */
enum Body(val kind: String):
  /** the first record: the node the account is in, the first holder's key. The account's id is this record's hash */
  case Open(node: String, of: String, key: String, unit: String) extends Body("open")
  /** `amount` to the account `to`; `memo` the hash of what it is for; `name` names the payment (paid once) */
  case Send(to: String, amount: Long, memo: String, name: String) extends Body("send")
  /** `amount` from the account `from`, whose `send` is `ref` */
  case Receive(from: String, ref: String, amount: Long) extends Body("receive")
  /** this account's `send` `ref` not taken by its receiver: its amount back */
  case Bounce(ref: String, amount: Long) extends Body("bounce")
  case Limits(credit: Long, ceiling: Long) extends Body("limits")
  case Granted(grant: Grant) extends Body("grant")
  /** a holder's key taken off, with every key granted under it */
  case Revoke(key: String) extends Body("revoke")
  /** `amount` of this account's credit line lent to the account `to` */
  case Vouch(to: String, amount: Long) extends Body("vouch")
  /** the line lent by the account `from`, whose `vouch` is `ref` */
  case Vouched(from: String, ref: String, amount: Long) extends Body("vouched")
  /** `amount` of the lent line given back by this account (the newcomer's side) */
  case Release(amount: Long) extends Body("release")
  /** `amount` of the line this account lent by its `vouch` `ref`, returned (the voucher's side) */
  case Released(ref: String, amount: Long) extends Body("released")
  /** A RECOVERY ANNOUNCED: `key` is to hold the account from the time `after` — unless a holder takes it off first
   * (`revoke` of that key). The node's word, given when the community confirms it knows who asks */
  case Recover(key: String, after: Long) extends Body("recover")
  /** the recovery in force: `key` holds the account (not before its announced time) */
  case Recovered(key: String) extends Body("recovered")
  /** TWO HOLDERS for a payment above `above` (0: one is enough): said by one that may manage */
  case Two(above: Long) extends Body("two")
  /** the first holder's half of such a payment: no amount moves; another holder's `send` of the same takes it */
  case Propose(to: String, amount: Long, memo: String, name: String) extends Body("propose")
  /** THE ACCOUNT CLOSED: its last record — by a holder that may manage, or by the node alone (its community's
   * decision for one who left); at a zero balance, nothing lent to it or by it */
  case Close() extends Body("close")

/** A RECORD of an account's chain: its place (`seq`, the hash of the one before), the time its signer says, what
 * it says. An opening record has no account yet: `account` and `prev` are empty */
final case class Record(account: String, seq: Long, prev: String, at: Long, body: Body)

/** A KEY THAT SIGNED FOR AN ACCOUNT it holds: `account`'s chain says, at its record `seq`, that `key` may manage
 * it — so the key's signature is that account's word wherever the account is a holder */
final case class Acting(key: String, account: String, seq: Long)

/** A RECORD AS IT TRAVELS: its text — the bytes that are hashed and signed, never written again by a reader —, the
 * signatures over it, each by a key, and for which accounts those keys signed (`acts`: beside the signatures, not
 * under them — anyone checks each against the named account's own chain) */
final case class Signed(text: String, sigs: Vector[(String, String)], acts: Vector[Acting] = Vector.empty):
  def hash: String = Sha256.hex(text)

object Record:
  val version = 1

  /** a record's text: JSON, its fields in this order. A reader checks the signature of the text it was given and
   * parses that same text, so no two encoders ever need to agree byte for byte */
  def text(r: Record): String = Json.print(Json.obj("v" -> Json.num(version), "account" -> Json.str(r.account),
    "seq" -> Json.num(r.seq), "prev" -> Json.str(r.prev), "at" -> Json.num(r.at), "kind" -> Json.str(r.body.kind),
    "body" -> body(r.body)))

  private def body(b: Body): Json = b match
    case Body.Open(node, of, key, unit) => Json.obj("node" -> Json.str(node), "of" -> Json.str(of),
        "key" -> Json.str(key), "unit" -> Json.str(unit))
    case Body.Send(to, amount, memo, name) => Json.obj("to" -> Json.str(to), "amount" -> Json.num(amount),
        "memo" -> Json.str(memo), "name" -> Json.str(name))
    case Body.Receive(from, ref, amount) => Json.obj("from" -> Json.str(from), "ref" -> Json.str(ref),
        "amount" -> Json.num(amount))
    case Body.Bounce(ref, amount) => Json.obj("ref" -> Json.str(ref), "amount" -> Json.num(amount))
    case Body.Limits(credit, ceiling) => Json.obj("credit" -> Json.num(credit), "ceiling" -> Json.num(ceiling))
    case Body.Granted(g) => Json.obj("key" -> Json.str(g.key), "spend" -> Json.bool(g.spend),
        "manage" -> Json.bool(g.manage), "payment" -> Json.num(g.payment), "period" -> Json.num(g.period),
        "per" -> Json.num(g.per))
    case Body.Revoke(key) => Json.obj("key" -> Json.str(key))
    case Body.Vouch(to, amount) => Json.obj("to" -> Json.str(to), "amount" -> Json.num(amount))
    case Body.Vouched(from, ref, amount) => Json.obj("from" -> Json.str(from), "ref" -> Json.str(ref),
        "amount" -> Json.num(amount))
    case Body.Release(amount) => Json.obj("amount" -> Json.num(amount))
    case Body.Released(ref, amount) => Json.obj("ref" -> Json.str(ref), "amount" -> Json.num(amount))
    case Body.Close() => Json.obj()
    case Body.Recover(key, after) => Json.obj("key" -> Json.str(key), "after" -> Json.num(after))
    case Body.Recovered(key) => Json.obj("key" -> Json.str(key))
    case Body.Two(above) => Json.obj("above" -> Json.num(above))
    case Body.Propose(to, amount, memo, name) => Json.obj("to" -> Json.str(to), "amount" -> Json.num(amount),
        "memo" -> Json.str(memo), "name" -> Json.str(name))

  /** a text read: none when it is not a record of this version */
  def parse(text: String): Option[Record] = Descent.parse(text).toOption.flatMap(j => scala.util.Try {
    require(j("v").long == version)
    val b = j("body")
    val read: Body = j("kind").string match
      case "open" => Body.Open(b("node").string, b("of").string, b("key").string, b("unit").string)
      case "send" => Body.Send(b("to").string, b("amount").long, b("memo").string, b("name").string)
      case "receive" => Body.Receive(b("from").string, b("ref").string, b("amount").long)
      case "bounce" => Body.Bounce(b("ref").string, b("amount").long)
      case "limits" => Body.Limits(b("credit").long, b("ceiling").long)
      case "grant" => Body.Granted(Grant(b("key").string, b("spend").boolean, b("manage").boolean, b("payment").long,
          b("period").long, b("per").long))
      case "revoke" => Body.Revoke(b("key").string)
      case "vouch" => Body.Vouch(b("to").string, b("amount").long)
      case "vouched" => Body.Vouched(b("from").string, b("ref").string, b("amount").long)
      case "release" => Body.Release(b("amount").long)
      case "released" => Body.Released(b("ref").string, b("amount").long)
      case "recover" => Body.Recover(b("key").string, b("after").long)
      case "recovered" => Body.Recovered(b("key").string)
      case "two" => Body.Two(b("above").long)
      case "propose" => Body.Propose(b("to").string, b("amount").long, b("memo").string, b("name").string)
      case "close" => Body.Close()
    Record(j("account").string, j("seq").long, j("prev").string, j("at").long, read)
  }.toOption)

object Signed:
  /** a signature says for whom it was given when it was for an account: `for`, `seq` */
  def json(s: Signed): Json.Obj = Json.obj("record" -> Json.str(s.text), "sigs" -> Json.Arr(s.sigs.map((k, sig) =>
    Json.Obj(Vector("key" -> Json.str(k), "sig" -> Json.str(sig)) ++ s.acts.find(_.key == k).toVector.flatMap(a =>
      Vector("for" -> Json.str(a.account), "seq" -> Json.num(a.seq)))))))
  def parse(j: Json): Option[Signed] = scala.util.Try {
    val sigs = j("sigs").list
    Signed(j("record").string, sigs.map(s => s("key").string -> s("sig").string), sigs.flatMap(s =>
      s.field("for").collect { case Json.Str(a) => Acting(s("key").string, a, s.field("seq").fold(0L)(_.long)) }))
  }.toOption
