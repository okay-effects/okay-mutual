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
- [ ] **publish** — the site on Pages pointing at the public hub; README's table; the site's page for okay-effects
  (its section on okay-effects.github.io).
- [ ] **mall** — Okay Mall names Okay Mutual: its landing's "Also from us" links the site (`MALL_LINKS`); the
  prototype branch `mutual` (the 0.1.0 ledger inside the mall) is superseded and left unmerged; specs/mall.md says
  so. Deployed.

## Later (BACKLOG)

- Trust lines between nodes (protocol stage 4).
- A holder that is an account (families, companies), two signatures, recovery by the node.
- `release` of a lent line; a flight's retry under test; batching of the head.
- Moving a node from a hub to its own deployment with its history (export, import).
- Closure for the wallet's script (2.7 MB); QR scanning for adding a device.
- A lawyer's and a tax adviser's review; the terms a hub's operator needs.
