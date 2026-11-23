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
the account*). You change your name yourself (*Devices → Change the name*). For anything else, ask your community; if it does not answer,
ask the operator.

## Changes

A change of this notice is published here [30 days] before it applies.

## Where these drafts are weak

Found on a first reading of the three drafts (this notice, [the terms](terms.md), [the processing
agreement](processing.md)) against Polish practice — by us, not by a lawyer; each to be confirmed or dismissed:

1. **Who the controller is.** A community without a legal person, "acting through those who hold its keys", is a
   weak controller, and the other party to the processing agreement is unclear; the person who creates a community
   declaring they may accept for it does not settle whether they can represent the group.
2. **"The software as published" as the instructions.** The operator chooses the version the hub runs, so it
   decides the means of processing — which could make it a controller; an update changes the instructions by one
   side.
3. **Roles mixed.** Backups are named a processor's duty *and* a legitimate interest; "if your community does not
   answer, ask the operator" gives a processor a duty it does not have.
4. **What stays after closing** is pseudonymous, not anonymous: still personal data. Keeping it needs an exception
   of art. 17(3) named.
5. **What a notice must say and this one lacks**: the community's own identity and contact, how long logs are kept,
   whether giving the data is a contractual requirement, how to get a copy of the transfer safeguards, where data
   about the other side of a deal comes from (art. 14).
6. **The terms and consumers.** "As is", no warranty and a vague cap on liability may be unfair terms toward a
   community that is a consumer; the software's licence does not cover the hosting service. Changing the terms
   without listed reasons and courts of the operator's seat are risky toward consumers.
7. **The terms and e-services law**: no complaints procedure, no technical requirements; the Digital Services
   Act's rules for hosts (notice and action, a statement of reasons for a suspension) are missing.
8. **The processing agreement and art. 28(3–4)**: nothing on keeping data where the law requires it, nothing on
   transfers outside the EEA, the processor's full liability for its sub-processors not said, audits limited to
   "reasonable" ones, objecting to a sub-processor leaves only leaving.
9. **Transfers**: no mechanism named for Cloudflare's global network and GitHub — the Data Privacy Framework or
   standard clauses are still "to be checked".
10. **Special categories**: forbidden in the terms but not preventable by the software, and the agreement says
    nothing of what happens if they appear.
