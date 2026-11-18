# Run a node

A node is a community's computer: it admits members, keeps each account's chain, co-signs every record and publishes
a signed head of everything it holds. Anyone can run one. Read [how it works](how-it-works.md) first.

Before inviting anyone you do not know personally: a node's operator is answerable for its members and its taxes.
Settle the legal form and the rules on payment services in your country with a lawyer
([open questions](../specs/mutual.md#questions-for-a-lawyer-and-a-tax-adviser)).

## On one machine

Needs Java 21 or newer and [sbt](https://www.scala-sbt.org/).

```sh
git clone https://github.com/okay-effects/okay-mutual && cd okay-mutual
sbt site host/assembly                       # the site with the wallet, and the node's jar
NODE_NAME="Our Town" JOIN=open DEFAULT_CREDIT=10000 DEFAULT_CEILING=30000 java -jar target/okay-mutual-node.jar
```

Open http://127.0.0.1:8787/wallet/ — the wallet, talking to your node.

The first start writes two files into `node-data/`, readable by you alone:

- `node.key` — the node's key. Whoever has it can sign as your node. Back it up; never share it.
- `setup.token` — printed once at the first start. In the wallet choose **Set up this community** and enter it: that
  opens the community's own account with your device's key. From then on the token opens nothing.

Everything the node knows is in `node-data/okay.sqlite`. Back up the directory.

| Variable | Meaning | Default |
|---|---|---|
| `NODE_NAME` | the community's name, as wallets show it | `A community` |
| `NODE_UNIT` | the unit's name; a unit counts as one of the national currency | `PLN` |
| `JOIN` | `open`: anyone may open an account; anything else: only those you admit | admitted |
| `DEFAULT_CREDIT`, `DEFAULT_CEILING` | a new member's limits, in hundredths (`10000` is 100.00) | `0` |
| `NODE_DIR` | where the data is kept | `node-data` |
| `PORT`, `HOST` | where it listens | `8787`, `127.0.0.1` |
| `SITE` | the site's directory to serve | `target/site` |

To be reachable by others, put it behind a web server with HTTPS (a wallet's keys need a secure page) and set
`HOST=0.0.0.0` or proxy to it.

## Who governs the node

Nobody from outside, and no password. The community's own account is an account like any other: the devices whose
keys may *manage* it are the community's board. In the wallet, with that account in use:

- **Community** — the members, the sum of all balances (always zero), and each member's limits to set.
- **Devices** — add another board member's device, or take one off. The same screen a person uses for their phone
  and laptop.

A command to the node (setting a limit) is signed by such a key, is good for ten minutes and is taken once.

Where joining is not open, a member is admitted by a board member's command carrying the newcomer's own signed first
record. The wallet does not have a screen for this yet.

## What is not there yet

- Trust lines to other nodes: designed ([protocol](../specs/protocol.md)), not built. A node today is one community.
- A Cloudflare Worker's entry: the node's code runs there (it is tested on the same in-memory cloud), the packaging
  is not written.
- Family and company accounts held by other accounts, two signatures, recovery of a lost key by the node.
- Backups beyond copying the directory; rate limits on opening accounts where joining is open.
