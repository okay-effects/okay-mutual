**English** · [Русский](ru/processing.md) · [Українська](uk/processing.md) · [Polski](pl/processing.md)

# Data processing agreement (model) — a community and the hub's operator

A model under art. 28 of the GDPR, written by engineers for a lawyer to check; not legal advice. Everything in
square brackets is to be filled in. It is accepted on a community's behalf by whoever creates the community on
the public hub ([the terms](terms.md), point 2.1).

## 1. The parties

- **The controller**: the community [its name, its address on the hub; its legal person if it has one; otherwise
  the persons who manage it], acting through those who hold its own account's keys.
- **The processor**: [the operator's name, address and e-mail], the operator of the public hub.

## 2. Subject, nature, purpose, duration

2.1. The processor hosts the controller's node of Okay Mutual: stores, computes and answers what the software does
with the controller's data, for the purpose of keeping the controller's members' account book.

2.2. Duration: while the community keeps its node on the hub, and until its data is deleted under point 9.

## 3. Data and data subjects

- **Data subjects**: the controller's members, those who ask to join, those who ask an account back.
- **Data**: names as given; kinds of account; device public keys; account records (amounts, times, accounts on the
  other side, keys, fingerprints); what payments are for; board entries; stamp cards and stamps; requests and
  votes; the community's rules and policy. No special categories are expected ([the terms](terms.md), 3.3).

## 4. Instructions

The controller's documented instructions are: the software as published (its open source code at the version the
hub runs), and the commands signed by the controller's managers. The processor does nothing else with the data,
unless the law requires it — then it tells the controller first, if the law allows.

## 5. Confidentiality

Only the operator and persons bound to confidentiality have access to the hub's account and to backups.

## 6. Security (art. 32)

The processor keeps at least the measures in the annex, and improves them as the state of the art moves.

## 7. Sub-processors

The controller authorises the sub-processors in the annex. The processor tells the controller of any new one
[30 days] ahead; the controller may object and leave (point 9). The processor binds each to the same duties.

## 8. Assistance

The processor helps the controller answer data subjects and meet its duties (security, breaches, impact
assessments). Much of it the software does: access and checking (*Check the node*), a copy (*History*, the
community's file), erasure (closing an account).

## 9. At the end

When the community leaves or ends: the processor gives the controller its file on request (the controller can also
take it itself at any time), deletes the community's data from the hub within [30 days], and lets its backups
expire within [30 days].

## 10. Breaches

The processor tells the controller of a personal data breach without undue delay, and at the latest within [48
hours] of learning of it, with what it knows.

## 11. Information and audits

The processor gives the controller what it needs to show this agreement is kept, and allows inspections that are
reasonable in scope, time and cost.

## 12. Liability; the rest

Liability as the GDPR sets it. This agreement goes with [the terms](terms.md); where they differ on personal data,
this agreement prevails. Polish law.

## Annex A. Measures

- Every record of an account is signed by its holder's device and by the node; anyone can check a chain, and a
  changed record is seen. The hub holds no member's private key.
- Connections are encrypted (HTTPS). Each account is kept in its own isolated storage object.
- The operator's access to the hub's account is protected by [a strong password and a second factor].
- Backups are taken [daily] by the operator, kept [encrypted / with access limited to the operator] for [30 days],
  and their restoring is tested.
- Erasure: closing an account erases what is erased at once (see [the privacy notice](privacy.md)).

## Annex B. Sub-processors

| Who | What | Where |
|---|---|---|
| Cloudflare, Inc. | runs the hub: Workers, Durable Objects | global network; [DPA and transfer basis] |
| GitHub, Inc. | serves the site and the app's page | United States; [DPA and transfer basis] |
