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
 * the time of `Clock`, the person of `Ui`. What it says, it says in `w`: a language is a value (`Words`).
 *
 * It trusts the node with nothing it can check: every debit is signed here, by a key that never leaves the device;
 * "Check the node" folds the account's chain again from its first record, checks every signature and that the
 * chain's head is under the node's signed head — and remembers that head, to see a node that goes back on it.
 *
 * It also is the community's office: who holds a key that may manage the community's own account sets the members'
 * limits from here, by signed commands. There is no other administrator
 */
final class Wallet[R[_]](w: Words = Words.En)(using In[Ui, R], In[Net, R], In[Device, R], In[Settings, R],
  In[Signer, R], In[Clock, R], In[Clipboard, R]):
  private val week = 7L * 86400000L

  // ---- the screens every program has, in this wallet's words (okay-ui's own are English)

  private def inform(title: String, text: String): Unit ! R =
    show(Screen[Unit](title)(Part.text(text))(Button(w.ok, Style.Primary)(())))
  private def confirm(question: String, title: String): Boolean ! R =
    show(Screen[Boolean](title)(Part.text(question))(Button(w.yes, Style.Primary)(true), Button(w.no)(false)))
  private def edit[P](title: String, form: Form[P], parts: Part*): Option[P] ! R =
    show(Screen[Option[P]](title)((parts :+ form)*)(Button.submit(w.save, form)(Some(_)), Button(w.cancel)(None)))
  /** a field whose own complaints (okay-ui's: "required"…) are said in this wallet's language */
  private def said[A](f: Field[A]): Field[A] = f.copy(read = e => f.read(e).left.map(w.problem))

  // ---- what the wallet remembers: the accounts this device holds, the one in use

  /** what is remembered of the node in use, apart from every other node's: `<name>:<the node's address>` */
  private def scoped(name: String): String ! R = setting[R]("node").map(n => s"$name:${n.getOrElse("")}")
  private def here(name: String): Option[String] ! R = scoped(name).flatMap(setting[R](_)).map(_.filter(_.nonEmpty))
  private def keepHere(name: String, value: String): Unit ! R = scoped(name).flatMap(keep[R](_, value))

  private def held: Vector[(String, String)] ! R = here("accounts").map(_.flatMap(t => Descent.parse(t).toOption)
    .flatMap(j => scala.util.Try(j.list.map(a => a("id").string -> a("name").string)).toOption).getOrElse(Vector.empty))
  private def listed(hs: Vector[(String, String)]): String =
    Json.print(Json.Arr(hs.map((i, n) => Json.obj("id" -> Json.str(i), "name" -> Json.str(n)))))
  private def remember(id: String, name: String): Unit ! R = held.flatMap(hs =>
    keepHere("accounts", listed(hs.filterNot(_._1 == id) :+ (id -> name))).andThen(keepHere("current", id)))
  private def forget(id: String): Unit ! R = held.flatMap(hs =>
    keepHere("accounts", listed(hs.filterNot(_._1 == id))).andThen(keepHere("current", "")))
  /** the request to join that waits at this node: the account's id it will have, the name asked under */
  private def pending: Option[(String, String)] ! R = here("pending").map(_.flatMap(t => Descent.parse(t).toOption)
    .flatMap(j => scala.util.Try(j("id").string -> j("name").string).toOption))

  // ---- amounts and words

  def cents(units: BigDecimal): Long = (units.setScale(2, BigDecimal.RoundingMode.HALF_UP) * 100).toLongExact
  def units(cents: Long): BigDecimal = BigDecimal(cents) / 100
  private def money(cents: Long, unit: String): String = Cell.Money(units(cents), unit).text
  private def day(at: Long): String = Date.ofEpochDay(Math.floorDiv(at, 86400000L)).toString
  private def short(key: String): String = if key.length <= 14 then key else key.take(6) + "…" + key.takeRight(6)

  /** a refusal in a person's words */
  def refusal(r: Refusal, unit: String): String = (r.why, r.detail) match
    case ("credit", d) => w.overCredit(money(d.toLongOption.getOrElse(0L), unit))
    case ("ceiling", d) => w.overCeiling(money(d.toLongOption.getOrElse(0L), unit))
    case ("limit", "payment") => w.limitPayment
    case ("limit", _) => w.limitWeek
    case ("unsigned", _) => w.unsigned
    case ("forbidden", "manage") => w.onlyManage
    case ("forbidden", "last") => w.lastManager
    case ("forbidden", "held") => w.heldAlready
    case ("forbidden", "credit") => w.noCreditHere
    case ("place", _) => w.changedMeanwhile
    case ("named", _) => w.namedAlready
    case ("admitted", _) => w.admitsItself
    case ("rules", _) => w.rulesChanged
    case ("business", _) => w.onlyBusinesses
    case ("taken", _) => w.openAlready
    case ("token", _) => w.notTheToken
    case ("set", _) => w.setUpAlready
    case ("offline", _) => w.offline
    case (why, _) => w.refused(why)

  // ---- the application, for as long as it is open

  def main: Unit ! R = connected.flatMap(about => here("current").flatMap(current =>
    held.flatMap { hs =>
      hs.find(h => current.contains(h._1)).orElse(hs.headOption) match
        case Some((id, name)) => home(about, id, name)
        case None => pending.flatMap {
            case Some((id, name)) => waiting(about, id, name)
            case None => welcome(about, first = true)
          }
    })).andThen(defer(main))

  /** the node this wallet talks to: the one it is served by, or the one the person names */
  private def connected: About ! R = Node.about[R].flatMap {
    case Some(about) => pure(about)
    case None => find.andThen(defer(connected))
  }

  /** NO NODE IN USE: the communities of the hub this wallet is at, an address given by a community elsewhere, or a
   * new community made */
  private def find: Unit ! R = Node.listed[R].flatMap { nodes =>
    val table = Table(nodes.toList)(Column.text(w.community)(_.name), Column.text(w.unitShort)(_.unit),
      Column.text(w.joining)(n => if n.open then w.joinOpen else w.joinAdmitted)).keyedBy(_.slug)
      .whenEmpty(w.noCommunities)
    val form = Form.one(said(Field.text(w.nodeAddress).hinted(w.nodeHint)))
    // the table a part of the screen itself (a button chooses its row), under its own line of a title
    show(Screen[Option[String]](w.findCommunity)(Part.text(w.findText), Part.section(w.communitiesHere)(), table,
      form)(Button.select(w.use, table)(n => Some(s"/n/${n.slug}")),
      Button.submit(w.useAddress, form, Style.Plain)(a => Some(a.trim.stripSuffix("/"))),
      Button(w.createCommunity)(None))).flatMap {
      case Some(address) => keep[R]("node", address)
      case None => create
    }
  }

  /** A COMMUNITY MADE: its node at the hub, then its own account opened by this device's key — its founder's */
  private def create: Unit ! R =
    edit[(String, String, String, Boolean, String, BigDecimal, BigDecimal, String)](w.createCommunity, Form(
      said(Field.text(w.communityName)), said(Field.text(w.place).hinted(w.placeHint)),
      said(Field.text(w.unitLabel).initially("PLN")),
      said(Field.choice(w.howJoin, w.byAdmission -> false, w.joinOpen -> true).initially(false)),
      // private people take part, and where a community starts they earn before they spend (docs/legal.md: no
      // credit is given to a consumer, so the rules on consumer credit do not arise)
      said(Field.choice(w.whoMayJoin, w.peopleEarn -> "earned", w.businessesOnly -> "business", w.peopleCredit -> "")
        .initially("earned")),
      said(Field.decimal(w.newcomersLine).initially(BigDecimal(100)).check(w.notBelowZero)(_ >= 0)),
      said(Field.decimal(w.newcomersCeiling).initially(BigDecimal(300)).check(w.notBelowZero)(_ >= 0)),
      said(Field.longText(w.rulesLabel).hinted(w.rulesHint).initially(w.standardRules))),
      Part.text(w.createText)).flatMap {
      case None => pure(())
      case Some((name, place, unit, open, only, line, ceiling, rules)) =>
        Node.make[R](place.trim.toLowerCase, name, unit.trim, open, cents(line), cents(ceiling), only, rules.trim)
          .flatMap {
          case Left(r) => inform(w.notSetUp, r.why match
              case "taken" => w.placeTaken
              case "body" => w.placeBad
              case "limit" | "full" => w.hubLimit
              case "token" => w.hubClosed
              case _ => refusal(r, unit))
          case Right((address, token)) => keep[R]("node", address).andThen(Node.about[R]).flatMap {
              case None => inform(w.notSetUp, w.offline)
              case Some(about) => opening(about, "community").flatMap(Node.setup[R](_, name, token)).flatMap {
                  case Right(s) => remember(s.id, name).andThen(inform(w.communityCreated, w.communityCreatedText))
                  case Left(r) => inform(w.notSetUp, refusal(r, about.unit))
                }
            }
        }
    }

  /** asked to join, not yet admitted: the account taken up as soon as it is there */
  private def waiting(about: About, id: String, name: String): Unit ! R = Node.account[R](id).flatMap {
    case Some(_) => remember(id, name).andThen(keepHere("pending", ""))
    case None => show(Screen[Int](w.asked)(Part.text(w.waitingText(about.name)))(
        Button(w.checkAgain, Style.Primary)(1), Button(w.withdraw)(2), Button(w.anotherCommunity)(3))).flatMap {
        case 2 => keepHere("pending", "")
        case 3 => keep[R]("node", "")
        case _ => pure(())
      }
  }

  private def welcome(about: About, first: Boolean): Unit ! R = Node.community[R].flatMap { community =>
    val buttons = List(Button[Int](w.openAccount, Style.Primary)(1), Button[Int](w.addThisDevice)(2)) ++
      (if community.isEmpty then List(Button[Int](w.setUpCommunity)(3)) else Nil) ++
      (if first then Nil else List(Button[Int](w.back)(0)))
    show(Screen[Int]("Okay Mutual")(Part.text(w.intro(about.unit)),
      Part.details(w.community -> about.name, w.joining -> (if about.open then w.joinOpen else w.joinAdmitted),
        w.newCredit -> money(about.credit, about.unit), w.newCeiling -> money(about.ceiling, about.unit)))(
      buttons*)).flatMap {
      case 1 => open(about)
      case 2 => addThisDevice
      case 3 => setUp(about)
      case _ => pure(())
    }
  }

  private def opening(about: About, of: String): Signed ! R = Device.ready[R].flatMap(pub => Clock.now[R].flatMap(now =>
    Device.signed[R](Record("", 1, "", now, Body.Open(about.key, of, pub, about.unit)))))

  /** AN ACCOUNT ASKED FOR: the community's rules read and accepted first (docs/legal.md: they are the contract
   * among its members) — the acceptance goes to the node as the hash of the text that was shown */
  private def open(about: About): Unit ! R = Node.rules[R].flatMap { (text, hash) =>
    if text.isEmpty then openWith(about, hash)
    else show(Screen[Boolean](w.rulesOf(about.name))(text.linesIterator.filter(_.trim.nonEmpty).map(Part.text)
      .toSeq*)(Button(w.accept, Style.Primary)(true), Button(w.back)(false))).flatMap(yes =>
      if yes then openWith(about, hash) else pure(()))
  }

  private def openWith(about: About, rules: String): Unit ! R =
    // a community of businesses only offers no personal account
    val kinds = if about.only == "business" then List(w.business -> "business")
      else List(w.personal -> "personal", w.business -> "business")
    edit[(String, String)](w.openAccount, Form(said(Field.text(w.yourName).hinted(w.nameHint)),
      said(Field.choice(w.accountKind, kinds*).initially(kinds.head._2)))).flatMap {
      case None => pure(())
      case Some((name, of)) => opening(about, of).flatMap(signed => Node.open[R](signed, name, rules).flatMap {
          // an account with no credit line (a private person's, where people earn first) is told how it starts
          case Right(Some(s)) => remember(s.id, name).andThen(inform(w.opened,
              if s.floor == 0 then w.openedEarning else w.openedText(money(-s.floor, about.unit))))
          // where joining is not open the record waits: the account's id will be its hash
          case Right(None) => keepHere("pending", Json.print(Json.obj("id" -> Json.str(signed.hash),
              "name" -> Json.str(name)))).andThen(inform(w.asked, w.waitingText(about.name)))
          case Left(r) => inform(w.notOpened, refusal(r, about.unit))
        })
    }

  private def setUp(about: About): Unit ! R =
    edit[(String, String)](w.setUpCommunity, Form(said(Field.text(w.communityName)),
      said(Field.secret(w.setupToken))), Part.text(w.setupText)).flatMap {
      case None => pure(())
      case Some((name, token)) => opening(about, "community").flatMap(Node.setup[R](_, name, token)).flatMap {
          case Right(s) => remember(s.id, name).andThen(inform(w.setUpDone, w.setUpDoneText))
          case Left(r) => inform(w.notSetUp, refusal(r, about.unit))
        }
    }

  /** this device's key shown, to be signed in from a device that holds the account already */
  private def addThisDevice: Unit ! R = Device.ready[R].flatMap { pub =>
    val form = Form.one(said(Field.text(w.accountId).hinted(w.accountIdHint)))
    show(Screen[Option[String]](w.addThisDeviceTitle)(Part.text(w.addDeviceHow), Part.qr(pub, w.thisDeviceKey),
      Part.code(pub), form)(Button.submit(w.itIsAdded, form)(Some(_)), Button(w.back)(None))).flatMap {
      case None => pure(())
      case Some(id) => Node.account[R](id.trim).flatMap {
          case Some(s) if s.grant(pub).isDefined => Node.members[R].flatMap(ms =>
              remember(s.id, ms.find(_.id == s.id).fold(w.anAccount)(_.name)).andThen(inform(w.holds, w.holdsText)))
          case Some(_) => inform(w.notYet, w.notYetText)
          case None => inform(w.notFound, w.notFoundText)
        }
    }
  }

  // ---- an account's home

  private def home(about: About, id: String, name: String): Unit ! R = Node.account[R](id).flatMap {
    case None => confirm(w.notAtNode, name).flatMap(yes => if yes then forget(id) else pure(()))
    case Some(s) => Device.ready[R].flatMap(key => Node.community[R].flatMap { community =>
        community.fold(pure[Boolean, R](false))(c => Node.account[R](c).map(_.exists(_.grant(key).exists(_.manage))))
          .flatMap { governs =>
            val buttons = List(Button[Int](w.pay, Style.Primary)(1), Button[Int](w.history)(2),
              Button[Int](w.vouch)(3), Button[Int](w.devices)(4), Button[Int](w.checkNode)(5)) ++
              (if governs then List(Button[Int](w.community)(6)) else Nil) ++
              List(Button[Int](w.invite)(8), Button[Int](w.accounts)(7))
            show(Screen[Int](name)(Part.details(w.balance -> Cell.Money(units(s.balance), about.unit),
              w.canSpend -> Cell.Money(units(s.balance - s.floor), about.unit),
              w.canReceive -> Cell.Money(units(s.ceiling - s.balance), about.unit),
              w.community -> Cell.Text(about.name)))(buttons*)).flatMap {
              case 1 => pay(about, s)
              case 2 => history(about, s)
              case 3 => vouch(about, s)
              case 4 => devices(about, s, key)
              case 5 => check(about, s.id)
              case 6 => office(about, key)
              case 8 => Node.invite[R].flatMap(link => show(Screen[Unit](w.invite)(Part.text(w.inviteText),
                  Part.qr(link, w.invite), Part.code(link))(Button(w.back)(()))))
              case _ => accounts(about)
            }
          }
      })
  }

  /** a holder's record: the chain's next as the account is now, signed here, given to the node */
  private def say(s: State, body: Long => Body, memo: String = ""): Either[Refusal, State] ! R =
    Clock.now[R].flatMap(now => Device.signed[R](Record(s.id, s.seq + 1, s.head, now.max(s.at), body(now)))
      .flatMap(Node.append[R](s.id, _, memo)))

  /** the other members here; another community's account is none of them — its members are paid through it */
  private def others(s: State): Vector[Member] ! R =
    Node.members[R].map(_.filter(m => m.id != s.id && m.of != "line"))

  /** A PAYMENT: to a member of this community, or — where there are trust lines — of another */
  private def pay(about: About, s: State): Unit ! R = others(s).flatMap(ms => Node.lines[R].flatMap { lines =>
    if ms.isEmpty && lines.isEmpty then inform(w.nobodyToPay, w.firstMember)
    else if lines.isEmpty then paying(about, s, ms, None)
    else if ms.isEmpty then elsewhere(about, s, lines)
    else show(Screen[Boolean](w.pay)()(Button(about.name, Style.Primary)(false), Button(w.payElsewhere)(true)))
      .flatMap(far => if far then elsewhere(about, s, lines) else paying(about, s, ms, None))
  })

  /** to another community: which (when there are several), then its members, asked of its own node */
  private def elsewhere(about: About, s: State, lines: Vector[TrustLine]): Unit ! R =
    val table = Table(lines.toList)(Column.text(w.community)(_.name)).keyedBy(_.node)
    (if lines.size == 1 then pure[Option[TrustLine], R](lines.headOption)
    else show(Screen[Option[TrustLine]](w.whichCommunity)(table)(Button.select(w.use, table)(Some(_)),
      Button(w.back)(None)))).flatMap {
      case None => pure(())
      case Some(line) => Node.membersAt[R](line.address).map(_.filter(m => m.of != "line" && m.of != "community"))
          .flatMap(there => if there.isEmpty then inform(w.payElsewhere, w.nobodyThere)
            else paying(about, s, there, Some(line)))
    }

  private def paying(about: About, s: State, ms: Vector[Member], line: Option[TrustLine]): Unit ! R =
    edit[(String, BigDecimal, Option[String])](line.fold(w.pay)(l => s"${w.pay} · ${l.name}"), Form(
      said(Field.choice(w.to, ms.map(m => m.name -> m.id)*)),
      said(Field.money(w.amount, about.unit).check(w.moreThanZero)(_ > 0)),
      said(Field.text(w.forWhat).optional))).flatMap {
      case None => pure(())
      case Some((to, amount, what)) =>
        val who = ms.find(_.id == to).fold(to)(_.name) + line.fold("")(l => s" · ${l.name}")
        val memo = what.getOrElse("").trim
        confirm(w.payQuestion(money(cents(amount), about.unit), who), w.pay).flatMap { yes =>
          if !yes then pure(())
          // the payment's name: one a payment, so sent again — a second press, a lost answer — it is paid once;
          // a member of another community is named with its node's key: `<account>@<key>`
          else say(s, now => Body.Send(line.fold(to)(l => s"$to@${l.node}"), cents(amount),
            if memo.isEmpty then "" else Sha256.hex(memo), Sha256.hex(s"${s.id}:${s.seq}:$now").take(24)), memo)
            .flatMap {
            case Right(after) if after.balance == s.balance => inform(w.cameBack,
                line.fold(w.cameBackText(who))(l => w.cameBackElsewhere(l.name)))
            case Right(after) => inform(w.paid, w.paidText(money(cents(amount), about.unit), who,
                money(after.balance, about.unit)))
            case Left(r) => inform(w.notPaid, refusal(r, about.unit))
          }
        }
    }

  private def vouch(about: About, s: State): Unit ! R = others(s).flatMap { ms =>
    if ms.isEmpty then inform(w.nobodyToVouch, w.firstMember)
    else edit[(String, BigDecimal)](w.vouch, Form(said(Field.choice(w.vouchFor, ms.map(m => m.name -> m.id)*)),
      said(Field.money(w.partOfLine, about.unit).check(w.moreThanZero)(_ > 0))), Part.text(w.vouchText)).flatMap {
      case None => pure(())
      case Some((to, amount)) => say(s, _ => Body.Vouch(to, cents(amount))).flatMap {
          case Right(after) if after.lent == s.lent => inform(w.cameBack, w.hasVoucher)
          case Right(_) => inform(w.vouched, w.vouchedText(money(cents(amount), about.unit)))
          case Left(r) => inform(w.notVouched, refusal(r, about.unit))
        }
    }
  }

  private final case class Row(seq: Long, day: String, what: String, who: String, amount: BigDecimal, memo: String)

  private def history(about: About, s: State): Unit ! R = Node.members[R].flatMap(ms => Node.lines[R].flatMap(peers =>
    Node.chain[R](s.id).flatMap { lines =>
      // a member of another community (`<account>@<its node's key>`) is shown by its community: the line's name
      def name(id: String): String = ms.find(_.id == id).fold(
        if id.contains("@") then peers.find(_.node == id.dropWhile(_ != '@').drop(1)).fold(short(id))(_.name)
        else short(id))(_.name)
      def m(cents: Long): String = money(cents, about.unit)
      val rows = lines.map { l =>
        val (what, who, amount) = l.record.body match
          case _: Body.Open => (w.accountOpened, "", 0L)
          case Body.Send(to, a, _, _) => (w.paidKind, name(to), -a)
          case Body.Receive(from, _, a) => (w.received, name(from), a)
          case Body.Bounce(_, a) => (w.cameBackKind, "", a)
          case Body.Limits(credit, ceiling) => (w.limitsKind(m(credit), m(ceiling)), about.name, 0L)
          case Body.Granted(g) => (w.deviceAdded(short(g.key)), "", 0L)
          case Body.Revoke(k) => (w.deviceRemoved(short(k)), "", 0L)
          case Body.Vouch(to, a) => (w.lineLent(m(a)), name(to), 0L)
          case Body.Vouched(from, _, a) => (w.lineReceived(m(a)), name(from), 0L)
          case Body.Release(a) => (w.lineGivenBack(m(a)), "", 0L)
          case Body.Released(_, a) => (w.lineReturned(m(a)), "", 0L)
        Row(l.record.seq, day(l.record.at), what, who, units(amount), l.memo)
      }.reverse.toList
      // the deals as a statement for the books (docs/legal.md: a deal in units is a sale, booked as any other)
      def field(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
      val csv = (List(w.day, w.what, w.withWhom, s"${w.amount} (${about.unit})", w.forColumn) +: rows.reverse
        .filter(_.amount != 0).map(r => List(r.day, r.what, r.who, r.amount.toString, r.memo)))
        .map(_.map(field).mkString(",")).mkString("\n")
      show(Screen[Boolean](w.history)(Table(rows)(Column.text(w.day)(_.day), Column.text(w.what)(_.what),
        Column.text(w.withWhom)(_.who), Column.money(w.amount, about.unit)(_.amount),
        Column.text(w.forColumn)(_.memo)).keyedBy(_.seq.toString).whenEmpty(w.nothingYet))(
        Button(w.copyStatement)(true), Button(w.back)(false))).flatMap(asked =>
        if !asked then pure(()) else copy[R](csv).flatMap(done =>
          if done then inform(w.statementCopied, w.statementCopiedText) else inform(w.copyStatement, w.notCopied)))
  }))

  // ---- devices: who may act for the account

  private def devices(about: About, s: State, mine: String): Unit ! R =
    def may(g: Grant) = (g.spend, g.manage) match
      case (true, true) => w.spendAndManage
      case (true, false) => w.spend
      case (false, true) => w.manage
      case _ => w.nothing
    def limit(g: Grant) = List(Option.when(g.payment > 0)(w.aPayment(money(g.payment, about.unit))),
      Option.when(g.period > 0)(w.inDays(money(g.period, about.unit), g.per / 86400000L))).flatten match
      case Nil => w.accountsOwn
      case some => some.mkString(", ")
    val table = Table(s.grants.toList)(Column.text(w.key)(g => short(g.key) + (if g.key == mine then
      " " + w.thisDevice else "")), Column.text(w.may)(may), Column.text(w.atMost)(limit)).keyedBy(_.key)
    show(Screen[Option[Option[Grant]]](w.devices)(Part.text(w.devicesText), Part.details(w.accountsId -> s.id),
      table)(Button(w.addDevice, Style.Primary)(Some(None)),
      Button.select(w.takeOff, table, Style.Danger)(g => Some(Some(g))), Button(w.back)(None))).flatMap {
      case None => pure(())
      case Some(None) => edit[(String, Boolean, Option[BigDecimal], Option[BigDecimal])](w.addDevice, Form(
          said(Field.text(w.newKey).hinted(w.newKeyHint)), said(Field.flag(w.mayManage)),
          said(Field.money(w.atMostPayment, about.unit).optional),
          said(Field.money(w.atMostWeek, about.unit).optional))).flatMap {
          case None => pure(())
          case Some((key, manage, payment, weekly)) =>
            val period = weekly.fold(0L)(cents)
            say(s, _ => Body.Granted(Grant(key.trim, spend = true, manage, payment.fold(0L)(cents), period,
              if period > 0 then week else 0L))).flatMap {
              case Right(_) => inform(w.added, w.addedText)
              case Left(r) => inform(w.notAdded, refusal(r, about.unit))
            }
        }
      case Some(Some(g)) => confirm(w.takeOffQuestion(short(g.key)), w.takeOff).flatMap(yes =>
          if !yes then pure(()) else say(s, _ => Body.Revoke(g.key)).flatMap {
            case Right(_) => inform(w.takenOff, w.takenOffText)
            case Left(r) => inform(w.notTakenOff, refusal(r, about.unit))
          })
    }

  // ---- the node, checked

  private def check(about: About, id: String): Unit ! R =
    working[R](w.checking)(Part.text(w.checkingText)).andThen(Node.chain[R](id)).flatMap(lines =>
      Check.chain[R](lines.map(_.signed)).flatMap(folded =>
      Node.account[R](id).flatMap(stated => Node.proof[R](id).flatMap { proof =>
        proof.fold(pure[Boolean, R](false))(p => Signer.verify[R](about.key, p.head.text,
          p.head.sigs.find(_._1 == about.key).fold("")(_._2))).flatMap { signed =>
          val kept = s"head:${about.key}"
          setting[R](kept).flatMap { before =>
            val (number, root) = before.map(_.split(':')).collect { case Array(n, r) =>
              (n.toLongOption.getOrElse(0L), r) }.getOrElse((-1L, ""))
            val chain = folded match
              case Right(s) if s.node != about.key => Part.problem(w.anotherNode)
              case Right(s) if !stated.exists(n => n.head == s.head && n.balance == s.balance) =>
                Part.problem(w.notWhatRecordsMake)
              case Right(s) => Part.info(w.recordsHold(s.seq, money(s.balance, about.unit)))
              case Left((at, fault)) => Part.problem(w.recordFails(at + 1, fault.toString))
            val head = (proof, folded) match
              case (None, _) => Part.problem(w.noProof)
              case (Some(_), _) if !signed => Part.problem(w.headNotSigned)
              case (Some(p), Right(s)) if p.accountHead != s.head || !Merkle.holds(p.root, id, p.accountHead,
                p.steps) => Part.problem(w.notUnderHead)
              case (Some(p), _) if p.number < number => Part.problem(w.headWentBack(p.number, number))
              case (Some(p), _) if p.number == number && p.root != root => Part.problem(w.twoHeads(p.number))
              case (Some(p), _) => Part.info(w.underHead(p.number))
            val remembered = (proof, head) match
              case (Some(p), Part.Notice(Level.Info, _)) => keep[R](kept, s"${p.number}:${p.root}")
              case _ => pure[Unit, R](())
            remembered.andThen(show(Screen[Unit](w.checked)(chain, head, Part.text(w.didSums))(Button(w.back)(()))))
          }
        }
      })))

  // ---- the community's office: for a device that may manage the community's own account

  /** a command of the community: its text signed by this device, given to the node */
  private def command(key: String, cmd: String, fields: (String, Json)*): Either[Refusal, Unit] ! R =
    Clock.now[R].flatMap { now =>
      val text = Json.print(Json.Obj(Vector("cmd" -> Json.str(cmd), "at" -> Json.num(now)) ++ fields))
      Device.sign[R](text).flatMap(sig => Node.command[R](text, key, sig))
    }

  /** who asks to join: admitted with the limits the community gives, or rejected */
  private def asking(about: About, key: String): Unit ! R = Node.requests[R].flatMap { asked =>
    val table = Table(asked.toList)(Column.text(w.yourName)(_.name), Column.text(w.accountKind)(a => w.kind(a.of)))
      .keyedBy(_.id).whenEmpty(w.noRequests)
    show(Screen[Option[(Asked, Boolean)]](w.requestsTitle)(table)(Button.select(w.admit, table)(a => Some(a -> true)),
      Button.select(w.reject, table, Style.Danger)(a => Some(a -> false)), Button(w.back)(None))).flatMap {
      case None => pure(())
      case Some((a, false)) => command(key, "reject", "id" -> Json.str(a.id)).flatMap {
          case Right(_) => inform(w.rejected, a.name)
          case Left(r) => inform(w.notAdmitted, refusal(r, about.unit))
        }.andThen(defer(asking(about, key)))
      case Some((a, true)) =>
        // a private person where people earn first: no credit line to give, and the form says so
        val earning = about.only == "earned" && a.of == "personal"
        edit[(BigDecimal, BigDecimal)](w.admitOf(a.name), Form(
          said(Field.money(w.creditLine, about.unit).initially(if earning then BigDecimal(0) else units(about.credit))
            .check(w.notBelowZero)(_ >= 0)),
          said(Field.money(w.ceiling, about.unit).initially(units(about.ceiling)).check(w.notBelowZero)(_ >= 0))),
          (if earning then Seq(Part.info(w.noCreditHere)) else Nil)*).flatMap {
          case None => pure(())
          case Some((credit, ceiling)) => command(key, "admit", "id" -> Json.str(a.id),
              "credit" -> Json.num(cents(credit)), "ceiling" -> Json.num(cents(ceiling))).flatMap {
              case Right(_) => inform(w.admitted, w.admittedText(a.name))
              case Left(r) => inform(w.notAdmitted, refusal(r, about.unit))
            }
        }.andThen(defer(asking(about, key)))
    }
  }

  /** THE COMMUNITY'S TRUST LINES: how far each other community may owe this one, and where each stands */
  private def lines(about: About, key: String): Unit ! R = Node.lines[R].flatMap { ls =>
    ls.foldLeft(pure[Vector[(TrustLine, State)], R](Vector.empty))((acc, l) => acc.flatMap(all =>
      Node.account[R](l.id).map(_.fold(all)(s => all :+ (l -> s))))).flatMap { all =>
      val table = Table(all.toList)(Column.text(w.community)(_._1.name),
        Column.money(w.theyMayOwe, about.unit)(x => units(x._2.credit)),
        Column.money(w.balance, about.unit)(x => units(x._2.balance))).keyedBy(_._1.node).whenEmpty(w.noLines)
      show(Screen[Option[Option[TrustLine]]](w.trustLines)(Part.text(w.linesText), table)(
        Button(w.openLine, Style.Primary)(Some(None)), Button.select(w.changeLine, table)(x => Some(Some(x._1))),
        Button(w.back)(None))).flatMap {
        case None => pure(())
        case Some(chosen) =>
          val address = said(Field.text(w.lineAddress).hinted(w.lineAddressHint))
          edit[(String, BigDecimal)](chosen.fold(w.openLine)(l => s"${w.trustLines} · ${l.name}"), Form(
            chosen.fold(address)(l => address.initially(l.address)),
            said(Field.money(w.theyMayOwe, about.unit).check(w.notBelowZero)(_ >= 0)))).flatMap {
            case None => pure(())
            case Some((to, credit)) => command(key, "line", "address" -> Json.str(to.trim.stripSuffix("/")),
                "credit" -> Json.num(cents(credit))).flatMap {
                case Right(_) => Node.lines[R].flatMap(now => inform(w.lineOpened, w.lineOpenedText(
                    now.find(l => l.address == to.trim.stripSuffix("/")).fold(to)(_.name),
                    money(cents(credit), about.unit))))
                case Left(r) => inform(w.lineNotOpened, r.why match
                    case "unit" => w.lineUnit
                    case "no such node" | "command" | "peer" => w.noSuchNode
                    case "same" => w.lineSame
                    case _ => refusal(r, about.unit))
              }
          }.andThen(defer(lines(about, key)))
      }
    }
  }

  private def office(about: About, key: String): Unit ! R = Node.requests[R].flatMap(asked =>
    Node.members[R].flatMap { ms =>
    ms.foldLeft(pure[Vector[(Member, State)], R](Vector.empty))((acc, m) => acc.flatMap(all =>
      Node.account[R](m.id).map(_.fold(all)(s => all :+ (m -> s))))).flatMap { all =>
      val table = Table(all.toList)(Column.text(w.member)(_._1.name), Column.text(w.accountKind)(x => w.kind(x._1.of)),
        Column.money(w.balance, about.unit)(x => units(x._2.balance)),
        Column.money(w.creditLine, about.unit)(x => units(x._2.credit)),
        Column.money(w.ceiling, about.unit)(x => units(x._2.ceiling))).keyedBy(_._1.id).whenEmpty(w.noMembers)
      show(Screen[Option[Either[Int, (Member, State)]]](s"${w.community} · ${about.name}")(Part.details(
        w.members -> Cell.Number(BigDecimal(all.size)),
        w.sum -> Cell.Money(units(all.map(_._2.balance).sum), about.unit)), Part.text(w.sumText), table)(
        Button.select(w.setLimits, table)(x => Some(Right(x))),
        Button(w.requestsButton(asked.size))(Some(Left(1))), Button(w.trustLines)(Some(Left(2))),
        Button(w.rulesButton)(Some(Left(3))), Button(w.back)(None))).flatMap {
        case None => pure(())
        case Some(Left(1)) => asking(about, key).andThen(defer(office(about, key)))
        case Some(Left(2)) => lines(about, key).andThen(defer(office(about, key)))
        // the community's rules: from then on the ones a newcomer accepts
        case Some(Left(_)) => Node.rules[R].flatMap((text, _) => edit[Option[String]](w.rulesOf(about.name),
            Form.one(said(Field.longText(w.rulesLabel).hinted(w.rulesHint).initially(text).optional))).flatMap {
            case None => pure(())
            case Some(next) => command(key, "rules", "text" -> Json.str(next.getOrElse(""))).flatMap {
                case Right(_) => inform(w.rulesSaved, about.name)
                case Left(r) => inform(w.notSet, refusal(r, about.unit))
              }
          }).andThen(defer(office(about, key)))
        case Some(Right((m, s))) => edit[(BigDecimal, BigDecimal)](w.limitsOf(m.name), Form(
            said(Field.money(w.creditLine, about.unit).initially(units(s.credit)).check(w.notBelowZero)(_ >= 0)),
            said(Field.money(w.ceiling, about.unit).initially(units(s.ceiling)).check(w.notBelowZero)(_ >= 0))))
            .flatMap {
            case None => pure(())
            case Some((credit, ceiling)) => command(key, "limits", "account" -> Json.str(m.id),
                "credit" -> Json.num(cents(credit)), "ceiling" -> Json.num(cents(ceiling))).flatMap {
                case Right(_) => inform(w.limitsSet, w.limitsSetText(m.name, money(cents(credit), about.unit),
                    money(cents(ceiling), about.unit)))
                case Left(r) => inform(w.notSet, refusal(r, about.unit))
              }
          }.andThen(defer(office(about, key)))
      }
    }
  })

  // ---- the accounts this device holds

  private def accounts(about: About): Unit ! R = held.flatMap { hs =>
    val table = Table(hs.toList)(Column.text(w.anAccount)(_._2), Column.text(w.id)(h => short(h._1))).keyedBy(_._1)
    show(Screen[Option[Option[String]]](w.accounts)(Part.text(w.accountsText), table)(
      Button.select(w.use, table)(h => Some(Some(h._1))), Button(w.openOrAdd)(Some(None)),
      Button(w.anotherCommunity)(Some(Some(""))), Button(w.back)(None))).flatMap {
      // another community: no node in use — the wallet asks which (what it holds at this one is kept)
      case Some(Some("")) => keep[R]("node", "")
      case Some(Some(id)) => keepHere("current", id)
      case Some(None) => welcome(about, first = false)
      case None => pure(())
    }
  }
