# Reguły dokumentowania zmian

> Status: obowiązujące dla całego repozytorium

Dokumentacja jest częścią implementacji. Zmiana kodu nie jest kompletna, jeżeli pozostawia nieaktualny
Javadoc, dokument projektowy, konfigurację przykładową albo opis zachowania widocznego dla użytkownika.

## 1. Zasady ogólne

- Dokumentacja opisuje odpowiedzialność, kontrakt, ograniczenia i przyczyny istotnych decyzji.
- Nie powiela kodu linia po linii i nie opisuje oczywistej składni.
- Opis musi odpowiadać aktualnemu zachowaniu po zmianie. Nie dokumentujemy planowanego zachowania jako
  już istniejącego.
- Nazwy klas, metod, parametrów, stanów i właściwości konfiguracyjnych zapisujemy dokładnie tak jak
  w kodzie.
- Dokumentację aktualizujemy w tej samej zmianie co kod, którego dotyczy.
- **Cały Javadoc jest zawsze pisany w języku angielskim.** Reguła nie ma wyjątków i obowiązuje
  niezależnie od języka dokumentacji projektowej, interfejsu użytkownika, zadania lub rozmowy.
  Nie wolno mieszać języków w obrębie Javadoc.
- Istotne odstępstwo od reguł musi mieć krótkie uzasadnienie w review.

## 2. Kiedy aktualizować dokumentację repozytorium

Zmiana wymaga aktualizacji odpowiedniego pliku w `.docs`, gdy wpływa na:

- architekturę, granice modułów albo dozwolony kierunek zależności;
- publiczny kontrakt między feature’ami lub modułami;
- cykl życia serwera, maszynę stanów, odzyskiwanie procesu albo bezpieczeństwo danych;
- konfigurację, układ katalogów, wymagane zależności lub sposób uruchomienia;
- zachowanie panelu widoczne dla użytkownika;
- założenie lub ograniczenie, na którym opiera się późniejsza implementacja.

`.docs/architectural_decisions.md` jest nadrzędnym rejestrem krytycznych decyzji projektowych, a
`.docs/design.md` zawiera ich szczegóły oraz pozostałe uzgodnione założenia. Nie należy
zmieniać decyzji w tych plikach bez akceptacji użytkownika. Plany realizacji poszczególnych feature’ów
powstają osobno i nie są dopisywane do dokumentów z decyzjami i głównymi założeniami.

## 3. Dokumentacja projektowa systemu w katalogu `dokumentacja/`

Poza regułami i decyzjami z `.docs` repozytorium utrzymuje osobną dokumentację projektową opisującą
sam system: jego funkcjonalności i sposób ich działania. Ten sam katalog zawiera instrukcję obsługi
dla użytkownika końcowego, opisaną w sekcji 3.6. `.docs` odpowiada na pytanie „dlaczego tak
zdecydowaliśmy”, a `dokumentacja/` na pytanie „co system robi i jak to realizuje”. Nie powielamy
treści między tymi katalogami; zamiast kopiowania decyzji wstawiamy odnośnik do właściwego dokumentu
w `.docs`.

### 3.1 Lokalizacja i układ plików

Dokumentacja projektowa znajduje się w katalogu `dokumentacja/` w głównym katalogu projektu, obok
`pom.xml`. Obowiązuje następujący układ:

```text
vdeck/
├── pom.xml
└── dokumentacja/
    ├── dokumentacja-projektowa.md          ← główny dokument opisujący system
    ├── diagramy/
    │   ├── mermaid-config.json             ← konfiguracja renderowania obrazków
    │   ├── <feature>/                      ← jeden katalog = jeden feature
    │   │   ├── klasy-<obszar>.mmd          ← źródło diagramu
    │   │   ├── przeplyw-<proces>.mmd
    │   │   └── img/                        ← obrazki wygenerowane ze źródeł
    │   │       ├── klasy-<obszar>.svg
    │   │       └── przeplyw-<proces>.svg
    │   └── wspolne/                        ← diagramy nienależące do żadnego feature'a
    │       ├── przeplyw-<proces>.mmd
    │       └── img/
    │           └── przeplyw-<proces>.svg
    └── instrukcja-uzytkownika/             ← instrukcja obsługi, osobne reguły w sekcji 3.6
```

- `dokumentacja-projektowa.md` jest jedynym punktem wejścia. Jeżeli dokument urośnie na tyle, że
  przestaje być czytelny, wydzielamy pliki tematyczne w tym samym katalogu, a główny dokument
  pozostaje spisem treści i odsyła do nich odnośnikami.
- Podkatalog `diagramy/` zawiera źródła diagramów, po jednym diagramie na plik, oraz wygenerowane
  z nich obrazki.
- **Obrazek wygenerowany ze źródła leży w podkatalogu `img/` katalogu feature'a** i ma tę samą nazwę
  co źródło, z rozszerzeniem `.svg`. Obrazki są wersjonowane w repozytorium — bez nich dokumentacja
  nie pokazuje diagramów.
- **Diagramy są podzielone na katalogi w regule „jeden katalog = jeden feature".** Katalog nazywa się
  dokładnie tak jak pakiet feature'a w `com.github.pplociennik.vdeck.feature`, na przykład
  `diagramy/authentication/`. Żaden plik `.mmd` nie leży bezpośrednio w `diagramy/`.
- Każdy katalog feature'a ma własny podkatalog `img/`; obrazki nie są trzymane wspólnie ani obok
  źródeł.
- Diagram opisujący zagadnienie przekrojowe albo infrastrukturę, czyli nienależący do żadnego
  pojedynczego feature'a — jak start aplikacji czy konfiguracja — trafia do katalogu
  `diagramy/wspolne/`. `wspolne/` nie jest workiem na diagramy, których właściciela nie chciało się
  ustalić: diagram dotyczący jednego feature'a zawsze należy do jego katalogu.
- Katalog feature'a powstaje razem z pierwszym jego diagramem i jest usuwany razem z ostatnim.
- Nazwy plików zapisujemy małymi literami, z myślnikami, bez polskich znaków diakrytycznych.
- Dokumentację projektową piszemy po polsku, spójnie z pozostałymi dokumentami w repozytorium.
  Nazwy klas, metod, pakietów, endpointów, właściwości konfiguracyjnych i stanów podajemy dokładnie
  tak jak w kodzie, bez tłumaczenia. Reguła obowiązkowego angielskiego Javadoc pozostaje niezmieniona.

### 3.2 Spis treści

Główny dokument zaczyna się od spisu treści z odnośnikami do wszystkich sekcji pierwszego i drugiego
poziomu. Spis treści jest aktualizowany razem z dodaniem, usunięciem lub zmianą nazwy sekcji.

### 3.3 Zakres i podział na sekcje

Dokument opisuje wszystkie zaimplementowane funkcjonalności systemu, z czytelnym podziałem na sekcje.
Opisujemy stan faktyczny — funkcjonalność trafia do dokumentacji dopiero wtedy, gdy jest
zaimplementowana. Planowanego zachowania nie przedstawiamy jako gotowego; jeżeli jest potrzebne dla
kontekstu, jawnie oznaczamy je jako nieukończone.

Dokument zawiera co najmniej:

- krótkie wprowadzenie: przeznaczenie systemu, główne moduły i sposób uruchomienia;
- przekrojowy opis architektury na poziomie modułów i feature'ów;
- osobną sekcję dla każdej funkcjonalności;
- sekcje przekrojowe dla zagadnień wspólnych, takich jak konfiguracja, bezpieczeństwo, persystencja
  i logowanie.

Sekcja pojedynczej funkcjonalności opisuje, stosownie do jej charakteru:

- odpowiedzialność funkcjonalności i problem, który rozwiązuje;
- widoczne zachowanie: ekrany, endpointy, akcje użytkownika i wynik ich wykonania;
- najważniejsze komponenty realizujące funkcjonalność wraz z pakietami, w których się znajdują;
- przebieg głównego scenariusza oraz istotnych wariantów i błędów;
- dane utrwalane oraz ich cykl życia;
- konfigurację wpływającą na zachowanie funkcjonalności;
- ograniczenia i znane luki.

### 3.4 Diagramy

Każdy proces złożony — obejmujący kilka komponentów, stanów, kroków w czasie albo wariantów błędu —
wymaga diagramu. W szczególności dotyczy to procesów opisanych w sekcji 8 tego dokumentu. Stosujemy
dwa rodzaje diagramów:

- **diagram klas** (`classDiagram`) — struktura komponentów obszaru i relacje między nimi;
- **diagram przepływu** (`flowchart`, `sequenceDiagram` albo `stateDiagram-v2`) — kolejność kroków,
  rozgałęzienia, przejścia stanów i obsługa błędów.

Obowiązują następujące zasady:

- Źródło każdego diagramu jest zapisane jako osobny plik `.mmd` w katalogu feature'a albo w
  `wspolne/`, zgodnie z sekcją 3.1. Plik zawiera wyłącznie kod Mermaid, bez ogrodzenia blokiem kodu
  Markdown, i zaczyna się od komentarza `%%` z tytułem diagramu.
- Nazwa pliku zaczyna się od rodzaju diagramu: `klasy-` albo `przeplyw-`, a dalej wskazuje opisywany
  obszar lub proces, na przykład `authentication/przeplyw-zmiana-hasla.mmd`,
  `wspolne/przeplyw-start-aplikacji.mmd`. Nazwa nie powtarza nazwy katalogu feature'a.
- **Diagram musi być widoczny w dokumentacji.** W treści dokumentu osadzamy wygenerowany obrazek,
  a nie kod Mermaid — bloki ` ```mermaid ` renderują się tylko w części czytników Markdown, więc
  wklejone źródło jest dla czytelnika ścianą tekstu. Wklejanie zawartości pliku `.mmd` do dokumentu
  jest niedozwolone.
- Osadzenie ma postać obrazka z tekstem alternatywnym równym tytułowi diagramu, a bezpośrednio pod
  nim znajduje się odnośnik do źródła:

  ```markdown
  ![Diagram przepływu: wymuszona zmiana hasła panelu vDeck](diagramy/authentication/img/przeplyw-zmiana-hasla.svg)

  Źródło diagramu: [`diagramy/authentication/przeplyw-zmiana-hasla.mmd`](diagramy/authentication/przeplyw-zmiana-hasla.mmd)
  ```

- **Obrazek generujemy ze źródła i nigdy nie edytujemy ręcznie.** Po każdej zmianie pliku `.mmd`
  obrazek trzeba wygenerować ponownie w tej samej zmianie:

  ```powershell
  npx -y @mermaid-js/mermaid-cli `
    -i dokumentacja\diagramy\<feature>\<nazwa>.mmd `
    -o dokumentacja\diagramy\<feature>\img\<nazwa>.svg `
    -b white -c dokumentacja\diagramy\mermaid-config.json
  ```

  Konfiguracja z `dokumentacja/diagramy/mermaid-config.json` jest obowiązkowa: wyłącza etykiety HTML,
  bez czego napisy trafiają do `foreignObject` i znikają, gdy SVG jest wyświetlane jako obrazek.
  Jeżeli po wygenerowaniu diagram jest nieczytelny — na przykład skrajnie szeroki — poprawiamy układ
  w źródle, na przykład zmieniając `direction`, zamiast zostawiać nieczytelny obrazek.
- **Każdy diagram jest podlinkowany w dokumentacji projektowej** — odnośnikiem względnym do pliku
  źródłowego, umieszczonym w sekcji opisującej dany proces.
- W `diagramy/` nie pozostawiamy źródeł ani obrazków, do których nie prowadzi żaden odnośnik, ani
  odnośników do plików nieistniejących. Usunięcie diagramu oznacza usunięcie źródła, obrazka,
  osadzenia i wpisu w indeksie diagramów.
- Diagram pokazuje kontrakt procesu, a nie każdą prywatną metodę pomocniczą. Jeżeli staje się
  nieczytelny, dzielimy go na kilka diagramów o węższym zakresie.

### 3.5 Kiedy aktualizować dokumentację projektową

Dokumentację projektową **oraz jej diagramy** aktualizujemy w tej samej zmianie co kod, gdy zmiana:

- dodaje, usuwa albo istotnie zmienia funkcjonalność systemu;
- **zmienia warunki lub zasady działania funkcjonalności** — warunki wstępne, reguły walidacji,
  warunki decyzyjne i ich kolejność, wymagane uprawnienia, wartości domyślne, limity, timeouty,
  przejścia stanów albo klasyfikację wyników i błędów;
- zmienia widoczne zachowanie panelu, przebieg procesu albo obsługę błędu;
- zmienia komponenty lub relacje pokazane na istniejącym diagramie;
- zmienia konfigurację, dane utrwalane albo sposób uruchomienia opisany w dokumencie.

Zmiana warunku lub reguły działania nigdy nie jest wyłącznie zmianą kodu: wymaga poprawienia opisu
w sekcji danej funkcjonalności **i** każdego diagramu, który ten warunek pokazuje — także wtedy, gdy
nie zmieniają się nazwy klas ani struktura komponentów. Diagram, który pokazuje nieaktualny warunek,
kolejność kroków albo wariant błędu, traktujemy jak błąd w dokumentacji.

Refaktoryzacja bez zmiany zachowania wymaga aktualizacji tylko wtedy, gdy unieważnia nazwy albo
strukturę pokazaną w dokumencie lub na diagramie.

### 3.6 Instrukcja obsługi użytkownika

Zgodnie z AD-022 katalog `dokumentacja/instrukcja-uzytkownika/` zawiera polską instrukcję obsługi
dla użytkownika końcowego. Build modułu `.dist` konwertuje ją do `vDeck-instrukcja.pdf` dołączanego
do paczki ZIP. Dokumentacja projektowa opisuje, jak system działa; instrukcja — jak z niego korzystać.

```text
dokumentacja/instrukcja-uzytkownika/
├── instrukcja-uzytkownika.adoc   ← źródło (AsciiDoc), jedyny punkt wejścia
├── motyw/vdeck-theme.yml         ← motyw PDF: kolory, okładka, nagłówek i stopka
├── rysunki/<nazwa>.mmd           ← źródła rysunków (Mermaid)
├── img/                          ← zrzuty ekranu (.jpg) i rysunki wygenerowane z rysunki/ (.png)
└── zrzuty/zrzuty-ekranu.mjs      ← skrypt Playwright odtwarzający wszystkie zrzuty ekranu
```

**Instrukcja musi być zawsze aktualna.** W tej samej zmianie co kod aktualizujemy jej treść, zrzuty
ekranu i rysunki, gdy zmiana:

- dodaje, usuwa albo zmienia ekran, przycisk, pole, komunikat lub etykietę widoczną dla użytkownika;
- zmienia przebieg czynności użytkownika, warunki, wartości domyślne albo limity opisane w instrukcji;
- zmienia instalację, uruchomienie, pliki ustawień, zawartość paczki lub wymagania systemowe;
- udostępnia funkcję dotąd oznaczoną w instrukcji jako niedostępna.

Zasady treści:

- Piszemy po polsku, prosto i w formie instrukcji krok po kroku, dla osoby bez wiedzy technicznej.
  Nie opisujemy klas, pakietów ani architektury — od tego jest dokumentacja projektowa.
- Etykiety ekranów, przycisków i komunikatów podajemy dokładnie tak jak w `messages.properties`.
- Opisujemy wyłącznie zaimplementowane zachowanie; przyciski przyszłych funkcji oznaczamy jako
  niedostępne.
- Czytelność PDF ma pierwszeństwo: krótkie kroki, tabele zamiast długich akapitów, ramki
  `NOTE`/`TIP`/`IMPORTANT`/`WARNING`/`CAUTION` dla uwag oraz odsyłacze `<<...>>` między rozdziałami.

Zasady obrazów:

- Każdy zrzut ekranu tworzy skrypt `zrzuty/zrzuty-ekranu.mjs`; nie wklejamy ręcznie robionych zrzutów,
  bo nie dałoby się ich odtworzyć po zmianie interfejsu. Nowy zrzut oznacza nowy krok w skrypcie.
- Skrypt uruchamiamy na świeżo wypakowanej paczce z danymi przykładowymi, nigdy na prawdziwej
  instalacji. Polecenia i wymagania opisuje komentarz na początku skryptu.
- Zrzut pokazuje tylko potrzebny fragment: sekcję formularza, przycisk albo kadr wokół elementu,
  a pełny ekran tylko dla orientacji. Ważne miejsca oznaczamy ramką lub numerem (funkcja `mark`),
  a numery objaśniamy pod rysunkiem w tej samej kolejności.
- Rysunki, np. schematy przebiegu, zapisujemy jako `.mmd` w `rysunki/` i renderujemy do PNG,
  bo konwerter PDF nie obsługuje etykiet SVG z mermaid-cli:

  ```powershell
  npx -y @mermaid-js/mermaid-cli `
    -i dokumentacja\instrukcja-uzytkownika\rysunki\<nazwa>.mmd `
    -o dokumentacja\instrukcja-uzytkownika\img\<nazwa>.png `
    -b white -s 2 -c dokumentacja\diagramy\mermaid-config.json
  ```

- Każdy obraz ma tytuł (podpis „Rysunek N.”), tekst alternatywny i szerokość `pdfwidth` dobraną tak,
  aby tekst na obrazie był czytelny na stronie A4. W `img/` nie zostawiamy nieużywanych plików.

Build przerywa ostrzeżenie Asciidoctora, np. brakujący obrazek albo nieistniejący odnośnik. Po zmianie
instrukcji generujemy PDF (`.\mvnw.cmd package` albo samo wykonanie `user-manual-pdf` modułu `.dist`)
i przeglądamy zmienione strony.

### 3.7 Lista zmian wersji

Zgodnie z AD-029 plik `vdeck-app/src/main/resources/release-notes/<X.Y.Z>.md` (wersja bez
`-SNAPSHOT`) jest opisem wydania na GitHubie i treścią panelu „Co nowego w vDeck”. Każda zmiana
widoczna dla użytkownika dopisuje do pliku bieżącej wersji jedną pozycję w tej samej zmianie co kod.
Zmiany wyłącznie wewnętrzne (refaktoryzacja, testy, dokumentacja programisty) nie trafiają do pliku.

```markdown
## Nowości
- **Lista zmian po aktualizacji**
  Po pierwszym uruchomieniu nowej wersji panel pokazuje, co się w niej zmieniło.

## Poprawki
- **Konsola**
  Ostatnia linia logu pojawia się od razu.
```

- Dozwolone są wyłącznie nagłówki `## Nowości`, `## Zmiany`, `## Poprawki`, w tej kolejności, każdy
  co najwyżej raz; pusty nagłówek jest dozwolony.
- Pozycja to wiersz `- **Tytuł**` (do 80 znaków) i dokładnie jeden wiersz opisu wcięty dwiema
  spacjami (do 200 znaków). Opis jest obowiązkowy.
- Piszemy po polsku, dla użytkownika końcowego: co zmieniło się w jego pracy, a nie w kodzie. Bez
  HTML-a, linków, numerów zadań i nazw klas.
- Pozycję poprawiamy, zamiast dopisywać drugą, gdy kolejna zmiana w tej samej wersji dotyczy tej
  samej funkcji.
- Plik wydanej wersji jest niezmienny. Plik następnej wersji tworzy `.env/release.bat` z szablonu
  `.env/release-notes-template.md`.

`ReleaseNotesFilesTest` sprawdza format każdego pliku i istnienie pliku bieżącej wersji; workflow
i `release.bat` przerywają wydanie, gdy plik nie zawiera żadnej pozycji.

## 4. Zakres obowiązkowego Javadoc

Javadoc jest wymagany nad:

- każdą klasą, interfejsem, rekordem, typem wyliczeniowym i adnotacją;
- każdym konstruktorem innym niż `private`;
- każdym polem innym niż `private`;
- każdą stałą (`static final`) kodu produkcyjnego, **niezależnie od widoczności**, także `private`;
- każdą metodą inną niż `private`.

Stała zawsze ma Javadoc, bo jej wartość jest decyzją (limit, timeout, nazwa, format), której powodu
ani jednostki nie widać z samego kodu. Opis mówi, co stała oznacza i do czego służy, a przy limitach
i czasach także jednostkę oraz skutek przekroczenia. Wyjątkiem są tylko loggery generowane przez
Lombok (`@Log4j2`).

Pozostałe metody i pola `private` dokumentujemy wtedy, gdy ich odpowiedzialność, założenia, algorytm lub skutki
uboczne nie są oczywiste z nazwy i kodu. Javadoc nie zastępuje właściwego nazewnictwa.

Wygenerowane elementy oraz proste akcesory utworzone przez Lombok nie wymagają osobnego Javadoc,
jeśli udokumentowany jest typ lub pole będące źródłem ich kontraktu.

## 5. Javadoc typu

Opis typu powinien krótko określać:

- jego pojedynczą główną odpowiedzialność;
- miejsce w module lub feature’rze;
- najważniejsze relacje z innymi komponentami, jeśli są częścią kontraktu;
- reguły cyklu życia, współbieżności lub własności zasobów, jeśli mają znaczenie;
- ograniczenia użycia, których nie można wyrazić typami.

Nie wystarcza opis powtarzający nazwę, np. `Service responsible for server services`.

Przykład:

```java
/**
 * Coordinates state transitions and stop operations for the single Valheim server managed by vDeck.
 * All state-changing events must be submitted through this component's serialized event queue.
 */
final class ServerRuntime {
}
```

## 6. Javadoc pól

Opis pola wyjaśnia jego znaczenie, jednostkę, dozwolone wartości albo rolę w cyklu życia obiektu.
W szczególności należy dokumentować:

- stałe (zawsze, także prywatne — §4) i wartości konfiguracyjne;
- współdzielony stan oraz zasady dostępu współbieżnego;
- uchwyty i zasoby wymagające zamknięcia;
- wartości opcjonalne, sentinel values oraz jednostki czasu lub rozmiaru.

Nie należy opisywać oczywistego szczegółu implementacyjnego, który nie stanowi kontraktu.

## 7. Javadoc metod i konstruktorów

Pierwsze zdanie krótko opisuje odpowiedzialność metody albo efekt utworzenia obiektu. Dalsza część,
jeśli jest potrzebna, opisuje kontrakt, warunki wejściowe, skutki uboczne i zachowanie w sytuacjach
brzegowych.

Dokumentacja zawiera odpowiednie tagi:

- `@param` dla każdego parametru i `@param <T>` dla każdego parametru typu;
- `@return` dla każdej metody zwracającej wartość inną niż `void` — z opisem znaczenia wyniku,
  nie tylko jego typu;
- `@throws` dla wyjątków wynikających z kontraktu metody, wraz z warunkiem ich wystąpienia;
- `@see` lub `{@link ...}` tylko wtedy, gdy powiązanie realnie pomaga zrozumieć kontrakt.

Należy opisać również:

- czy metoda zmienia stan lub wykonuje I/O;
- czy może blokować i jaki timeout ją ogranicza;
- czy jest bezpieczna współbieżnie;
- czy zwracana kolekcja lub obiekt może być modyfikowany;
- znaczenie `null`, pustej wartości albo braku wyniku, jeśli są dozwolone; publiczny kontrakt musi
  być zgodny z `@Nonnull`/`@Nullable` określonymi w `clean-code.md`;
- istotne skutki częściowego niepowodzenia.

Nie deklarujemy w Javadoc wyjątków, których metoda nie rzuca i które nie należą do jej kontraktu.
Przy prostym override można użyć `{@inheritDoc}`, o ile dokumentacja typu bazowego w pełni opisuje
zachowanie. Różnice względem kontraktu bazowego muszą zostać dopisane.

Przykład:

```java
/**
 * Attempts to adopt a running Valheim process as the server managed by this runtime.
 *
 * @param candidate verified process candidate selected by the panel user
 * @return current server status after a successful adoption
 * @throws ProcessIdentityMismatchException if the process no longer matches the verified identity
 * @throws IllegalStateException if another server is already managed
 */
ServerStatus adopt(RecoveryCandidate candidate) {
    // ...
}
```

## 8. Metody rozpoczynające złożone procesy

Metoda będąca wejściem do procesu obejmującego kilka etapów, stanów, komponentów albo wariantów błędu
wymaga rozszerzonego Javadoc. Dotyczy to w szczególności uruchamiania i zatrzymywania serwera,
odzyskiwania procesu, snapshotów, restore, aktualizacji, operacji RCON i zmian konfiguracji modów.

Rozszerzony opis powinien, stosownie do procesu, zawierać:

- warunki wstępne i stan wymagany przed wywołaniem;
- główne etapy i ich kolejność;
- możliwe warianty wykonania oraz fallback;
- przejścia stanów;
- granice transakcji i moment utrwalenia danych;
- timeouty, retry oraz zasady anulowania;
- skutki błędu i stan pozostawiony po częściowym wykonaniu;
- zdarzenia lub wynik sygnalizujące zakończenie.

Jeżeli przepływ ma co najmniej kilka rozgałęzień albo istotne przejścia stanów, Javadoc powinien
zawierać czytelną reprezentację tekstową w bloku `<pre>`. Diagram pokazuje kontrakt procesu, nie
każdą prywatną metodę pomocniczą.

Przykład:

```java
/**
 * Stops the managed server using the configured data-safety policy.
 *
 * <p>The operation is asynchronous and completes when the process exits or the global deadline
 * is reached. Only one lifecycle operation can be active at a time.</p>
 *
 * <pre>
 * RUNNING
 *    |
 *    v
 * PRE_STOP snapshot -- RCON available --> save confirmed --> CTRL_C
 *    |                                      |                  |
 *    |                                      v                  v
 *    +-------------------------------> BEST_EFFORT       process exit
 *                                                               |
 *                         timeout -------------------------------+
 *                            |
 *                            v
 *                    retry / guarded force kill
 * </pre>
 *
 * @param request stop policy and user authorization applicable to this operation
 * @return handle completed with the final stop outcome
 * @throws IllegalStateException if no server is running or another lifecycle operation is active
 */
CompletionStage<StopOutcome> stop(StopRequest request) {
    // ...
}
```

Jeżeli diagram nie mieści się czytelnie w Javadoc, krótki Javadoc powinien odsyłać do sekcji
dokumentacji projektowej opisującej ten proces oraz do jego diagramu w `dokumentacja/diagramy/`,
zgodnie z sekcją 3.

## 9. Komentarze wewnątrz kodu

Komentarze liniowe stosujemy oszczędnie. Powinny wyjaśniać przede wszystkim „dlaczego”, np. wymóg
Win32, obejście zachowania biblioteki albo nieoczywistą kolejność operacji. Komentarz opisujący
„co” robi bezpośrednio kolejna instrukcja zazwyczaj jest zbędny.

Komentarze zawierające uzasadnienie techniczne muszą pozostać aktualne po refaktoryzacji. Tymczasowe
obejścia powinny wskazywać warunek, po którego zniknięciu można je usunąć.

## 10. Kontrola przed zakończeniem zmiany

Przed uznaniem zmiany za kompletną należy sprawdzić:

- czy wszystkie nowe i zmodyfikowane typy mają aktualny Javadoc;
- czy wszystkie nieprywatne pola, konstruktory i metody są udokumentowane;
- czy każda stała kodu produkcyjnego, także `private`, ma Javadoc;
- czy `@param`, `@return` i `@throws` odpowiadają rzeczywistej sygnaturze i zachowaniu;
- czy złożone wejścia do procesów opisują warianty, stany i błędy;
- czy zmiana nie unieważniła diagramu lub opisu w `.docs`;
- czy `dokumentacja/dokumentacja-projektowa.md` opisuje zmienioną funkcjonalność, a jej spis treści
  jest aktualny;
- czy zmienione warunki i reguły działania funkcjonalności zostały poprawione w opisie i na każdym
  diagramie, który je pokazuje;
- czy każdy złożony proces objęty zmianą ma aktualny diagram w katalogu swojego feature'a;
- czy obrazek w `img/` został wygenerowany ponownie po zmianie źródła `.mmd` i jest osadzony
  w dokumencie zamiast kodu Mermaid;
- czy każdy diagram jest podlinkowany w dokumentacji i nie pozostał żaden osierocony plik `.mmd`
  ani `.svg`;
- czy dokumentacja nie przedstawia niezaimplementowanego zachowania jako gotowego;
- czy zmiana widoczna dla użytkownika jest opisana w instrukcji obsługi, zrzuty ekranu zostały
  odtworzone skryptem, a PDF buduje się bez ostrzeżeń i został przejrzany;
- czy zmiana widoczna dla użytkownika ma pozycję w `release-notes/<X.Y.Z>.md` bieżącej wersji (§3.7);
- czy odnośniki i nazwy plików nadal są poprawne.
