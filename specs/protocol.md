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

### 1. Keys

- A key pair is **ECDSA P-256**, hashes **SHA-256**. Why not Ed25519: P-256 is in every browser's WebCrypto for a
  decade, in every phone's secure hardware, and is what passkeys sign with — so a member's key can be unextractable
  on the device, and later a passkey (biometric, synced by the phone's maker) with no seed phrase to lose. A key
  carries its algorithm's name, so another can be added.
- An account has a **set of keys** (devices). The set is changed by a record signed by a key already in it.
- **Recovery** when every key is lost: the member's node, after a waiting period announced on the chain, adds a new
  key — the old keys can cancel during the wait. This is possible because members are known people, not anonymous
  addresses: the community that gave the credit can also restore the access. The node cannot spend for the member
  without this visible, delayed step.

### 2. An account's chain

Every account has its own chain of records. A record:

```
account   the account's id: the hash of its opening record (its node, its first key)
seq       1, 2, 3, …
prev      the hash of the record before it
kind      open | send | receive | bounce | limits | vouch | release | keys | recover | close
body      the kind's fields (amounts in hundredths of the node's unit)
at        the time the signer says
by, sig   the key that signed, the signature over the canonical bytes of all above
node sig  the node's signature: "I ordered this as seq N of this account and my rules allowed it"
```

- **A debit needs the member's key**: `send`, `vouch`, `keys`, `close`. Nobody, the node included, moves a member's
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

1. **Chains.** The record's canonical bytes and hash; signing and checking as an effect (`Signer`), WebCrypto and
   the JVM behind it; the fold; fork proofs. Pure and small: the kernel everything else stands on. Test vectors that
   a node in another language must reproduce.
2. **A node.** An object an account; `send` / `receive` / `bounce`; the node's co-signature, directory, head and
   inclusion proofs; its HTTP API and descriptor. The 0.1.0 suites re-read as the node's.
3. **The wallet.** Keys on the device, signing, the checks of §6; in okay-mall, in place of the present page.
4. **Trust lines.** A node as an account, transit messages and receipts, time-outs; two nodes in one test.
5. **Witnesses, recovery, paths, offline** — in the order experience asks for them.

## Open questions

1. Recovery's waiting period, and whether vouchers (not only the node) can restore a key.
2. The head's batching (every change, or every minute) against the cost on Cloudflare.
3. What a node must publish about itself for another to give it a line (legal person, rules, measures).
4. Leaving: a member moving to another node with its history; a node closing with debts on its lines.
5. Legal: each node's operator against e-money and payment-service rules — per country, a lawyer's.
