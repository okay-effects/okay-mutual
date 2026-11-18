# AGENTS.md — okay-mutual

Read `specs/mutual.md` first: the spec is written before the code and kept true after it.

- The ledger of a mutual credit network on effects: `Ledger` (accounts, limits, payments, vouching, the journal),
  handled onto okay-serverless' durable state (`LedgerDurable`), `Memory` in tests. `Rules` and `Measures` are pure.
- `specs/protocol.md` is the design of the open network (signed chains, nodes, trust lines, the wallet), built in
  stages; stage 1 is the module `chain` (`okay.mutual.chain`), on okay-scala alone so that a wallet's page links
  nothing else. The module `mutual` is the 0.1.0 prototype (one ledger, no signatures) until the node's stage.
- A library: a host mounts `LedgerObject` and decides who may call it. Members' pages, sign-in, billing and a host's
  words live in the host (first okay-mall), never here.
- The invariant every change keeps: the balances sum to zero; both limits hold; the journal only grows.
- Tests on both platforms (`sbt test`); test data is fictional.
- Scala 3.3, lines at most 120 wide, English in code and docs. Scripts are scala-cli, never Python.
- The plan: SPRINT.md (what is being done, its plan written before the work) and BACKLOG.md (what is not done,
  in order; the operator's open decisions first).
- Commits: plain messages, no attribution trailers.
