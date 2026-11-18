**English** · [Русский](ru/how-it-works.md) · [Українська](uk/how-it-works.md) · [Polski](pl/how-it-works.md)

# How it works

Read [the idea](idea.md) first. This page follows a member through the system and says, at each step, what protects
them. The exact formats are in [the protocol](../specs/protocol.md). Parts marked *(designed)* are specified and not
yet built; the [README](../README.md) says what runs today.

## Your account

You join a **node** — a community: a town's businesses, a trade, a cooperative. The community decides whom it
admits and gives you two limits:

- a **credit line**: how far below zero you may go. A newcomer's is small or zero;
- a **ceiling**: how far above zero you may rise before you must spend.

Your balance starts at zero. Amounts are counted in units; a unit counts as one unit of the national currency.

## Your keys

When you open the app, your phone makes a **key**: a secret that never leaves the device, and a public half that
identifies it. To pay, your phone signs the payment with its key. Without that signature nothing leaves your
account — the node itself cannot spend for you.

**Each device has its own key.** You do not copy a secret from the phone to the laptop. The laptop makes its own
key and shows it as a QR code; your phone signs "this key is mine too". If the phone is lost, the laptop signs
"that key is no longer mine" and the phone is cut off — together with anything the phone had authorised.

Each key can be limited: the work computer may spend at most 200 a week; the old tablet may only look.

**If every device is lost**, you ask for your account back from a new one (**I lost my devices**). Your community
has to confirm it is you — it knows you; a message is not enough, it should speak to you. Then the new device holds
the account, but only after a week that is announced openly: during it any of your old devices, if one turns up or
was only mislaid, shows a warning and can cancel. This is possible because members are known people: the community
that trusted you with credit can also restore your access. It cannot do so quietly, and not at once.

## A payment

You buy a haircut for 60.

1. Your phone signs: *send 60 to the barber*.
2. The node checks the signature and that 60 does not take you below your credit line, adds its own signature, and
   appends the record to your account's chain. You are debited.
3. The node appends *received 60* to the barber's chain — unless the barber is at their ceiling, in which case the
   60 comes straight back to you.

A payment has a name (a key made by your app), so pressing the button twice, or again after a lost connection, pays
once.

## Your chain

Every account has its own **chain**: the list of everything that ever happened to it, each record carrying the
fingerprint (hash) of the one before, each signed. Your balance is not a number someone stores; it is what you get
by adding up your chain. Your phone can do the sum itself, and so can anyone you show the chain to.

Because each record includes the fingerprint of the previous one, nobody can remove or alter an old record without
breaking every signature after it.

The chain holds amounts and fingerprints, not your name, address or what the payment was for. Those details are
kept beside it and can be erased on your request without breaking the chain.

## The board

Members say what they offer and what they look for: a tailor's hemming for 40, half of it in units; a logo wanted.
Whoever opens an offer can pay for it from there — the member, the part of the price that is in units and what it is
for are already filled in; the rest of the price is paid in money, between the two, outside the system. An entry is
its member's own signed word and is taken off by them alone.

## Vouching

A newcomer has no history, so no credit. A member who knows them can **vouch**: lend part of their own credit line.
The newcomer can now buy; the voucher's own room shrinks by the same amount. When the newcomer has earned their way
back to zero, the line returns to the voucher.

There is no reward for bringing people in — only your own room at risk. That is deliberate: it makes members bring
people they would trust with their own money.

## Families, teams, companies *(designed)*

An account can be held not by a key but by **other accounts**. A family account names Anna's and Jan's accounts as
holders, and their daughter's with a limit of 50 a week. Anna pays from the family account with whichever device is
in her hand; when she gets a new phone she adds it once, to her own account, and it works everywhere she is a
holder.

A company does the same with its people, and can require **two signatures** above an amount. A department's budget
is an account to which the company lends part of its credit line — the same mechanism as vouching — and takes it
back when the budget is cut.

## Nodes

A node is a community with a computer: it admits members, sets limits, puts each account's records in order and
co-signs them. Anyone can run one — the code is open and runs on a small cloud service or a single machine.

A node cannot forge your payments (it has no key of yours). To keep it from quietly rewriting history, it publishes
after every batch of changes a **signed summary** of everything it holds. Your app checks that your own chain is
inside that summary, and keeps the summaries it has seen. Two summaries that contradict each other are a proof —
checkable by anyone — that the node lied.

A node is also somebody in the ordinary sense: a cooperative, an association, a company, answerable for its members
and its taxes.

## Between communities

Two nodes that know each other open a **trust line**: each gives the other an account in its own books, with a
credit line and a ceiling like anyone else's. That says how much each community is willing to be owed by the other.

When you pay someone in another community, your node credits the other node's account with it and tells the other
node, which pays its own member. Each keeps the other's signed messages, so neither can deny what passed between
them.

This is what limits fraud. Someone can set up a node full of invented members and unlimited credit — and it buys
them nothing, because no honest node has given them a trust line. A community that does extend a line can lose at
most that line. There is no way to create credit that others are forced to accept.

## What protects you

| What could go wrong | What stops it |
|---|---|
| someone pays from your account | they do not have your device's key |
| your phone is stolen | another device, or recovery, cuts it off; its key was limited |
| you pay twice by accident | the payment's name: the second press finds the first |
| a member spends beyond their line | the node refuses; if it colluded, the two signed records prove it |
| the node changes the books | your chain no longer fits its signed summary — and you hold the proof |
| a fake community appears | it has no trust lines; with one, the loss is at most that line |
| a member never pays their debt back | no software stops this: limits, vouching and knowing each other do |

The last row is the honest one. The system proves who promised what and keeps every loss bounded by limits that
somebody chose. It does not make people keep promises.

## Why there is no blockchain

A cryptocurrency guards one scarce asset, so the entire world has to agree on the single order of all payments.
That is what mining and staking are for, and why they are slow and expensive.

Here nothing is scarce. A unit is one member's promise; the only thing to prevent is that member's promises growing
past their limits unnoticed. That requires putting *one account's* records in order — which that account's node
does — not the world's. So the network grows by adding accounts and nodes; no step ever waits for the whole.

## How it grows

More members in a node, more nodes, more trust lines. No central registry, no permission from us. A node chooses
its own rules; the protocol only fixes what a valid record is, so that any app can check any node. The same app
works with every node that speaks the protocol.
