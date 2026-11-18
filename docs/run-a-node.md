**English** · [Русский](ru/run-a-node.md) · [Українська](uk/run-a-node.md) · [Polski](pl/run-a-node.md)

# Have a node for your community

A node is a community's computer: it admits members, keeps each account's chain, co-signs every record and publishes
a signed head of everything it holds. Read [how it works](how-it-works.md) first.

There are three ways to have one, from a minute to an evening. All three run the same open code.

Before inviting anyone you do not know personally: whoever runs a community is answerable for its members and its
taxes. Settle the legal form and the rules on payment services in your country with a lawyer
([open questions](../specs/mutual.md#questions-for-a-lawyer-and-a-tax-adviser)).

## 1. Create a community on a hub — a minute, no server

A hub is one deployment that holds many communities' nodes. Ours is public:

1. Open the wallet: https://okay-mutual.okay-effects.workers.dev/wallet/
2. Choose **Create a community**: its name, its place in the address, its unit, how one joins, a newcomer's limits.
3. That is all. Your device's key has opened the community's own account: you are its founder.
4. **Invite** shows a link. Whoever opens it is in the wallet at your community and can ask to join.
5. **Community** is your office: who asks to join (admit with limits, or reject), the members, their limits.

What you hold and what the hub holds: the community's key — the one that may admit members and set limits — is on
your device and nowhere else. The node's own key, which co-signs every record, is kept by the hub. So the hub's
operator cannot spend for anyone or govern your community, but could stop serving it. Every member's wallet keeps
checking the node, and would show it if records were changed. If that is not enough, run your own hub.

A public hub limits how many communities one address may create a day.

## 2. Your own hub on Cloudflare — half an hour, a free account is enough

Needs a Cloudflare account, Java 21 or newer, [sbt](https://www.scala-sbt.org/) and Node.js.

```sh
git clone https://github.com/okay-effects/okay-mutual && cd okay-mutual
printf 'CLOUDFLARE_API_TOKEN=…\nCLOUDFLARE_ACCOUNT_ID=…\n' > .env && chmod 600 .env
./worker/deploy.sh
```

The token needs the permission to edit Workers. The script builds the hub and the site and deploys them; it prints
your address, `https://okay-mutual.<your-account>.workers.dev`. Open `/wallet/` there and create your community.

There is no secret to set: each node's key pair is made when the node is, inside Cloudflare, and never leaves it.
Settings are in `worker/wrangler.toml`:

| Variable | Meaning | Default |
|---|---|---|
| `HUB_OPEN` | `1`: anyone may create a community on your hub; anything else: only who has `HUB_TOKEN` | `1` |
| `HUB_PER_DAY` | how many communities one address may create a day | `3` |
| `HUB_MAX` | how many communities there may be in all | `500` |

To keep the hub for yourself, set `HUB_OPEN = ""`, run `npx wrangler secret put HUB_TOKEN` in `worker/`, and
create communities with a request that carries the header `x-hub-token`.

## 3. On your own machine — everything in your hands

Needs Java 21 or newer and sbt.

```sh
git clone https://github.com/okay-effects/okay-mutual && cd okay-mutual
sbt site host/assembly                       # the site with the wallet, and the hub's jar
java -jar target/okay-mutual-node.jar
```

Open http://127.0.0.1:8787/wallet/ and create a community.

Everything the hub knows — every node's key included — is in `node-data/okay.sqlite`. Back up the directory, and
keep it to yourself: whoever has it can sign as your nodes.

| Variable | Meaning | Default |
|---|---|---|
| `NODE_DIR` | where the data is kept | `node-data` |
| `PORT`, `HOST` | where it listens | `8787`, `127.0.0.1` |
| `HUB_OPEN`, `HUB_TOKEN`, `HUB_PER_DAY`, `HUB_MAX` | as above | `1`, none, `20`, `500` |
| `SITE` | the site's directory to serve | `target/site` |

To be reachable by others, put it behind a web server with HTTPS (a wallet's keys need a secure page) and set
`HOST=0.0.0.0` or proxy to it.

## Who governs a community

Nobody from outside, and no password. The community's own account is an account like any other: the devices whose
keys may *manage* it are the community's board. In the wallet, with that account in use:

- **Community** — who asks to join, the members, the sum of all balances (always zero), each member's limits.
- **Devices** — add another board member's device, or take one off. The same screen a person uses for their phone
  and laptop.

A command to the node (admitting a member, setting a limit) is signed by such a key, is good for ten minutes and is
taken once.

## What is not there yet

- Trust lines between communities: designed ([protocol](../specs/protocol.md)), not built. A payment to a member of
  another community comes back.
- Moving a community from one hub to another with its history.
- Family and company accounts held by other accounts, two signatures, recovery of a lost key by the node.
- Terms of use for a hub's operator; backups beyond what the platform or your own copies give.
