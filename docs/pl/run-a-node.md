[English](../run-a-node.md) · [Русский](../ru/run-a-node.md) · [Українська](../uk/run-a-node.md) · **Polski**

# Uruchom węzeł

Węzeł to komputer społeczności: przyjmuje uczestników, prowadzi łańcuch każdego konta, współpodpisuje każdy zapis
i publikuje podpisane podsumowanie wszystkiego, co przechowuje. Uruchomić go może każdy. Najpierw przeczytaj,
[jak to działa](how-it-works.md).

Zanim zaprosisz kogokolwiek, kogo nie znasz osobiście: operator węzła odpowiada za jego uczestników i za jego
podatki. Formę prawną i przepisy o usługach płatniczych w swoim kraju ustal z prawnikiem
([otwarte pytania](../../specs/mutual.md#questions-for-a-lawyer-and-a-tax-adviser), po angielsku).

## Na jednej maszynie

Potrzebna jest Java 21 lub nowsza oraz [sbt](https://www.scala-sbt.org/).

```sh
git clone https://github.com/okay-effects/okay-mutual && cd okay-mutual
sbt site host/assembly                       # the site with the wallet, and the node's jar
NODE_NAME="Our Town" JOIN=open DEFAULT_CREDIT=10000 DEFAULT_CEILING=30000 java -jar target/okay-mutual-node.jar
```

Otwórz http://127.0.0.1:8787/wallet/ — to portfel, który rozmawia z twoim węzłem.

Przy pierwszym uruchomieniu węzeł zapisuje w `node-data/` dwa pliki, które możesz odczytać tylko ty:

- `node.key` — klucz węzła. Kto go ma, może podpisywać jako twój węzeł. Zrób kopię zapasową; nigdy go nikomu nie
  udostępniaj.
- `setup.token` — wypisywany raz, przy pierwszym uruchomieniu. W portfelu wybierz **Załóż tę społeczność** i wpisz go:
  to otwiera własne konto społeczności kluczem twojego urządzenia. Od tej chwili token
  niczego już nie otwiera.

Wszystko, co węzeł wie, jest w `node-data/okay.sqlite`. Rób kopie zapasowe całego katalogu.

| Zmienna | Znaczenie | Domyślnie |
|---|---|---|
| `NODE_NAME` | nazwa społeczności, tak jak pokazują ją portfele | `A community` |
| `NODE_UNIT` | nazwa jednostki; jednostka liczy się jak jedna jednostka waluty krajowej | `PLN` |
| `JOIN` | `open`: konto może otworzyć każdy; cokolwiek innego: tylko ci, których przyjmiesz | tylko przyjęci |
| `DEFAULT_CREDIT`, `DEFAULT_CEILING` | limity nowego uczestnika, w setnych częściach (`10000` to 100.00) | `0` |
| `NODE_DIR` | gdzie przechowywane są dane | `node-data` |
| `PORT`, `HOST` | gdzie węzeł nasłuchuje | `8787`, `127.0.0.1` |
| `SITE` | katalog strony, którą węzeł ma serwować | `target/site` |

Żeby węzeł był dostępny dla innych, postaw go za serwerem WWW z HTTPS (klucze portfela wymagają bezpiecznej
strony) i ustaw `HOST=0.0.0.0` albo przekieruj do niego ruch przez proxy.

## Kto zarządza węzłem

Nikt z zewnątrz i żadne hasło. Własne konto społeczności jest kontem jak każde inne: urządzenia, których klucze
mogą nim *zarządzać*, to zarząd społeczności. W portfelu, kiedy używasz tego konta:

- **Społeczność** — uczestnicy, suma wszystkich sald (zawsze zero) i limity każdego uczestnika, które
  można tu ustawić.
- **Urządzenia** — dodaj urządzenie kolejnego członka zarządu albo któreś usuń. To ten sam ekran,
  którego każdy używa dla swojego telefonu i laptopa.

Polecenie dla węzła (ustawienie limitu) jest podpisane takim kluczem, jest ważne przez dziesięć minut i przyjmowane
tylko raz.

Tam, gdzie dołączanie nie jest otwarte, uczestnika przyjmuje się poleceniem członka zarządu, do którego dołączony
jest pierwszy zapis nowej osoby, podpisany przez nią samą. Portfel nie ma jeszcze do tego ekranu.

## Czego jeszcze nie ma

- Linie zaufania do innych węzłów: zaprojektowane ([protokół](../../specs/protocol.md), po angielsku), niezbudowane.
  Dziś węzeł to jedna społeczność.
- Punkt wejścia dla Cloudflare Workera: kod węzła tam działa (jest testowany na takiej samej chmurze w pamięci),
  ale paczka nie jest jeszcze przygotowana.
- Konta rodzinne i firmowe, których posiadaczami są inne konta, dwa podpisy, odzyskiwanie utraconego klucza przez
  węzeł.
- Kopie zapasowe inne niż skopiowanie katalogu; ograniczenie częstości otwierania kont tam, gdzie dołączanie jest
  otwarte.
