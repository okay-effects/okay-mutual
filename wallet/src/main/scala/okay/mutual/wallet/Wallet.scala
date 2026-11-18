package okay.mutual.wallet

import okay.business.scala3.*
import okay.effects.json.{Descent, Json}
import okay.effects.time.Date
import okay.mutual.chain.*
import okay.scala3.std.Clock

/**
 * THE WALLET (specs/protocol.md "The wallet"): a member's side of the network, in okay-ui's words — screens, forms,
 * tables — so the same program is a page on a phone, a terminal, a test's script. What it knows of the world it asks:
 * the node of `Net`, this device's key of `Device`, signatures checked of `Signer`, what it remembers of `Settings`,
 * the time of `Clock`, the person of `Ui`.
 *
 * It trusts the node with nothing it can check: every debit is signed here, by a key that never leaves the device;
 * "Check the node" folds the account's chain again from its first record, checks every signature and that the
 * chain's head is under the node's signed head — and remembers that head, to see a node that goes back on it.
 *
 * It also is the community's office: who holds a key that may manage the community's own account sets the members'
 * limits from here, by signed commands. There is no other administrator
 */
final class Wallet[R[_]](using In[Ui, R], In[Net, R], In[Device, R], In[Settings, R], In[Signer, R], In[Clock, R]):
  private val week = 7L * 86400000L

  // ---- what the wallet remembers: the accounts this device holds, the one in use

  private def held: Vector[(String, String)] ! R = setting[R]("accounts").map(_.flatMap(t => Descent.parse(t).toOption)
    .flatMap(j => scala.util.Try(j.list.map(a => a("id").string -> a("name").string)).toOption).getOrElse(Vector.empty))
  private def remember(id: String, name: String): Unit ! R = held.flatMap(hs =>
    keep[R]("accounts", Json.print(Json.Arr((hs.filterNot(_._1 == id) :+ (id -> name)).map((i, n) =>
      Json.obj("id" -> Json.str(i), "name" -> Json.str(n)))))).andThen(keep[R]("current", id)))
  private def forget(id: String): Unit ! R = held.flatMap(hs => keep[R]("accounts", Json.print(Json.Arr(
    hs.filterNot(_._1 == id).map((i, n) => Json.obj("id" -> Json.str(i), "name" -> Json.str(n))))))
    .andThen(keep[R]("current", "")))

  // ---- amounts and words

  def cents(units: BigDecimal): Long = (units.setScale(2, BigDecimal.RoundingMode.HALF_UP) * 100).toLongExact
  def units(cents: Long): BigDecimal = BigDecimal(cents) / 100
  private def money(cents: Long, unit: String): String = Cell.Money(units(cents), unit).text
  private def day(at: Long): String = Date.ofEpochDay(Math.floorDiv(at, 86400000L)).toString
  private def short(key: String): String = if key.length <= 14 then key else key.take(6) + "…" + key.takeRight(6)

  /** a refusal in a person's words */
  def said(r: Refusal, unit: String): String = (r.why, r.detail) match
    case ("credit", d) => s"That is over your credit line by ${money(d.toLongOption.getOrElse(0L), unit)}."
    case ("ceiling", d) => s"That is over the ceiling by ${money(d.toLongOption.getOrElse(0L), unit)}."
    case ("limit", "payment") => "That is more than this device may pay at once."
    case ("limit", _) => "That is more than this device may pay in a week."
    case ("unsigned", _) => "This device does not hold the account, or may not do this."
    case ("forbidden", "manage") => "Only a device that may manage the account can do this."
    case ("forbidden", "last") => "One device that may manage must stay."
    case ("forbidden", "held") => "That key holds the account already."
    case ("place", _) => "The account changed meanwhile. Try again."
    case ("named", _) => "A payment of this name was made already."
    case ("admitted", _) => "This community admits its members itself: ask it for an account."
    case ("taken", _) => "This account is open already."
    case ("token", _) => "That is not the setup token."
    case ("set", _) => "This community is set up already."
    case ("offline", _) => "The node does not answer. Check the connection and try again."
    case (why, _) => s"The node refused: $why."

  // ---- the application, for as long as it is open

  def main: Unit ! R = connected.flatMap(about => setting[R]("current").flatMap(current =>
    held.flatMap { hs =>
      hs.find(h => current.contains(h._1)).orElse(hs.headOption) match
        case None => welcome(about, first = true)
        case Some((id, name)) => home(about, id, name)
    })).andThen(defer(main))

  /** the node this wallet talks to: the one it is served by, or the one the person names */
  private def connected: About ! R = Node.about[R].flatMap {
    case Some(about) => pure(about)
    case None => edit[String, R]("Choose a community's node", Form.one(Field.text("The node's address")
        .hinted("as the community gave it: https://…")), Part.text("This wallet works with any node of the " +
        "network. No node answers at the address it has.")).flatMap {
        case Some(url) => keep[R]("node", url.trim.stripSuffix("/")).andThen(defer(connected))
        case None => defer(connected)
      }
  }

  private def welcome(about: About, first: Boolean): Unit ! R = Node.community[R].flatMap { community =>
    val buttons = List(Button[Int]("Open an account", Style.Primary)(1),
      Button[Int]("Add this device to my account")(2)) ++
      (if community.isEmpty then List(Button[Int]("Set up this community")(3)) else Nil) ++
      (if first then Nil else List(Button[Int]("Back")(0)))
    show(Screen[Int]("Okay Mutual")(
      Part.text("A network of mutual credit: members sell to and buy from each other for units they issue to one " +
        s"another. A unit counts as 1 ${about.unit} and is never exchanged for money. Your key is made on this " +
        "device and never leaves it."),
      Part.details("Community" -> about.name, "Joining" -> (if about.open then "open to anyone" else
        "by the community's admission"), "A new member's credit line" -> money(about.credit, about.unit),
        "A new member's ceiling" -> money(about.ceiling, about.unit)))(buttons*)).flatMap {
      case 1 => open(about)
      case 2 => addThisDevice
      case 3 => setUp(about)
      case _ => pure(())
    }
  }

  private def opening(about: About, of: String): Signed ! R = Device.ready[R].flatMap(pub => Clock.now[R].flatMap(now =>
    Device.signed[R](Record("", 1, "", now, Body.Open(about.key, of, pub, about.unit)))))

  private def open(about: About): Unit ! R =
    edit[(String, String), R]("Open an account", Form(Field.text("Your name").hinted("as the other members will " +
      "see it"), Field.choice("Account", "Personal" -> "personal", "Business" -> "business"))).flatMap {
      case None => pure(())
      case Some((name, of)) => opening(about, of).flatMap(Node.open[R](_, name)).flatMap {
          case Right(s) => remember(s.id, name).andThen(inform[R]("Your account is open",
              s"Your credit line is ${money(-s.floor, about.unit)}: you can buy before you have sold."))
          case Left(r) => inform[R]("Not opened", said(r, about.unit))
        }
    }

  private def setUp(about: About): Unit ! R =
    edit[(String, String), R]("Set up this community", Form(Field.text("The community's name"),
      Field.secret("Setup token")), Part.text("The community's own account is opened with this device's key. " +
      "Whoever holds a key that may manage it sets the members' limits; more such keys are added under Devices.")
    ).flatMap {
      case None => pure(())
      case Some((name, token)) => opening(about, "community").flatMap(Node.setup[R](_, name, token)).flatMap {
          case Right(s) => remember(s.id, name).andThen(inform[R]("The community is set up",
              "Its account is in this wallet. Open your own account too: Accounts, then Open an account."))
          case Left(r) => inform[R]("Not set up", said(r, about.unit))
        }
    }

  /** this device's key shown, to be signed in from a device that holds the account already */
  private def addThisDevice: Unit ! R = Device.ready[R].flatMap { pub =>
    val form = Form.one(Field.text("The account's id").hinted("shown there under Devices"))
    show(Screen[Option[String]]("Add this device")(
      Part.text("On a device that holds the account: Devices, Add a device, and enter this device's key."),
      Part.qr(pub, "this device's key"), Part.code(pub), form)(
      Button.submit("It is added", form)(Some(_)), Button("Back")(None))).flatMap {
      case None => pure(())
      case Some(id) => Node.account[R](id.trim).flatMap {
          case Some(s) if s.grant(pub).isDefined => Node.members[R].flatMap(ms =>
              remember(s.id, ms.find(_.id == s.id).fold("Account")(_.name))
                .andThen(inform[R]("This device holds the account", "It can act within the limits it was given.")))
          case Some(_) => inform[R]("Not yet", "The account does not name this device's key.")
          case None => inform[R]("Not found", "No such account at this node.")
        }
    }
  }

  // ---- an account's home

  private def home(about: About, id: String, name: String): Unit ! R = Node.account[R](id).flatMap {
    case None => confirm[R]("This account is not at this node. Take it off this wallet?", name).flatMap(yes =>
        if yes then forget(id) else pure(()))
    case Some(s) => Device.ready[R].flatMap(key => Node.community[R].flatMap { community =>
        community.fold(pure[Boolean, R](false))(c => Node.account[R](c).map(_.exists(_.grant(key).exists(_.manage))))
          .flatMap { governs =>
            val buttons = List(Button[Int]("Pay", Style.Primary)(1), Button[Int]("History")(2),
              Button[Int]("Vouch")(3), Button[Int]("Devices")(4), Button[Int]("Check the node")(5)) ++
              (if governs then List(Button[Int]("Community")(6)) else Nil) :+ Button[Int]("Accounts")(7)
            show(Screen[Int](name)(Part.details("Balance" -> Cell.Money(units(s.balance), about.unit),
              "You can still spend" -> Cell.Money(units(s.balance - s.floor), about.unit),
              "You can still receive" -> Cell.Money(units(s.ceiling - s.balance), about.unit),
              "Community" -> Cell.Text(about.name)))(buttons*)).flatMap {
              case 1 => pay(about, s)
              case 2 => history(about, s)
              case 3 => vouch(about, s)
              case 4 => devices(about, s, key)
              case 5 => check(about, s.id)
              case 6 => office(about, key)
              case _ => accounts(about)
            }
          }
      })
  }

  /** a holder's record: the chain's next as the account is now, signed here, given to the node */
  private def say(s: State, body: Long => Body, memo: String = ""): Either[Refusal, State] ! R =
    Clock.now[R].flatMap(now => Device.signed[R](Record(s.id, s.seq + 1, s.head, now.max(s.at), body(now)))
      .flatMap(Node.append[R](s.id, _, memo)))

  private def others(s: State): Vector[Member] ! R = Node.members[R].map(_.filter(_.id != s.id))

  private def pay(about: About, s: State): Unit ! R = others(s).flatMap { ms =>
    if ms.isEmpty then inform[R]("Nobody to pay yet", "You are the first member here.")
    else edit[(String, BigDecimal, Option[String]), R]("Pay", Form(Field.choice("To", ms.map(m => m.name -> m.id)*),
      Field.money("Amount", about.unit).check("more than zero")(_ > 0), Field.text("For what").optional)).flatMap {
      case None => pure(())
      case Some((to, amount, what)) =>
        val who = ms.find(_.id == to).fold(to)(_.name)
        val memo = what.getOrElse("").trim
        confirm[R](s"Pay ${money(cents(amount), about.unit)} to $who?", "Pay").flatMap { yes =>
          if !yes then pure(())
          // the payment's name: one a payment, so sent again — a second press, a lost answer — it is paid once
          else say(s, now => Body.Send(to, cents(amount), if memo.isEmpty then "" else Sha256.hex(memo),
            Sha256.hex(s"${s.id}:${s.seq}:$now").take(24)), memo).flatMap {
            case Right(after) if after.balance == s.balance => inform[R]("It came back",
                s"$who cannot take it now: they are at their ceiling and must spend first. Nothing was paid.")
            case Right(after) => inform[R]("Paid", s"${money(cents(amount), about.unit)} to $who. Your balance: " +
                s"${money(after.balance, about.unit)}.")
            case Left(r) => inform[R]("Not paid", said(r, about.unit))
          }
        }
    }
  }

  private def vouch(about: About, s: State): Unit ! R = others(s).flatMap { ms =>
    if ms.isEmpty then inform[R]("Nobody to vouch for yet", "You are the first member here.")
    else edit[(String, BigDecimal), R]("Vouch", Form(Field.choice("For", ms.map(m => m.name -> m.id)*),
      Field.money("Part of your credit line", about.unit).check("more than zero")(_ > 0)),
      Part.text("You lend part of your own credit line to a member you trust. Your own room shrinks by it until " +
        "they have earned their way back. There is no reward: only your own room at risk.")).flatMap {
      case None => pure(())
      case Some((to, amount)) => say(s, _ => Body.Vouch(to, cents(amount))).flatMap {
          case Right(after) if after.lent == s.lent => inform[R]("It came back",
              "That member has a voucher already.")
          case Right(_) => inform[R]("Vouched", s"${money(cents(amount), about.unit)} of your line is lent.")
          case Left(r) => inform[R]("Not vouched", said(r, about.unit))
        }
    }
  }

  private final case class Row(seq: Long, day: String, what: String, who: String, amount: BigDecimal, memo: String)

  private def history(about: About, s: State): Unit ! R = Node.members[R].flatMap(ms => Node.chain[R](s.id).flatMap {
    lines =>
      def name(id: String): String = ms.find(_.id == id).fold(short(id))(_.name)
      val rows = lines.map { l =>
        val (what, who, amount) = l.record.body match
          case _: Body.Open => ("account opened", "", 0L)
          case Body.Send(to, a, _, _) => ("paid", name(to), -a)
          case Body.Receive(from, _, a) => ("received", name(from), a)
          case Body.Bounce(_, a) => ("came back", "", a)
          case Body.Limits(credit, ceiling) => (s"limits: line ${money(credit, about.unit)}, ceiling " +
              money(ceiling, about.unit), about.name, 0L)
          case Body.Granted(g) => (s"device added: ${short(g.key)}", "", 0L)
          case Body.Revoke(k) => (s"device removed: ${short(k)}", "", 0L)
          case Body.Vouch(to, a) => (s"line lent: ${money(a, about.unit)}", name(to), 0L)
          case Body.Vouched(from, _, a) => (s"line received: ${money(a, about.unit)}", name(from), 0L)
          case Body.Release(a) => (s"line given back: ${money(a, about.unit)}", "", 0L)
          case Body.Released(_, a) => (s"line returned: ${money(a, about.unit)}", "", 0L)
        Row(l.record.seq, day(l.record.at), what, who, units(amount), l.memo)
      }.reverse.toList
      show(Screen[Unit]("History")(Table(rows)(Column.text("Day")(_.day), Column.text("What")(_.what),
        Column.text("With")(_.who), Column.money("Amount", about.unit)(_.amount), Column.text("For")(_.memo))
        .keyedBy(_.seq.toString).whenEmpty("nothing yet"))(Button("Back")(())))
  })

  // ---- devices: who may act for the account

  private def devices(about: About, s: State, mine: String): Unit ! R =
    def may(g: Grant) = (g.spend, g.manage) match
      case (true, true) => "spend and manage"
      case (true, false) => "spend"
      case (false, true) => "manage"
      case _ => "nothing"
    def limit(g: Grant) = List(Option.when(g.payment > 0)(s"${money(g.payment, about.unit)} a payment"),
      Option.when(g.period > 0)(s"${money(g.period, about.unit)} in ${g.per / 86400000L} days")).flatten match
      case Nil => "the account's own"
      case some => some.mkString(", ")
    val table = Table(s.grants.toList)(Column.text("Key")(g => short(g.key) + (if g.key == mine then
      " (this device)" else "")), Column.text("May")(may), Column.text("At most")(limit)).keyedBy(_.key)
    show(Screen[Option[Option[Grant]]]("Devices")(Part.text("Each device has its own key. A lost device is taken " +
      "off here from another one; what it had handed on goes with it."), Part.details("The account's id" -> s.id),
      table)(Button("Add a device", Style.Primary)(Some(None)),
      Button.select("Take off", table, Style.Danger)(g => Some(Some(g))), Button("Back")(None))).flatMap {
      case None => pure(())
      case Some(None) => edit[(String, Boolean, Option[BigDecimal], Option[BigDecimal]), R]("Add a device", Form(
          Field.text("The new device's key").hinted("shown there under Add this device to my account"),
          Field.flag("May manage the account"), Field.money("At most a payment", about.unit).optional,
          Field.money("At most a week", about.unit).optional)).flatMap {
          case None => pure(())
          case Some((key, manage, payment, weekly)) =>
            val period = weekly.fold(0L)(cents)
            say(s, _ => Body.Granted(Grant(key.trim, spend = true, manage, payment.fold(0L)(cents), period,
              if period > 0 then week else 0L))).flatMap {
              case Right(_) => inform[R]("Added", "That device now holds the account.")
              case Left(r) => inform[R]("Not added", said(r, about.unit))
            }
        }
      case Some(Some(g)) => confirm[R](s"Take ${short(g.key)} off the account, with every device it added?",
          "Take off").flatMap(yes => if !yes then pure(()) else say(s, _ => Body.Revoke(g.key)).flatMap {
          case Right(_) => inform[R]("Taken off", "That key no longer acts for the account.")
          case Left(r) => inform[R]("Not taken off", said(r, about.unit))
        })
    }

  // ---- the node, checked

  private def check(about: About, id: String): Unit ! R =
    working[R]("Checking the node")(Part.text("Every record and signature of your account, from the first.")).andThen(
    Node.chain[R](id)).flatMap(lines => Check.chain[R](lines.map(_.signed)).flatMap(folded =>
      Node.account[R](id).flatMap(stated => Node.proof[R](id).flatMap { proof =>
        proof.fold(pure[Boolean, R](false))(p => Signer.verify[R](about.key, p.head.text,
          p.head.sigs.find(_._1 == about.key).fold("")(_._2))).flatMap { signed =>
          val kept = s"head:${about.key}"
          setting[R](kept).flatMap { before =>
            val (number, root) = before.map(_.split(':')).collect { case Array(n, r) =>
              (n.toLongOption.getOrElse(0L), r) }.getOrElse((-1L, ""))
            val chain = folded match
              case Right(s) if s.node != about.key => Part.problem("The chain names another node's key.")
              case Right(s) if !stated.exists(n => n.head == s.head && n.balance == s.balance) =>
                Part.problem("The node's account is not what its own records make.")
              case Right(s) => Part.info(s"${s.seq} records, every signature holds; they make a balance of " +
                  s"${money(s.balance, about.unit)}.")
              case Left((at, fault)) => Part.problem(s"Record ${at + 1} does not hold: $fault.")
            val head = (proof, folded) match
              case (None, _) => Part.problem("The node gave no proof for this account.")
              case (Some(_), _) if !signed => Part.problem("The node's head is not signed by its key.")
              case (Some(p), Right(s)) if p.accountHead != s.head || !Merkle.holds(p.root, id, p.accountHead,
                p.steps) => Part.problem("Your chain is not under the node's signed head.")
              case (Some(p), _) if p.number < number => Part.problem(s"The node's head went back: number " +
                  s"${p.number} after $number.")
              case (Some(p), _) if p.number == number && p.root != root => Part.problem("Two different heads of " +
                  s"one number (${p.number}): the node showed two histories.")
              case (Some(p), _) => Part.info(s"Your chain is under the node's signed head number ${p.number}.")
            val remembered = (proof, head) match
              case (Some(p), Part.Notice(Level.Info, _)) => keep[R](kept, s"${p.number}:${p.root}")
              case _ => pure[Unit, R](())
            remembered.andThen(show(Screen[Unit]("The node, checked")(chain, head, Part.text("This device did " +
              "the sums itself; the node was only asked for the records."))(Button("Back")(()))))
          }
        }
      })))

  // ---- the community's office: for a device that may manage the community's own account

  private def office(about: About, key: String): Unit ! R = Node.members[R].flatMap { ms =>
    ms.foldLeft(pure[Vector[(Member, State)], R](Vector.empty))((acc, m) => acc.flatMap(all =>
      Node.account[R](m.id).map(_.fold(all)(s => all :+ (m -> s))))).flatMap { all =>
      val table = Table(all.toList)(Column.text("Member")(_._1.name), Column.text("Account")(_._1.of),
        Column.money("Balance", about.unit)(x => units(x._2.balance)),
        Column.money("Credit line", about.unit)(x => units(x._2.credit)),
        Column.money("Ceiling", about.unit)(x => units(x._2.ceiling))).keyedBy(_._1.id).whenEmpty("no members yet")
      show(Screen[Option[(Member, State)]](s"Community · ${about.name}")(Part.details(
        "Members" -> Cell.Number(BigDecimal(all.size)),
        "The balances' sum" -> Cell.Money(units(all.map(_._2.balance).sum), about.unit)),
        Part.text("The sum of all balances is zero, always: a unit is born when a member buys and gone when it " +
          "sells."), table)(Button.select("Set limits", table)(Some(_)), Button("Back")(None))).flatMap {
        case None => pure(())
        case Some((m, s)) => edit[(BigDecimal, BigDecimal), R](s"Limits · ${m.name}", Form(
            Field.money("Credit line", about.unit).initially(units(s.credit)).check("not below zero")(_ >= 0),
            Field.money("Ceiling", about.unit).initially(units(s.ceiling)).check("not below zero")(_ >= 0))).flatMap {
            case None => pure(())
            case Some((credit, ceiling)) => Clock.now[R].flatMap { now =>
                val text = Json.print(Json.obj("cmd" -> Json.str("limits"), "at" -> Json.num(now),
                  "account" -> Json.str(m.id), "credit" -> Json.num(cents(credit)),
                  "ceiling" -> Json.num(cents(ceiling))))
                Device.sign[R](text).flatMap(sig => Node.command[R](text, key, sig)).flatMap {
                  case Right(_) => inform[R]("Set", s"${m.name}: line ${money(cents(credit), about.unit)}, " +
                      s"ceiling ${money(cents(ceiling), about.unit)}.")
                  case Left(r) => inform[R]("Not set", said(r, about.unit))
                }
              }
          }.andThen(defer(office(about, key)))
      }
    }
  }

  // ---- the accounts this device holds

  private def accounts(about: About): Unit ! R = held.flatMap { hs =>
    val table = Table(hs.toList)(Column.text("Account")(_._2), Column.text("Id")(h => short(h._1))).keyedBy(_._1)
    show(Screen[Option[Option[String]]]("Accounts")(Part.text("One device can hold several accounts: your own, " +
      "your family's, your company's."), table)(Button.select("Use", table)(h => Some(Some(h._1))),
      Button("Open or add another")(Some(None)), Button("Back")(None))).flatMap {
      case Some(Some(id)) => keep[R]("current", id)
      case Some(None) => welcome(about, first = false)
      case None => pure(())
    }
  }
