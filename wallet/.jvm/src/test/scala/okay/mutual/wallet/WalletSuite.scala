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
  /** done just before this device gives the node a record: what happens meanwhile, in a test */
  var meanwhile: () => Unit = () => ()
  if node.nonEmpty then kept("node") = node
  /** what the wallet copied: its statements */
  val copied = scala.collection.mutable.Buffer.empty[String]
  /** the files it saved: name, text */
  val saved = scala.collection.mutable.Buffer.empty[(String, String)]
  type World[X] = (Ui + Settings + Clipboard + Clock + Net + Device + Signer + Files)[X]

  private def asked(method: String, path: String, body: Option[Json], headers: (String, String)*): (Int, Json) =
    val r = hub.ask(Request(method, path).copy(body = body.fold("")(Json.print(_)), headers =
      (headers :+ ("content-type" -> "application/json")).map((k, v) => k -> List(v)).toMap))
    (r.status, Descent.parse(r.body).toOption.getOrElse(Json.Null))

  private def net[R[_], Rest[_], A](p: A ! R)(using Split[Net, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Net, Rest, A, A](a => pure(a), [X] => (op: Net[X]) => op match
      case Net.Call(method, path, body, token) =>
        // what the test says happens while a form is open: before the account is asked for anew
        if method == "GET" && path.matches("/api/accounts/[0-9a-f]{64}") then { val m = meanwhile; meanwhile = () => (); m() }
        asked(method, kept.getOrElse("node", "") + path, body,
          (if token.isEmpty then Nil else List("x-setup-token" -> token))*)
      case Net.AtHub(method, path, body) => asked(method, path, body, "cf-connecting-ip" -> "1.2.3.4")
      // another node of this hub by its place; one elsewhere is not reached from a test
      case Net.Elsewhere(address, path) => if address.startsWith("/") then asked("GET", address + path, None) else
          (0, Json.Null)
      case Net.Invite() => s"https://wallet.example/?node=${kept.getOrElse("node", "")}"
      // nothing fires by itself in a test: a screen's "Check" asks the same
      case Net.Changes(_, _) => Events.none[Unit]))

  private def device[R[_], Rest[_], A](p: A ! R)(using Split[Device, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Device, Rest, A, A](a => pure(a), [X] => (asked: Device[X]) => asked match
      case Device.Key() => pair.map(_.pub)
      case Device.Make() =>
        pair = Some(SignerJdk.generate())
        pair.get.pub
      case Device.Sign(text) => SignerJdk.sign(pair.get.secret, text)))

  /** the wallet from its start, the acts done in order: what was seen, and the screen it stopped at */
  def play(acts: Act*): Played[(Long, Unit)] =
    Play(steps(keptIn(kept)(copiedTo(copied)(Clock.virtual(hub.now)(SignerJdk.run(device(net(files(
      Wallet[World](words).main)))))))), acts*)

  private def files[R[_], Rest[_], A](p: A ! R)(using Split[Files, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Files, Rest, A, A](a => pure(a), [X] => (op: Files[X]) => op match
      case Files.Save(name, text) =>
        saved += name -> text
        true))

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
      "Set up this community", "I lost my devices"))
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
    assert(home.at.get.buttons.map(_.label).containsSlice(List("Community", "Board", "Cards", "Invite", "Accounts")))
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

  test("a payment received while the app is open does not spoil the next one: the record is the chain's next as it is NOW") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan"))
    // Anna has her Pay form open; meanwhile Jan pays her; then she presses Save — her chain has moved on by a record
    var paidMeanwhile = false
    val acts = Seq(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "30"), Act.press("Yes"))
    // the first time the app asks for her account is the home screen; the hook is armed after it is drawn
    val home = anna.play()
    assertEquals(home.at.map(_.title), Some("Anna"))
    var asked = 0
    anna.meanwhile = () => ()
    val paid = {
      // arm on the second asking for the account in this play: the first draws the home, the second is `say`'s
      def arm(): Unit = anna.meanwhile = () => { asked += 1; if asked == 1 then arm() else if !paidMeanwhile then {
        paidMeanwhile = true
        assertEquals(jan.play(Act.press("Pay"), Act.press("Save", "To" -> "Anna", "Amount" -> "20"), Act.press("Yes"))
          .at.map(_.title), Some("Paid")) } }
      arm()
      anna.play(acts*)
    }
    assert(paidMeanwhile)
    assertEquals(paid.at.map(_.title), Some("Paid"), Seen.of(paid))
    assert(Seen.of(anna.play()).contains("Balance: -10.00"), Seen.of(anna.play()))
    assertEquals(town.checked(anna.account).map(_.balance), Right(-1000L))
  }

  test("every device lost: the account asked back from a new one, confirmed by the community, held after a week") {
    val town = Town()
    val founder = Phone(town)
    founder.play(Act.press("Set up this community"), Act.press("Save", "The community's name" -> "Test Town",
      "Setup token" -> town.token), Act.press("OK"))
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan"))
    // her phone is gone; on a new device she says which account is hers
    val fresh = Phone(town)
    val asked = fresh.play(Act.press("I lost my devices"), Act.choose("This is my account", "Anna"))
    assertEquals(asked.at.map(_.title), Some("You asked your account back"), Seen.of(asked))
    assert(Seen.of(fresh.play()).contains("Test Town has to confirm it is you"), Seen.of(fresh.play()))
    assert(!Seen.of(anna.play()).contains("asked this account back"))
    // the community's office: a warning to be sure, then the recovery announced
    val office = founder.play(Act.press("Community"), Act.press("Lost devices (1)"))
    assert(Seen.of(office).contains("Warning: Confirm only when you are sure"), Seen.of(office))
    val announced = founder.play(Act.press("Community"), Act.press("Lost devices (1)"),
      Act.choose("It is them: confirm", "Anna"), Act.press("Yes"))
    assertEquals(announced.at.map(_.title), Some("Announced"), Seen.of(announced))
    // the new device waits out the week; meanwhile it holds nothing
    val waits = fresh.play()
    assert(Seen.of(waits).contains("Info: Your community confirmed it. This device holds the account from 2026-11-25"),
      Seen.of(waits))
    assert(Seen.of(fresh.play(Act.press("Take the account up"))).contains("the waiting time is not over"))
    // an old device, were it only mislaid, is warned and can say no
    val warned = anna.play()
    assert(Seen.of(warned).contains("Warning: Someone asked this account back for another device"), Seen.of(warned))
    assert(warned.at.get.buttons.exists(_.label == "It was not me: cancel"))
    town.now += 7L * 86400000L
    val back = fresh.play(Act.press("Take the account up"))
    assertEquals(back.at.map(_.title), Some("Anna"), Seen.of(back))
    assertEquals(fresh.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "10"), Act.press("Yes"))
      .at.map(_.title), Some("Paid"))
    assertEquals(town.checked(fresh.account).map(s => (s.grants.size, s.balance)), Right((2, -1000L)))
    // another asking, by someone else: the owner cancels it from the device she has now
    val thief = Phone(town)
    thief.play(Act.press("I lost my devices"), Act.choose("This is my account", "Anna"), Act.press("OK"))
    founder.play(Act.press("Community"), Act.press("Lost devices (1)"), Act.choose("It is them: confirm", "Anna"),
      Act.press("Yes"))
    val cancelled = fresh.play(Act.press("It was not me: cancel"))
    assert(Seen.of(cancelled).contains("that device will hold nothing"), Seen.of(cancelled))
    town.now += 8L * 86400000L
    assertEquals(thief.play(Act.press("Check again")).at.map(_.title), Some("You asked your account back"))
    // one the community does not believe is dropped
    val liar = Phone(town)
    liar.play(Act.press("I lost my devices"), Act.choose("This is my account", "Jan"), Act.press("OK"))
    founder.play(Act.press("Community"), Act.press("Lost devices (1)"), Act.choose("Not them: drop", "Jan"))
    assert(founder.play(Act.press("Community")).at.get.buttons.exists(_.label == "Lost devices (0)"))
  }

  test("the board: a member offers, another pays for it — the form filled from the offer; wants; taken off") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna"), joined(town, "Jan"))
    assertEquals(anna.play(Act.press("Board")).at.map(_.title), Some("Board"))
    val posted = anna.play(Act.press("Board"), Act.press("I offer"), Act.press("Save", "What" -> "Trousers hemmed",
      "More about it" -> "by Friday", "Price" -> "40", "In units, %" -> "50"))
    assertEquals(posted.at.map(_.title), Some("It is on the board"), Seen.of(posted))
    assertEquals(jan.play(Act.press("Board"), Act.press("I look for"), Act.press("Save", "What" -> "A logo")).at
      .map(_.title), Some("It is on the board"))
    val board = jan.play(Act.press("Board"))
    assert(Seen.of(board).contains("offers | Trousers hemmed | Anna | 40.00 PLN | 50%"), Seen.of(board))
    assert(Seen.of(board).contains("looks for | A logo | Jan | by agreement | 100%"), Seen.of(board))
    // Jan opens the offer and pays: whom, the half that is in units and what for are there already
    val offer = jan.play(Act.press("Board"), Act.choose("Open", "Trousers hemmed"))
    assert(Seen.of(offer).contains("by Friday") && offer.at.get.buttons.map(_.label) == List("Pay", "Back"),
      Seen.of(offer))
    val paid = jan.play(Act.press("Board"), Act.choose("Open", "Trousers hemmed"), Act.press("Pay"),
      Act.press("Save"), Act.press("Yes"))
    assertEquals(paid.at.map(_.title), Some("Paid"), Seen.of(paid))
    assert(Seen.of(paid).contains("20.00 PLN to Anna"), Seen.of(paid))
    assert(Seen.of(anna.play(Act.press("History"))).contains("received | Jan | 20.00 PLN | Trousers hemmed"))
    // a want is not paid for; one's own entry is taken off, another's is not
    assertEquals(anna.play(Act.press("Board"), Act.choose("Open", "A logo")).at.get.buttons.map(_.label),
      List("Back"))
    assertEquals(jan.play(Act.press("Board"), Act.choose("Open", "A logo")).at.get.buttons.map(_.label),
      List("Take off the board", "Back"))
    anna.play(Act.press("Board"), Act.choose("Open", "Trousers hemmed"), Act.press("Take off the board"))
    assert(!Seen.of(jan.play(Act.press("Board"))).contains("Trousers hemmed"))
    // a share is a share
    assertEquals(anna.play(Act.press("Board"), Act.press("I offer"), Act.press("Save", "What" -> "Bread",
      "In units, %" -> "150")).problems.size, 1)
  }

  test("a shared account: opened from the app, held by members' accounts, used from any device; two signatures") {
    val town = Town()
    val (anna, jan, shop) = (joined(town, "Anna"), joined(town, "Jan"), joined(town, "Shop", "Business"))
    val (annas, jans) = (anna.account, jan.account)
    def pays(phone: Phone, to: String, amount: String) = phone.play(Act.press("Pay"), Act.press("Save", "To" -> to,
      "Amount" -> amount, "For what" -> "paint"), Act.press("Yes"))
    // Anna opens the family's account: her ACCOUNT holds it, her phone signs
    val made = anna.play(Act.press("Accounts"), Act.press("Shared accounts"), Act.press("New shared account"),
      Act.press("Save", "Its name" -> "Home"), Act.press("OK"))
    assertEquals(made.at.map(_.title), Some("Home"), Seen.of(made))
    assertEquals(made.at.get.buttons.map(_.label), List("Pay", "Receive", "History", "Holders", "Check the node", "Accounts"))
    val home = anna.account
    assertEquals(town.checkedShared(home).map(s => (s.of, s.grants.map(_.key), s.floor)),
      Right(("family", Vector(annas), 0L)))
    assertEquals(pays(shop, "Home", "60").at.map(_.title), Some("Paid"))
    // Jan's account a holder, 50.00 a week; he finds the account from his own phone and pays from it
    val added = anna.play(Act.press("Holders"), Act.press("Add a holder"), Act.press("Save", "Member" -> "Jan",
      "At most a week" -> "50"))
    assertEquals(added.at.map(_.title), Some("Added"), Seen.of(added))
    val found = jan.play(Act.press("Accounts"), Act.press("Shared accounts"))
    assert(Seen.of(found).contains("Home | shared: family"), Seen.of(found))
    assertEquals(jan.play(Act.press("Accounts"), Act.press("Shared accounts"), Act.choose("Use", "Home")).at
      .map(_.title), Some("Home"))
    assertEquals(pays(jan, "Shop", "20").at.map(_.title), Some("Paid"))
    val holders = jan.play(Act.press("Holders"))
    assert(Seen.of(holders).contains("Anna | spend and manage") && Seen.of(holders).contains(
      "Jan (you) | spend | 50.00 PLN in 7 days"), Seen.of(holders))
    // two signatures above 10.00: Jan's payment waits; he cannot confirm his own; Anna does
    val set = anna.play(Act.press("Holders"), Act.press("Two signatures"), Act.press("Save",
      "Two holders for a payment above" -> "10"))
    assertEquals(set.at.map(_.title), Some("Set"), Seen.of(set))
    val waits = pays(jan, "Shop", "15")
    assertEquals(waits.at.map(_.title), Some("It waits for a second holder"), Seen.of(waits))
    assert(Seen.of(jan.play()).contains("Balance: 40.00"))
    val own = jan.play(Act.press("To confirm (1)"), Act.choose("Confirm and pay", "Shop"))
    assert(Seen.of(own).contains("another holder has to confirm"), Seen.of(own))
    val listed = anna.play(Act.press("To confirm (1)"))
    assert(Seen.of(listed).contains("Shop | 15.00 PLN | Jan"), Seen.of(listed))
    val confirmed = anna.play(Act.press("To confirm (1)"), Act.choose("Confirm and pay", "Shop"))
    assertEquals(confirmed.at.map(_.title), Some("Paid"), Seen.of(confirmed))
    assert(Seen.of(anna.play()).contains("Balance: 25.00"))
    assert(!anna.play().at.get.buttons.exists(_.label.startsWith("To confirm")))
    // what it was for went with the proposal to the payment; the history says who and what
    assert(Seen.of(shop.play(Act.press("History"))).contains("received | Home | 15.00 PLN | paint"))
    val history = Seen.of(anna.play(Act.press("History")))
    assert(history.contains("holder Jan added") && history.contains("two holders above 10.00 PLN") &&
      history.contains("payment of 15.00 PLN proposed | Shop"), history)
    // the node, checked: every record signed for a holder is checked against that holder's own chain
    val checked = anna.play(Act.press("Check the node"))
    assert(Seen.of(checked).contains("every signature holds; they make a balance of 25.00"), Seen.of(checked))
    assertEquals(town.checkedShared(home).map(_.balance), Right(2500L))
    // Jan's new tablet, signed in to HIS account once, finds the family's account and pays from it
    assertEquals(jan.play(Act.press("Accounts"), Act.choose("Use", "Jan")).at.map(_.title), Some("Jan"))
    val tablet = Phone(town)
    tablet.play(Act.press("Add this device to my account"))
    assertEquals(jan.play(Act.press("Devices"), Act.press("Add a device"), Act.press("Save",
      "The new device's key" -> tablet.key, "May manage the account" -> "true")).at.map(_.title), Some("Added"))
    tablet.play(Act.press("Add this device to my account"), Act.press("It is added", "The account's id" -> jans),
      Act.press("OK"))
    assertEquals(tablet.play(Act.press("Accounts"), Act.press("Shared accounts"), Act.choose("Use", "Home")).at
      .map(_.title), Some("Home"))
    assertEquals(pays(tablet, "Shop", "5").at.map(_.title), Some("Paid"))
    // taken off by Anna, Jan pays from it no more
    assertEquals(anna.play(Act.press("Holders"), Act.choose("Take off", "Jan"), Act.press("Yes")).at.map(_.title),
      Some("Taken off"))
    assert(Seen.of(pays(tablet, "Shop", "1")).contains("does not hold the account"))
    assertEquals(town.checkedShared(home).map(_.balance), Right(2000L))
  }

  test("the community's policy: changed from its office, inside what the hub allows; in force at once") {
    val hub = Hubs("HUB_PERSONAL" -> "business,earned,vouched,record", "HUB_MAX_PERSONAL" -> "5000")
    val founder = new Phone(hub)
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "The Guild",
      "Its place in the address" -> "guild", "How one joins" -> "open to anyone"), Act.press("OK"))
    def said = hub.json(hub.get("/n/guild/.well-known/okay-mutual.json"))
    assertEquals((said("only").string, said("join").string), ("earned", "open"))
    def joins(name: String, kind: String): Phone =
      val phone = new Phone(hub, node = "/n/guild")
      phone.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> name,
        "Account" -> kind), Act.press("OK"))
      phone
    val (shop, anna) = (joins("The shop", "Business"), joins("Anna", "Personal"))
    assert(Seen.of(anna.play()).contains("You can still spend: 0.00"), Seen.of(anna.play()))
    // the office shows the policy in force
    val shown = founder.play(Act.press("Community"), Act.press("Policy"))
    assert(Seen.of(shown).contains("Who may join: businesses, and private people who earn before they spend") &&
      Seen.of(shown).contains("Days a recovery waits: 7"), Seen.of(shown))
    def people(fields: (String, String)*) = founder.play(Act.press("Community"), Act.press("Policy"),
      Act.press("Private people"), Act.press("Save", fields*))
    // members may lend a person a line, 30.00 at most: Anna, who was there before, can be lent one at once
    val saved = people("Who may join" -> "businesses, and private people with only a line a member lends them",
      "The most a person may owe" -> "30")
    assertEquals(saved.at.map(_.title), Some("The policy is saved"), Seen.of(saved))
    assertEquals((said("only").string, said("personalMax").long), ("vouched", 3000L))
    shop.play(Act.press("Vouch"), Act.press("Save", "For" -> "Anna", "Part of your credit line" -> "20"))
    assert(Seen.of(anna.play()).contains("You can still spend: 20.00"), Seen.of(anna.play()))
    // past what the hub's operator allows a person to owe: said, and nothing changes
    val over = people("The most a person may owe" -> "60")
    assert(Seen.of(over).contains("does not allow that"), Seen.of(over))
    assertEquals(said("personalMax").long, 3000L)
    // no accounts of families here, a recovery waits three days
    val general = founder.play(Act.press("Community"), Act.press("Policy"), Act.press("Joining and businesses"),
      Act.press("Save", "Accounts of families and teams" -> "No", "Days a recovery waits" -> "3"))
    assertEquals(general.at.map(_.title), Some("The policy is saved"), Seen.of(general))
    assertEquals((said("shared").boolean, said("wait").long), (false, 3L))
    assert(Seen.of(anna.play(Act.press("Accounts"), Act.press("Shared accounts"))).contains("no shared accounts"))
    // nobody but the community's own holders sees its office
    assert(!shop.play().at.get.buttons.exists(_.label == "Community"))
  }

  test("to be paid by a link: an account's own link and code; the app opened by it goes to the payment, once") {
    val town = Town()
    val (shop, anna) = (joined(town, "The shop", "Business"), joined(town, "Anna", "Business"))
    def asks(to: String, amount: String, what: String) = Json.print(Json.obj("to" -> Json.str(to),
      "amount" -> Json.str(amount), "for" -> Json.str(what)))
    val shown = shop.play(Act.press("Receive"))
    assert(Seen.of(shown).contains(s"?node=${town.prefix}&pay=${shop.account}"), Seen.of(shown))
    // what the page keeps of such a link: whom, how much, what for
    anna.kept("pay") = asks(shop.account, "20.00", "order 17")
    val form = anna.play()
    assertEquals(form.at.map(_.title), Some("Pay"), Seen.of(form))
    anna.kept("pay") = asks(shop.account, "20.00", "order 17")
    val paid = anna.play(Act.press("Save"), Act.press("Yes"))
    assertEquals(paid.at.map(_.title), Some("Paid"), Seen.of(paid))
    assert(Seen.of(shop.play(Act.press("History"))).contains("received | Anna | 20.00 PLN | order 17"))
    // once: the app is at home again
    assertEquals(anna.play().at.map(_.title), Some("Anna"))
    // one who is no member here is not paid
    anna.kept("pay") = asks("0" * 64, "", "")
    assert(Seen.of(anna.play()).contains("not a member of this community"))
  }

  test("stamp cards: a shop's card; a stamp by a code the customer's app claims; the reward when the card is full") {
    val town = Town()
    val (shop, anna) = (joined(town, "Cafe", "Business"), joined(town, "Anna"))
    val made = shop.play(Act.press("Cards"), Act.press("Make my shop's card"), Act.press("Save",
      "Card" -> "Coffee card", "Stamps to fill it" -> "2", "Reward" -> "a free coffee"))
    assertEquals(made.at.map(_.title), Some("The card is saved"), Seen.of(made))
    // a code the shop's device signs, as the link the customer's camera opens carries it
    def code(button: String): String =
      town.now += 1000
      Seen.of(shop.play(Act.press("Cards"), Act.press(button))).linesIterator.find(_.contains("&stamp=")).get
        .split("&stamp=")(1).trim
    def scans(phone: Phone, c: String) = { phone.kept("stamp") = c; phone.play() }
    val first = code("Give a stamp")
    val one = scans(anna, first)
    assertEquals(one.at.map(_.title), Some("A stamp"), Seen.of(one))
    assert(Seen.of(one).contains("Cafe: 1 of 2."), Seen.of(one))
    // once: the app is at home again; the same code a second time is refused
    assertEquals(anna.play().at.map(_.title), Some("Anna"))
    assert(Seen.of(scans(anna, first)).contains("used already"))
    assert(Seen.of(scans(anna, code("Give the reward"))).contains("not full yet"))
    val two = scans(anna, code("Give a stamp"))
    assert(Seen.of(two).contains("Cafe: 2 of 2. The card is full: a free coffee."), Seen.of(two))
    val listed = anna.play(Act.press("Cards"))
    assert(Seen.of(listed).contains("Cafe | Coffee card | 2 / 2 ✓ | a free coffee"), Seen.of(listed))
    // the reward: taken off the card, the customer's screen says it
    val reward = scans(anna, code("Give the reward"))
    assertEquals(reward.at.map(_.title), Some("The reward"), Seen.of(reward))
    assert(Seen.of(reward).contains("Cafe: a free coffee. Show this screen to the seller."), Seen.of(reward))
    assert(Seen.of(anna.play(Act.press("Cards"))).contains("Cafe | Coffee card | 0 / 2 | a free coffee"))
    // a shop does not stamp its own card; what is no code is said to be none
    assert(Seen.of(scans(shop, code("Give a stamp"))).contains("your own shop's code"))
    assert(Seen.of(scans(anna, "bm90IGEgY29kZSBhdCBhbGw")).contains("not a shop's code"))
    // a stamp is no unit
    assert(Seen.of(anna.play()).contains("Balance: 0.00") && Seen.of(shop.play()).contains("Balance: 0.00"))
  }

  test("a community outlives its founder's phone: a second manager from the office; else the members elect one") {
    val day = 86400000L
    val hub = Hubs()
    val founder = new Phone(hub)
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "Our Town",
      "Its place in the address" -> "our-town"), Act.press("OK"))
    val community = founder.account
    // one device governs: the office says so, and a second manager is added there as a device is
    val office = founder.play(Act.press("Community"))
    assert(Seen.of(office).contains("Warning: One device governs this community"), Seen.of(office))
    val second = new Phone(hub, node = "/n/our-town")
    second.play(Act.press("Add this device to my account"))
    val added = founder.play(Act.press("Community"), Act.press("Managers"), Act.press("Add a device"),
      Act.press("Save", "The new device's key" -> second.key, "May manage the account" -> "true"))
    assertEquals(added.at.map(_.title), Some("Added"), Seen.of(added))
    assert(!Seen.of(founder.play(Act.press("Community"))).contains("One device governs"))
    // the members, for when both are lost
    def joins(name: String): Phone =
      val phone = new Phone(hub, node = "/n/our-town")
      phone.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> name,
        "Account" -> "Business"), Act.press("OK"))
      founder.play(Act.press("Community"), Act.press("Requests (1)"), Act.choose("Admit", name), Act.press("Save"))
      assertEquals(phone.play().at.map(_.title), Some(name))
      phone
    val (anna, jan, olaf) = (joins("Anna"), joins("Jan"), joins("Olaf"))
    def elects(phone: Phone, fields: (String, String)*) = phone.play(Act.press("Check the node"),
      Act.press("Elect a manager"), Act.press("Say this key", fields*))
    // a member of a day has no say
    assert(Seen.of(elects(anna)).contains("members for a month"), Seen.of(elects(anna)))
    hub.now += 31 * day
    val one = elects(anna)
    assert(Seen.of(one).contains("Your word is kept: 1 of 2."), Seen.of(one))
    val shown = jan.play(Act.press("Check the node"), Act.press("Elect a manager"))
    assert(Seen.of(shown).contains("2 members of a month or more") && Seen.of(shown).contains("| 1 / 2"),
      Seen.of(shown))
    // Jan says Anna's key too: announced on the community's own chain; in force after the week
    val two = elects(jan, "The new manager's device key" -> anna.key)
    assert(Seen.of(two).contains("the key is announced on the community's chain"), Seen.of(two))
    val waits = olaf.play(Act.press("Check the node"), Act.press("Elect a manager"))
    assert(Seen.of(waits).contains("Info: Announced: the key"), Seen.of(waits))
    assert(Seen.of(olaf.play(Act.press("Check the node"), Act.press("Elect a manager"),
      Act.press("Take the account up"))).contains("Not yet"))
    hub.now += 7 * day + 1000
    val up = olaf.play(Act.press("Check the node"), Act.press("Elect a manager"), Act.press("Take the account up"))
    assert(Seen.of(up).contains("The new manager governs now"), Seen.of(up))
    // Anna's phone takes the community's account up and governs
    val taken = anna.play(Act.press("Accounts"), Act.press("Open or add another"),
      Act.press("Add this device to my account"), Act.press("It is added", "The account's id" -> community),
      Act.press("OK"))
    assertEquals(taken.at.map(_.title), Some("Our Town"), Seen.of(taken))
    assert(taken.at.get.buttons.exists(_.label == "Community"))
  }

  test("a community's copy and its move: saved from the office, brought to another hub, closed at its old place") {
    val hub = Hubs()
    val founder = new Phone(hub)
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "Our Town",
      "Its place in the address" -> "our-town", "How one joins" -> "open to anyone"), Act.press("OK"))
    val anna = new Phone(hub, node = "/n/our-town")
    anna.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> "Anna",
      "Account" -> "Business"), Act.press("OK"))
    // the copy: the whole community as one file, its key in it — the office says so
    val screen = founder.play(Act.press("Community"), Act.press("Move or back up"))
    assert(Seen.of(screen).contains("Warning: The file holds the community's key"), Seen.of(screen))
    val saved = founder.play(Act.press("Community"), Act.press("Move or back up"), Act.press("Save a copy"))
    assertEquals(saved.at.map(_.title), Some("Saved"), Seen.of(saved))
    val (name, file) = founder.saved.last
    assert(name.startsWith("our-town-") && name.endsWith(".okay-mutual.json"), name)
    // another hub: brought from the file where communities are made; every account there as it was
    val south = Hubs()
    val newcomer = new Phone(south)
    val bad = newcomer.play(Act.press("Bring a community from a file"), Act.press("Save",
      "The community's file" -> "{\"not\":\"a file\"}", "Its place in the address" -> "town"))
    assert(Seen.of(bad).contains("not a community's file"), Seen.of(bad))
    val brought = newcomer.play(Act.press("Bring a community from a file"), Act.press("Save",
      "The community's file" -> file, "Its place in the address" -> "town"))
    assertEquals(brought.at.map(_.title), Some("The community is here"), Seen.of(brought))
    assertEquals(south.json(south.get(s"/n/town/api/accounts/${anna.account}"))("id").string, anna.account)
    assertEquals(south.json(south.get("/n/town/.well-known/okay-mutual.json"))("name").string, "Our Town")
    // closed at its old place: an app that comes there is sent where it went (here: another community of this hub)
    founder.play(Act.press("Accounts"), Act.press("Another community"), Act.press("Create a community"),
      Act.press("Save", "The community's name" -> "Next Town", "Its place in the address" -> "next-town"),
      Act.press("OK"))
    founder.play(Act.press("Accounts"), Act.press("Another community"), Act.choose("Use", "Our Town"))
    val closed = founder.play(Act.press("Community"), Act.press("Move or back up"), Act.press("It moved: close it here"),
      Act.press("Save", "Its new address" -> "/n/next-town"), Act.press("Yes"))
    assertEquals(closed.at.map(_.title), Some("Closed here"), Seen.of(closed))
    val told = anna.play()
    assertEquals(told.at.map(_.title), Some("The community moved"), Seen.of(told))
    assertEquals(anna.play(Act.press("Go there")).at.map(_.title), Some("Okay Mutual"))
    assertEquals(anna.kept("node"), "/n/next-town")
  }

  test("leaving: an account closed from the app at zero, its name and memos erased; the office closes one who left") {
    val town = Town()
    val (anna, jan) = (joined(town, "Anna", "Business"), joined(town, "Jan", "Business"))
    anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Jan", "Amount" -> "20", "For what" -> "a red dress"),
      Act.press("Yes"))
    // not while it owes: told how much and why
    val owes = anna.play(Act.press("Devices"), Act.press("Close the account"))
    assert(Seen.of(owes).contains("The balance is -20.00 PLN. An account is closed at zero"), Seen.of(owes))
    jan.play(Act.press("Pay"), Act.press("Save", "To" -> "Anna", "Amount" -> "20"), Act.press("Yes"))
    val asked = anna.play(Act.press("Devices"), Act.press("Close the account"))
    assert(Seen.of(asked).contains("Your name, what your payments were for"), Seen.of(asked))
    val closed = anna.play(Act.press("Devices"), Act.press("Close the account"), Act.press("Yes"))
    assertEquals(closed.at.map(_.title), Some("Closed"), Seen.of(closed))
    // this device holds it no more: the app starts again; the members list has no Anna
    assertEquals(anna.play().at.map(_.title), Some("Okay Mutual"))
    assertEquals(town.json(town.get("/api/members"))("members").list.map(_("name").string), Vector("Jan"))
    // Jan's history keeps his own record of the deal, with no name of hers
    val history = Seen.of(jan.play(Act.press("History")))
    assert(history.contains("a red dress") && !history.contains("Anna"), history)
  }

  test("the office closes the account of one who left: those at zero; the member's app is told") {
    val hub = Hubs()
    val founder = new Phone(hub)
    founder.play(Act.press("Create a community"), Act.press("Save", "The community's name" -> "Our Town",
      "Its place in the address" -> "our-town", "How one joins" -> "open to anyone"), Act.press("OK"))
    def joins(name: String): Phone =
      val phone = new Phone(hub, node = "/n/our-town")
      phone.play(Act.press("Open an account"), Act.press("I accept"), Act.press("Save", "Your name" -> name,
        "Account" -> "Business"), Act.press("OK"))
      phone
    val (gone, owes) = (joins("Gone"), joins("Owes"))
    owes.play(Act.press("Pay"), Act.press("Save", "To" -> "Gone", "Amount" -> "5"), Act.press("Yes"))
    gone.play(Act.press("Pay"), Act.press("Save", "To" -> "Owes", "Amount" -> "5"), Act.press("Yes"))
    owes.play(Act.press("Pay"), Act.press("Save", "To" -> "Gone", "Amount" -> "5"), Act.press("Yes"))
    val list = founder.play(Act.press("Community"), Act.press("Close accounts of those who left"))
    assert(!Seen.of(list).contains("Owes |"), Seen.of(list))
    gone.play(Act.press("Pay"), Act.press("Save", "To" -> "Owes", "Amount" -> "5"), Act.press("Yes"))
    val done = founder.play(Act.press("Community"), Act.press("Close accounts of those who left"),
      Act.choose("Close the account", "Gone"), Act.press("Yes"))
    assertEquals(done.at.map(_.title), Some("Closed"), Seen.of(done))
    val told = gone.play()
    assert(Seen.of(told).contains("Gone was closed by the community"), Seen.of(told))
    assertEquals(gone.play(Act.press("OK")).at.map(_.title), Some("Okay Mutual"))
  }

  test("knowing one was paid: Receive turns into what came; the home says once what came since it was last seen") {
    val town = Town()
    val (shop, anna) = (joined(town, "Cafe", "Business"), joined(town, "Anna", "Business"))
    // nothing came yet: "Check" leaves Receive as it was
    assertEquals(shop.play(Act.press("Receive"), Act.press("Check")).at.map(_.title), Some("Receive"))
    // the seller shows Receive; the customer pays; the seller's screen says it (the page by itself, here "Check")
    def pays(amount: String, what: String) = anna.play(Act.press("Pay"), Act.press("Save", "To" -> "Cafe",
      "Amount" -> amount, "For what" -> what), Act.press("Yes"))
    // the customer pays while the seller's Receive is open: just before the seller's app asks the node again
    var asked = 0
    def arm(): Unit = shop.meanwhile = () => { asked += 1; if asked < 2 then arm() else pays("12.50", "two coffees") }
    arm()
    val got = shop.play(Act.press("Receive"), Act.press("Check"))
    assertEquals(got.at.map(_.title), Some("Received"), Seen.of(got))
    assert(Seen.of(got).contains("Info: Received 12.50 PLN from Anna — for: two coffees."), Seen.of(got))
    // the home, next time: what came since it was last seen — once
    pays("3", "")
    val home = shop.play()
    assert(Seen.of(home).contains("Info: Received 12.50 PLN from Anna — for: two coffees.") &&
      Seen.of(home).contains("Info: Received 3.00 PLN from Anna."), Seen.of(home))
    assert(!Seen.of(shop.play()).contains("Received"), Seen.of(shop.play()))
  }
