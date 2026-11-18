package okay.mutual.node

import okay.effects.json.Json
import okay.mutual.chain.*
import okay.scala3.*

/** A NODE (specs/protocol.md "A payment", "A node"): accounts opened by their first record, payments carried or
 * bounced, the community's own government, the signed head — real signatures throughout */
class NodeSuite extends munit.FunSuite:
  private val day = 86400000L
  private def town(): (Net, Wallet, Wallet) =
    val net = Net()
    val anna = Wallet(net)
    val jan = Wallet(net)
    assertEquals(anna.join("Anna").status, 200)
    assertEquals(jan.join("Jan", "business").status, 200)
    (net, anna, jan)
  private def error(net: Net, r: okay.serverless.cloud.Response): (Int, String, Json) =
    (r.status, net.json(r)("error").string, net.json(r)("detail"))

  test("a node says what it is; an account is opened by its first signed record and listed") {
    val (net, anna, jan) = town()
    val said = net.json(net.get("/.well-known/okay-mutual.json"))
    assertEquals((said("key").string, said("unit").string, said("name").string, said("join").string),
      (net.node.pub, "PLN", "Test Town", "open"))
    val s = anna.state
    assertEquals((s("seq").long, s("balance").long, s("floor").long, s("ceiling").long, s("of").string),
      (2L, 0L, -10000L, 30000L, "personal"))
    assertEquals(net.json(net.get("/api/members"))("members").list.map(m => m("name").string -> m("id").string),
      Vector("Anna" -> anna.id, "Jan" -> jan.id))
    // the chain the node gives is checked by anyone, from its first record: the node's key and the member's
    assertEquals(net.checked(anna.id).map(st => (st.id, st.seq, st.node)), Right((anna.id, 2L, net.node.pub)))
    // not twice; not for another node, another unit, without a name; not by a signature that is not the key's
    assertEquals(anna.join("Anna again").status, 409)
    val stranger = Wallet(net)
    def opened(r: Record, by: Pair = stranger.key, name: String = "X") =
      net.post("/api/accounts", Json.obj("record" -> stranger.signed(r, by), "name" -> Json.str(name))).status
    assertEquals(opened(stranger.opening().copy(body = Body.Open("another", "personal", stranger.key.pub, "PLN"))), 400)
    assertEquals(opened(stranger.opening().copy(body = Body.Open(net.node.pub, "personal", stranger.key.pub, "EUR"))),
      400)
    assertEquals(opened(stranger.opening(), name = " "), 400)
    assertEquals(opened(stranger.opening(), by = anna.key), 409)
    assertEquals(net.get("/api/accounts/" + "0" * 64).status, 404)
    assertEquals(net.get("/api/accounts/not-an-id").status, 404)
  }

  test("a payment: the sender's record, then the receiver's; both chains hold by their signatures") {
    val (net, anna, jan) = town()
    val paid = anna.pay(jan, 4000)
    assertEquals(paid.status, 200, paid.body)
    assertEquals((anna.balance, jan.balance), (-4000L, 4000L))
    val sent = Signed.parse(net.json(paid)("record")).get
    assertEquals(sent.sigs.map(_._1).toSet, Set(anna.key.pub, net.node.pub))
    val received = net.json(net.get(s"/api/accounts/${jan.id}/chain?from=3"))("records").list.flatMap(Signed.parse)
    assertEquals(received.map(s => Record.parse(s.text).get.body), Vector[Body](Body.Receive(anna.id, sent.hash, 4000)))
    assertEquals(net.checked(anna.id).map(_.balance), Right(-4000L))
    assertEquals(net.checked(jan.id).map(_.balance), Right(4000L))
    // nothing left in flight
    assert(!net.cloud.stored("ACCOUNTS", anna.id).keys.exists(_.contains("/flight/")))
    assertEquals(net.alarms(), 0)
  }

  test("what a payment is for travels beside the chains: kept at both ends when its hash is the record's") {
    val (net, anna, jan) = town()
    def memos(w: Wallet) = net.json(net.get(s"/api/accounts/${w.id}/chain"))("records").list
      .flatMap(_.field("memo").map(_.string))
    val record = anna.next(Body.Send(jan.id, 4000, Sha256.hex("a jacket altered"), ""))
    assertEquals(net.post(s"/api/accounts/${anna.id}/records", Json.obj("record" -> anna.signed(record),
      "memo" -> Json.str("a jacket altered"))).status, 200)
    assertEquals((memos(anna), memos(jan)), (Vector("a jacket altered"), Vector("a jacket altered")))
    // other words than the hash names are not kept
    val other = anna.next(Body.Send(jan.id, 100, Sha256.hex("bread"), ""))
    net.post(s"/api/accounts/${anna.id}/records", Json.obj("record" -> anna.signed(other),
      "memo" -> Json.str("something else")))
    assertEquals((memos(anna).size, memos(jan).size, jan.balance), (1, 1, 4100L))
    assertEquals(net.json(net.get("/api/community"))("community"), Json.Null)
    assertEquals(net.checked(jan.id).map(_.balance), Right(4100L))
  }

  test("refused: past the credit line, by a key that holds nothing, out of place, out of time") {
    val (net, anna, jan) = town()
    assertEquals(error(net, anna.pay(jan, 10001)), (409, "credit", Json.num(1)))
    assertEquals(error(net, anna.say(Body.Send(jan.id, 100, "", ""), by = jan.key)),
      (409, "unsigned", Json.str("holder")))
    val record = anna.next(Body.Send(jan.id, 100, "", ""))
    assertEquals(anna.send(record).status, 200)
    // the same record again: its place is taken
    assertEquals(error(net, anna.send(record)), (409, "place", Json.str("seq")))
    assertEquals(error(net, anna.send(anna.next(Body.Send(jan.id, 1, "", "")).copy(at = net.now + 11 * 60000)))._2,
      "place")
    // a member does not write the node's records
    assertEquals(error(net, anna.say(Body.Limits(1000000, 1000000))), (409, "forbidden", Json.str("node")))
    assertEquals(error(net, anna.say(Body.Receive(jan.id, "r", 1000000))), (409, "forbidden", Json.str("node")))
    assertEquals((anna.balance, jan.balance), (-100L, 100L))
  }

  test("a payment nobody takes comes back: no such account, or a receiver at its ceiling") {
    val (net, anna, jan) = town()
    val lost = anna.say(Body.Send("f" * 64, 500, "", ""))
    assertEquals((lost.status, net.json(lost)("state")("balance").long), (200, 0L))
    val kinds = net.json(net.get(s"/api/accounts/${anna.id}/chain"))("records").list.flatMap(Signed.parse)
      .map(s => Record.parse(s.text).get.body.kind)
    assertEquals(kinds, Vector("open", "limits", "send", "bounce"))
    // Jan at 300.00 of his 300.00: one more unit bounces
    val rich = Wallet(net)
    rich.join("Rich")
    val founder = Wallet(net)
    net.post("/api/setup", Json.obj("record" -> founder.signed(founder.opening("community")),
      "name" -> Json.str("Test Town")), "x-setup-token" -> net.token)
    net.command(founder.key, "cmd" -> Json.str("limits"), "at" -> Json.num(net.now), "account" -> Json.str(rich.id),
      "credit" -> Json.num(50000), "ceiling" -> Json.num(30000))
    assertEquals(rich.pay(jan, 30000).status, 200)
    assertEquals(rich.pay(jan, 1).status, 200)
    assertEquals((rich.balance, jan.balance), (-30000L, 30000L))
    assertEquals(net.checked(rich.id).map(_.balance), Right(-30000L))
  }

  test("a named payment sent twice pays once; its name with another payment is refused") {
    val (net, anna, jan) = town()
    val first = anna.pay(jan, 4000, "k1")
    val again = anna.pay(jan, 4000, "k1")
    assertEquals((first.status, again.status), (200, 200))
    assertEquals(net.json(again)("record"), net.json(first)("record"))
    assertEquals((anna.balance, jan.balance), (-4000L, 4000L))
    assertEquals(error(net, anna.pay(jan, 4001, "k1"))._2, "named")
  }

  test("a second device: its own key, signed in by the first, limited; cut off by a record") {
    val (net, anna, jan) = town()
    val tablet = SignerJdk.generate()
    assertEquals(anna.say(Body.Granted(Grant(tablet.pub, spend = true, manage = false, 2000, 5000, 7 * day))).status,
      200)
    assertEquals(anna.say(Body.Send(jan.id, 2000, "", ""), by = tablet).status, 200)
    assertEquals(error(net, anna.say(Body.Send(jan.id, 2001, "", ""), by = tablet)),
      (409, "limit", Json.str("payment")))
    assertEquals(anna.say(Body.Send(jan.id, 2000, "", ""), by = tablet).status, 200)
    assertEquals(error(net, anna.say(Body.Send(jan.id, 1001, "", ""), by = tablet)), (409, "limit", Json.str("period")))
    assertEquals(error(net, anna.say(Body.Revoke(anna.key.pub), by = tablet)), (409, "forbidden", Json.str("manage")))
    assertEquals(anna.say(Body.Revoke(tablet.pub)).status, 200)
    assertEquals(error(net, anna.say(Body.Send(jan.id, 1, "", ""), by = tablet))._2, "unsigned")
    assertEquals(net.checked(anna.id).map(s => (s.balance, s.grants.size)), Right((-4000L, 1)))
  }

  test("vouching: a member lends part of its line to another, across their two chains") {
    val (net, anna, jan) = town()
    assertEquals(anna.say(Body.Vouch(jan.id, 3000)).status, 200)
    assertEquals((anna.state("floor").long, jan.state("floor").long, jan.state("voucher").string),
      (-7000L, -13000L, anna.id))
    // a line nobody can take comes back
    assertEquals(anna.say(Body.Vouch("f" * 64, 1000)).status, 200)
    assertEquals((anna.state("floor").long, anna.state("lent").long), (-7000L, 3000L))
  }

  test("the community governs itself as an account: set up once, commands by those its chain says may manage") {
    val net = Net("admitted")
    val founder = Wallet(net)
    val setup = Json.obj("record" -> founder.signed(founder.opening("community")), "name" -> Json.str("Test Town"))
    assertEquals(net.post("/api/setup", setup).status, 403)
    assertEquals(net.post("/api/setup", setup, "x-setup-token" -> "wrong").status, 403)
    assertEquals(net.post("/api/setup", setup, "x-setup-token" -> net.token).status, 200)
    val other = Wallet(net)
    assertEquals(net.post("/api/setup", Json.obj("record" -> other.signed(other.opening("community")),
      "name" -> Json.str("Another")), "x-setup-token" -> net.token).status, 409)
    // joining is not open: a member is admitted by a command, with the limits the community gives
    val anna = Wallet(net)
    // she asks: her signed first record is kept as a request; nobody asks in another's name
    val asked = anna.join("Anna")
    assertEquals(asked.status, 202, asked.body)
    val request = net.json(asked)("requested").string
    val eve = Wallet(net)
    assertEquals(net.post("/api/accounts", Json.obj("record" -> eve.signed(eve.opening(), by = anna.key),
      "name" -> Json.str("Eve"))).status, 409)
    assertEquals(net.json(net.get("/api/requests"))("requests").list.map(q => q("name").string -> q("id").string),
      Vector("Anna" -> request))
    assert(!net.get("/api/requests").body.contains("record"))
    assertEquals(net.get(s"/api/accounts/$request").status, 404)
    def admit(by: Pair, at: Long = net.now, id: String = request) = net.command(by, "cmd" -> Json.str("admit"),
      "at" -> Json.num(at), "id" -> Json.str(id), "credit" -> Json.num(2000), "ceiling" -> Json.num(8000))
    assertEquals(admit(founder.key, id = "0" * 64).status, 404)
    assertEquals(admit(anna.key).status, 403)
    assertEquals(admit(founder.key, net.now - 11 * 60000).status, 409)
    val admitted = admit(founder.key)
    assertEquals(admitted.status, 200, admitted.body)
    anna.id = net.json(admitted)("state")("id").string
    assertEquals((anna.id, anna.state("floor").long, anna.state("ceiling").long), (request, -2000L, 8000L))
    assertEquals(net.json(net.get("/api/requests"))("requests").list.size, 0)
    // one who is rejected is no longer waiting
    assertEquals(eve.join("Eve").status, 202)
    assertEquals(net.command(founder.key, "cmd" -> Json.str("reject"), "at" -> Json.num(net.now),
      "id" -> Json.str(net.json(net.get("/api/requests"))("requests").list.head("id").string)).status, 200)
    assertEquals(net.json(net.get("/api/requests"))("requests").list.size, 0)
    // the same command again is not taken; a forged signature is none
    assertEquals(admit(founder.key).status, 409)
    val text = Json.print(Json.obj("cmd" -> Json.str("limits"), "at" -> Json.num(net.now),
      "account" -> Json.str(anna.id), "credit" -> Json.num(999999), "ceiling" -> Json.num(999999)))
    assertEquals(net.post("/api/command", Json.obj("command" -> Json.str(text), "key" -> Json.str(founder.key.pub),
      "sig" -> Json.str(SignerJdk.sign(anna.key.secret, text)))).status, 403)
    // the founder signs a second holder in; it commands too — and no longer once taken off
    val board = SignerJdk.generate()
    founder.id = net.json(net.get("/api/members"))("members").list.find(_("of").string == "community").get("id").string
    assertEquals(founder.say(Body.Granted(Grant(board.pub, spend = false, manage = true))).status, 200)
    def limits(by: Pair, credit: Long) = net.command(by, "cmd" -> Json.str("limits"), "at" -> Json.num(net.now),
      "account" -> Json.str(anna.id), "credit" -> Json.num(credit), "ceiling" -> Json.num(8000))
    assertEquals(limits(board, 3000).status, 200)
    assertEquals(anna.state("floor").long, -3000L)
    assertEquals(founder.say(Body.Revoke(board.pub)).status, 200)
    assertEquals(limits(board, 4000).status, 403)
    assertEquals(anna.state("floor").long, -3000L)
  }

  test("the node's head: signed, over every account; a proof for each; it moves with every record") {
    val (net, anna, jan) = town()
    def head() = Signed.parse(net.json(net.get("/api/head"))).get
    val before = head()
    assert(SignerJdk.verify(net.node.pub, before.text, before.sigs.head._2))
    val h = okay.effects.json.Descent.parse(before.text).toOption.get
    assertEquals((h("count").long, h("node").string), (2L, net.node.pub))
    def proved(w: Wallet): Boolean =
      val p = net.json(net.get(s"/api/accounts/${w.id}/proof"))
      val signed = Signed.parse(p("head")).get
      val root = okay.effects.json.Descent.parse(signed.text).toOption.get("root").string
      SignerJdk.verify(net.node.pub, signed.text, signed.sigs.head._2) && p("accountHead").string ==
        w.state("head").string && Merkle.holds(root, w.id, p("accountHead").string, p("proof").list.map(s =>
        s("hash").string -> s("left").boolean).toVector)
    assert(proved(anna) && proved(jan))
    anna.pay(jan, 100)
    val after = okay.effects.json.Descent.parse(head().text).toOption.get
    assert(after("number").long > h("number").long)
    assertNotEquals(after("root").string, h("root").string)
    assert(proved(anna) && proved(jan))
    assertEquals(net.get("/api/accounts/" + "0" * 64 + "/proof").status, 404)
  }

  test("a hub: anyone makes a node, each with its own key; nodes do not see each other's accounts") {
    val hub = Hubs()
    assertEquals(hub.json(hub.get("/.well-known/okay-mutual-hub.json"))("making").string, "open")
    val (north, south) = (Net(hub = hub, slug = "north", name = "North"), Net(hub = hub, slug = "south", name = "South"))
    assertNotEquals(north.node.pub, south.node.pub)
    assertNotEquals(north.token, south.token)
    assertEquals(hub.json(hub.get("/api/nodes"))("nodes").list.map(n => n("slug").string -> n("name").string),
      Vector("north" -> "North", "south" -> "South"))
    // what is said of a node never holds its secret or its token
    val listed = hub.get("/api/nodes").body + north.get("/.well-known/okay-mutual.json").body
    assert(!listed.contains("secret") && !listed.contains(north.token))
    val (anna, olaf) = (Wallet(north), Wallet(south))
    assertEquals((anna.join("Anna").status, olaf.join("Olaf").status), (200, 200))
    assertEquals(north.json(north.get("/api/members"))("members").list.map(_("name").string), Vector("Anna"))
    // an account of one node is not there for the other: not read, not written, not paid
    assertEquals(south.get(s"/api/accounts/${anna.id}").status, 404)
    assertEquals(south.post(s"/api/accounts/${anna.id}/records", Json.obj("record" ->
      anna.signed(anna.next(Body.Send(olaf.id, 100, "", ""))))).status, 404)
    // a payment across nodes comes back: there are no trust lines yet
    assertEquals(anna.pay(olaf, 500).status, 200)
    assertEquals((anna.balance, olaf.balance), (0L, 0L))
    assertEquals(north.checked(anna.id).map(_.seq), Right(4L))
    // a wallet of one node cannot open at another with its record
    assertEquals(south.post("/api/accounts", Json.obj("record" -> anna.signed(anna.opening()),
      "name" -> Json.str("Anna"))).status, 400)
    assertEquals(hub.get("/n/nowhere/api/members").status, 404)
  }

  test("any origin may ask, and is told so once: an answer that came through an object is not told twice") {
    val through = okay.serverless.cloud.Response(200, Map("access-control-allow-origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST", "content-type" -> "application/json"), "{}")
    val opened = Http.open(through)
    assertEquals(opened.headers.keys.count(_.toLowerCase == "access-control-allow-origin"), 1)
    assertEquals(opened.headers.keys.count(_.toLowerCase == "access-control-allow-methods"), 1)
    assertEquals(opened.headers.get("content-type"), Some("application/json"))
    val net = Net()
    for path <- Seq("/api/members", "/api/head", "/.well-known/okay-mutual.json") do
      assertEquals(net.get(path).headers.keys.count(_.toLowerCase == "access-control-allow-origin"), 1, path)
    assertEquals(net.hub.get("/api/nodes").headers.keys.count(_.toLowerCase == "access-control-allow-origin"), 1)
  }

  test("making a node: a proper place, once; a few a day from one address; behind a token where it is not open") {
    val hub = Hubs("HUB_PER_DAY" -> "2")
    assertEquals(hub.make("our-town", "Our Town").status, 200)
    assertEquals(hub.make("our-town", "Again").status, 409)
    for bad <- Seq("x", "Bad Slug", "-a-", "api", "wallet", "a" * 41) do assertEquals(hub.make(bad, "X").status, 400, bad)
    assertEquals(hub.make("no-name", " ").status, 400)
    assertEquals(hub.make("second", "Second").status, 200)
    assertEquals(hub.make("third", "Third").status, 429)
    assertEquals(hub.make("third", "Third", from = "5.6.7.8").status, 200)
    hub.now += 86400000L
    assertEquals(hub.make("fourth", "Fourth").status, 200)
    val closed = Hubs("HUB_OPEN" -> "")
    closed.cloud.secrets += "HUB_TOKEN" -> "hub-token"
    assertEquals(closed.make("town", "Town").status, 403)
    assertEquals(closed.post("/api/nodes", Json.obj("slug" -> Json.str("town"), "name" -> Json.str("Town"),
      "unit" -> Json.str("PLN"), "join" -> Json.str("open"), "credit" -> Json.num(0), "ceiling" -> Json.num(0)),
      "x-hub-token" -> "hub-token").status, 200)
    val full = Hubs("HUB_MAX" -> "1")
    assertEquals((full.make("one", "One").status, full.make("two", "Two", from = "9.9.9.9").status), (200, 429))
  }

  /** a community set up by a founder's device, with a member; its line opened by the founder's command */
  private final class Community(val net: Net, member: String):
    val founder = Wallet(net)
    assertEquals(net.post("/api/setup", Json.obj("record" -> founder.signed(founder.opening("community")),
      "name" -> Json.str(member + "'s town")), "x-setup-token" -> net.token).status, 200)
    val one = Wallet(net)
    assertEquals(one.join(member).status, 200)
    def line(address: String, credit: Long) = net.command(founder.key, "cmd" -> Json.str("line"),
      "at" -> Json.num(net.now), "address" -> Json.str(address), "credit" -> Json.num(credit))
    /** the account this node keeps for the node of `other` */
    def lineTo(other: Net): Json =
      val id = net.json(net.get(s"/api/line?node=${other.node.pub}"))("id").string
      net.json(net.get(s"/api/accounts/$id"))
    def sum: Long = net.json(net.get("/api/members"))("members").list.map(m =>
      net.json(net.get(s"/api/accounts/${m("id").string}"))("balance").long).sum
    def pay(to: Wallet, at: Net, amount: Long) = one.say(Body.Send(s"${to.id}@${at.node.pub}", amount, "", ""))

  /** two communities with lines to each other: each lets the other owe it 500.00 */
  private def trading(north: Net, south: Net, northAddress: String, southAddress: String): (Community, Community) =
    val (n, s) = (Community(north, "Anna"), Community(south, "Olaf"))
    assertEquals(n.line(southAddress, 50000).status, 200, n.line(southAddress, 50000).body)
    assertEquals(s.line(northAddress, 50000).status, 200)
    (n, s)

  private def traded(n: Community, s: Community): Unit =
    val (north, south) = (n.net, s.net)
    // Anna (north) pays Olaf (south) 40.00: her chain, north's key on north's account at south, Olaf's chain
    assertEquals(n.pay(s.one, south, 4000).status, 200)
    assertEquals((n.one.balance, s.one.balance), (-4000L, 4000L))
    // the two line accounts mirror each other; each community's balances still sum to zero
    assertEquals((n.lineTo(south)("balance").long, s.lineTo(north)("balance").long), (4000L, -4000L))
    assertEquals((n.sum, s.sum), (0L, 0L))
    // every chain holds by its signatures: the line at south is held by north's key and ordered by south's
    val atSouth = s.lineTo(north)
    assertEquals(atSouth("grants").list.map(_("key").string), Vector(north.node.pub))
    assertEquals(south.checked(atSouth("id").string).map(st => (st.node, st.balance)), Right((south.node.pub, -4000L)))
    assertEquals(north.checked(n.one.id).map(_.balance), Right(-4000L))
    assertEquals(south.checked(s.one.id).map(_.balance), Right(4000L))
    // Olaf sees whom it came from: the other community, listed among his own
    assert(south.json(south.get("/api/members"))("members").list.exists(m => m("of").string == "line" &&
      m("name").string == "North"))
    // the way back: Olaf pays Anna 10.00
    assertEquals(s.pay(n.one, north, 1000).status, 200)
    assertEquals((n.one.balance, s.one.balance), (-3000L, 3000L))
    assertEquals((n.lineTo(south)("balance").long, s.lineTo(north)("balance").long), (3000L, -3000L))
    // past what south lets north owe (500.00): refused there, back to Anna here — whose own line would allow it
    north.command(n.founder.key, "cmd" -> Json.str("limits"), "at" -> Json.num(north.now),
      "account" -> Json.str(n.one.id), "credit" -> Json.num(9000000), "ceiling" -> Json.num(30000))
    assertEquals(n.pay(s.one, south, 47001).status, 200)
    assertEquals((n.one.balance, s.lineTo(north)("balance").long, n.lineTo(south)("balance").long),
      (-3000L, -3000L, 3000L))
    // a receiver at its ceiling there: bounced there, back here
    assertEquals(n.pay(s.one, south, 28000).status, 200)
    assertEquals((n.one.balance, s.one.balance, s.lineTo(north)("balance").long), (-3000L, 3000L, -3000L))
    // nobody of that id there; no such node at all
    assertEquals(n.pay(Wallet(north), south, 100).status, 200)
    assertEquals(n.one.say(Body.Send(s"${s.one.id}@nobody", 100, "", "")).status, 200)
    assertEquals((n.one.balance, n.sum, s.sum), (-3000L, 0L, 0L))
    // a line's credit is changed by the same command, and its ceiling is never bounded
    assertEquals(n.line(n.net.json(n.net.get("/api/lines"))("lines").list.head("address").string, 100).status, 200)
    assertEquals((n.lineTo(south)("credit").long, n.lineTo(south)("ceiling").long), (100L, Peers.unbounded))
    north.command(n.founder.key, "cmd" -> Json.str("limits"), "at" -> Json.num(north.now + 1),
      "account" -> Json.str(n.lineTo(south)("id").string), "credit" -> Json.num(200), "ceiling" -> Json.num(1))
    assertEquals((n.lineTo(south)("credit").long, n.lineTo(south)("ceiling").long), (200L, Peers.unbounded))
    // south may now owe north only 2.00: Olaf's payment of 31.00 more to Anna (south already owes... no: north owes
    // south 30.00, so 32.00 more would put south 2.00 in debt, the most it may) passes, one more unit does not
    assertEquals(s.pay(n.one, north, 3200).status, 200)
    assertEquals(n.lineTo(south)("balance").long, -200L)
    assertEquals(s.pay(n.one, north, 1).status, 200)
    assertEquals((n.lineTo(south)("balance").long, s.lineTo(north)("balance").long, n.sum, s.sum), (-200L, 200L, 0L, 0L))

  test("trust lines on one hub: a payment across two communities, mirrored, bounded by the receiver's trust") {
    val hub = Hubs("HUB_PER_DAY" -> "9")
    val (north, south) = (Net(hub = hub, slug = "north", name = "North"), Net(hub = hub, slug = "south", name = "South"))
    // no line yet: the payment comes back
    val (n0, s0) = (Wallet(north), Wallet(south))
    n0.join("Early"); s0.join("Late")
    assertEquals(n0.say(Body.Send(s"${s0.id}@${south.node.pub}", 100, "", "")).status, 200)
    assertEquals(n0.balance, 0L)
    val (n, s) = trading(north, south, "/n/north", "/n/south")
    // a line is the community's to open: not a stranger's command, not to itself, not to a place that is no node
    assertEquals(north.command(n.one.key, "cmd" -> Json.str("line"), "at" -> Json.num(north.now),
      "address" -> Json.str("/n/south"), "credit" -> Json.num(1)).status, 403)
    assertEquals(n.line("/n/north", 1).status, 409)
    assertEquals(n.line("/n/nowhere", 1).status, 404)
    assertEquals(n.line("ftp://x", 1).status, 400)
    traded(n, s)
  }

  test("trust lines between two hubs: the same, over the network") {
    val (a, b) = (Hubs(), Hubs())
    def wire(from: Hubs, host: String, to: Hubs): Unit =
      val before = from.cloud.internet
      from.cloud.internet = (url, r) => if url.startsWith(host) then to.ask(okay.serverless.cloud.Request(r.method,
        url.stripPrefix(host)).copy(body = r.body, headers = r.headers)) else before(url, r)
    wire(a, "https://south.example", b)
    wire(b, "https://north.example", a)
    val (north, south) = (Net(hub = a, slug = "north", name = "North"), Net(hub = b, slug = "south", name = "South"))
    val (n, s) = trading(north, south, "https://north.example/n/north", "https://south.example/n/south")
    traded(n, s)
  }

  test("a community's rules: no account without accepting them as they are now; businesses only where it says so") {
    val hub = Hubs()
    assertEquals(hub.make("guild", "The Guild", only = "business", rules = "1. Units are not money.").status, 200)
    assertEquals(hub.make("odd", "Odd", only = "people").status, 400)
    def at(path: String) = s"/n/guild$path"
    val about = hub.json(hub.get(at("/.well-known/okay-mutual.json")))
    assertEquals(about("only").string, "business")
    val rules = hub.json(hub.get(at("/api/rules")))
    assertEquals((rules("text").string, rules("hash").string), ("1. Units are not money.",
      Sha256.hex("1. Units are not money.")))
    val key = SignerJdk.generate()
    def join(of: String, accepted: Option[String]) =
      val text = Record.text(Record("", 1, "", hub.now, Body.Open(about("key").string, of, key.pub, "PLN")))
      hub.post(at("/api/accounts"), Json.Obj(Vector("record" -> Signed.json(Signed(text, Vector(key.pub ->
        SignerJdk.sign(key.secret, text)))), "name" -> Json.str("A firm")) ++ accepted.map(h => "rules" -> Json.str(h))))
    def refused(r: okay.serverless.cloud.Response) = (r.status, hub.json(r)("error").string)
    assertEquals(refused(join("business", None)), (409, "rules"))
    assertEquals(refused(join("business", Some("another hash"))), (409, "rules"))
    assertEquals(refused(join("personal", Some(rules("hash").string))), (409, "business"))
    assertEquals(join("business", Some(rules("hash").string)).status, 200)
    // a node with no rules asks for none (the nodes made before there were any)
    val plain = Net(hub = hub, slug = "plain", name = "Plain")
    assertEquals(plain.json(plain.get("/api/rules"))("hash").string, "")
    assertEquals(Wallet(plain).join("Anna").status, 200)
    // the community changes its rules by a command: from then on those are the ones accepted
    val town = Community(plain, "Olaf")
    assertEquals(plain.command(town.founder.key, "cmd" -> Json.str("rules"), "at" -> Json.num(plain.now),
      "text" -> Json.str("New rules.")).status, 200)
    assertEquals(plain.json(plain.get("/api/rules"))("hash").string, Sha256.hex("New rules."))
    assertEquals(Wallet(plain).join("Late").status, 409)
    assertEquals(plain.command(town.one.key, "cmd" -> Json.str("rules"), "at" -> Json.num(plain.now),
      "text" -> Json.str("Mine.")).status, 403)
  }

  test("private people who earn, then spend: a personal account never goes below zero; a business's does") {
    val hub = Hubs()
    assertEquals(hub.make("market", "The Market", only = "earned").status, 200)
    val market = new Net(hub = hub, slug = "market2", name = "Market Two")
    val net = Community(market, "Shop").net
    // the same on a node made so: use it through its own place
    def at(path: String) = s"/n/market$path"
    val about = hub.json(hub.get(at("/.well-known/okay-mutual.json")))
    assertEquals(about("only").string, "earned")
    final class Member(of: String, name: String):
      val key = SignerJdk.generate()
      def sign(r: Record) = { val text = Record.text(r); Signed.json(Signed(text, Vector(key.pub ->
        SignerJdk.sign(key.secret, text)))) }
      val opened = hub.json(hub.post(at("/api/accounts"), Json.obj("record" -> sign(Record("", 1, "", hub.now,
        Body.Open(about("key").string, of, key.pub, "PLN"))), "name" -> Json.str(name))))
      val id = opened("state")("id").string
      def state = hub.json(hub.get(at(s"/api/accounts/$id")))
      def say(body: Body) = hub.post(at(s"/api/accounts/$id/records"), Json.obj("record" -> sign(Record(id,
        state("seq").long + 1, state("head").string, hub.now, body))))
    val (shop, anna) = (Member("business", "Shop"), Member("personal", "Anna"))
    // the default line is the business's; the person's account opens with none, its ceiling as anyone's
    assertEquals((shop.state("floor").long, anna.state("floor").long, anna.state("ceiling").long),
      (-10000L, 0L, 30000L))
    val refused = anna.say(Body.Send(shop.id, 1, "", ""))
    assertEquals((refused.status, hub.json(refused)("error").string), (409, "credit"))
    // she earns — the shop pays her for work — and then spends within what she earned
    assertEquals(shop.say(Body.Send(anna.id, 5000, "", "")).status, 200)
    assertEquals(anna.say(Body.Send(shop.id, 3000, "", "")).status, 200)
    assertEquals((anna.state("balance").long, hub.json(anna.say(Body.Send(shop.id, 2001, "", "")))("error").string),
      (2000L, "credit"))
    // a line lent to her comes back to its lender: nobody gives a person credit here
    assertEquals(shop.say(Body.Vouch(anna.id, 1000)).status, 200)
    assertEquals((anna.state("floor").long, shop.state("lent").long), (0L, 0L))
    // one business vouches for another as before
    val other = Member("business", "Bakery")
    assertEquals(shop.say(Body.Vouch(other.id, 1000)).status, 200)
    assertEquals(other.state("floor").long, -11000L)
    assert(net.prefix.nonEmpty)
  }

  test("recovery: asked by a new device, confirmed by the community, in force after a week — or cancelled") {
    val town = Community(Net(), "Anna")
    val (net, anna) = (town.net, town.one)
    val fresh = SignerJdk.generate()
    def ask(key: Pair, by: Pair) = net.post(s"/api/accounts/${anna.id}/recovery", Json.obj("key" -> Json.str(key.pub),
      "sig" -> Json.str(SignerJdk.sign(by.secret, s"recover:${anna.id}:${key.pub}"))))
    // only by a device that holds the key it names; not for a key that holds the account already
    assertEquals(ask(fresh, anna.key).status, 403)
    assertEquals(ask(anna.key, anna.key).status, 409)
    assertEquals(net.post(s"/api/accounts/${"0" * 64}/recovery", Json.obj("key" -> Json.str(fresh.pub),
      "sig" -> Json.str(SignerJdk.sign(fresh.secret, s"recover:${"0" * 64}:${fresh.pub}")))).status, 404)
    assertEquals(ask(fresh, fresh).status, 202)
    assertEquals(net.json(net.get("/api/recoveries"))("recoveries").list.map(q => (q("name").string, q("key").string)),
      Vector(("Anna", fresh.pub)))
    // asking changes nothing: the community must say it knows who asks
    assertEquals(anna.state("recovering"), Json.Null)
    def confirm(by: Pair) = net.command(by, "cmd" -> Json.str("recover"), "at" -> Json.num(net.now),
      "account" -> Json.str(anna.id))
    assertEquals(confirm(anna.key).status, 403)
    assertEquals(confirm(town.founder.key).status, 200)
    assertEquals(anna.state("recovering")("after").long, net.now + NodeApp.waiting)
    assertEquals(net.json(net.get("/api/recoveries"))("recoveries").list.size, 0)
    // announced, not in force: the new key spends nothing, and making it so is refused until its time
    def spend(by: Pair) = anna.say(Body.Send(town.founder.id, 100, "", ""), by = by)
    assertEquals(spend(fresh).status, 409)
    assertEquals(net.post(s"/api/accounts/${anna.id}/recovered", Json.obj()).status, 409)
    net.now += NodeApp.waiting
    assertEquals(net.post(s"/api/accounts/${anna.id}/recovered", Json.obj()).status, 200)
    assertEquals(spend(fresh).status, 200)
    assertEquals(net.checked(anna.id).map(s => (s.grants.size, s.recovering)), Right((2, None)))
    assertEquals(net.post(s"/api/accounts/${anna.id}/recovered", Json.obj()).status, 404)
    // another time the phone was only mislaid: its owner cancels what someone else started
    val thief = SignerJdk.generate()
    assertEquals(ask(thief, thief).status, 202)
    assertEquals(net.command(town.founder.key, "cmd" -> Json.str("recover"), "at" -> Json.num(net.now),
      "account" -> Json.str(anna.id)).status, 200)
    assertEquals(anna.say(Body.Revoke(thief.pub)).status, 200)
    net.now += NodeApp.waiting
    assertEquals(net.post(s"/api/accounts/${anna.id}/recovered", Json.obj()).status, 404)
    assertEquals(spend(thief).status, 409)
    // an asking the community does not believe is dropped
    assertEquals(ask(thief, thief).status, 202)
    assertEquals(net.command(town.founder.key, "cmd" -> Json.str("forget"), "at" -> Json.num(net.now),
      "account" -> Json.str(anna.id)).status, 200)
    assertEquals(net.json(net.get("/api/recoveries"))("recoveries").list.size, 0)
  }

  test("the board: a member's offers and wants, by its own signed word; taken off by itself alone") {
    val (net, anna, jan) = town()
    def say(w: Wallet, by: Pair, fields: (String, Json)*) =
      val text = Json.print(Json.Obj(Vector("at" -> Json.num(net.now), "account" -> Json.str(w.id)) ++ fields))
      net.post("/api/board", Json.obj("command" -> Json.str(text), "key" -> Json.str(by.pub),
        "sig" -> Json.str(SignerJdk.sign(by.secret, text))))
    def list(w: Wallet, by: Pair, kind: String, title: String, price: Long = 0, share: Long = 100) = say(w, by,
      "cmd" -> Json.str("list"), "kind" -> Json.str(kind), "title" -> Json.str(title), "text" -> Json.str("details"),
      "price" -> Json.num(price), "share" -> Json.num(share))
    def board = net.json(net.get("/api/board"))("listings").list
    assertEquals(board.size, 0)
    val listed = list(anna, anna.key, "offer", "  Trousers   hemmed ", 4000, 50)
    assertEquals(listed.status, 200, listed.body)
    net.now += 1000
    assertEquals(list(jan, jan.key, "want", "A logo").status, 200)
    assertEquals(board.map(l => (l("kind").string, l("title").string, l("price").long, l("share").long,
      l("account").string)), Vector(("want", "A logo", 0L, 100L, jan.id), ("offer", "Trousers hemmed", 4000L, 50L,
      anna.id)))
    // not in another's name, not unsigned, not twice the same word, not nonsense
    assertEquals(list(anna, jan.key, "offer", "Mine").status, 403)
    assertEquals(list(anna, anna.key, "sale", "X").status, 400)
    assertEquals(list(anna, anna.key, "offer", " ").status, 400)
    assertEquals(list(anna, anna.key, "offer", "Free", share = 101).status, 400)
    assertEquals(list(jan, jan.key, "want", "A logo").status, 409)
    // taken off by its own member only
    val entry = net.json(listed)("id").string
    assertEquals(say(jan, jan.key, "cmd" -> Json.str("unlist"), "id" -> Json.str(entry)).status, 404)
    assertEquals(say(anna, anna.key, "cmd" -> Json.str("unlist"), "id" -> Json.str(entry)).status, 200)
    assertEquals(board.map(_("title").string), Vector("A logo"))
    // a member has room for twenty
    for i <- 1 to Directory.own do { net.now += 1000; assertEquals(list(anna, anna.key, "offer", s"Offer $i").status, 200) }
    net.now += 1000
    assertEquals(list(anna, anna.key, "offer", "One more").status, 429)
  }
