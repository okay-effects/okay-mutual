package okay.mutual.chain

import okay.scala3.*

/** THE CHAINS' RULES (specs/protocol.md "An account's chain", "Keys, devices and who may act"), signatures faked */
class ChainSuite extends munit.FunSuite:
  private val node = Fake.pair("node")
  private val phone = Fake.pair("phone")
  private val laptop = Fake.pair("laptop")
  private val tablet = Fake.pair("tablet")
  private val day = 86400000L

  /** an account opened by the phone, with a line of 100.00 and a ceiling of 300.00 */
  private def account(): Writer =
    val w = Writer(node, phone)
    assert(w.node(Body.Limits(10000, 30000)).isRight)
    w

  test("SHA-256: the standard's vectors, one and two blocks") {
    assertEquals(Sha256.hex(""), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    assertEquals(Sha256.hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    assertEquals(Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
      "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1")
    assertEquals(Sha256.hex("a" * 1000), "41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3")
    assertEquals(Sha256.hex("zażółć gęślą jaźń"), Sha256.hex("zażółć gęślą jaźń"))
  }

  test("a record's text is read back as it was written; a text of another version or shape is no record") {
    val bodies = Vector[Body](Body.Open("N", "personal", "K", "PLN"), Body.Send("to", 4000, "memo", "k1"),
      Body.Receive("from", "ref", 1), Body.Bounce("ref", 2), Body.Limits(1, 2),
      Body.Granted(Grant("K2", spend = true, manage = false, 100, 500, 7 * day)), Body.Revoke("K2"),
      Body.Vouch("to", 3), Body.Vouched("from", "ref", 3), Body.Release(3), Body.Released("ref", 3),
      Body.Recover("K3", 99), Body.Recovered("K3"), Body.Two(5000), Body.Propose("to", 9000, "memo", "k2"), Body.Close())
    for b <- bodies do
      val r = Record("acc", 7, "prev", 123L, b)
      assertEquals(Record.parse(Record.text(r)), Some(r), b.kind)
    assertEquals(Record.parse("{}"), None)
    assertEquals(Record.parse("not json"), None)
    assertEquals(Record.parse(Record.text(Record("a", 1, "", 1, Body.Release(1))).replace("\"v\":1", "\"v\":2")),
      None)
    val s = Signed("text", Vector("K" -> "sig"))
    assertEquals(Signed.parse(Signed.json(s)), Some(s))
    val acted = Signed("text", Vector("K" -> "sig", "N" -> "sig2"), Vector(Acting("K", "anna", 3)))
    assertEquals(Signed.parse(Signed.json(acted)), Some(acted))
  }

  test("an account shared: its holders are accounts, a person's device signs for the person's — while it holds it") {
    val jan = Fake.pair("jan")
    val (anna, jans) = (Writer(node, phone), Writer(node, jan))
    // the family's account: opened by Anna's ACCOUNT — her phone signs, the record says for whom
    val open = Record("", 1, "", 2000, Body.Open(node.pub, "family", anna.id, "PLN"))
    def held(a: Acting): Boolean ! Fake.W = Check.holds[Fake.W](if a.account == anna.id then anna.records
      else jans.records, a)
    def signedFor(r: Record, by: Pair, who: Writer): Signed =
      val s = Fake(Check.signed[Fake.W](r, node, by))
      s.copy(acts = Vector(Acting(by.pub, who.id, who.state.get.seq)))
    var chain = Vector(signedFor(open, phone, anna))
    def state = Fake(Check.chain[Fake.W](chain, held))
    def say(by: Pair, who: Writer, at: Long)(body: Body) =
      val s = state.toOption.get
      val next = chain :+ signedFor(Record(s.id, s.seq + 1, s.head, at, body), by, who)
      val made = Fake(Check.chain[Fake.W](next, held))
      if made.isRight then chain = next
      made.left.map(_._2)
    assertEquals(state.map(_.grants), Right(Vector(Grant(anna.id, spend = true, manage = true))))
    // the same records with nobody answering for the acts: the holder did not sign
    assertEquals(Fake(Check.chain[Fake.W](chain)), Left(0 -> Fault.Unsigned("holder")))
    // a key that does not hold the account it names signs for nobody
    assertEquals(Fake(Check.chain[Fake.W](Vector(signedFor(open, jan, anna)), held)), Left(0 -> Fault.Unsigned("holder")))
    // Jan's account a holder, 50.00 a week; his device pays for the family within it
    assert(say(phone, anna, 2001)(Body.Granted(Grant(jans.id, spend = true, manage = false, 0, 5000, 7 * day))).isRight)
    assert(Fake(Check.signed[Fake.W](Record(state.toOption.get.id, 3, state.toOption.get.head, 2002,
      Body.Limits(10000, 30000)), node).map(s => { chain :+= s; state })).isRight)
    assert(say(jan, jans, 2003)(Body.Send("shop", 4000, "", "a")).isRight)
    assertEquals(say(jan, jans, 2004)(Body.Send("shop", 2000, "", "b")), Left(Fault.Limit("period")))
    assertEquals(say(jan, jans, 2005)(Body.Two(1)), Left(Fault.Forbidden("manage")))
    // Anna's laptop, signed in to HER account, acts for the family at once; her phone, taken off, no more
    assert(anna.by(phone)(Body.Granted(Grant(laptop.pub, spend = true, manage = true))).isRight)
    assert(say(laptop, anna, 2006)(Body.Send("shop", 1000, "", "c")).isRight)
    assert(anna.by(laptop)(Body.Revoke(phone.pub)).isRight)
    assertEquals(say(phone, anna, 2007)(Body.Send("shop", 1000, "", "d")), Left(Fault.Unsigned("holder")))
    assertEquals(state.map(_.balance), Right(-5000L))
  }

  test("an account closed: by a holder that may manage or by the node alone, at zero; nothing after it") {
    val w = account()
    assert(w.by(phone)(Body.Granted(Grant(laptop.pub, spend = true, manage = false))).isRight)
    assert(w.node(Body.Receive("from", "r1", 500)).isRight)
    assertEquals(w.by(phone)(Body.Close()), Left(Fault.Forbidden("balance")))
    assert(w.by(phone)(Body.Send("to", 500, "", "a")).isRight)
    assert(w.node(Body.Vouched("v", "r2", 1000)).isRight)
    assertEquals(w.by(phone)(Body.Close()), Left(Fault.Forbidden("line")))
    assert(w.node(Body.Release(1000)).isRight)
    // a holder that may only spend does not close it; one that may manage does
    assertEquals(w.by(laptop)(Body.Close()), Left(Fault.Forbidden("manage")))
    assertEquals(w.by(phone)(Body.Close()).map(_.closed), Right(true))
    // nothing is its next record any more: not a payment to it, not its holder's
    assertEquals(w.node(Body.Receive("from", "r3", 100)), Left(Fault.Place("closed")))
    assertEquals(w.by(phone)(Body.Send("to", 1, "", "b")), Left(Fault.Place("closed")))
    assertEquals(StateJson.read(StateJson.write(w.state.get)), w.state)
    // the node alone closes one whose member left: the community's decision
    val gone = account()
    assertEquals(gone.node(Body.Close()).map(_.closed), Right(true))
    assertEquals(Fake(Check.chain[Fake.W](gone.records)).map(_.closed), Right(true))
  }

  test("two holders: above the threshold a payment is proposed by one and sent by another, within a week") {
    val w = account()
    assert(w.node(Body.Limits(100000, 300000)).isRight)
    assert(w.by(phone)(Body.Granted(Grant(laptop.pub, spend = true, manage = false))).isRight)
    assert(w.by(phone)(Body.Granted(Grant(tablet.pub, spend = true, manage = false))).isRight)
    assertEquals(w.by(laptop)(Body.Two(5000)), Left(Fault.Forbidden("manage")))
    assertEquals(w.by(phone)(Body.Two(5000)).map(_.two), Right(5000L))
    // at the threshold and under it: one holder
    assertEquals(w.by(laptop)(Body.Send("shop", 5000, "", "a")).map(_.balance), Right(-5000L))
    assertEquals(w.by(laptop)(Body.Send("shop", 5001, "m", "b")), Left(Fault.Unsigned("second")))
    assertEquals(w.by(laptop)(Body.Propose("shop", 5001, "m", "b")).map(s => (s.balance, s.proposed.size)),
      Right((-5000L, 1)))
    // not by the one who proposed, not another payment
    assertEquals(w.by(laptop)(Body.Send("shop", 5001, "m", "b")), Left(Fault.Unsigned("second")))
    assertEquals(w.by(tablet)(Body.Send("shop", 5002, "m", "b")), Left(Fault.Unsigned("second")))
    assertEquals(w.by(tablet)(Body.Send("thief", 5001, "m", "b")), Left(Fault.Unsigned("second")))
    assertEquals(w.by(tablet)(Body.Send("shop", 5001, "m", "b")).map(s => (s.balance, s.proposed.size)),
      Right((-10001L, 0)))
    // taken once
    assertEquals(w.by(phone)(Body.Send("shop", 5001, "m", "b2")), Left(Fault.Unsigned("second")))
    // a proposal lapses in a week
    assert(w.by(laptop)(Body.Propose("shop", 6000, "", "c")).isRight)
    w.now += Chain.life
    assertEquals(w.by(tablet)(Body.Send("shop", 6000, "", "c")), Left(Fault.Unsigned("second")))
    // the threshold off: one holder again
    assert(w.by(phone)(Body.Two(0)).isRight)
    assertEquals(w.by(tablet)(Body.Send("shop", 6000, "", "c")).map(_.balance), Right(-16001L))
    assertEquals(StateJson.read(StateJson.write(w.state.get)), w.state)
  }

  test("an account opens by its node and its first key; its id is the hash of that record") {
    val w = Writer(node, phone)
    val s = w.state.get
    assertEquals((s.id, s.head, s.seq, s.balance, s.node), (w.records.head.hash, w.records.head.hash, 1L, 0L, node.pub))
    assertEquals(s.grants, Vector(Grant(phone.pub, spend = true, manage = true)))
    val open = Record("", 1, "", 1, Body.Open(node.pub, "personal", phone.pub, "PLN"))
    def opened(by: Pair*) = Fake(Check.signed[Fake.W](open, by*).flatMap(Check.step[Fake.W](None, _)))
    assertEquals(opened(phone), Left(Fault.Unsigned("node")))
    assertEquals(opened(node), Left(Fault.Unsigned("holder")))
    assertEquals(opened(node, phone).map(_.seq), Right(1L))
    assertEquals(Fake(Check.signed[Fake.W](open.copy(seq = 2), node, phone).flatMap(Check.step[Fake.W](None, _))),
      Left(Fault.Place("open")))
  }

  test("a record is the next only in its place: its account, its number, the head before it, not earlier") {
    val w = account()
    val good = w.next(Body.Send("other", 100, "", ""))
    assertEquals(w.add(good.copy(account = "other"), node, phone), Left(Fault.Place("account")))
    assertEquals(w.add(good.copy(seq = good.seq + 1), node, phone), Left(Fault.Place("seq")))
    assertEquals(w.add(good.copy(prev = "0" * 64), node, phone), Left(Fault.Place("prev")))
    assertEquals(w.add(good.copy(at = w.now - 1), node, phone), Left(Fault.Place("time")))
    assertEquals(w.add(good, node, phone).map(_.seq), Right(3L))
    // the same record again is no longer the next
    assertEquals(w.add(good, node, phone), Left(Fault.Place("seq")))
  }

  test("a debit needs a holder's key and the node's; a credit and a rule are the node's alone") {
    val w = account()
    val send = w.next(Body.Send("other", 100, "", ""))
    assertEquals(w.add(send, node), Left(Fault.Unsigned("holder")))
    assertEquals(w.add(send, phone), Left(Fault.Unsigned("node")))
    assertEquals(w.add(send, node, laptop), Left(Fault.Unsigned("holder")))
    // a signature of another text is none
    val forged = Signed(Record.text(send), Vector(node.pub -> "00", phone.pub -> "00"))
    assertEquals(Fake(Check.step[Fake.W](w.state, forged)), Left(Fault.Unsigned("node")))
    assertEquals(w.add(send, node, phone).map(_.balance), Right(-100L))
    assertEquals(w.add(w.next(Body.Receive("other", "ref", 50)), phone), Left(Fault.Unsigned("node")))
    assertEquals(w.node(Body.Receive("other", "ref", 50)).map(_.balance), Right(-50L))
  }

  test("the balance is the fold: sends within the floor, receipts within the ceiling, a bounce returns a send") {
    val w = account()
    assertEquals(w.by(phone)(Body.Send("b", 10001, "", "")), Left(Fault.Credit(1)))
    assertEquals(w.by(phone)(Body.Send("b", 0, "", "")), Left(Fault.Amount))
    assertEquals(w.by(phone)(Body.Send(w.id, 1, "", "")), Left(Fault.Forbidden("same")))
    assertEquals(w.by(phone)(Body.Send("b", 4000, "", "k1")).map(_.balance), Right(-4000L))
    val sent = w.state.get.head
    // a payment's name is in the chain once
    assertEquals(w.by(phone)(Body.Send("b", 4000, "", "k1")), Left(Fault.Named))
    assertEquals(w.node(Body.Receive("c", "r", 34001)), Left(Fault.Ceiling(1)))
    assertEquals(w.node(Body.Receive("c", "r", 34000)).map(_.balance), Right(30000L))
    assertEquals(w.node(Body.Bounce("nothing", 4000)), Left(Fault.Unknown("nothing")))
    assertEquals(w.node(Body.Bounce(sent, 3999)), Left(Fault.Unknown(sent)))
    assertEquals(w.node(Body.Bounce(sent, 4000)).map(_.balance), Right(34000L))
    assertEquals(w.node(Body.Bounce(sent, 4000)), Left(Fault.Unknown(sent)))
    // the limits change, never to a floor above the debt there is
    val d = account()
    d.by(phone)(Body.Send("b", 8000, "", ""))
    assertEquals(d.node(Body.Limits(7999, 30000)), Left(Fault.Credit(1)))
    assertEquals(d.node(Body.Limits(20000, 30000)).map(_.floor), Right(-20000L))
  }

  test("a device has its own key: granted by one that may manage, never more than the giver has, limited") {
    val w = account()
    w.node(Body.Limits(100000, 300000))
    // the laptop: a full holder; the tablet under it: 50.00 a payment, 120.00 a week
    assertEquals(w.by(laptop)(Body.Granted(Grant(laptop.pub, true, true))), Left(Fault.Unsigned("holder")))
    assert(w.by(phone)(Body.Granted(Grant(laptop.pub, spend = true, manage = true))).isRight)
    assertEquals(w.by(phone)(Body.Granted(Grant(laptop.pub, true, true))), Left(Fault.Forbidden("held")))
    assert(w.by(laptop)(Body.Granted(Grant(tablet.pub, spend = true, manage = false, 5000, 12000, 7 * day))).isRight)
    assertEquals(w.state.get.grant(tablet.pub).flatMap(_.under), Some(laptop.pub))
    assertEquals(w.by(tablet)(Body.Granted(Grant("Pkid", true, false))), Left(Fault.Forbidden("manage")))
    assertEquals(w.by(tablet)(Body.Send("b", 5001, "", "")), Left(Fault.Limit("payment")))
    assert(w.by(tablet)(Body.Send("b", 5000, "", "")).isRight)
    w.now += day
    assert(w.by(tablet)(Body.Send("b", 5000, "", "")).isRight)
    assertEquals(w.by(tablet)(Body.Send("b", 2001, "", "")), Left(Fault.Limit("period")))
    // the phone's spending is its own: no limit, and not counted as the tablet's
    assert(w.by(phone)(Body.Send("b", 20000, "", "")).isRight)
    // a week after the first payment its amount is out of the window
    w.now += 6 * day + 1
    assert(w.by(tablet)(Body.Send("b", 5000, "", "")).isRight)
    assertEquals(w.by(tablet)(Body.Send("b", 2001, "", "")), Left(Fault.Limit("period")))
    // a limited giver gives no more than it has, and a period with its window
    val l = account()
    val giver = Grant(laptop.pub, spend = true, manage = true, 5000, 12000, 7 * day)
    assert(l.by(phone)(Body.Granted(giver)).isRight)
    assertEquals(l.by(laptop)(Body.Granted(Grant(tablet.pub, true, false))), Left(Fault.Limit("payment")))
    assertEquals(l.by(laptop)(Body.Granted(Grant(tablet.pub, true, false, 5001, 12000, 7 * day))),
      Left(Fault.Limit("payment")))
    assertEquals(l.by(laptop)(Body.Granted(Grant(tablet.pub, true, false, 5000, 12001, 7 * day))),
      Left(Fault.Limit("period")))
    assertEquals(l.by(laptop)(Body.Granted(Grant(tablet.pub, true, false, 5000, 12000, day))),
      Left(Fault.Limit("period")))
    assertEquals(l.by(phone)(Body.Granted(Grant(tablet.pub, true, false, 0, 100, 0))), Left(Fault.Amount))
    assert(l.by(laptop)(Body.Granted(Grant(tablet.pub, true, false, 2000, 6000, 7 * day))).isRight)
  }

  test("a lost device is cut off with all it gave; the holders that may manage are equals; one always stays") {
    val w = account()
    w.by(phone)(Body.Granted(Grant(laptop.pub, spend = true, manage = true)))
    w.by(phone)(Body.Granted(Grant(tablet.pub, spend = true, manage = false, 5000, 12000, 7 * day)))
    val watch = Fake.pair("watch")
    w.by(laptop)(Body.Granted(Grant(watch.pub, spend = true, manage = false, 1000, 2000, day)))
    assertEquals(w.by(tablet)(Body.Revoke(phone.pub)), Left(Fault.Forbidden("manage")))
    assertEquals(w.by(phone)(Body.Revoke("Pnobody")), Left(Fault.Unknown("Pnobody")))
    // the phone — the first key — is lost: the laptop takes it off, and the tablet the phone gave goes with it
    assertEquals(w.by(laptop)(Body.Revoke(phone.pub)).map(_.grants.map(g => g.key -> g.under)),
      Right(Vector(laptop.pub -> None, watch.pub -> Some(laptop.pub))))
    assertEquals(w.by(phone)(Body.Send("b", 1, "", "")), Left(Fault.Unsigned("holder")))
    assertEquals(w.by(tablet)(Body.Send("b", 1, "", "")), Left(Fault.Unsigned("holder")))
    assert(w.by(watch)(Body.Send("b", 1, "", "")).isRight)
    // the last that may manage does not take itself off
    assertEquals(w.by(laptop)(Body.Revoke(laptop.pub)), Left(Fault.Forbidden("last")))
  }

  test("vouching: a line lent by one that may manage, within its floor; returned in parts") {
    val voucher = account()
    val newcomer = Writer(node, laptop, "personal")
    newcomer.node(Body.Limits(0, 10000))
    assertEquals(newcomer.by(laptop)(Body.Send(voucher.id, 100, "", "")), Left(Fault.Credit(100)))
    voucher.by(phone)(Body.Granted(Grant(tablet.pub, spend = true, manage = false)))
    assertEquals(voucher.by(tablet)(Body.Vouch(newcomer.id, 3000)), Left(Fault.Forbidden("manage")))
    assertEquals(voucher.by(phone)(Body.Vouch(voucher.id, 3000)), Left(Fault.Forbidden("same")))
    assertEquals(voucher.by(phone)(Body.Vouch(newcomer.id, 3000)).map(_.floor), Right(-7000L))
    val vouch = voucher.state.get.head
    assertEquals(newcomer.node(Body.Vouched(voucher.id, vouch, 3000)).map(_.floor), Right(-3000L))
    assertEquals(newcomer.node(Body.Vouched("another", "r", 1)), Left(Fault.Forbidden("vouched")))
    assert(newcomer.by(laptop)(Body.Send("b", 3000, "", "")).isRight)
    // the voucher's debt must fit the line it keeps
    voucher.by(phone)(Body.Send("b", 7000, "", ""))
    assertEquals(voucher.by(phone)(Body.Vouch(newcomer.id, 1)), Left(Fault.Credit(1)))
    // the newcomer owes 3000 on the lent line: 1000 of it back once 1000 is earned
    assertEquals(newcomer.node(Body.Release(1000)), Left(Fault.Credit(1000)))
    newcomer.node(Body.Receive("b", "r", 1000))
    assertEquals(newcomer.node(Body.Release(1000)).map(s => (s.floor, s.voucher)), Right((-2000L, Some(voucher.id))))
    assertEquals(voucher.node(Body.Released(vouch, 1000)).map(_.floor), Right(-8000L))
    assertEquals(voucher.node(Body.Released(vouch, 2001)), Left(Fault.Unknown(vouch)))
    newcomer.node(Body.Receive("b", "r", 2000))
    assertEquals(newcomer.node(Body.Release(2000)).map(s => (s.floor, s.voucher)), Right((0L, None)))
    assertEquals(voucher.node(Body.Released(vouch, 2000)).map(s => (s.floor, s.lends.size)), Right((-10000L, 0)))
    assertEquals(newcomer.node(Body.Release(1)), Left(Fault.Amount))
  }

  test("a whole chain is checked from its opening record: the account, or the first fault and where") {
    val w = account()
    w.by(phone)(Body.Send("b", 4000, "", "k1"))
    w.node(Body.Receive("c", "r", 1000))
    assertEquals(Fake(Check.chain[Fake.W](w.records)).map(s => (s.id, s.seq, s.balance)), Right((w.id, 4L, -3000L)))
    // one record changed after it was signed: its signatures no longer hold
    val changed = w.records.updated(2, w.records(2).copy(text = w.records(2).text.replace("4000", "40")))
    assertEquals(Fake(Check.chain[Fake.W](changed)), Left(2 -> Fault.Unsigned("node")))
    // one record left out: the next does not follow
    assertEquals(Fake(Check.chain[Fake.W](w.records.patch(2, Nil, 1))), Left(2 -> Fault.Place("seq")))
    assertEquals(Fake(Check.chain[Fake.W](Vector.empty)), Left(0 -> Fault.Malformed))
  }

  test("a fork's proof: two records in one place, and who signed both") {
    val w = account()
    val a = w.next(Body.Send("b", 4000, "", ""))
    val b = w.next(Body.Send("c", 9000, "", ""))
    def signed(r: Record, by: Pair*) = Fake(Check.signed[Fake.W](r, by*))
    def fork(x: Signed, y: Signed) = Fake(Check.fork[Fake.W](x, y))
    // the node ordered both: its key is in the proof, with the holder's
    assertEquals(fork(signed(a, node, phone), signed(b, node, phone)), Set(node.pub, phone.pub))
    // the holder signed two, the node took one: the holder's key alone
    assertEquals(fork(signed(a, node, phone), signed(b, phone)), Set(phone.pub))
    // the same record twice, another place, an opening record, a bad signature: nothing proved
    assertEquals(fork(signed(a, node, phone), signed(a, node, phone)), Set.empty[String])
    assertEquals(fork(signed(a, node), signed(b.copy(seq = b.seq + 1), node)), Set.empty[String])
    assertEquals(fork(signed(a, node), Signed(Record.text(b), Vector(node.pub -> "00"))), Set.empty[String])
  }

  test("a node's head: the root over the accounts' heads, a proof for each, false for any other head") {
    assertEquals(Merkle.root(Map.empty), Merkle.empty)
    for n <- Seq(1, 2, 3, 4, 5, 8, 13) do
      val heads = (1 to n).map(i => s"account$i" -> Sha256.hex(s"head$i")).toMap
      val root = Merkle.root(heads)
      for (account, head) <- heads do
        val proof = Merkle.proof(heads, account).get
        assert(Merkle.holds(root, account, head, proof), s"$n: $account")
        assert(!Merkle.holds(root, account, Sha256.hex("another"), proof), s"$n: $account, another head")
        assert(!Merkle.holds(root, "stranger", head, proof), s"$n: $account, another account")
      assertEquals(Merkle.proof(heads, "stranger"), None)
      assertNotEquals(Merkle.root(heads.updated("account1", Sha256.hex("changed"))), root)
    // the order the accounts came in does not matter
    assertEquals(Merkle.root(Map("a" -> "1", "b" -> "2")), Merkle.root(Map("b" -> "2", "a" -> "1")))
  }

  test("a state is written and read back the same") {
    val w = account()
    w.by(phone)(Body.Granted(Grant(tablet.pub, spend = true, manage = false, 5000, 12000, 7 * day)))
    w.by(tablet)(Body.Send("b", 4000, "", "k1"))
    w.by(phone)(Body.Vouch("n", 1000))
    w.node(Body.Vouched("v", "r", 500))
    assertEquals(StateJson.read(StateJson.write(w.state.get)), w.state)
    assertEquals(StateJson.read(okay.effects.json.Json.obj()), None)
  }

  test("recovery: announced by the node, in force only after its time, cancelled by a device that still holds") {
    val w = account()
    val fresh = Fake.pair("fresh")
    // a holder does not announce one, and nothing is in force that was not announced
    assertEquals(w.add(w.next(Body.Recover(fresh.pub, w.now + 7 * day)), phone), Left(Fault.Unsigned("node")))
    assertEquals(w.node(Body.Recovered(fresh.pub)), Left(Fault.Unknown(fresh.pub)))
    assertEquals(w.node(Body.Recover(phone.pub, w.now + 7 * day)), Left(Fault.Forbidden("held")))
    assertEquals(w.node(Body.Recover(fresh.pub, w.now - 1)), Left(Fault.Place("time")))
    assertEquals(w.node(Body.Recover(fresh.pub, w.now + 7 * day)).map(_.recovering), Right(Some(fresh.pub -> (w.now + 7 * day))))
    // one at a time; the new key holds nothing while it waits; not in force a moment early
    assertEquals(w.node(Body.Recover("Pother", w.now + 7 * day)), Left(Fault.Forbidden("recovering")))
    assertEquals(w.by(fresh)(Body.Send("b", 1, "", "")), Left(Fault.Unsigned("holder")))
    w.now += 7 * day - 1
    assertEquals(w.node(Body.Recovered(fresh.pub)), Left(Fault.Place("time")))
    w.now += 1
    assertEquals(w.node(Body.Recovered(fresh.pub)).map(s => (s.recovering, s.grants.map(_.key))),
      Right((None, Vector(phone.pub, fresh.pub))))
    assert(w.by(fresh)(Body.Send("b", 100, "", "")).isRight)
    // the phone was only mislaid: its owner cancels a recovery someone else started
    val v = account()
    v.node(Body.Recover(fresh.pub, v.now + 7 * day))
    v.by(phone)(Body.Granted(Grant(tablet.pub, spend = true, manage = false)))
    assertEquals(v.by(tablet)(Body.Revoke(fresh.pub)), Left(Fault.Forbidden("manage")))
    assertEquals(v.by(phone)(Body.Revoke(fresh.pub)).map(_.recovering), Right(None))
    v.now += 8 * day
    assertEquals(v.node(Body.Recovered(fresh.pub)), Left(Fault.Unknown(fresh.pub)))
    assertEquals(StateJson.read(StateJson.write(w.state.get)), w.state)
  }
