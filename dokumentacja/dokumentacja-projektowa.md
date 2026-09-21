# vDeck — dokumentacja projektowa

> Status: opis zaimplementowanego stanu systemu · Aktualizacja: 2026-09-21

Dokument opisuje, **co system robi i jak to realizuje**. Uzasadnienia decyzji projektowych znajdują się
w [`.docs/architectural_decisions.md`](../.docs/architectural_decisions.md) oraz
[`.docs/design.md`](../.docs/design.md) i nie są tutaj powielane.

Opisane jest wyłącznie zachowanie już zaimplementowane. Obszary zaplanowane, ale nieukończone, są
wymienione w sekcji [9. Stan implementacji i znane ograniczenia](#9-stan-implementacji-i-znane-ograniczenia)
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
  - [3.5 Tłumaczenia interfejsu](#35-tłumaczenia-interfejsu)
- [4. Uwierzytelnianie i dostęp do panelu](#4-uwierzytelnianie-i-dostęp-do-panelu)
  - [4.1 Zakres funkcjonalności](#41-zakres-funkcjonalności)
  - [4.2 Model danych](#42-model-danych)
  - [4.3 Domyślne konto administratora](#43-domyślne-konto-administratora)
  - [4.4 Logowanie](#44-logowanie)
  - [4.5 Zmiana hasła](#45-zmiana-hasła)
  - [4.6 Sesje i wylogowanie](#46-sesje-i-wylogowanie)
  - [4.7 Komponenty feature'a](#47-komponenty-featurea)
- [5. Warstwa interfejsu użytkownika](#5-warstwa-interfejsu-użytkownika)
  - [5.1 Zasoby statyczne i arkusze stylów](#51-zasoby-statyczne-i-arkusze-stylów)
  - [5.2 Wyspa akcji](#52-wyspa-akcji)
  - [5.3 Ikony](#53-ikony)
  - [5.4 Ekrany panelu](#54-ekrany-panelu)
  - [5.5 Menu boczne](#55-menu-boczne)
- [6. Bezpieczeństwo](#6-bezpieczeństwo)
- [7. Persystencja](#7-persystencja)
- [8. Logowanie zdarzeń](#8-logowanie-zdarzeń)
- [9. Stan implementacji i znane ograniczenia](#9-stan-implementacji-i-znane-ograniczenia)
- [10. Indeks diagramów](#10-indeks-diagramów)

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
jest katalogiem instalacyjnym opisanym w sekcji [3.3](#33-rozwiązywanie-ścieżek). Przy pierwszym
starcie powstają w nim `vDeck.properties` oraz plik bazy `vdeck.db`.

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
| `config` | Kompozycja kontekstu Springa: `PathConfiguration`, `SystemPropertiesConfiguration`, `SecurityConfiguration` oraz inicjalizacja startowa `DataInitializer` |
| `feature.authentication` | Pionowy feature uwierzytelniania: kontroler, `domain`, `dto`, `persistence`, `service` i `service.impl` |
| `security` | Przekrojowe filtry i handlery Spring Security, niezwiązane z jednym feature'em |
| `shared` | Infrastruktura bez logiki biznesowej: kontrakt ustawień, kontrakt kluczy tłumaczeń `TranslationKeyIf`, serwisy ścieżek i properties, wyjątki w `shared.exc` |

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
(`jdbc:sqlite:vdeck.db`), dialekt Hibernate `SQLiteDialect` z `ddl-auto: update`, zachowanie
`MessageSource` (`spring.messages.use-code-as-default-message`) oraz ustawienia serwera:
`address: 127.0.0.1` i ciasteczko sesji z `http-only: true` i `same-site: lax`.

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

### 3.5 Tłumaczenia interfejsu

Interfejs jest w języku polskim, ale wszystkie teksty przechodzą przez mechanizm tłumaczeń Springa
(patrz [`AD-009`](../.docs/architectural_decisions.md)). Teksty nie są kanałem ustawień z
[3.1](#31-dwa-kanały-konfiguracji) ani zasobem zewnętrznym — są częścią aplikacji.

| Element | Wartość |
|---|---|
| Plik | `vdeck-app/src/main/resources/messages.properties` |
| Źródło | Classpath aplikacji, przez domyślny `MessageSource` Spring Boota |
| Zapis znaków | Czysto ASCII — polskie znaki jako sekwencje `\uXXXX` |
| Konfiguracja | `spring.messages.use-code-as-default-message: true` w `application.yaml` |

Zachowanie:

- Panel ma jeden język, więc nie istnieje plik `messages_<język>.properties`, katalog tłumaczeń w
  instalacji ani własna klasa `@Configuration` tworząca `MessageSource`. Bundle rozwiązuje się dla
  każdego locale, ponieważ plik bazowy nie ma sufiksu językowego.
- Klucz bez tłumaczenia renderuje się jako sam klucz — widoczna usterka tekstu jest bezpieczniejsza
  niż błąd całej strony.
- Zmiana albo dodanie tekstu jest zmianą w repozytorium i wymaga nowego wydania panelu.
- Kod Javy nigdy nie przekazuje gotowego tekstu, tylko klucz; zamknięte zbiory komunikatów opisują
  typy wyliczeniowe implementujące `TranslationKeyIf`, jak `PasswordChangeMessage`.

---

## 4. Uwierzytelnianie i dostęp do panelu

### 4.1 Zakres funkcjonalności

Feature `authentication` chroni cały panel: każde żądanie wymaga uwierzytelnienia, a konto tworzone
przy pierwszym starcie nie daje dostępu do panelu, dopóki jego hasło początkowe nie zostanie zmienione.

Widoczne zachowanie:

| Adres | Metoda | Dostęp | Efekt |
|---|---|---|---|
| `/login` | GET, POST | publiczny | Formularz logowania generowany przez Spring Security |
| `/initial-password-change` | GET | uprawnienie `PASSWORD_CHANGE_REQUIRED` | Formularz wymuszonej zmiany hasła początkowego, bez pola aktualnego hasła |
| `/initial-password-change` | POST | uprawnienie `PASSWORD_CHANGE_REQUIRED` | Zmiana hasła i wylogowanie albo ponowny formularz z komunikatem |
| `/change-password` | GET | rola `ADMIN` | Formularz zmiany hasła na życzenie, z polem aktualnego hasła i wyspą akcji |
| `/change-password` | POST | rola `ADMIN` | Zmiana hasła i wylogowanie albo ponowny formularz z komunikatem |
| `/` | GET | rola `ADMIN` | Strona główna panelu |
| `/logout` | POST | uwierzytelniony | Domyślne wylogowanie Spring Security |
| `/css/**`, `/icons/**`, `/img/**` | GET | rola `ADMIN` | Zasoby statyczne interfejsu; osiągalne także przed zmianą hasła początkowego |
| `/js/**` | GET | rola `ADMIN` | Moduły JavaScript interfejsu; nieosiągalne przed zmianą hasła początkowego, bo ekran wymuszonej zmiany nie używa skryptów |
| pozostałe | dowolna | rola `ADMIN` | Dostęp do panelu |

Zmiana hasła ma **dwa osobne ekrany**, bo są to dwie różne operacje. Wymuszona zmiana hasła
początkowego nie pyta o aktualne hasło — konto używa opublikowanego hasła domyślnego, którego
użytkownik nigdy nie wybierał — i dlatego jej adres jest zarezerwowany dla kont z uprawnieniem
`PASSWORD_CHANGE_REQUIRED`. Zmiana na życzenie, uruchamiana z wyspy akcji w dowolnym momencie, wymaga
podania aktualnego hasła i jest dostępna dla każdego konta z rolą `ADMIN`. Konto przed zmianą hasła
początkowego nie może otworzyć tego drugiego ekranu — filtr odsyła je na ekran wymuszony.

Panel nie ma na tym etapie własnej strony logowania — używana jest strona generowana przez Spring
Security. Własne widoki Thymeleaf osiągalne pod adresem to `authentication/change-password`,
`authentication/initial-password-change` oraz `home/index`; opisuje je sekcja
[5. Warstwa interfejsu użytkownika](#5-warstwa-interfejsu-użytkownika).

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
`/initial-password-change`, a pozostałe logowania deleguje do
`SavedRequestAwareAuthenticationSuccessHandler`, więc po zwykłym zalogowaniu użytkownik wraca do
adresu, o który pierwotnie prosił, a przy braku zapamiętanego żądania trafia na stronę główną `/`.

`PasswordChangeRequiredFilter` jest wpięty za `UsernamePasswordAuthenticationFilter` i działa raz na
żądanie. Dla konta z uprawnieniem `PASSWORD_CHANGE_REQUIRED` dopuszcza:

- dokładne ścieżki `/initial-password-change` i `/logout`;
- żądania zaczynające się od `/css/`, `/icons/` albo `/img/`, czyli zasoby statyczne, bez których
  ekran zmiany hasła nie ma stylów, ikon ani tekstur.

Ekran `/change-password` **nie** jest na tej liście: pytałby o aktualne hasło, którego konto z hasłem
początkowym nigdy nie wybierało. Każde inne żądanie kończy się przekierowaniem na
`/initial-password-change`. Dopasowanie zasobów działa
wyłącznie na początku ścieżki, więc adres panelu zawierający katalog zasobów w dalszej części
(`/servers/css/list`) pozostaje zablokowany, a wyjątek nie omija reguł autoryzacji łańcucha
bezpieczeństwa — zasoby nadal wymagają roli `ADMIN`.

### 4.5 Zmiana hasła

Zmiana hasła ma dwa warianty, obsługiwane przez osobne kontrolery i osobne operacje serwisu:

| Wariant | Adres i kontroler | Operacja serwisu | Nośnik żądania | Pyta o aktualne hasło | Wyspa akcji |
|---|---|---|---|---|---|
| Wymuszona zmiana hasła początkowego | `/initial-password-change`, `InitialPasswordChangeController` | `changeInitialPassword` | `InitialPasswordChangeRequest` | nie | nie |
| Zmiana na życzenie | `/change-password`, `PasswordChangeController` | `changePassword` | `PasswordChangeRequest` | tak | tak |

Walidacja adnotacjami (`@NotBlank`, `@Size(min = 12, max = 128)` dla nowego hasła i potwierdzenia)
odrzuca wejście przed sięgnięciem do bazy. Dalsze kontrole wykonuje `PanelUserServiceImpl`, a pierwsza
nieudana kontrola kończy operację:

1. **tylko wariant na życzenie** — zgodność aktualnego hasła z zapisanym hashem;
2. **tylko wariant wymuszony** — czy konto nadal ma znacznik `passwordChangeRequired`; jego brak
   oznacza, że hasło zostało już wybrane, więc pominięcie kontroli aktualnego hasła przestaje być
   dopuszczalne i operacja kończy się `IllegalStateException`;
3. zgodność nowego hasła z potwierdzeniem;
4. różnicę względem aktualnego hasła;
5. wymagany zestaw rodzajów znaków — cyfrę, małą literę, wielką literę oraz znak specjalny, czyli
   znak niebędący literą, cyfrą ani białym znakiem.

Po każdym niepowodzeniu formularz jest renderowany ponownie z **pustymi** polami i pojedynczym
komunikatem; komunikaty nie zdradzają, który element poświadczeń był niepoprawny bardziej, niż wymaga
tego obsługa błędu.

Wynik operacji jest rekordem `PasswordChangeResult`, który niesie **status oraz klucz komunikatu do
pokazania**. Dzięki temu kontroler nie tłumaczy statusu na własne brzmienie i oba ekrany opisują ten
sam wynik identycznie. Rekord pilnuje niezmiennika: sukces nie ma klucza komunikatu, a odrzucenie
zawsze go ma.

| `PasswordChangeStatus` | Warunek | `messageKey` |
|---|---|---|
| `CURRENT_PASSWORD_INCORRECT` | Aktualne hasło nie pasuje do zapisanego hasha (tylko wariant na życzenie) | `PasswordChangeMessage.CURRENT_PASSWORD_INCORRECT` |
| `NEW_PASSWORD_MISMATCH` | Nowe hasło różni się od potwierdzenia | `PasswordChangeMessage.NEW_PASSWORD_MISMATCH` |
| `NEW_PASSWORD_UNCHANGED` | Nowe hasło jest identyczne z aktualnym | `PasswordChangeMessage.NEW_PASSWORD_UNCHANGED` |
| `NEW_PASSWORD_TOO_WEAK` | Nowe hasło nie zawiera cyfry, małej litery, wielkiej litery i znaku specjalnego | `PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK` |
| `SUCCESS` | Wszystkie kontrole przeszły | brak — następuje wylogowanie i przekierowanie na `/login` |

Błąd walidacji adnotacji nie tworzy `PasswordChangeResult` — kontroler od razu publikuje
`PasswordChangeMessage.INVALID_INPUT`, bo żądanie nie dotarło do serwisu.

Kontroler dodaje do modelu wyłącznie klucz tłumaczenia (`passwordChangeErrorKey`), a brzmienie
komunikatu pochodzi z pliku tłumaczeń opisanego w [3.5](#35-tłumaczenia-interfejsu).

![Diagram przepływu: zmiana hasła panelu vDeck w obu wariantach](diagramy/authentication/img/przeplyw-zmiana-hasla.svg)

Źródło diagramu: [`diagramy/authentication/przeplyw-zmiana-hasla.mmd`](diagramy/authentication/przeplyw-zmiana-hasla.mmd)

Po sukcesie — w obu wariantach — sesja jest unieważniana, a kontekst bezpieczeństwa czyszczony, więc
użytkownik musi zalogować się nowym hasłem. Po wymuszonej zmianie uprawnienie
`PASSWORD_CHANGE_REQUIRED` nie pojawi się już przy kolejnym załadowaniu konta.

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

`PasswordChangeStatus` i `PasswordChangeResult` należą do `domain`, a `PasswordChangeMessage` — do
korzenia feature'a, bo zawiera również `INVALID_INPUT`, komunikat walidacji warstwy webowej, którego
serwis nigdy nie zwraca. Rekord wyniku wiąże je, nie znając zbioru komunikatów: jego pole
`messageKey` ma typ `shared.TranslationKeyIf`, więc `domain` zależy wyłącznie od pakietu wspólnego.

---

## 5. Warstwa interfejsu użytkownika

Reguły obowiązujące przy budowie interfejsu opisuje [`.docs/ui.md`](../.docs/ui.md); ta sekcja opisuje
wyłącznie stan zaimplementowany. Interfejs jest budowany z szablonów Thymeleaf i arkuszy CSS oraz
natywnych modułów ES bez procesu budowania frontendu i bez zależności od CDN — panel wyświetla się
w całości z zasobów wbudowanych w aplikację. Jedynym modułem jest obecnie `side-menu.js`, sterujący
menu bocznym.

### 5.1 Zasoby statyczne i arkusze stylów

```text
vdeck-app/src/main/resources/
├── static/
│   ├── css/
│   │   ├── variables.css        ← wszystkie zmienne CSS panelu
│   │   ├── base.css             ← baza dokumentu, layout, komponenty wspólne
│   │   └── authentication.css   ← styl ekranów hasła
│   ├── icons/icons.svg          ← jeden sprite ze wszystkimi ikonami
│   ├── img/                     ← page-, panel-, island- i side-menu-background.jpg
│   └── js/side-menu.js          ← zachowanie menu bocznego
├── messages.properties          ← polski zestaw tekstów interfejsu
└── templates/
    ├── common/island.html          ← komponent wspólny: wyspa akcji
    ├── common/password-rules.html  ← komponent wspólny: wymagania dla nowego hasła
    ├── common/side-menu.html       ← komponent wspólny: menu boczne
    ├── home/index.html             ← strona główna panelu
    ├── authentication/change-password.html
    └── authentication/initial-password-change.html
```

- `variables.css` jest jedynym miejscem definicji zmiennych CSS. Każdy pozostały arkusz zaczyna się
  od `@import url("variables.css")` i nie definiuje własnych tokenów.
- `base.css` zawiera bazę dokumentu, wyśrodkowanie widoku (`.page-center`), wspólną powierzchnię
  z ramą i teksturą (`.surface`), kontrakt ikony (`.icon`) oraz style komponentów wspólnych.
- Interfejs używa czterech tekstur: `page-background.jpg` na tle strony, `panel-background.jpg` na
  panelach, `island-background.jpg` na wyspie akcji oraz `side-menu-background.jpg` w menu bocznym.
  Panel jest przypadkiem domyślnym: `.surface` ustawia teksturę panelu, a powierzchnia o innej
  teksturze nadpisuje wyłącznie lokalną właściwość `--surface-texture` — robią to modyfikatory
  `.surface--island` i `.surface--side-menu`.
- Kontrolki — przyciski i pola formularza — mają jednolicie czarne tło, więc odcinają się od tekstury
  zamiast jej prześwitywać. Stan wyróżnia jasność tła (`hover`), obramowanie akcentem (`focus`) oraz
  przygaszona ikona lub etykieta (`disabled`); wyłączona kontrolka nie jest wygaszana przezroczystością,
  bo pokazałaby wtedy teksturę pod spodem.
- Tło strony to tekstura `page-background.jpg` przyciemniona winietą. Obie warstwy są rysowane
  w jednym stałym elemencie za treścią, bez `background-attachment: fixed`, którego przeglądarki
  mobilne nie renderują stabilnie.
- Układ jest responsywny w obu orientacjach: wysokości pełnoekranowe liczone są w `dvh`, obszary
  dotykowe mają co najmniej 44 × 44 px, a przy szerokości poniżej 33 rem wyspa zawija grupy akcji.
- **Strona nie przewija się w żadnej osi.** `body` jest powłoką o wysokości jednego widoku
  (`100dvh`, `overflow: hidden`); wyspa zajmuje swoją wysokość, a obszar treści (`.page-content`)
  bierze resztę. Gdy widok nie mieści się w bardzo niskim oknie, układ najpierw oddaje odstępy
  dekoracyjne, a dopiero potem przewija się sam obszar treści albo karta widoku — nigdy strona.

### 5.2 Wyspa akcji

Wyspa jest komponentem wspólnym — plik `templates/common/island.html` z fragmentem `island`, który
widok osadza przez `th:replace="~{common/island :: island}"`. Jest to pasek nagłówka wyśrodkowany
w poziomie u góry strony, o zaokrąglonych rogach, grubej czarnej ramie, teksturze
`island-background.jpg` i wysokości nieprzekraczającej 100 px.

Wyspa dzieli się na trzy grupy akcji rozdzielone pionowymi liniami o wysokości 80 % wyspy:

| Grupa | Szerokość | Akcje | Stan |
|---|---|---|---|
| Sterowanie serwerem | najszersza | start, zatrzymanie, restart, wymuszone zatrzymanie | Przyciski bez akcji — funkcjonalność nie jest zaimplementowana |
| Kopie bezpieczeństwa | nieco węższa | utworzenie kopii świata, utworzenie kopii serwera | Przyciski bez akcji — funkcjonalność nie jest zaimplementowana |
| Zarządzanie panelem | wąska | zmiana hasła, wylogowanie | Działa: odnośnik do `/change-password` oraz formularz `POST /logout` obsługiwany przez domyślne wylogowanie Spring Security |

Przyciski są kwadratowe i zawierają wyłącznie ikonę, więc każdy ma `aria-label` z nazwą akcji.
Na wąskich ekranach grupy zawijają się do kolejnych wierszy, a separatory — które nie leżałyby już
między sąsiadami — są ukrywane.

Formularz wylogowania korzysta z `th:action`, więc Thymeleaf dokłada do niego ukryty token CSRF
wymagany przez łańcuch bezpieczeństwa. Wyspa nie jest osadzana na ekranie wymuszonej zmiany hasła
początkowego — konto przed zmianą hasła nie może wykonać żadnej z jej akcji.

### 5.3 Ikony

Wszystkie ikony pochodzą z jednego lokalnego sprite'a `/icons/icons.svg` i są osadzane przez `<use>`.
Symbole są monochromatyczne i dziedziczą kolor komponentu przez `currentColor`; sprite zawiera
`icon-play`, `icon-stop`, `icon-restart`, `icon-power`, `icon-world-backup`, `icon-server-backup`,
`icon-key`, `icon-logout`, `icon-menu` (przycisk menu bocznego) i `icon-chevron` (stan gałęzi menu). Obie ikony kopii bezpieczeństwa mają ten sam znacznik duplikatu, ponieważ
przyciski tworzą kopię, a nie pobierają pliku.

Sprite musi być osiągalny również na ekranie wymuszonej zmiany hasła, dlatego
`PasswordChangeRequiredFilter` przepuszcza katalogi zasobów statycznych (patrz [4.4](#44-logowanie)).

### 5.4 Ekrany panelu

- **Strona główna** (`home/index.html`, `HomeController`, adres `/`) osadza wyspę akcji nad pustym
  obszarem treści oraz menu boczne. Na tym etapie pokazuje wyłącznie tło panelu, wyspę i menu —
  treść pojawi się razem z funkcjonalnościami, które ma prezentować.
- **Wymuszona zmiana hasła początkowego** (`authentication/initial-password-change.html`) to
  wyśrodkowana karta na wspólnej powierzchni `.surface`, **bez wyspy, bez menu bocznego i bez
  jakiejkolwiek innej akcji**: nagłówek z ikoną klucza, opis, opcjonalny komunikat błędu z `role="alert"` oraz formularz
  dwóch pól hasła — nowego i potwierdzenia — z przyciskiem wysłania.
- **Zmiana hasła na życzenie** (`authentication/change-password.html`) to ta sama karta osadzona pod
  wyspą akcji, w zwykłym obszarze treści, uzupełniona o pole aktualnego hasła; ekran ma też menu
  boczne.
- Oba ekrany hasła osadzają komponent wspólny `common/password-rules.html` z wymaganiami
  egzekwowanymi przez serwer — długość 12–128 znaków, cyfra, mała litera, wielka litera, znak
  specjalny, różnica względem aktualnego hasła i zgodność obu pól — powiązany z polem nowego hasła
  przez `aria-describedby`. Pola mają rozmiar tekstu 1 rem, aby przeglądarki mobilne nie przybliżały
  widoku po ustawieniu fokusu.
- **Logowanie** korzysta ze strony generowanej przez Spring Security — nie jest ani stylowane, ani
  tłumaczone, więc pozostaje w języku angielskim.

Żaden szablon nie zawiera tekstu wpisanego na stałe: teksty, tytuły stron oraz atrybuty `aria-label`
i `title` pochodzą z tłumaczeń opisanych w [3.5](#35-tłumaczenia-interfejsu).

### 5.5 Menu boczne

Menu boczne jest komponentem wspólnym — plik `templates/common/side-menu.html` z fragmentem
`sideMenu`, osadzanym przez `th:replace="~{common/side-menu :: sideMenu}"` na stronie głównej i na
ekranie zmiany hasła na życzenie. Fragment dołącza własny moduł `/js/side-menu.js`, więc widok nie
musi o nim pamiętać; style leżą w `base.css`.

Widoczne zachowanie:

- Menu jest domyślnie ukryte. W pobliżu lewego dolnego rogu stale widoczny jest okrągły przycisk
  z obramowaniem i ikoną `icon-menu`, odsunięty od krawędzi o 2,75 rem; na telefonie w dowolnej
  orientacji odsunięcie maleje do 1,25 rem, aby niski widok zostawił wysokość na pozycje menu.
- Kliknięcie przycisku otwiera menu: pionowy prostokąt o lekko zaokrąglonych rogach, z teksturą
  `side-menu-background.jpg` i czarną ramą. Menu nie wysuwa się zza krawędzi ekranu — wyłania się
  w miejscu, narastając z przezroczystości, lekko pomniejszone i obniżone w stronę przycisku. Otwarcie
  i zamknięcie trwają po 280 ms; przy `prefers-reduced-motion` zmiana jest natychmiastowa.
- Przycisk jest głęboko wpisany w lewy dolny róg otwartego menu: róg leży 1,5 rem za środkiem
  przycisku, w stronę narożnika ekranu, więc większa część przycisku znajduje się w obrysie menu.
  Z menu wycięte jest koło nieco większe od przycisku, dzięki czemu między przyciskiem a menu zostaje
  szczelina w kształcie łuku, obwiedziona od strony menu wyraźnym pierścieniem w kolorze akcentu.
- Otwarte menu nie zmienia ikony przycisku — przycisk wygląda na wciśnięty: ma wklęsły cień i obramowanie
  oraz ikonę w kolorze akcentu.
- Menu zamyka ponowne kliknięcie przycisku, klawisz Escape albo wciśnięcie w dowolnym miejscu poza
  menu i przyciskiem. Zamknięcie zwija wszystkie gałęzie, a fokus z wnętrza menu wraca na przycisk.
- Pozycje są pogrupowane w sekcje. Sekcja nie ma własnej ramy — wyróżnia ją jedynie tytuł: przygaszony
  tekst na środku poziomej linii. Sekcje dzielą większe odstępy niż pozycje wewnątrz nich. Przykładowe
  sekcje to „Konfiguracja”, „Operacje” i „Kopie bezpieczeństwa”.
- W obrębie sekcji pozycje tworzą drzewo o co najwyżej trzech poziomach, działające jak akordeon:
  gałąź rozwija swoje dzieci w miejscu, z wcięciem, a rozwinięcie gałęzi zwija rozwinięte rodzeństwo
  z tej samej listy wraz z jego potomkami. Stan gałęzi pokazuje obracany chevron.
- Rozmiar menu jest ograniczony do widoku, więc strona nie przewija się także na telefonie w poziomie;
  gdy pozycje się nie mieszczą, przewija się lista wewnątrz menu.

![Diagram przepływu: otwieranie i zamykanie menu bocznego oraz rozwijanie gałęzi drzewa](diagramy/wspolne/img/przeplyw-menu-boczne.svg)

Źródło diagramu: [`diagramy/wspolne/przeplyw-menu-boczne.mmd`](diagramy/wspolne/przeplyw-menu-boczne.mmd)

Budowa komponentu:

| Element | Rola |
|---|---|
| `sideMenu` | Korzeń: przycisk `[data-side-menu-toggle]` z `aria-expanded` i `aria-controls`, panel `nav[data-side-menu-panel]` oraz moduł skryptu |
| `section.side-menu-section` | Sekcja: tytuł `sectionTitle( labelKey, titleId )`, który nazywa sekcję przez `aria-labelledby`, oraz lista pierwszego poziomu |
| `branchToggle( labelKey, iconId, childrenId )` | Pozycja rozwijająca gałąź; bezpośrednio po niej w tym samym elemencie listy leży opakowanie dzieci `.side-menu-children` o identyfikatorze `childrenId` |
| `leaf( labelKey, iconId )` | Pozycja otwierająca panel; `iconId` może być `null` |
| `side-menu.js` | Przełącza atrybuty `data-state`, `aria-expanded` i `inert`; animacje należą wyłącznie do arkusza stylów |

Drzewo jest zapisane bezpośrednio w znacznikach fragmentu — nie ma modelu w Javie. Dodanie panelu
polega na dodaniu elementu listy z wywołaniem `leaf(...)` w odpowiedniej sekcji lub gałęzi. Zamknięte menu
i zwinięte gałęzie mają atrybut `inert`, więc ich pozycje nie są osiągalne klawiaturą ani przez
technologie asystujące. Stan otwarcia nie jest zapamiętywany między stronami.

## 6. Bezpieczeństwo

Zaimplementowane mechanizmy przekrojowe:

- **Hasła.** Wyłącznie hashe adaptacyjne z `PasswordEncoderFactories.createDelegatingPasswordEncoder()`,
  czyli aktualna rekomendacja Spring Security wraz z identyfikatorem enkodera w zapisanej wartości.
  Ten sam enkoder obsługuje hasło początkowe i zmienione, więc zmiana rekomendacji nie wymaga migracji.
- **Brak wycieku sekretów.** Hasła jawne nie są logowane ani zwracane w `toString()`. Serwis ustawień
  loguje tylko nazwy kluczy. Logi zmiany hasła nie zawierają nazwy konta ani wartości.
- **Domyślne poświadczenia.** Konto `admin`/`admin` jest bezużyteczne poza formularzem wymuszonej
  zmiany hasła — ograniczenie jest egzekwowane po stronie serwera przy każdym żądaniu, a nie tylko
  po zalogowaniu.
- **Dostęp.** Wszystkie żądania poza `/initial-password-change` wymagają roli `ADMIN`. Adres
  `/initial-password-change` wymaga uprawnienia `PASSWORD_CHANGE_REQUIRED`, ponieważ zmienia hasło
  bez weryfikacji aktualnego; konto, które wybrało już własne hasło, dostaje na nim `403`, a serwis
  odrzuca takie żądanie również wtedy, gdy trafi do niego z pominięciem łańcucha. Konto z hasłem
  początkowym poza tym adresem osiąga tylko katalogi zasobów statycznych `/css/`, `/icons/`
  i `/img/`; wyjątek jest dopasowywany po początku ścieżki i nie zdejmuje wymagania roli.
- **Ekspozycja sieciowa.** Panel nasłuchuje wyłącznie na `127.0.0.1`.
- **Granice systemu plików.** Każda skonfigurowana ścieżka przechodzi przez `PathService` i nie może
  opuścić katalogu instalacyjnego — także przez dowiązania i junction (patrz [3.3](#33-rozwiązywanie-ścieżek)).
- **Trwałość ustawień.** Zapis pliku ustawień jest atomowy i wymuszony na urządzenie, więc utrata
  zasilania nie zostawia pustego pliku konfiguracji.

## 7. Persystencja

Bazą jest jednoplikowy SQLite (`vdeck.db`) obsługiwany przez Spring Data JPA z dialektem
`org.hibernate.community.dialect.SQLiteDialect`. Schemat jest utrzymywany przez `ddl-auto: update`.

Obecnie utrwalana jest jedna encja — `PanelUser` w tabeli `panel_users`. Repozytorium `UserRepository`
rozszerza `JpaRepository< PanelUser, String >` i nie deklaruje własnych metod. Operacje zmieniające
stan (`DataInitializer.run`, `PanelUserServiceImpl.changePassword` oraz
`PanelUserServiceImpl.changeInitialPassword`) są `@Transactional`; zmiana hasła zapisuje się przez
dirty checking zarządzanej encji, bez jawnego `save`.

## 8. Logowanie zdarzeń

Log4j2 jest jedynym API i backendem logowania; `spring-boot-starter-logging` jest wykluczony ze
wszystkich starterów, które go wciągają. Klasy produkcyjne używają Lombokowego `@Log4j2`.

Zdarzenia rejestrowane obecnie:

| Poziom | Zdarzenie |
|---|---|
| `INFO` | Rozwiązanie katalogu bazowego; utworzenie pliku ustawień z domyślnymi wartościami; wczytanie ustawień; zmiana ustawienia; utworzenie domyślnego administratora; zmiana hasła na życzenie użytkownika; zmiana hasła początkowego wraz ze zdjęciem ograniczenia pierwszego logowania |
| `WARN` | Przywrócenie domyślnej wartości brakującego ustawienia; nieudane usunięcie pliku tymczasowego |
| `DEBUG` | Źródło katalogu bazowego (override albo `ApplicationHome`); pominięcie inicjalizacji istniejącego administratora; odrzucenie zmiany hasła wraz z `PasswordChangeStatus`, który o tym zdecydował |

Żaden komunikat nie zawiera hasła, hasha ani wartości ustawień. Wpis o odrzuconej zmianie hasła
zawiera wyłącznie nazwę statusu, a nie wprowadzone wartości ani nazwę konta.

## 9. Stan implementacji i znane ograniczenia

Zaimplementowane: infrastruktura konfiguracji (`shared`), rozwiązywanie ścieżek, uwierzytelnianie
panelu wraz z oboma wariantami zmiany hasła, teksty interfejsu w resource bundle oraz warstwa
interfejsu — arkusze stylów, sprite ikon, komponenty wspólne wyspy akcji, wymagań dla hasła i menu
bocznego, strona główna panelu oraz oba ostylowane ekrany hasła.

**Niezaimplementowane** — wymienione wyłącznie jako kontekst, żaden z tych obszarów nie działa:
sterowanie cyklem życia serwera Valheim, moduł `vdeck-runtime` (nie ma katalogu `src`), profile
uruchomieniowe, światy i kopie bezpieczeństwa, podgląd logów, listy graczy, RCON, mody, harmonogramy
i aktualizacje serwera.

Znane ograniczenia stanu bieżącego:

- panel nie ma własnej strony logowania — używana jest strona generowana przez Spring Security;
- strona główna jest pusta poza wyspą akcji: pokazuje tło i wyspę, ale nie prezentuje jeszcze żadnych
  danych ani stanu serwera;
- przyciski sterowania serwerem i kopii bezpieczeństwa na wyspie są elementami interfejsu bez
  akcji — odpowiadające funkcjonalności nie są zaimplementowane;
- menu boczne zawiera wyłącznie przykładowe sekcje i pozycje (numerowane sekcje, grupy i panele)
  pokazujące podział na sekcje i trzy poziomy drzewa; żadna pozycja nie otwiera jeszcze panelu;
- panel jest jednojęzyczny i nie ma mechanizmu zmiany języka; zmiana brzmienia tekstu wymaga nowego
  wydania, a strona logowania generowana przez Spring Security pozostaje angielska niezależnie od
  tekstów panelu;
- rejestracja i zarządzanie kontami nie istnieją: jedynym kontem jest `admin` tworzony przy starcie,
  a `PanelRole` ma jedną wartość;
- brak mechanizmu odzyskiwania dostępu po utracie hasła — odzyskanie wymaga ingerencji w plik bazy;
- ścieżka bazy `jdbc:sqlite:vdeck.db` z `application.yaml` jest względna wobec katalogu roboczego
  procesu i **nie** przechodzi przez `PathService`;
- plik ustawień jest czytany wyłącznie przy starcie, więc ręczna edycja wymaga restartu panelu;
- zapis pliku ustawień gubi komentarze i kolejność kluczy;
- brak testów ArchUnit egzekwujących opisane granice pakietów.

## 10. Indeks diagramów

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
| Zmiana hasła w obu wariantach | przepływ | [`img/przeplyw-zmiana-hasla.svg`](diagramy/authentication/img/przeplyw-zmiana-hasla.svg) | [`diagramy/authentication/przeplyw-zmiana-hasla.mmd`](diagramy/authentication/przeplyw-zmiana-hasla.mmd) | [4.5](#45-zmiana-hasła) |
| Feature authentication | klasy | [`img/klasy-komponenty.svg`](diagramy/authentication/img/klasy-komponenty.svg) | [`diagramy/authentication/klasy-komponenty.mmd`](diagramy/authentication/klasy-komponenty.mmd) | [4.7](#47-komponenty-featurea) |
| Menu boczne | przepływ | [`img/przeplyw-menu-boczne.svg`](diagramy/wspolne/img/przeplyw-menu-boczne.svg) | [`diagramy/wspolne/przeplyw-menu-boczne.mmd`](diagramy/wspolne/przeplyw-menu-boczne.mmd) | [5.5](#55-menu-boczne) |
