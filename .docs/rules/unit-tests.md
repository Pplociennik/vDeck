# Reguły testów jednostkowych

> Status: obowiązujące dla całego repozytorium

Testy jednostkowe są częścią implementacji zachowania, a nie osobnym, opcjonalnym etapem. Każda nowa
klasa produkcyjna zawierająca logikę lub własny kontrakt zachowania musi otrzymać odpowiadający jej
zestaw testów jednostkowych. Zmiana zachowania istniejącej klasy wymaga jednoczesnej aktualizacji jej
testów.

## 1. Zakres testów nowej klasy

Zestaw testów powinien pokrywać co najmniej:

- typowe, poprawne przypadki użycia;
- wartości graniczne i corner cases;
- puste kolekcje, puste napisy i brak danych, jeśli należą do kontraktu;
- każdy parametr referencyjny przekazany jako `null`, jeżeli metoda może zostać wywołana z Javy;
- wszystkie wyjątki będące częścią kontraktu klasy;
- błędne stany i niedozwolone kolejności operacji;
- istotne warianty warunków oraz każdą gałąź wpływającą na wynik lub skutek uboczny.

Testy mają weryfikować zachowanie i kontrakt, a nie prywatną strukturę implementacji. Sama wysoka
wartość pokrycia kodu nie zastępuje sprawdzenia istotnych przypadków.

Dedykowana klasa testowa nie jest wymagana dla pasywnych typów, które nie implementują własnego
zachowania, w szczególności:

- enumów będących wyłącznie zamkniętym zbiorem nazwanych wartości;
- DTO i rekordów będących wyłącznie niemutowalnymi nośnikami danych;
- klas właściwości i konfiguracji zawierających wyłącznie deklaratywne mapowanie lub wiring Springa;
- marker interfaces i adnotacji bez własnej logiki;
- prostych typów wyjątków, które jedynie przekazują komunikat lub przyczynę do nadklasy.

Wyjątek znika, gdy taki typ zawiera walidację, normalizację, obliczenia, niestandardowe metody
fabryczne, własne przejścia stanów albo inne zachowanie mogące ulec regresji. Pasywny typ jest nadal
testowany pośrednio w testach klasy, która korzysta z jego kontraktu. Nie tworzymy testu sprawdzającego
wyłącznie kod wygenerowany przez kompilator, Lombok lub framework.

## 2. Lokalizacja i nazwa

- Test znajduje się w `src/test/java` w pakiecie odpowiadającym pakietowi klasy produkcyjnej.
- Klasa testowa ma nazwę `<TestowanaKlasa>Test`, np. `ServerStateMachineTest`.
- Jedna klasa testowa odpowiada jednej klasie produkcyjnej.
- Testy jednostkowe nie uruchamiają kontekstu Springa. Test wymagający Springa, bazy, systemu plików,
  procesu lub sieci jest testem integracyjnym i powinien być oznaczony oraz zorganizowany osobno.
- Wyjątkiem jest katalog tymczasowy JUnit (`@TempDir`): taki test pozostaje testem jednostkowym,
  ponieważ katalog jest izolowany, unikalny dla metody testowej, tworzony i usuwany przez framework
  oraz nie dotyka katalogu instalacji, danych użytkownika ani żadnego zasobu współdzielonego. Zakaz
  dotyczy rzeczywistego systemu plików aplikacji, bazy danych, procesów systemowych i sieci.

## 3. Wymagana struktura klasy testowej

Każda klasa testowa zawiera:

- pole typu testowanej klasy o dokładnej nazwie `underTest`;
- pola wymaganych mocków, fake’ów lub stubów;
- metodę oznaczoną `@BeforeEach`, która tworzy świeże `underTest` i przygotowuje wspólne środowisko;
- metodę `@AfterEach`, jeżeli testy otwierają zasoby albo wymagają jawnego sprzątania;
- metody testowe;
- metody pomocnicze umieszczone na końcu klasy.

Klasa jest podzielona na sekcje w następującej kolejności:

1. fields;
2. before methods;
3. after methods;
4. tests;
5. helper methods.

Każda sekcja jest oddzielona separatorem na pełną szerokość przyjętej linii kodu:

```java
// =================================================================================================
// FIELDS
// =================================================================================================
```

Sekcję bez zawartości można pominąć. Nie pozostawiamy pustych nagłówków `AFTER METHODS` lub
`HELPER METHODS` tylko po to, aby zachować komplet sekcji.

## 4. Inicjalizacja testów

`underTest` jest tworzony od nowa przed każdym testem. Wspólny, mutowalny stan nie może przeciekać
między metodami testowymi.

Mocki deklarujemy jako pola. Preferowany jest `@ExtendWith(MockitoExtension.class)` i `@Mock`, a
metoda `@BeforeEach` tworzy testowany obiekt z przekazanymi zależnościami. Jeżeli Mockito nie jest
potrzebne, nie dodajemy rozszerzenia ani adnotacji `@Mock`.

Przykład:

```java
/**
 * Verifies the behavior of {@link ServerService} in isolation from the server runtime.
 */
@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ServerRuntime serverRuntime;

    private ServerService underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service instance before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new ServerService(serverRuntime);
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the service returns a running status when the runtime reports an active server.
     */
    @Test
    void shouldReturnRunningStatus_whenServerIsActive() {
        // GIVEN
        var expectedStatus = ServerStatus.running(42L);
        given(serverRuntime.status()).willReturn(expectedStatus);

        // WHEN
        var result = underTest.status();

        // THEN
        assertThat(result).isEqualTo(expectedStatus);
    }
}
```

## 5. Javadoc metod testowych

Każda metoda testowa musi mieć Javadoc napisany **zawsze w języku angielskim**. Javadoc krótko
opisuje sprawdzany przypadek, warunki wejściowe oraz oczekiwane zachowanie. Nie wystarcza powtórzenie
nazwy metody innymi słowami.

Javadoc metod przygotowujących środowisko i metod pomocniczych podlega ogólnym regułom z
`documentation.md`: jest wymagany, jeśli metoda nie jest `private`. Prywatne helpery dokumentujemy,
gdy ich odpowiedzialność nie jest oczywista.

Przykład:

```java
/**
 * Verifies that a null launch specification is rejected before the process launcher is invoked.
 */
@Test
void shouldThrowException_whenLaunchSpecIsNull() {
    // ...
}
```

## 6. Nazewnictwo metod testowych

Nazwa metody opisuje oczekiwany rezultat oraz warunek badanego przypadku:

```text
should<ExpectedBehavior>_when<Condition>()
```

Przykłady:

```java
shouldBeDark_whenHourLaterThan20()
shouldRejectStart_whenServerIsAlreadyRunning()
shouldReturnEmptyList_whenNoRecoveryCandidatesExist()
shouldThrowException_whenLaunchSpecIsNull()
```

Nazwa nie powinna zawierać ogólnych określeń takich jak `testMethod`, `works`, `happyPath` albo
numerów przypadków bez znaczenia domenowego.

## 7. Testy parametryzowane

Testy parametryzowane JUnit 5 są dozwolone i zalecane, gdy ten sam kontrakt ma zostać sprawdzony dla
wielu równoważnych danych wejściowych. Można używać między innymi `@ValueSource`, `@CsvSource`,
`@EnumSource`, `@NullSource`, `@EmptySource`, `@NullAndEmptySource` oraz `@MethodSource`.

Metoda oznaczona `@ParameterizedTest` podlega wszystkim zasadom zwykłej metody testowej:

- ma angielski Javadoc opisujący wspólny kontrakt wszystkich przypadków;
- ma nazwę w formacie `should..._when...`;
- zawiera sekcje `GIVEN`, `WHEN` i `THEN`;
- używa wyłącznie AssertJ;
- każdy zestaw parametrów musi być możliwy do zidentyfikowania w raporcie testów.

Nazwa wyświetlana testu parametryzowanego powinna opisywać konkretne dane przypadku, np. przez
`@ParameterizedTest(name = "[{index}] hour={0} should be dark")`. Treść `name` jest zawsze po
angielsku.

Przykład:

```java
/**
 * Verifies that every hour after the configured evening boundary is classified as dark.
 *
 * @param hour hour of day being classified
 */
@ParameterizedTest(name = "[{index}] hour={0} should be dark")
@ValueSource(ints = {21, 22, 23})
void shouldBeDark_whenHourLaterThan20(int hour) {
    // GIVEN
    var time = LocalTime.of(hour, 0);

    // WHEN
    var result = underTest.isDark(time);

    // THEN
    assertThat(result).isTrue();
}
```

`@MethodSource` stosujemy dla danych złożonych albo wtedy, gdy przypadek wymaga czytelnej nazwy.
Metoda dostarczająca argumenty znajduje się w sekcji `HELPER METHODS`. Jeżeli nie jest `private`,
musi mieć angielski Javadoc zgodnie z `documentation.md`.

Nie łączymy w jednym teście parametryzowanym scenariuszy o różnych oczekiwanych zachowaniach,
różnych skutkach ubocznych lub różnych typach błędów tylko po to, aby zmniejszyć liczbę metod.
Takie przypadki pozostają osobnymi testami.

## 8. GIVEN, WHEN, THEN

Każda metoda testowa zawiera jawnie wydzielone sekcje:

```java
// GIVEN

// WHEN

// THEN
```

- `GIVEN` przygotowuje dane wejściowe, stan i zachowanie zależności;
- `WHEN` zawiera pojedynczą badaną akcję;
- `THEN` zawiera asercje wyniku, stanu i istotnych interakcji.

Dla testu wyjątku wywołanie badanej akcji pozostaje w `WHEN`, a asercje dotyczące typu i treści
wyjątku znajdują się w `THEN`:

```java
/**
 * Verifies that starting a second server is rejected while another server is running.
 */
@Test
void shouldThrowException_whenServerIsAlreadyRunning() {
    // GIVEN
    underTest.markRunning(TEST_PROCESS_ID);

    // WHEN
    var exception = catchThrowable(() -> underTest.start(launchSpec));

    // THEN
    assertThat(exception)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already running");
}
```

Jeżeli przygotowanie lub asercje są rozbudowane, należy wydzielić dobrze nazwane prywatne helpery,
ale badana akcja powinna pozostać widoczna w metodzie testowej.

## 9. Asercje AssertJ

Wszystkie asercje w testach jednostkowych są wykonywane przy użyciu biblioteki **AssertJ**. Stosujemy
statyczne importy z `org.assertj.core.api.Assertions` i dobieramy asercję do znaczenia sprawdzanej
wartości.

Nie używamy junitowych `Assertions.assertEquals`, `assertTrue`, `assertFalse`, `assertNull`,
`assertThrows` ani ich odpowiedników. JUnit 5 pozostaje frameworkiem uruchamiającym testy, natomiast
AssertJ jest jedynym standardem zapisu asercji.

Preferowane konstrukcje:

```java
assertThat(result).isEqualTo(expected);
assertThat(items).containsExactly(expectedFirst, expectedSecond);
assertThat(optionalResult).contains(expected);
assertThatThrownBy(() -> underTest.start(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("launchSpec");
assertThatCode(() -> underTest.stop(request)).doesNotThrowAnyException();
```

Jeżeli ścisłe rozdzielenie `WHEN` i `THEN` poprawia czytelność testu wyjątku, należy przechwycić go
przez AssertJ `catchThrowable`, `catchThrowableOfType` albo odpowiadającą metodę typowaną, a następnie
sprawdzić w sekcji `THEN` przez `assertThat`.

Nie sprowadzamy bogatej wartości do wartości logicznej tylko po to, aby użyć `isTrue()`. Zamiast
`assertThat(items.contains(expected)).isTrue()` używamy semantycznej asercji
`assertThat(items).contains(expected)`.

## 10. Mockowanie

Mockowanie jest ograniczone do sytuacji, w których rzeczywista zależność:

- wykonuje I/O lub korzysta z systemu operacyjnego, procesu, zegara, sieci albo bazy;
- jest niedeterministyczna;
- jest kosztowna lub niemożliwa do bezpiecznego użycia w teście jednostkowym;
- reprezentuje granicę modułu, której zachowanie nie jest przedmiotem danego testu.

Nie mockujemy:

- testowanej klasy;
- prostych obiektów domenowych, value objects, rekordów ani kolekcji;
- kodu, który można utworzyć jako lekki rzeczywisty obiekt;
- zależności tylko po to, aby zweryfikować prywatny sposób implementacji.

Preferujemy rzeczywiste obiekty, a następnie proste fake’i lub stuby. Mock jest ostatnią z tych opcji.
Weryfikujemy tylko interakcje stanowiące część kontraktu lub istotny skutek uboczny.

**Statyczne mockowanie jest zabronione.** Nie używamy `Mockito.mockStatic`, PowerMock ani podobnych
mechanizmów. Kod wymagający statycznego mocka należy przeprojektować przez wstrzyknięcie zależności,
opakowanie granicy zewnętrznej albo przekazanie deterministycznej funkcji.

## 11. Jakość i niezależność testów

Test jednostkowy musi być:

- deterministyczny i niezależny od kolejności uruchomienia;
- szybki oraz pozbawiony rzeczywistych opóźnień i aktywnego oczekiwania;
- niezależny od lokalnej strefy czasowej, zegara systemowego i losowości albo korzystać z jawnie
  kontrolowanych odpowiedników, np. `Clock.fixed`;
- niezależny od plików i ustawień pozostawionych przez inny test;
- czytelny bez konieczności analizowania implementacji klasy produkcyjnej.

Jeden test powinien opisywać jeden przypadek zachowania. Może zawierać kilka asercji, jeśli wspólnie
opisują ten sam rezultat. Nie łączymy niepowiązanych scenariuszy w jednej metodzie testowej.

## 12. Kontrola przed zakończeniem zmiany

Przed uznaniem nowej lub zmodyfikowanej klasy za kompletną należy sprawdzić:

- czy klasa zawierająca zachowanie ma odpowiadającą jej klasę testową;
- czy brak dedykowanego testu dotyczy wyłącznie pasywnego typu objętego jawnym wyjątkiem;
- czy test posiada pole `underTest` i poprawne przygotowanie w `@BeforeEach`;
- czy uwzględniono poprawne użycie, granice, `null`, wyjątki i błędne stany;
- czy każdy parametr publicznego API oznaczony `@Nonnull` ma test potwierdzający odrzucenie `null`
  przez `requireNonNull` przed wykonaniem skutków ubocznych;
- czy każda metoda testowa ma angielski Javadoc;
- czy nazwy metod stosują `should..._when...`;
- czy równoważne zestawy danych zostały sensownie zgrupowane w test parametryzowany;
- czy raport testu parametryzowanego pozwala rozpoznać każdy zestaw argumentów;
- czy każda metoda testowa zawiera `GIVEN`, `WHEN` i `THEN`;
- czy sekcje klasy są uporządkowane i oddzielone pełnymi separatorami;
- czy wszystkie asercje używają AssertJ zamiast `org.junit.jupiter.api.Assertions`;
- czy mocki są rzeczywiście konieczne;
- czy test nie używa statycznego mockowania;
- czy testy jednostkowe dla zmienionego obszaru zostały uruchomione i zakończyły się powodzeniem.
