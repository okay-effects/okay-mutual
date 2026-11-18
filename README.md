# Okay Mutual

An open network of mutual credit: people and businesses sell to and buy from each other for units they issue to one
another, when money is short but work, goods and needs are not. It runs beside ordinary money and replaces nothing.

**Try it:** the app at https://okay-mutual.okay-effects.workers.dev/app/ — join the demo community or create
your own.

There is no coin and nothing to invest in. Every member holds their own keys, every record is signed, anyone can
run a community's node, and communities connect by trust lines they choose themselves.

Читать по-русски: [идея](docs/ru/idea.md), [право](docs/ru/legal.md), [как это работает](docs/ru/how-it-works.md), [вопросы](docs/ru/faq.md),
[запуск узла](docs/ru/run-a-node.md) · Українською: [ідея](docs/uk/idea.md), [право](docs/uk/legal.md), [як це працює](docs/uk/how-it-works.md),
[запитання](docs/uk/faq.md), [запуск вузла](docs/uk/run-a-node.md) · Po polsku: [idea](docs/pl/idea.md), [prawo](docs/pl/legal.md),
[jak to działa](docs/pl/how-it-works.md), [pytania](docs/pl/faq.md), [uruchomienie węzła](docs/pl/run-a-node.md)

- **[The idea](docs/idea.md)** — the problem, what mutual credit is, where it has worked for ninety years, what
  this is not.
- **[How it works](docs/how-it-works.md)** — an account, keys and devices, a payment, vouching, families and
  companies, nodes, trust lines, and what protects a member at each step.
- **[Questions](docs/faq.md)** — is it a cryptocurrency, does it replace money, taxes, what if someone does not
  pay.
- **[Staying light](docs/legal.md)** — which regulated kinds the design stays out of and why (money, payment
  services, crypto-assets, consumer credit), what applies anyway (tax, data, contracts), the questions for a lawyer.
- **[Have a node](docs/run-a-node.md)** — three ways: create a community on a hub from the app, deploy your own
  hub to Cloudflare, run it on your machine; and who governs a community.
- **[The protocol](specs/protocol.md)** — the design for those who build: records, chains, nodes, trust lines.
- **[The prototype's spec](specs/mutual.md)** — the rules, the measures of a network's health, what is tested
  first.

## What runs today, and what is designed

| | Status |
|---|---|
| The rules: credit line, ceiling, vouching, corrections, payments paid once | runs, tested (JVM and Scala.js) |
| A single community's ledger on a small cloud service or one machine | runs, tested |
| The measures of a network's health (velocity, who both sells and buys, stale balances) | runs, tested |
| A signed chain per account: records, hashes, the balance as their fold, fork proofs | runs, tested (JVM and Scala.js; the JDK's and Web Crypto's signatures check each other's) |
| A key a device, with its own limits; a lost device cut off | runs, tested (in the chain's rules; no app yet) |
| A community's node: an object an account, payments carried or returned, the node's signature on every record, its signed head with a proof for each account | runs, tested (in-memory cloud, real signatures; one JVM process with SQLite) |
| The app: an account opened by a key made on the device, payments, history, vouching, devices with limits, the node checked by the device itself; in English, Polish, Ukrainian and Russian | runs, tested (played against the node in each language; in a real browser against a local node) |
| The community governing itself: its own account, its holders setting members' limits by signed commands | runs, tested |
| Recovery when every device is lost: asked from a new device, confirmed by the community, in force after an announced week, cancelled by a device that still holds the account | runs, tested |
| The board: members' offers and wants by their own signed word; paying for an offer from it | runs, tested |
| Shared accounts of a family or a team, held by members' accounts with limits; a budget lent to a team; two signatures above an amount (proposed by one holder, confirmed by another) | runs, tested |
| Policies set at every level: a hub's operator bounds its communities; a community changes its own policy (joining, lines, private people: none / earn first / lent lines / a line after a record / lines for all, shared accounts, the recovery's wait); an account its holders, limits and two-holder threshold | runs, tested |
| To be paid by a link or a code ("Receive"); a shop of Okay Mall says it takes units and its storefront links the payment | runs, tested |
| Stamp cards: a shop's card, a stamp and a reward by a code the customer scans — not units, kept beside the chains | runs, tested |
| A shared account held by another shared account; taking a lent line back | designed |
| A hub of nodes: anyone creates a community by a call or from the app; each node's key made with it; accounts of different nodes apart | runs, tested; LIVE on Cloudflare Workers: https://okay-mutual.okay-effects.workers.dev (checked in a browser: two devices, a payment, the node verified) |
| Joining by request where it is not open: asked from the app, admitted or rejected in the community's office | runs, tested |
| A community's rules, accepted by every newcomer before an account is opened; private people who earn before they spend (an account that never goes below zero), or businesses only; a member's deals copied as a statement for the books | runs, tested |
| Trust lines between communities: each opens a line to the other, a member pays a member of the other; mirrored accounts, bounded by the receiver's trust; on one hub and between hubs | runs, tested |
| A payment through a third community; lines between different units | designed |

No community uses it yet. It has not been reviewed by a lawyer or a tax adviser; [what we think the law says](docs/legal.md)
is our reading, with the questions to ask one.

## For developers

The ledger is an effect, `Ledger`; a host answers it — a Durable Object's SQLite on Cloudflare, one JVM process,
memory in tests — and the rules are pure functions:

```scala
import okay.mutual.*

// a program says what it wants of the ledger
def deal[R[_]](using In[Ledger, R]) = Ledger.pay[R]("baker", "tailor", 4000, "a jacket altered", now, key = "k1")

// a host answers it
LedgerDurable.run(deal)
```

Scala 3 on [okay-serverless](https://github.com/okay-effects/okay-serverless). `sbt test` runs the suites on the JVM
and Scala.js. Released as `io.github.okay-effects::okay-mutual:0.1.0` at
`https://okay-effects.github.io/okay-mutual/maven`.

## Licence

Apache-2.0.
