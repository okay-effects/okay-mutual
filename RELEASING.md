# Releasing

Releases are a Maven repository on GitHub Pages: the `gh-pages` branch, its `maven/` directory, served at
https://okay-effects.github.io/okay-mutual/maven. While nobody but us uses it, a change is REPUBLISHED under the
current number (0.1.0), the tag moved; a new number only when someone else depends on it.

1. Everything green with no local copies: `sbt -Dsbt.ivy.home=$(mktemp -d) clean test`.
2. `ThisBuild / version` without `-SNAPSHOT`; committed, tagged (`git tag -f v0.1.0`).
3. `rm -rf target/pages-maven && sbt publish` (both platforms).
4. Onto `gh-pages`, under `maven/`:

   ```sh
   git worktree add /tmp/okay-mutual-pages gh-pages   # the first time: git worktree add --orphan -b gh-pages …
   mkdir -p /tmp/okay-mutual-pages/maven && rsync -ac target/pages-maven/ /tmp/okay-mutual-pages/maven/
   touch /tmp/okay-mutual-pages/.nojekyll
   git -C /tmp/okay-mutual-pages add -A && git -C /tmp/okay-mutual-pages commit -m "release 0.1.0"
   git -C /tmp/okay-mutual-pages push origin gh-pages && git worktree remove /tmp/okay-mutual-pages
   git push -f origin v0.1.0
   ```

5. A user that had the old jars clears them:
   `rm -rf ~/Library/Caches/Coursier/v1/https/okay-effects.github.io/okay-mutual`.

## The site

https://okay-effects.github.io/okay-mutual/ — the landing and the wallet, on the same `gh-pages` branch, beside
`maven/`:

```sh
sbt site                                              # target/site
git worktree add /tmp/okay-mutual-pages gh-pages
rsync -ac --delete --exclude maven --exclude .git --exclude .nojekyll target/site/ /tmp/okay-mutual-pages/
git -C /tmp/okay-mutual-pages add -A && git -C /tmp/okay-mutual-pages commit -m "the site"
git -C /tmp/okay-mutual-pages push origin gh-pages && git worktree remove /tmp/okay-mutual-pages
```

The wallet published there is served by no node: it asks for a node's address (or takes the one in its page's
`<meta name="okay-mutual-node">`). A node's own host serves the same site beside its calls.
