# Okay Mutual

An open network of mutual credit: people and businesses sell to and buy from each other for units they issue to one
another, when money is short but work, goods and needs are not. It runs beside ordinary money and replaces nothing.

There is no coin and nothing to invest in. Every member holds their own keys, every record is signed, anyone can
run a community's node, and communities connect by trust lines they choose themselves.

Читать по-русски: [идея](docs/ru/idea.md), [как это работает](docs/ru/how-it-works.md), [вопросы](docs/ru/faq.md),
[запуск узла](docs/ru/run-a-node.md) · Українською: [ідея](docs/uk/idea.md), [як це працює](docs/uk/how-it-works.md),
[запитання](docs/uk/faq.md), [запуск вузла](docs/uk/run-a-node.md) · Po polsku: [idea](docs/pl/idea.md),
[jak to działa](docs/pl/how-it-works.md), [pytania](docs/pl/faq.md), [uruchomienie węzła](docs/pl/run-a-node.md)

- **[The idea](docs/idea.md)** — the problem, what mutual credit is, where it has worked for ninety years, what
  this is not.
- **[How it works](docs/how-it-works.md)** — an account, keys and devices, a payment, vouching, families and
  companies, nodes, trust lines, and what protects a member at each step.
- **[Questions](docs/faq.md)** — is it a cryptocurrency, does it replace money, taxes, what if someone does not
  pay.
- **[Run a node](docs/run-a-node.md)** — a community's node on one machine, and who governs it.
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
| The wallet: an account opened by a key made on the device, payments, history, vouching, devices with limits, the node checked by the device itself; in English, Polish, Ukrainian and Russian | runs, tested (played against the node in each language; in a real browser against a local node) |
| The community governing itself: its own account, its holders setting members' limits by signed commands | runs, tested |
| Family and company accounts held by other accounts, budgets, two signatures, recovery | designed |
| A node on Cloudflare Workers | its code runs on the in-memory cloud; the Worker's packaging is not written |
| Trust lines between communities | designed |

No community uses it yet. It has not been reviewed by a lawyer or a tax adviser.

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
