package okay.mutual.wallet

import okay.business.scala3.*
import okay.effects.json.{Descent, Json}
import okay.mutual.chain.*
import okay.mutual.node.Net as Town
import okay.scala3.std.Clock
import okay.serverless.cloud.Request

/** A DEVICE IN A TEST: its own key (the JDK's), what its wallet remembers, the wallet played by a script against the
 * node itself in memory — the same program a page runs */
final class Phone(town: Town):
  var pair: Option[Pair] = None
  val kept = scala.collection.mutable.Map.empty[String, String]
  type World[X] = (Ui + Settings + Clock + Net + Device + Signer)[X]

  private def net[R[_], Rest[_], A](p: A ! R)(using Split[Net, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Net, Rest, A, A](a => pure(a), [X] => (asked: Net[X]) => asked match
      case Net.Call(method, path, body, token) =>
        val r = town.ask(Request(method, path).copy(body = body.fold("")(Json.print(_)), headers =
          Map("content-type" -> List("application/json")) ++ (if token.isEmpty then Map.empty else
            Map("x-setup-token" -> List(token)))))
        (r.status, Descent.parse(r.body).toOption.getOrElse(Json.Null))))

  private def device[R[_], Rest[_], A](p: A ! R)(using Split[Device, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Device, Rest, A, A](a => pure(a), [X] => (asked: Device[X]) => asked match
      case Device.Key() => pair.map(_.pub)
      case Device.Make() =>
        pair = Some(SignerJdk.generate())
        pair.get.pub
      case Device.Sign(text) => SignerJdk.sign(pair.get.secret, text)))

  /** the wallet from its start, the acts done in order: what was seen, and the screen it stopped at */
  def play(acts: Act*): Played[(Long, Unit)] =
    Play(steps(keptIn(kept)(Clock.virtual(town.now)(SignerJdk.run(device(net(Wallet[World].main)))))), acts*)

  def key: String = pair.get.pub
  def account: String = kept("current")

object Seen:
  /** a screen's words: its texts, notices, details and the cells of its tables */
  def of(p: Played[?]): String = p.at.fold("")(s => (s.title +: s.parts.flatMap(part)).mkString("\n"))
  private def part(p: Part): List[String] = p match
    case Part.Text(s) => List(s)
    case Part.Notice(level, s) => List(s"$level: $s")
    case Part.Details(rows) => rows.map((label, cell) => s"$label: ${cell.text}")
    case Part.Section(title, parts) => title :: parts.flatMap(part)
    case t: Table[r] => t.rows.map(row => t.cells(row).map(_.text).mkString(" | "))
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
    assertEquals(Phone(town).play(Act.press("Open an account"), Act.press("Save")).problems.size, 2)
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
      "Test Town", "Setup token" -> "setup-token"), Act.press("OK"))
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
