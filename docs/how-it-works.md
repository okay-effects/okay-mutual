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

## Leaving

A member leaves by closing the account (*Devices → Close the account*). It is closed at zero: what one owes is
earned first, what one has is spent first, a lent line is given back — so nobody is owed anything by the closing.
Then the account's last record is written and what was kept beside its chain is erased: the member's name, what
their payments were for, their board entries, their stamp cards. The records of the amounts stay, without a name:
every other member's chain and the community's signed head rest on them. A payment to a closed account comes back
to its sender.

The other side of a deal keeps its own record of it, with what it was for: that is their bookkeeping, not the
leaving member's data. The community can also close the account of one who left without closing it, at zero
(*Community → Close accounts of those who left*). One who leaves owing is the community's to settle, by its rules.

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

## Stamp cards

Many shops hand out a paper card: a stamp for each purchase, the sixth coffee free. Paper cards get lost. A shop
here can keep such a card for its customers: it says how many stamps fill it and what a full card gives. At the
counter the seller shows a code on their phone; the customer points their camera at it, the app opens, and the
stamp is on their card. A full card's reward is given the same way. A code works once, for ten minutes.

A stamp is **not a unit**: nobody owes it, it cannot be passed on or spent anywhere else — it is a shop's promise
of a discount, kept where it cannot be lost. It works for purchases paid in ordinary money too, so a card is the
easiest way for a shop's regulars to start using the app: an account is all a card needs.

## Vouching

A newcomer has no history, so no credit. A member who knows them can **vouch**: lend part of their own credit line.
The newcomer can now buy; the voucher's own room shrinks by the same amount. When the newcomer has earned their way
back to zero, the line returns to the voucher.

There is no reward for bringing people in — only your own room at risk. That is deliberate: it makes members bring
people they would trust with their own money.

## Families, teams, companies

An account can be held not by a device's key but by **members' accounts**. A family opens a shared account
(*Accounts → Shared accounts*) and names Anna's and Jan's accounts as its holders, and their daughter's with a limit
of 50 a week. Anna pays from the family's account with whichever device is in her hand; when she gets a new phone
she adds it once, to her own account, and it works everywhere she is a holder. Every record says both things: which
device signed, and for whose account — and anyone can check that against that account's own chain.

A shared account has no credit line of its own. It spends what it is paid, or a line a member lends it — the same
mechanism as vouching. That is a **budget**: a business opens a team's account and lends it part of its own line. A
family's account is people's: where private people earn before they spend, it never goes below zero either.

**Two signatures.** Any account can say: above this amount, two holders. One holder proposes the payment; it waits,
in the open, for up to a week; another holder confirms it, and only then do units move. Enough for a cashier and a
director, small enough to explain.

Not built yet: a shared account held by another shared account, and taking a lent budget back from the app.

## Policies: each level sets its own

Nothing here is decided once for everybody. Each level sets its own policy, inside what the level above allows:

- **A hub's operator** says what communities on that machine may be: which ways with private people they may
  choose, the most a business's starting line, the most a person may owe. An operator who wants to host only the
  cautious kind says so, and no community there can go further.
- **A community** says how one joins (open, or by admission), what line and ceiling a new business gets, whether
  families and teams may share accounts, how long a recovery waits — and how it treats **private people**:

  | The community's choice | A private person |
  |---|---|
  | businesses only | has no account |
  | earn first *(the default)* | has no line and is lent none: earns, then spends |
  | lent lines only | has no line from the community, but a member may lend part of their own |
  | a line after a record | is given a line by the community once they have earned a set amount |
  | lines for all | opens with a line, as a business does |

  With any choice but the first two the community also says the most a person may owe. It changes its policy from
  its office (*Community → Policy*), by the signed word of those who govern it; the change is in force at once, and
  lines given before stay.
- **A family, a team, a company** says who holds its account, how much each holder may spend a payment and a week,
  and above what amount two holders must sign.
- **A person** says which devices hold their account and how much each may spend.

The first two choices keep credit away from consumers; the others give it, and with it the questions in
[the legal notes](legal.md).

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
