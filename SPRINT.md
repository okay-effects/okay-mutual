# SPRINT — okay-mutual

Each item: what, why, how, done-when. `[ ]` to do, `[~]` in progress, `[x]` done (with the outcome). A fresh agent
resumes from here: read `specs/protocol.md` first.

## Sprint 1 — a node anyone can have (operator, 2026-11-18: "a node on Cloudflare; a way for anyone to create their own node; in the mall too; publish")

- [~] **hub** — many communities on one deployment. Why: "anyone creates a node" must not need a server or a
  terminal. How: a node is DATA of the deployment, not its configuration: `Hub` (an object) keeps the nodes; each
  node's key pair is made at its creation and kept in its own `Directory` object; its calls live under
  `/n/<slug>/…`; `POST /api/nodes {slug, name, unit, join, credit, ceiling}` creates one and answers its address and
  its setup token, once; `GET /api/nodes` lists the communities. Creation open or behind `HUB_TOKEN`, limited per
  address a day and in all. Done when: `NodeSuite` and `WalletSuite` run through `/n/<slug>/`, two nodes in one
  cloud do not see each other's accounts, a third is refused past the limit.
- [ ] **admission** — joining where it is not open. Why: an open node gives credit to strangers; a real community
  admits. How: `POST …/api/accounts` on such a node keeps the signed first record as a request; the office lists
  requests; `admit {id, credit, ceiling}` / `reject {id}` commands; the wallet shows "waiting" and takes the account
  up once it is there. Done when: played in `WalletSuite` end to end.
- [ ] **create in the wallet** — "Create a community" on the wallet's first screens: name, unit, how one joins, a
  newcomer's limits → the hub creates the node → the founder's device sets it up → the address to give to others
  (a link `…/wallet/?node=…`). In four languages. Done when: played in `WalletSuite`; in a browser against the host.
- [ ] **worker** — the hub on Cloudflare Workers: module `worker` (okay-serverless-cloudflare's `Worker`),
  `worker/entry.mjs`, `worker/wrangler.toml` (objects `ACCOUNTS`, `NODE`, `HUB`; the site as assets),
  `worker/deploy.sh`. Done when: `wrangler dev` passes a scripted check; deployed at workers.dev; the published
  wallet creates a community there.
- [ ] **host** — the same hub on one machine (the fallback beside Cloudflare): `Serve` starts the hub; a first
  community from the environment as before. Done when: the browser check passes against the jar.
- [ ] **guides** — docs/run-a-node.md in four languages: three ways to have a node — create one on a hub from the
  wallet; deploy your own hub to Cloudflare; run it on your machine.
- [x] **publish** (DONE: Pages site names the public hub (`sbt -Dhub=… site`); checked in Chromium in Russian from Pages against the hub — found and fixed the doubled CORS header; README's table; the section on okay-effects.github.io with a screenshot) — the site on Pages pointing at the public hub; README's table; the site's page for okay-effects
  (its section on okay-effects.github.io).
- [x] **mall** (DONE: okay-mall main: `MALL_LINKS` names Okay Mutual, specs/mall.md "Okay Mutual", deployed; szykownia.pl's landing shows the link) — Okay Mall names Okay Mutual: its landing's "Also from us" links the site (`MALL_LINKS`); the
  prototype branch `mutual` (the 0.1.0 ledger inside the mall) is superseded and left unmerged; specs/mall.md says
  so. Deployed.

## Sprint 2 — trust lines: communities trade with each other (protocol stage 4; operator, 2026-11-18: "continue")

Design (specs/protocol.md "Trust lines between nodes", made exact): at node X ONE account for each peer Y — `L_X(Y)`,
of `line`, HELD BY Y'S NODE KEY (so only Y debits it), its credit X's trust in Y (how far Y's community may owe
X's), its ceiling unbounded (owing is no risk to the one who owes). A payment a@X → b@Y: a's `send {to: b@<Y's
key>}` on a's chain; X, with its node key, signs a `send` to b on `L_Y(X)` at Y — an ordinary holder's record, which
Y checks against the credit it gives X, co-signs and carries to b; then X credits `L_X(Y)`. Refused or bounced at Y:
the amount back to a. The two line accounts mirror each other; each node's balances still sum to zero.

- [x] **lines in the node** (DONE: `Peers`, `dir/line`, `across` in `AccountObject`, the `line` command; NodeSuite 15 green — one hub and two hubs over the network) — `line {address, credit}` command (the peer signs the opening record of its account
  here; same unit only), `dir/line/<peer>`; `GET …/api/lines`, `GET …/api/line?node=`, `POST …/api/line/open`; a
  flight to `id@<peer key>` carried across — a peer on the same hub by its objects, another by HTTPS; retried when
  the peer's line moved meanwhile; the payment's name makes a retry pay once. Done when: NodeSuite — two nodes on
  one hub and two hubs: paid, mirrored, sums zero, over the peer's credit bounced, no line bounced, the way back.
- [x] **lines in the wallet** (DONE: the office's "Trust lines", "To another community", history by community; four languages; WalletSuite 10 green. Not run in Chromium against the jar: checked against the live Worker instead) — the office: "Trust lines" (open one by the other community's address, how far they
  may owe us; change it); Pay: "To another community" → its members → pay; history names the community. Four
  languages. Done when: played in WalletSuite; in Chromium against the jar.
- [x] **publish** (DONE: Worker and Pages redeployed; LIVE check in Chromium on Cloudflare — `demo-hill` and
  `demo-river` created from wallets, lines opened both ways, a member paid a member of the other, the receiver's
  wallet verified its node; docs in four languages, README and the protocol say what runs. The two demo
  communities' founders' keys were in the check's browser and are gone: nobody governs them) — Worker and Pages redeployed; a second demo community with a line to the first; docs
  (how-it-works, run-a-node, README, protocol) say what runs, in four languages.

## Sprint 3 — light by design: nothing that needs a licence (operator, 2026-11-18: "make it so that it needs no complex regulation — is that possible?")

Answer and reasoning: docs/legal.md. The rules that keep the system out of the regulated kinds (money, payment
services, crypto-assets, credit to consumers, deposits) become things the software DOES, not only says:

- [x] **legal.md** (DONE: docs/legal.md and its three translations (by helpers, structure checked); linked from README and the landing's nav) — what each rule avoids and why, what still applies whatever we do (tax, data protection,
  contracts, platform reporting), the profile to start in, the questions left for one legal opinion. Four
  languages. Not legal advice, and says so.
- [x] **a community's rules, accepted** (DONE: `dir/rules`, `GET …/api/rules`, the `rules` command, the hash named to join; the wallet shows and accepts, the office edits; both suites) — a community has its rules (a text its founder writes from a standard one:
  units are a count of mutual obligations, never sold or exchanged for money; members are known; each pays its own
  taxes; leaving settles the balance); `GET …/api/rules`; joining names the rules' hash — no account without it;
  the `rules` command changes them. Done when: NodeSuite and WalletSuite (shown, accepted, refused without).
- [x] **businesses only** (DONE: `NodeEnv.only`; chosen in the create form (the default); both suites) — a community may admit only businesses (the simplest footing: no consumer law, no
  consumer credit); chosen at creation, kept by the node. Done when: tested in both suites.
- [x] **a statement for the books** (DONE: History → "Copy as a statement (CSV)" by okay-ui's `Clipboard`; WalletSuite) — a member copies its deals as CSV (day, counterparty, amount, what for): a
  deal in units is a sale to be booked and taxed as any other. Done when: played in WalletSuite.
- [x] **publish** (DONE: Worker and Pages redeployed; README and the landing's nav link legal.md; smoke-checked in
  Chromium on the live hub in Russian — an account, a payment, the statement on the clipboard. A community created
  with rules was not tried live: this address had made its three for the day) — Worker, Pages; README and the
  site link legal.md.

## Sprint 4 — private people take part, without credit; "account", not "wallet" (operator, 2026-11-18)

The operator: private people take part too, as decided at the start; and the app is to be called an account, not a
wallet (docs/legal.md: not the vocabulary of crypto).

- [x] **people without credit** (DONE: `NodeEnv.only = earned` (the default in the create form): a personal account opens with no line, `limits` refuses one, a lent line comes back; NodeSuite 17, WalletSuite 13; docs/legal.md and the FAQ in four languages. Found on the way, in Chromium against the jar, and fixed with a test: a record was built on the account as the screen had it, so a payment received while the app was open made the next one fail ("changed meanwhile")) — a third answer to "who may join", and the default: businesses, and private
  people whose accounts never go below zero. A person earns first — by work or goods sold to members, or units a
  business gives — and then spends: no credit is given to a consumer, so consumer-credit rules do not arise. The
  node keeps it: a personal account opens with no credit line, `limits` refuses one, a line lent (`vouch`) to it
  comes back. Done when: NodeSuite and WalletSuite; docs/legal.md says so in four languages.
- [x] **account, not wallet** (DONE: the app's words, the landing, the page at `/app/` with `/wallet/` sending on, README, run-a-node in four languages (the other documents never said wallet)) — the app's words, the site (its page at `/app/`, the old address redirected), the
  documents in four languages, the okay-effects site. Code names (`wallet` module, `Wallet`) stay: nobody reads
  them. Done when: no "wallet" in what a person sees; the browser check passes at `/app/`.
- [x] **publish** (DONE: Worker and Pages redeployed — `/app/` answers, `/wallet/` sends on; the section on
  okay-effects.github.io says the same. In Chromium against the jar, in Russian: the old address, a community made
  with the default "people earn first" and the standard rules, a business and a private person joined by the
  inviting link; the person could not buy until paid. Not tried on the live hub: this address had made its three
  communities for the day) — Worker, Pages.

## Sprint 5 — an account is not lost with a phone; members find each other (operator, 2026-11-18: "continue")

Two holes between a working system and one people can live in: losing every device loses the account, and members
have no place to say what they offer and what they need — which is what the whole thing is for.

- [x] **recovery** — specs/protocol.md "Keys": a new device asks (proving it holds its key), the community confirms
  it knows the person (a command), the node writes `recover {key, after}` on the account's chain — announced, not
  yet in force; any device that still holds the account sees it and can cancel (`revoke` of that key); after the
  waiting period (7 days) `recovered {key}` makes the key a holder. Chain kinds, the node, the app (ask, wait,
  cancel, the office's confirmations), four languages. Done when: ChainSuite, NodeSuite, WalletSuite.
- [x] **the board** — offers and wants of a community's members: what, for how much, what share in units; posted
  and taken off by a holder's signed word (kept beside the chains, erasable); read by anyone the community shows
  its members to. In the app: the board, one's own entries, "Pay" from an offer. Done when: both suites.
- [x] **publish** — Worker, Pages; docs in four languages (how-it-works: recovery no longer "designed"; the board).

## Later

Everything not done is in [BACKLOG.md](BACKLOG.md), in order.

## Sprint 6 — shared accounts: a family, a team, two signatures (operator, 2026-11-18: "continue")

His requirement from the start ("families and organisations with shared budgets"), until now only designed
(specs/protocol.md "Keys": a holder is a key — or another account).

- [x] **the chain** — a holder may be an ACCOUNT: a record as it travels says for which account a key signed
  (`sigs[].for`, `seq`: where that account's chain says the key may manage it); the fold is the same — its signers
  are the keys and the accounts they acted for. `two {above}`: above this amount a payment needs two holders —
  the first says `propose {to, amount, memo, name}` (a record: it survives the chain moving on, it is audited, it
  lapses in a week), another holder's `send` of the same takes it. Done when: ChainSuite, Laws.
- [x] **the node** — the acting checked against the holder account's own chain, here, now; an account of a family
  (`family`) or a team (`team`: a business's) opened by a member's account with no line of its own (a budget is
  lent — `vouch` — or given by the community; a family's never goes below zero where people earn first); who
  holds what (`GET /api/held?by=`). Done when: NodeSuite.
- [x] **the app** — Accounts → shared accounts: made, used as one's own from every device of the person; holders
  (members, with limits), the threshold, payments that wait for a second holder; checked from the first record with
  the holders' chains; four languages. Done when: WalletSuite.
- [x] **publish** — Worker, Pages, docs in four languages, README, protocol.

Not in it: a holder that is itself a shared account (a department under a company holds by `vouch`, not by
holding); a shared account on the board; taking a lent line back (`release`).

Sprint 6 closed 2026-11-18: hub redeployed, Pages republished (gh-pages 454237e); checked in a browser against the
live hub — a shared account opened from the app by a new member's account in `demo`, its holders shown, the
two-holder threshold set by a record signed for the holder account; accounts kept before this sprint still read.
Found on the way and fixed: History failed on an account with a recovery record. The check left test accounts in
`demo` ("Marta", "Marta's home"). Pages serves a new build about a minute after the push: check after that.

## Sprint 7 — policies set at every level (operator, 2026-11-18: "make all these policies configurable at each
level: hubs, communities, groups, organisations, families")

Until now a community's policy was fixed when it was made, the hub's was three numbers, and "whether a private
person has credit" had three answers. One rule for all levels: EACH LEVEL SETS ITS OWN POLICY INSIDE WHAT THE
LEVEL ABOVE ALLOWS — the hub's operator says what communities here may be; a community says how one joins and who
is given what credit, and may change it; an account shared by a family or a team says who holds it, how much each
may spend, and above what two must sign (sprint 6).

- [x] **the node** — a community's policy as data it changes by its own command (`policy {…}`): joining; private
  people — `business` (none) | `earned` (no line) | `vouched` (only a line a member lends) | `record` (a line the
  community gives once the person has earned `after`) | `` (a line as anyone's); the default lines of a business
  and of a person; the most a person may owe (`personalMax`); shared accounts allowed or not; the recovery's wait.
  The hub's bounds (`HUB_PERSONAL`, `HUB_MAX_CREDIT`, `HUB_MAX_PERSONAL`) hold at making and at every change. An
  account's object asks the policy in force, not the one it was opened under. `earned` in the fold. Done when:
  ChainSuite, NodeSuite.
- [x] **the app** — the office's "Policy" screen; making a community offers what the hub allows; four languages.
  Done when: WalletSuite.
- [x] **publish** — Worker, Pages; docs ×4 (how-it-works, run-a-node, legal: which choices take on which
  questions), README, protocol.

Sprint 7 closed 2026-11-18: hub redeployed, Pages republished. Checked on the live hub: its bounds
(`/.well-known/okay-mutual-hub.json`: every way allowed, no bound — the operator has set none) and the policies of
the communities made before this sprint, read as they were (lines for all, a week, shared accounts). The office's
Policy screen is checked by WalletSuite only: no community on the live hub has an office whose key we hold (`demo`
is not set up; the founders' keys of `demo-hill` and `demo-river` went with the browser that made them). A new
Worker version reaches the edge and running objects within about a minute, as Pages does.

## Sprint 8 — Okay Mall takes units (operator, 2026-11-18: "connect the mall")

A shop of the mall says it takes units; its storefront says so with a link that opens the app at a payment to the
shop. The mall holds no key and no unit: it shows a link its owner gave it (okay-mall specs/mall.md "Okay Mutual").

- [x] **here** — "Receive": an account's link and code to be paid by (`?node=…&pay=<account>`, with `amount`,
  `for`); the app opened by such a link goes to the payment, filled in, once the person holds an account there.
  Four languages. Done when: WalletSuite.
- [x] **the mall** — a shop's "Okay Mutual" in its owner's panel (the link, the share of a price taken in units);
  the storefront's line and foot link; only links to apps the operator lists. Done when: its suite; deployed.
- [x] **publish** — both; docs.

Sprint 8 closed 2026-11-18: this hub and Pages republished; okay-mall deployed (fbb31a7): its pages byte-identical
before and after (no shop has said it takes units yet), `/workshop/units/` answers as every owner's screen, 171
tests. Checked in a browser on the live app: opened by a shop-style link (`?node=…&pay=…&amount=…&for=…`), a new
client opens an account and lands on the payment. Not checked live: an owner saving the link in a real shop's
panel (it needs an owner's sign-in) — the mall's `UnitsSuite` covers it. Next for the mall: the units' part of an
order at checkout and the order marked by the payment seen on the shop's chain (okay-shop's pages).

After sprint 8, the same day (operator: "the next step"): AN ORDER'S PART IN UNITS, in okay-mall (c8db2c1, deployed;
its specs/mall.md "Okay Mutual: an order's part in units"). An order's page says how much of its price may be paid
in units and links the app with `amount` and `for=<the order's number>`; the shop's object reads the account's
chain from this node's public API (`/api/accounts/<id>/chain?from=`), adds up the receipts that name an order, and
shows them to the client and the owner. Nothing here changed for it: the app's link and the node's API were enough.
Left there: the order marked paid by what is seen; the same for bookings; records checked by signatures — which
needs the module `chain` released as a library.

## Sprint 9 — stamp cards (operator, 2026-11-18: "shops give paper cards with stamps, a free purchase when the
card is full; the cards get lost — Okay Mutual can keep such cards")

A shop's card: so many stamps, then a reward. NOT UNITS: a stamp is not owed by anyone, is not passed on, counts
only at its shop — a shop's promise of a discount, kept where it cannot be lost. It is also the easiest way in for
an ordinary customer: an account is all a card needs.

- [x] **the node** — beside the chains (as the board): a shop's card (`card {title, need, reward}`, its own signed
  word); a stamp and a reward given by a TOKEN the shop's device signs (`stamp` / `redeem`, good ten minutes, taken
  once) and the customer's device claims with its own signature; `GET /api/cards[?of=]`. Done when: NodeSuite.
- [x] **the app** — "Cards": a customer's cards and stamps; a shop's own card, "Give a stamp" and "Give the reward"
  as a code to scan; the app opened by the code's link claims it. Four languages. Done when: WalletSuite.
- [x] **publish** — Worker, Pages, docs ×4, README.

Sprint 9 closed 2026-11-18: hub redeployed, Pages republished (gh-pages 6929bee). Checked on the live app with two
browsers — a shop made its card and showed codes; a new customer opened the first code's link, opened an account,
got the stamp, a second one ("the card is full"), then the reward; the code is taken out of the address once read.
Left in `demo`: "Corner cafe (demo)" with its card and "Regular (demo)". The operator also asked how all of this
could be a non-profit organisation: answered in the conversation; nothing decided — see docs/legal.md for what a
community's legal person is for.

2026-11-18, after sprint 9 (operator: "prepare it"): docs/organisation.md — who owns what: a foundation for the
code, the name and the public hub; each community its own legal person; the builders ordinary businesses — and
docs/statute.md, a draft statute of the foundation with the list of where it is weak; both in four languages (the
Polish statute is the working text). A proposal for a lawyer: nothing is decided or registered.

## Sprint 10 — a community outlives its founder's phone (BACKLOG item 1; operator, 2026-11-20: "continue")

A community's own account is recovered by nobody: its managers confirm everyone else's recovery, and nobody
confirms theirs. Two communities of ours are already governed by nobody.

- [x] **two managers** — the office warns while one key governs the community and adds a second from there (a
  device, another person's): the first thing a founder is asked to do.
- [x] **the members elect** — where every manager's key is gone: a member names a new manager's key, signed; when
  more than half of the members who have been members a month say the same key, the node announces it on the
  community's chain as a recovery — in force after the community's wait, cancelled by any manager that still
  holds a key. Only where joining is by admission: where anyone joins, anyone could vote themselves in. A
  member's age counted from the node's clock, not from the record the member wrote.
- [x] **publish** — Worker, Pages, docs ×4.

Sprint 10 closed 2026-11-20: hub redeployed, Pages republished (gh-pages 2f5c8c4). On the live hub the election
answers (`demo` is open to anyone: no election there, as designed). The office's warning and the members' election
are checked by the suites only — the live hub has no community whose office key we hold (BACKLOG item 7).

## Sprint 11 — a community's data out and in (BACKLOG item 2; the hub is hosting: the operator owes backups)

- [x] **the node** — `export`, a command of a manager: the whole community as one file (its key pair, its
  directory, every account's storage); `POST /api/nodes {slug, import}`: a hub takes such a file — every chain
  folded again and checked by its signatures, the hub's bounds held, refused where any account is already there;
  `moved {address}`: the old node answers what it was asked to, refuses every change and says where it went;
  `GET /api/backup` (`x-hub-token`): every community of the hub, for its operator. Done when: NodeSuite — a
  community moved between two hubs, payments going on there, the old one refusing.
- [x] **the app** — the office's "Move or back up": save a copy (a file), close here once moved; "Bring a
  community from a file" where communities are made; an app at a moved community goes where it went. Four
  languages. Done when: WalletSuite.
- [x] **the operator's backups** — `scripts/backup.scala` (scala-cli): the hub's backup into a dated file; the
  live hub's `HUB_TOKEN` set (kept in `.env`). Done when: a backup of the live hub taken and read back.
- [x] **publish** — Worker, Pages, docs ×4.

Sprint 11 closed 2026-11-20: the live hub's token set (in `.env`, never printed); a backup of the live hub taken
by `scripts/backup.scala` (3 communities, 20 accounts) and every community of it brought into a hub on this
machine (the JVM host) — balances as on the live hub. That check found that a state kept by an older node lacks
what newer ones count, so a hub bringing a file now writes each state as its chain makes it, checking only where
the chain ends and its balance against the file.
