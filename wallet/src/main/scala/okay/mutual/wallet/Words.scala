package okay.mutual.wallet

/**
 * THE WALLET'S WORDS: everything it says to a person, in one language. The program says what is on a screen; this
 * says it in words — English, Russian, Ukrainian, Polish (`Words.of`). A language is an object of this trait, so the
 * compiler knows when one lacks a word
 */
trait Words:
  /** the language's code, as a page's `lang` */
  def code: String
  // ---- the buttons every screen shares
  def ok: String; def yes: String; def no: String; def save: String; def cancel: String; def back: String
  /** a form's own complaint (okay-ui's, in English) in this language */
  def problem(english: String): String
  // ---- the page around the wallet
  def tagline: String; def about: String
  // ---- a node chosen
  def chooseNode: String; def nodeAddress: String; def nodeHint: String; def noNode: String
  // ---- the first screen
  def intro(unit: String): String
  def community: String; def joining: String; def joinOpen: String; def joinAdmitted: String
  def newCredit: String; def newCeiling: String
  def openAccount: String; def addThisDevice: String; def setUpCommunity: String
  // ---- an account opened
  def yourName: String; def nameHint: String; def accountKind: String; def personal: String; def business: String
  def opened: String; def openedText(line: String): String; def notOpened: String
  /** an account's kind as the node names it (`personal`, `business`, `community`) */
  def kind(of: String): String
  // ---- the community set up
  def communityName: String; def setupToken: String; def setupText: String
  def setUpDone: String; def setUpDoneText: String; def notSetUp: String
  // ---- this device added to an account
  def addThisDeviceTitle: String; def addDeviceHow: String; def thisDeviceKey: String
  def accountId: String; def accountIdHint: String; def itIsAdded: String
  def holds: String; def holdsText: String; def notYet: String; def notYetText: String
  def notFound: String; def notFoundText: String; def anAccount: String
  // ---- an account's home
  def notAtNode: String; def balance: String; def canSpend: String; def canReceive: String
  def pay: String; def history: String; def vouch: String; def devices: String; def checkNode: String
  def accounts: String
  // ---- a payment
  def nobodyToPay: String; def firstMember: String; def to: String; def amount: String; def moreThanZero: String
  def forWhat: String; def payQuestion(amount: String, who: String): String
  def cameBack: String; def cameBackText(who: String): String
  def paid: String; def paidText(amount: String, who: String, balance: String): String; def notPaid: String
  // ---- vouching
  def nobodyToVouch: String; def vouchFor: String; def partOfLine: String; def vouchText: String
  def hasVoucher: String; def vouched: String; def vouchedText(amount: String): String; def notVouched: String
  // ---- the history
  def day: String; def what: String; def withWhom: String; def forColumn: String; def nothingYet: String
  def accountOpened: String; def paidKind: String; def received: String; def cameBackKind: String
  def limitsKind(line: String, ceiling: String): String
  def deviceAdded(key: String): String; def deviceRemoved(key: String): String
  def lineLent(amount: String): String; def lineReceived(amount: String): String
  def lineGivenBack(amount: String): String; def lineReturned(amount: String): String
  // ---- devices
  def devicesText: String; def accountsId: String; def key: String; def may: String; def atMost: String
  def thisDevice: String; def spendAndManage: String; def spend: String; def manage: String; def nothing: String
  def aPayment(amount: String): String; def inDays(amount: String, days: Long): String; def accountsOwn: String
  def addDevice: String; def takeOff: String; def newKey: String; def newKeyHint: String; def mayManage: String
  def atMostPayment: String; def atMostWeek: String
  def added: String; def addedText: String; def notAdded: String
  def takeOffQuestion(key: String): String; def takenOff: String; def takenOffText: String; def notTakenOff: String
  // ---- the node, checked
  def checking: String; def checkingText: String; def checked: String
  def anotherNode: String; def notWhatRecordsMake: String
  def recordsHold(records: Long, balance: String): String; def recordFails(at: Int, fault: String): String
  def noProof: String; def headNotSigned: String; def notUnderHead: String
  def headWentBack(now: Long, before: Long): String; def twoHeads(number: Long): String
  def underHead(number: Long): String; def didSums: String
  // ---- the community's office
  def members: String; def sum: String; def sumText: String; def member: String; def creditLine: String
  def ceiling: String; def noMembers: String; def setLimits: String; def limitsOf(name: String): String
  def notBelowZero: String; def limitsSet: String
  def limitsSetText(name: String, line: String, ceiling: String): String
  def notSet: String
  // ---- the accounts this device holds
  def accountsText: String; def id: String; def use: String; def openOrAdd: String
  // ---- the node's refusals
  def overCredit(by: String): String; def overCeiling(by: String): String
  def limitPayment: String; def limitWeek: String; def unsigned: String; def onlyManage: String; def lastManager: String
  def heldAlready: String; def changedMeanwhile: String; def namedAlready: String; def admitsItself: String
  def openAlready: String; def notTheToken: String; def setUpAlready: String; def offline: String
  def refused(why: String): String
  // ---- a community found, made, joined by asking
  def findCommunity: String; def findText: String; def communitiesHere: String; def noCommunities: String
  def useAddress: String; def createCommunity: String; def createText: String
  def place: String; def placeHint: String; def unitLabel: String; def howJoin: String; def byAdmission: String
  def newcomersLine: String; def newcomersCeiling: String
  def communityCreated: String; def communityCreatedText: String
  def invite: String; def inviteText: String
  def asked: String; def waitingText(name: String): String; def checkAgain: String; def withdraw: String
  def requestsButton(n: Int): String; def requestsTitle: String; def noRequests: String
  def admit: String; def reject: String; def admitOf(name: String): String
  def admitted: String; def admittedText(name: String): String; def rejected: String; def notAdmitted: String
  def placeTaken: String; def placeBad: String; def hubLimit: String; def hubClosed: String
  def anotherCommunity: String; def unitShort: String
  // ---- trust lines: communities trading with each other
  def payElsewhere: String; def whichCommunity: String; def nobodyThere: String
  def cameBackElsewhere(community: String): String
  def trustLines: String; def linesText: String; def theyMayOwe: String; def noLines: String
  def openLine: String; def lineAddress: String; def lineAddressHint: String; def changeLine: String
  def lineOpened: String; def lineOpenedText(community: String, credit: String): String; def lineNotOpened: String
  def lineUnit: String; def noSuchNode: String; def lineSame: String
  // ---- a community's rules, whom it admits, a statement for the books (docs/legal.md)
  def whoMayJoin: String; def businessesOnly: String; def peopleEarn: String; def peopleCredit: String
  def openedEarning: String; def noCreditHere: String
  def rulesLabel: String; def rulesHint: String; def standardRules: String
  def rulesOf(community: String): String; def accept: String; def rulesButton: String; def rulesSaved: String
  def rulesChanged: String; def onlyBusinesses: String
  def copyStatement: String; def statementCopied: String; def statementCopiedText: String; def notCopied: String
  // ---- an account asked back when every device is lost
  def lostDevices: String; def lostText: String; def itIsMine: String
  def askedBack: String; def askedBackText(community: String): String
  def confirmedBack(day: String): String; def notYetBack: String; def backNow: String; def forgetAsking: String
  def recoveryWarning(key: String, day: String): String; def cancelRecovery: String; def recoveryCancelled: String
  def recoveriesButton(n: Int): String; def recoveriesTitle: String; def recoveriesText: String
  def noRecoveries: String; def confirmIdentity: String; def confirmQuestion(name: String): String
  def recoveryAnnounced: String; def recoveryAnnouncedText(name: String): String; def dropAsking: String
  // ---- the board: what members offer and look for
  def board: String; def boardText: String; def boardKind: String; def offers: String; def wants: String
  def boardEmpty: String; def openEntry: String; def addOffer: String; def addWant: String
  def entryText: String; def price: String; def priceHint: String; def byAgreement: String
  def inUnits: String; def inUnitsHint: String; def shareRange: String
  def posted: String; def notPosted: String; def boardFull: String; def takeOffEntry: String
  // ---- an account shared by a family or a team; two holders for a large payment
  def sharedAccounts: String; def sharedText: String; def noShared: String; def newShared: String
  def sharedName: String; def sharedNeedsOwn: String; def sharedOpenedText: String
  def holders: String; def holdersText: String; def addHolder: String; def holderWho: String
  def holderAdded(name: String): String; def holderRemoved(name: String): String; def you: String
  def twoHolders: String; def twoAbove: String; def twoHint: String; def twoSet: String
  def twoSetText(amount: String): String; def twoOff: String
  def toConfirm(n: Int): String; def toConfirmTitle: String; def toConfirmText: String; def nothingWaits: String
  def confirmPayment: String; def proposedBy: String; def proposed: String
  def proposedText(amount: String, who: String): String; def needsAnother: String; def needsSecond: String
  def twoKind(amount: String): String; def proposedKind(amount: String): String
  // ---- the community's policy
  def policy: String; def policyText: String; def policyGeneral: String; def policyPeople: String
  def policyPeopleText: String; def peopleVouched: String; def peopleRecord: String
  def personLine: String; def personLineHint: String; def personMax: String; def personMaxHint: String
  def noBound: String; def earnedBefore: String; def earnedBeforeHint: String; def sharedAllowed: String
  def recoveryWait: String; def waitRange: String; def policySaved: String; def policyBad: String
  def hubForbids: String; def sharedOff: String
  // ---- to be paid by a link
  def receive: String; def receiveText: String; def notMemberHere: String
  // ---- stamp cards
  def cards: String; def cardsText: String; def noCards: String; def shop: String; def cardTitle: String
  def stamps: String; def reward: String; def rewardHint: String; def myCard: String; def myCardText: String
  def stampsNeeded: String; def stampsRange: String; def makeCard: String; def changeCard: String
  def takeCardOff: String; def takeCardOffQuestion: String; def cardSaved: String
  def giveStamp: String; def giveStampText: String; def giveReward: String; def giveRewardText: String
  def nextCode: String; def stampGot: String; def stampGotText(shop: String, have: Long, need: Long): String
  def rewardReady(reward: String): String; def rewardTaken: String
  def rewardTakenText(shop: String, reward: String): String
  def codeUsed: String; def codeOld: String; def codeBad: String; def notEnoughStamps: String
  def noSuchCard: String; def ownCode: String
  // ---- who governs a community, and when their keys are gone
  def oneManager: String; def managers: String; def electManager: String; def electText(need: Int): String
  def electSay: String; def electNobody: String; def electKey: String; def electKeyHint: String
  def electSayIt: String; def electAnnounced(key: String, day: String): String; def electInForce: String
  def electDone: String; def electCount(count: Int, need: Int): String; def electNotHere: String
  def electYoung: String
  // ---- a community's copy, its move
  def moveOrBackUp: String; def moveText: String; def copyIsKey: String; def saveCopy: String; def copySaved: String
  def copySavedText: String; def notSaved: String; def closeHere: String; def closeText: String
  def newAddress: String; def newAddressHint: String; def closeQuestion: String; def closedHere: String
  def closedHereText: String; def movedTitle: String; def movedText(name: String, address: String): String
  def goThere: String; def bringCommunity: String; def bringText: String; def theFile: String; def brought: String
  def broughtText: String; def bringHere: String; def bringChain: String; def bringFile: String

object Words:
  /** the language of a code (`ru`, `uk-UA`, `pl`…): English when it is none of ours */
  def of(language: String): Words = language.toLowerCase.takeWhile(c => c != '-' && c != '_') match
    case "ru" => WordsRu
    case "uk" => WordsUk
    case "pl" => WordsPl
    case _ => En
  val all: List[(String, Words)] = List("EN" -> En, "PL" -> WordsPl, "UA" -> WordsUk, "RU" -> WordsRu)

  object En extends Words:
    val code = "en"
    val ok = "OK"; val yes = "Yes"; val no = "No"; val save = "Save"; val cancel = "Cancel"; val back = "Back"
    def problem(english: String): String = english
    val tagline = "mutual credit, beside money"; val about = "About"
    val chooseNode = "Choose a community's node"; val nodeAddress = "The node's address"
    val nodeHint = "as the community gave it: https://…"
    val noNode = "This app works with any node of the network. No node answers at the address it has."
    def intro(unit: String) = "A network of mutual credit: members sell to and buy from each other for units " +
      s"they issue to one another. A unit counts as 1 $unit and is never exchanged for money. Your key is made on " +
      "this device and never leaves it."
    val community = "Community"; val joining = "Joining"; val joinOpen = "open to anyone"
    val joinAdmitted = "by the community's admission"
    val newCredit = "A new member's credit line"; val newCeiling = "A new member's ceiling"
    val openAccount = "Open an account"; val addThisDevice = "Add this device to my account"
    val setUpCommunity = "Set up this community"
    val yourName = "Your name"; val nameHint = "as the other members will see it"; val accountKind = "Account"
    val personal = "Personal"; val business = "Business"
    val opened = "Your account is open"
    def openedText(line: String) = s"Your credit line is $line: you can buy before you have sold."
    val notOpened = "Not opened"
    def kind(of: String) = of match
      case "line" => "another community"
      case "family" => "shared: family"
      case "team" => "shared: team"
      case other => other
    val communityName = "The community's name"; val setupToken = "Setup token"
    val setupText = "The community's own account is opened with this device's key. Whoever holds a key that may " +
      "manage it sets the members' limits; more such keys are added under Devices."
    val setUpDone = "The community is set up"
    val setUpDoneText = "Its account is in this app. Open your own account too: Accounts, then Open an account."
    val notSetUp = "Not set up"
    val addThisDeviceTitle = "Add this device"
    val addDeviceHow = "On a device that holds the account: Devices, Add a device, and enter this device's key."
    val thisDeviceKey = "this device's key"
    val accountId = "The account's id"; val accountIdHint = "shown there under Devices"; val itIsAdded = "It is added"
    val holds = "This device holds the account"; val holdsText = "It can act within the limits it was given."
    val notYet = "Not yet"; val notYetText = "The account does not name this device's key."
    val notFound = "Not found"; val notFoundText = "No such account at this node."; val anAccount = "Account"
    val notAtNode = "This account is not at this node. Take it off this device?"
    val balance = "Balance"; val canSpend = "You can still spend"; val canReceive = "You can still receive"
    val pay = "Pay"; val history = "History"; val vouch = "Vouch"; val devices = "Devices"
    val checkNode = "Check the node"; val accounts = "Accounts"
    val nobodyToPay = "Nobody to pay yet"; val firstMember = "You are the first member here."
    val to = "To"; val amount = "Amount"; val moreThanZero = "more than zero"; val forWhat = "For what"
    def payQuestion(amount: String, who: String) = s"Pay $amount to $who?"
    val cameBack = "It came back"
    def cameBackText(who: String) =
      s"$who cannot take it now: they are at their ceiling and must spend first. Nothing was paid."
    val paid = "Paid"
    def paidText(amount: String, who: String, balance: String) = s"$amount to $who. Your balance: $balance."
    val notPaid = "Not paid"
    val nobodyToVouch = "Nobody to vouch for yet"; val vouchFor = "For"; val partOfLine = "Part of your credit line"
    val vouchText = "You lend part of your own credit line to a member you trust. Your own room shrinks by it " +
      "until they have earned their way back. There is no reward: only your own room at risk."
    val hasVoucher = "That member has a voucher already."; val vouched = "Vouched"
    def vouchedText(amount: String) = s"$amount of your line is lent."
    val notVouched = "Not vouched"
    val day = "Day"; val what = "What"; val withWhom = "With"; val forColumn = "For"; val nothingYet = "nothing yet"
    val accountOpened = "account opened"; val paidKind = "paid"; val received = "received"
    val cameBackKind = "came back"
    def limitsKind(line: String, ceiling: String) = s"limits: line $line, ceiling $ceiling"
    def deviceAdded(key: String) = s"device added: $key"; def deviceRemoved(key: String) = s"device removed: $key"
    def lineLent(amount: String) = s"line lent: $amount"; def lineReceived(amount: String) = s"line received: $amount"
    def lineGivenBack(amount: String) = s"line given back: $amount"
    def lineReturned(amount: String) = s"line returned: $amount"
    val devicesText = "Each device has its own key. A lost device is taken off here from another one; what it had " +
      "handed on goes with it."
    val accountsId = "The account's id"; val key = "Key"; val may = "May"; val atMost = "At most"
    val thisDevice = "(this device)"; val spendAndManage = "spend and manage"; val spend = "spend"
    val manage = "manage"; val nothing = "nothing"
    def aPayment(amount: String) = s"$amount a payment"
    def inDays(amount: String, days: Long) = s"$amount in $days days"
    val accountsOwn = "the account's own"
    val addDevice = "Add a device"; val takeOff = "Take off"; val newKey = "The new device's key"
    val newKeyHint = "shown there under Add this device to my account"; val mayManage = "May manage the account"
    val atMostPayment = "At most a payment"; val atMostWeek = "At most a week"
    val added = "Added"; val addedText = "That device now holds the account."; val notAdded = "Not added"
    def takeOffQuestion(key: String) = s"Take $key off the account, with every device it added?"
    val takenOff = "Taken off"; val takenOffText = "That key no longer acts for the account."
    val notTakenOff = "Not taken off"
    val checking = "Checking the node"
    val checkingText = "Every record and signature of your account, from the first."
    val checked = "The node, checked"
    val anotherNode = "The chain names another node's key."
    val notWhatRecordsMake = "The node's account is not what its own records make."
    def recordsHold(records: Long, balance: String) =
      s"$records records, every signature holds; they make a balance of $balance."
    def recordFails(at: Int, fault: String) = s"Record $at does not hold: $fault."
    val noProof = "The node gave no proof for this account."
    val headNotSigned = "The node's head is not signed by its key."
    val notUnderHead = "Your chain is not under the node's signed head."
    def headWentBack(now: Long, before: Long) = s"The node's head went back: number $now after $before."
    def twoHeads(number: Long) = s"Two different heads of one number ($number): the node showed two histories."
    def underHead(number: Long) = s"Your chain is under the node's signed head number $number."
    val didSums = "This device did the sums itself; the node was only asked for the records."
    val members = "Members"; val sum = "The balances' sum"
    val sumText = "The sum of all balances is zero, always: a unit is born when a member buys and gone when it sells."
    val member = "Member"; val creditLine = "Credit line"; val ceiling = "Ceiling"; val noMembers = "no members yet"
    val setLimits = "Set limits"; def limitsOf(name: String) = s"Limits · $name"; val notBelowZero = "not below zero"
    val limitsSet = "Set"
    def limitsSetText(name: String, line: String, ceiling: String) = s"$name: line $line, ceiling $ceiling."
    val notSet = "Not set"
    val accountsText = "One device can hold several accounts: your own, your family's, your company's."
    val id = "Id"; val use = "Use"; val openOrAdd = "Open or add another"
    def overCredit(by: String) = s"That is over your credit line by $by."
    def overCeiling(by: String) = s"That is over the ceiling by $by."
    val limitPayment = "That is more than this device may pay at once."
    val limitWeek = "That is more than this device may pay in a week."
    val unsigned = "This device does not hold the account, or may not do this."
    val onlyManage = "Only a device that may manage the account can do this."
    val lastManager = "One device that may manage must stay."
    val heldAlready = "That key holds the account already."
    val changedMeanwhile = "The account changed meanwhile. Try again."
    val namedAlready = "A payment of this name was made already."
    val admitsItself = "This community admits its members itself: ask it for an account."
    val openAlready = "This account is open already."; val notTheToken = "That is not the setup token."
    val setUpAlready = "This community is set up already."
    val offline = "The node does not answer. Check the connection and try again."
    def refused(why: String) = s"The node refused: $why."
    val findCommunity = "Find your community"
    val findText = "Each community runs its own node with its own members and rules. Choose yours, enter the address " +
      "it gave you, or start a new one."
    val communitiesHere = "Communities here"; val noCommunities = "none yet"
    val useAddress = "Use this address"; val createCommunity = "Create a community"
    val createText = "You become its founder: your device's key opens the community's own account, and from it you " +
      "admit members and set their limits. Nobody else administers it."
    val place = "Its place in the address"; val placeHint = "small letters, digits, hyphens: our-town"
    val unitLabel = "Unit (the national currency it counts as)"; val howJoin = "How one joins"
    val byAdmission = "by your admission (safer)"
    val newcomersLine = "A newcomer's credit line"; val newcomersCeiling = "A newcomer's ceiling"
    val communityCreated = "The community is created"
    val communityCreatedText = "Its account is in this app. Invite people with the link under Invite, and open " +
      "your own account too: Accounts, then Open or add another."
    val invite = "Invite"
    val inviteText = "Send this link to someone you want in the community. It opens the app at this community."
    val asked = "You asked to join"
    def waitingText(name: String) = s"$name admits its members itself. Your request waits for its decision; your key " +
      "stays on this device."
    val checkAgain = "Check again"; val withdraw = "Forget this request"
    def requestsButton(n: Int) = s"Requests ($n)"; val requestsTitle = "Who asks to join"
    val noRequests = "nobody is waiting"
    val admit = "Admit"; val reject = "Reject"; def admitOf(name: String) = s"Admit · $name"
    val admitted = "Admitted"; def admittedText(name: String) = s"$name has an account now."
    val rejected = "Rejected"; val notAdmitted = "Not admitted"
    val placeTaken = "That place is taken: choose another."
    val placeBad = "The place needs 3 to 40 small letters, digits or hyphens; the unit, letters."
    val hubLimit = "No more communities can be made from here today."
    val hubClosed = "Communities are not made here by anyone: ask the operator of this site."
    val anotherCommunity = "Another community"; val unitShort = "Unit"
    val payElsewhere = "To another community"; val whichCommunity = "Which community"
    val nobodyThere = "That community has no members to pay yet."
    def cameBackElsewhere(community: String) = s"$community did not take it: it trusts this community no further, " +
      "or the receiver cannot take it now. Nothing was paid."
    val trustLines = "Trust lines"
    val linesText = "A trust line says how far another community may owe this one. Its members can then pay ours, " +
      "and ours theirs as far as THEY trust us. A positive balance: we owe them; a negative one: they owe us."
    val theyMayOwe = "They may owe us"; val noLines = "no lines yet"
    val openLine = "Open a line"; val lineAddress = "The other community's address"
    val lineAddressHint = "as its members' invitation shows it after ?node="
    val changeLine = "Change"
    val lineOpened = "The line is open"
    def lineOpenedText(community: String, credit: String) = s"$community may owe this community up to $credit. For " +
      "our members to pay theirs, they open a line to us too."
    val lineNotOpened = "The line is not open"
    val lineUnit = "That community counts in another unit: lines between units are not built yet."
    val noSuchNode = "No community answers at that address."; val lineSame = "That is this community itself."
    val whoMayJoin = "Who may join"; val businessesOnly = "businesses and the self-employed only (simplest)"
    val peopleEarn = "businesses, and private people who earn before they spend (recommended)"
    val peopleCredit = "businesses and private people, all with credit lines"
    val openedEarning = "Your account starts at zero and does not go below it. Sell something or do some work " +
      "for a member, and then spend what you earned."
    val noCreditHere = "A private person's account has no credit line in this community: it earns, then spends."
    val rulesLabel = "The community's rules"
    val rulesHint = "a starting text: every newcomer accepts it to join. Have a lawyer read it before you rely on it."
    val standardRules = "1. This community keeps a shared account of what its members give to and take from each " +
      "other. A unit is the measure in that account; it counts as one unit of the national currency in prices.\n" +
      "2. Units are not money. They are never sold, bought or exchanged for money, by anyone.\n" +
      "3. A balance above zero says how much a member has given beyond what it has taken; a balance below zero, how " +
      "much it has taken beyond what it has given, and owes back to the community in its own goods and services.\n" +
      "4. Each member has a credit line and a ceiling, set by the community, which may change them.\n" +
      "5. Members are admitted by the community and are known to it by their real names.\n" +
      "6. A sale for units is a sale. Each member issues its own invoices and pays its own taxes, in money, as for " +
      "any sale.\n" +
      "7. A member may leave at any time by bringing its balance to zero. A member who leaves below zero settles the " +
      "difference in money within 30 days.\n" +
      "8. The community may close the account of a member who breaks these rules."
    def rulesOf(community: String) = s"The rules · $community"; val accept = "I accept"
    val rulesButton = "Rules"; val rulesSaved = "The rules are saved"
    val rulesChanged = "The community's rules have changed: read and accept them again."
    val onlyBusinesses = "This community admits only businesses and the self-employed."
    val copyStatement = "Copy as a statement (CSV)"; val statementCopied = "Copied"
    val statementCopiedText = "Your deals are on the clipboard as CSV: paste them into a spreadsheet or give them to " +
      "your accountant. A deal in units is a sale, booked and taxed as any other."
    val notCopied = "This browser does not let the page copy. Select the table and copy it by hand."
    val lostDevices = "I lost my devices"
    val lostText = "Choose your account. This device makes a new key and asks your community to confirm it is you. " +
      "Once it has, the key holds your account after its waiting time (a week, unless it says otherwise) — " +
      "time for you to say no if someone else asked."
    val itIsMine = "This is my account"
    val askedBack = "You asked your account back"
    def askedBackText(community: String) = s"$community has to confirm it is you: get in touch with it. Nothing " +
      "changes until it does."
    def confirmedBack(day: String) = s"Your community confirmed it. This device holds the account from $day."
    val notYetBack = "Not yet: the waiting time is not over."; val backNow = "Take the account up"
    val forgetAsking = "Forget this"
    def recoveryWarning(key: String, day: String) = s"Someone asked this account back for another device ($key), " +
      s"and your community confirmed it. That device holds the account from $day. If it was not you, cancel it now."
    val cancelRecovery = "It was not me: cancel"; val recoveryCancelled = "Cancelled: that device will hold nothing."
    def recoveriesButton(n: Int) = s"Lost devices ($n)"; val recoveriesTitle = "Who asks an account back"
    val recoveriesText = "Confirm only when you are sure it is the member: speak to them, do not trust a message. " +
      "The account's old devices can still cancel for a week."
    val noRecoveries = "nobody asks"; val confirmIdentity = "It is them: confirm"
    def confirmQuestion(name: String) = s"Are you sure the one who asks is $name? Their new device will hold the " +
      "account after the community's waiting time."
    val recoveryAnnounced = "Announced"
    def recoveryAnnouncedText(name: String) = s"$name's new device holds the account after the waiting time, " +
      "unless an old " +
      "device cancels."
    val dropAsking = "Not them: drop"
    val board = "Board"
    val boardText = "What members offer and what they look for. A price is in the national currency; the share says " +
      "how much of it is taken in units — the rest is paid in money, outside."
    val boardKind = "Kind"; val offers = "offers"; val wants = "looks for"; val boardEmpty = "nothing yet: be the first"
    val openEntry = "Open"; val addOffer = "I offer"; val addWant = "I look for"
    val entryText = "More about it"; val price = "Price"; val priceHint = "leave empty: by agreement"
    val byAgreement = "by agreement"; val inUnits = "In units, %"
    val inUnitsHint = "what share of the price you take or pay in units: 100 — all, 50 — half"
    val shareRange = "from 0 to 100"
    val posted = "It is on the board"; val notPosted = "Not done"
    val boardFull = "The board has no room for another entry of yours: take one off first."
    val takeOffEntry = "Take off the board"
    val sharedAccounts = "Shared accounts"
    val sharedText = "An account of a family or of a team is held by its members' accounts, not by devices: each " +
      "holder uses it from any device of their own, and a lost phone is replaced once, in the person's own account."
    val noShared = "you share no account yet"; val newShared = "New shared account"
    val sharedName = "Its name"
    val sharedNeedsOwn = "Open your own account first: a shared account is held by its members' accounts."
    val sharedOpenedText = "The account is open and you hold it. It has no credit line of its own: it spends what " +
      "it is paid, or a line a member lends it. Add the others under Holders."
    val holders = "Holders"
    val holdersText = "Who may act for this account: members' accounts, each with what it may do and how much. A " +
      "holder uses it from any device of their own."
    val addHolder = "Add a holder"; val holderWho = "Member"
    def holderAdded(name: String) = s"holder $name added"; def holderRemoved(name: String) = s"holder $name taken off"
    val you = "you"
    val twoHolders = "Two signatures"; val twoAbove = "Two holders for a payment above"
    val twoHint = "leave empty: one holder is enough for any payment"
    val twoSet = "Set"
    def twoSetText(amount: String) = s"A payment above $amount now needs two holders: one proposes it, another " +
      "confirms."
    val twoOff = "One holder is enough for any payment now."
    def toConfirm(n: Int) = s"To confirm ($n)"; val toConfirmTitle = "Payments that wait for a second holder"
    val toConfirmText = "A payment above the account's threshold is proposed by one holder and confirmed by " +
      "another. One not confirmed within a week lapses."
    val nothingWaits = "nothing waits"
    val confirmPayment = "Confirm and pay"; val proposedBy = "Proposed by"
    val proposed = "It waits for a second holder"
    def proposedText(amount: String, who: String) = s"$amount to $who is above this account's threshold: another " +
      "holder has to confirm it within a week. Nothing has been paid yet."
    val needsAnother = "You proposed this payment: another holder has to confirm it."
    val needsSecond = "This payment needs a second holder."
    def twoKind(amount: String) = if amount.isEmpty then "one holder is enough" else s"two holders above $amount"
    def proposedKind(amount: String) = s"payment of $amount proposed"
    val policy = "Policy"
    val policyText = "The community's own policy: in force from the moment it is saved, for every account. Lines " +
      "given before stay as they are. The hub this community is at may allow less than is offered elsewhere."
    val policyGeneral = "Joining and businesses"; val policyPeople = "Private people"
    val policyPeopleText = "Giving a private person credit, even without interest, may fall under the rules on " +
      "consumer credit. Where people earn before they spend, no credit is given and the question does not arise."
    val peopleVouched = "businesses, and private people with only a line a member lends them"
    val peopleRecord = "businesses, and private people given a line once they have earned"
    val personLine = "A new person's credit line"
    val personLineHint = "where private people have lines from the start"
    val personMax = "The most a person may owe"
    val personMaxHint = "own line and lent together; leave empty: no bound"
    val noBound = "no bound"
    val earnedBefore = "Earned before a line is given"
    val earnedBeforeHint = "what a person has to have been paid before the community gives a line"
    val sharedAllowed = "Accounts of families and teams"
    val recoveryWait = "Days a recovery waits"; val waitRange = "from 1 to 90"
    val policySaved = "The policy is saved"
    val policyBad = "This is not a policy the community can have: check the amounts."
    val hubForbids = "The hub this community is at does not allow that."
    val sharedOff = "This community has no shared accounts."
    val receive = "Receive"
    val receiveText = "Show this code or send this link to whoever pays you: it opens the app at a payment to this " +
      "account. A shop puts the link on its page."
    val notMemberHere = "The one this link asks you to pay is not a member of this community."
    val cards = "Cards"
    val cardsText = "Stamp cards of the shops here: a stamp for each purchase, a reward when the card is full. " +
      "A stamp is not a unit: it counts only at its shop and cannot be passed on. The card is kept here — it " +
      "cannot be lost."
    val noCards = "no shop here has a card yet"; val shop = "Shop"; val cardTitle = "Card"
    val stamps = "Stamps"; val reward = "Reward"; val rewardHint = "what a full card gives: a free coffee"
    val myCard = "My card"
    val myCardText = "Your shop's card: how many stamps make it full, and what a full card gives."
    val stampsNeeded = "Stamps to fill it"; val stampsRange = "from 2 to 50"
    val makeCard = "Make my shop's card"; val changeCard = "Change my card"
    val takeCardOff = "Take my card off"
    val takeCardOffQuestion = "Take the card off? Nobody gets a stamp on it from then on."
    val cardSaved = "The card is saved"
    val giveStamp = "Give a stamp"
    val giveStampText = "Let the customer point their phone's camera at this code: the app opens and the stamp is " +
      "on their card. A code works once, for ten minutes."
    val giveReward = "Give the reward"
    val giveRewardText = "The customer with a full card scans this code: the reward is taken off the card, and " +
      "their screen says so. A code works once, for ten minutes."
    val nextCode = "A new code"
    val stampGot = "A stamp"
    def stampGotText(shop: String, have: Long, need: Long) = s"$shop: $have of $need."
    def rewardReady(reward: String) = s"The card is full: $reward."
    val rewardTaken = "The reward"
    def rewardTakenText(shop: String, reward: String) = s"$shop: $reward. Show this screen to the seller."
    val codeUsed = "This code was used already: ask for a new one."
    val codeOld = "This code is too old: ask for a new one."
    val codeBad = "This is not a shop's code."
    val notEnoughStamps = "The card is not full yet."
    val noSuchCard = "This shop has no card now."
    val ownCode = "This is your own shop's code."
    val oneManager = "One device governs this community. If it is lost, nobody can admit members or set limits " +
      "until the members elect a new manager. Add a second manager now — another device of yours, or a person " +
      "you trust."
    val managers = "Managers"
    val electManager = "Elect a manager"
    def electText(need: Int) = "For when the keys that governed this community are lost. A member names a " +
      "device's key that should govern — this device's, or one shown to them — and signs it. When " +
      s"$need members of a month or more say the same key, it is announced; after the community's waiting time " +
      "it governs, unless a manager who still holds a key says no."
    val electSay = "Members saying it"; val electNobody = "nobody has said a key yet"
    val electKey = "The new manager's device key"
    val electKeyHint = "this device's unless you put another's: shown there under Add this device to my account"
    val electSayIt = "Say this key"
    def electAnnounced(key: String, day: String) = s"Announced: the key $key governs from $day."
    val electInForce = "The new manager governs now. On that device: Accounts, Open or add another, Add this " +
      "device to my account — with the community's account id."
    val electDone = "Enough members said it: the key is announced on the community's chain."
    def electCount(count: Int, need: Int) = s"Your word is kept: $count of $need."
    val electNotHere = "This community is open to anyone, so its members elect no manager: anyone could vote " +
      "themselves in. Keep two managers."
    val electYoung = "Only those who have been members for a month have a say."
    val moveOrBackUp = "Move or back up"
    val moveText = "Save a copy of the whole community — every account's records, the members, the rules, the " +
      "community's key — as one file. Keep copies; with one the community can be brought to another hub, or to " +
      "your own node, from where communities are made there. Once it is there, close it here."
    val copyIsKey = "The file holds the community's key: whoever has it can act as this community's node. Keep it as " +
      "you keep a key."
    val saveCopy = "Save a copy"; val copySaved = "Saved"
    val copySavedText = "The copy is in your device's downloads."
    val notSaved = "The copy was not saved."
    val closeHere = "It moved: close it here"
    val closeText = "When the community is brought to its new place, close it here: from then on this hub changes " +
      "nothing, and every member's app that comes here is sent to the new address."
    val newAddress = "Its new address"; val newAddressHint = "https://… — as the new hub shows it"
    val closeQuestion = "Close the community here? Nothing can be paid here from then on."
    val closedHere = "Closed here"
    val closedHereText = "Members' apps that come here go to the new address."
    val movedTitle = "The community moved"
    def movedText(name: String, address: String) = s"${if name.isEmpty then "This community" else name} moved to " +
      s"$address. Nothing changes here any more."
    val goThere = "Go there"
    val bringCommunity = "Bring a community from a file"
    val bringText = "A community's copy, saved in its office: it is brought to this hub as it was — its accounts, " +
      "its members, its key. Every record is checked before it is taken."
    val theFile = "The community's file"
    val brought = "The community is here"
    val broughtText = "Close it at its old place now (its office: Move or back up). Your devices hold its accounts " +
      "as before."
    val bringHere = "This community is on this hub already, or one of its accounts is."
    val bringChain = "The file's records do not hold: it was changed or damaged."
    val bringFile = "This is not a community's file."
