[English](../run-a-node.md) · [Русский](../ru/run-a-node.md) · [Українська](../uk/run-a-node.md) · **Polski**

# Węzeł dla twojej społeczności

Węzeł to komputer społeczności: przyjmuje uczestników, prowadzi łańcuch każdego konta, współpodpisuje każdy zapis
i publikuje podpisane podsumowanie wszystkiego, co przechowuje. Najpierw przeczytaj,
[jak to działa](how-it-works.md).

Są trzy sposoby, żeby go mieć: od minuty do jednego wieczoru. Wszystkie trzy działają na tym samym otwartym kodzie.

Zanim zaprosisz kogokolwiek, kogo nie znasz osobiście: kto prowadzi społeczność, odpowiada za jej uczestników i za
jej podatki. Formę prawną i przepisy o usługach płatniczych w swoim kraju ustal z prawnikiem
([otwarte pytania](../../specs/mutual.md#questions-for-a-lawyer-and-a-tax-adviser), po angielsku).

## 1. Utwórz społeczność na hubie — minuta, bez serwera

Hub to jedno wdrożenie, które utrzymuje węzły wielu społeczności. Nasz jest publiczny:

1. Otwórz aplikację: https://okay-mutual.okay-effects.workers.dev/app/
2. Wybierz **Utwórz społeczność**: jej nazwa, jej miejsce w adresie, jej jednostka, sposób dołączania, kto może
   dołączyć, limity nowego uczestnika, jej zasady.
3. To wszystko. Klucz twojego urządzenia otworzył własne konto społeczności: jesteś jej założycielem.
4. **Zaproś** pokazuje link. Kto go otworzy, trafia do aplikacji w twojej społeczności i może poprosić o przyjęcie.
5. **Społeczność** to twoje biuro: kto prosi o przyjęcie (przyjmij z limitami albo odrzuć), uczestnicy, ich limity.

Co masz ty, a co hub: klucz społeczności — ten, który pozwala przyjmować uczestników i ustalać limity — jest na
twoim urządzeniu i nigdzie indziej. Własny klucz węzła, który współpodpisuje każdy zapis, przechowuje hub. Operator
huba nie może więc wydawać za nikogo ani zarządzać twoją społecznością, ale mógłby przestać ją obsługiwać.
Aplikacja każdego uczestnika stale sprawdza węzeł i pokazałaby, gdyby zapisy zostały zmienione. Jeśli to za mało,
uruchom własny hub.

Publiczny hub ogranicza liczbę społeczności, które można utworzyć z jednego adresu w ciągu dnia.

## 2. Własny hub na Cloudflare — pół godziny, wystarczy darmowe konto

Potrzebne są: konto Cloudflare, Java 21 lub nowsza, [sbt](https://www.scala-sbt.org/) i Node.js.

```sh
git clone https://github.com/okay-effects/okay-mutual && cd okay-mutual
printf 'CLOUDFLARE_API_TOKEN=…\nCLOUDFLARE_ACCOUNT_ID=…\n' > .env && chmod 600 .env
./worker/deploy.sh
```

Token musi mieć uprawnienie do edycji Workers. Skrypt buduje hub i stronę, a potem je wdraża; wypisuje twój adres,
`https://okay-mutual.<your-account>.workers.dev`. Otwórz pod nim `/app/` i utwórz swoją społeczność.

Nie trzeba ustawiać żadnego sekretu: para kluczy każdego węzła powstaje razem z węzłem, wewnątrz Cloudflare, i nigdy
go nie opuszcza. Ustawienia są w `worker/wrangler.toml`:

| Zmienna | Znaczenie | Domyślnie |
|---|---|---|
| `HUB_OPEN` | `1`: każdy może utworzyć społeczność na twoim hubie; cokolwiek innego: tylko ten, kto ma `HUB_TOKEN` | `1` |
| `HUB_PER_DAY` | ile społeczności można utworzyć z jednego adresu w ciągu dnia | `3` |
| `HUB_MAX` | ile społeczności może być łącznie | `500` |
| `HUB_PERSONAL` | które sposoby postępowania z osobami prywatnymi może wybrać społeczność tutaj: `business`, `earned`, `vouched`, `record`, `line` (po przecinku) | wszystkie |
| `HUB_MAX_CREDIT` | najwyższa linia kredytowa nowej firmy, jaką może ustawić społeczność, w setnych (0: dowolna) | `0` |
| `HUB_MAX_PERSONAL` | ile najwyżej może być winna osoba prywatna w dowolnej społeczności tutaj, w setnych (0: dowolnie) | `0` |

Żeby zachować hub dla siebie, ustaw `HUB_OPEN = ""`, uruchom `npx wrangler secret put HUB_TOKEN` w `worker/`
i twórz społeczności żądaniem z nagłówkiem `x-hub-token`.

## 3. Na własnej maszynie — wszystko w twoich rękach

Potrzebne są Java 21 lub nowsza i sbt.

```sh
git clone https://github.com/okay-effects/okay-mutual && cd okay-mutual
sbt site host/assembly                       # the site with the app, and the hub's jar
java -jar target/okay-mutual-node.jar
```

Otwórz http://127.0.0.1:8787/app/ i utwórz społeczność.

Wszystko, co hub wie — łącznie z kluczem każdego węzła — jest w `node-data/okay.sqlite`. Rób kopie zapasowe tego
katalogu i nikomu go nie udostępniaj: kto go ma, może podpisywać jako twoje węzły.

| Zmienna | Znaczenie | Domyślnie |
|---|---|---|
| `NODE_DIR` | gdzie przechowywane są dane | `node-data` |
| `PORT`, `HOST` | gdzie nasłuchuje | `8787`, `127.0.0.1` |
| `HUB_OPEN`, `HUB_TOKEN`, `HUB_PER_DAY`, `HUB_MAX` | jak wyżej | `1`, brak, `20`, `500` |
| `SITE` | katalog strony, którą ma serwować | `target/site` |

Żeby był dostępny dla innych, postaw go za serwerem WWW z HTTPS (klucze aplikacji wymagają bezpiecznej strony)
i ustaw `HOST=0.0.0.0` albo przekieruj do niego ruch przez proxy.

## Kto zarządza społecznością

Nikt z zewnątrz i żadne hasło. Własne konto społeczności jest kontem jak każde inne: urządzenia, których klucze
mogą nim *zarządzać*, to zarząd społeczności. W aplikacji, kiedy używasz tego konta:

- **Społeczność** — kto prosi o przyjęcie, uczestnicy, suma wszystkich sald (zawsze zero), limity każdego
  uczestnika.
- **Urządzenia** — dodaj urządzenie kolejnego członka zarządu albo któreś usuń. To ten sam ekran, którego każdy
  używa dla swojego telefonu i laptopa.

Polecenie dla węzła (przyjęcie uczestnika, ustawienie limitu) jest podpisane takim kluczem, jest ważne przez
dziesięć minut i przyjmowane tylko raz.

### Gdy klucze zarządzających zginą

Społecznością zarządzają klucze, które mają jej własne konto. **Dodaj drugiego zarządzającego pierwszego dnia** —
inne swoje urządzenie albo osobę, której ufasz (*Społeczność → Zarządzający*); biuro ostrzega, dopóki
zarządzający jest jeden.

Jeśli mimo to zginą wszystkie takie klucze, uczestnicy mogą wybrać nowego zarządzającego — tam, gdzie społeczność
sama przyjmuje uczestników. Uczestnik otwiera *Sprawdź węzeł → Wybierz zarządzającego* i wskazuje klucz
urządzenia; gdy ponad połowa tych, którzy są uczestnikami co najmniej od miesiąca, wskaże ten sam klucz, węzeł
ogłasza go w łańcuchu samej społeczności. Po czasie oczekiwania przyjętym w społeczności ten klucz zarządza —
chyba że w tym czasie zarządzający, który ma jeszcze klucz, powie „nie”.

Tam, gdzie dołączyć może każdy, takich wyborów nie ma: każdy mógłby zagłosować na siebie. Tam jedynym
zabezpieczeniem jest dwóch zarządzających.

## Kopia i przeniesienie społeczności

*Społeczność → Przenieś albo zachowaj kopię → Zachowaj kopię* zapisuje całą społeczność jako jeden plik: zapisy
każdego konta, uczestników, zasady, politykę — i parę kluczy społeczności. **Kto ma plik, może działać jako węzeł
tej społeczności: przechowuj go jak klucz.** Trzymaj kilka kopii.

Żeby przenieść społeczność — z huba na własny węzeł albo na inny hub: w nowym miejscu wybierz *Przenieś
społeczność z pliku* tam, gdzie tworzy się społeczności. Każdy zapis jest sprawdzany przed przyjęciem; stan
każdego konta jest liczony od nowa z jego łańcucha. Potem w starym miejscu — *Przeniosła się: zamknij tutaj* z
nowym adresem: od tej chwili stare miejsce niczego nie zmienia, a aplikacja każdego uczestnika, która tam trafi,
jest kierowana dalej. Linie zaufania z innymi społecznościami otwiera się ponownie z nowego adresu
(*Społeczność → Linie zaufania*, ten sam partner, nowy adres).

### Kopie zapasowe — dla operatora huba

Hub, na którym działają cudze społeczności, ma obowiązek robić im kopie zapasowe. Ustaw token huba (`npx wrangler
secret put HUB_TOKEN` w `worker/`; na własnej maszynie — `HUB_TOKEN` w środowisku), trzymaj go w `.env` i uruchamiaj

```sh
scala-cli run scripts/backup.scala -- https://your-hub.example
```

Skrypt zapisuje wszystkie społeczności huba do `backups/<host>-<czas>.json` (nie trafia do gita: są tam klucze
wszystkich społeczności) i odczytuje plik z powrotem. Każda społeczność w nim to plik, który inny hub przyjmuje,
jak opisano wyżej.

## Czego jeszcze nie ma

- Linie zaufania łączą dwie społeczności, z których każda otworzyła linię do drugiej (**Społeczność**, potem
  **Linie zaufania**), w tej samej jednostce. Jeszcze nie ma: płatności przez trzecią społeczność i linii między
  różnymi jednostkami.
- Przeniesienie społeczności z jednego huba na inny razem z jej historią.
- Konto wspólne, którego posiadaczem jest inne konto wspólne; odbieranie pożyczonej linii.
- Regulamin dla operatora huba; kopie zapasowe inne niż te, które daje platforma albo twoje własne kopie.
