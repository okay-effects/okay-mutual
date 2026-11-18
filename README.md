# Okay Mutual

An open network of mutual credit: people and businesses sell to and buy from each other for units they issue to one
another, when money is short but work, goods and needs are not. It runs beside ordinary money and replaces nothing.

There is no coin and nothing to invest in. Every member holds their own keys, every record is signed, anyone can
run a community's node, and communities connect by trust lines they choose themselves.

- **[The idea](docs/idea.md)** — the problem, what mutual credit is, where it has worked for ninety years, what
  this is not.
- **[How it works](docs/how-it-works.md)** — an account, keys and devices, a payment, vouching, families and
  companies, nodes, trust lines, and what protects a member at each step.
- **[Questions](docs/faq.md)** — is it a cryptocurrency, does it replace money, taxes, what if someone does not
  pay.
- **[The protocol](specs/protocol.md)** — the design for those who build: records, chains, nodes, trust lines.
- **[The prototype's spec](specs/mutual.md)** — the rules, the measures of a network's health, what is tested
  first.

## What runs today, and what is designed

| | Status |
|---|---|
| The rules: credit line, ceiling, vouching, corrections, payments paid once | runs, tested (JVM and Scala.js) |
| A single community's ledger on a small cloud service or one machine | runs, tested |
| The measures of a network's health (velocity, who both sells and buys, stale balances) | runs, tested |
| A member's page: sign-in by an emailed link, balance, history, payment | built in a host application (not public), not yet deployed |
| Members' own keys, a signed chain per account | designed ([protocol](specs/protocol.md), stage 1) |
| Device keys, family and company accounts, budgets | designed |
| Nodes anyone runs, signed summaries, checking by the member's app | designed |
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
