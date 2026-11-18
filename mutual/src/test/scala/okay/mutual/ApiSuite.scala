package okay.mutual

/** THE LEDGER'S CALLS, as a host sends them */
class ApiSuite extends munit.FunSuite:
  private def network(): World =
    val w = World()
    for id <- Seq("baker", "tailor") do
      assertEquals(w.ask("PUT", s"/accounts/$id",
        s"""{"kind":"business","name":"The $id","credit":10000,"ceiling":30000}""").status, 200)
    w

  test("accounts opened, listed, read with their journal; a PUT of one that is there sets its limits") {
    val w = network()
    assertEquals(w.json(w.ask("GET", "/accounts"))("accounts").list.map(_("id").string), Vector("baker", "tailor"))
    assertEquals(w.ask("PUT", "/accounts/Bad Id", """{"kind":"personal","name":"x","credit":0,"ceiling":0}""")
      .status, 400)
    assertEquals(w.ask("PUT", "/accounts/anna", """{"name":"Anna"}""").status, 400)
    assertEquals(w.ask("PUT", "/accounts/anna", "not json").status, 400)
    val set = w.json(w.ask("PUT", "/accounts/baker", """{"credit":20000,"ceiling":40000}"""))
    assertEquals((set("floor").long, set("ceiling").long, set("name").string), (-20000L, 40000L, "The baker"))
    assertEquals(w.ask("GET", "/accounts/nobody").status, 404)
    // the holder: said when opened, changed with the limits, kept when not said
    val held = w.json(w.ask("PUT", "/accounts/anna",
      """{"kind":"personal","name":"Anna","credit":0,"ceiling":5000,"holder":"Anna@Example.com"}"""))
    assertEquals(held("holder").string, "anna@example.com")
    assertEquals(w.json(w.ask("PUT", "/accounts/anna", """{"credit":0,"ceiling":6000}"""))("holder").string,
      "anna@example.com")
    assertEquals(w.json(w.ask("PUT", "/accounts/baker",
      """{"credit":20000,"ceiling":40000,"holder":"jan@example.com"}"""))("holder").string, "jan@example.com")
  }

  test("a named payment sent twice (a retry) makes one line") {
    val w = network()
    val named = """{"from":"tailor","to":"baker","amount":100,"key":"retry-1"}"""
    assertEquals(w.json(w.ask("POST", "/pay", named))("seq").long, 1L)
    assertEquals(w.json(w.ask("POST", "/pay", named))("seq").long, 1L)
    assertEquals(w.json(w.ask("GET", "/journal"))("journal").list.size, 1)
  }

  test("a payment answered with its line; a refusal with its code and by how much") {
    val w = network()
    val paid = w.json(w.ask("POST", "/pay", """{"from":"baker","to":"tailor","amount":4000,"memo":"a hem"}"""))
    assertEquals((paid("seq").long, paid("amount").long, paid("at").long), (1L, 4000L, w.now))
    val no = w.ask("POST", "/pay", """{"from":"baker","to":"tailor","amount":6001}""")
    assertEquals((no.status, w.json(no)("error").string, w.json(no)("detail").long), (409, "credit", 1L))
    assertEquals(w.ask("POST", "/pay", """{"from":"baker","to":"nobody","amount":1}""").status, 404)
    assertEquals(w.ask("POST", "/pay", """{"from":"baker"}""").status, 400)
    val seen = w.json(w.ask("GET", "/accounts/tailor"))
    assertEquals((seen("account")("balance").long, seen("journal").list.size), (4000L, 1))
    // two months of 4000 over three months
    assertEquals(seen("suggested").long, 2667L)
    assertEquals(w.json(w.ask("GET", "/journal"))("journal").list.size, 1)
  }

  test("reverse, vouch, release and the measures") {
    val w = network()
    w.ask("PUT", "/accounts/anna", """{"kind":"personal","name":"Anna","credit":0,"ceiling":10000}""")
    val lent = w.json(w.ask("POST", "/vouch", """{"voucher":"baker","newcomer":"anna","amount":2000}"""))
    assertEquals((lent("floor").long, lent("voucher").string), (-2000L, "baker"))
    assertEquals(w.ask("POST", "/pay", """{"from":"anna","to":"tailor","amount":2000}""").status, 200)
    assertEquals(w.json(w.ask("POST", "/release", """{"newcomer":"anna"}"""))("error").string, "credit")
    val back = w.json(w.ask("POST", "/reverse", """{"seq":1}"""))
    assertEquals((back("reverses").long, back("from").string), (1L, "tailor"))
    assertEquals(w.ask("POST", "/reverse", """{"seq":1}""").status, 409)
    assertEquals(w.json(w.ask("POST", "/release", """{"newcomer":"anna"}"""))("floor").long, 0L)
    val m = w.json(w.ask("GET", "/measures"))
    assertEquals((m("accounts").int, m("sum").long, m("turnover").long), (3, 0L, 0L))
    assertEquals(w.ask("GET", "/nothing").status, 404)
  }
