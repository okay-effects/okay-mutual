[English](../how-it-works.md) · [Русский](../ru/how-it-works.md) · [Українська](../uk/how-it-works.md) · **Polski**

# Jak to działa

Najpierw przeczytaj [ideę](idea.md). Ta strona prowadzi uczestnika przez system i przy każdym kroku mówi, co go
chroni. Dokładne formaty są w [protokole](../../specs/protocol.md) (po angielsku). Części oznaczone
*(zaprojektowane)* są opisane w specyfikacji, ale jeszcze niezbudowane; w [README](../../README.md) (po angielsku)
jest napisane, co działa dziś.

## Twoje konto

Dołączasz do **węzła** — czyli do społeczności: firm z jednego miasta, jednej branży, spółdzielni. Społeczność
decyduje, kogo przyjmuje, i daje ci dwa ograniczenia:

- **linię kredytową**: jak głęboko poniżej zera możesz zejść. U nowej osoby jest mała albo zerowa;
- **pułap**: jak wysoko powyżej zera możesz urosnąć, zanim trzeba będzie coś wydać.

Twoje saldo zaczyna od zera. Kwoty liczy się w jednostkach; jednostka liczy się jak jedna jednostka waluty
krajowej.

## Twoje klucze

Kiedy otwierasz aplikację, twój telefon tworzy **klucz**: tajną część, która nigdy nie opuszcza urządzenia,
i publiczną, po której można je rozpoznać. Żeby zapłacić, telefon podpisuje płatność swoim kluczem. Bez tego
podpisu nic nie wychodzi z twojego konta — nawet sam węzeł nie może wydawać za ciebie.

**Każde urządzenie ma własny klucz.** Nie przenosisz tajnego klucza z telefonu na laptop. Laptop tworzy własny
klucz i pokazuje go jako kod QR, a telefon podpisuje: „ten klucz też jest mój”. Jeśli telefon zginie, laptop
podpisuje: „tamten klucz nie jest już mój” — i telefon zostaje odcięty, razem ze wszystkim, do czego wcześniej
upoważnił.

Każdy klucz można ograniczyć: komputer w pracy może wydać najwyżej 200 tygodniowo; stary tablet może tylko
podglądać.

**Jeśli zginą wszystkie urządzenia**, prosisz o odzyskanie konta z nowego (**Straciłem(-am) swoje urządzenia**).
Społeczność musi potwierdzić, że to ty — zna cię; wiadomość nie wystarczy, powinna z tobą porozmawiać. Potem nowe
urządzenie przejmuje konto, ale dopiero po tygodniu, który jest ogłaszany jawnie: przez ten czas każde twoje stare
urządzenie, jeśli się znajdzie albo było tylko zapodziane, pokazuje ostrzeżenie i może to anulować. Jest to możliwe,
bo uczestnicy to znani ludzie: społeczność, która zaufała ci na tyle, żeby dać kredyt, może też przywrócić ci
dostęp. Ale nie po cichu i nie od razu.

## Odejście

Uczestnik odchodzi, zamykając konto (*Urządzenia → Zamknij konto*). Konto zamyka się przy zerze: najpierw zarabia
się to, co się jest winnym, wydaje to, co się ma, oddaje pożyczoną linię — więc zamknięcie nikogo nie zostawia
bez należności. Potem zapisuje się ostatni zapis konta i usuwa wszystko, co było przechowywane obok jego łańcucha:
imię uczestnika, tytuły jego płatności, jego ogłoszenia na tablicy, jego karty z pieczątkami. Zapisy kwot zostają —
bez imienia: opierają się na nich łańcuchy wszystkich pozostałych uczestników i podpisana głowa społeczności.
Płatność na zamknięte konto wraca do nadawcy.

Druga strona transakcji zachowuje swój zapis o niej razem z tytułem: to jej księgowość, a nie dane osoby, która
odeszła. Społeczność może też zamknąć konto kogoś, kto odszedł, nie zamykając go sam — przy zerze (*Społeczność →
Zamknij konta tych, którzy odeszli*). Kogoś, kto odszedł z długiem, społeczność rozlicza według swoich zasad.

## Płatność

Idziesz do fryzjera i płacisz 60.

1. Twój telefon podpisuje: *wyślij 60 fryzjerowi*.
2. Węzeł sprawdza podpis oraz to, czy po odjęciu 60 nie zejdziesz poniżej swojej linii kredytowej, dodaje własny
   podpis i dopisuje zapis do łańcucha twojego konta. Twoje konto zostaje obciążone.
3. Węzeł dopisuje *otrzymano 60* do łańcucha fryzjera — chyba że fryzjer jest już na swoim pułapie; wtedy te 60 od
   razu wraca do ciebie.

Płatność ma nazwę (klucz utworzony przez twoją aplikację), więc jeśli naciśniesz przycisk dwa razy albo ponownie
po zerwanym połączeniu, zapłacisz raz.

## Twój łańcuch

Każde konto ma własny **łańcuch**: listę wszystkiego, co się z nim kiedykolwiek stało. Każdy zapis zawiera odcisk
(skrót) poprzedniego i każdy jest podpisany. Twoje saldo to nie liczba, którą ktoś gdzieś przechowuje; to wynik
zsumowania twojego łańcucha. Twój telefon może policzyć to sam — i może to zrobić każdy, komu pokażesz łańcuch.

Ponieważ każdy zapis zawiera odcisk poprzedniego, nikt nie może usunąć ani zmienić starego zapisu, nie psując
wszystkich podpisów, które są po nim.

W łańcuchu są kwoty i odciski — nie ma twojego imienia i nazwiska, adresu ani tego, za co była płatność. Te
szczegóły są przechowywane obok i na twoją prośbę można je usunąć bez zrywania łańcucha.

## Tablica

Uczestnicy piszą, co oferują i czego szukają: skrócenie spodni za 40, połowa w jednostkach; potrzebne logo. Kto
otworzy ofertę, może zapłacić za nią od razu stamtąd — uczestnik, część ceny w jednostkach i tytuł płatności są już
wypełnione; resztę ceny strony płacą sobie pieniędzmi, poza systemem. Ogłoszenie to podpisane słowo samego
uczestnika i tylko on może je zdjąć.

## Karty z pieczątkami

Wiele sklepów rozdaje papierową kartę: pieczątka za każdy zakup, szósta kawa gratis. Papierowe karty się gubią.
Sklep może tu prowadzić taką kartę dla swoich klientów: mówi, ile pieczątek ją wypełnia i co daje pełna karta.
Przy kasie sprzedawca pokazuje kod na swoim telefonie; klient kieruje na niego aparat, otwiera się aplikacja i
pieczątka jest już na jego karcie. Nagrodę za pełną kartę wydaje się tak samo. Kod działa raz, przez dziesięć
minut.

Pieczątka **nie jest jednostką**: nikt jej nikomu nie jest winien, nie można jej przekazać ani wydać gdzie
indziej — to obietnica rabatu od sklepu, przechowywana tam, gdzie się nie zgubi. Działa też przy zakupach za
zwykłe pieniądze, więc karta to najprostszy sposób, żeby stali klienci sklepu zaczęli korzystać z aplikacji: do
karty wystarczy konto.

## Poręczenie

Nowa osoba nie ma historii, więc nie ma kredytu. Uczestnik, który ją zna, może za nią **poręczyć**: pożyczyć jej
część własnej linii kredytowej. Nowa osoba może teraz kupować; własny zapas poręczyciela kurczy się o tę samą
kwotę. Kiedy nowa osoba zarobi tyle, żeby wrócić do zera, linia wraca do poręczyciela.

Za przyprowadzanie ludzi nie ma żadnej nagrody — jest tylko ryzyko dla własnego zapasu. To celowe: dzięki temu
uczestnicy przyprowadzają ludzi, którym powierzyliby własne pieniądze.

## Rodziny, zespoły, firmy

Konto może należeć nie do klucza urządzenia, lecz do **kont uczestników**. Rodzina otwiera konto wspólne (*Konta →
Konta wspólne*) i wskazuje jako posiadaczy konta Anny i Jana, a konto ich córki — z limitem 50 tygodniowo. Anna
płaci z konta rodziny tym urządzeniem, które ma w ręku; nowy telefon dodaje raz — do własnego konta, i działa on
wszędzie, gdzie jest posiadaczką. Każdy zapis mówi dwie rzeczy: które urządzenie podpisało i w imieniu czyjego
konta — a każdy może to sprawdzić w łańcuchu tamtego konta.

Konto wspólne nie ma własnej linii kredytowej. Wydaje to, co mu zapłacono, albo linię pożyczoną przez uczestnika —
ten sam mechanizm co poręczenie. To właśnie **budżet**: firma otwiera konto zespołu i pożycza mu część swojej linii.
Konto rodziny jest kontem ludzi: tam, gdzie osoby prywatne najpierw zarabiają, a potem wydają, ono też nigdy nie
schodzi poniżej zera.

**Dwa podpisy.** Każde konto może powiedzieć: powyżej tej kwoty — dwóch posiadaczy. Jeden posiadacz proponuje
płatność; czeka ona jawnie do tygodnia; drugi posiadacz ją potwierdza i dopiero wtedy jednostki przechodzą.
Wystarcza to kasjerowi i dyrektorowi, a łatwo to wytłumaczyć.

Jeszcze niezbudowane: konto wspólne, którego posiadaczem jest inne konto wspólne, oraz odbieranie pożyczonego
budżetu z aplikacji.

## Polityki: każdy poziom ustala własną

Nic tu nie jest ustalone raz dla wszystkich. Każdy poziom ustala własną politykę — w granicach tego, na co pozwala
poziom wyżej:

- **Operator huba** mówi, jakie mogą być społeczności na jego maszynie: które sposoby postępowania z osobami
  prywatnymi mogą wybrać, jaka może być najwyższa początkowa linia firmy i ile najwyżej może być winna osoba.
  Operator, który chce gościć tylko ostrożne społeczności, tak to ustawia i żadna społeczność u niego nie pójdzie
  dalej.
- **Społeczność** mówi, jak się do niej dołącza (otwarcie albo za zgodą), jaką linię i pułap dostaje nowa firma,
  czy rodziny i zespoły mogą otwierać konta wspólne, ile czeka odzyskiwanie konta — i jak traktuje **osoby
  prywatne**:

  | Wybór społeczności | Osoba prywatna |
  |---|---|
  | tylko firmy | nie ma konta |
  | najpierw zarób *(domyślnie)* | nie ma linii i nie można jej pożyczyć: zarabia, potem wydaje |
  | tylko linie pożyczone | nie ma własnej linii od społeczności, ale uczestnik może pożyczyć część swojej |
  | linia po historii | dostaje linię od społeczności, gdy zarobiła ustaloną kwotę |
  | linie dla wszystkich | otwiera konto od razu z linią, jak firma |

  Przy każdym wyborze poza dwoma pierwszymi społeczność ustala też, ile najwyżej może być winna osoba. Politykę
  zmienia się z biura społeczności (*Społeczność → Polityka*) podpisanym słowem tych, którzy nią zarządzają;
  zmiana obowiązuje od razu, linie przyznane wcześniej zostają.
- **Rodzina, zespół, firma** mówi, kto jest posiadaczem jej konta, ile każdy posiadacz może wydać na płatność i
  na tydzień oraz powyżej jakiej kwoty potrzeba dwóch podpisów.
- **Osoba** mówi, które urządzenia mają dostęp do jej konta i ile każde może wydawać.

Dwa pierwsze wybory nie dają kredytu konsumentowi; pozostałe dają — a z nim pytania z
[notatek prawnych](legal.md).

## Węzły

Węzeł to społeczność z komputerem: przyjmuje uczestników, ustala limity, porządkuje zapisy każdego konta
i współpodpisuje je. Uruchomić go może każdy — kod jest otwarty i działa na niewielkiej usłudze w chmurze albo na
jednej maszynie.

Węzeł nie może podrobić twoich płatności (nie ma żadnego twojego klucza). Żeby nie mógł po cichu przepisać
historii, po każdej porcji zmian publikuje **podpisane podsumowanie** wszystkiego, co przechowuje. Twoja aplikacja
sprawdza, czy twój łańcuch mieści się w tym podsumowaniu, i zachowuje podsumowania, które widziała. Dwa
podsumowania, które sobie przeczą, to dowód — możliwy do sprawdzenia przez każdego — że węzeł skłamał.

Węzeł jest też kimś w zwykłym sensie: spółdzielnią, stowarzyszeniem, firmą — odpowiada za swoich uczestników
i za swoje podatki.

## Między społecznościami

Dwa węzły, które się znają, otwierają **linię zaufania**: każdy daje drugiemu konto we własnych księgach, z linią
kredytową i pułapem, tak jak każdemu innemu. To określa, ile druga społeczność może być winna pierwszej — i na
odwrót.

Kiedy płacisz komuś z innej społeczności, twój węzeł zapisuje tę kwotę na dobro konta, które tamten węzeł ma
u niego, i daje mu znać, a tamten wypłaca ją swojemu uczestnikowi. Każdy zachowuje podpisane wiadomości drugiego,
więc żaden nie może wyprzeć się tego, co między nimi przeszło.

To właśnie ogranicza oszustwa. Ktoś może założyć węzeł pełen zmyślonych uczestników z nieograniczonym kredytem —
i nic mu to nie da, bo żaden uczciwy węzeł nie dał mu linii zaufania. Społeczność, która jednak taką linię otworzy,
może stracić najwyżej tę linię. Nie da się stworzyć kredytu, który inni byliby zmuszeni przyjąć.

## Co cię chroni

| Co mogłoby pójść źle | Co temu zapobiega |
|---|---|
| ktoś płaci z twojego konta | nie ma klucza twojego urządzenia |
| ktoś ukradł ci telefon | inne urządzenie albo odzyskiwanie dostępu odcina go; jego klucz miał limit |
| płacisz dwa razy przez pomyłkę | nazwa płatności: drugie naciśnięcie trafia na pierwsze |
| uczestnik wydaje ponad swoją linię | węzeł odmawia; jeśli był w zmowie, dowodzą tego dwa podpisane zapisy |
| węzeł zmienia księgi | twój łańcuch przestaje pasować do jego podpisanego podsumowania — a dowód masz ty |
| pojawia się fałszywa społeczność | nie ma linii zaufania; jeśli ma jedną, strata to najwyżej ta linia |
| uczestnik nigdy nie spłaca długu | tego nie zatrzyma żaden program: robią to limity, poręczenie i to, że ludzie się znają |

Ostatni wiersz jest tym uczciwym. System dowodzi, kto co obiecał, i trzyma każdą stratę w granicach limitów, które
ktoś świadomie ustalił. Nie sprawia, że ludzie dotrzymują obietnic.

## Dlaczego nie ma blockchaina

Kryptowaluta strzeże jednego rzadkiego dobra, więc cały świat musi się zgodzić co do jednej kolejności wszystkich
płatności. Do tego służą kopanie i staking i dlatego są powolne i drogie.

Tutaj nic nie jest rzadkie. Jednostka to obietnica jednego uczestnika; trzeba zapobiec tylko temu, żeby obietnice
tego uczestnika niepostrzeżenie przerosły jego limity. Wystarczy do tego uporządkować zapisy *jednego konta* — co
robi węzeł tego konta — a nie zapisy całego świata. Dlatego sieć rośnie przez dodawanie kont i węzłów; żaden krok
nigdy nie czeka na całość.

## Jak to rośnie

Więcej uczestników w węźle, więcej węzłów, więcej linii zaufania. Bez centralnego rejestru, bez pytania nas
o zgodę. Węzeł sam wybiera swoje zasady; protokół ustala tylko, czym jest poprawny zapis, tak żeby każda aplikacja
mogła sprawdzić każdy węzeł. Ta sama aplikacja działa z każdym węzłem, który mówi tym protokołem.
