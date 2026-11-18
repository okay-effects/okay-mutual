# Okay Mutual — spec

A mutual credit network for businesses and private people: they sell and buy services and goods for units they
issue to each other, find work, clients and staff, and their accounting follows from the deals. This library is the
network's ledger; a host (first Okay Mall) gives it members, pages and sign-in.

Status: MVP. Not reviewed by a lawyer or a tax adviser; not yet tried with people.

## Purpose and limits

- A member sells and buys inside the network for units. One unit is priced as one unit of the national currency
  (1 unit = 1 PLN) in prices and in accounting.
- Units are never exchanged for money and never sold. The balances always sum to zero: a unit is born when a member
  buys and gone when it sells. There is no interest.
- The network does not promise "no tax". A deal in units is an ordinary sale at the market price.
- Out of the MVP: tokens and blockchains, exchange of units, a salary in units, fees and the insurance fund (a fee is
  money and is the host's), set-off of invoices (below, "Later").

## Accounts

| Kind | Who | Credit (below zero) | Ceiling (above zero) |
|---|---|---|---|
| business | a firm, a sole trader | a share of its sales in the network, small at the start | a multiple of its credit |
| personal | a private person | 0 at the start; grows with history and vouching | set, small |

An account: `id`, `kind`, `name`, `credit`, `ceiling`, `balance`, `joined`, and vouching's `voucher`, `vouched`,
`lent`, and its `holder` — the address (an email) of the one who may act
for it, signed in by the host; empty: nobody but the operator. Amounts are whole numbers of hundredths of a unit. Its `floor` — the lowest balance it may reach — is
`-(credit + vouched - lent)`.

## Rules

1. **A payment** moves `amount` from the buyer to the seller if the buyer stays at or above its floor and the seller
   at or below its ceiling. Both hold or nothing is written. Who reaches the ceiling must spend before selling more:
   a hoarder blocks the network as a debtor does.
2. **Limits** are set by the operator. A new credit line never puts the floor above the debt there is. The line a
   business's sales suggest: two months of its average monthly sales over the last three months, capped
   (`Rules.suggested`, `Api.months`, `Api.cap`) — shown beside the account, applied by a person.
3. **Vouching.** A member lends part of its own credit line to a newcomer: the newcomer's floor goes down by it, the
   voucher's up. One voucher a newcomer. The line returns to the voucher (`release`) when the newcomer's balance fits
   its own line without it. There is no reward for bringing a member — only the risk of one's own room.
4. **The journal** only grows. A mistake is undone by a line that reverses it (once; whatever the limits say: it is a
   correction, not a deal). A reversed line and its reversal are not deals in the measures.
5. **A change is one step**: the accounts it moves and the journal's line are committed together or not at all; a
   commit that lost a race is run again from the start.

6. **A named payment is paid once.** A payment may carry a `key`; sent again under the same key (a retry after a
   lost answer) it answers the line it made and moves nothing. The same key with another buyer, seller or amount
   is refused. A refused payment keeps no key.

## Measures

Decided on once a month: go on, change the segment, or change the mechanics. Never registrations or listings:

- `velocity` — the last 30 days' turnover over the sum of the positive balances;
- `both` — the share of accounts that both sold and bought in the last 30 days;
- `stale` — the share of the positive balances held by accounts with no line in the last 60 days;
- `firstSale` — the median days from joining to the first sale;
- `repeat` — the share of the last 30 days' deals between a pair that had dealt before;
- `sum` — the balances' sum: zero, or the ledger is broken.

## What is tested first (Lean Startup)

| # | Hypothesis | How | "Yes" |
|---|---|---|---|
| H1 | People agree to work for units | 10–20 members, the operator the broker | `firstSale` reached by ≥ 60% in 30 days |
| H2 | What is earned can be spent | the same | `both` ≥ 0.6; `velocity` ≥ 1 |
| H3 | Members bring members | vouching | ≥ 0.5 vouched newcomers a member in 2 months |
| H4 | A business pays a fee in money | asked after 2 months | ≥ 30% pay |
| H5 | Firms need set-off of invoices | 10 firms' invoices, cycles by hand | ≥ 10% of the sum cleared |

The thresholds are first guesses. In the MVP the operator records the deals on the members' behalf (the broker of
Sardex, by hand); code is written for what the broker already does and what slows them down.

## The code

- `Ledger` — the effect: `Open`, `Find`, `Accounts`, `Limit`, `Hold`, `Pay`, `Reverse`, `Vouch`, `Release`, `Journal`.
- `Rules` — pure: what a change does to the accounts it touches, or the `Refusal`.
- `LedgerDurable` — its handler onto okay-serverless' `Storage` (`Durable.step`): a Durable Object's SQLite on
  Cloudflare, okay-serverless' host without it, `Memory` in tests. Keys under `mutual/`.
- `Measures` — pure, over the accounts and the journal.
- `Api` — the calls over HTTP under a prefix; `LedgerObject` — the network's `Actor` (namespace `MUTUAL`). Who may
  call is the host's question.

## Later

- Members' own pages and sign-in; a member pays by its own confirmation (the host's).
- Paying an order or a booking of a shop in units, wholly or in part; the invoice follows from the deal.
- The board of offers and wants; matching (who to sell to, where to spend a surplus).
- Set-off of invoices without units: cycles in the graph of unpaid invoices cleared by the smallest amount of each,
  with every party's consent.
- The fee in money and the insurance fund; leaving the network with a debt.
- Starting a business inside the network: the credit line as the budget of an experiment, pre-orders in units as the
  test of demand.

## Questions for a lawyer and a tax adviser

1. Is a unit electronic money or a payment service (the limited-network exclusion)?
2. The operator's form: a cooperative, an association or a company; who answers for the debt of one who left.
3. VAT and the moment of income when paid in units; mixed payment.
4. Private people: the limit of unregistered activity; what the operator must report.
5. Bonuses to employees in units: allowed, and how taxed.
6. Multilateral set-off: the form of consent, its accounting, KSeF.
7. GDPR: the history of deals as a rating.
8. KYC/AML duties of the operator.
