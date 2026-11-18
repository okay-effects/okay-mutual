[English](../organisation.md) · [Русский](../ru/organisation.md) · [Українська](../uk/organisation.md) · **Polski**

# Co do kogo należy: fundacja, społeczności i ci, którzy budują

To propozycja napisana przez inżynierów po to, żeby sprawdził ją prawnik, z myślą o starcie w Polsce. Nie jest to
porada prawna. Nic tutaj nie jest jeszcze postanowione ani zarejestrowane. Projekt statutu fundacji jest
[obok](statute.md); pytania do prawnika są [na końcu](#o-co-zapytać-prawnika).

## Pomysł w jednym akapicie

Okay Mutual nie ma centrum: operator huba tylko utrzymuje maszynę, a każda społeczność rządzi się sama.
Organizacja postawiona nad tym nie może tego zmienić. Dlatego nic tutaj „nie zarządza siecią”. Są trzy warstwy,
każda z własną formą prawną, i tylko pierwsza jest organizacją non-profit, którą zakładamy sami.

## Od czego się zaczyna: autor i otwarte oprogramowanie

Punkt wyjścia nie wymaga żadnej organizacji. Jedna osoba napisała oprogramowanie o otwartym kodzie; ludzie
korzystają z niego dobrowolnie. To wszystko — i jest to mocna pozycja, dopóki prawdziwe są cztery rzeczy:

- **Kod jest na otwartej licencji bez gwarancji** (Apache 2.0): każdy może go uruchamiać, zmieniać i
  rozpowszechniać, a autor niczego w jego sprawie nie obiecuje.
- **Autor niczego dla nikogo nie prowadzi.** Kto chce społeczności, sam uruchamia jej węzeł — darmowe konto w
  chmurze i pół godziny ([jak](run-a-node.md)) — i sam za niego odpowiada: za uczestników, zasady, dane.
- **Autor niczego nie bierze z korzystania**: ani opłaty, ani udziału, ani jednostek.
- **Autor o niczym nie decyduje w niczyjej społeczności**: oprogramowanie nie daje autorowi ani klucza, ani
  wejścia.

Wtedy autor jest dla społeczności tym, kim autor arkusza kalkulacyjnego dla księgowego, który go używa.

**Jedyne miejsce, w którym to przestaje być prawdą, to hub, który autor utrzymuje otwarty dla innych.** Kto
hostuje węzeł społeczności, ten przechowuje jej dane i jest jej hostem, z obowiązkami hosta (co najmniej w
zakresie ochrony danych) — czy mu się płaci, czy nie. Dlatego publiczny hub autora jest albo **demonstracją**:
tak nazwaną, do wypróbowania, z danymi co jakiś czas usuwanymi, nie do kont prawdziwej społeczności — albo jest
już hostingiem, a za hosting ktoś musi odpowiadać. W tym miejscu organizacja po raz pierwszy staje się
przydatna.

Wszystko poniżej — fundacja, rada, umowy — to coś, co może przyjść **później**, gdy społeczności będą chciały
wspólnego huba, chronionej nazwy i właściciela, któremu ufają. Żeby zacząć, nic z tego nie jest potrzebne.

## Trzy warstwy

| Warstwa | Czym jest | Forma prawna | Co do niej należy | Czego nigdy nie może robić |
|---|---|---|---|---|
| **Dobro wspólne** | protokół, otwarty kod, dokumenty, nazwa, publiczny hub | **fundacja** | znak towarowy, nazwy domen, prawa do kodu, które jej przekazano, publiczny hub | przechowywać pieniędzy ani jednostek uczestników, pobierać opłat w jednostkach, przyjmować ani wykluczać uczestników społeczności, ustalać polityki społeczności |
| **Społeczność** | uczestnicy, którzy prowadzą rachunki między sobą | własne **stowarzyszenie** albo **spółdzielnia** | jej zasady, przyjmowanie jej uczestników, jej polityka, klucz jej węzła | sprzedawać jednostek za pieniądze ani wymieniać ich z powrotem |
| **Wykonawcy** | każdy, kto rozwija, wdraża i wspiera | zwykłe firmy (jednoosobowa działalność gospodarcza, spółka) | ich własna praca i narzędzia | przedstawiać swoich usług jako decyzji fundacji |

Dlaczego pierwsza warstwa jest non-profit: kod, nazwa i publiczny hub powinny należeć do kogoś neutralnego, komu
społeczności ufają bardziej niż osobie albo firmie — i kogo nie da się kupić, sprzedać ani odziedziczyć.

Dlaczego druga warstwa nie jest jej częścią: społeczność, która dała swoim uczestnikom linie kredytowe, odpowiada
za własne zasady i własne długi. Gdyby fundacja zarządzała społecznościami, „prowadziłaby walutę” — a właśnie tego
unika cały projekt ([lekko](legal.md)).

Dlaczego trzecia warstwa pozostaje komercyjna: pisanie oprogramowania i utrzymywanie maszyn kosztuje, a ludzie,
którzy to robią, powinni móc z tego żyć otwarcie, na fakturę, jak w każdym projekcie open source.

## Jak się do siebie mają

```
                 ┌─────────────────────────────┐
                 │  FUNDACJA                   │   własność: nazwa, domeny, prawa do kodu, publiczny hub
                 │  zarząd · rada · fundator   │   przychody: opłaty w pieniądzu, dotacje, darowizny
                 └──────┬───────────────┬──────┘
        hosting na      │               │  umowy na wykonanie prac, na warunkach
        podstawie umowy,│               │  rynkowych, zatwierdzane przez radę
        za pieniądze    │               │
                 ┌──────┴──────┐   ┌────┴──────────────┐
                 │ SPOŁECZNOŚCI│   │ WYKONAWCY         │
                 │ (każda jest │   │ (firmy, które     │
                 │ własną osobą│   │  rozwijają,       │
                 │ prawną)     │   │  wdrażają,        │
                 │             │   │  wspierają)       │
                 └──────┬──────┘   └───────────────────┘
                        │ społeczność może też zapłacić wykonawcy bezpośrednio
                        │ albo prowadzić własny węzeł i nie potrzebować nikogo
                        ▼
                    jej uczestnicy
```

- **Społeczność i fundacja**: umowa hostingu i umowa powierzenia przetwarzania danych, nic więcej. Społeczność
  może w każdej chwili przenieść się z publicznego huba na własny węzeł; oprogramowanie na to pozwala.
- **Głos społeczności w fundacji**: społeczności, które korzystają z publicznego huba, wskazują przedstawicieli do
  rady fundacji. Rada zatwierdza budżet, opłaty i każdą umowę z podmiotem powiązanym.
- **Wykonawca i fundacja**: zwykła umowa na wykonanie prac, którą zlecają i odbierają ludzie niemający z niej
  korzyści.
- **Nikt nie stoi nad społecznością**: fundacja nie może odczytać kluczy społeczności, wydawać za jej uczestników
  ani zmienić jej polityki. Tak działa oprogramowanie i tak jest zapisane w statucie.

## Dlaczego najpierw fundacja i co potem

- **Fundację** ustanawia jeden fundator, który określa jej cele i statut; kieruje nią zarząd. Może zacząć od
  jednej osoby i niewielkiego funduszu założycielskiego. Pasuje do chwili, w której nie ma jeszcze społeczności.
- **Stowarzyszenie** potrzebuje co najmniej siedmiu członków założycieli (rejestrowe; zwykłe — trzech),
  a rządzi nim głosowanie członków. Lepiej
  pasuje do zasady „nikt nie rządzi” — kiedy są już społeczności, które mogą być jego członkami.
- **Droga**: zacząć jako fundacja; od pierwszej społeczności dać społecznościom rzeczywisty głos przez radę; kiedy
  społeczności będzie kilka, albo zachować ten układ, albo założyć związek społeczności, który przejmie miejsca
  fundacji. Projekt statutu celowo zostawia to otwarte.

## Pieniądze

Co fundacja może przyjmować i w jaki sposób:

| Źródło | Forma |
|---|---|
| opłata społeczności za hosting i wsparcie | faktura, w pieniądzu |
| dotacje na otwarte oprogramowanie i ekonomię społeczną | na podstawie umowy z grantodawcą |
| darowizny | w pieniądzu |
| odpłatne szkolenia, dokumentacja, wydarzenia | w ramach celów statutowych |

Czego nigdy nie przyjmuje: opłaty w jednostkach, udziału w transakcjach uczestników, niczego, co zależy od tego,
ile społeczność handluje. Fundacja nie ma konta w jednostkach żadnej społeczności, chyba że jako zwykły uczestnik
społeczności, do której przystąpiła, na zasadach tej społeczności.

Na co wydaje: publiczny hub, przeglądy bezpieczeństwa, opinie prawne, z których może skorzystać każda społeczność,
tłumaczenia, dokumentację i prace nad budową systemu — na podstawie umów.

## Konflikt interesów, powiedziany wprost

Fundator będzie zapewne także pierwszym wykonawcą: osoba, która ustanowiła fundację, sprzedaje jej również prace
programistyczne. W open source to częste, a dopuszczalne tylko wtedy, gdy jest jawne i kontrolowane:

- fundator nie głosuje nad umową z własną firmą;
- każdą taką umowę zatwierdza rada i publikuje się ją razem z ceną;
- sprawozdanie finansowe fundacji jest publikowane co roku;
- kod pozostaje na otwartej licencji bez względu na to, co się stanie z fundacją albo z którymkolwiek wykonawcą,
  więc nikt nie zależy od żadnego z nich.

## Co przechodzi na fundację i kiedy

1. Nazwa i zgłoszenie znaku towarowego; nazwy domen.
2. Publiczny hub (konto hostingowe) i obowiązki jego operatora wynikające z przepisów o ochronie danych.
3. Licencja na kod. Kod jest już na otwartej licencji, której nikt nie może cofnąć; fundacja potrzebuje prawa do
   wypowiadania się w imieniu projektu i do obrony nazwy, a nie wyłącznej własności kodu.
4. Później: opinie prawne i wzory dokumentów (wzorcowe zasady społeczności, wzór umowy hostingu).

Dopóki fundacja nie istnieje, wszystko to pozostaje przy autorze, a dokumenty mówią to wprost.

## Czego społeczność potrzebuje dla siebie

Społeczność, która wychodzi poza próbę w gronie znajomych, powinna być własną osobą prawną, tak żeby jej zasady
były umową, a długi jej uczestników wobec siebie miały gdzie zostać rozliczone. Zwykłe możliwości: stowarzyszenie
zwykłe (proste, dla uczestników, którzy się znają), stowarzyszenie rejestrowe albo spółdzielnia (kiedy uczestnicy
to głównie firmy i chcą udziałów oraz ograniczonej odpowiedzialności). Fundacja może zaproponować wzorcowe zasady;
za nikogo nie wybiera.

## O co zapytać prawnika

1. Fundacja czy stowarzyszenie dla pierwszej warstwy, skoro teraz jest jeden fundator, a społeczności będą później?
   Jaki jest najmniejszy rozsądny fundusz założycielski?
2. Czy odpłatny hosting społeczności jest odpłatną działalnością statutową (odpłatną działalnością pożytku
   publicznego), czy działalnością gospodarczą, która wymaga wpisu do rejestru przedsiębiorców i własnej części
   funduszu założycielskiego?
3. Fundator jako wykonawca: co muszą mówić statut i regulamin zarządu, żeby umowy z firmą fundatora były ważne
   i nie zagrażały późniejszemu statusowi organizacji pożytku publicznego.
4. Rada: czy mogą w niej zasiadać przedstawiciele społeczności, które same są osobami prawnymi, i czy można dać
   jej uprawnienie do zatwierdzania umów z podmiotami powiązanymi?
5. Znak towarowy i domeny: przeniesienie czy licencja, i po jakiej wartości?
6. Czy prowadzenie publicznego huba czyni fundację w oczach regulacji finansowej kimś więcej niż dostawcą
   hostingu, biorąc pod uwagę to, [co oprogramowanie robi, a czego nie robi](legal.md)?
7. Ochrona danych: fundacja jako podmiot przetwarzający dla każdej społeczności; co musi zawierać wzór umowy.
8. Likwidacja: komu może przypaść majątek, tak żeby nazwa i hub nie mogły stać się czyjąś prywatną własnością.
