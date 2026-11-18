package okay.mutual.wallet

import okay.business.scala3.*
import okay.effects.json.{Descent, Json}
import okay.mutual.chain.*
import okay.mutual.node.{Hubs, Net as Town}
import okay.scala3.std.Clock
import okay.serverless.cloud.Request

/** A DEVICE IN A TEST: its own key (the JDK's), what its wallet remembers, the wallet played by a script against the
 * hub itself in memory — the same program a page runs. `node`: the node it starts at (none: it has to find one) */
final class Phone(val hub: Hubs, words: Words = Words.En, node: String = ""):
  var pair: Option[Pair] = None
  val kept = scala.collection.mutable.Map.empty[String, String]
  if node.nonEmpty then kept("node") = node
  /** what the wallet copied: its statements */
  val copied = scala.collection.mutable.Buffer.empty[String]
  type World[X] = (Ui + Settings + Clipboard + Clock + Net + Device + Signer)[X]

  private def asked(method: String, path: String, body: Option[Json], headers: (String, String)*): (Int, Json) =
    val r = hub.ask(Request(method, path).copy(body = body.fold("")(Json.print(_)), headers =
      (headers :+ ("content-type" -> "application/json")).map((k, v) => k -> List(v)).toMap))
    (r.status, Descent.parse(r.body).toOption.getOrElse(Json.Null))

  private def net[R[_], Rest[_], A](p: A ! R)(using Split[Net, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Net, Rest, A, A](a => pure(a), [X] => (op: Net[X]) => op match
      case Net.Call(method, path, body, token) => asked(method, kept.getOrElse("node", "") + path, body,
          (if token.isEmpty then Nil else List("x-setup-token" -> token))*)
      case Net.AtHub(method, path, body) => asked(method, path, body, "cf-connecting-ip" -> "1.2.3.4")
      // another node of this hub by its place; one elsewhere is not reached from a test
      case Net.Elsewhere(address, path) => if address.startsWith("/") then asked("GET", address + path, None) else
          (0, Json.Null)
      case Net.Invite() => s"https://wallet.example/?node=${kept.getOrElse("node", "")}"))

  private def device[R[_], Rest[_], A](p: A ! R)(using Split[Device, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Device, Rest, A, A](a => pure(a), [X] => (asked: Device[X]) => asked match
      case Device.Key() => pair.map(_.pub)
      case Device.Make() =>
        pair = Some(SignerJdk.generate())
        pair.get.pub
      case Device.Sign(text) => SignerJdk.sign(pair.get.secret, text)))

  /** the wallet from its start, the acts done in order: what was seen, and the screen it stopped at */
  def play(acts: Act*): Played[(Long, Unit)] =
    Play(steps(keptIn(kept)(copiedTo(copied)(Clock.virtual(hub.now)(SignerJdk.run(device(net(
      Wallet[World](words).main))))))), acts*)

  def key: String = pair.get.pub
  /** the account in use at the node in use */
  def account: String = kept(s"current:${kept("node")}")

object Phone:
  /** a device that starts at a node */
  def apply(town: Town): Phone = new Phone(town.hub, node = town.prefix)
  def apply(town: Town, words: Words): Phone = new Phone(town.hub, words, town.prefix)

object Seen:
  /** a screen's words: its texts, notices, details and the cells of its tables */
  def of(p: Played[?]): String = p.at.fold("")(s => (s.title +: s.parts.flatMap(part)).mkString("\n"))
  private def part(p: Part): List[String] = p match
    case Part.Text(s) => List(s)
    case Part.Notice(level, s) => List(s"$level: $s")
    case Part.Details(rows) => rows.map((label, cell) => s"$label: ${cell.text}")
    case Part.Section(title, parts) => title :: parts.flatMap(part)
    case t: Table[r] => t.rows.map(row => t.cells(row).map(_.text).mkString(" | "))
    // a text shown as it is (a key, a link): its lines
    case c: Part.Code => c.productElement(0).asInstanceOf[List[String]]
    case _ => Nil

/** THE WALLET (specs/protocol.md "The wallet"), played as a person would, against a real node in memory */
class WalletSuite extends munit.FunSuite:
  private def joined(town: Town, name: String, of: String = "Personal"): Phone =
    val phone = Phone(town)
    val p = phone.play(Act.press("Open an account"), Act.press("Save", "Your name" -> name, "Account" -> of),
      Act.press("OK"))
    assertEquals(p.problems, Nil)
    assertEquals(p.at.map(_.title), Some(name), Seen.of(p))
    phone

  test("an account opened from the wallet: the key made on the device, the first record signed by it") {
    val town = Town()
    val first = Phone(town).play()
    assert(Seen.of(first).contains("Community: Test Town") && Seen.of(first).contains("open to anyone"),
      Seen.of(first))
    assertEquals(first.at.get.buttons.map(_.label), List("Open an account", "Add this device to my account",
      "Set up this community"))
    val anna = joined(town, "Anna")
    val home = anna.play()
    assert(Seen.of(home).contains("Balance: 0.00") && Seen.of(home).contains("You can still spend: 100.00"),
      Seen.of(home))
    // the node's chain of that account holds by the device's key and the node's
    assertEquals(town.checked(anna.account).map(s => s.grants.map(_.key)), Right(Vector(anna.key)))
    assertEquals(town.json(town.get("/api/members"))("members").list.map(_("name").string), Vector("Anna"))
    // the form says what it needs
    assertEquals(Phone(town).play(Act.press("Open an account"), Act.press("Save")).problems.size, 1)
  }

  test("a payment from the wallet: signed here, seen at both ends, with what it was for; refusals in words") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan", "Business"))
    val paid = anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "40", "For what" ->
      "a jacket altered"), Act.press("Yes"))
    assertEquals(paid.at.map(_.title), Some("Paid"), Seen.of(paid))
    assert(Seen.of(paid).contains("40.00") && Seen.of(paid).contains("to Jan"), Seen.of(paid))
    assert(Seen.of(anna.play()).contains("Balance: -40.00"))
    assert(Seen.of(jan.play()).contains("Balance: 40.00"))
    val history = jan.play(Act.press("History"))
    assert(Seen.of(history).contains("received | Anna | 40.00 PLN | a jacket altered"), Seen.of(history))
    assert(Seen.of(anna.play(Act.press("History"))).contains("paid | Jan | -40.00 PLN | a jacket altered"))
    // over the line: the node's refusal, said
    val over = anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "60.01"), Act.press("Yes"))
    assertEquals(over.at.map(_.title), Some("Not paid"))
    assert(Seen.of(over).contains("over your credit line by 0.01"), Seen.of(over))
    // not confirmed: nothing sent
    anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "5"), Act.press("No"))
    assert(Seen.of(anna.play()).contains("Balance: -40.00"))
    assertEquals(anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "0")).problems.size, 1)
  }

  test("the node, checked by the wallet: the chain folded again, every signature, the head; a head going back seen") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan"))
    anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "12.50"), Act.press("Yes"))
    val checked = anna.play(Act.press("Check the node"))
    assertEquals(checked.at.map(_.title), Some("The node, checked"))
    assert(Seen.of(checked).contains("Info: 3 records, every signature holds; they make a balance of -12.50"),
      Seen.of(checked))
    assert(Seen.of(checked).contains("Info: Your chain is under the node's signed head number"), Seen.of(checked))
    // the wallet remembers the head: a node answering with an older one is seen
    val key = s"head:${town.node.pub}"
    val number = anna.kept(key).takeWhile(_ != ':').toLong
    anna.kept(key) = s"${number + 5}:${anna.kept(key).dropWhile(_ != ':').drop(1)}"
    assert(Seen.of(anna.play(Act.press("Check the node"))).contains("Problem: The node's head went back"))
    anna.kept(key) = s"$number:${"0" * 64}"
    assert(Seen.of(anna.play(Act.press("Check the node"))).contains("the node showed two histories"))
  }

  test("a second device: its key shown there, signed in here with limits, pays within them, taken off") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan"))
    val tablet = Phone(town)
    val shown = tablet.play(Act.press("Add this device to my account"))
    assert(Seen.of(shown).contains("enter this device's key"), Seen.of(shown))
    val notYet = tablet.play(Act.press("Add this device to my account"), Act.press("It is added",
      "The account's id" -> anna.account))
    assertEquals(notYet.at.map(_.title), Some("Not yet"))
    val added = anna.play(Act.press("Devices"), Act.press("Add a device"), Act.press("Save",
      "The new device's key" -> tablet.key, "At most a payment" -> "20", "At most a week" -> "50"))
    assertEquals(added.at.map(_.title), Some("Added"), Seen.of(added))
    val in = tablet.play(Act.press("Add this device to my account"), Act.press("It is added",
      "The account's id" -> anna.account), Act.press("OK"))
    assertEquals(in.at.map(_.title), Some("Anna"), Seen.of(in))
    def pay(amount: String) = tablet.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> amount),
      Act.press("Yes"))
    assertEquals(pay("20").at.map(_.title), Some("Paid"))
    assert(Seen.of(pay("20.01")).contains("more than this device may pay at once"))
    val devices = anna.play(Act.press("Devices"))
    assert(Seen.of(devices).contains("(this device) | spend and manage | the account's own"), Seen.of(devices))
    assert(Seen.of(devices).contains("spend | 20.00 PLN a payment, 50.00 PLN in 7 days"), Seen.of(devices))
    // the tablet may not manage; the phone takes it off
    val row = Seen.of(devices).linesIterator.find(_.contains("20.00 PLN a payment")).get.takeWhile(_ != ' ')
    val refused = tablet.play(Act.press("Devices"), Act.choose("Take off", row + " (this device)"), Act.press("Yes"))
    assert(Seen.of(refused).contains("Only a device that may manage"), Seen.of(refused))
    assertEquals(anna.play(Act.press("Devices"), Act.choose("Take off", row), Act.press("Yes")).at.map(_.title),
      Some("Taken off"))
    assert(Seen.of(pay("1")).contains("does not hold the account"))
    assertEquals(town.checked(anna.account).map(s => (s.balance, s.grants.size)), Right((-2000L, 1)))
  }

  test("the community's office: set up from a wallet, its holder sets a member's limits; nobody else sees it") {
    val town = Town()
    val founder = Phone(town)
    val wrong = founder.play(Act.press("Set up this community"), Act.press("Save", "The community's name" ->
      "Test Town", "Setup token" -> "guess"))
    assert(Seen.of(wrong).contains("not the setup token"), Seen.of(wrong))
    val set = founder.play(Act.press("Set up this community"), Act.press("Save", "The community's name" ->
      "Test Town", "Setup token" -> town.token), Act.press("OK"))
    assertEquals(set.at.map(_.title), Some("Test Town"), Seen.of(set))
    assert(set.at.get.buttons.exists(_.label == "Community"))
    val anna = joined(town, "Anna")
    assert(!anna.play().at.get.buttons.exists(_.label == "Community"))
    // set up once: the offer is gone
    assert(!Phone(town).play().at.get.buttons.exists(_.label == "Set up this community"))
    val office = founder.play(Act.press("Community"))
    assert(Seen.of(office).contains("The balances' sum: 0.00") &&
      Seen.of(office).contains("Anna | personal | 0.00 PLN | 100.00 PLN | 300.00 PLN"), Seen.of(office))
    val limited = founder.play(Act.press("Community"), Act.choose("Set limits", "Anna"), Act.press("Save",
      "Credit line" -> "250", "Ceiling" -> "500"))
    assertEquals(limited.at.map(_.title), Some("Set"), Seen.of(limited))
    assert(Seen.of(anna.play()).contains("You can still spend: 250.00"))
    // the founder's device holds two accounts: the community's and, once opened, its own
    val own = founder.play(Act.press("Accounts"), Act.press("Open or add another"), Act.press("Open an account"),
      Act.press("Save", "Your name" -> "Marek", "Account" -> "Personal"), Act.press("OK"))
    assertEquals(own.at.map(_.title), Some("Marek"), Seen.of(own))
    val both = founder.play(Act.press("Accounts"))
    assert(Seen.of(both).contains("Test Town") && Seen.of(both).contains("Marek"), Seen.of(both))
  }

  test("the wallet in Russian, Ukrainian and Polish: the same program, its words another value") {
    val town = Town()
    joined(town, "Jan", "Business")
    for (words, name) <- List(WordsRu -> "Анна", WordsUk -> "Оксана", WordsPl -> "Zofia") do
      val phone = Phone(town, words)
      val first = phone.play()
      assert(Seen.of(first).contains(s"${words.community}: Test Town") && Seen.of(first).contains(words.joinOpen),
        Seen.of(first))
      // a form's own complaint is said in the language too
      val empty = phone.play(Act.press(words.openAccount), Act.press(words.save))
      assertEquals(empty.problems.map(_.message).distinct, List(words.problem("required")), words.code)
      assertNotEquals(words.problem("required"), "required")
      val home = phone.play(Act.press(words.openAccount), Act.press(words.save, words.yourName -> name,
        words.accountKind -> words.personal), Act.press(words.ok))
      assertEquals(home.at.map(_.title), Some(name), Seen.of(home))
      val paid = phone.play(Act.press(words.pay), Act.press(words.save, words.to -> "Jan", words.amount -> "15",
        words.forWhat -> "chleb"), Act.press(words.yes))
      assertEquals(paid.at.map(_.title), Some(words.paid), Seen.of(paid))
      val over = phone.play(Act.press(words.pay), Act.press(words.save, words.to -> "Jan", words.amount -> "500"),
        Act.press(words.yes))
      assert(Seen.of(over).contains(words.overCredit("415.00 PLN")), Seen.of(over))
      val checked = phone.play(Act.press(words.checkNode))
      assert(Seen.of(checked).contains(words.recordsHold(3, "-15.00 PLN")), Seen.of(checked))
      val history = phone.play(Act.press(words.history))
      assert(Seen.of(history).contains(s"${words.paidKind} | Jan | -15.00 PLN | chleb"), Seen.of(history))
    assertEquals((Words.of("ru-RU"), Words.of("uk"), Words.of("pl_PL"), Words.of("de")),
      (WordsRu, WordsUk, WordsPl, Words.En))
  }

  test("vouching from the wallet; a node that does not answer is asked for by its address") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan"))
    val vouched = anna.play(Act.press("Vouch"), Act.press("Save", "For" -> "Jan", "Part of your credit line" -> "30"))
    assertEquals(vouched.at.map(_.title), Some("Vouched"), Seen.of(vouched))
    assert(Seen.of(anna.play()).contains("You can still spend: 70.00"))
    assert(Seen.of(jan.play()).contains("You can still spend: 130.00"))
    val away = Phone(Town())
    away.kept ++= anna.kept
    val lost = away.play()
    assert(Seen.of(lost).contains("This account is not at this node"), Seen.of(lost))
  }

  test("a community made from a blank wallet: its node at the hub, its account by this device, the link to invite") {
    val hub = Hubs()
    val founder = new Phone(hub)
    val blank = founder.play()
    assertEquals(blank.at.map(_.title), Some("Find your community"), Seen.of(blank))
    assert(Seen.of(blank).contains("Communities here") && !Seen.of(blank).contains(" | "), Seen.of(blank))
    def create(place: String) = founder.play(Act.press("Create a community"), Act.press("Save",
      "The community's name" -> "Our Town", "Its place in the address" -> place))
    assert(Seen.of(create("X")).contains("The place needs 3 to 40"), Seen.of(create("X")))
    val made = create("our-town")
    assertEquals(made.at.map(_.title), Some("The community is created"), Seen.of(made))
    assertEquals(founder.kept("node"), "/n/our-town")
    val home = founder.play()
    assertEquals(home.at.map(_.title), Some("Our Town"), Seen.of(home))
    assert(home.at.get.buttons.map(_.label).containsSlice(List("Community", "Invite", "Accounts")))
    assert(Seen.of(founder.play(Act.press("Invite"))).contains("https://wallet.example/?node=/n/our-town"))
    // the place is taken now
    val second = new Phone(hub)
    val taken = second.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "Again",
      "Its place in the address" -> "our-town"))
    assert(Seen.of(taken).contains("That place is taken"), Seen.of(taken))
    // the hub lists it, by admission as its founder left it
    val listed = second.play()
    assert(Seen.of(listed).contains("Our Town | PLN | by the community's admission"), Seen.of(listed))
    // an address typed works as a place chosen
    val typed = new Phone(hub)
    assertEquals(typed.play(Act.press("Use this address", "The node's address" -> "/n/our-town/")).at.map(_.title),
      Some("Okay Mutual"))
    assertEquals(typed.kept("node"), "/n/our-town")
  }

  test("joining where it is not open: asked from the wallet, admitted or rejected in the community's office") {
    val hub = Hubs()
    val founder = new Phone(hub)
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "Our Town",
      "Its place in the address" -> "our-town"), Act.press("OK"))
    def asks(name: String): Phone =
      val phone = new Phone(hub)
      val asked = phone.play(Act.choose("Use", "Our Town"), Act.press("Open an account"), Act.press("I accept"),
        Act.press("Save", "Your name" -> name, "Account" -> "Business"), Act.press("OK"))
      assertEquals(asked.at.map(_.title), Some("You asked to join"), Seen.of(asked))
      assert(Seen.of(asked).contains("Our Town admits its members itself"), Seen.of(asked))
      phone
    val (anna, eve) = (asks("Anna"), asks("Eve"))
    // nothing is hers yet: she waits, and checking again changes nothing
    assertEquals(anna.play(Act.press("Check again")).at.map(_.title), Some("You asked to join"))
    val office = founder.play(Act.press("Community"))
    assert(office.at.get.buttons.exists(_.label == "Requests (2)"), office.at.get.buttons.map(_.label).toString)
    val admitted = founder.play(Act.press("Community"), Act.press("Requests (2)"), Act.choose("Admit", "Anna"),
      Act.press("Save", "Credit line" -> "150"))
    assertEquals(admitted.at.map(_.title), Some("Admitted"), Seen.of(admitted))
    val rejected = founder.play(Act.press("Community"), Act.press("Requests (1)"), Act.choose("Reject", "Eve"))
    assertEquals(rejected.at.map(_.title), Some("Rejected"), Seen.of(rejected))
    assert(founder.play(Act.press("Community")).at.get.buttons.exists(_.label == "Requests (0)"))
    // Anna's wallet takes her account up, with the limits the community gave; her key was hers all along
    val home = anna.play()
    assertEquals(home.at.map(_.title), Some("Anna"), Seen.of(home))
    assert(Seen.of(home).contains("You can still spend: 150.00"), Seen.of(home))
    // Eve still waits; she forgets the request and is at the start again
    assertEquals(eve.play().at.map(_.title), Some("You asked to join"))
    assertEquals(eve.play(Act.press("Forget this request")).at.map(_.title), Some("Okay Mutual"))
    // another community: Anna's account here is kept for when she comes back
    val away = anna.play(Act.press("Accounts"), Act.press("Another community"))
    assertEquals(away.at.map(_.title), Some("Find your community"), Seen.of(away))
    assertEquals(anna.play(Act.choose("Use", "Our Town")).at.map(_.title), Some("Anna"))
  }

  test("trust lines from the wallet: two communities open lines to each other, a member pays across") {
    val hub = Hubs("HUB_PER_DAY" -> "9")
    def community(name: String, place: String, unit: String = "PLN"): Phone =
      val founder = new Phone(hub)
      val made = founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> name,
        "Its place in the address" -> place, "Unit (the national currency it counts as)" -> unit,
        "How one joins" -> "open to anyone"), Act.press("OK"))
      assertEquals(made.at.map(_.title), Some(name), Seen.of(made))
      founder
    def member(place: String, name: String): Phone =
      val phone = new Phone(hub, node = s"/n/$place")
      phone.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> name,
        "Account" -> "Business"), Act.press("OK"))
      phone
    val (north, south) = (community("North", "north"), community("South", "south"))
    val (anna, olaf) = (member("north", "Anna"), member("south", "Olaf"))
    def line(founder: Phone, address: String, credit: String) = founder.play(Act.press("Community"),
      Act.press("Trust lines"), Act.press("Open a line"), Act.press("Save", "The other community's address" -> address,
        "They may owe us" -> credit))
    // with no line a member is offered only its own community
    assertEquals(anna.play(Act.press("Pay")).at.map(_.title), Some("Pay"))
    val opened = line(north, "/n/south", "500")
    assertEquals(opened.at.map(_.title), Some("The line is open"), Seen.of(opened))
    assert(Seen.of(opened).contains("South may owe this community up to 500.00 PLN"), Seen.of(opened))
    assertEquals(line(south, "/n/north/", "500").at.map(_.title), Some("The line is open"))
    // not to a place that is no community, not to itself, not across units
    assert(Seen.of(line(north, "/n/nowhere", "1")).contains("No community answers"))
    assert(Seen.of(line(north, "/n/north", "1")).contains("this community itself"))
    community("Euroville", "euroville", "EUR")
    assert(Seen.of(line(north, "/n/euroville", "1")).contains("another unit"))
    // Anna pays Olaf, of the other community
    val asked = anna.play(Act.press("Pay"))
    assertEquals(asked.at.get.buttons.map(_.label), List("North", "To another community"), Seen.of(asked))
    val paid = anna.play(Act.press("Pay"), Act.press("To another community"), Act.press("Save", "To" -> "Olaf",
      "Amount" -> "40", "For what" -> "a logo"), Act.press("Yes"))
    assertEquals(paid.at.map(_.title), Some("Paid"), Seen.of(paid))
    assert(Seen.of(paid).contains("40.00 PLN to Olaf · South"), Seen.of(paid))
    assert(Seen.of(olaf.play()).contains("Balance: 40.00"))
    val got = olaf.play(Act.press("History"))
    assert(Seen.of(got).contains("received | North | 40.00 PLN | a logo"), Seen.of(got))
    val sent = anna.play(Act.press("History"))
    assert(Seen.of(sent).contains("paid | South | -40.00 PLN | a logo"), Seen.of(sent))
    // the office shows where the line stands: we owe them 40, and they may owe us 500
    val lines = north.play(Act.press("Community"), Act.press("Trust lines"))
    assert(Seen.of(lines).contains("South | 500.00 PLN | 40.00 PLN"), Seen.of(lines))
    val members = north.play(Act.press("Community"))
    assert(Seen.of(members).contains("The balances' sum: 0.00") && Seen.of(members).contains("South | another " +
      "community | 40.00 PLN"), Seen.of(members))
    // past what South lets North owe: it comes back, said so
    north.play(Act.press("Community"), Act.choose("Set limits", "Anna"), Act.press("Save", "Credit line" -> "9000"))
    val back = anna.play(Act.press("Pay"), Act.press("To another community"), Act.press("Save", "To" -> "Olaf",
      "Amount" -> "470"), Act.press("Yes"))
    assertEquals(back.at.map(_.title), Some("It came back"), Seen.of(back))
    assert(Seen.of(back).contains("South did not take it"), Seen.of(back))
    assert(Seen.of(anna.play()).contains("Balance: -40.00"))
  }

  test("a community's rules and whom it admits, from the wallet; a statement for the books") {
    val hub = Hubs()
    val founder = new Phone(hub)
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "The Guild",
      "Its place in the address" -> "guild", "How one joins" -> "open to anyone", "Who may join" ->
        "businesses and the self-employed only (simplest)"), Act.press("OK"))
    // a newcomer reads the rules first; not accepted, nothing is opened
    val anna = new Phone(hub, node = "/n/guild")
    val shown = anna.play(Act.press("Open an account"))
    assertEquals(shown.at.map(_.title), Some("The rules · The Guild"), Seen.of(shown))
    assert(Seen.of(shown).contains("2. Units are not money.") && Seen.of(shown).contains("pays its own taxes"),
      Seen.of(shown))
    assertEquals(anna.play(Act.press("Open an account"), Act.press("Back")).at.map(_.title), Some("Okay Mutual"))
    // businesses only, as its founder left it: no personal account is offered, and the node would refuse one
    val form = anna.play(Act.press("Open an account"), Act.press("I accept"))
    assert(Seen.of(anna.play()).contains("Community: The Guild"))
    val opened = anna.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save",
      "Your name" -> "Anna's bakery"), Act.press("OK"))
    assertEquals(opened.at.map(_.title), Some("Anna's bakery"), Seen.of(opened) + form.problems)
    assertEquals(hub.json(hub.get(s"/n/guild/api/accounts/${anna.account}"))("of").string, "business")
    // the founder changes the rules: a newcomer shown the old ones is told to read again
    val late = new Phone(hub, node = "/n/guild")
    val saved = founder.play(Act.press("Community"), Act.press("Rules"), Act.press("Save",
      "The community's rules" -> "One rule: be fair."))
    assertEquals(saved.at.map(_.title), Some("The rules are saved"), Seen.of(saved))
    assert(Seen.of(late.play(Act.press("Open an account"))).contains("One rule: be fair."))
    // a statement for the books: the member's deals as CSV, on the clipboard
    val olaf = new Phone(hub, node = "/n/guild")
    olaf.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> "Olaf, tailor"),
      Act.press("OK"))
    anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Olaf, tailor", "Amount" -> "40", "For what" ->
      "a jacket, \"blue\""), Act.press("Yes"))
    val copied = anna.play(Act.press("History"), Act.press("Copy as a statement (CSV)"))
    assertEquals(copied.at.map(_.title), Some("Copied"), Seen.of(copied))
    assertEquals(anna.copied.last.linesIterator.toList, List(
      "\"Day\",\"What\",\"With\",\"Amount (PLN)\",\"For\"",
      "\"2026-11-18\",\"paid\",\"Olaf, tailor\",\"-40\",\"a jacket, \"\"blue\"\"\""))
  }

  test("private people take part: they earn, then spend — an account that never goes below zero") {
    val hub = Hubs()
    val founder = new Phone(hub)
    // as a community is made unless its founder says otherwise
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "The Market",
      "Its place in the address" -> "market"), Act.press("OK"))
    assertEquals(hub.json(hub.get("/n/market/.well-known/okay-mutual.json"))("only").string, "earned")
    def asks(name: String, kind: String): Phone =
      val phone = new Phone(hub, node = "/n/market")
      phone.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> name,
        "Account" -> kind), Act.press("OK"))
      phone
    val (shop, anna) = (asks("The shop", "Business"), asks("Anna", "Personal"))
    // admitting a person, the office is told there is no credit line to give; whatever is typed, she gets none
    val form = founder.play(Act.press("Community"), Act.press("Requests (2)"), Act.choose("Admit", "Anna"))
    assert(Seen.of(form).contains("Info: A private person's account has no credit line"), Seen.of(form))
    founder.play(Act.press("Community"), Act.press("Requests (2)"), Act.choose("Admit", "Anna"), Act.press("Save",
      "Credit line" -> "500"))
    founder.play(Act.press("Community"), Act.press("Requests (1)"), Act.choose("Admit", "The shop"), Act.press("Save"))
    val home = anna.play()
    assertEquals(home.at.map(_.title), Some("Anna"), Seen.of(home))
    assert(Seen.of(home).contains("You can still spend: 0.00") && Seen.of(home).contains("You can still receive: " +
      "300.00"), Seen.of(home))
    assert(Seen.of(shop.play()).contains("You can still spend: 100.00"))
    // she cannot buy yet; the shop pays her for work; then she spends what she earned, and no more
    def pays(phone: Phone, to: String, amount: String) = phone.play(Act.press("Pay"), Act.press("Save", "To" -> to,
      "Amount" -> amount), Act.press("Yes"))
    assert(Seen.of(pays(anna, "The shop", "5")).contains("over your credit line"))
    assertEquals(pays(shop, "Anna", "60").at.map(_.title), Some("Paid"))
    assertEquals(pays(anna, "The shop", "45").at.map(_.title), Some("Paid"))
    assert(Seen.of(pays(anna, "The shop", "15.01")).contains("over your credit line by 0.01"))
    assert(Seen.of(anna.play()).contains("Balance: 15.00"))
    // the office cannot give her a line either, and says why; a line lent to her comes back
    val limited = founder.play(Act.press("Community"), Act.choose("Set limits", "Anna"), Act.press("Save",
      "Credit line" -> "50"))
    assert(Seen.of(limited).contains("has no credit line in this community"), Seen.of(limited))
    val lent = shop.play(Act.press("Vouch"), Act.press("Save", "For" -> "Anna", "Part of your credit line" -> "10"))
    assertEquals(lent.at.map(_.title), Some("It came back"), Seen.of(lent))
    // where people only earn, a newcomer who is a person is told how the account starts
    val open = Hubs()
    val first = new Phone(open)
    first.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "Open Market",
      "Its place in the address" -> "open-market", "How one joins" -> "open to anyone"), Act.press("OK"))
    val person = new Phone(open, node = "/n/open-market")
    val opened = person.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" ->
      "Ola"))
    assert(Seen.of(opened).contains("starts at zero and does not go below it"), Seen.of(opened))
  }
