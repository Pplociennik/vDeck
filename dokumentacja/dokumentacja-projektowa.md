# vDeck — dokumentacja projektowa

> Status: opis zaimplementowanego stanu systemu · Aktualizacja: 2026-08-18

Dokument opisuje, **co system robi i jak to realizuje**. Uzasadnienia decyzji projektowych znajdują się
w [`.docs/architectural_decisions.md`](../.docs/architectural_decisions.md) oraz
[`.docs/first_design_ideas.md`](../.docs/first_design_ideas.md) i nie są tutaj powielane.

Opisane jest wyłącznie zachowanie już zaimplementowane. Obszary zaplanowane, ale nieukończone, są
wymienione w sekcji [8. Stan implementacji i znane ograniczenia](#8-stan-implementacji-i-znane-ograniczenia)
i wyraźnie oznaczone jako niezrealizowane.

---

## Spis treści

- [1. Wprowadzenie](#1-wprowadzenie)
  - [1.1 Przeznaczenie systemu](#11-przeznaczenie-systemu)
  - [1.2 Moduły i stos technologiczny](#12-moduły-i-stos-technologiczny)
  - [1.3 Uruchomienie panelu](#13-uruchomienie-panelu)
- [2. Architektura aplikacji](#2-architektura-aplikacji)
  - [2.1 Podział na pakiety](#21-podział-na-pakiety)
  - [2.2 Start aplikacji](#22-start-aplikacji)
- [3. Konfiguracja](#3-konfiguracja)
  - [3.1 Dwa kanały konfiguracji](#31-dwa-kanały-konfiguracji)
  - [3.2 Ustawienia panelu w vDeck.properties](#32-ustawienia-panelu-w-vdeckproperties)
  - [3.3 Rozwiązywanie ścieżek](#33-rozwiązywanie-ścieżek)
  - [3.4 Komponenty konfiguracji](#34-komponenty-konfiguracji)
- [4. Uwierzytelnianie i dostęp do panelu](#4-uwierzytelnianie-i-dostęp-do-panelu)
  - [4.1 Zakres funkcjonalności](#41-zakres-funkcjonalności)
  - [4.2 Model danych](#42-model-danych)
  - [4.3 Domyślne konto administratora](#43-domyślne-konto-administratora)
  - [4.4 Logowanie](#44-logowanie)
  - [4.5 Wymuszona zmiana hasła](#45-wymuszona-zmiana-hasła)
  - [4.6 Sesje i wylogowanie](#46-sesje-i-wylogowanie)
  - [4.7 Komponenty feature'a](#47-komponenty-featurea)
- [5. Bezpieczeństwo](#5-bezpieczeństwo)
- [6. Persystencja](#6-persystencja)
- [7. Logowanie zdarzeń](#7-logowanie-zdarzeń)
- [8. Stan implementacji i znane ograniczenia](#8-stan-implementacji-i-znane-ograniczenia)
- [9. Indeks diagramów](#9-indeks-diagramów)

---

## 1. Wprowadzenie

### 1.1 Przeznaczenie systemu

vDeck jest lokalnym panelem webowym do zarządzania dedykowanym serwerem Valheim. Aplikacja jest
dystrybuowana razem z instalacją serwera, uruchamiana na tej samej maszynie i obsługiwana z poziomu
przeglądarki. Docelowy zakres produktu obejmuje sterowanie cyklem życia serwera, konfigurację profilu
uruchomieniowego, zarządzanie światami i kopiami bezpieczeństwa, podgląd logów, listy graczy oraz
opcjonalne RCON i mody. Obecnie zaimplementowana jest infrastruktura konfiguracji oraz
uwierzytelnianie dostępu do panelu.

Wspierany jest wyłącznie Windows.

### 1.2 Moduły i stos technologiczny

| Moduł Maven | Odpowiedzialność | Stan |
|---|---|---|
| `vdeck-runtime` | Czysta Java: cykl życia serwera, procesy, sygnały, logi. Bez Springa. | Pusty — brak katalogu `src` |
| `vdeck-app` | Aplikacja Spring Boot: web, bezpieczeństwo, konfiguracja, persystencja. | Zawiera cały istniejący kod |

Zależność jest jednostronna: `vdeck-app` zależy od `vdeck-runtime`.

Stos: Java 25, Maven (wrapper 3.9.16), Spring Boot 4.1.0, Spring MVC + Thymeleaf, Spring Security,
Spring Data JPA + SQLite (`org.xerial:sqlite-jdbc`), Log4j2 jako jedyne API i backend logowania,
Lombok, JUnit 5 + AssertJ + Mockito.

### 1.3 Uruchomienie panelu

Panel nasłuchuje na `127.0.0.1` i domyślnym porcie serwera osadzonego (`8080`); adres jest ustawiony
w `application.yaml`, więc panel nie jest wystawiony poza maszynę lokalną.

Uruchomienie produkcyjne:

```powershell
.\mvnw.cmd package
java -jar vdeck-app\target\vdeck-app-0.0.1-SNAPSHOT.jar
```

Nazwa artefaktu buildu to `vdeck-app-<wersja>.jar`; dokumenty projektowe mówią o `vdeck.jar` jako
nazwie pliku w gotowej instalacji obok `valheim_server.exe`. Katalog, w którym leży uruchomiony jar,
jest katalogiem instalacyjnym opisanym w sekcji [3.3](#33-rozwiązywanie-ścieżek).

Uruchomienie z IDE lub przez `spring-boot:run` **wymaga** wskazania katalogu instalacyjnego, ponieważ
bez niego `ApplicationHome` wskazuje na `vdeck-app/target/classes`:

```powershell
.\mvnw.cmd install -DskipTests
.\mvnw.cmd -pl vdeck-app spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-Dvdeck.server-directory=D:\sciezka\do\sandboxa"
```

---

## 2. Architektura aplikacji

`vdeck-app` jest modularnym monolitem z pionowymi feature slice'ami. Kod webowy, logika aplikacyjna
i persystencja jednej funkcji pozostają w jej podpakiecie.

### 2.1 Podział na pakiety

Bazowy pakiet to `com.github.pplociennik.vdeck`. Istnieją wyłącznie pakiety zawierające realny kod:

| Pakiet | Zawartość |
|---|---|
| `` (bazowy) | `VdeckApplication` — jedyny wyjątek od reguły umieszczania klas w podpakietach |
| `config` | Kompozycja kontekstu Springa: `PathConfiguration`, `SystemPropertiesConfiguration`, `SecurityConfiguration` oraz inicjalizacja danych startowych `DataInitializer` |
| `feature.authentication` | Pionowy feature uwierzytelniania: kontroler, `domain`, `dto`, `persistence`, `service` i `service.impl` |
| `security` | Przekrojowe filtry i handlery Spring Security, niezwiązane z jednym feature'em |
| `shared` | Infrastruktura bez logiki biznesowej: kontrakt ustawień, serwisy ścieżek i properties, wyjątki w `shared.exc` |

Kontrakty serwisów znajdują się w pakiecie `service`, a implementacje w `service.impl`. Konsumenci
wstrzykują interfejs; klasy implementacyjne pozostają pakietowo-prywatne tam, gdzie to możliwe.

### 2.2 Start aplikacji

Start panelu jest procesem wieloetapowym: rozwiązanie katalogu instalacyjnego, przygotowanie pliku
ustawień, aktualizacja schematu bazy i utworzenie konta domyślnego. Błąd rozwiązywania katalogu
bazowego przerywa start, zamiast pozwolić aplikacji działać z niepoprawną konfiguracją.

![Diagram przepływu: start aplikacji vDeck i przygotowanie środowiska](diagramy/wspolne/img/przeplyw-start-aplikacji.svg)

Źródło diagramu: [`diagramy/wspolne/przeplyw-start-aplikacji.mmd`](diagramy/wspolne/przeplyw-start-aplikacji.mmd)

---

## 3. Konfiguracja

### 3.1 Dwa kanały konfiguracji

Konfiguracja jest rozdzielona według właściciela:

| Kanał | Lokalizacja | Właściciel | Edytowalny przez użytkownika |
|---|---|---|---|
| `application.yaml` | wewnątrz jara | infrastruktura Spring Boot: datasource, JPA, adres serwera, ciasteczka sesji | nie |
| `vDeck.properties` | obok `vdeck.jar`, w katalogu instalacyjnym | własne ustawienia panelu | tak |

Bieżąca zawartość `application.yaml` obejmuje nazwę aplikacji, źródło danych SQLite
(`jdbc:sqlite:vdeck.db`), dialekt Hibernate `SQLiteDialect` z `ddl-auto: update` oraz ustawienia
serwera: `address: 127.0.0.1` i ciasteczko sesji z `http-only: true` i `same-site: lax`.

### 3.2 Ustawienia panelu w vDeck.properties

Zbiór ustawień pliku jest zamknięty i opisany typem wyliczeniowym implementującym `SystemPropertyIf`.
Dla pliku `vDeck.properties` jest to `VdeckProperty`:

| Klucz | Znaczenie | Wartość domyślna |
|---|---|---|
| `game.log.directory` | Katalog z plikiem logu serwera gry, względny wobec katalogu instalacyjnego | `vdeck-logs` |

Zachowanie `SystemPropertiesServiceImpl` — jedna instancja obsługuje dokładnie jeden plik:

- plik jest wczytywany **raz, podczas konstrukcji**, i utrzymywany w pamięci; ręczna edycja pliku przy
  działającym panelu zostanie uwzględniona dopiero po restarcie;
- brakujący plik jest tworzony z wartościami domyślnymi;
- brakujący lub pusty klucz zarządzany jest przywracany do wartości domyślnej i zapisywany — to
  jednocześnie ścieżka migracji ustawień dodanych w nowej wersji;
- wpisy nienależące do zbioru zarządzanego są zachowywane, ale komentarze i kolejność kluczy przepadają
  przy pierwszym zapisie pliku przez aplikację;
- po konstrukcji każde zarządzane ustawienie ma niepustą wartość, więc `getValue` jest zwykłym odczytem;
- zapis jest atomowy: dane trafiają do pliku tymczasowego, są wypychane na urządzenie (`FileChannel.force`),
  a następnie podmieniane przez `ATOMIC_MOVE`; nieudany zapis przywraca poprzednią wartość w pamięci,
  więc pamięć i dysk nie mogą się rozjechać;
- do logu trafiają wyłącznie nazwy kluczy, nigdy wartości, ponieważ plik ustawień może zawierać sekrety.

### 3.3 Rozwiązywanie ścieżek

`PathService` jest jedynym komponentem zamieniającym skonfigurowany tekst na `Path`, dzięki czemu
normalizacja i kontrola zasięgu obowiązują w całym panelu.

Katalog bazowy (instalacyjny) to katalog zawierający `vdeck.jar` i `valheim_server.exe`. Jest brany z
`ApplicationHome`, chyba że ustawiono override `vdeck.server-directory`. Rozwiązanie następuje raz,
podczas konstrukcji, więc błędna konfiguracja przerywa start panelu.

`getAbsolutePath( String )` odrzuca wartość, która:

- jest pusta lub złożona wyłącznie z białych znaków — `IllegalArgumentException`;
- jest ścieżką bezwzględną lub składniowo niepoprawną — `PathResolutionException`;
- wychodzi poza katalog instalacyjny bezpośrednio, przez segmenty `..`, przez dowiązanie symboliczne
  albo junction systemu Windows — `PathResolutionException`.

Gdy cel już istnieje, zwracana jest jego postać rzeczywista (`toRealPath`) i ponownie sprawdzany jest
zasięg. Metoda nigdy nie tworzy plików ani katalogów, więc zwrócona ścieżka może jeszcze nie istnieć.

### 3.4 Komponenty konfiguracji

![Diagram klas: infrastruktura konfiguracji i rozwiązywania ścieżek](diagramy/wspolne/img/klasy-konfiguracja.svg)

Źródło diagramu: [`diagramy/wspolne/klasy-konfiguracja.mmd`](diagramy/wspolne/klasy-konfiguracja.mmd)

Dodanie kolejnego pliku ustawień polega na utworzeniu nowego typu wyliczeniowego implementującego
`SystemPropertyIf` i nowego beana `SystemPropertiesService` dla tego pliku. `SystemPropertyIf` celowo
nie niesie nic poza kluczem — wartość domyślna i plik właściciela należą do konkretnego enuma.

---

## 4. Uwierzytelnianie i dostęp do panelu

### 4.1 Zakres funkcjonalności

Feature `authentication` chroni cały panel: każde żądanie wymaga uwierzytelnienia, a konto tworzone
przy pierwszym starcie nie daje dostępu do panelu, dopóki jego hasło początkowe nie zostanie zmienione.

Widoczne zachowanie:

| Adres | Metoda | Dostęp | Efekt |
|---|---|---|---|
| `/login` | GET, POST | publiczny | Formularz logowania generowany przez Spring Security |
| `/change-password` | GET | uwierzytelniony | Formularz wymuszonej zmiany hasła |
| `/change-password` | POST | uwierzytelniony | Zmiana hasła i wylogowanie albo ponowny formularz z komunikatem |
| `/logout` | POST | uwierzytelniony | Domyślne wylogowanie Spring Security |
| pozostałe | dowolna | rola `ADMIN` | Dostęp do panelu |

Panel nie ma na tym etapie własnej strony logowania — używana jest strona generowana przez Spring
Security. Jedynym własnym widokiem jest szablon Thymeleaf `authentication/change-password`.

### 4.2 Model danych

Encja `PanelUser` (tabela `panel_users`) przechowuje komplet stanu logowania:

| Kolumna | Typ | Uwagi |
|---|---|---|
| `username` | `VARCHAR(64)`, klucz główny | Niemodyfikowalna nazwa logowania |
| `password_hash` | `VARCHAR(255)`, niepusty | Jednokierunkowy hash adaptacyjny wraz z identyfikatorem enkodera |
| `role` | `VARCHAR(32)`, niepusty | `PanelRole` zapisany jako `STRING`; obecnie wyłącznie `ADMIN` |
| `password_change_required` | `BOOLEAN`, niepusty | Znacznik konieczności zmiany hasła początkowego |

Encja waliduje własne niezmienniki w konstruktorze: `username` i `passwordHash` nie mogą być puste ani
złożone z białych znaków, a `role` nie może być `null`. Zmiana hasła przechodzi wyłącznie przez metodę
`changePassword( String )`, która ustawia nowy hash **i** zdejmuje znacznik `passwordChangeRequired` —
oba stany nie mogą się rozjechać. Hasło jawne nie jest nigdzie przechowywane; nośnik `PasswordChangeRequest`
nadpisuje `toString()` stałym tekstem z zamaskowanymi polami.

### 4.3 Domyślne konto administratora

`DataInitializer` jest `CommandLineRunner` uruchamianym po starcie kontekstu. Gdy nie istnieje konto o
nazwie `admin`, tworzy konto:

- nazwa: `admin`;
- hasło początkowe: `admin`, zapisane wyłącznie jako hash z `PasswordEncoder`;
- rola: `ADMIN`;
- `passwordChangeRequired`: `true`.

Inicjalizacja jest idempotentna — istniejące rekordy nigdy nie są nadpisywane, więc kolejne starty nie
resetują zmienionego hasła. Błąd zapisu jest propagowany, aby aplikacja nie wystartowała bez zdatnych
poświadczeń.

### 4.4 Logowanie

`PanelUserServiceImpl.loadUserByUsername` ładuje konto i buduje `UserDetails`. Do uprawnień trafia
`ROLE_` + nazwa roli, a gdy konto ma znacznik zmiany hasła — dodatkowo uprawnienie techniczne
`PASSWORD_CHANGE_REQUIRED`. To uprawnienie jest jedynym nośnikiem ograniczenia pierwszego logowania:
odczytują je zarówno handler sukcesu logowania, jak i filtr żądań, dzięki czemu ograniczenie działa
także dla sesji nawiązanej przed zmianą hasła.

![Diagram przepływu: logowanie do panelu i skierowanie do wymuszonej zmiany hasła](diagramy/authentication/img/przeplyw-logowanie.svg)

Źródło diagramu: [`diagramy/authentication/przeplyw-logowanie.mmd`](diagramy/authentication/przeplyw-logowanie.mmd)

`PasswordChangeAuthenticationSuccessHandler` przekierowuje konto z ograniczeniem prosto na
`/change-password`, a pozostałe logowania deleguje do `SavedRequestAwareAuthenticationSuccessHandler`,
więc po zwykłym zalogowaniu użytkownik wraca do adresu, o który pierwotnie prosił.

`PasswordChangeRequiredFilter` jest wpięty za `UsernamePasswordAuthenticationFilter` i działa raz na
żądanie. Dla konta z uprawnieniem `PASSWORD_CHANGE_REQUIRED` dopuszcza wyłącznie ścieżki
`/change-password` i `/logout`; każde inne żądanie kończy się przekierowaniem na `/change-password`.

### 4.5 Wymuszona zmiana hasła

Formularz obsługuje `PasswordChangeController`, a decyzje o poprawności podejmuje
`PanelUserServiceImpl.changePassword`. Walidacja adnotacjami (`@NotBlank`, `@Size(min = 12, max = 128)`
dla nowego hasła i potwierdzenia) odrzuca wejście przed sięgnięciem do bazy. Po każdym niepowodzeniu
formularz jest renderowany ponownie z **pustymi** polami i pojedynczym komunikatem; komunikaty nie
zdradzają, który element poświadczeń był niepoprawny bardziej, niż wymaga tego obsługa błędu.

Wynik operacji jest zwracany jako `PasswordChangeResult`:

| Wynik | Warunek | Komunikat dla użytkownika |
|---|---|---|
| `CURRENT_PASSWORD_INCORRECT` | Aktualne hasło nie pasuje do zapisanego hasha | „The current password is incorrect." |
| `NEW_PASSWORD_MISMATCH` | Nowe hasło różni się od potwierdzenia | „The new password and confirmation do not match." |
| `NEW_PASSWORD_UNCHANGED` | Nowe hasło jest identyczne z aktualnym | „The new password must differ from the current password." |
| `SUCCESS` | Wszystkie kontrole przeszły | brak — następuje wylogowanie i przekierowanie na `/login` |

![Diagram przepływu: wymuszona zmiana hasła panelu vDeck](diagramy/authentication/img/przeplyw-zmiana-hasla.svg)

Źródło diagramu: [`diagramy/authentication/przeplyw-zmiana-hasla.mmd`](diagramy/authentication/przeplyw-zmiana-hasla.mmd)

Po sukcesie sesja jest unieważniana, a kontekst bezpieczeństwa czyszczony, więc użytkownik musi
zalogować się nowym hasłem. Uprawnienie `PASSWORD_CHANGE_REQUIRED` nie pojawi się już przy kolejnym
załadowaniu konta.

### 4.6 Sesje i wylogowanie

`SecurityConfiguration` ustawia:

- `sessionFixation().changeSessionId()` — identyfikator sesji jest zmieniany po zalogowaniu;
- `maximumSessions( 1 )` z `maxSessionsPreventsLogin( false )` — nowe logowanie unieważnia poprzednią
  sesję zamiast blokować logowanie; wygasła sesja trafia na `/login?expired`;
- `HttpSessionEventPublisher` — wymagany, aby kontrola równoległych sesji widziała zakończone sesje;
- domyślne wylogowanie Spring Security (`POST /logout`).

Ciasteczko sesji jest `HttpOnly` i `SameSite=Lax` (`application.yaml`). Ochrona CSRF pozostaje w
domyślnej, włączonej konfiguracji Spring Security.

### 4.7 Komponenty feature'a

![Diagram klas: feature authentication oraz współpracujące komponenty bezpieczeństwa](diagramy/authentication/img/klasy-komponenty.svg)

Źródło diagramu: [`diagramy/authentication/klasy-komponenty.mmd`](diagramy/authentication/klasy-komponenty.mmd)

Filtr i handler bezpieczeństwa leżą w przekrojowym pakiecie `security`, a nie w feature'rze, ponieważ
dotyczą każdego żądania do panelu. `DataInitializer` należy do `config`, mimo że wstrzykuje repozytorium
uwierzytelniania, ponieważ jest elementem inicjalizacji startowej aplikacji.

---

## 5. Bezpieczeństwo

Zaimplementowane mechanizmy przekrojowe:

- **Hasła.** Wyłącznie hashe adaptacyjne z `PasswordEncoderFactories.createDelegatingPasswordEncoder()`,
  czyli aktualna rekomendacja Spring Security wraz z identyfikatorem enkodera w zapisanej wartości.
  Ten sam enkoder obsługuje hasło początkowe i zmienione, więc zmiana rekomendacji nie wymaga migracji.
- **Brak wycieku sekretów.** Hasła jawne nie są logowane ani zwracane w `toString()`. Serwis ustawień
  loguje tylko nazwy kluczy. Logi zmiany hasła nie zawierają nazwy konta ani wartości.
- **Domyślne poświadczenia.** Konto `admin`/`admin` jest bezużyteczne poza formularzem zmiany hasła —
  ograniczenie jest egzekwowane po stronie serwera przy każdym żądaniu, a nie tylko po zalogowaniu.
- **Dostęp.** Wszystkie żądania poza `/change-password` wymagają roli `ADMIN`; `/change-password`
  wymaga samego uwierzytelnienia, ponieważ konto z ograniczeniem musi móc się tam dostać.
- **Ekspozycja sieciowa.** Panel nasłuchuje wyłącznie na `127.0.0.1`.
- **Granice systemu plików.** Każda skonfigurowana ścieżka przechodzi przez `PathService` i nie może
  opuścić katalogu instalacyjnego — także przez dowiązania i junction (patrz [3.3](#33-rozwiązywanie-ścieżek)).
- **Trwałość ustawień.** Zapis pliku ustawień jest atomowy i wymuszony na urządzenie, więc utrata
  zasilania nie zostawia pustego pliku konfiguracji.

## 6. Persystencja

Bazą jest jednoplikowy SQLite (`vdeck.db`) obsługiwany przez Spring Data JPA z dialektem
`org.hibernate.community.dialect.SQLiteDialect`. Schemat jest utrzymywany przez `ddl-auto: update`.

Obecnie utrwalana jest jedna encja — `PanelUser` w tabeli `panel_users`. Repozytorium `UserRepository`
rozszerza `JpaRepository< PanelUser, String >` i nie deklaruje własnych metod. Operacje zmieniające
stan (`DataInitializer.run`, `PanelUserServiceImpl.changePassword`) są `@Transactional`; zmiana hasła
zapisuje się przez dirty checking zarządzanej encji, bez jawnego `save`.

## 7. Logowanie zdarzeń

Log4j2 jest jedynym API i backendem logowania; `spring-boot-starter-logging` jest wykluczony ze
wszystkich starterów, które go wciągają. Klasy produkcyjne używają Lombokowego `@Log4j2`.

Zdarzenia rejestrowane obecnie:

| Poziom | Zdarzenie |
|---|---|
| `INFO` | Rozwiązanie katalogu bazowego; utworzenie pliku ustawień z domyślnymi wartościami; wczytanie ustawień; zmiana ustawienia; utworzenie domyślnego administratora; zmiana hasła użytkownika panelu |
| `WARN` | Przywrócenie domyślnej wartości brakującego ustawienia; nieudane usunięcie pliku tymczasowego |
| `DEBUG` | Źródło katalogu bazowego (override albo `ApplicationHome`); pominięcie inicjalizacji istniejącego administratora |

Żaden komunikat nie zawiera hasła, hasha ani wartości ustawień.

## 8. Stan implementacji i znane ograniczenia

Zaimplementowane: infrastruktura konfiguracji (`shared`), rozwiązywanie ścieżek, uwierzytelnianie
panelu wraz z wymuszoną zmianą hasła początkowego.

**Niezaimplementowane** — wymienione wyłącznie jako kontekst, żaden z tych obszarów nie działa:
sterowanie cyklem życia serwera Valheim, moduł `vdeck-runtime` (nie ma katalogu `src`), profile
uruchomieniowe, światy i kopie bezpieczeństwa, podgląd logów, listy graczy, RCON, mody, harmonogramy
i aktualizacje serwera.

Znane ograniczenia stanu bieżącego:

- panel nie ma własnej strony logowania ani żadnej strony głównej — po zalogowaniu konta bez
  ograniczenia zapamiętane żądanie prowadzi do zasobu, którego jeszcze nie ma;
- rejestracja i zarządzanie kontami nie istnieją: jedynym kontem jest `admin` tworzony przy starcie,
  a `PanelRole` ma jedną wartość;
- brak mechanizmu odzyskiwania dostępu po utracie hasła — odzyskanie wymaga ingerencji w plik bazy;
- ścieżka bazy `jdbc:sqlite:vdeck.db` z `application.yaml` jest względna wobec katalogu roboczego
  procesu i **nie** przechodzi przez `PathService`;
- plik ustawień jest czytany wyłącznie przy starcie, więc ręczna edycja wymaga restartu panelu;
- zapis pliku ustawień gubi komentarze i kolejność kluczy;
- brak testów ArchUnit egzekwujących opisane granice pakietów.

## 9. Indeks diagramów

W treści dokumentu osadzone są wygenerowane obrazki `.svg`, dzięki czemu diagramy są widoczne w każdym
podglądzie Markdown. Źródłem prawdy pozostaje plik `.mmd`; obrazek leży w podkatalogu `img/` obok
źródła i **po każdej zmianie źródła musi zostać wygenerowany ponownie**:

```powershell
npx -y @mermaid-js/mermaid-cli `
  -i dokumentacja\diagramy\<feature>\<nazwa>.mmd `
  -o dokumentacja\diagramy\<feature>\img\<nazwa>.svg `
  -b white -c dokumentacja\diagramy\mermaid-config.json
```

Konfiguracja `mermaid-config.json` wyłącza etykiety HTML — bez tego napisy trafiają do
`foreignObject` i nie wyświetlają się, gdy SVG jest osadzone jako obrazek.

| Diagram | Rodzaj | Obrazek | Źródło | Opisany w |
|---|---|---|---|---|
| Start aplikacji vDeck | przepływ | [`img/przeplyw-start-aplikacji.svg`](diagramy/wspolne/img/przeplyw-start-aplikacji.svg) | [`diagramy/wspolne/przeplyw-start-aplikacji.mmd`](diagramy/wspolne/przeplyw-start-aplikacji.mmd) | [2.2](#22-start-aplikacji) |
| Infrastruktura konfiguracji | klasy | [`img/klasy-konfiguracja.svg`](diagramy/wspolne/img/klasy-konfiguracja.svg) | [`diagramy/wspolne/klasy-konfiguracja.mmd`](diagramy/wspolne/klasy-konfiguracja.mmd) | [3.4](#34-komponenty-konfiguracji) |
| Logowanie do panelu | przepływ | [`img/przeplyw-logowanie.svg`](diagramy/authentication/img/przeplyw-logowanie.svg) | [`diagramy/authentication/przeplyw-logowanie.mmd`](diagramy/authentication/przeplyw-logowanie.mmd) | [4.4](#44-logowanie) |
| Wymuszona zmiana hasła | przepływ | [`img/przeplyw-zmiana-hasla.svg`](diagramy/authentication/img/przeplyw-zmiana-hasla.svg) | [`diagramy/authentication/przeplyw-zmiana-hasla.mmd`](diagramy/authentication/przeplyw-zmiana-hasla.mmd) | [4.5](#45-wymuszona-zmiana-hasła) |
| Feature authentication | klasy | [`img/klasy-komponenty.svg`](diagramy/authentication/img/klasy-komponenty.svg) | [`diagramy/authentication/klasy-komponenty.mmd`](diagramy/authentication/klasy-komponenty.mmd) | [4.7](#47-komponenty-featurea) |
