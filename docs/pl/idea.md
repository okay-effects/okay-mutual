[English](../idea.md) · [Русский](../ru/idea.md) · [Українська](../uk/idea.md) · **Polski**

# Idea

## Problem

Krawcowa ma w tym miesiącu wolne godziny i żadnych klientów. Piekarz chce oddać marynarkę do przeróbki i ma chleb,
którego nikt nie kupił. Programistka zrobiłaby krawcowej stronę do zapisów, a sama potrzebuje i chleba, i marynarki.
Każde z nich ma coś do dania i czegoś potrzebuje. Nic się nie dzieje, bo każde czeka, aż najpierw pojawią się
pieniądze.

Pieniędzy brakuje właśnie wtedy, kiedy są najbardziej potrzebne: między fakturami, między zleceniami, zanim nowa
firma zdobędzie pierwszego klienta, w czasie spowolnienia, kiedy banki pożyczają mniej. Praca, towary i potrzeby
są wciąż na miejscu. Brakuje tylko sposobu, żeby prowadzić rachunek.

## Czym jest kredyt wzajemny

Wystarczy prowadzić rachunek. Dajmy każdemu z tej trójki konto, które zaczyna od zera:

| Co się dzieje | Piekarz | Krawcowa | Programistka |
|---|---|---|---|
| na początku | 0 | 0 | 0 |
| krawcowa przerabia piekarzowi marynarkę za 80 | −80 | +80 | 0 |
| programistka robi krawcowej stronę za 200 | −80 | −120 | +200 |
| piekarz zaopatruje biuro programistki za 150 | +70 | −120 | +50 |

Doszło do trzech transakcji i nikt nie potrzebował pieniędzy, żeby zacząć. Spójrz na ostatni wiersz: sumuje się do
zera i zawsze tak będzie. Nikt nie „ma” jednostek tak, jak ma się monety. Saldo dodatnie znaczy: *dałem
społeczności więcej, niż wziąłem.* Ujemne: *wziąłem więcej, niż dałem, i jestem to winien — oddam własną pracą.*

To cały mechanizm. Jednostki tworzy kupujący w chwili zakupu, a znikają one, kiedy ten sam uczestnik coś sprzeda.
Nie ma emitenta, nie ma odsetek i nie ma czego „kopać”.

## Dwa ograniczenia, dzięki którym to działa

Gdyby na tym poprzestać, jeden uczestnik mógłby brać bez końca, a inny bez końca dawać. Dlatego każde konto ma dwa
ograniczenia:

- **Dolna granica** — jak głęboko poniżej zera konto może zejść: to jego linia kredytowa. U nowej osoby jest mała;
  rośnie razem z tym, ile uczestnik naprawdę sprzedaje.
- **Pułap** — jak wysoko powyżej zera konto może urosnąć. To zwykle zaskakuje. Uczestnik, który tylko sprzedaje
  i nigdy nie kupuje, wcale nie jest wzorem cnót: wysysa pozostałych, bo oni mogą zarobić tylko wtedy, gdy komuś
  coś sprzedadzą. Kto doszedł do pułapu, musi najpierw coś wydać, zanim sprzeda więcej.

Oba ograniczenia pochodzą z planu, który John Maynard Keynes przygotował w czasie wojny dla handlu między państwami
(Międzynarodowa Unia Clearingowa): zarówno wierzyciele, jak i dłużnicy mieli być w nim popychani z powrotem ku
równowadze. Plan przegrał z dolarem w Bretton Woods; sam pomysł był słuszny.

## To stara rzecz i działa

- **WIR** w Szwajcarii od 1934 roku prowadzi obieg kredytu wzajemnego między małymi i średnimi firmami. Jego obroty
  rosną w czasie recesji, kiedy kredytu bankowego ubywa — robi więc to, do czego go stworzono.
- **Sardex** na Sardynii, założony w 2009 roku, po kryzysie finansowym, łączy kilka tysięcy lokalnych firm. Nauka
  z niego: salda pozostają w ruchu dzięki brokerom, którzy czynnie kojarzą oferty i potrzeby uczestników.
- **Słowenia** od początku lat 90. rozlicza łańcuchy niezapłaconych faktur między firmami przez wielostronną
  kompensatę — bez żadnej nowej waluty.
- Kręgi **LETS** i banki czasu robią to samo między sąsiadami od lat 80. Nauka z nich jest odwrotna: na samych
  wolontariuszach, kiedy w ofercie nie ma nic naprawdę potrzebnego, krąg pozostaje mały.

Bywało też, że to się nie udawało, a porażki uczą nie mniej. Argentyńskie kluby barterowe urosły w 2001 roku do
milionów ludzi i w ciągu roku upadły: ich papierowe bony podrabiano i wypuszczano w nadmiarze, bo nic nie wiązało
bonu z uczestnikiem, który był go winien. Każda decyzja projektowa opisana niżej jest po to, żeby to było
niemożliwe.

## Co tu jest nowego

Dawne sieci działają każda na komputerze jednego operatora. Uczestnicy muszą wierzyć, że jego księgi są rzetelne,
a sieć nie może przerosnąć swojego operatora. Okay Mutual zachowuje ekonomię, a zmienia księgowość:

- **Każdy zapis jest podpisany własnym kluczem uczestnika**, przechowywanym na jego telefonie lub komputerze.
  Nikt — nawet operator — nie może wydać cudzego kredytu i nikt nie może wyprzeć się tego, co podpisał.
- **Każdy może uruchomić węzeł**: społeczność z własnymi uczestnikami i własnymi zasadami. Kod jest otwarty.
- **Społeczności łączą się liniami zaufania**: każda sama decyduje, ile inna społeczność może być jej winna.
  Płatność może przejść z jednej społeczności do drugiej w granicach tych linii.
- **Uczestnicy sami sprawdzają księgi.** Aplikacja w telefonie weryfikuje każdy zapis, który dotyczy jej
  właściciela, i potrafi tego dowieść, gdyby węzeł kiedykolwiek skłamał.

Bierze z kryptowalut to, co czyni je wiarygodnymi — klucze, podpisy, zapisy, których nie da się po cichu
przepisać — a zostawia to, co czyni je powolnymi, kosztownymi i spekulacyjnymi: nie ma monety, nie ma kopania, nie
ma globalnego łańcucha ani kursu, na który można by stawiać. Zobacz: [jak to działa](how-it-works.md).

## Obok pieniądza, nie zamiast niego

Okay Mutual działa równolegle do zwykłego pieniądza i daje wybór. Niczego nie zastępuje.

- Ceny zostają w walucie krajowej; jednostka liczy się jak jedna jej jednostka.
- Transakcja może być częściowo w jednostkach, częściowo w pieniądzu. Każdy sprzedawca sam decyduje, jaką część
  przyjmie w jednostkach — całość, połowę, nic.
- Podatki nalicza się i płaci w pieniądzu, dokładnie tak jak przy każdej sprzedaży. Sprzedaż za jednostki to
  sprzedaż.
- Jednostek nigdy się nie sprzedaje ani nie wymienia na pieniądze, więc nie ma czym spekulować.
- Nikt nie musi przyjmować więcej, niż sam postanowił, i każdy może odejść, doprowadzając swoje saldo do zera.

Czynsz, paliwo i podatki dalej będą opłacane pieniędzmi. Sieć jest dla tej części życia gospodarczego, którą brak
pieniędzy zatrzymuje bez potrzeby.

## Czym to nie jest

- **To nie kryptowaluta.** Nie ma monety i nie ma w co inwestować. Nikt się nie wzbogaci na tym, że dołączył
  wcześnie.
- **To nie marketing sieciowy.** Nikt nie zarabia na werbowaniu. Uczestnik, który przyprowadza nową osobę,
  *poręcza* za nią, pożyczając jej część własnej linii kredytowej — podejmuje ryzyko, a nie zarabia prowizję.
- **To nie sposób na obejście podatków.** Przeciwnie: po każdej transakcji zostaje podpisany zapis, a księgowość
  wynika z niego wprost.
- **To nie obietnica.** Podpis dowodzi, kto co obiecał. Nie dowiedzie, że obietnica zostanie dotrzymana. To opiera
  się — jak zawsze — na tym, że ludzie się znają, na poręczeniu i na limitach, które rosną tylko wraz z historią
  dotrzymanego słowa.

## Dla kogo to jest

- **Małe firmy i samozatrudnieni**: sprzedaj wolne moce, kup to, czego potrzebujesz, nie czekając na gotówkę.
- **Osoby między jedną pracą a drugą albo na starcie**: zarabiaj tym, co umiesz, i daj się znaleźć tym, którzy
  tego potrzebują.
- **Nowe firmy**: linia kredytowa to budżet na sprawdzenie pomysłu — strona, logo, pierwsza partia — a społeczność
  to pierwszy rynek. Zamówienia złożone w jednostkach, zanim produkt powstanie, to najtańszy dowód, że jest popyt.
- **Rodziny, zespoły i organizacje**: wspólne konta z budżetem dla każdej osoby lub działu.
- **Społeczności**: miasto, branża, diaspora — każdy, kto potrafi uruchomić węzeł i jest gotów odpowiadać za jego
  uczestników.

## Czego to od ciebie wymaga

Żeby dawać, zanim się weźmie, albo niedługo potem. Żeby przyjmować jednostki za coś prawdziwego. Żeby wydawać to,
co się zarobiło, a nie gromadzić. I — jeśli za kogoś poręczasz — żeby robić to na serio.

## Na jakim to jest etapie

Projekt i działający prototyp — w [README](../../README.md) (po angielsku) jest dokładnie opisane, co działa dziś.
Formę prawną operatora węzła oraz to, jak w danym kraju stosują się przepisy o podatkach i usługach płatniczych,
trzeba ustalić z prawnikiem i doradcą podatkowym, zanim społeczność zaprosi uczestników, których nie zna osobiście.

## Do poczytania

- Thomas Greco, *The End of Money and the Future of Civilization* — argumenty za kredytem wzajemnym, razem z jego
  historią.
- E. C. Riegel, *Flight from Inflation* — pieniądz jako obietnica uczestnika, pół wieku przed oprogramowaniem.
- Bernard Lietaer, *The Future of Money* — waluty komplementarne na całym świecie.
- David Graeber, *Debt: The First 5,000 Years* (wyd. polskie: *Dług. Pierwsze pięć tysięcy lat*) — kredyt był
  wcześniej niż monety.
- Alfred Mitchell-Innes, *The Credit Theory of Money* (1914) — krótki klasyk.
- Tomaž Fleischman, Paolo Dini, Giuseppe Littera, *Liquidity-Saving through Obligation-Clearing and Mutual Credit*
  (2020) — słoweńska kompensata i Sardex, z liczbami.
- James Stodder, *Complementary Credit Networks and Macroeconomic Stability: Switzerland's Wirtschaftsring* (2009).
- Elinor Ostrom, *Governing the Commons* (wyd. polskie: *Dysponowanie wspólnymi zasobami*) — jak społeczności
  utrzymują przy życiu wspólny zasób.
- Eric Ries, *The Lean Startup* (wyd. polskie: *Metoda Lean Startup*) — najpierw sprawdź najbardziej ryzykowne
  założenie; tak robimy, zobacz [specyfikację](../../specs/mutual.md) (po angielsku).
