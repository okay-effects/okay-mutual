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
    case ("unsigned", "second") => w.needsSecond
    case ("member", _) => w.sharedNeedsOwn
    case ("unsigned", _) => w.unsigned
    case ("forbidden", "manage") => w.onlyManage
    case ("forbidden", "last") => w.lastManager
    case ("forbidden", "held") | ("held", _) => w.heldAlready
    case ("forbidden", "credit") => w.noCreditHere
    case ("place", _) => w.changedMeanwhile
    case ("named", _) => w.namedAlready
    case ("admitted", _) => w.admitsItself
    case ("rules", _) => w.rulesChanged
    case ("business", _) => w.onlyBusinesses
    case ("shared", _) => w.sharedOff
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
            case None => recovering.flatMap {
                case Some((id, name)) => backing(about, id, name)
                case None => welcome(about, first = true)
              }
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
  private def create: Unit ! R = Node.bounds[R].flatMap(creating)

  /** the ways a community may have with private people, of those its hub allows */
  private def ways(allowed: Set[String]): List[(String, String)] = List(w.peopleEarn -> "earned",
    w.businessesOnly -> "business", w.peopleVouched -> "vouched", w.peopleRecord -> "record", w.peopleCredit -> "")
    .filter(way => allowed(way._2))

  private def creating(hub: Bounds): Unit ! R =
    val most = if hub.maxCredit > 0 then units(hub.maxCredit).min(BigDecimal(100)) else BigDecimal(100)
    edit[(String, String, String, Boolean, String, BigDecimal, BigDecimal, String)](w.createCommunity, Form(
      said(Field.text(w.communityName)), said(Field.text(w.place).hinted(w.placeHint)),
      said(Field.text(w.unitLabel).initially("PLN")),
      said(Field.choice(w.howJoin, w.byAdmission -> false, w.joinOpen -> true).initially(false)),
      // private people take part, and where a community starts they earn before they spend (docs/legal.md: no
      // credit is given to a consumer, so the rules on consumer credit do not arise)
      said(Field.choice(w.whoMayJoin, ways(hub.personal)*).initially(ways(hub.personal).headOption.fold("")(_._2))),
      said(Field.decimal(w.newcomersLine).initially(most).check(w.notBelowZero)(_ >= 0)),
      said(Field.decimal(w.newcomersCeiling).initially(BigDecimal(300)).check(w.notBelowZero)(_ >= 0)),
      said(Field.longText(w.rulesLabel).hinted(w.rulesHint).initially(w.standardRules))),
      Part.text(w.createText)).flatMap {
      case None => pure(())
      case Some((name, place, unit, open, only, line, ceiling, rules)) =>
        // where the hub bounds what a person may owe, a community starts at that bound; its office sets its own
        val owing = if Set("vouched", "record", "")(only) then hub.maxPersonal else 0L
        Node.make[R](place.trim.toLowerCase, name, unit.trim, open, cents(line), cents(ceiling), only, rules.trim,
          if owing > 0 then cents(line).min(owing) else cents(line), owing).flatMap {
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
      List(Button[Int](w.lostDevices)(4)) ++ (if first then Nil else List(Button[Int](w.back)(0)))
    show(Screen[Int]("Okay Mutual")(Part.text(w.intro(about.unit)),
      Part.details(w.community -> about.name, w.joining -> (if about.open then w.joinOpen else w.joinAdmitted),
        w.newCredit -> money(about.credit, about.unit), w.newCeiling -> money(about.ceiling, about.unit)))(
      buttons*)).flatMap {
      case 1 => open(about)
      case 2 => addThisDevice
      case 3 => setUp(about)
      case 4 => lost(about)
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
          .flatMap(g => Clock.now[R].map(g -> _)).flatMap { (governs, now) =>
            // an account a family or a team shares: its holders are accounts; it stands on no board, invites nobody
            val shared = Wallet.shared(s.of)
            val waits = s.proposed.count(_.at > now - Chain.life)
            val buttons = List(Button[Int](w.pay, Style.Primary)(1)) ++
              (if waits > 0 then List(Button[Int](w.toConfirm(waits))(11)) else Nil) ++
              List(Button[Int](w.history)(2)) ++ (if shared then Nil else List(Button[Int](w.vouch)(3))) ++
              List(Button[Int](if shared then w.holders else w.devices)(4), Button[Int](w.checkNode)(5)) ++
              (if governs then List(Button[Int](w.community)(6)) else Nil) ++
              (if shared then Nil else List(Button[Int](w.board)(10), Button[Int](w.invite)(8))) ++
              List(Button[Int](w.accounts)(7)) ++
              // a recovery announced for another device: this one, which still holds the account, can say no
              s.recovering.toList.map(_ => Button[Int](w.cancelRecovery, Style.Danger)(9))
            val warning = s.recovering.toList.map((k, after) => Part.warning(w.recoveryWarning(short(k), day(after))))
            show(Screen[Int](name)((warning :+ Part.details(w.balance -> Cell.Money(units(s.balance), about.unit),
              w.canSpend -> Cell.Money(units(s.balance - s.floor), about.unit),
              w.canReceive -> Cell.Money(units(s.ceiling - s.balance), about.unit),
              w.community -> Cell.Text(about.name)))*)(buttons*)).flatMap {
              case 10 => board(about, s, key)
              case 9 => s.recovering.fold(pure[Unit, R](()))((k, _) => say(s, _ => Body.Revoke(k)).flatMap {
                  case Right(_) => inform(w.cancelRecovery, w.recoveryCancelled)
                  case Left(r) => inform(w.cancelRecovery, refusal(r, about.unit))
                })
              case 1 => pay(about, s)
              case 2 => history(about, s)
              case 3 => vouch(about, s)
              case 4 => me(s).flatMap(devices(about, s, _))
              case 11 => me(s).flatMap(confirming(about, s, _))
              case 5 => check(about, s.id)
              case 6 => office(about, key)
              case 8 => Node.invite[R].flatMap(link => show(Screen[Unit](w.invite)(Part.text(w.inviteText),
                  Part.qr(link, w.invite), Part.code(link))(Button(w.back)(()))))
              case _ => accounts(about)
            }
          }
      })
  }

  /** a holder's record: the chain's next AS THE ACCOUNT IS NOW — asked of the node at this moment, not as it was
   * when the screen was drawn: a payment received while the app was open has moved the chain on —, signed here,
   * given to the node. Answered: the account just before the record, and after it */
  private def say(s: State, body: Long => Body, memo: String = ""): Either[Refusal, (State, State)] ! R =
    Node.account[R](s.id).flatMap { fresh =>
      val now0 = fresh.getOrElse(s)
      Clock.now[R].flatMap(now => Device.signed[R](Record(now0.id, now0.seq + 1, now0.head, now.max(now0.at),
        body(now))).flatMap(acted(now0, _)).flatMap(Node.append[R](s.id, _, memo))).map(_.map(now0 -> _))
    }

  /** WHO THIS DEVICE IS TO AN ACCOUNT: its own key where the key holds it; else the account of this device — one it
   * holds here — that is the account's holder (a family's, a team's: held by its members' accounts) */
  private def me(s: State): String ! R = Device.ready[R].flatMap(pub =>
    if s.grant(pub).isDefined then pure(pub)
    else held.map(_.map(_._1).find(h => s.grant(h).isDefined).getOrElse(pub)))

  /** a record signed by this device, said to be signed FOR the account it acts through, when it does */
  private def acted(s: State, signed: Signed): Signed ! R = Device.ready[R].flatMap(pub => me(s).map(who =>
    if who == pub then signed else signed.copy(acts = Vector(Acting(pub, who, 0)))))

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

  /** the payment's form — filled in beforehand when it pays for an offer of the board (`preset`: whom, how much,
   * what for), every field still the payer's to change */
  private def paying(about: About, s: State, ms: Vector[Member], line: Option[TrustLine],
                     preset: Option[(String, BigDecimal, String)] = None): Unit ! R =
    val to = Field.choice(w.to, ms.map(m => m.name -> m.id)*)
    val amount = Field.money(w.amount, about.unit)
    edit[(String, BigDecimal, Option[String])](line.fold(w.pay)(l => s"${w.pay} · ${l.name}"), Form(
      said(preset.filter(p => ms.exists(_.id == p._1)).fold(to)(p => to.initially(p._1))),
      said(preset.filter(_._2 > 0).fold(amount)(p => amount.initially(p._2)).check(w.moreThanZero)(_ > 0)),
      said(preset.fold(Field.text(w.forWhat))(p => Field.text(w.forWhat).initially(p._3)).optional))).flatMap {
      case None => pure(())
      case Some((to, amount, what)) =>
        val who = ms.find(_.id == to).fold(to)(_.name) + line.fold("")(l => s" · ${l.name}")
        val memo = what.getOrElse("").trim
        confirm(w.payQuestion(money(cents(amount), about.unit), who), w.pay).flatMap { yes =>
          if !yes then pure(())
          // the payment's name: one a payment, so sent again — a second press, a lost answer — it is paid once;
          // a member of another community is named with its node's key: `<account>@<key>`
          // above the account's threshold of two holders: proposed here, confirmed by another holder
          else if s.two > 0 && cents(amount) > s.two then say(s, now => Body.Propose(
            line.fold(to)(l => s"$to@${l.node}"), cents(amount), if memo.isEmpty then "" else Sha256.hex(memo),
            Sha256.hex(s"${s.id}:${s.seq}:$now").take(24)), memo).flatMap {
            case Right(_) => inform(w.proposed, w.proposedText(money(cents(amount), about.unit), who))
            case Left(r) => inform(w.notPaid, refusal(r, about.unit))
          }
          else say(s, now => Body.Send(line.fold(to)(l => s"$to@${l.node}"), cents(amount),
            if memo.isEmpty then "" else Sha256.hex(memo), Sha256.hex(s"${s.id}:${s.seq}:$now").take(24)), memo)
            .flatMap {
            case Right((was, after)) if after.balance == was.balance => inform(w.cameBack,
                line.fold(w.cameBackText(who))(l => w.cameBackElsewhere(l.name)))
            case Right((_, after)) => inform(w.paid, w.paidText(money(cents(amount), about.unit), who,
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
          case Right((was, after)) if after.lent == was.lent => inform(w.cameBack, w.hasVoucher)
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
          // a holder that is a member's account is said by its name; a device, by its key
          case Body.Granted(g) => (ms.find(_.id == g.key).fold(w.deviceAdded(short(g.key)))(m =>
              w.holderAdded(m.name)), "", 0L)
          case Body.Revoke(k) => (ms.find(_.id == k).fold(w.deviceRemoved(short(k)))(m => w.holderRemoved(m.name)),
              "", 0L)
          case Body.Recover(k, _) => (w.recoveryAnnounced, short(k), 0L)
          case Body.Recovered(k) => (w.deviceAdded(short(k)), "", 0L)
          case Body.Two(above) => (w.twoKind(if above > 0 then m(above) else ""), "", 0L)
          case Body.Propose(to, a, _, _) => (w.proposedKind(m(a)), name(to), 0L)
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

  /** THE HOLDERS OF AN ACCOUNT: a person's are devices' keys; a shared account's (a family's, a team's) are its
   * members' accounts. Added with what each may and how much, taken off, and the threshold above which a payment
   * needs two of them */
  private def devices(about: About, s: State, mine: String): Unit ! R = Node.members[R].flatMap { ms =>
    val shared = Wallet.shared(s.of)
    def may(g: Grant) = (g.spend, g.manage) match
      case (true, true) => w.spendAndManage
      case (true, false) => w.spend
      case (false, true) => w.manage
      case _ => w.nothing
    def limit(g: Grant) = List(Option.when(g.payment > 0)(w.aPayment(money(g.payment, about.unit))),
      Option.when(g.period > 0)(w.inDays(money(g.period, about.unit), g.per / 86400000L))).flatten match
      case Nil => w.accountsOwn
      case some => some.mkString(", ")
    def who(key: String): String = ms.find(_.id == key).fold(short(key))(_.name)
    val table = Table(s.grants.toList)(Column.text(if shared then w.holderWho else w.key)(g => who(g.key) +
      (if g.key == mine then " " + (if shared then s"(${w.you})" else w.thisDevice) else "")),
      Column.text(w.may)(may), Column.text(w.atMost)(limit)).keyedBy(_.key)
    val two = Part.details(w.twoHolders -> (if s.two > 0 then w.twoKind(money(s.two, about.unit)) else w.twoKind("")))
    show(Screen[Option[Either[Int, Grant]]](if shared then w.holders else w.devices)(
      Part.text(if shared then w.holdersText else w.devicesText), Part.details(w.accountsId -> s.id), two, table)(
      Button(if shared then w.addHolder else w.addDevice, Style.Primary)(Some(Left(1))),
      Button.select(w.takeOff, table, Style.Danger)(g => Some(Right(g))), Button(w.twoHolders)(Some(Left(2))),
      Button(w.back)(None))).flatMap {
      case None => pure(())
      case Some(Left(1)) =>
        // a shared account's new holder is chosen among the members; a person's new device is named by its key
        val candidates = ms.filter(m => (m.of == "personal" || m.of == "business") && s.grant(m.id).isEmpty)
        val holder = if shared then Field.choice(w.holderWho, candidates.map(m => m.name -> m.id)*)
          else Field.text(w.newKey).hinted(w.newKeyHint)
        if shared && candidates.isEmpty then inform(w.addHolder, w.noMembers)
        else edit[(String, Boolean, Option[BigDecimal], Option[BigDecimal])](if shared then w.addHolder else
          w.addDevice, Form(said(holder), said(Field.flag(w.mayManage)),
          said(Field.money(w.atMostPayment, about.unit).optional),
          said(Field.money(w.atMostWeek, about.unit).optional))).flatMap {
          case None => pure(())
          case Some((key, manage, payment, weekly)) =>
            val period = weekly.fold(0L)(cents)
            say(s, _ => Body.Granted(Grant(key.trim, spend = true, manage, payment.fold(0L)(cents), period,
              if period > 0 then week else 0L))).flatMap {
              case Right(_) => inform(w.added, if shared then w.holderAdded(who(key.trim)) else w.addedText)
              case Left(r) => inform(w.notAdded, refusal(r, about.unit))
            }
        }
      case Some(Left(_)) =>
        val above = Field.money(w.twoAbove, about.unit).hinted(w.twoHint)
        edit[Option[BigDecimal]](w.twoHolders, Form.one(said((if s.two > 0 then above.initially(units(s.two))
          else above).optional))).flatMap {
          case None => pure(())
          case Some(amount) => say(s, _ => Body.Two(amount.fold(0L)(cents))).flatMap {
              case Right(_) => inform(w.twoSet, amount.filter(_ > 0).fold(w.twoOff)(a =>
                  w.twoSetText(money(cents(a), about.unit))))
              case Left(r) => inform(w.notSet, refusal(r, about.unit))
            }
        }
      case Some(Right(g)) => confirm(w.takeOffQuestion(who(g.key)), w.takeOff).flatMap(yes =>
          if !yes then pure(()) else say(s, _ => Body.Revoke(g.key)).flatMap {
            case Right(_) => inform(w.takenOff, if shared then w.holderRemoved(who(g.key)) else w.takenOffText)
            case Left(r) => inform(w.notTakenOff, refusal(r, about.unit))
          })
    }
  }

  /** THE PAYMENTS THAT WAIT FOR A SECOND HOLDER: confirmed by one who did not propose — its own `send` of the same
   * payment, with what the proposer said it is for */
  private def confirming(about: About, s: State, mine: String): Unit ! R = Clock.now[R].flatMap(now =>
    Node.members[R].flatMap { ms =>
      def name(id: String): String = if id == mine then w.you else
        ms.find(_.id == id.takeWhile(_ != '@')).fold(short(id))(_.name)
      val waits = s.proposed.filter(_.at > now - Chain.life).toList
      val table = Table(waits)(Column.text(w.to)(p => name(p.to)),
        Column.money(w.amount, about.unit)(p => units(p.amount)), Column.text(w.proposedBy)(p => name(p.by)),
        Column.text(w.day)(p => day(p.at))).keyedBy(_.hash).whenEmpty(w.nothingWaits)
      show(Screen[Option[Proposal]](w.toConfirmTitle)(Part.text(w.toConfirmText), table)(
        Button.select(w.confirmPayment, table, Style.Primary)(Some(_)), Button(w.back)(None))).flatMap {
        case None => pure(())
        case Some(p) if p.by == mine => inform(w.toConfirmTitle, w.needsAnother)
        case Some(p) => Node.chain[R](s.id).flatMap { lines =>
            val memo = lines.find(_.signed.hash == p.hash).fold("")(_.memo)
            say(s, _ => Body.Send(p.to, p.amount, p.memo, p.name), memo).flatMap {
              case Right((was, after)) if after.balance == was.balance => inform(w.cameBack, w.cameBackText(name(p.to)))
              case Right((_, after)) => inform(w.paid, w.paidText(money(p.amount, about.unit), name(p.to),
                  money(after.balance, about.unit)))
              case Left(r) => inform(w.notPaid, refusal(r, about.unit))
            }
          }
      }
    })

  // ---- the node, checked

  private def check(about: About, id: String): Unit ! R =
    working[R](w.checking)(Part.text(w.checkingText)).andThen(Node.chain[R](id)).flatMap(lines =>
      // a record signed for a holder that is an account is checked against that account's own chain
      Check.chain[R](lines.map(_.signed), a => Node.chain[R](a.account).flatMap(c =>
        Check.holds[R](c.map(_.signed), a))).flatMap(folded =>
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
        val earning = a.of == "personal" && Set("earned", "vouched", "record")(about.only)
        edit[(BigDecimal, BigDecimal)](w.admitOf(a.name), Form(
          said(Field.money(w.creditLine, about.unit).initially(if earning then BigDecimal(0) else
            units(if a.of == "personal" then about.personalCredit else about.credit))
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

  // ---- the board: what members offer and what they look for

  /** a member's own word on the board, signed by this device */
  private def post(key: String, cmd: String, account: String, fields: (String, Json)*): Either[Refusal, Unit] ! R =
    Clock.now[R].flatMap { now =>
      val text = Json.print(Json.Obj(Vector("cmd" -> Json.str(cmd), "at" -> Json.num(now),
        "account" -> Json.str(account)) ++ fields))
      Device.sign[R](text).flatMap(sig => Node.post[R](text, key, sig))
    }

  private def board(about: About, s: State, key: String): Unit ! R = Node.board[R].flatMap(entries =>
    Node.members[R].flatMap { ms =>
      def who(id: String): String = ms.find(_.id == id).fold(short(id))(_.name)
      def price(l: Listing): String = if l.price == 0 then w.byAgreement else money(l.price, about.unit)
      val table = Table(entries.toList)(Column.text(w.boardKind)(l => if l.kind == "offer" then w.offers else w.wants),
        Column.text(w.what)(_.title), Column.text(w.member)(l => who(l.account)), Column.text(w.price)(price),
        Column.text(w.inUnits)(l => s"${l.share}%")).keyedBy(_.id).whenEmpty(w.boardEmpty)
      show(Screen[Option[Either[String, Listing]]](w.board)(Part.text(w.boardText), table)(
        Button.select(w.openEntry, table)(l => Some(Right(l))), Button(w.addOffer, Style.Primary)(Some(Left("offer"))),
        Button(w.addWant)(Some(Left("want"))), Button(w.back)(None))).flatMap {
        case None => pure(())
        case Some(Left(kind)) => edit[(String, Option[String], Option[BigDecimal], Long)](
            if kind == "offer" then w.addOffer else w.addWant, Form(said(Field.text(w.what)),
            said(Field.longText(w.entryText).optional),
            said(Field.money(w.price, about.unit).hinted(w.priceHint).optional),
            said(Field.whole(w.inUnits).hinted(w.inUnitsHint).initially(100L).check(w.shareRange)(n =>
              n >= 0 && n <= 100)))).flatMap {
            case None => pure(())
            case Some((title, more, cost, share)) => post(key, "list", s.id, "kind" -> Json.str(kind),
                "title" -> Json.str(title), "text" -> Json.str(more.getOrElse("")),
                "price" -> Json.num(cost.fold(0L)(cents)), "share" -> Json.num(share)).flatMap {
                case Right(_) => inform(w.posted, title)
                case Left(r) => inform(w.notPosted, if r.why == "full" then w.boardFull else refusal(r, about.unit))
              }
          }.andThen(defer(board(about, s, key)))
        case Some(Right(l)) =>
          val mine = l.account == s.id
          val buttons = (if mine then List(Button[Int](w.takeOffEntry, Style.Danger)(2))
            else if l.kind == "offer" then List(Button[Int](w.pay, Style.Primary)(1)) else Nil) :+
            Button[Int](w.back)(0)
          show(Screen[Int](l.title)((Part.details(
            w.boardKind -> (if l.kind == "offer" then w.offers else w.wants), w.member -> who(l.account),
            w.price -> price(l), w.inUnits -> s"${l.share}%") +: l.text.linesIterator.filter(_.trim.nonEmpty)
            .map(Part.text).toSeq)*)(buttons*)).flatMap {
            // paying for an offer: its member, the part of its price taken in units, its title as what it is for
            case 1 => others(s).flatMap(members => paying(about, s, members, None,
                Some((l.account, units(l.price * l.share / 100), l.title))))
            case 2 => post(key, "unlist", s.id, "id" -> Json.str(l.id)).flatMap {
                case Right(_) => inform(w.takeOffEntry, l.title)
                case Left(r) => inform(w.notPosted, refusal(r, about.unit))
              }
            case _ => pure(())
          }.andThen(defer(board(about, s, key)))
      }
    })

  // ---- an account asked back: every device lost (specs/protocol.md "Keys")

  /** THIS DEVICE ASKS AN ACCOUNT BACK: its own new key, signed to show it holds it; the community is to confirm */
  private def lost(about: About): Unit ! R = Node.members[R].flatMap { ms =>
    val table = Table(ms.filter(m => m.of != "line" && !Wallet.shared(m.of)).toList)(Column.text(w.yourName)(_.name),
      Column.text(w.accountKind)(m => w.kind(m.of))).keyedBy(_.id).whenEmpty(w.noMembers)
    show(Screen[Option[Member]](w.lostDevices)(Part.text(w.lostText), table)(
      Button.select(w.itIsMine, table)(Some(_)), Button(w.back)(None))).flatMap {
      case None => pure(())
      case Some(m) => Device.ready[R].flatMap(pub => Device.sign[R](s"recover:${m.id}:$pub").flatMap(sig =>
          Node.askBack[R](m.id, pub, sig))).flatMap {
          case Right(_) => keepHere("recovering", Json.print(Json.obj("id" -> Json.str(m.id),
              "name" -> Json.str(m.name)))).andThen(inform(w.askedBack, w.askedBackText(about.name)))
          case Left(r) => inform(w.lostDevices, refusal(r, about.unit))
        }
    }
  }

  /** the account this device asked back, when it did */
  private def recovering: Option[(String, String)] ! R = here("recovering").map(_.flatMap(t =>
    Descent.parse(t).toOption).flatMap(j => scala.util.Try(j("id").string -> j("name").string).toOption))

  /** asked back, not yet held: waiting for the community, then for the week; taken up once the key holds it */
  private def backing(about: About, id: String, name: String): Unit ! R =
    Device.ready[R].flatMap(pub => Node.account[R](id).flatMap {
      case Some(s) if s.grant(pub).isDefined => remember(id, name).andThen(keepHere("recovering", ""))
      case Some(s) if s.recovering.exists(_._1 == pub) =>
        show(Screen[Int](w.askedBack)(Part.info(w.confirmedBack(day(s.recovering.get._2))))(
          Button(w.backNow, Style.Primary)(1), Button(w.forgetAsking)(2))).flatMap {
          case 1 => Node.recovered[R](id).flatMap(done => if done then pure(()) else inform(w.askedBack, w.notYetBack))
          case _ => keepHere("recovering", "")
        }
      case _ => show(Screen[Int](w.askedBack)(Part.text(w.askedBackText(about.name)))(
          Button(w.checkAgain, Style.Primary)(1), Button(w.forgetAsking)(2))).flatMap {
          case 2 => keepHere("recovering", "")
          case _ => pure(())
        }
    })

  /** who asks an account back: confirmed only by one sure it is the member — or dropped */
  private def losses(about: About, key: String): Unit ! R = Node.recoveries[R].flatMap { asked =>
    val table = Table(asked.toList)(Column.text(w.member)(_.name), Column.text(w.key)(l => short(l.key)))
      .keyedBy(_.account).whenEmpty(w.noRecoveries)
    show(Screen[Option[(Lost, Boolean)]](w.recoveriesTitle)(Part.warning(w.recoveriesText), table)(
      Button.select(w.confirmIdentity, table)(l => Some(l -> true)),
      Button.select(w.dropAsking, table, Style.Danger)(l => Some(l -> false)), Button(w.back)(None))).flatMap {
      case None => pure(())
      case Some((l, false)) => command(key, "forget", "account" -> Json.str(l.account)).as(())
          .andThen(defer(losses(about, key)))
      case Some((l, true)) => confirm(w.confirmQuestion(l.name), w.recoveriesTitle).flatMap { yes =>
          if !yes then pure(()) else command(key, "recover", "account" -> Json.str(l.account)).flatMap {
            case Right(_) => inform(w.recoveryAnnounced, w.recoveryAnnouncedText(l.name))
            case Left(r) => inform(w.notSet, refusal(r, about.unit))
          }
        }.andThen(defer(losses(about, key)))
    }
  }

  private def office(about: About, key: String): Unit ! R = Node.requests[R].flatMap(asked =>
    Node.recoveries[R].flatMap(lostOnes => Node.members[R].flatMap { ms =>
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
        Button(w.rulesButton)(Some(Left(3))), Button(w.policy)(Some(Left(5))),
        Button(w.recoveriesButton(lostOnes.size))(Some(Left(4))),
        Button(w.back)(None))).flatMap {
        case None => pure(())
        case Some(Left(1)) => asking(about, key).andThen(defer(office(about, key)))
        case Some(Left(4)) => losses(about, key).andThen(defer(office(about, key)))
        case Some(Left(2)) => lines(about, key).andThen(defer(office(about, key)))
        // its policy changed, what the app knows of the community is asked again: back to the start
        case Some(Left(5)) => policy(about, key)
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
  }))

  /** THE COMMUNITY'S POLICY (specs/protocol.md "Policies"): how one joins, what a business and a person are given,
   * whether families and teams share accounts, how long a recovery waits — the community's own to change, inside
   * what the hub it is at allows; in force from then on, the lines given before left as they are */
  private def policy(about: About, key: String): Unit ! R =
    def m(c: Long) = money(c, about.unit)
    def saved(result: Either[Refusal, Unit]): Unit ! R = result match
      case Right(_) => inform(w.policySaved, about.name)
      case Left(r) if r.why == "policy" => inform(w.notSet, if r.detail.startsWith("hub") then w.hubForbids else
          w.policyBad)
      case Left(r) => inform(w.notSet, refusal(r, about.unit))
    val notBelow = (a: BigDecimal) => a >= 0
    show(Screen[Int](s"${w.policy} · ${about.name}")(Part.text(w.policyText), Part.details(
      w.howJoin -> (if about.open then w.joinOpen else w.byAdmission), w.newcomersLine -> m(about.credit),
      w.newcomersCeiling -> m(about.ceiling), w.sharedAllowed -> (if about.shared then w.yes else w.no),
      w.recoveryWait -> about.days.toString,
      w.whoMayJoin -> ways(Bounds().personal).find(_._2 == about.only).fold(about.only)(_._1),
      w.personLine -> m(about.personalCredit),
      w.personMax -> (if about.personalMax > 0 then m(about.personalMax) else w.noBound),
      w.earnedBefore -> m(about.after)))(Button(w.policyGeneral, Style.Primary)(1), Button(w.policyPeople)(2),
      Button(w.back)(0))).flatMap {
      case 1 => edit[(Boolean, BigDecimal, BigDecimal, Boolean, Long)](w.policyGeneral, Form(
          said(Field.choice(w.howJoin, w.byAdmission -> false, w.joinOpen -> true).initially(about.open)),
          said(Field.money(w.newcomersLine, about.unit).initially(units(about.credit)).check(w.notBelowZero)(notBelow)),
          said(Field.money(w.newcomersCeiling, about.unit).initially(units(about.ceiling))
            .check(w.notBelowZero)(notBelow)),
          said(Field.choice(w.sharedAllowed, w.yes -> true, w.no -> false).initially(about.shared)),
          said(Field.whole(w.recoveryWait).initially(about.days.toLong).check(w.waitRange)(d => d >= 1 && d <= 90))))
          .flatMap {
          case None => pure(())
          case Some((open, line, ceiling, shared, days)) => command(key, "policy",
              "join" -> Json.str(if open then "open" else "admitted"), "credit" -> Json.num(cents(line)),
              "ceiling" -> Json.num(cents(ceiling)), "shared" -> Json.bool(shared), "wait" -> Json.num(days))
              .flatMap(saved)
        }
      case 2 =>
        val most = Field.money(w.personMax, about.unit).hinted(w.personMaxHint)
        edit[(String, BigDecimal, Option[BigDecimal], BigDecimal)](w.policyPeople, Form(
          said(Field.choice(w.whoMayJoin, ways(about.hub.personal)*).initially(about.only)),
          said(Field.money(w.personLine, about.unit).hinted(w.personLineHint).initially(units(about.personalCredit))
            .check(w.notBelowZero)(notBelow)),
          said((if about.personalMax > 0 then most.initially(units(about.personalMax)) else most).optional),
          said(Field.money(w.earnedBefore, about.unit).hinted(w.earnedBeforeHint).initially(units(about.after))
            .check(w.notBelowZero)(notBelow))), Part.text(w.policyPeopleText)).flatMap {
          case None => pure(())
          case Some((only, line, owing, after)) => command(key, "policy", "only" -> Json.str(only),
              "personalCredit" -> Json.num(cents(line)), "personalMax" -> Json.num(owing.fold(0L)(cents)),
              "after" -> Json.num(cents(after))).flatMap(saved)
        }
      case _ => pure(())
    }

  // ---- the accounts this device holds

  private def accounts(about: About): Unit ! R = held.flatMap { hs =>
    val table = Table(hs.toList)(Column.text(w.anAccount)(_._2), Column.text(w.id)(h => short(h._1))).keyedBy(_._1)
    show(Screen[Option[Option[String]]](w.accounts)(Part.text(w.accountsText), table)(
      Button.select(w.use, table)(h => Some(Some(h._1))), Button(w.sharedAccounts)(Some(Some("+"))),
      Button(w.openOrAdd)(Some(None)), Button(w.anotherCommunity)(Some(Some(""))), Button(w.back)(None))).flatMap {
      case Some(Some("+")) => shared(about)
      // another community: no node in use — the wallet asks which (what it holds at this one is kept)
      case Some(Some("")) => keep[R]("node", "")
      case Some(Some(id)) => keepHere("current", id)
      case Some(None) => welcome(about, first = false)
      case None => pure(())
    }
  }

  /** THE ACCOUNTS THIS PERSON SHARES (specs/protocol.md "Keys": a family, a team): held by the person's own account,
   * so found by asking the node what that account holds — on a new device as on the old one. A new one is opened by
   * the person's account: this device signs, the record says for whom */
  private def shared(about: About): Unit ! R = if !about.shared then inform(w.sharedAccounts, w.sharedOff) else
    Device.ready[R].flatMap(pub => held.flatMap { hs =>
    hs.foldLeft(pure[Vector[State], R](Vector.empty))((acc, h) => acc.flatMap(all => Node.account[R](h._1).map(
      _.filter(_.grant(pub).exists(_.manage)).fold(all)(all :+ _)))).flatMap { own =>
      if own.isEmpty then inform(w.sharedAccounts, w.sharedNeedsOwn)
      else own.foldLeft(pure[Vector[String], R](Vector.empty))((acc, p) => acc.flatMap(all =>
        Node.heldBy[R](p.id).map(all ++ _))).flatMap(ids => Node.members[R].flatMap { ms =>
        val table = Table(ids.distinct.flatMap(i => ms.find(_.id == i)).toList)(Column.text(w.anAccount)(_.name),
          Column.text(w.accountKind)(m => w.kind(m.of))).keyedBy(_.id).whenEmpty(w.noShared)
        show(Screen[Option[Option[Member]]](w.sharedAccounts)(Part.text(w.sharedText), table)(
          Button.select(w.use, table)(m => Some(Some(m))), Button(w.newShared, Style.Primary)(Some(None)),
          Button(w.back)(None))).flatMap {
          case None => pure(())
          case Some(Some(m)) => remember(m.id, m.name)
          case Some(None) =>
            // a family's is people's; a team's is a business's to open
            val opener = own.find(_.of != "personal").getOrElse(own.head)
            val kinds = (if about.only == "business" then Nil else List(w.kind("family") -> "family")) ++
              (if opener.of == "personal" then Nil else List(w.kind("team") -> "team"))
            edit[(String, String)](w.newShared, Form(said(Field.text(w.sharedName)),
              said(Field.choice(w.accountKind, kinds*).initially(kinds.head._2)))).flatMap {
              case None => pure(())
              case Some((name, of)) =>
                val by = if of == "team" then opener else own.head
                Clock.now[R].flatMap(now => Device.signed[R](Record("", 1, "", now, Body.Open(about.key, of, by.id,
                  about.unit)))).flatMap(signed => Node.open[R](signed.copy(acts = Vector(Acting(pub, by.id, 0))),
                  name)).flatMap {
                  case Right(Some(s)) => remember(s.id, name).andThen(inform(w.opened, w.sharedOpenedText))
                  case Right(None) => pure(())
                  case Left(r) => inform(w.notOpened, refusal(r, about.unit))
                }
            }
        }
      })
    }
  })

object Wallet:
  /** an account held by its members' accounts: a family's, a team's */
  def shared(of: String): Boolean = of == "family" || of == "team"
