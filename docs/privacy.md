**English** · [Русский](ru/privacy.md) · [Українська](uk/privacy.md) · [Polski](pl/privacy.md)

# Privacy notice: the Okay Mutual app and the public hub

A draft, written by engineers for a lawyer to check; not legal advice. Everything in square brackets is to be
filled in. It covers the app at okay-effects.github.io/okay-mutual and the public hub at
okay-mutual.okay-effects.workers.dev. A community on its own node writes its own notice; this one can be its model.

## Who is responsible for what

- **Your community is the controller** of what it keeps about its members: it decides who joins, on what rules,
  with what limits. It acts through those who manage it (its founders, its managers). Ask them first.
- **The hub's operator is the processor** for every community on the public hub: [the operator's name, address
  and e-mail]. The operator keeps the machine and processes the communities' data only as the software and the
  communities' signed commands say ([the processing agreement](processing.md)), and keeps backups.
- **The operator is the controller** only of what the hub and the site need for themselves: technical logs and
  protection against abuse (below).

## What is kept, and why

| What | Where | Why | Basis |
|---|---|---|---|
| The name you give, the kind of account (personal, business, family, team) | your community's node | others see whom they deal with | your agreement with your community (art. 6(1)(b) GDPR) |
| Your account's records: amounts, times, the accounts on the other side, the keys that signed, fingerprints of what payments were for | your community's node | the account book itself: your balance is computed from them; every member can check them | the same |
| What a payment is for, as the payer wrote it | beside the record, at both ends | your statement, your bookkeeping | the same |
| Your board entries, stamp cards and stamps | your community's node | what you chose to publish to your community | the same |
| A request to join, a request to get your account back, a vote for a manager | your community's node | until your community decides | the same |
| Your device's public key | your community's node | to know a record is yours | the same |
| Your device's private key | **only in your browser**, unreadable even by the app | to sign | — (never leaves the device) |
| The app's settings: which accounts this device holds, the community's address, the language | only in your browser | the app remembers you | — (never sent anywhere) |
| IP addresses and request logs | Cloudflare (the hub), GitHub (the site) | delivery, security, abuse | legitimate interest (art. 6(1)(f)) |
| Backups of whole communities | the operator's own storage | to restore a community after a fault | the processor's duty to the communities; legitimate interest |

No cookies, no analytics, no advertising, no profiling, no automated decisions about you. Your name and records are
not sold or given to anyone outside your community, except the providers below and where the law requires it.

## Who else handles it

- **Cloudflare, Inc.** runs the hub (Workers, Durable Objects) — sub-processor of the operator.
- **GitHub, Inc.** serves the site and the app's page (GitHub Pages) — sees the addresses of those who open it.

Both are US companies; data may be processed outside the European Economic Area, under the EU–US Data Privacy
Framework or standard contractual clauses [to be checked for each].

## How long

- While your account is open: as above.
- **When your account is closed** (by you, or by your community for one who left): your name, what your payments
  were for, your board entries, your cards and stamps, your requests and votes are erased at once. **What stays**:
  your account's records of amounts, times, keys and fingerprints — without your name. Every other member's records
  and the community's signed head rest on them; on their own they say who you are only to someone who knew it
  already. The other side of a deal keeps its own record of it, with what it was for: that is their bookkeeping.
- Backups: [up to 30 days], then replaced.
- When a community leaves the hub or ends: its data is deleted from the hub within [30 days]; its backups expire.

## Your rights

You may ask for access to your data, its correction, its erasure, a restriction of its use, a copy in a common
format, and object to its use; and you may complain to the President of the Personal Data Protection Office
(Prezes Urzędu Ochrony Danych Osobowych, uodo.gov.pl) or the authority of your country.

Much of this the app does itself: it shows your records and checks them (*Check the node*), gives your statement
as a table (*History → Copy the statement*), and closes your account and erases what is erased (*Devices → Close
the account*). A name is changed by your community. For anything else, ask your community; if it does not answer,
ask the operator.

## Changes

A change of this notice is published here [30 days] before it applies.
