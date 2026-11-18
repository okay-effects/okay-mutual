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
