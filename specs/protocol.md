# Okay Mutual — the protocol (design, 2026-11-18)

How the network grows past one operator: anyone runs a node, a member holds its own keys in a browser or a phone,
every record is signed and can be checked by whoever holds it. Status: DESIGN, for the owner's review; the code of
0.1.0 (`specs/mutual.md`: one ledger, no signatures) is the prototype this replaces, its rules and measures kept.

## What mutual credit needs, and what it does not

A cryptocurrency guards one scarce asset, so the whole world must agree on one order of all payments: that is what
consensus, mining and stake are for, and what makes it slow and costly. Mutual credit has no such asset. A unit is a
promise between members of a community; the only thing that must never happen is a member's promises growing past
its limits unseen. That needs an order of ONE ACCOUNT'S records, never of the world's. So:

| Taken from blockchains | Left out, and why |
|---|---|
| a key is the identity; no signature, no spending | global consensus (PoW, PoS, BFT): nothing global to order |
| append-only hash-chained signed records | a coin, gas, fees to a chain: spam is stopped by membership and limits |
| a chain per account (Nano's block lattice, Holochain's source chains) | anonymity: a debt must have a known debtor |
| Merkle heads and light clients (Certificate Transparency) | a contract VM: the rules are few and fixed per version |
| fraud proofs: a lie is two signatures that contradict | everything on-chain: personal details stay off the chain |
| a deterministic state machine: the same records, the same balances, anywhere | |
| an open protocol and a reference node anyone runs | |

Trust is not removed — credit is trust. It is made **local, explicit and limited**: a member is trusted by its
community up to its credit line, a community by another up to a trust line. The damage any liar can do is bounded by
the limits others chose to give it. That, not consensus, is the security of the whole (Ripple's original design, the
Credit Commons, Sardex's circuits, correspondent banking).

## Beside money, not instead of it

The network runs parallel to ordinary money and gives a choice; it replaces nothing.

- **Prices stay in the national currency.** A unit is counted as one unit of it (1 unit = 1 zł in a Polish node), so
  nobody learns a new price list, and accounting and taxes are done as for any sale — in money, to the state.
- **A deal can be part units, part money.** A seller says for each offer what share it takes in units — all, a
  half, none. The money part is paid as it always was; the network neither holds nor moves money.
- **Nobody is obliged.** A member accepts units only up to its own ceiling and only for what it offered; it can
  stop, and it can leave by bringing its balance to zero (in goods and services, or in money if it owes).
- **Units are never sold or exchanged for money** inside the network. That is what keeps it a means of exchange
  among people who produce something, not a thing to speculate on.
- **What it adds** is room to trade when money is short but work, goods and needs are not: a firm between
  invoices, a person between jobs, a new business before its first revenue.

## The parts

```
 wallet (browser, phone) ── holds the member's key and its own chain; signs; checks the node
        │
 node (a community) ─────── admits members, sets limits, orders and co-signs its accounts' records,
        │                   publishes a signed head of everything it holds
 trust lines ────────────── a node is an account in another node's ledger, with limits as any account
        │
 witnesses ──────────────── anyone: keep nodes' heads, show two that contradict
```

### 1. Keys, devices and who may act

**A device makes its own key and never gives it away.** A phone, a laptop, a work computer: three keys, none ever
copied. A key pair is **ECDSA P-256**, hashes **SHA-256**. Why not Ed25519: P-256 is in every browser's WebCrypto for
a decade, in every phone's secure hardware, and is what passkeys sign with — so a key can be unextractable on the
device, and later a passkey (a fingerprint, no seed phrase to lose). A key carries its algorithm's name, so another
can be added.

**An account names its holders.** A holder is a key — or another account. Each holder has a grant:

```
holder    a key, or an account's id
name      "phone", "Anna", "accounting"          (kept beside the chain, not on it)
may       spend | manage                         manage: change holders and limits of spending
limit     at most this much a payment, and this much a period (a week, a month); none: the account's own limits
under     the holder that gave this grant
```

- **The hierarchy is who gave what.** A grant is given by a holder that may `manage`, and never gives more than the
  giver has. A holder taken off (`revoke`) goes with everything given under it: a lost phone is cut off from the
  laptop in one record, and the limited keys the phone had handed on go with it.
- **The holders that may `manage` are equals.** Any of them can take off any holder — also the one that once signed
  it in: the first phone is lost, the laptop it added takes it off and goes on, on its own. One that may manage
  always stays. (A thief with such a device can do the same: that is what recovery's delay and the node are for.)
- **A person** is an account held by its devices' keys: the first device opens it and may `manage`; the next device
  shows its new key (a QR code), an old one signs it in.
- **A family, a team, a company** is an account held by ACCOUNTS: the family's account names Anna's and Jan's
  accounts as holders, their child's with a limit of 50 a week. Anna signs for the family with whatever device she
  has in her hand — her own account says which keys are hers. A new phone is added once, to her account, and works
  everywhere she is a holder. A record signed this way says both: the key, and the account it acted for.
- **A budget** is an account with its own limits. A company gives a department a budget as a member vouches for a
  newcomer: it lends part of its own credit line (`vouch`), and takes it back (`release`). The department's holders
  are its people's accounts.
- **Two signatures** for large amounts: an account may say "above this amount, two holders" — the first signs, the
  node keeps the payment waiting, the second signs. One threshold, no more: enough for a company's cashier and
  director, small enough to explain.
- The chain of who-acts-for-whom is at most three steps (a key → a person → a department → a company), so every
  check ends.
- **A holder's limit is checked from the chain itself**: what that holder sent in the period is a sum over records,
  the same in the node and in the wallet.

**Recovery** when every key of a person is lost: holders the person named for it (family, a friend's account) or
the member's node add a new key — after a waiting period announced on the chain, during which any old key can
cancel. This is possible because members are known people, not anonymous addresses: the community that gave the
credit can also restore the access. Nobody spends for the member without this visible, delayed step.

### 2. An account's chain

Every account has its own chain of records. A record:

```
account   the account's id: the hash of its opening record (its node, its first key)
seq       1, 2, 3, …
prev      the hash of the record before it
kind      open | send | receive | bounce | limits | vouch | release | grant | revoke | recover | close
body      the kind's fields (amounts in hundredths of the node's unit)
at        the time the signer says
by, sig   the key that signed — and, when it acts for a holder account, that account and the place in its
          chain where the key is its holder — and the signature over the canonical bytes of all above;
          a second `by, sig` when the account asks two
node sig  the node's signature: "I ordered this as seq N of this account and my rules allowed it"
```

- **A debit needs a holder's key**: `send` by one that may spend; `vouch`, `grant`, `revoke`, `close` by one that
  may manage. Nobody, the node included, moves a member's
  credit without it.
- **A credit and a rule are the node's**: `receive`, `bounce`, `limits`, `recover`, signed by the node alone — a
  member need not be online to be paid, and credit policy is the community's.
- **The balance is a fold** of the chain: the same pure function in the node, in the wallet, in an auditor's script.
  The 0.1.0 rules stay as they are: floor `-(credit + vouched - lent)`, ceiling, vouching.
- **Details are not on the chain.** A record carries the hash of its memo; the memo, names and addresses are kept
  beside it and can be erased (GDPR) without breaking the chain.
- **A fork** — two records with the same `account` and `seq` — is the only way to spend the same room twice. Both are
  signed, so the two together are a proof anyone can check, against the member and against the node that co-signed
  both.

### 3. A payment

Inside a node, between accounts A and B:

1. A's wallet signs `send {to: B, amount, memo hash, key}` as A's next record and gives it to the node.
2. The node checks the signature, the sequence and A's floor, co-signs, appends. A is debited: this step alone is
   final for A.
3. The node appends `receive {from: A's record}` to B's chain if B's ceiling allows — else `bounce` to A's chain,
   which returns the amount.

Two steps, each on one chain, each atomic; between them the amount is "in flight" and both wallets see it. There is
no lock across accounts and no ledger-wide order: a node can keep every account in its own storage (on Cloudflare a
Durable Object an account) and grows by adding accounts, not by a bigger machine.

### 4. A node

- Anyone runs one: the reference node is this repository's code on okay-serverless — a Cloudflare Worker, or one JVM
  process with a directory. It describes itself at `/.well-known/okay-mutual.json`: its key, its unit, the protocol's
  version, its endpoints.
- It is a **community with its own rules**: who is admitted, credit lines, ceilings, fees in money, who governs.
  The protocol fixes the records and what makes them valid, not the policy.
- It must be **somebody**: a legal person answerable for its members and its taxes. The protocol does not check
  this; other nodes do, when they decide whether to open a trust line.
- **Its head**: at every change (batched) it signs `{number, time, Merkle root of all its accounts' heads, sum}`.
  A wallet asks for a proof that its own chain's head is under the root; the balances' sum (with the trust lines)
  is zero or the head is false. Two heads with the same number are a proof the node rewrote history.

### 5. Trust lines between nodes

Node X opens in its own ledger an account for node Y, with a floor and a ceiling as any account — and Y for X. That
pair is a trust line: how far each community lets the other owe it.

A payment from a (at X) to b (at Y):

1. a signs `send {to: b@Y, …}`; X appends it and credits Y's account at X (`receive`).
2. X sends Y a signed transit message with a's record. Y checks X's signature and its own limits for X, debits X's
   account at Y, appends `receive` to b.
3. Y answers with a signed receipt, which X keeps. No answer in time, or a refusal: X bounces the amount to a.

Both nodes hold the other's signatures for every step, so the line's balance is never one side's word. The risk a
community takes on another is exactly the line it chose; a node of invented members can cheat no further than the
lines honest nodes gave it — and they give none to a stranger.

- **Paths** (X → Z → Y when X and Y have no line) work the same way hop by hop (Ripple's routing, Interledger's
  hold-and-commit). Later; the records already allow it.
- **Units**: a line names its unit. Nodes with the same unit (PLN-priced) connect directly; a line between units
  carries a rate the two nodes agree on. Later.

### 6. The wallet

(What a person sees is called their ACCOUNT and "the app" — never a wallet, which is the word of another world,
docs/legal.md; `wallet` stays the name of the module and of this section.)

A web app (PWA), the same on a desktop and a phone:

- makes the key on the device (WebCrypto, unextractable), keeps the member's own chain;
- signs `send`; shows what is in flight;
- **checks the node**: every record of its chain (signatures, hashes, the fold), the proof that its head is in the
  node's signed head; keeps the heads it has seen and shouts when two contradict;
- works with any node that speaks the protocol — the mall's is one of many.

The node's code is Scala.js too, so a node can run inside a page (a demo, a family, a classroom); a node others rely
on has to be reachable, so it lives on a server.

**Offline** (a market stall with no signal): the payer signs a `send` and shows it as a QR code; the payee's wallet
checks the signature and takes the risk that the payer was already at its floor, up to a small amount the node sets.
Later.

### 7. Who checks whom

| Lie | Who sees it | With what |
|---|---|---|
| a payment the member did not make | the member's wallet, anyone | no valid signature of the member |
| a member spends the same room twice | its node (refuses); else anyone | two signed records with one `seq` |
| a node rewrites or hides a record | the member's wallet, a witness | a record or head it holds, signed by the node |
| a node shows different states to different people | witnesses comparing heads | two heads with one number |
| a node invents members and credit | the nodes with lines to it | their own limit: the loss is at most the line |
| a node denies what it owes on a line | the other node | the signed transit messages and receipts |

What no signature can show: that a member will honour its debt. That stays where it has always been — in knowing
the member, in vouching, in limits that grow with history.

## What changes in the code

| 0.1.0 | The protocol |
|---|---|
| `Account` with a balance, changed in place | an account's chain; the balance a fold |
| `Ledger.Pay` moves both balances in one step | `send` on one chain, `receive` or `bounce` on the other |
| one object, the whole network | an object an account; a node's directory and head |
| the operator records for the members | the member's key signs; the operator sets limits |
| the mall is the network | the mall runs a node and serves a wallet |

`Rules`, `Measures`, vouching, named payments (a key: paid once), the members' page's words stay.

## Stages (each tested before the next)

1. **Chains.** BUILT (module `chain`, `okay.mutual.chain`, on okay-scala alone). A record is a text — JSON — that is
   hashed and signed as it is and parsed by its reader from those same bytes, so no two encoders need to agree
   (`Record`, `Signed`); SHA-256 pure (`Sha256`); signing and checking as the effect `Signer`, the JDK's and Web
   Crypto's behind it; the fold and its rules (`Chain.step`, `State`, `Fault`); the checks that need signatures
   (`Check`: a record's signers, a whole chain, a fork's proof). Kinds: `open`, `send`, `receive`, `bounce`,
   `limits`, `grant`, `revoke`, `vouch`, `vouched`, `release`, `released`. `ChainSuite` (the rules, signatures
   faked) and `Laws` (one program every handler must satisfy, with a record signed by the JDK and one by Web Crypto
   kept as vectors) on the JVM and Scala.js. NOT in it, left for the node's stage: a holder that is an account, two
   signatures, recovery, `close`; the size of what the fold keeps (every payment's name, every send not bounced).
2. **A node.** BUILT (module `node`, on okay-serverless 0.2.0; `host`: one JVM process). `AccountObject`: an object
   an account — its chain, the node's signature on every record, a `send` or `vouch` carried to its receiver or
   returned (`bounce`, `released`), a flight tried again by the alarm, named payments paid once, a payment's memo
   beside the chain at both ends. `Directory`: the members, every account's head, the node's signed head and a
   proof for each account. `NodeApp`: the calls, CORS for any wallet. `NodeSuite` on `MemoryCloud` with real
   signatures. NOT in it: `release` of a lent line, a retried flight under test, rate limits on joining, the
   Worker's packaging, batching of the head.
3. **The wallet.** BUILT (module `wallet`, a program of okay-ui's screens; `site/`: the pages; `sbt site`). Its
   world as effects: `Net` (the node), `Device` (this device's key — in a page Web Crypto's unextractable key kept
   by IndexedDB). An account opened, a payment, history, vouching, devices with limits, the node checked (the chain
   folded again, every signature, the signed head, a head going back or forked), the community's office.
   `WalletSuite` plays it against the node in memory; checked in Chromium against a node on the host (two devices,
   a payment, a reload, the check, the key unextractable). English only; no admission screen where joining is not
   open; no QR scanning (the key is shown as a QR code and typed or pasted).
4. **Trust lines.** BUILT (sprint 2), made exact: at node X one account for each peer Y, of `line`, HELD BY Y'S
   NODE KEY, its credit X's trust in Y, its ceiling unbounded (`Peers.unbounded`: owing is no risk to the one who
   owes). The command `line {address, credit}`: the peer says who it is and signs the opening record of its account
   here (`POST …/api/line/open`; the same unit only), the account is opened, listed among the members under the
   peer's name, kept as `dir/line/<peer key>`. A payment to `<account>@<peer key>`: the sender's `send` on its own
   chain; this node's key signs a `send` on ITS account at the peer — an ordinary holder's record: the peer checks
   its credit, co-signs, carries it to its member; taken there (the send stays in the account's `sent`), the peer's
   account here is credited — else the amount goes back to its sender. The payment's name there is the hash of the
   sender's record: tried again, paid once. No "transit message" and no "receipt" as kinds of their own: the
   records the two nodes hold of each other's signatures ARE them. A peer on the same hub is reached through its
   objects, another over HTTPS (`Peers`). `NodeSuite`: one hub and two hubs — paid, mirrored, sums zero, over the
   peer's credit and a receiver at its ceiling bounced, no line, the way back; `WalletSuite`: the office's "Trust
   lines", "To another community". NOT in it: a path through a third node, lines between units, a line lent
   (`vouch`) across nodes, closing a line.
5. **Witnesses, recovery, paths, offline** — in the order experience asks for them.

## Anyone has a node

A node is DATA of a deployment, not its configuration. A deployment — a HUB — holds as many communities' nodes as
people make: `POST /api/nodes {slug, name, unit, join, credit, ceiling}` makes one (its key pair generated with it
and kept in its own `Directory` object, its setup token derived from the secret and said once), and its calls live
under `/n/<slug>/…`. The hub lists its communities (`GET /api/nodes`). Making is open (`HUB_OPEN=1`) or behind
`HUB_TOKEN`, limited per address a day and in all.

- **Three ways to have a node** (docs/run-a-node.md): create a community on a public hub from the wallet; deploy
  one's own hub to Cloudflare (`worker/`, `sbt dist`, `worker/deploy.sh`); run it on one's own machine (`host`).
- **What a hosted community's founder holds, and what the hub does**: the keys that may manage the community's own
  account are the founder's devices'; the node's key — which orders and co-signs every record — is the hub's. The
  hub's operator cannot spend for a member or govern a community; it could stop serving one, or sign two histories —
  which the members' wallets, checking the node, would show. Moving a node off a hub with its history: not built.
- **Accounts of different nodes are apart**: an account's object answers only its own node (`x-node`); a payment to
  an account of another node comes back. Trust lines (stage 4) are what will join them.
- **Joining where it is not open**: `POST …/api/accounts` keeps the signed first record as a request (202);
  `GET …/api/requests` says who waits; the commands `admit {id, credit, ceiling}` and `reject {id}`.
- **The wallet**: with no node in use it lists the hub's communities, takes an address or an inviting link
  (`?node=`), or creates a community; what it remembers is kept a node apart.
- BUILT (sprint 1): `Hub`, `Directory` a node, `NodeApp` by `/n/<slug>`; `NodeSuite` (isolation, limits, requests),
  `WalletSuite` (created, asked, admitted, rejected, another community); checked in Chromium against the host's jar
  and against the Worker. LIVE: https://okay-mutual.okay-effects.workers.dev, its demo community `demo` (open).

## Light by design (sprint 3; docs/legal.md)

What keeps the system out of the regulated kinds is what the software does, and two things a community chooses:

- **A community's rules** (`dir/rules`: the text and its hash; `GET …/api/rules`; the `rules` command): the
  contract among its members. `POST …/api/accounts` names the hash of the rules accepted; with rules in force and
  another hash, or none, no account and no request (409 `rules`). A node without rules asks for none. The wallet
  shows the rules before the form and sends the hash of what it showed; a founder writes them from a standard text
  (`Words.standardRules`, in each language). Rules changed later are not asked of those already members.
- **Whom it admits** (`NodeEnv.only`: `business` or any; said at the node's making, in its descriptor): a
  community of businesses refuses a personal account (409 `business`), and its wallet offers none.
- **A statement** (the wallet's History, "Copy as a statement"): a member's deals as CSV — a deal in units is a
  sale, booked and taxed as any other.
- Not in it: the acceptance recorded on the member's chain (it is in the request alone); terms for a hub's
  operator; anything a lawyer may add after reading docs/legal.md.

## Recovery and the board (sprint 5)

- **Recovery**, made exact (the section "Keys" said who and how long; this is what the records are): a new device
  asks (`POST …/api/accounts/<id>/recovery {key, sig}` — its signature of `recover:<id>:<key>` shows it holds the
  key; kept as `dir/recovery/<id>`); one who may manage the community confirms it knows who asks (the command
  `recover {account}`; `forget {account}` drops the asking); the node writes `recover {key, after}` on the
  account's chain — announced, `after` a week on (`NodeApp.waiting`); between the two any holder that may manage
  cancels by `revoke` of that key; from `after`, `recovered {key}` (the node's, asked for by anyone:
  `POST …/recovered`) makes the key a holder that may manage. One recovery at a time; the chain's rules, not the
  node's good will, say when it may be in force — a node that wrote `recovered` early would have signed a record
  every wallet rejects. Recovery by holders the member named (family, a friend) instead of the community: not built.
- **The board**: `dir/listing/<id>` — a member's offer or want (`kind`, `title`, `text`, `price` — 0: by agreement
  —, `share`: the per cent of the price taken in units); `GET …/api/board`; `POST …/api/board {command, key, sig}`
  — `list {…}` / `unlist {id}`, the text signed by a key that holds the account it names and may spend, fresh,
  taken once. No record of a chain: an entry is taken off without a trace. At most `Directory.own` a member. The
  app pays for an offer with the form filled from it (the units' part of the price); the money part is the two
  parties' own. Not built: a board across communities, search, matching.

## Who governs: nobody above, everyone their own

There is no administrator of the network, and none of a node either. The same four relations, at every size:

| Relation | Between | What it says |
|---|---|---|
| holding | a key or an account, and an account | who may act for it, within what limits (`grant`, `revoke`) |
| membership | an account and its node | the community orders its chain and sets its limits (`limits`) |
| lending a line | two accounts | one answers for the other with its own room (`vouch`): a newcomer, a department's budget |
| a trust line | two nodes | how far each community lets the other owe it |

A person governs their devices. A family or a company governs its members' access to its account. A community
governs its members' limits — through ITS OWN ACCOUNT, whose holders that may manage are its board, added and taken
off by the same records as a person's phone and laptop; a command to the node is signed by such a key. Communities
are equals: none is above another, each decides alone whom it trusts and how far. Every account has exactly one
home — the node that orders its chain — and that is the only hierarchy there is.

## Open questions

1. Recovery's waiting period; a holder account on another node (its chain must be read across nodes).
2. The head's batching (every change, or every minute) against the cost on Cloudflare.
3. What a node must publish about itself for another to give it a line (legal person, rules, measures).
4. Leaving: a member moving to another node with its history; a node closing with debts on its lines.
5. Legal: each node's operator against e-money and payment-service rules — per country, a lawyer's.
