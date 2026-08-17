# Reguły pracy w repozytorium

> Status: obowiązujący indeks i zasady interpretacji

Każda osoba i każdy agent pracujący w repozytorium czyta przed zmianą nadrzędne decyzje
architektoniczne, główne założenia projektowe oraz wszystkie pliki Markdown z tego katalogu. Ten
indeks ułatwia odnalezienie reguł, ale nie zastępuje przeczytania dokumentów źródłowych.

## 1. Indeks

| Dokument | Zakres |
|---|---|
| [`../architectural_decisions.md`](../architectural_decisions.md) | Nadrzędny rejestr najbardziej krytycznych decyzji projektu. |
| [`../first_design_ideas.md`](../first_design_ideas.md) | Uzgodniona architektura, granice modułów i feature'ów oraz zachowanie systemu. |
| [`clean-code.md`](clean-code.md) | Czytelność, projektowanie klas, nazewnictwo, nullability oraz frontend. |
| [`documentation.md`](documentation.md) | Dokumentacja zmian i obowiązkowy angielski Javadoc. |
| [`formating.md`](formating.md) | Wygląd kodu: wcięcia, odstępy, importy i kolejność elementów w klasie. |
| [`integration-tests.md`](integration-tests.md) | Izolacja, struktura i uruchamianie testów integracyjnych. |
| [`maven.md`](maven.md) | Minimalna, czytelna konfiguracja POM i zarządzanie wersjami. |
| [`security.md`](security.md) | Bezpieczeństwo, ochrona sekretów, walidacja granic oraz logowanie. |
| [`unit-tests.md`](unit-tests.md) | Zakres i struktura testów jednostkowych, AssertJ oraz zasady mockowania. |

Plany implementacji poszczególnych feature'ów powstają osobno, bezpośrednio przed pracą nad danym
feature'em. Nie trafiają do tego katalogu jako globalne reguły i nie zmieniają samodzielnie głównych
założeń projektowych.

## 2. Kolejność pierwszeństwa

Reguły interpretujemy w następującej kolejności:

1. aktualne, jawne polecenie użytkownika i zaakceptowana przez niego zmiana decyzji;
2. krytyczne decyzje z `.docs/architectural_decisions.md`;
3. szczegółowe uzgodnienia z `.docs/first_design_ideas.md`;
4. wyspecjalizowana reguła z dokumentu dotyczącego danego obszaru;
5. ogólna reguła czystego kodu;
6. plan implementacji aktualnego feature'a;
7. zastany kod i dotychczasowy zwyczaj w repozytorium.

Reguła bardziej szczegółowa doprecyzowuje regułę ogólną, ale nie unieważnia jawnej decyzji
projektowej. Plan feature'a może wybierać rozwiązanie w granicach dokumentacji globalnej, lecz nie
może jej po cichu nadpisywać.

Zastany kod nie jest automatycznie wzorcem. Jeżeli jest sprzeczny z aktualnymi regułami, nowa zmiana
nie powiela naruszenia, a bezpośrednio dotknięty fragment jest porządkowany w rozsądnym zakresie.

## 3. Sprzeczności i brak decyzji

- Sprzeczności między dokumentami zgłaszamy przed implementacją; nie wybieramy wygodniejszej reguły
  bez uzasadnienia.
- Zmianę uzgodnionej decyzji zapisujemy w odpowiednim dokumencie w tej samej zmianie co implementację.
- Jeżeli dokument wyspecjalizowany nie opisuje przypadku, stosujemy reguły ogólne, KISS i YAGNI.
- Brak implementacji w pustym lub nieukończonym module nie oznacza zgody na dowolną architekturę.
- Tymczasowe odstępstwo musi mieć jawny powód, ograniczony zakres i warunek usunięcia. Istotne
  odstępstwo wymaga akceptacji użytkownika.

## 4. Definition of Done

Zmiana jest kompletna, gdy wszystkie punkty mające zastosowanie są spełnione:

- zachowanie odpowiada zaakceptowanemu zakresowi feature'a i głównym decyzjom projektowym;
- granice modułów, feature'ów i kierunek zależności pozostają zachowane;
- elementy mają najmniejszą potrzebną widoczność, a serwisy są wstrzykiwane przez interfejsy z
  pakietu `service`, implementowane w `service.impl`;
- kod jest prosty, czytelny, poprawnie nazwany i nie zawiera niepotrzebnych abstrakcji;
- Lombok usuwa możliwy do bezpiecznego wygenerowania boilerplate, w szczególności zapewnia
  `@Log4j2` klasom wymagającym loggera, bez ukrywania kontraktów i walidacji; dla prostych,
  niemutowalnych nośników danych pierwszeństwo przed klasą Lombok ma Java `record`;
- publiczne kontrakty referencyjne mają informacyjne adnotacje nullability, a `@Nonnull` jest
  egzekwowane przez `requireNonNull()` zgodnie z regułami projektu;
- zachowanie istotne dla bezpieczeństwa waliduje dane, chroni sekrety i egzekwuje autoryzację;
- klasy wykonujące operacje mają odpowiednie logi `INFO` i `DEBUG`, bez ujawniania danych wrażliwych;
- nowa lub zmieniona logika ma testy jednostkowe obejmujące przypadki typowe, brzegowe, `null` oraz
  wyjątki; pasywne typy korzystają wyłącznie z jawnych wyjątków opisanych w `unit-tests.md`;
- wszystkie wymagane Javadoc są aktualne i napisane po angielsku;
- odpowiednia dokumentacja została zaktualizowana razem ze zmianą;
- POM zawiera wyłącznie potrzebne wpisy i spełnia reguły Maven, jeżeli został zmieniony;
- nie pozostawiono martwego kodu, nieaktualnych komentarzy, sekretów, przypadkowych plików ani zmian
  niezwiązanych z zakresem;
- nie pozostawiono pustych pakietów po przeniesieniu lub usunięciu klas;
- wykonano testy i inne dostępne sprawdzenia proporcjonalne do ryzyka zmiany, a wynik jest znany przed
  przekazaniem pracy.

Testy integracyjne stosujemy dla zachowania zależnego od współpracy frameworka lub infrastruktury,
którego test jednostkowy nie potwierdza, zgodnie z `integration-tests.md`. Nie zastępują testów
jednostkowych logiki klas.
