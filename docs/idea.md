**English** · [Русский](ru/idea.md) · [Українська](uk/idea.md) · [Polski](pl/idea.md)

# The idea

## The problem

A tailor has free hours and no clients this month. A baker needs a jacket altered and has bread nobody has bought.
A programmer would build the tailor a booking page and wants both bread and a jacket. Everyone has something to
give and something they need. Nothing happens, because each is waiting for money to arrive first.

Money is scarce exactly when it is needed most: between invoices, between jobs, before a new business has its first
client, in a downturn when banks lend less. The work, the goods and the needs are all still there. What is missing
is only a way to keep count.

## What mutual credit is

Keeping count is all that is needed. Give each of the three an account that starts at zero:

| What happens | Baker | Tailor | Programmer |
|---|---|---|---|
| at the start | 0 | 0 | 0 |
| the tailor alters the baker's jacket for 80 | −80 | +80 | 0 |
| the programmer builds the tailor's page for 200 | −80 | −120 | +200 |
| the baker supplies the programmer's office for 150 | +70 | −120 | +50 |

Three deals were done and nobody needed money to start. Look at the bottom row: it sums to zero, and it always will.
Nobody "has" units the way one has coins. A positive balance means: *I have given the community more than I have
taken.* A negative one: *I have taken more than I have given, and I owe it back in my own work.*

That is the whole mechanism. The units are created by the buyer at the moment of buying and disappear when the same
member sells. There is no issuer, no interest, and nothing to mine.

## The two limits that make it work

Left like that, one member could take forever and another could give forever. So each account has two limits:

- **A floor** — how far below zero it may go: its credit line. Small for a newcomer; it grows with what the member
  actually sells.
- **A ceiling** — how far above zero it may rise. This one surprises people. A member who only sells and never buys
  is not being virtuous: it is draining the others, who can only earn by selling to someone. At the ceiling it must
  spend before it can sell more.

Both limits come from John Maynard Keynes's wartime plan for trade between countries (the International Clearing
Union), where creditors and debtors alike were to be pressed back towards balance. The plan lost to the dollar at
Bretton Woods; the idea was right.

## This is old, and it works

- **WIR** in Switzerland has run a mutual credit circuit among small and medium firms since 1934. Its turnover
  rises in recessions, when bank credit shrinks — it does what it was built for.
- **Sardex** in Sardinia, founded in 2009 after the financial crisis, connects several thousand local businesses.
  Its lesson: brokers who actively match members' offers and needs are what keeps balances moving.
- **Slovenia** has cleared chains of unpaid invoices between firms by multilateral set-off since the early 1990s,
  with no new currency at all.
- **LETS** circles and time banks have done the same between neighbours since the 1980s. Their lesson is the
  opposite one: on volunteers alone, with nothing one really needs on offer, a circle stays small.

It has also failed, and the failures teach as much. Argentina's barter clubs grew to millions of people in 2001 and
collapsed within a year: their paper credits were forged and over-issued, because nothing tied a credit to a member
who owed it. Every design choice below is there to make that impossible.

## What is new here

The old networks each run on one operator's computer. Members must trust that the operator's books are honest, and
a network cannot outgrow its operator. Okay Mutual keeps the economics and changes the bookkeeping:

- **Every record is signed by the member's own key**, held on their own phone or computer. Nobody — not even the
  operator — can spend a member's credit, and nobody can deny what they signed.
- **Anyone can run a node**: a community with its own members and its own rules. The code is open.
- **Communities connect by trust lines**: each decides how much it is willing to be owed by another. A payment can
  cross from one community to the next within those lines.
- **Members check the books themselves.** The app on the phone verifies every record that concerns its owner and
  can prove it if a node ever lies.

It borrows from cryptocurrencies what makes them trustworthy — keys, signatures, records that cannot be quietly
rewritten — and leaves out what makes them slow, costly and speculative: there is no coin, no mining, no global
chain and no price to bet on. See [how it works](how-it-works.md).

## Beside money, not instead of it

Okay Mutual runs parallel to ordinary money and offers a choice. It replaces nothing.

- Prices stay in the national currency; a unit counts as one unit of it.
- A deal can be part units and part money. Each seller decides what share of units to accept — all, half, none.
- Taxes are owed and paid in money, exactly as for any sale. A sale for units is a sale.
- Units are never sold or exchanged for money, so there is nothing to speculate on.
- Nobody is obliged to accept more than they chose to, and anyone can leave by bringing their balance to zero.

Rent, fuel and taxes will go on being paid in money. The network is for the part of economic life that money
shortage needlessly stops.

## What it is not

- **Not a cryptocurrency.** There is no coin and nothing to invest in. Nobody gets rich by joining early.
- **Not network marketing.** Nobody earns from recruiting. A member who brings a newcomer *vouches* for them by
  lending part of their own credit line — a risk taken, not a commission earned.
- **Not a way around taxes.** The opposite: every deal leaves a signed record, and bookkeeping follows from it.
- **Not a promise.** A signature proves who promised what. It cannot prove the promise will be kept. That rests,
  as it always has, on people knowing each other, on vouching, and on limits that grow only with a record of
  keeping one's word.

## Who it is for

- **Small businesses and the self-employed**: sell spare capacity, buy what you need without waiting for cash.
- **People between jobs or starting out**: earn by what you can do, and be found by those who need it.
- **New businesses**: the credit line is a budget for testing an idea — a page, a logo, a first batch — and the
  community is the first market. Orders placed in units before the product exists are the cheapest proof of demand.
- **Families, teams and organisations**: shared accounts with a budget for each member or department.
- **Communities**: a town, a trade, a diaspora — anyone able to run a node and willing to answer for its members.

## What it asks of you

To give before you take, or soon after. To accept units for something real. To spend what you earn rather than
hoard it. And, if you vouch for someone, to mean it.

## Where it stands

A design and a working prototype — see the [README](../README.md) for exactly what runs today. The legal form of a
node's operator, and how each country's rules on taxes and payment services apply, must be settled with a lawyer and
a tax adviser before any community invites members it does not personally know.

## Further reading

- Thomas Greco, *The End of Money and the Future of Civilization* — the case for mutual credit, with its history.
- E. C. Riegel, *Flight from Inflation* — money as a member's promise, half a century before software.
- Bernard Lietaer, *The Future of Money* — complementary currencies around the world.
- David Graeber, *Debt: The First 5,000 Years* — credit came before coins.
- Alfred Mitchell-Innes, *The Credit Theory of Money* (1914) — the short classic.
- Tomaž Fleischman, Paolo Dini, Giuseppe Littera, *Liquidity-Saving through Obligation-Clearing and Mutual Credit*
  (2020) — the Slovenian set-off and Sardex, with numbers.
- James Stodder, *Complementary Credit Networks and Macroeconomic Stability: Switzerland's Wirtschaftsring* (2009).
- Elinor Ostrom, *Governing the Commons* — how communities keep a shared resource alive.
- Eric Ries, *The Lean Startup* — test the riskiest assumption first; we do, see the [spec](../specs/mutual.md).
