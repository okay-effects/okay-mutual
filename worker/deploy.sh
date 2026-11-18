#!/bin/bash
# THE HUB DEPLOYED to Cloudflare Workers (docs/run-a-node.md): built (`sbt dist`), then `wrangler deploy`. The
# account's API token and id come from the environment, or from an .env beside this script or in the repository's
# root (gitignored, mode 600): CLOUDFLARE_API_TOKEN=…, CLOUDFLARE_ACCOUNT_ID=…
set -euo pipefail
cd "$(dirname "$0")"
for env in .env ../.env; do [ -f "$env" ] && { set -a; . "$env"; set +a; }; done
: "${CLOUDFLARE_API_TOKEN:?no CLOUDFLARE_API_TOKEN: put it in worker/.env (docs/run-a-node.md)}"
(cd .. && sbt -batch dist)
[ -d node_modules ] || npm install --no-fund --no-audit
npx wrangler deploy
