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

## Later (BACKLOG)

- A payment through a third community (a path); lines between different units (a rate); a line lent across
  nodes; closing a line; a flight across a line retried by the alarm under test.
- The demo community `demo` on the public hub is not set up (no community account): its setup token is in the
  operator's `.env` (`DEMO_SETUP_TOKEN`); whoever sets it up governs it. Demo members were made by the checks.
- A shop of Okay Mall taking units: its community and account named in its config, shown on its pages.
- Trust lines between nodes (protocol stage 4).
- A holder that is an account (families, companies), two signatures, recovery by the node.
- `release` of a lent line; a flight's retry under test; batching of the head.
- Moving a node from a hub to its own deployment with its history (export, import).
- Closure for the wallet's script (2.7 MB); QR scanning for adding a device.
- A lawyer's and a tax adviser's review; the terms a hub's operator needs.
