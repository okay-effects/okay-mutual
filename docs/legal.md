**English** · [Русский](ru/legal.md) · [Українська](uk/legal.md) · [Polski](pl/legal.md)

# Staying light: what the design avoids, and what applies anyway

This is our reading of the rules in the European Union, written by engineers. It is not legal advice, and a rule
read here as not applying may be read otherwise by a regulator. It exists so that a lawyer can check a short list of
precise claims instead of starting from nothing — see [the questions](#what-to-ask-a-lawyer-once) at the end.

The aim: a community should be able to use Okay Mutual without a licence, a registration with a financial
supervisor, or a compliance department. That is possible only because of what the system *is not*, and each "is
not" below is a property of the software, not a promise in the small print.

## What the system is

A shared account book. Members of a community sell things to each other and write down who has given more and who
has taken more. A unit is the measure in that book. Nobody issues units, nobody holds anybody's money, and the
balances always add up to zero.

The closest things in existing law are multilateral barter and set-off between businesses, which are old and
ordinary, and the books of an association or a cooperative.

## The rules that keep it light

| The rule, as the software enforces it | What it keeps the system out of |
|---|---|
| **Units are never sold for money.** There is no way to buy a unit: a balance changes only when one member sells something to another. | Electronic money, which by definition is issued "on receipt of funds". |
| **Units are never exchanged back into money.** There is no redemption, no cash-out, no exchange rate, no market. | Electronic money again; exchange services; anything to speculate on. |
| **The system never touches money.** A deal that is part units and part money has its money part paid the ordinary way, outside. A community's fee is an ordinary invoice. | Payment services, which concern transfers of *funds*; taking deposits, which needs a banking licence. |
| **Nobody issues anything.** A unit is created by the buyer in the act of buying and disappears when that member sells. There is no issuer, no reserve, no token to hold. | Being an issuer of anything, in the sense financial rules give the word. |
| **Units stay inside a community of known members.** An account exists only by a community's admission; units pass only between members, and between two communities only where each has chosen to trust the other. | A public offer; the "limited network" tests of several rules are met with room to spare. |
| **Members are known, not anonymous.** A community admits people it knows, each with a credit line it chose. | The concerns that money-laundering rules answer: hidden parties, unlimited amounts. |
| **No interest, on either side.** A debt costs nothing; a surplus earns nothing. | Lending as a business; investment products. |
| **Every balance is bounded** — below by a credit line, above by a ceiling. | Accumulation; large exposures. |
| **A hub's operator only keeps the machine.** It cannot spend for a member, admit anyone, or set a limit; those need keys it does not have. | Being a party to the members' dealings. |

Two more, which are choices a community makes rather than properties of the code:

- **Private people earn before they spend.** A community admits businesses, the self-employed and private people,
  and a private person's account never goes below zero. The software enforces it: such an account opens with no
  credit line, none can be given to it, and a line another member lends it comes back. A person earns units first —
  by work or goods sold to members, or units a business gives — and then spends them. No credit is given to a
  consumer, so the rules on consumer credit do not arise. A community may choose otherwise: admit only businesses,
  or give private people credit lines too and take those rules on.
- **The other choices, and what each takes on.** The policy is the community's own, and it can change it (see
  [how it works](how-it-works.md#policies-each-level-sets-its-own)). Between "earn first" and "lines for all" there
  are two middle ways: a person may be lent a line by a member who answers for it, or be given one by the community
  after they have earned a set amount — both with a bound on what a person may owe. Each of them is credit to a
  consumer, small and interest-free, and brings the consumer-credit question below; the bound keeps what is at
  stake small, it does not answer the question. **A hub's operator can rule these choices out** for every
  community it hosts (`HUB_PERSONAL`), or bound them (`HUB_MAX_PERSONAL`) — which is how an operator stays inside
  the profile it has had checked.
- **A legal person for the community** — an association or a cooperative whose members keep accounts with each
  other — rather than an operator who "runs a currency".

## What applies whatever we do

Staying out of financial regulation does not put a community outside the law. These apply, and none of them needs a
licence:

- **Tax.** A sale for units is a sale. VAT and income tax are owed in money, on the ordinary price, as for a sale
  for cash. This is the rule barter networks were shut down for breaking. The software helps by keeping a signed
  record of every deal, and a member can copy its statement for the books.
- **Reporting by platforms.** Since 2023 the EU requires those who operate a platform on which sellers earn to
  report the sellers' income to the tax office (the directive known as DAC7). Whether a community's legal person is
  such an operator depends on how it is set up and on its size; small sellers are exempt. **This is the first
  question for the lawyer.**
- **Data protection.** A community keeps members' names and their dealings. It needs the usual: a stated purpose, a
  privacy notice, an agreement with the hub's operator who processes the data on its behalf. The design helps:
  names and memos are kept beside the chain and can be erased; the chain itself holds amounts and fingerprints.
- **Contracts.** A community's rules are a contract among its members: what a unit is, how one joins and leaves,
  what happens to a debt left behind. The software asks every newcomer to accept the rules before it opens an
  account.
- **Ordinary liability.** A member who does not deliver, or does not honour a debt, answers to the others as under
  any contract.
- **A buyer's rights.** A business that sells to a private person owes them what it owes any consumer — honest
  information, a warranty, the right to return — whatever the price is counted in.

## Where we are not sure

Honest uncertainty, in order of weight:

1. **The EU's rules on crypto-assets (MiCA).** They cover "a digital representation of a value or of a right that
   is able to be transferred and stored electronically using distributed ledger technology or similar technology".
   Our reading: a unit is not an asset anyone holds or can sell, it cannot leave its community, nobody issues or
   offers it, and each community's book is kept by one party — its node — which is what a ledger has always been.
   Signatures and fingerprints make the book checkable; they do not make it distributed. A regulator could read
   "similar technology" more widely. If it did, the exemption for a limited network of parties under contract is the
   fallback. Worth a lawyer's written view, and worth not using the vocabulary of that world where another word
   does.
2. **Credit to private people.** A credit line given to a consumer may fall under consumer-credit rules even with
   no interest; the EU's revised directive, applying from late 2026, narrows the old exemptions. Whether a
   community's mutual credit is "credit granted in the course of a trade" is unsettled. Where private people only
   earn and then spend, they are given no credit and the question does not arise; a community that gives them
   credit lines takes it on.
3. **When a person's selling becomes a business.** Someone who sells work or goods now and then is a private
   person; someone who does it regularly may be carrying on a business in the tax office's eyes, registered or
   not, and each country draws that line in its own place. This is so for sales in money too; the statement the
   app gives makes the count easy to keep.
4. **Platform reporting (DAC7)**, as above.
5. **Each country.** The rules above are European; each state applies them in its own way, and some add their own.

## The profile to start in

Everything above points one way for a first real community:

- one country and one unit;
- businesses, the self-employed, and private people who earn before they spend;
- an association or cooperative as the community's legal person, with its rules written down and accepted by each
  member;
- admission by decision, small credit lines that grow with a member's record;
- a fee, if any, in money, by invoice;
- no trust lines to other communities until the first one works.

With that profile the open questions shrink to three — MiCA's reach, platform reporting, and where a private
person's selling becomes a business — and a single legal opinion can answer them.

## What to ask a lawyer, once

1. Is a member's balance in such a book "electronic money", "funds" or a "crypto-asset" under the rules of this
   country? Our reading says no to each, for the reasons in the table.
2. Is the community's legal person a "platform operator" with a duty to report its members' sales, and from what
   size?
3. Which legal form suits the community here — association, cooperative, company — and who answers for a debt a
   leaving member does not settle?
4. VAT and income tax on a deal in units, and on a deal that is part units and part money: the moment the income
   arises, the invoice.
5. Private people who only earn and spend, with no credit line: does anything of consumer law apply beyond a
   buyer's ordinary rights? From what size is a person's selling a business here? And if a community gives private
   people credit lines: do consumer-credit rules apply to them?
6. What must the hub's operator, who only hosts, have in place: a processing agreement, terms of use, nothing else?
7. Does anything change when two communities open trust lines to each other across a border?

More detail for the lawyer is in [the protocol](../specs/protocol.md): what a record is, who signs what, who can do
what. How the whole could be organised — a foundation, the communities, the builders — is in
[who owns what](organisation.md), with a [draft statute](statute.md).
