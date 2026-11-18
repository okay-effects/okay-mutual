# okay-mutual

The ledger of a mutual credit network, on effects: members sell and buy for units they issue to each other. The
balances always sum to zero, every account has a floor (its credit line) and a ceiling, a member can lend part of its
line to a newcomer it vouches for, and the journal only grows. See [specs/mutual.md](specs/mutual.md).

```scala
import okay.mutual.*

// a program says what it wants of the ledger
def deal[R[_]](using In[Ledger, R]) = Ledger.pay[R]("baker", "tailor", 4000, "a hem", now)

// a host answers it: a Durable Object's SQLite on Cloudflare, okay-serverless' host, memory in tests
LedgerDurable.run(deal)
```

`LedgerObject` is the network's object (`Actor`) with the calls of `Api` under `/_mutual`; a host binds it and
decides who may call. `Measures` says how the network lives: velocity, who both sold and bought, stale balances.

`sbt test` runs the suites on the JVM and Scala.js.
