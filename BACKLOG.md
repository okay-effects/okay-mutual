# BACKLOG — okay-mutual

What is not done, in the order it should be. Each item: what, why, done-when. An item moves to SPRINT.md when it
is started — its plan written there before the work. Written 2026-11-18 after sprint 9; the operator's position
(docs/organisation.md): an author of open software that people use of their own will.

## Decisions that are the operator's (nothing below them starts until said)

- **D1. The public hub is HOSTING** (decided 2026-11-20). The operator hosts the communities made on it and
  answers for that as a host: their data, a processing agreement, a privacy notice, backups, a way out. So items
  2, 3 and 6 are owed, not optional. Open: under whose name the hosting is offered (the operator's own, his
  business's) — a question for the lawyer and the accountant.
- **D2. Private people may be given credit on the public hub** (decided 2026-11-20): every way with private
  people stays allowed there (`HUB_PERSONAL` not set), no bound of the hub's; each community chooses, and one that
  gives people credit takes on the consumer-credit question (docs/legal.md).
- **D3. A community without a legal person**: rewrite docs/legal.md and docs/organisation.md to three steps — an
  informal group for a trial among people who know each other, an ordinary association, a legal person — in four
  languages, with the question of a civil-law partnership added for the lawyer. Now the documents recommend a
  legal person from the first real community.
- **D4. The lawyer**: one opinion on the questions of docs/legal.md (and of docs/organisation.md if a foundation is
  wanted) before a community gives real credit lines to people its founder does not know.
- **D5. Money without a grant** (said in the conversation, not in a document): Okay Mall's paid plans, a first
  community set up as a service, paid hosting, donations through a fiscal host, work paid in units. A grant: NLnet
  takes individuals by country of residence, next deadline 2027-01-03; its form asks to disclose generative AI in
  the proposal and in the code — the proposal has to be the operator's own words.

## Before the first real members (in this order)

1. DONE (sprint 10, 2026-11-20): the office's warning and a second manager; the members' election where
   joining is by admission. Was: **A founder's key lost locks the community.** A community's own account has nobody to confirm its recovery:
   `demo-hill` and `demo-river` are already governed by nobody. How: making a community asks for a second manager
   — a second device or another member's key — before it admits anyone; the office warns while there is one
   manager; perhaps recovery of a community's account by a quorum of its members. Done when: a suite shows a
   community that survives its founder's phone.
2. **A community's data out and in.** No export: an operator's mistake or a platform's fault loses every account.
   How: a node's whole state (chains, names, memos, the directory, the policy — never its secret unless asked by
   the setup token) as one file; the same file taken by another hub or one's own node, the node's key with it.
   This is also "leaving a hub" (protocol: "Anyone has a node"). Done when: a community moved between two hubs in
   a test with every chain still checking.
3. **Leaving and erasing.** An account cannot be closed and a member's name and memos cannot be erased, though
   the documents say they can and data-protection law asks it. How: `close` (a chain's last record, balance zero
   or settled by the community's decision), the name and memos beside the chain erased, the listing removed.
   Done when: both suites; docs say what is erased and what stays (amounts and fingerprints).
4. **The key on an iPhone.** Safari may erase a site's storage after about a week without use unless the site is
   on the home screen — then the device's key is gone and the member needs recovery. Not verified. How: the
   operator's test (open in Safari, open an account, leave it a week); if so: the app asks to be installed, says
   why, and asks for a second device early. Done when: the test's result is written down and the app acts on it.
5. **Knowing one was paid.** A member learns of a payment only by opening the app. How first: the "Receive"
   screen and the home refresh by themselves; later web push. Done when: a seller at a counter sees a payment
   arrive without touching the phone.
6. **A privacy notice and terms** for the app and the hub, four languages; a model processing agreement between
   a community and a host. None exists. Depends on D1.

## To have something to show

7. **A clean demonstration.** `demo` is cluttered by the checks (Anna the tailor ×3, Marta, Marta's home, Corner
   cafe, Regular, Client of the shop) and has no office; `demo-hill` and `demo-river` have lost their founders'
   keys. How: a new demo community with an office whose key is kept, earn-first, with a card and a board; the old
   ones removed (needs item 3 or an operator's erase). In okay-mall: a demo shop that takes units and has a card.
8. **On real phones.** Two phones: a code on one screen scanned by the other's camera — a payment by link, a
   stamp, a reward; installed to the home screen; Android and iPhone. Never tried on devices.
9. **One page for shops**, Polish first: a stamp card for your shop in five minutes; then units.
10. **The live check as a script of ours.** The browser check is a Node/Playwright script outside the repository
    (/tmp, lost on reboot). The operator's rule: scripts are scala-cli. Done when: `scripts/check-live.scala`
    runs the two-phone path against a hub.

## Okay Mall (okay-mall's own board holds the detail)

11. An order marked paid by the mall when its whole price is seen in units; the units' part noted on the payment.
12. The same on a booking's page.
13. The node's records checked by their signatures in the mall — needs the module `chain` released as a library.
14. A shop's stamp card shown on its storefront and given from the shop's panel.
15. okay-shop: 0.1.0 is published from the branch `release-0.1`; its main is ahead, on okay-serverless 0.2.0. A
    release from main moves the mall to 0.2.0 — a step of its own, the operator's to call.

## The protocol, later

- A payment through a third community (a path); lines between different units (a rate); closing a line; a
  flight across a line retried by the alarm, under test.
- `release` of a lent line from the app; a shared account held by another shared account.
- The rules' acceptance recorded on the member's chain; members asked again when the rules change.
- Recovery by holders the member named (family, a friend) instead of the community.
- A level between a hub and a community (a federation's policy); a member's own narrower policy.
- Cards: a team's card; a shop's list of its card holders; cards across communities.
- A shared account on the board.
- `earned` counted from the chain for accounts older than sprint 7.
- The hub's root answering `/.well-known/okay-mutual.json` with CORS (a console error in the app, harmless).

## Housekeeping

- Closure for the app's script (2.7 MB).
- QR scanning inside the app for adding a device.
- A domain of its own instead of workers.dev; the trademark.
- Watching the hub: an alert when it is down or its errors grow.
- An independent security review of the chain's rules and the node.
- Batching of the head; the release of `chain`, `node` and the app's module as libraries (RELEASING.md).

## The organisation (only if wanted; docs/organisation.md, docs/statute.md)

- Nothing is registered or decided. If a foundation: first the lawyer's answers on § 9 (paid statutory activity or
  business), the founder as contractor, the council's make-up — "Where this draft is weak" in the statute.
- A page "Support the project" (a fiscal host for donations) once D5 is decided.
