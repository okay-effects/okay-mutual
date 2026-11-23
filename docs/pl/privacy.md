[English](../privacy.md) · [Русский](../ru/privacy.md) · [Українська](../uk/privacy.md) · **Polski**

# Polityka prywatności: aplikacja Okay Mutual i publiczny hub

Projekt napisany przez inżynierów do sprawdzenia przez prawnika; nie stanowi porady prawnej. Wszystko, co jest
w nawiasach kwadratowych, należy uzupełnić. Dokument dotyczy aplikacji pod adresem
okay-effects.github.io/okay-mutual oraz publicznego huba pod adresem okay-mutual.okay-effects.workers.dev.
Społeczność, która ma własny węzeł, sporządza własną informację; ta może jej posłużyć za wzór.

## Kto za co odpowiada

- **Twoja społeczność jest administratorem danych** w zakresie tego, co przechowuje o swoich uczestnikach: decyduje,
  kogo przyjmuje, na jakich zasadach i z jakimi limitami. Działa przez osoby, które nią zarządzają (jej założycieli,
  jej zarządzających). Zwracaj się najpierw do nich.
- **Operator huba jest podmiotem przetwarzającym** dla każdej społeczności na publicznym hubie: [nazwa, adres
  i adres e-mail operatora]. Operator utrzymuje maszynę i przetwarza dane społeczności wyłącznie tak, jak stanowią
  oprogramowanie oraz podpisane polecenia społeczności ([umowa powierzenia przetwarzania danych
  osobowych](processing.md)), a także wykonuje kopie zapasowe.
- **Operator jest administratorem danych** wyłącznie w zakresie tego, czego hub i strona potrzebują na własne
  potrzeby: logów technicznych i ochrony przed nadużyciami (zob. niżej).

## Co jest przechowywane i w jakim celu

| Co | Gdzie | W jakim celu | Podstawa prawna |
|---|---|---|---|
| Imię i nazwisko albo nazwa, które podajesz, rodzaj konta (osobiste, firmowe, rodzinne, zespołu) | węzeł twojej społeczności | inni widzą, z kim mają do czynienia | twoja umowa ze społecznością (art. 6 ust. 1 lit. b RODO) |
| Zapisy twojego konta: kwoty, czas, konta po drugiej stronie, klucze, którymi je podpisano, odciski tytułów płatności | węzeł twojej społeczności | sama księga rachunkowa: na ich podstawie oblicza się twoje saldo; każdy uczestnik może je sprawdzić | jak wyżej |
| Tytuł płatności w brzmieniu nadanym przez płacącego | obok zapisu, po obu stronach | twój wyciąg, twoja księgowość | jak wyżej |
| Twoje ogłoszenia na tablicy, karty z pieczątkami i pieczątki | węzeł twojej społeczności | to, co postanowiłeś(-aś) opublikować w swojej społeczności | jak wyżej |
| Wniosek o przyjęcie, wniosek o odzyskanie konta, głos na zarządzającego | węzeł twojej społeczności | do czasu decyzji twojej społeczności | jak wyżej |
| Klucz publiczny twojego urządzenia | węzeł twojej społeczności | aby wiedzieć, że zapis jest twój | jak wyżej |
| Klucz prywatny twojego urządzenia | **wyłącznie w twojej przeglądarce**, nieodczytywalny nawet dla aplikacji | do podpisywania | — (nigdy nie opuszcza urządzenia) |
| Ustawienia aplikacji: które konta są na tym urządzeniu, adres społeczności, język | wyłącznie w twojej przeglądarce | aplikacja cię pamięta | — (nigdzie nie są wysyłane) |
| Adresy IP i logi zapytań | Cloudflare (hub), GitHub (strona) | dostarczanie, bezpieczeństwo, przeciwdziałanie nadużyciom | prawnie uzasadniony interes (art. 6 ust. 1 lit. f RODO) |
| Kopie zapasowe całych społeczności | własna przestrzeń dyskowa operatora | aby przywrócić społeczność po awarii | obowiązek podmiotu przetwarzającego wobec społeczności; prawnie uzasadniony interes |

Bez plików cookie, bez narzędzi analitycznych, bez reklam, bez profilowania i bez zautomatyzowanego podejmowania
decyzji wobec ciebie. Twoje imię i nazwisko oraz twoje zapisy nie są sprzedawane ani przekazywane nikomu spoza
twojej społeczności, z wyjątkiem podmiotów wymienionych niżej oraz przypadków, w których wymaga tego prawo.

## Kto jeszcze przetwarza dane

- **Cloudflare, Inc.** obsługuje hub (Workers, Durable Objects) — jest dalszym podmiotem przetwarzającym operatora.
- **GitHub, Inc.** udostępnia stronę i stronę aplikacji (GitHub Pages) — widzi adresy osób, które ją otwierają.

Obie są spółkami amerykańskimi; dane mogą być przetwarzane poza Europejskim Obszarem Gospodarczym, na podstawie
ram ochrony danych UE–USA (EU–US Data Privacy Framework) albo standardowych klauzul umownych [do sprawdzenia dla
każdej z nich].

## Jak długo

- Dopóki twoje konto jest otwarte: jak wyżej.
- **Po zamknięciu konta** (przez ciebie albo przez twoją społeczność w przypadku osoby, która odeszła): twoje imię
  i nazwisko, tytuły twoich płatności, twoje ogłoszenia na tablicy, twoje karty i pieczątki, twoje wnioski i głosy
  są usuwane od razu. **Co zostaje**: zapisy twojego konta dotyczące kwot, czasu, kluczy i odcisków — bez twojego
  imienia i nazwiska. Opierają się na nich zapisy wszystkich pozostałych uczestników i podpisana głowa
  społeczności; same w sobie ujawniają, kim jesteś, tylko komuś, kto już to wiedział. Druga strona transakcji
  zachowuje własny zapis o niej, wraz z jej tytułem: to jej księgowość.
- Kopie zapasowe: [do 30 dni], po czym są zastępowane nowymi.
- Gdy społeczność opuszcza hub albo kończy działalność: jej dane są usuwane z huba w ciągu [30 dni]; jej kopie
  zapasowe wygasają.

## Twoje prawa

Masz prawo żądać dostępu do swoich danych, ich sprostowania, ich usunięcia, ograniczenia ich przetwarzania, kopii
w powszechnie używanym formacie, a także wnieść sprzeciw wobec ich przetwarzania; masz też prawo wnieść skargę do
Prezesa Urzędu Ochrony Danych Osobowych (uodo.gov.pl) albo do organu nadzorczego twojego państwa.

Wiele z tego aplikacja robi sama: pokazuje twoje zapisy i je sprawdza (*Sprawdź węzeł*), daje twój wyciąg w postaci
tabeli (*Historia → Skopiuj wyciąg*), zamyka twoje konto i usuwa to, co podlega usunięciu (*Urządzenia → Zamknij
konto*). Imię i nazwisko zmieniasz sam (*Urządzenia → Zmień nazwę*). We wszystkich innych sprawach zwróć się do swojej społeczności;
jeśli nie odpowie, zwróć się do operatora.

## Zmiany

Zmiana tej informacji jest publikowana tutaj [30 dni] przed jej wejściem w życie.

## Gdzie te projekty są słabe

Znalezione przy pierwszym czytaniu trzech projektów (tej polityki, [regulaminu](terms.md), [umowy
powierzenia](processing.md)) na tle polskiej praktyki — przez nas, nie przez prawnika; każde do potwierdzenia albo
odrzucenia:

1. **Kto jest administratorem.** Społeczność bez osobowości prawnej, „działająca przez tych, którzy mają jej
   klucze”, to słaby administrator, a druga strona umowy powierzenia jest niejasna; oświadczenie osoby tworzącej
   społeczność, że może ją reprezentować, nie przesądza, czy może reprezentować grupę.
2. **„Oprogramowanie w opublikowanej wersji” jako polecenia.** Wersję uruchamianą na hubie wybiera operator, więc
   decyduje o sposobach przetwarzania — co może uczynić go administratorem; aktualizacja zmienia polecenia
   jednostronnie.
3. **Pomieszane role.** Kopie zapasowe nazwano obowiązkiem podmiotu przetwarzającego *i* prawnie uzasadnionym
   interesem; „jeśli społeczność nie odpowiada, zwróć się do operatora” nakłada na podmiot przetwarzający
   obowiązek, którego nie ma.
4. **To, co zostaje po zamknięciu,** jest spseudonimizowane, nie zanonimizowane: to nadal dane osobowe. Ich
   przechowywanie wymaga wskazania wyjątku z art. 17 ust. 3 RODO.
5. **Czego brakuje w informacji (art. 13 i 14 RODO)**: danych i kontaktu samej społeczności, okresu przechowywania
   logów, informacji, czy podanie danych jest wymogiem umownym, sposobu uzyskania kopii zabezpieczeń przekazania,
   źródła danych o drugiej stronie transakcji (art. 14).
6. **Regulamin a konsumenci.** „Tak jak jest”, brak gwarancji i nieostre ograniczenie odpowiedzialności mogą być
   klauzulami niedozwolonymi wobec społeczności-konsumenta (art. 385^1 i 385^3 KC); licencja oprogramowania nie
   obejmuje samej usługi hostingu. Zmiana regulaminu bez wskazanych ważnych przyczyn i sąd właściwy dla siedziby
   operatora są ryzykowne wobec konsumentów.
7. **Regulamin a ustawa o świadczeniu usług drogą elektroniczną**: brak trybu reklamacji i wymagań technicznych;
   brak zasad aktu o usługach cyfrowych dla hostingu (zgłaszanie i działanie, uzasadnienie zawieszenia, art. 17
   DSA).
8. **Umowa powierzenia a art. 28 ust. 3–4 RODO**: nic o przechowywaniu danych, gdy wymaga tego prawo, nic o
   przekazaniu poza EOG, brak pełnej odpowiedzialności podmiotu przetwarzającego za dalsze podmioty, audyty
   ograniczone do „rozsądnych”, sprzeciw wobec dalszego podmiotu pozostawia tylko odejście.
9. **Przekazanie danych**: dla globalnej sieci Cloudflare i dla GitHub nie wskazano mechanizmu — Data Privacy
   Framework albo standardowe klauzule umowne wciąż „do sprawdzenia”.
10. **Szczególne kategorie danych**: zakazane w regulaminie, ale oprogramowanie nie może temu zapobiec, a umowa
    milczy o tym, co robić, gdy się pojawią.
