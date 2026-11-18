**English** · [Русский](ru/organisation.md) · [Українська](uk/organisation.md) · [Polski](pl/organisation.md)

# Who owns what: a foundation, the communities, the people who build

This is a proposal written by engineers for a lawyer to check, for a start in Poland. It is not legal advice.
Nothing here is decided or registered yet. A draft of the foundation's statute is [beside it](statute.md); the
questions for the lawyer are [at the end](#what-to-ask-a-lawyer).

## The idea in one paragraph

Okay Mutual has no centre: a hub's operator only keeps a machine, and each community governs itself. An
organisation on top of it must not change that. So nothing here "runs the network". There are three layers, each
with its own legal form, and only the first is a non-profit that we set up ourselves.

## The three layers

| Layer | What it is | Legal form | What it owns | What it must never do |
|---|---|---|---|---|
| **The commons** | the protocol, the open code, the documents, the name, a public hub | a **foundation** (*fundacja*) | the trademark, the domain names, the rights it is given in the code, the public hub | hold members' money or units, take a fee in units, admit or expel a community's members, set a community's policy |
| **A community** | members who keep accounts with each other | its own **association** or **cooperative** (*stowarzyszenie*, *spółdzielnia*) | its rules, its members' admission, its policy, its node's key | sell units for money or exchange them back |
| **The builders** | whoever develops, deploys and supports | ordinary businesses (a sole trader, a company) | their own work and tools | present their services as the foundation's decisions |

Why the first layer is a non-profit: the code, the name and the public hub should belong to someone neutral, whom
communities trust more than a person or a company — and who cannot be bought, sold or inherited.

Why the second layer is not part of it: a community that gave its members credit lines answers for its own rules
and its own debts. If the foundation governed the communities, it would be "running a currency" — the one thing
the whole design avoids ([staying light](legal.md)).

Why the third layer stays commercial: writing software and running machines costs money, and the people who do it
should be able to earn a living from it openly, by invoice, as for any open-source project.

## How they relate

```
                 ┌─────────────────────────────┐
                 │  THE FOUNDATION             │   owns: name, domains, code rights, public hub
                 │  board · council · founder  │   income: fees in money, grants, donations
                 └──────┬───────────────┬──────┘
        hosting, by     │               │  contracts for work, at arm's length,
        agreement,      │               │  approved by the council
        paid in money   │               │
                 ┌──────┴──────┐   ┌────┴──────────────┐
                 │ COMMUNITIES │   │ THE BUILDERS      │
                 │ (each its   │   │ (businesses that  │
                 │  own legal  │   │  develop, deploy, │
                 │  person)    │   │  support)         │
                 └──────┬──────┘   └───────────────────┘
                        │ a community may also pay a builder directly,
                        │ or run its own node and need nobody
                        ▼
                    its members
```

- **A community and the foundation**: a hosting agreement and a data-processing agreement, nothing more. A
  community may leave the public hub for its own node at any time; the software lets it.
- **A community's voice in the foundation**: communities that use the public hub name representatives to the
  foundation's council. The council approves the budget, the fees and every contract with a related party.
- **A builder and the foundation**: an ordinary contract for work, awarded and accepted by people who do not
  profit from it.
- **Nobody is above a community**: the foundation cannot read a community's keys, spend for its members, or change
  its policy. That is true of the software and is written into the statute.

## Why a foundation first, and what later

- **A foundation** is set up by one founder, who writes its purposes and its statute; a board runs it. It can
  start with one person and a small founding fund. It fits the moment when there are no communities yet.
- **An association** needs at least seven founding members (a registered one; an ordinary one, three)
  and is governed by its members' vote. It fits "nobody
  is in charge" better — once there are communities to be its members.
- **The path**: start as a foundation; give the communities a real voice through the council from the first
  community on; when several communities exist, either keep that arrangement or set up a union of communities
  that takes the foundation's seats. The statute's draft leaves this open on purpose.

## Money

What the foundation may take, and how:

| Source | Form |
|---|---|
| a community's fee for hosting and support | an invoice, in money |
| grants for open-source software and the social economy | by agreement with the grant-giver |
| donations | in money |
| paid training, documentation, events | within its statutory purposes |

What it never takes: a fee in units, a share of members' deals, anything that depends on how much a community
trades. The foundation holds no account in any community's units except as an ordinary member of a community it
has joined, on that community's terms.

What it spends on: the public hub, security reviews, legal opinions that every community can reuse, translation,
documentation, and the work of building — by contract.

## The conflict of interest, said plainly

The founder is likely to be the first builder too: the person who set up the foundation also sells it
development. That is common in open source and is acceptable only when it is visible and controlled:

- the founder does not vote on a contract with their own business;
- every such contract is approved by the council and published, with its price;
- the foundation's accounts are published each year;
- the code stays under its open licence whatever happens to the foundation or to any builder, so nobody depends
  on either.

## What moves to the foundation, and when

1. The name and the trademark application; the domain names.
2. The public hub (the hosting account) and its operator's duties under data-protection law.
3. A licence to the code. The code is already under an open licence, which nobody can withdraw; the foundation
   needs the right to speak for the project and to defend the name, not exclusive ownership of the code.
4. Later: the legal opinions and model documents (a community's model rules, a model hosting agreement).

Until the foundation exists, all of this stays with its author, and the documents say so.

## What a community needs for itself

A community that goes beyond a trial among friends should be a legal person of its own, so that its rules are a
contract and its members' debts to each other have somewhere to be settled. The usual choices: an ordinary
association (simple, for members who know each other), a registered association, or a cooperative (when the
members are mostly businesses and want shares and limited liability). The foundation can offer model rules; it
does not choose for anyone.

## What to ask a lawyer

1. Foundation or association for the first layer, given one founder now and communities later? What is the
   smallest sound founding fund?
2. Is hosting a community for a fee a paid statutory activity of a public-benefit kind, or a business activity
   that needs an entry in the register of entrepreneurs and its own share of the founding fund?
3. The founder as a contractor: what the statute and the board's rules must say so that contracts with the
   founder's business are valid and do not endanger a later public-benefit status.
4. The council: can it include representatives of communities that are themselves legal persons, and can it be
   given the power to approve related-party contracts?
5. The trademark and the domains: transfer or licence, and at what value?
6. Does operating the public hub make the foundation anything more than a host in the eyes of financial
   regulation, given [what the software does and does not do](legal.md)?
7. Data protection: the foundation as a processor for each community; what the model agreement must contain.
8. Liquidation: to whom the assets may go, so that the name and the hub cannot end up as someone's private
   property.
