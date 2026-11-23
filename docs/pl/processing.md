[English](../processing.md) · [Русский](../ru/processing.md) · [Українська](../uk/processing.md) · **Polski**

# Umowa powierzenia przetwarzania danych osobowych (wzór) — społeczność i operator huba

Wzór na podstawie art. 28 RODO, napisany przez inżynierów do sprawdzenia przez prawnika; nie stanowi porady
prawnej. Wszystko, co jest w nawiasach kwadratowych, należy uzupełnić. Umowę akceptuje w imieniu społeczności ten,
kto tworzy społeczność na publicznym hubie ([regulamin](terms.md), pkt 2.1).

## 1. Strony

- **Administrator danych**: społeczność [jej nazwa, jej adres na hubie; jej osoba prawna, jeśli ją ma; w przeciwnym
  razie osoby, które nią zarządzają], działająca przez osoby, które mają klucze jej własnego konta.
- **Podmiot przetwarzający**: [nazwa, adres i adres e-mail operatora], operator publicznego huba.

## 2. Przedmiot, charakter, cel, czas trwania

2.1. Podmiot przetwarzający hostuje węzeł Okay Mutual administratora: przechowuje dane administratora, wykonuje na
nich obliczenia i udziela odpowiedzi tak, jak robi to oprogramowanie, w celu prowadzenia księgi rachunkowej
uczestników administratora.

2.2. Czas trwania: dopóki społeczność utrzymuje swój węzeł na hubie i do czasu usunięcia jej danych zgodnie
z pkt 9.

## 3. Dane i osoby, których dane dotyczą

- **Osoby, których dane dotyczą**: uczestnicy administratora, osoby, które proszą o przyjęcie, osoby, które proszą
  o odzyskanie konta.
- **Dane**: imiona i nazwiska albo nazwy w podanym brzmieniu; rodzaje kont; klucze publiczne urządzeń; zapisy kont
  (kwoty, czas, konta po drugiej stronie, klucze, odciski); tytuły płatności; ogłoszenia na tablicy; karty
  z pieczątkami i pieczątki; wnioski i głosy; regulamin i polityka społeczności. Nie przewiduje się przetwarzania
  szczególnych kategorii danych osobowych ([regulamin](terms.md), pkt 3.3).

## 4. Polecenia

Udokumentowanymi poleceniami administratora są: oprogramowanie w opublikowanej postaci (jego otwarty kod źródłowy
w wersji, którą uruchamia hub) oraz polecenia podpisane przez zarządzających administratora. Podmiot przetwarzający
nie robi z danymi niczego innego, chyba że wymaga tego prawo — wówczas najpierw informuje o tym administratora,
o ile prawo na to pozwala.

## 5. Poufność

Dostęp do konta huba i do kopii zapasowych mają wyłącznie operator oraz osoby zobowiązane do zachowania tajemnicy.

## 6. Bezpieczeństwo (art. 32 RODO)

Podmiot przetwarzający stosuje co najmniej środki wymienione w załączniku i doskonali je wraz z rozwojem stanu
wiedzy technicznej.

## 7. Dalsze podmioty przetwarzające

Administrator upoważnia podmiot przetwarzający do korzystania z dalszych podmiotów przetwarzających wymienionych
w załączniku. Podmiot przetwarzający informuje administratora o każdym nowym dalszym podmiocie przetwarzającym
z [30-dniowym] wyprzedzeniem; administrator może wnieść sprzeciw i odejść (pkt 9). Podmiot przetwarzający nakłada
na każdy z nich te same obowiązki.

## 8. Pomoc

Podmiot przetwarzający pomaga administratorowi odpowiadać osobom, których dane dotyczą, i wywiązywać się z jego
obowiązków (bezpieczeństwo, naruszenia, oceny skutków dla ochrony danych). Wiele z tego robi oprogramowanie: dostęp
i sprawdzanie (*Sprawdź węzeł*), kopia (*Historia*, plik społeczności), usunięcie (zamknięcie konta).

## 9. Zakończenie

Gdy społeczność odchodzi albo kończy działalność: podmiot przetwarzający wydaje administratorowi na żądanie jego
plik (administrator może go też w każdej chwili pobrać sam), usuwa dane społeczności z huba w ciągu [30 dni]
i pozwala, by jej kopie zapasowe wygasły w ciągu [30 dni].

## 10. Naruszenia

Podmiot przetwarzający zgłasza administratorowi naruszenie ochrony danych osobowych bez zbędnej zwłoki, a najpóźniej
w ciągu [48 godzin] od jego stwierdzenia, przekazując to, co o nim wie.

## 11. Informacje i audyty

Podmiot przetwarzający udostępnia administratorowi informacje niezbędne do wykazania przestrzegania niniejszej umowy
i umożliwia przeprowadzanie inspekcji w rozsądnym zakresie, terminie i kosztach.

## 12. Odpowiedzialność; postanowienia końcowe

Odpowiedzialność na zasadach określonych w RODO. Niniejsza umowa uzupełnia [regulamin](terms.md); w razie
rozbieżności w sprawach danych osobowych pierwszeństwo ma niniejsza umowa. Prawo polskie.

## Załącznik A. Środki

- Każdy zapis konta jest podpisywany przez urządzenie jego posiadacza i przez węzeł; każdy może sprawdzić łańcuch,
  a zmieniony zapis jest widoczny. Hub nie przechowuje klucza prywatnego żadnego uczestnika.
- Połączenia są szyfrowane (HTTPS). Każde konto jest przechowywane we własnym, odizolowanym obiekcie przechowywania.
- Dostęp operatora do konta huba jest chroniony [silnym hasłem i drugim składnikiem uwierzytelniania].
- Kopie zapasowe są wykonywane przez operatora [codziennie], przechowywane [w postaci zaszyfrowanej / z dostępem
  ograniczonym do operatora] przez [30 dni], a ich odtwarzanie jest testowane.
- Usunięcie: zamknięcie konta od razu usuwa to, co podlega usunięciu (zob. [polityka prywatności](privacy.md)).

## Załącznik B. Dalsze podmioty przetwarzające

| Kto | Co | Gdzie |
|---|---|---|
| Cloudflare, Inc. | obsługuje hub: Workers, Durable Objects | sieć globalna; [umowa powierzenia i podstawa przekazania] |
| GitHub, Inc. | udostępnia stronę i stronę aplikacji | Stany Zjednoczone; [umowa powierzenia i podstawa przekazania] |
