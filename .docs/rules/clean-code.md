# Reguły czystego kodu

> Status: obowiązujące dla całego repozytorium

Kod vDeck ma być prosty do zrozumienia, bezpieczny do zmiany i łatwy w utrzymaniu. Czytelność oraz
jawność intencji mają pierwszeństwo przed sprytnymi skrótami, nadmierną abstrakcją i przedwczesną
optymalizacją.

Reguły dotyczą zarówno kodu produkcyjnego, jak i testów. Należy stosować je pragmatycznie: celem
jest redukcja złożoności, a nie mechaniczne spełnianie metryk albo używanie wzorców dla nich samych.

## 1. Podstawowe zasady

### SOLID

- **Single Responsibility Principle** — klasa i metoda mają jeden spójny powód do zmiany.
- **Open/Closed Principle** — stabilny kod rozszerzamy przez dobrze dobrane punkty rozszerzeń tylko
  tam, gdzie istnieje realna zmienność. Nie projektujemy abstrakcji dla hipotetycznej przyszłości.
- **Liskov Substitution Principle** — implementacja nie osłabia kontraktu typu bazowego i nie
  zaskakuje odmiennymi warunkami wejściowymi lub skutkami ubocznymi.
- **Interface Segregation Principle** — interfejsy są małe i skupione na potrzebach ich klientów.
- **Dependency Inversion Principle** — logika wysokiego poziomu nie zależy bezpośrednio od
  szczegółów systemu operacyjnego, sieci ani innych niestabilnych granic.

SOLID nie oznacza obowiązkowego tworzenia interfejsu dla każdej klasy ani architektury heksagonalnej
w każdym feature. Serwisy aplikacyjne są jawnym wyjątkiem: zawsze udostępniają interfejs zgodnie z
sekcją „Wstrzykiwanie zależności”. Pozostała abstrakcja musi usuwać rzeczywiste sprzężenie lub
reprezentować faktyczny kontrakt.

### KISS

Wybieramy najprostsze rozwiązanie, które poprawnie realizuje obecny kontrakt. Preferujemy jawny,
liniowy przepływ nad wielopoziomową hierarchią, refleksją, metaprogramowaniem lub ukrytą magią.

### DRY

Nie powielamy wiedzy biznesowej, reguł, stałych ani algorytmów. Dwa podobne fragmenty składni nie
zawsze reprezentują tę samą wiedzę — przypadkowego podobieństwa nie należy na siłę łączyć we wspólną
abstrakcję. Błędna abstrakcja jest gorsza od niewielkiej, świadomej duplikacji.

### YAGNI

Nie implementujemy funkcji, punktów rozszerzeń, opcji konfiguracyjnych ani uogólnień, których nie
wymaga aktualny feature lub zatwierdzone założenie projektowe. Kod przewidziany „na wszelki wypadek”
zwiększa koszt utrzymania od momentu powstania.

### Zakaz boilerplate'u

Nie piszemy kodu, który wyłącznie mechanicznie powtarza informację już wyrażoną przez typ, konstrukcję
języka, Lombok, Spring Data albo inny świadomie używany mechanizm projektu. W szczególności nie
tworzymy ręcznie:

- getterów, konstruktorów, loggerów, `equals`, `hashCode` i `toString`, jeśli mogą zostać bezpiecznie
  wygenerowane zgodnie z kontraktem klasy;
- implementacji CRUD delegujących bez dodatkowego zachowania do generycznego repozytorium Spring Data;
- interfejsu i implementacji dla klasy niebędącej serwisem, bez rzeczywistej granicy lub zmienności;
- par DTO i mapperów kopiujących te same pola, jeżeli nie istnieje faktyczna różnica kontraktów;
- wrapperów, adapterów i klas pomocniczych, które tylko zmieniają nazwę tego samego wywołania;
- builderów, fabryk albo obiektów parametrów, które nie poprawiają czytelności i nie chronią
  niezmienników.

Usunięcie boilerplate'u nie może ukrywać logiki domenowej, walidacji, obsługi błędów ani istotnego
kontraktu. Kod generowany lub mechanizm frameworka musi być prostszy do zrozumienia niż kod, który
zastępuje. Jeżeli zachowanie wymaga wyjaśnienia, zapisujemy je jawnie zamiast polegać na magii.

## 2. Metody

Metoda powinna:

- mieć jedną jasno nazwaną odpowiedzialność;
- działać na jednym poziomie abstrakcji;
- być na tyle krótka, aby jej przepływ był widoczny bez przewijania i wielokrotnego cofania się;
- ujawniać intencję przez nazwy i strukturę, a nie przez komentarze tłumaczące kolejne instrukcje;
- minimalizować skutki uboczne i wyraźnie komunikować te, których nie można uniknąć.

Nie obowiązuje sztywny limit liczby linii. Długość jest sygnałem problemu, gdy metoda łączy walidację,
mapowanie, I/O, podejmowanie decyzji i modyfikację stanu albo wymaga komentarzy dzielących ją na
wewnętrzne fazy.

Preferujemy:

- guard clauses i szybkie zakończenie dla błędnych warunków;
- płaski przepływ zamiast zagnieżdżonych `if`/`else`;
- wydzielenie dobrze nazwanej metody, gdy fragment reprezentuje osobną decyzję lub krok domenowy;
- rozdzielenie zapytań od operacji zmieniających stan, jeśli poprawia to kontrakt;
- niewielką liczbę parametrów; spójny zestaw danych może stać się rekordem lub value object.

Należy unikać:

- wielu poziomów zagnieżdżenia;
- flag typu `boolean`, które całkowicie zmieniają znaczenie metody;
- metod zwracających specjalne, nieudokumentowane wartości sygnalizujące błąd;
- modyfikowania argumentów przekazanych przez wywołującego;
- ukrytego I/O w metodzie wyglądającej jak proste obliczenie;
- strumieni i lambd, jeśli zwykła pętla lub instrukcja warunkowa jest czytelniejsza.

Jeżeli metoda rozpoczyna większy proces, może pozostać orkiestratorem wywołującym kilka jasno
nazwanych operacji. Jej kontrakt i warianty muszą być opisane zgodnie z `documentation.md`.

## 3. Klasy i odpowiedzialności

Klasa powinna być spójna: jej pola i metody wspólnie realizują jedną odpowiedzialność. Mały rozmiar
nie jest celem samym w sobie — ważniejsze są wysoka kohezja, wąski publiczny kontrakt i brak
niepowiązanych powodów do zmiany.

Nową klasę warto wydzielić, gdy:

- fragment ma własną odpowiedzialność i nazwę domenową;
- posiada własne niezmienniki lub cykl życia;
- izoluje zmienną zależność albo operację I/O;
- upraszcza złożone warunki przez zamknięcie ich w polityce;
- może być testowany jako samodzielny kontrakt;
- istniejąca klasa zaczyna skupiać kilka niezależnych obszarów.

Nie wydzielamy klasy, jeśli byłaby wyłącznie jednorazowym przekazaniem wywołania bez własnej semantyki
i zwiększałaby liczbę elementów potrzebnych do zrozumienia prostego przepływu.

Publiczne API klasy powinno być możliwie małe. Stan obiektu pozostaje prywatny, a poprawne niezmienniki
są ustanawiane w konstruktorze lub fabryce. Zależności przekazujemy jawnie przez konstruktor; nie
ukrywamy ich w globalnym stanie ani statycznym locatorze serwisów.

### Higiena pakietów

Pakiet istnieje tylko wtedy, gdy zawiera rzeczywisty kod lub wymagany plik opisujący kontrakt pakietu.
Po przeniesieniu albo usunięciu ostatniej klasy usuwamy również pusty katalog pakietu z drzewa
`src/main` lub `src/test`. Nie pozostawiamy pustych pakietów jako rezerwacji pod przyszły kod ani jako
śladu po wcześniejszej strukturze. Katalogi generowane w `target` nie należą do źródłowego drzewa
pakietów i nie są przedmiotem tej reguły.

Każdy typ, konstruktor, pole i metoda otrzymuje najmniejszą widoczność potrzebną rzeczywistym
konsumentom. Szczegół używany wyłącznie w klasie jest `private`, a element współdzielony wyłącznie w
pakiecie jest package-private. `public` i `protected` stosujemy dopiero dla kontraktu przekraczającego
granicę pakietu albo gdy wymaga ich Java lub framework. Wykrywanie komponentów, kontrolerów i klas
konfiguracyjnych przez Spring nie wymaga samo w sobie widoczności `public`.

### Wstrzykiwanie zależności

Zależności komponentów Springa wstrzykujemy **wyłącznie przez konstruktor**. Pola zależności są
`private final`, dzięki czemu obiekt nigdy nie istnieje w stanie niekompletnym, a komplet wymaganych
współpracowników jest widoczny w sygnaturze.

Każdy serwis ma interfejs w podpakiecie `service` pakietu będącego jego właścicielem oraz implementację
w `service.impl`. Dla feature'a są to odpowiednio
`com.github.pplociennik.vdeck.feature.<nazwa>.service` i jego `impl`; serwis infrastrukturalny stosuje
ten sam układ pod pakietem infrastruktury, np. `shared.service` i `shared.service.impl`. Konsumenci
deklarują i wstrzykują wyłącznie typ interfejsu. Implementacja pozostaje package-private, o ile
techniczny mechanizm proxy albo centralna konfiguracja w innym pakiecie nie wymaga szerszej
widoczności. Interfejs jest `public` tylko wtedy, gdy ma konsumentów poza własnym pakietem.

Wszystkie klasy Spring oznaczone `@Configuration` umieszczamy w głównym pakiecie
`com.github.pplociennik.vdeck.config`. Stanowią composition root i mogą zależeć od konkretnych
implementacji wyłącznie w celu utworzenia beanów. Pozostali konsumenci zależą od interfejsów.

- Nie używamy wstrzykiwania w pole ani przez setter. `@Autowired` na polu, `@Inject` na polu oraz
  `@Autowired` na setterze są zabronione; ukrywają zależność przed kontraktem klasy i uniemożliwiają
  utworzenie obiektu w teście jednostkowym bez kontenera.
- Klasa z jednym konstruktorem nie potrzebuje `@Autowired` — Spring wybiera go automatycznie.
  Adnotację dodajemy dopiero przy wielu konstruktorach, co samo w sobie jest sygnałem do uproszczenia.
- Nie wstrzykujemy `ApplicationContext`, `BeanFactory` ani `ObjectProvider` po to, aby wyszukiwać
  współpracowników w trakcie działania. Jest to statyczny locator serwisów w innym przebraniu.
- Przy kilku kandydatach tego samego typu rozstrzygamy przez `@Qualifier` albo osobny typ, nigdy przez
  zbieżność nazwy pola.
- Konstruktor zapisujemy ręcznie zawsze, gdy wymaga `@Nonnull`, `requireNonNull`, dodatkowej walidacji
  lub szczegółowego Javadoc. `@RequiredArgsConstructor` stosujemy tylko wtedy, gdy wygenerowany
  konstruktor spełnia wszystkie te wymagania, zgodnie z regułami Lomboka.
- Zależność opcjonalna jest jawnym parametrem konstruktora z `@Nullable` albo `Optional<T>` w
  sygnaturze metody `@Bean`, a nie polem uzupełnianym po utworzeniu obiektu.

Reguła obowiązuje także klasy tworzone ręcznie w metodach `@Bean`. Konstruktor pozostaje wtedy jedynym
kanałem przekazania zależności, dzięki czemu komponent daje się przetestować bez kontenera.

### Metody fabryczne i obiekty parametrów

Stosujemy statyczne metody fabryczne, gdy dają realną korzyść w porównaniu z publicznym konstruktorem,
w szczególności gdy:

- nazwa sposobu utworzenia wyjaśnia wariant lub intencję domenową;
- istnieje kilka poprawnych sposobów utworzenia obiektu;
- przed utworzeniem trzeba sprawdzić niezmienniki albo znormalizować dane;
- konstrukcja nie powinna ujawniać konkretnej implementacji;
- metoda może zwrócić istniejącą instancję lub właściwy podtyp;
- prywatny konstruktor zapobiega utworzeniu niepoprawnego stanu.

Preferujemy fabrykę umieszczoną bezpośrednio na tworzonym typie. Osobna klasa `*Factory` powstaje
dopiero wtedy, gdy proces tworzenia wymaga zewnętrznych zależności, wyboru implementacji albo stanowi
samodzielną odpowiedzialność. Nie dodajemy factory method do prostego obiektu, jeśli wywołanie
konstruktora jest równie czytelne i nie omija żadnych reguł.

Nazwy metod fabrycznych opisują znaczenie konstrukcji, np. `of`, `from`, `create`, `forScheduledStop`
albo `forUserRequest`. Dla kilku wariantów preferujemy nazwy domenowe zamiast parametrów logicznych,
np. `StopRequest.forSchedule(...)` i `StopRequest.forUser(...)` zamiast
`new StopRequest(..., boolean scheduled)`.

Metoda lub konstruktor z więcej niż trzema parametrami jest sygnałem do przeanalizowania, czy część
argumentów tworzy **domenowo powiązaną grupę** nadającą się na Parameter Object. Nie grupujemy
automatycznie wszystkich parametrów. Wydzielamy wyłącznie taki podzbiór, który wspólnie opisuje jedno
pojęcie, żądanie, konfigurację albo decyzję i ma własne niezmienniki.

Metoda może więc przyjmować jeden lub kilka spójnych obiektów parametrów oraz osobny argument, który
nie należy do żadnej z tych grup. Jeżeli argumentów nadal jest dużo, należy ponownie ocenić, czy
metoda nie łączy kilku odpowiedzialności.

Nazwa wydzielonego typu musi pochodzić z domeny, np. `LaunchRequest`, `SnapshotRequest`,
`NetworkSettings`, `WorldSelection` albo `RecoveryDecision`, a nie z ogólnego `Parameters`, `Data`
czy `Options` bez kontekstu.

Obiekt parametrów:

- jest klasą `final`;
- ma prywatny konstruktor;
- udostępnia nazwane statyczne metody fabryczne;
- ma wyłącznie prywatne pola `final` i nie udostępnia setterów;
- waliduje wymagane wartości i niezmienniki podczas tworzenia;
- wykonuje defensive copy mutowalnych danych wejściowych;
- nie udostępnia modyfikowalnych kolekcji ani swojego wewnętrznego stanu;
- używa `@Nonnull`, `@Nullable` i statycznie importowanego `requireNonNull` zgodnie z regułami
  null-safety;
- zawiera zachowanie związane ze swoim pojęciem, jeśli dzięki temu klient nie musi ponownie
  interpretować surowych pól.

Przykład:

```java
/**
 * Immutable request containing the domain-related settings required to launch a Valheim server.
 */
public final class LaunchRequest {

    private final String serverName;
    private final int port;
    private final String worldName;
    private final @Nullable String publicPassword;

    private LaunchRequest(String serverName,
                          int port,
                          String worldName,
                          @Nullable String publicPassword) {
        this.serverName = requireNonNull(serverName, "serverName must not be null");
        this.port = port;
        this.worldName = requireNonNull(worldName, "worldName must not be null");
        this.publicPassword = publicPassword;
    }

    /**
     * Creates a validated launch request.
     *
     * @param serverName public name of the server
     * @param port base network port used by the server
     * @param worldName name of the world to load
     * @param publicPassword optional password required from connecting players
     * @return validated immutable launch request
     * @throws NullPointerException if {@code serverName} or {@code worldName} is {@code null}
     */
    public static @Nonnull LaunchRequest create(@Nonnull String serverName,
                                                int port,
                                                @Nonnull String worldName,
                                                @Nullable String publicPassword) {
        requireNonNull(serverName, "serverName must not be null");
        requireNonNull(worldName, "worldName must not be null");
        return new LaunchRequest(serverName, port, worldName, publicPassword);
    }
}

/**
 * Starts a Valheim server using the supplied launch request.
 *
 * @param request validated launch settings
 * @return representation of the newly started server process
 * @throws NullPointerException if {@code request} is {@code null}
 */
public @Nonnull RunningServer start(@Nonnull LaunchRequest request) {
    requireNonNull(request, "request must not be null");
    // ...
}
```

Fabryka może sama przyjmować komplet **powiązanych domenowo** danych potrzebnych do atomowego
utworzenia obiektu, nawet jeśli jest ich więcej niż trzy. Ważne jest, że złożoność konstrukcji,
walidacja i niezmienniki pozostają zamknięte w jednym typie, a właściwe API operacji otrzymuje spójny
argument zamiast luźnego zestawu wartości.

Nie grupujemy przypadkowych, niezwiązanych parametrów tylko po to, aby spełnić limit. Jeżeli parametry
nie tworzą jednego pojęcia, należy ponownie ocenić odpowiedzialność metody albo podzielić operację.

## 4. Kompozycja zamiast dziedziczenia

Preferujemy kompozycję i delegację. Dziedziczenie stosujemy wyłącznie wtedy, gdy relacja „is-a” jest
rzeczywista, stabilna i wszystkie podtypy zachowują pełny kontrakt typu bazowego.

Nie używamy dziedziczenia jedynie w celu:

- ponownego wykorzystania kilku metod;
- udostępnienia chronionych pól;
- współdzielenia konfiguracji testów;
- ominięcia jawnego przekazania zależności.

Głębokie hierarchie klas są zabronione. Interfejs implementowany przez kilka strategii jest zwykle
czytelniejszy niż wielopoziomowa klasa bazowa z hook methods i stanem `protected`.

## 5. Wzorce projektowe

Popularne wzorce projektowe stosujemy wtedy, gdy nazywają realny problem i upraszczają kod:

- **Strategy** — gdy istnieje kilka wymiennych polityk o tym samym kontrakcie;
- **Factory** — gdy wybór lub utworzenie implementacji wymaga logiki, której nie powinien znać klient;
- **State** — gdy zachowanie rzeczywiście zależy od rozbudowanego stanu, a zwykła maszyna stanów
  przestaje być czytelna;
- **Adapter** — na granicy Win32, RCON, systemu plików lub innego zewnętrznego API;
- **Facade** — jako mała publiczna granica modułu lub złożonego podsystemu;
- **Policy** — gdy nazwana reguła biznesowa ma kilka wariantów albo zmienia się niezależnie.

Wzorzec jest uzasadniony tylko wtedy, gdy zmniejsza liczbę warunków, sprzężenie albo wiedzę wymaganą
do zrozumienia klienta. Nie dodajemy fabryki do pojedynczego prostego konstruktora, strategii do
jednego niezmiennego algorytmu ani interfejsu z jedną implementacją bez realnej granicy.

## 6. Nazewnictwo

Wszystkie nazwy w kodzie Java są pisane po angielsku. Dotyczy to klas, interfejsów, rekordów, enumów,
metod, parametrów, pól, zmiennych lokalnych, stałych, pakietów oraz nazw przypadków testowych.

Nazwa powinna mówić, czym element jest albo co robi:

- klasy i wartości mają nazwy rzeczownikowe, np. `ProcessToken`, `SnapshotPolicy`;
- metody wykonujące operacje rozpoczynają się od precyzyjnego czasownika, np. `validateSnapshot`,
  `discoverProcesses`, `requestStop`;
- predykaty używają `is`, `has`, `can` lub `should`, np. `isRunning`, `hasConfirmedSave`;
- kolekcje mają nazwę w liczbie mnogiej;
- jednostka jest częścią nazwy, jeśli typ jej nie wyraża, np. `timeoutSeconds`;
- nazwa opisuje znaczenie domenowe, a nie techniczny sposób implementacji.

Unikamy skrótów, pojedynczych liter poza krótkim zakresem matematycznym lub indeksami oraz ogólnych
nazw takich jak `data`, `info`, `manager`, `helper`, `util`, `processor`, `handle` czy `doWork`, jeśli
nie wyjaśniają konkretnej odpowiedzialności.

Nie kodujemy typu w nazwie (`strName`, `userList`) i nie używamy prefiksów pól. Dobra nazwa eliminuje
potrzebę komentarza tłumaczącego znaczenie zmiennej.

## 7. Kod self-explanatory

Szybkie przeczytanie metody powinno wystarczyć do zrozumienia głównego przepływu. Osiągamy to przez:

- nazwy odnoszące się do języka domeny;
- jawne typy reprezentujące istotne pojęcia zamiast luźnych prymitywów;
- małe, nazwane decyzje zamiast rozbudowanych wyrażeń logicznych;
- stałe zamiast magicznych liczb i napisów;
- uporządkowanie kodu od publicznego kontraktu do prywatnych szczegółów;
- usuwanie martwego kodu, zakomentowanych bloków i nieużywanych abstrakcji.

Komentarz nie może maskować nieczytelnego kodu. Najpierw poprawiamy nazwy i strukturę. Komentarze
pozostają dla powodów, ograniczeń zewnętrznych, nieoczywistych decyzji i wymagań platformowych.
Szczegółowe zasady komentarzy oraz Javadoc znajdują się w `documentation.md`.

## 8. Warunki i obsługa błędów

Złożone warunki otrzymują nazwę przez metodę lub zmienną opisującą ich znaczenie. Preferujemy guard
clauses zamiast kaskad `else`. Gdy zestaw warunków reprezentuje regułę domenową, wydzielamy politykę
lub value object zamiast rozszerzać kolejną instrukcję warunkową.

Wyjątki:

- reprezentują sytuacje wyjątkowe, nie zwykły przepływ sterowania — z jednym zamierzonym wyjątkiem:
  odrzucenie żądania przez regułę głównego procesu feature'a również kończy się wyjątkiem procesu
  (`AD-014`);
- mają precyzyjny typ i komunikat zawierający użyteczny kontekst;
- zachowują pierwotną przyczynę przy opakowywaniu;
- są przechwytywane na poziomie, który potrafi podjąć sensowną decyzję; w warstwie webowej jest nim
  `@ControllerAdvice`, a nie serwis ani kontroler;
- nie są ignorowane ani zastępowane pustym `catch`.

Nie przechwytujemy ogólnego `Exception` lub `Throwable`, chyba że jest to świadoma granica procesu,
która rejestruje błąd, zachowuje przyczynę i określa dalszy stan systemu.

Obsługa błędów warstwy webowej (`AD-014`):

- Każdy główny proces feature'a ma jeden wyjątek procesu w `exc` tego feature'a, np.
  `LaunchProfileUpdateException`. Rzuca go zarówno odrzucenie przez regułę (bez przyczyny), jak i
  niepowodzenie (z przyczyną). Wyjątek niesie komunikat jako enum implementujący
  `shared.TranslationKeyIf` i nigdy nie przechowuje sekretu.
- Serwis procesu nie zwraca obiektu wyniku odrzucenia. Kontroler nie sprawdza wyniku, a błąd
  walidacji adnotacji zgłasza tym samym wyjątkiem procesu.
- Wyjątek procesu obsługuje `@ControllerAdvice` w korzeniu feature'a, ograniczony przez
  `assignableTypes` do jego kontrolerów i oznaczony `@Order( Ordered.HIGHEST_PRECEDENCE )`. Advice
  loguje wyjątek dokładnie raz i przekierowuje z atrybutami flash.
- Pozostałe wyjątki obsługuje `shared.web.DefaultExceptionHandler`. Nie dodajemy lokalnego `catch`,
  który tylko loguje i zamienia wyjątek na wartość zwracaną.
- `catch` jest dozwolony, gdy zmienia znaczenie błędu: zamiana wyjątku technicznego na wyjątek
  procesu, zamiana checked exception na unchecked, przywrócenie stanu po nieudanej operacji,
  sprzątanie zasobu albo jawnie udokumentowana wartość zastępcza dla niepoprawnej danej.

Własny typ wyjątku tworzymy dopiero wtedy, gdy biblioteka standardowa nie wyraża danego warunku, np.
`IllegalArgumentException` dla niepoprawnego argumentu i `UncheckedIOException` dla awarii wejścia lub
wyjścia. Własne wyjątki umieszczamy w podpakiecie `exc` pakietu będącego ich właścicielem, np.
`shared/exc`; nie tworzymy jednego globalnego pakietu wyjątków dla całej aplikacji.

Parametry wymagane przez kontrakt walidujemy przy wejściu. Znaczenie `null` musi być jawne; brak
wartości zwracanej można reprezentować przez `Optional`, ale nie używamy `Optional` mechanicznie jako
pola encji ani parametru każdej metody.

### Null-safety publicznego API

Każdy publiczny konstruktor i każda publiczna metoda jawnie opisują nullability wszystkich typów
referencyjnych przy użyciu:

- `jakarta.annotation.Nonnull` — parametr lub wynik nie może być `null`;
- `jakarta.annotation.Nullable` — `null` jest dozwoloną i opisaną częścią kontraktu.

Adnotacji nie stosujemy do typów prymitywnych ani metod zwracających `void`. Publiczne pola są
niezalecane; jeśli istnieją, ich typ referencyjny również musi mieć `@Nonnull` albo `@Nullable`.
Adnotację wyniku umieszczamy przy metodzie, a adnotację parametru bezpośrednio przy parametrze.
Na obecnym etapie adnotacje pełnią funkcję informacyjną i dokumentują kontrakt. Nie wymagamy jeszcze
osobnego analizatora statycznego nullability; walidacja wymaganego parametru nadal odbywa się w
runtime zgodnie z poniższą regułą.

Każdy parametr oznaczony `@Nonnull` jest sprawdzany na początku publicznej metody lub konstruktora
przez `Objects.requireNonNull`. Używamy statycznego importu i komunikatu wskazującego nazwę parametru:

```java
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public @Nonnull ServerStatus start(@Nonnull LaunchSpec launchSpec,
                                    @Nullable String requestedBy) {
    requireNonNull(launchSpec, "launchSpec must not be null");
    // ...
}
```

W konstruktorze wynik `requireNonNull` może zostać przypisany bezpośrednio do pola:

```java
public ServerService(@Nonnull ServerRuntime serverRuntime) {
    this.serverRuntime = requireNonNull(serverRuntime, "serverRuntime must not be null");
}
```

`@Nonnull` jest deklaracją kontraktu i nie zastępuje walidacji runtime. `@Nullable` wymaga, aby kod
obsługiwał `null` jawnie; nie wolno natychmiast przekazać takiego parametru do `requireNonNull` ani
pozwolić na przypadkowy `NullPointerException`.

Metoda nadpisująca powtarza nullability kontraktu. Nie wolno zaostrzać parametrów z `@Nullable` do
`@Nonnull` ani osłabiać wyniku z `@Nonnull` do `@Nullable`.

Dla kolekcji osobno określamy nullability kontenera i jego elementów, gdy elementy mogą być `null`.
Preferujemy jednak kolekcje bez elementów `null`. `Optional` zwracany przez publiczną metodę sam jest
`@Nonnull`; brak wartości reprezentuje `Optional.empty()`, nigdy `null`.

Statyczne importy stosujemy dla dobrze znanych funkcji i stałych, gdy skracają kod bez ukrywania
znaczenia. `requireNonNull` zawsze importujemy statycznie. Nie używamy statycznego importu, jeśli
prowadzi do konfliktu nazw albo utrudnia rozpoznanie właściciela mniej znanej operacji.

## 9. Niemutowalność, kolekcje i zasoby

Preferujemy niemutowalne value objects, rekordy i niezmienne kolekcje. Zwracane kolekcje nie powinny
ujawniać wewnętrznego mutowalnego stanu klasy.

Zasoby takie jak pliki, strumienie, areny FFM i uchwyty muszą mieć jednoznacznego właściciela oraz
gwarantowane zamknięcie. Dla `AutoCloseable` używamy try-with-resources. Kod odpowiedzialny za
pozyskanie zasobu odpowiada również za jego zwolnienie, chyba że kontrakt jawnie przekazuje własność.

Współdzielony stan ograniczamy do minimum. Zasady synchronizacji, właściciel wątku oraz dozwolone
przejścia muszą wynikać ze struktury kodu i być udokumentowane zgodnie z `documentation.md`.

## 10. Współczesna Java

Projekt używa Java 25. Korzystamy ze współczesnych, stabilnych mechanizmów języka i biblioteki
standardowej, gdy upraszczają model lub przepływ, między innymi:

- rekordów dla niemutowalnych nośników danych i value objects;
- sealed classes/interfaces dla rzeczywiście zamkniętych hierarchii;
- pattern matching oraz wyrażeń `switch`, gdy są czytelniejsze od rzutowań i kaskad warunków;
- tekstowych bloków dla wielowierszowych treści;
- try-with-resources dla zasobów;
- fabryk niemutowalnych kolekcji, takich jak `List.of` i `Map.of`;
- `java.time` zamiast starszych typów daty i czasu;
- FFM API na uzgodnionej granicy integracji z Win32.

`var` jest dozwolone wyłącznie dla zmiennych lokalnych, gdy typ jest oczywisty z prawej strony i
skraca zapis bez utraty informacji. Nie używamy go, jeśli czytelnik musi przejść do definicji metody,
aby poznać znaczenie wartości.

Stream API stosujemy dla prostych transformacji danych. Pętla jest preferowana, gdy pipeline wymaga
złożonego stanu, wielu skutków ubocznych, zagnieżdżonych lambd albo kilku komentarzy.

Nie używamy funkcji preview bez jawnej decyzji projektowej i konfiguracji kompilacji oraz testów.
Nowość mechanizmu nie jest wystarczającym powodem jego zastosowania.

## 11. Lombok

Lombok jest standardowym narzędziem projektu. Używamy go wszędzie tam, gdzie usuwa mechaniczny
boilerplate bez ukrywania zachowania, kontraktu lub niezmienników klasy. Nie przepisujemy ręcznie
kodu, który Lombok może wygenerować w oczywisty i bezpieczny sposób.

Jeżeli ten sam prosty, niemutowalny model danych można czytelnie wyrazić zarówno przez Java `record`,
jak i klasę z adnotacjami Lombok, zawsze preferujemy `record`. Jest on natywnym elementem języka i
jednoznacznie komunikuje semantykę nośnika danych. Lombok nie zastępuje konstrukcji dostępnej
bezpośrednio w Javie, gdy konstrukcja językowa jest równie czytelna lub czytelniejsza.

Preferowane zastosowania:

- `@Log4j2` dla klas wymagających loggera zgodnie z `security.md`;
- `@Getter` dla pól, których odczyt jest świadomą częścią API;
- `@RequiredArgsConstructor` dla prostego wstrzykiwania wymaganych zależności, o ile wygenerowany
  konstruktor spełnia wszystkie wymagania null-safety, walidacji i Javadoc;
- `@Value` dla niemutowalnego obiektu wartościowego wyłącznie wtedy, gdy rekord Javy nie pasuje do
  wymaganego modelu, np. gdy framework wymaga zwykłej klasy albo model potrzebuje dodatkowego
  prywatnego stanu instancji, którego rekord nie może deklarować;
- `@Builder` tylko wtedy, gdy nazwane ustawianie wielu opcjonalnych wartości realnie poprawia
  czytelność i nie omija metod fabrycznych ani walidacji niezmienników;
- precyzyjne `@EqualsAndHashCode` lub `@ToString` wyłącznie wtedy, gdy ich wygenerowany kontrakt został
  świadomie sprawdzony.

Adnotacje stosujemy możliwie wąsko. Preferujemy `@Getter` na konkretnym polu lub klasie bez setterów
zamiast szerokiego `@Data`. `@Setter` jest dozwolony wyłącznie dla właściwości, która rzeczywiście ma
być mutowalna; nie dodajemy setterów tylko dla wygody testu lub frameworka.

`@Data` nie jest domyślnym wyborem dla encji JPA, modeli domenowych ani obiektów zawierających sekrety.
Automatyczne `toString`, `equals`, `hashCode` i settery mogą ujawnić dane wrażliwe, uruchomić lazy
loading, objąć relacje encji albo naruszyć tożsamość domenową. W takich klasach wybieramy pojedyncze
adnotacje i jawnie określamy uwzględniane pola.

Nie używamy `@SneakyThrows`, ponieważ ukrywa kontrakt błędów. Nie używamy Lombok `val` ani `var`, gdy
językowa deklaracja `var` zapewnia ten sam efekt. Wygenerowany builder, konstruktor lub akcesor nie
może zastępować walidacji domenowej.

Jeżeli publiczny konstruktor wymaga jawnego `@Nonnull`, wywołania `requireNonNull`, dodatkowej
walidacji albo szczegółowego Javadoc, zapisujemy go ręcznie. Oszczędność kilku linii nie ma
pierwszeństwa przed kontraktem publicznego API. Wygenerowane elementy dokumentujemy zgodnie z
`documentation.md` poprzez dokumentację typu i pól będących źródłem kontraktu.

## 12. HTML, CSS i JavaScript

Kod frontendu podlega tym samym zasadom prostoty, czytelności, małych odpowiedzialności i YAGNI co
kod Java. Nie tworzymy rozbudowanego systemu komponentów ani abstrakcji CSS/JS bez istniejących
przypadków użycia, ale nie duplikujemy elementów, które są już wspólne dla wielu widoków.

Wszystkie komentarze w HTML, CSS i JavaScript są zawsze pisane w języku angielskim. Komentarze nie
mogą zawierać sekretów, haseł, tokenów, wewnętrznych ścieżek ani informacji, których nie należy
wysyłać do przeglądarki. Komentarz dodajemy tylko wtedy, gdy opisuje logiczną sekcję, kontrakt
komponentu, nieoczywistą decyzję albo ograniczenie. Nie komentujemy oczywistego znaczenia poprawnie
nazwanego elementu lub stylu.

### HTML i Thymeleaf

Każda istotna logiczna sekcja widoku lub fragment o odpowiedzialności, której nie wyjaśnia
jednoznacznie semantyczny HTML, jest poprzedzona krótkim komentarzem w języku angielskim. Nie
komentujemy każdego pojedynczego tagu. Elementy takie jak poprawnie nazwany `button`, `label`, `nav`
lub prosty kontener powinny być zrozumiałe bez komentarza.

```html
<!-- Primary navigation available on every authenticated page. -->
<nav class="main-navigation" aria-label="Primary navigation">
    <a class="main-navigation__link" th:href="@{/}">Dashboard</a>
</nav>
```

Komentarz umieszczamy bezpośrednio przed opisywaną sekcją lub tagiem otwierającym. Nie komentujemy
osobno tagu zamykającego. Element szablonowy powtarzany przez `th:each` dokumentujemy najwyżej raz
przy jego definicji, jeżeli jego rola nie wynika jasno z nazwy i struktury.

Komentarze muszą pozostać krótkie i nie mogą zastępować semantycznego HTML. Nadal używamy właściwych
elementów (`nav`, `main`, `section`, `button`, `label`, nagłówki), dostępnych nazw, poprawnej hierarchii
nagłówków i atrybutów dostępności. Nie budujemy całego widoku z neutralnych `div`, nawet jeśli każdy
z nich ma komentarz.

Nie stosujemy stylów inline ani skryptów inline. Zachowanie HTMX wyrażamy czytelnymi atrybutami,
a bardziej złożoną logikę przenosimy do modułu JavaScript.

Element interfejsu używany przez więcej niż jeden istniejący widok wydzielamy do wspólnego fragmentu
Thymeleaf. Dotyczy to w szczególności nawigacji, komunikatów, przycisków akcji, formularzy o wspólnej
strukturze, status badges, dialogów i powtarzalnych tabel. Fragment ma mały, jawny zestaw parametrów
i nie zależy od przypadkowych zmiennych modelu strony.

Nie wydzielamy fragmentu dla jednorazowego elementu tylko dlatego, że potencjalnie może kiedyś zostać
ponownie użyty. Gdy pojawia się drugi rzeczywisty konsument albo wspólny element jest z założenia
częścią wielu bieżących widoków, duplikację zastępujemy fragmentem.

### CSS

Spójną sekcję arkusza, kontrakt reużywalnego komponentu oraz nieoczywistą regułę CSS poprzedzamy
krótkim komentarzem po angielsku. Nie komentujemy każdego prostego selektora. Dobrze nazwana klasa i
krótki zestaw deklaracji mają być zrozumiałe bez komentarza.

```css
/* Reusable primary action button used for safe, affirmative operations. */
.button-primary {
    padding: 0.5rem 1rem;
    color: var(--color-on-primary);
    background: var(--color-primary);
}
```

Style utrzymujemy najprostsze, jak to możliwe:

- preferujemy krótkie selektory klasowe o niskiej specyficzności;
- używamy krótkich, pełnych nazw w `kebab-case`, bez niejasnych skrótów;
- nazwa opisuje rolę lub komponent, np. `.server-status`, a nie jego bieżący wygląd, np. `.green-box`;
- element komponentu nazywamy konsekwentnie, np. `.server-card__status`, a wariant lub stan
  `.server-card--compact`, `.is-running` albo `.has-error`;
- klas używanych przez JavaScript nie traktujemy jako kontraktu stylowania; zachowanie podpinamy
  przez jawne atrybuty `data-*`;
- nie używamy identyfikatorów HTML do stylowania;
- unikamy głębokiego zagnieżdżenia selektorów i zależności od dokładnej struktury DOM;
- nie używamy `!important`, poza udokumentowanym obejściem zewnętrznego stylu;
- nie powielamy deklaracji, jeśli można użyć wspólnej klasy komponentu;
- powtarzalne wartości kolorów, odstępów i rozmiarów zapisujemy jako jasno nazwane custom properties;
- nie tworzymy rozbudowanej klasy utility dla pojedynczego użycia;
- usuwamy martwe selektory razem z usuwanym HTML.

Każdy styl, który ma rzeczywiste zastosowanie w wielu widokach lub komponentach, wydzielamy do
wspólnego stylu reużywalnego. Style globalne obejmują wyłącznie bazę aplikacji, layout, tokeny oraz
rzeczywiście wspólne komponenty. Reguły specyficzne dla jednego widoku pozostają przy stylach tego
widoku i nie zanieczyszczają globalnej przestrzeni nazw.

Wspólna klasa CSS opisuje komponent lub rolę interfejsu, np. `.status-badge`, `.action-button` albo
`.data-table`, zamiast konkretnej strony lub szczegółu wyglądu. Nazwy pozostają możliwie krótkie, ale
nigdy kosztem jednoznaczności. Nie łączymy niepowiązanych elementów jednym selektorem tylko dlatego,
że chwilowo wyglądają podobnie.

Zmiana wyglądu wspólnego komponentu nie powinna wymagać poprawek w wielu plikach. Jeżeli HTML,
fragment Thymeleaf i CSS wspólnie tworzą komponent, ich nazwy i kontrakt muszą pozostać spójne.

### JavaScript

JavaScript pozostaje podzielony na natywne moduły ES. Każdy moduł ma jedną odpowiedzialność, nie
tworzy niejawnego stanu globalnego i eksportuje możliwie małe API. Preferujemy `const`, a `let`
stosujemy tylko dla wartości rzeczywiście zmiennych. Nie używamy `var`.

Każda funkcja JavaScript — eksportowana, lokalna, callback, handler zdarzenia oraz funkcja strzałkowa
przypisana do zmiennej — musi mieć bezpośrednio poprzedzający ją komentarz **JSDoc napisany po
angielsku**. JSDoc pełni rolę analogiczną do Javadoc i opisuje odpowiedzialność, parametry, wynik,
wyjątki oraz istotne skutki uboczne.

Używamy odpowiednich tagów:

- `@param` dla każdego parametru, wraz z typem i znaczeniem;
- `@returns` dla każdej funkcji zwracającej wartość;
- `@throws` dla błędów należących do kontraktu;
- `@async` dla funkcji asynchronicznych, jeśli pomaga to narzędziom i czytelnikowi;
- `@template` dla parametrów typów w generycznych funkcjach JSDoc.

```javascript
/**
 * Adds the CSRF token to an outgoing HTMX request.
 *
 * @param {CustomEvent} event HTMX configuration event containing mutable request headers
 * @returns {void}
 * @throws {Error} when the page does not expose the required CSRF token
 */
function configureCsrfHeader(event) {
    const tokenElement = document.querySelector('meta[name="_csrf"]');

    if (tokenElement === null) {
        throw new Error('CSRF token is missing');
    }

    event.detail.headers['X-CSRF-TOKEN'] = tokenElement.content;
}

/**
 * Handles HTMX request configuration for every request issued by the document body.
 *
 * @param {CustomEvent} event HTMX configuration event
 * @returns {void}
 */
const handleHtmxConfigRequest = (event) => {
    configureCsrfHeader(event);
};
```

Złożone anonimowe callbacki wydzielamy do nazwanej i udokumentowanej funkcji. Dla bardzo krótkiego
callbacku nadal wymagany jest JSDoc; jeżeli nie da się go umieścić czytelnie przy funkcji inline,
callback otrzymuje nazwę i osobną deklarację.

Funkcje pozostają krótkie, mają jedną odpowiedzialność i używają guard clauses zamiast głęboko
zagnieżdżonych warunków. Preferujemy `async`/`await` nad długimi łańcuchami `then`, ale zawsze jawnie
obsługujemy odrzucenie operacji i stan błędu widoczny dla użytkownika.

Nie wykonujemy tego samego zapytania DOM wielokrotnie bez potrzeby, nie opieramy logiki na kruchych
selektorach strukturalnych i nie mieszamy generowania dużych fragmentów HTML z logiką zdarzeń.
Wspólne zachowanie używane przez wiele widoków wydzielamy do reużywalnego modułu. Kod właściwy tylko
dla jednej strony pozostaje w module tej strony.

## 13. Refaktoryzacja i zakres zmiany

Kod pozostawiamy co najmniej tak czytelny, jak przed zmianą. Lokalna refaktoryzacja bezpośrednio
potrzebna do bezpiecznej implementacji feature’a jest częścią pracy. Nie łączymy jednak zmiany
funkcjonalnej z szeroką, niezwiązaną przebudową wielu modułów.

Przed wydzieleniem abstrakcji należy wskazać konkretną duplikację, zmienność lub sprzężenie, które
usuwa. Przed dodaniem zależności zewnętrznej należy sprawdzić, czy standardowa biblioteka Javy albo
już używana zależność nie rozwiązuje problemu wystarczająco prosto.

## 14. Kontrola przed zakończeniem zmiany

Przed uznaniem zmiany za kompletną należy sprawdzić:

- czy klasy i metody mają jedną spójną odpowiedzialność;
- czy po przeniesieniach i usunięciach nie pozostały puste pakiety źródłowe lub testowe;
- czy każdy element ma najmniejszą wystarczającą widoczność, a `public` nie zostało użyte wyłącznie
  dla wygody albo wykrywania przez Spring;
- czy serwisy mają interfejs w `service`, implementację w `service.impl`, a konsumenci wstrzykują
  interfejs;
- czy główny przepływ jest płaski i zrozumiały przy szybkim czytaniu;
- czy nie napisano ręcznie boilerplate'u, który bezpiecznie usuwa Java, Lombok, Spring Data albo
  istniejący mechanizm projektu;
- czy nazwy są angielskie, precyzyjne i zgodne z językiem domeny;
- czy metoda lub konstruktor z więcej niż trzema parametrami zawierają domenowo powiązany podzbiór,
  który powinien stać się niemutowalnym obiektem parametrów z prywatnym konstruktorem i metodami
  fabrycznymi;
- czy zastosowanie nazwanej metody fabrycznej poprawiłoby czytelność albo ochronę niezmienników;
- czy nie dodano abstrakcji, wzorca albo opcji bez aktualnej potrzeby;
- czy kompozycja mogła zastąpić dziedziczenie;
- czy duplikowana wiedza została usunięta bez tworzenia sztucznej abstrakcji;
- czy błędy, zasoby, mutowalność i współbieżność mają jawne zasady;
- czy użyte mechanizmy Java 25 poprawiają, a nie pogarszają czytelność;
- czy Lombok usunął możliwy do bezpiecznego wygenerowania boilerplate bez ukrycia walidacji,
  zachowania, sekretów ani kontraktu encji;
- czy dla prostego niemutowalnego modelu danych wybrano `record` zamiast równoważnej klasy Lombok;
- czy klasa wymagająca loggera używa `@Log4j2`, chyba że jawne pole loggera ma konkretną przewagę;
- czy HTML i CSS są zrozumiałe dzięki semantycznej strukturze oraz krótkim, jednoznacznym nazwom;
- czy logiczne sekcje, kontrakty komponentów i nieoczywiste reguły frontendu mają krótki angielski
  komentarz, a oczywiste elementy nie są nim zaśmiecane;
- czy wspólne elementy widoków, style i zachowania zostały wydzielone bez tworzenia abstrakcji
  używanych tylko raz;
- czy każda funkcja JavaScript ma kompletny angielski JSDoc;
- czy usunięto martwy kod, nieaktualne komentarze i zbędne zależności;
- czy dokumentacja i testy spełniają odpowiednio `documentation.md` oraz `unit-tests.md`.
