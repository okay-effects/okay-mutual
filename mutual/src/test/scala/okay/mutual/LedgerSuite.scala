package okay.mutual

/** THE LEDGER (specs/mutual.md): the balances sum to zero, both limits hold, a change is whole or not at all */
class LedgerSuite extends munit.FunSuite:
  private def network(): World =
    val w = World()
    for id <- Seq("baker", "tailor", "coder") do assertEquals(w.open(id).map(_.balance), Right(0L))
    w

  test("an account opens at zero under a proper id, once") {
    val w = network()
    assertEquals(w.open("baker"), Left(Refusal.Taken("baker")))
    assertEquals(w.open("Bad Id"), Left(Refusal.Invalid("id")))
    assertEquals(w.open("x"), Left(Refusal.Invalid("id")))
    assertEquals(w.open("anna@example.com", Kind.Personal, credit = 0).map(_.floor), Right(0L))
    assertEquals(w.open("neg", credit = -1), Left(Refusal.Invalid("limits")))
    assertEquals(w.balances.values.sum, 0L)
  }

  test("a payment moves the amount from the buyer to the seller; the balances sum to zero") {
    val w = network()
    val t = w.pay("baker", "tailor", 4000, "a hem").toOption.get
    assertEquals((t.seq, t.from, t.to, t.amount, t.memo), (1L, "baker", "tailor", 4000L, "a hem"))
    assertEquals(w.pay("tailor", "coder", 6000).map(_.seq), Right(2L))
    assertEquals(w.balances, Map("baker" -> -4000L, "tailor" -> -2000L, "coder" -> 6000L))
    assertEquals(w.balances.values.sum, 0L)
    assertEquals(w.run(Ledger.journal[w.W](Some("baker"))).map(_.seq), Vector(1L))
    assertEquals(w.run(Ledger.journal[w.W]()).map(_.seq), Vector(1L, 2L))
  }

  test("the buyer's floor and the seller's ceiling both hold; a refusal writes nothing") {
    val w = network()
    assertEquals(w.pay("baker", "tailor", 10001), Left(Refusal.Credit(1)))
    assertEquals(w.pay("baker", "tailor", 10000).isRight, true)
    assertEquals(w.pay("baker", "coder", 1), Left(Refusal.Credit(1)))
    assertEquals(w.pay("coder", "tailor", 10000).isRight, true)
    // the tailor at 20000 of 30000: 10001 more is over by 1, and the payer is not charged
    val rich = w.open("rich", credit = 50000).toOption.get
    assertEquals(w.pay(rich.id, "tailor", 10001), Left(Refusal.Ceiling(1)))
    assertEquals(w.account("rich").balance, 0L)
    assertEquals(w.pay("baker", "baker", 1), Left(Refusal.Invalid("same")))
    assertEquals(w.pay("tailor", "baker", 0), Left(Refusal.Invalid("amount")))
    assertEquals(w.pay("tailor", "nobody", 1), Left(Refusal.Unknown("nobody")))
    assertEquals(w.run(Ledger.journal[w.W]()).size, 2)
    assertEquals(w.balances.values.sum, 0L)
  }

  test("limits change, never to a floor above the debt there is") {
    val w = network()
    w.pay("baker", "tailor", 8000)
    assertEquals(w.run(Ledger.limit[w.W]("baker", 7999, 30000)), Left(Refusal.Credit(1)))
    assertEquals(w.run(Ledger.limit[w.W]("baker", 20000, 50000)).map(a => (a.floor, a.ceiling)),
      Right((-20000L, 50000L)))
    assertEquals(w.account("baker").balance, -8000L)
  }

  test("a line is reversed by its opposite, once, whatever the limits say") {
    val w = network()
    w.pay("baker", "tailor", 10000)
    w.pay("tailor", "coder", 20000) // the tailor at -10000, its floor: it could not pay the baker back as a deal
    val back = w.run(Ledger.reverse[w.W](1, w.now)).toOption.get
    assertEquals((back.from, back.to, back.amount, back.reverses), ("tailor", "baker", 10000L, Some(1L)))
    assertEquals(w.balances, Map("baker" -> 0L, "tailor" -> -20000L, "coder" -> 20000L))
    assertEquals(w.run(Ledger.reverse[w.W](1, w.now)), Left(Refusal.Reversed(1)))
    assertEquals(w.run(Ledger.reverse[w.W](back.seq, w.now)), Left(Refusal.Reversed(back.seq)))
    assertEquals(w.run(Ledger.reverse[w.W](99, w.now)), Left(Refusal.Invalid("seq")))
    assertEquals(Measures.deals(w.run(Ledger.journal[w.W]())).map(_.seq), Vector(2L))
  }

  test("vouching lends the voucher's own line to a newcomer; released when the newcomer stands alone") {
    val w = network()
    w.open("anna", Kind.Personal, credit = 0)
    assertEquals(w.pay("anna", "baker", 100), Left(Refusal.Credit(100)))
    assertEquals(w.run(Ledger.vouch[w.W]("baker", "anna", 3000)).map(_.floor), Right(-3000L))
    assertEquals(w.account("baker").floor, -7000L)
    assertEquals(w.pay("anna", "tailor", 3000).isRight, true)
    // one voucher a newcomer; the voucher's debt must fit the line it keeps
    assertEquals(w.run(Ledger.vouch[w.W]("coder", "anna", 1000)), Left(Refusal.Vouched("anna")))
    w.pay("baker", "coder", 7000)
    assertEquals(w.run(Ledger.vouch[w.W]("baker", "anna", 1)), Left(Refusal.Credit(1)))
    // anna owes 3000 on the lent line: not released until she has earned it back
    assertEquals(w.run(Ledger.release[w.W]("anna")), Left(Refusal.Credit(3000)))
    w.pay("tailor", "anna", 3000)
    assertEquals(w.run(Ledger.release[w.W]("anna")).map(a => (a.floor, a.voucher)), Right((0L, None)))
    assertEquals(w.account("baker").floor, -10000L)
    assertEquals(w.run(Ledger.release[w.W]("anna")), Left(Refusal.Vouched("anna")))
    assertEquals(w.balances.values.sum, 0L)
  }

  test("a commit that loses a race is run again: no amount is lost or made") {
    val w = network()
    var raced = false
    w.storage.beforeCommit = () => if !raced then
      raced = true
      w.pay("coder", "tailor", 500)
    assertEquals(w.pay("baker", "tailor", 4000).map(_.seq), Right(2L))
    assertEquals(w.balances, Map("baker" -> -4000L, "coder" -> -500L, "tailor" -> 4500L))
  }

  test("the measures: velocity, who both sold and bought, stale balances, first sale, repeat deals") {
    val w = network()
    w.now += 2 * w.day
    w.pay("baker", "tailor", 4000) // the tailor's first sale: 2 days after joining
    w.now += 2 * w.day
    w.pay("tailor", "coder", 1000) // the coder's first sale: 4 days
    w.pay("baker", "tailor", 2000) // the same pair again
    val m = w.measures
    assertEquals((m.accounts, m.turnover, m.positive, m.sum), (3, 7000L, 6000L, 0L))
    assertEqualsDouble(m.velocity, 7000.0 / 6000.0, 1e-9)
    assertEqualsDouble(m.both, 1.0 / 3.0, 1e-9) // the tailor alone both sold and bought
    assertEqualsDouble(m.firstSale, 3.0, 1e-9)
    assertEqualsDouble(m.repeat, 1.0 / 3.0, 1e-9)
    assertEquals(m.stale, 0.0)
    // 61 days of silence: every positive balance is stale, nothing turned over
    w.now += 61 * w.day
    val later = w.measures
    assertEquals((later.turnover, later.velocity, later.stale), (0L, 0.0, 1.0))
    assertEquals(World().measures, Measures(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0))
  }

  test("the credit line a business's sales suggest: two months of the three months' average, capped") {
    assertEquals(Rules.suggested(90000, 2.0, 500000), 60000L)
    assertEquals(Rules.suggested(9000000, 2.0, 500000), 500000L)
    val w = network()
    w.pay("baker", "tailor", 9000)
    assertEquals(Measures.sales("tailor", w.run(Ledger.journal[w.W]()), w.now), 9000L)
    assertEquals(Measures.sales("tailor", w.run(Ledger.journal[w.W]()), w.now + 91 * w.day), 0L)
  }
