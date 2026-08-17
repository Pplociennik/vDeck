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
`.docs/first_design_ideas.md` zawiera ich szczegóły oraz pozostałe uzgodnione założenia. Nie należy
zmieniać decyzji w tych plikach bez akceptacji użytkownika. Plany realizacji poszczególnych feature’ów
powstają osobno i nie są dopisywane do dokumentów z decyzjami i głównymi założeniami.

## 3. Zakres obowiązkowego Javadoc

Javadoc jest wymagany nad:

- każdą klasą, interfejsem, rekordem, typem wyliczeniowym i adnotacją;
- każdym konstruktorem innym niż `private`;
- każdym polem innym niż `private`, w tym nad publicznymi stałymi;
- każdą metodą inną niż `private`.

Metody i pola `private` dokumentujemy wtedy, gdy ich odpowiedzialność, założenia, algorytm lub skutki
uboczne nie są oczywiste z nazwy i kodu. Javadoc nie zastępuje właściwego nazewnictwa.

Wygenerowane elementy oraz proste akcesory utworzone przez Lombok nie wymagają osobnego Javadoc,
jeśli udokumentowany jest typ lub pole będące źródłem ich kontraktu.

## 4. Javadoc typu

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

## 5. Javadoc pól

Opis pola wyjaśnia jego znaczenie, jednostkę, dozwolone wartości albo rolę w cyklu życia obiektu.
W szczególności należy dokumentować:

- stałe i wartości konfiguracyjne;
- współdzielony stan oraz zasady dostępu współbieżnego;
- uchwyty i zasoby wymagające zamknięcia;
- wartości opcjonalne, sentinel values oraz jednostki czasu lub rozmiaru.

Nie należy opisywać oczywistego szczegółu implementacyjnego, który nie stanowi kontraktu.

## 6. Javadoc metod i konstruktorów

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

## 7. Metody rozpoczynające złożone procesy

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

Jeżeli diagram nie mieści się czytelnie w Javadoc, krótki Javadoc powinien odsyłać do dedykowanego
dokumentu Markdown zawierającego diagram Mermaid lub większy diagram tekstowy.

## 8. Komentarze wewnątrz kodu

Komentarze liniowe stosujemy oszczędnie. Powinny wyjaśniać przede wszystkim „dlaczego”, np. wymóg
Win32, obejście zachowania biblioteki albo nieoczywistą kolejność operacji. Komentarz opisujący
„co” robi bezpośrednio kolejna instrukcja zazwyczaj jest zbędny.

Komentarze zawierające uzasadnienie techniczne muszą pozostać aktualne po refaktoryzacji. Tymczasowe
obejścia powinny wskazywać warunek, po którego zniknięciu można je usunąć.

## 9. Kontrola przed zakończeniem zmiany

Przed uznaniem zmiany za kompletną należy sprawdzić:

- czy wszystkie nowe i zmodyfikowane typy mają aktualny Javadoc;
- czy wszystkie nieprywatne pola, konstruktory i metody są udokumentowane;
- czy `@param`, `@return` i `@throws` odpowiadają rzeczywistej sygnaturze i zachowaniu;
- czy złożone wejścia do procesów opisują warianty, stany i błędy;
- czy zmiana nie unieważniła diagramu lub opisu w `.docs`;
- czy dokumentacja nie przedstawia niezaimplementowanego zachowania jako gotowego;
- czy odnośniki i nazwy plików nadal są poprawne.
