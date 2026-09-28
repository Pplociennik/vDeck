# vDeck — główne założenia projektowe

> Data: 2026-08-17 · Aktualizacja: 2026-09-29 · Status: żywy opis uzgodnionej architektury

vDeck jest lokalnym panelem webowym do zarządzania dedykowanym serwerem Valheim. Aplikacja jest
dystrybuowana razem z instalacją serwera, uruchamiana lokalnie, a następnie obsługiwana z poziomu
przeglądarki.

Dokument opisuje decyzje architektoniczne, ograniczenia, oczekiwane zachowanie oraz uzgodnione
mechanizmy techniczne. Nie jest roadmapą: nie określa kolejności prac ani list zadań implementacyjnych.
Plan dla każdego obszaru funkcjonalnego powstaje osobno bezpośrednio przed jego realizacją.

Plik [`architectural_decisions.md`](architectural_decisions.md) jest zarezerwowany jako nadrzędny
rejestr krytycznych decyzji. Po zapisaniu w nim decyzji ten dokument będzie je rozwijał i nie będzie
mógł być interpretowany w sposób z nimi sprzeczny.

---

## 1. Zakres produktu

Panel ma docelowo obejmować:

- uruchamianie, zatrzymywanie i monitorowanie serwera;
- konfigurację profilu uruchomieniowego;
- zarządzanie światami i kopiami bezpieczeństwa;
- podgląd logów i stanu serwera;
- zarządzanie listami graczy;
- opcjonalną obsługę RCON i modów;
- harmonogramy oraz aktualizacje serwera.

Na obecnym etapie wspierany jest wyłącznie Windows. Obsługa Linuksa może zostać dodana później bez
zmiany logiki warstwy webowej i zasad cyklu życia serwera.

---

## 2. Architektura modułowa

vDeck jest monolitem modularnym. Modularność obowiązuje na dwóch poziomach:

1. moduły Maven tworzą kompilacyjne granice między niezależnym runtime a aplikacją Spring Boot;
2. pionowe moduły funkcjonalne w `vdeck-app` grupują web, logikę aplikacyjną i persystencję według
   funkcji, a nie według warstw technicznych.

Przyjęty styl można precyzyjniej nazwać **modularnym monolitem z pionowymi feature slices i lekkim
warstwowaniem wewnątrz feature'a**. Nie jest to pełna architektura heksagonalna ani Clean Architecture:
model feature'a może używać adnotacji JPA, a repozytoria Spring Data są jego bezpośrednią granicą
persystencji. Interfejsy serwisów wyznaczają kontrakty dla konsumentów bez wprowadzania osobnych
portów i adapterów dla każdej operacji.

Początkowo projekt składa się z dwóch modułów Maven pod wspólnym parentem:

| Moduł | Odpowiedzialność |
|---|---|
| `vdeck-runtime` | Czysta Java: cykl życia serwera, procesy, sygnały, logi, snapshoty i granice zależne od platformy. Bez Springa. |
| `vdeck-app` | Aplikacja Spring Boot: web, bezpieczeństwo, konfiguracja, persystencja oraz integracja z runtime. |

Kierunek zależności Maven jest jednostronny: `vdeck-app` zależy od `vdeck-runtime`; runtime nigdy nie
zależy od aplikacji ani od Springa. Aplikacja korzysta z fasady `runtime.api`, a szczegóły cyklu życia
i implementacje Windows pozostają wewnętrzne dla runtime.

W `vdeck-app` obowiązuje package-by-feature. Wszystkie pionowe funkcjonalności znajdują się pod
wspólnym pakietem `com.github.pplociennik.vdeck.feature`. Kod webowy, logika aplikacyjna i
persystencja danej funkcji pozostają w jej podpakiecie. Jeden feature nie sięga bezpośrednio do
repozytorium ani encji innego feature’a — komunikacja przechodzi przez jego publiczny serwis/fasadę
lub zdarzenie. Nie tworzymy osobnych modułów Maven dla warstw technicznych.

Szablony Thymeleaf i zasoby webowe należą do `vdeck-app`. Granice z systemem operacyjnym oraz
opcjonalnymi zdolnościami, takimi jak RCON, są ukryte za interfejsami runtime.

Pakiet `shared` nie zawiera logiki biznesowej i nie może stać się domyślnym miejscem dla kodu bez
właściciela. Reguły zależności między modułami i feature’ami są egzekwowane testami ArchUnit.

Nowy moduł Maven powstaje dopiero wtedy, gdy wydzielenie daje rzeczywistą izolację zmiennego lub
trudnego do testowania kodu. Przy dodaniu drugiej platformy implementacja Windows zostanie wydzielona
z `vdeck-runtime` do modułu platformowego, równoległego do implementacji linuksowej; publiczna fasada
i orkiestracja runtime pozostaną bez zmian.

---

## 3. Docelowa struktura modułów, pakietów i głównych klas

Drzewo opisuje organizację odpowiedzialności, a nie kolejność implementacji. Nazwy klas są robocze
i mogą zostać doprecyzowane podczas planowania danego feature’a, ale kierunek zależności oraz granice
modułów i pakietów pozostają obowiązującym założeniem.

Poddrzewa `vdeck-runtime` i `feature.server` pokazują stan zaimplementowany. Pozostałe planowane
obszary runtime, w tym snapshoty, RCON i łagodne zatrzymanie, opisują dalsze sekcje dokumentu.

```text
vdeck/                                           ← parent i agregator Maven
├── pom.xml
├── .docs/
│   ├── architectural_decisions.md
│   ├── design.md
│   ├── ui.md
│   └── rules/
│       ├── README.md
│       ├── clean-code.md
│       ├── documentation.md
│       ├── formating.md
│       ├── integration-tests.md
│       ├── maven.md
│       ├── security.md
│       └── unit-tests.md
│
├── dokumentacja/                                ← dokumentacja projektowa systemu
│   ├── dokumentacja-projektowa.md
│   └── diagramy/                                ← źródła diagramów Mermaid (.mmd)
│       ├── <feature>/                           ← jeden katalog = jeden feature
│       │   └── img/                             ← obrazki .svg osadzane w dokumentacji
│       └── wspolne/                             ← diagramy przekrojowe i infrastruktury
│           └── img/
│
├── vdeck-runtime/                               ← czysta Java, bez Springa
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/github/pplociennik/vdeck/runtime/
│       │   ├── api/                             ← publiczna fasada runtime
│       │   │   ├── ServerRuntime
│       │   │   ├── ServerState
│       │   │   ├── ServerStatus
│       │   │   ├── RunningServer
│       │   │   └── LaunchSpec
│       │   ├── spi/                             ← wymienne granice zewnętrzne
│       │   │   └── GameProcessLauncher
│       │   ├── lifecycle/                       ← serializowane zmiany stanu
│       │   │   └── ServerStateMachine
│       │   └── windows/                         ← adapter Windows; moduł dopiero przy 2. OS
│       │       ├── Win32
│       │       ├── WindowsCommandLine
│       │       └── WindowsProcessLauncher
│       └── test/java/…                          ← testy runtime bez Springa i Valheim
│
└── vdeck-app/                                   ← aplikacja Spring Boot
    ├── pom.xml
    └── src/
        ├── main/java/com/github/pplociennik/vdeck/
        │   ├── VdeckApplication.java
        │   ├── config/                          ← konfiguracja i inicjalizacja aplikacji
        │   │   ├── DataInitializer
        │   │   ├── PathConfiguration
        │   │   ├── SecurityConfiguration
        │   │   ├── ServerRuntimeConfiguration
        │   │   └── SystemPropertiesInitializer
        │   ├── feature/                         ← pionowe funkcjonalności aplikacji
        │   │   ├── authentication/              ← konta panelu i zmiana hasła
        │   │   │   ├── PasswordChangeController
        │   │   │   ├── InitialPasswordChangeController
        │   │   │   ├── PasswordChangeExceptionHandler ← @ControllerAdvice obu ekranów hasła
        │   │   │   ├── PasswordChangeMessage
        │   │   │   ├── domain/
        │   │   │   │   ├── PanelUser
        │   │   │   │   └── PanelRole
        │   │   │   ├── dto/
        │   │   │   │   ├── PasswordChangeRequest
        │   │   │   │   └── InitialPasswordChangeRequest
        │   │   │   ├── exc/
        │   │   │   │   └── PasswordChangeException
        │   │   │   ├── persistence/
        │   │   │   │   └── UserRepository
        │   │   │   └── service/
        │   │   │       ├── PanelUserService
        │   │   │       └── impl/
        │   │   │           └── PanelUserServiceImpl
        │   │   ├── home/                        ← strona główna panelu
        │   │   │   └── HomeController
        │   │   ├── server/                      ← sterowanie i prezentacja stanu runtime
        │   │   │   ├── ServerController
        │   │   │   ├── ServerExceptionHandler
        │   │   │   ├── ServerStatusAdvice
        │   │   │   ├── ServerMessage
        │   │   │   ├── exc/ServerStartException
        │   │   │   └── service/
        │   │   │       ├── ServerService
        │   │   │       ├── LaunchCommandService
        │   │   │       ├── ServerLogService
        │   │   │       └── impl/
        │   │   │           ├── ServerServiceImpl
        │   │   │           ├── LaunchCommandServiceImpl
        │   │   │           ├── ServerLogServiceImpl
        │   │   │           └── ServerStartValidator
        │   │   ├── launch/                      ← profil uruchomieniowy (plik, AD-013)
        │   │   │   ├── ServerSettingsController ← ekran „Serwer”
        │   │   │   ├── GameSettingsController   ← ekran „Ustawienia gry”
        │   │   │   ├── LaunchProfileExceptionHandler ← @ControllerAdvice obu ekranów
        │   │   │   ├── LaunchProfileMessage
        │   │   │   ├── domain/
        │   │   │   │   ├── LaunchProperty
        │   │   │   │   ├── LaunchProfile
        │   │   │   │   ├── LaunchConfiguration
        │   │   │   │   ├── WorldPreset
        │   │   │   │   ├── WorldModifier
        │   │   │   │   └── WorldKey
        │   │   │   ├── dto/
        │   │   │   │   ├── ServerSettingsForm
        │   │   │   │   └── GameSettingsForm
        │   │   │   ├── exc/
        │   │   │   │   └── LaunchProfileUpdateException
        │   │   │   └── service/
        │   │   │       ├── LaunchProfileService
        │   │   │       ├── GameSettingsDisplayService
        │   │   │       └── impl/
        │   │   │           ├── LaunchProfileServiceImpl
        │   │   │           └── GameSettingsDisplayServiceImpl
        │   │   ├── world/                       ← światy, wybór, import i eksport
        │   │   │   ├── WorldController
        │   │   │   ├── domain/
        │   │   │   │   └── World
        │   │   │   ├── persistence/
        │   │   │   └── service/
        │   │   │       ├── WorldService
        │   │   │       └── impl/
        │   │   │           └── WorldServiceImpl
        │   │   ├── backup/                      ← backup, restore i retencja
        │   │   │   ├── BackupController
        │   │   │   ├── domain/
        │   │   │   │   └── Backup
        │   │   │   ├── persistence/
        │   │   │   └── service/
        │   │   │       ├── BackupService
        │   │   │       └── impl/
        │   │   │           └── BackupServiceImpl
        │   │   ├── players/                     ← gracze i listy dostępu
        │   │   │   ├── PlayerController
        │   │   │   ├── domain/
        │   │   │   │   └── PlayerSession
        │   │   │   ├── persistence/
        │   │   │   └── service/
        │   │   │       ├── PlayerService
        │   │   │       └── impl/
        │   │   │           └── PlayerServiceImpl
        │   │   ├── logs/                        ← SSE i bufor logów dla UI
        │   │   │   ├── LogStreamController
        │   │   │   └── service/
        │   │   │       ├── LogService
        │   │   │       └── impl/
        │   │   │           └── LogServiceImpl
        │   │   └── mods/                        ← BepInEx i RCON
        │   │       ├── ModController
        │   │       └── service/
        │   │           ├── ModService
        │   │           └── impl/
        │   │               └── ModServiceImpl
        │   ├── security/                        ← przekrojowa infrastruktura Spring Security
        │   │   ├── PasswordChangeRequiredFilter
        │   │   └── PasswordChangeAuthenticationSuccessHandler
        │   ├── persistence/                    ← wspólna konfiguracja JPA i SQLite
        │   └── shared/                          ← wyłącznie elementy faktycznie współdzielone
        │       ├── PropertyReader               ← statyczny odczyt wartości typowanych
        │       ├── SystemPropertyIf
        │       ├── SystemPropertyValues
        │       ├── VdeckProperty
        │       ├── exc/
        │       ├── web/
        │       │   └── DefaultExceptionHandler  ← domyślna obsługa wyjątków (AD-014)
        │       └── service/
        │           ├── PathService
        │           ├── SystemPropertiesService
        │           └── impl/
        │               ├── PathServiceImpl
        │               ├── PropertiesFile
        │               └── SystemPropertiesServiceImpl
        ├── main/resources/
        │   ├── application.yaml
        │   ├── messages.properties              ← polski zestaw tekstów interfejsu
        │   ├── templates/
        │   │   ├── common/                      ← reużywalne komponenty: island, password-rules, side-menu
        │   │   ├── home/
        │   │   │   └── index.html
        │   │   ├── launch/
        │   │   │   ├── server-settings.html
        │   │   │   └── game-settings.html
        │   │   └── authentication/
        │   │       ├── change-password.html
        │   │       └── initial-password-change.html
        │   └── static/
        │       ├── css/                         ← variables.css + arkusze
        │       ├── js/                          ← moduły ES: side-menu.js, game-settings.js
        │       ├── icons/                       ← icons.svg (jeden sprite)
        │       └── img/                         ← tekstury interfejsu
        └── test/java/…                          ← testy funkcji i granic ArchUnit
```

`vdeck-app` zależy od publicznego API `vdeck-runtime`; kod webowy nie odwołuje się bezpośrednio do
pakietów `windows` ani wewnętrznej orkiestracji runtime. Pakiet `feature` jest wspólnym korzeniem
funkcjonalności `launch`, `world`, `backup`, `players`, `logs`, `mods`, `authentication` i `server`.
Każdy bezpośredni podpakiet `feature` jest granicą domenową oraz właścicielem swojego kodu. Główne
pakiety `security`, `config`, `persistence` i `shared` nie są feature'ami: zawierają przekrojową
infrastrukturę aplikacji. Wewnątrz feature’a domyślnie pozostaje płaska, pragmatyczna struktura. Gdy
feature ma jawny model domenowy, trafia on do `domain`, DTO granicy webowej do `dto`, a repozytoria do
`persistence`. Interfejsy serwisów należą do `service`, a implementacje do `service.impl`; konsumenci
zależą wyłącznie od interfejsów.

Domyślnie stosujemy najmniejszą możliwą widoczność. Element używany wyłącznie we własnej klasie jest
`private`, a element współdzielony tylko wewnątrz pakietu jest package-private. `public` stosujemy,
gdy kontrakt rzeczywiście przekracza granicę pakietu albo wymaga go język lub framework. Samo
wykrywanie komponentu przez Spring nie jest powodem nadania klasie lub konstruktorowi widoczności
`public`.

Dalsze podpakiety powstają dopiero po pojawieniu się rzeczywistej złożoności. Nie narzucamy każdemu
feature’owi osobnych portów, adapterów, modeli transportowych, mapperów ani klas use case. Granice
między feature’ami nadal są pilnowane przez ArchUnit i komunikację przez serwis właściciela danych.

Główny pakiet `config` jest composition rootem Springa oraz właścicielem przekrojowej inicjalizacji
danych podczas startu aplikacji. Wszystkie klasy `@Configuration`, w tym
`SecurityConfiguration` i `PathConfiguration`, znajdują się właśnie w `config`. `DataInitializer` i
`SystemPropertiesInitializer` również pozostają w `config`, nawet gdy korzystają z repozytorium albo
typów feature'a. Konfigurowane filtry i handlery nadal należą do głównego `security`, a
implementacje serwisów do `service.impl` swojego właściciela. Mechanizm właściwości systemowych,
podobnie jak rozwiązywanie ścieżek względem katalogu instalacji, należy do `shared`; jego serwisy są
udostępniane przez interfejsy w `shared.service` i nie są powielane w feature’ach.

Własne typy wyjątków umieszczamy w podpakiecie `exc` pakietu, który jest ich właścicielem, np.
`shared/exc`. Nie tworzymy jednego globalnego pakietu wyjątków dla całej aplikacji, ponieważ
przecinałby granice feature’ów.

Obsługę wyjątków warstwy webowej określa `AD-014`. Każdy główny proces feature'a kończy się przy
odrzuceniu i przy niepowodzeniu jednym „dużym” wyjątkiem procesu z `exc` tego feature'a, niosącym
klucz komunikatu oraz — przy niepowodzeniu — przyczynę. Serwisy nie zwracają obiektów wyniku, a
kontrolery nie przechwytują wyjątków. Wyjątek procesu obsługuje `@ControllerAdvice` w korzeniu
feature'a, ograniczony do jego kontrolerów i mający pierwszeństwo; przekierowuje na wysłany ekran z
komunikatem i formularzem bez sekretów w atrybutach flash. Pozostałe wyjątki obsługuje
`shared.web.DefaultExceptionHandler`: loguje każdy `RuntimeException` na `WARN` i odpowiada `500`.

---

## 4. Persystencja

Panel używa SQLite oraz Spring Data JPA. Funkcjonalne repozytoria są generycznymi interfejsami
`JpaRepository` lub `CrudRepository` i znajdują się w podpakiecie `persistence` właściwego feature’a.
Encja stanowiąca model feature'a znajduje się w jego podpakiecie `domain`, tak jak
`feature.authentication.domain.PanelUser`. Wspólny pakiet `persistence` zawiera jedynie konfigurację
JPA i SQLite.

Schemat bazy jest tworzony i aktualizowany przez Hibernate:

```yaml
spring:
  datasource:
    url: jdbc:sqlite:vdeck.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
```

Flyway nie jest używany. Dla SQLite wykorzystywany jest dialekt z
`org.hibernate.orm:hibernate-community-dialects`.

Granice pakietów są chronione testami architektonicznymi. Zależności od `jakarta.persistence` mogą
występować w encjach należących do `..domain..` oraz w technicznych podpakietach `..persistence..`.

---

## 5. Katalog instalacji i danych

W dystrybucji produkcyjnej `vdeck.jar` znajduje się w tym samym katalogu co `valheim_server.exe`.
Katalog instalacji jest ustalany przez
`new ApplicationHome(VdeckApplication.class).getDir().toPath().toRealPath()`, a nie na podstawie
bieżącego katalogu procesu. Jest on również jawnym katalogiem roboczym procesu Valheim. Uruchomienie
z IDE i testy mogą korzystać z nadpisania `vdeck.server-directory`.

Przed `SpringApplication.run()` wykonywany jest preflight. Sprawdza obecność zwykłego pliku
`valheim_server.exe`, możliwość zapisu locka, tokenu PID, bazy i logów oraz poprawność wszystkich
skonfigurowanych ścieżek po ich normalizacji. Błąd kończy start przed utworzeniem kontekstu Springa
i pozostawia czytelny komunikat w konsoli.

Katalog danych Valheim jest zawsze wskazywany jawnie przez opcję `-savedir`. Dzięki temu panel nie
zależy od domyślnych ścieżek profilu użytkownika i jednoznacznie wie, gdzie znajdują się światy oraz
listy graczy. Katalog jest ustawieniem profilu uruchomieniowego (`server.save.directory`, domyślnie
`valheim-data`): ścieżką względną wewnątrz katalogu instalacji, innej niż sam katalog instalacji.

### Kanały konfiguracji

Konfiguracja vDeck jest rozdzielona według właściciela i nie miesza się w jednym pliku:

| Kanał | Właściciel | Zawartość | Edycja |
|---|---|---|---|
| `application.yaml` w jarze | Aplikacja | Infrastruktura Spring Boot: datasource, port, poziomy logowania, cykl życia kontekstu. | Tylko przez nowe wydanie. |
| `.vDeck/config/vDeck.properties` w katalogu instalacji | Użytkownik | Ustawienia aplikacyjne panelu, docelowo edytowalne z GUI. | Ręcznie oraz z panelu. |
| `.vDeck/config/valheim-server.properties` w katalogu instalacji | Użytkownik | Profil uruchomieniowy serwera Valheim (AD-013). | Ręcznie oraz z ekranów „Serwer” i „Ustawienia gry”. |

Zgodnie z `AD-010` wszystkie pliki ustawień leżą w podkatalogu `.vDeck/config` katalogu instalacji,
nigdy w `src/main/resources` ani bezpośrednio obok `vdeck.jar`. Ich lokalizację wyznacza wyłącznie
`PathService.getConfigurationFile`. Plik i brakujący katalog są tworzone z wartościami domyślnymi przy
pierwszym starcie. Brakujące klucze są uzupełniane przy kolejnym starcie, dzięki czemu właściwości
dodane w nowym wydaniu pojawiają się w istniejącej instalacji.

Zbiór właściwości pliku opisuje enum implementujący `SystemPropertyIf`; dostęp zapewnia
jeden `SystemPropertiesService` dla wszystkich plików (AD-013): każda operacja przyjmuje nazwę pliku
(`FILE_NAME` enuma), a plik musi zostać wcześniej wczytany przez `loadFile` z kompletem wartości
domyślnych. Wszystkie pliki wczytuje przy starcie `SystemPropertiesInitializer`, zanim serwer WWW
przyjmie pierwsze żądanie. Każda właściwość deklaruje, czy jest
obowiązkowa (`isMandatory()`): obowiązkowa ma niepustą wartość domyślną, która zastępuje także pustą
wartość w pliku; opcjonalna może być pusta — pusta wartość oznacza „nie używaj”, a przywracany jest
tylko brakujący klucz. `setValues` zapisuje wiele właściwości jednym atomowym zapisem: wszystkie albo
żadna. Zestaw wartości — domyślnych przy `loadFile` i zapisywanych przez `setValues` — przekazuje
tworzony osobno dla każdego wywołania obiekt `SystemPropertyValues`, nie mapa. Wartości typowane
(liczby, wartości logiczne, opcje z zamkniętej listy) odczytuje statyczne narzędzie
`shared.PropertyReader`, które przy wartości niepasującej do ustawienia zwraca wartość zastępczą
podaną przez wywołującego i loguje nazwę ustawienia na `WARN`. Plik jest kodowany w UTF-8, a zapis odbywa się przez plik tymczasowy i `ATOMIC_MOVE` — dlatego
zapis wykonany przez panel zachowuje wpisy nienależące do panelu, ale nie zachowuje komentarzy ani
kolejności kluczy wpisanych ręcznie.

Zapisy jednego pliku są serializowane. Zmienione wartości powstają w osobnym zestawie;
`PropertiesFile` publikuje cały zestaw w pamięci dopiero po udanym zapisie pliku. Błąd zapisu
pozostawia poprzedni zestaw bez zmian i nie wymaga odtwarzania wcześniejszych wartości.

Po załadowaniu plików `SystemPropertiesInitializer` wywołuje `LaunchProfileService.reload()`.
Serwis tworzy jedną niemutowalną `LaunchConfiguration`: typowany `LaunchProfile` oraz hasło
pochodzące z tej samej wersji ustawień. Kolejne odczyty zwracają gotowy obiekt. Zapis ustawień
serwera i gry oraz ponowne zbudowanie konfiguracji są serializowane przez serwis profilu;
nowa konfiguracja jest publikowana po udanym zapisie. Profile zmieniamy przez serwis właściciela,
aby zapis i odświeżenie konfiguracji pozostały jednym procesem. Widoki otrzymują wyłącznie
bezsekretny `LaunchProfile`; walidator startu i generator komendy otrzymują tę samą
`LaunchConfiguration`. `reload()` mapuje wartości już obecne w pamięci, nie czyta ponownie dysku.
Ręczna zmiana pliku wymaga restartu panelu. Błędny profil nie blokuje dostępu do ekranu jego edycji;
gotowość do startu, w tym pliki, katalogi i porty, jest sprawdzana dopiero przed startem serwera.

Teksty interfejsu nie są kanałem konfiguracji i nie leżą w katalogu instalacji. Zgodnie z `AD-009`
panel jest jednojęzyczny, a jego teksty pochodzą z pliku `resources/messages.properties` dołączonego
do aplikacji i czytanego przez domyślny `MessageSource` Spring Boota.

Pierwszą właściwością jest `game.log.directory` — względna ścieżka katalogu logów serwera gry,
domyślnie `.vDeck/log` (AD-016). Każda skonfigurowana ścieżka przechodzi przez `PathService`: musi być
względna, jest normalizowana i po rozwiązaniu do postaci rzeczywistej musi pozostać wewnątrz katalogu
instalacji. Ścieżka absolutna, wychodząca poza katalog instalacji albo prowadząca poza niego przez
dowiązanie lub punkt reparsowania jest odrzucana.

Druga właściwość, `panel.game.settings.advanced` (domyślnie `false`), zapamiętuje, czy ekran
„Ustawienia gry” pokazuje sekcję „Zaawansowane” (AD-012). To preferencja wyświetlania panelu, a nie
część profilu uruchomieniowego: jej zmiana nie wpływa na opcje przekazywane serwerowi.

`game.log.retention.days` (domyślnie `14`) określa retencję dziennych katalogów logów; przed startem
usuwane są katalogi starsze niż bieżąca data minus tę liczbę dni. `server.start.script` (domyślnie
`start_headless_server.bat`) wskazuje skrypt producenta czytany wyłącznie po `SteamAppId` (AD-015).
Nie jest kanałem wykonywania poleceń. Panel generuje własny skrypt lustrzany
`.vDeck/vDeck-server-start.bat` bez zapisanego hasła, ale nigdy go nie uruchamia.

Podstawowy układ danych:

```text
<ApplicationHome>/
├── valheim_server.exe
├── vdeck.jar
├── .vDeck/
│   ├── config/                        ← wszystkie pliki ustawień (AD-010)
│   │   ├── vDeck.properties
│   │   └── valheim-server.properties
│   ├── vDeck-server-start.bat          ← regenerowany skrypt lustrzany bez hasła
│   └── log/                           ← domyślny `game.log.directory`
│       └── 2026-09-29/
│           └── 2026-09-29_12-00-00.log ← osobny plik na start
├── valheim-data/                      ← katalog domyślny; zmienia go `server.save.directory`
├── vdeck.lock
├── server.pid
└── vdeck.db

<katalog danych Valheim>/
└── światy i listy graczy

<katalog snapshotów poza instalacją serwera>/
└── historia snapshotów wraz z metadanymi
```

Snapshoty są przechowywane poza instalacją Valheim, aby nie podlegały operacjom aktualizacji ani
weryfikacji plików serwera.

---

## 6. Zarządzanie procesem

Panel jest właścicielem zarządzanego procesu Valheim i przechowuje go jako `RunningServer` w runtime.
Serwer jest uruchamiany przez `CreateProcessW` wywoływane przez FFM z flagą `CREATE_NO_WINDOW`.
Oddzielna konsola procesu izoluje zdarzenia CTRL+C od panelu i terminala, z którego uruchomiono vDeck.

Zgodnie z AD-015 `CreateProcessW` otrzymuje argumenty z profilu, jawny katalog roboczy i własny blok
środowiska. `SteamAppId` jest odczytywany ze skryptu producenta; brak wpisu lub pliku oraz błąd odczytu
dają ostrzeżenie i wartość zastępczą `892970`. Hasło znajduje się wyłącznie w rzeczywistym argumencie
procesu; skrypt lustrzany używa `"%VDECK_SERVER_PASSWORD%"`. Błąd zapisu skryptu nie blokuje startu.
`LaunchCommandService.build()` tworzy bezpośrednio `LaunchSpec` z przechwyconej konfiguracji
i nowej ścieżki logu. `renderScript(launchSpec)` renderuje skrypt z tej samej specyfikacji,
bez ponownego czytania ustawień i bez pośredniego obiektu komendy.

Panel nie przekierowuje strumieni konsoli serwera. Plik logu wskazuje jawnie opcja
`-logFile <ścieżka>` przekazana w wierszu poleceń, kierująca zapis do
`<game.log.directory>/<rrrr-MM-dd>/<rrrr-MM-dd_HH-mm-ss>.log` (AD-016). Domyślny katalog to
`.vDeck/log`; przy istniejącym pliku dodawany jest przyrostek `-1`, `-2` itd. Ścieżka jest
rozwiązywana przez `PathService` i przekazywana jako absolutna. Właścicielem pliku jest proces
serwera, a panel jedynie go śledzi, dzięki czemu logowanie nie zależy od życia procesu panelu.

Launcher nie dziedziczy więc żadnych uchwytów: `stdout` i `stderr` procesu pozostają domyślne, a
`STARTUPINFO` nie ustawia `STARTF_USESTDHANDLES`. Uchwyty `PROCESS_INFORMATION` są zamykane
bezpośrednio po uruchomieniu procesu. Każdy start otrzymuje nową ścieżkę logu; przyszły `LogTailer`
wiąże śledzenie z konkretnym uruchomieniem zamiast zakładać nadpisywanie stałego pliku przy restarcie.

Łagodne zatrzymanie odbywa się przez osobny, krótkotrwały proces-helper wykonujący:

```text
FreeConsole → AttachConsole(pid) → SetConsoleCtrlHandler(NULL, TRUE)
            → GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)
```

Izoluje to procesowe ustawienia konsoli i obsługę CTRL+C od głównej JVM. Helper nie wymaga osobnego
artefaktu: ten sam `vdeck.jar` jest uruchamiany w trybie technicznym `--ctrl-sender <pid>`, który
kończy się bez tworzenia kontekstu Springa. Skrypt Python byłby możliwy technicznie, ale wymagałby
zewnętrznego interpretera i dlatego nie jest przyjętym wariantem dystrybucji.

Panel udostępnia również Kill realizowany przez `ProcessHandle.destroyForcibly()`. Użycie tej
możliwości podlega zasadom bezpieczeństwa danych opisanym niżej.

Zamknięcie panelu zatrzymuje zarządzany serwer przez asynchroniczne `SmartLifecycle.stop(Runnable)`,
nie przez własny shutdown hook. Koordynator wysyła polecenie do kolejki zdarzeń i wywołuje callback
Springa po zakończeniu operacji albo po przekroczeniu jej deadline’u. Ma jawną fazę zamykania i
zależność od serwisu zapisującego metadane, aby repozytoria i `DataSource` pozostały dostępne.

Globalny deadline zatrzymania wynosi domyślnie 5 minut, a konfiguracja vDeck ustawia
`spring.lifecycle.timeout-per-shutdown-phase` na 6 minut. Aktualizacja lub kontrolowany restart
panelu oznacza więc również restart serwera Valheim.

---

## 7. Jedyność instancji i odzyskiwanie

`vdeck.lock` używa `FileLock` i zapewnia, że w danym katalogu działa najwyżej jeden panel. System
operacyjny zwalnia blokadę po śmierci procesu, również gdy nie wykona się kontrolowane zamknięcie.

`server.pid` identyfikuje zarządzany serwer za pomocą PID, czasu startu i ścieżki do pliku
wykonywalnego. Sam PID nie jest wystarczający, ponieważ Windows może wykorzystać go ponownie.

Po zdobyciu locka panel sprawdza token oraz skanuje `ProcessHandle.allProcesses()`. Kandydatem jest
proces, którego `info().command()` po normalizacji wskazuje dokładnie `valheim_server.exe` z bieżącego
`ApplicationHome`. Tam, gdzie system udostępnia dane, panel porównuje również użytkownika i
`startInstant`, a wyniki skanowania łączy z tokenem po PID i czasie startu. Jeżeli znajdzie kandydatów:

- blokuje uruchomienie kolejnego serwera;
- pokazuje użytkownikowi pełną listę znalezionych procesów;
- pozwala podjąć osobną decyzję dla każdego procesu;
- nie wykonuje automatycznej adopcji ani zatrzymania procesu, którego tożsamości nie potrafi
  wystarczająco potwierdzić.

Dostępne decyzje to adopcja, pozostawienie procesu bez zarządzania, łagodne zatrzymanie albo Kill
z dodatkowym potwierdzeniem. vDeck zarządza najwyżej jednym serwerem. Pozostawienie aktywnego procesu
z tej samej instalacji bez zarządzania nadal blokuje uruchomienie nowej instancji.

---

## 8. Stan i współbieżność

Podstawowa ścieżka stanów to:

```text
STOPPED → STARTING → RUNNING → STOPPING → STOPPED
```

Stany błędów to `START_FAILED`, `CRASHED` i `STOP_FAILED`. `STOP_FAILED` oznacza, że wyczerpano
łagodne zatrzymanie, ale proces nadal działa, ponieważ Kill nie był dozwolony albo również zawiódł.
`RECOVERY_REQUIRED` oznacza konieczność rozstrzygnięcia procesów znalezionych po starcie panelu.

Wszystkie zdarzenia zmieniające stan — HTTP, harmonogram, parser logów, `onExit()` i zamykanie panelu
— trafiają do jednej kolejki zdarzeń. Tylko jej wątek zmienia stan i aktualne `RunningServer`.
Długie operacje I/O wykonują workery, a wynik wraca do kolejki z `operationId`. Wynik jest przyjmowany
wyłącznie wtedy, gdy dotyczy nadal aktywnej operacji; spóźnione callbacki są ignorowane.

`onExit()` podczas `STOPPING` prowadzi do `STOPPED`, podczas `STARTING` do `START_FAILED`, a podczas
`RUNNING` do `CRASHED`. Snapshot po crashu musi zakończyć się przed ewentualnym auto-restartem.
Sprzeczne polecenia są odrzucane jako „Operacja w toku”.

---

## 9. RCON

RCON jest opcjonalną zdolnością dostarczaną przez mod `Tristan/ValheimRcon`. Panel pozostaje w pełni
użyteczny bez moda. Instalację, konfigurację i włączenie RCON można wykonać z panelu, ale aktywacja
wymaga restartu Valheim.

RCON udostępnia między innymi `save`, `say`, `showMessage` i `players`, ale nie udostępnia
`shutdown`. Jest kanałem zapisu i komunikacji, nie mechanizmem kończenia procesu.

Runtime modeluje RCON jako opcjonalny `WorldPersistenceChannel`. Dostępność kanału jest wykrywana
dynamicznie, a brak lub awaria moda nie blokują podstawowego zarządzania serwerem.

Odpowiedź na `save` informuje o rozpoczęciu zapisu. Za potwierdzenie sukcesu uznajemy dopiero
odpowiadający tej operacji komunikat ukończenia w logu. Snapshot wykonany po takim potwierdzeniu ma
wyższy poziom zaufania niż kopia żywych plików bez wcześniejszego zapisu.

Potwierdzony zapis pozwala bezpiecznie wymusić zakończenie procesu pod względem spójności zapisanego
świata. Nadal mogą zostać utracone zmiany powstałe między zapisem a zakończeniem, dlatego przed
force kill panel — o ile RCON odpowiada — ponawia zapis i czeka na jego ukończenie.

---

## 10. Zatrzymanie i bezpieczeństwo danych

Preferowaną metodą zatrzymania jest CTRL+C, zgodnie z zalecanym sposobem zamykania serwera Valheim.
RCON, jeśli jest dostępny, służy wcześniej do zapisania świata. Force kill jest ostatecznością po
nieudanych próbach łagodnego zatrzymania.

Uzgodniona drabina zatrzymania:

```text
0. snapshot PRE_STOP                       ← BEST_EFFORT; brak snapshotu przerywa operację
1. RCON: say + save                        [30 s]
   odpowiedź „World save started”
   → potwierdzenie ukończenia w logu
   → snapshot POST_SAVE                    ← CONFIRMED_SAVE
2. CTRL_C przez helper → oczekiwanie       [120 s]
3. jednorazowe ponowienie CTRL_C           [30 s]
4. jeśli proces żyje i RCON odpowiada:
   ponowne save → potwierdzenie → snapshot FINAL_SAVE
5. force kill zgodnie z polityką bezpieczeństwa
```

Timeouty etapów mieszczą się we wspólnym pięciominutowym deadline i nie wydłużają go przez proste
sumowanie kolejnych retry. Przekroczenie deadline’u prowadzi do `STOP_FAILED`, a nie do automatycznej
zgody na Kill.

Snapshoty mają dwa poziomy zaufania:

| Poziom | Znaczenie |
|---|---|
| `BEST_EFFORT` | Kopia plików działającego serwera bez potwierdzonego zapisu. Walidacja strukturalna nie gwarantuje semantycznej spójności świata. |
| `CONFIRMED_SAVE` | Snapshot wykonany po potwierdzonym ukończeniu RCON `save`. |

Automatyczny force kill, np. z harmonogramu, wymaga aktualnego `CONFIRMED_SAVE`. Użytkownik może
ręcznie zaakceptować większe ryzyko i wykonać Kill mając jedynie `BEST_EFFORT`.

Snapshot jest zapisywany jako zestaw tymczasowy, następnie utrwalany przez `fsync` i publikowany przez
`ATOMIC_MOVE`. Metadane zawierają co najmniej powód, czas, poziom zaufania, rozmiary i sumy SHA-256.

Przy kopiowaniu żywego świata mtime i rozmiary całego zestawu `.db`/`.fwl`/`.old` są odczytywane
przed i po kopiowaniu. Zmiana dowolnego pliku powoduje odrzucenie i ponowienie całego zestawu, a nie
tylko pojedynczego pliku. Mechanizm ogranicza ryzyko niespójności, ale go nie eliminuje; walidacja
strukturalna nie dowodzi semantycznej poprawności świata i nie zastępuje historii wielu kopii.

Retencja nigdy nie usuwa ostatniego `CONFIRMED_SAVE`. Jeżeli taki snapshot jeszcze nie istnieje,
zachowuje ostatni strukturalnie poprawny `BEST_EFFORT`. Po crashu snapshot powstaje przed ewentualnym
auto-restartem, ponieważ ponowny start gry może nadpisać pliki `.old`.

---

## 11. Panel webowy

| Obszar | Ustalenie |
|---|---|
| Użytkownicy | Brak rejestracji. Przy pierwszym starcie `DataInitializer` tworzy brakujące konto `admin` z rolą `ADMIN`; istniejącego konta nie zmienia. |
| Logowanie | Domyślny panel logowania Spring Security. Po zwykłym logowaniu obowiązuje standardowe przekierowanie, a przy braku zapamiętanego żądania — strona główna panelu (`/`). |
| Hasło początkowe | `admin`, zapisane wyłącznie jako adaptacyjny hash i z wymuszoną zmianą przy pierwszym logowaniu. |
| Zmiana hasła | Dwa osobne ekrany. `/initial-password-change` obsługuje wymuszoną zmianę hasła początkowego: nie pyta o aktualne hasło, bo konto go nigdy nie wybierało, i wymaga uprawnienia `PASSWORD_CHANGE_REQUIRED`. `/change-password` obsługuje zmianę na życzenie, uruchamianą z wyspy akcji, i wymaga podania aktualnego hasła. Każdy ekran ma własne walidowane DTO; nowe hasło ma 12–128 znaków, a po zmianie użytkownik jest wylogowany. |
| Próby logowania | Na obecnym etapie brak blokady konta i trwałego licznika prób; koszt weryfikacji zapewnia adaptacyjny `PasswordEncoder`. |
| Sesje | Maksymalnie jedna aktywna sesja; nowsze logowanie unieważnia wcześniejszą sesję. |
| Widoki | Thymeleaf, fragmenty HTMX i natywne moduły ES bez obowiązkowego procesu budowania frontendu. |
| Nawigacja | Menu boczne jako wspólny fragment `common/side-menu.html`: domyślnie ukryte, otwierane okrągłym przyciskiem w lewym dolnym rogu, z pozycjami pogrupowanymi w tytułowane sekcje; w obrębie sekcji pozycje tworzą drzewo o co najwyżej trzech poziomach działające jak akordeon. Drzewo jest zapisane w znacznikach fragmentu, bez modelu w Javie. Menu nie jest osadzane na ekranie wymuszonej zmiany hasła początkowego. |
| Profil uruchomieniowy | Dwa ekrany otwierane z sekcji „Konfiguracja” menu bocznego: „Serwer” (`/server-settings` — tożsamość, dostęp, świat i zapis, kopie automatyczne) oraz „Ustawienia gry” (`/game-settings` — zestaw ustawień świata na górze, a modyfikatory i klucze świata w sekcji „Zaawansowane”, domyślnie ukrytej, z zapamiętywaną widocznością). Pola odpowiadają opcjom `valheim_server.exe` z instrukcji serwera dedykowanego, są pogrupowane w sekcje i każde ma opis z dozwolonymi wartościami. Każdy ekran zapisuje atomowo wyłącznie własne ustawienia; hasło serwera nigdy nie wraca do przeglądarki. Zmiany obowiązują od następnego uruchomienia serwera. |
| Logi | Aktualizowane w przeglądarce przez SSE. |
| Bezpieczeństwo żądań | Włączone CSRF; token jest dodawany do `hx-post` przez `htmx:configRequest`. |

Natywny JavaScript pozostaje bez build-stepu, dopóki jego rozmiar i zależności nie uzasadnią
wprowadzenia bundlera. Logi SSE korzystają z bufora pierścieniowego, aby nowy klient mógł otrzymać
ostatnie zdarzenia bez przechowywania nieograniczonej historii w pamięci.

---

## 12. Zweryfikowane założenia techniczne

Poniższe zachowania zostały potwierdzone w spike’u na Windows 11 i Java 25. Są uzasadnieniem decyzji,
nie planem implementacji:

| Sprawdzone zachowanie | Wynik |
|---|---|
| `Process.destroy()` | Twarde zakończenie; świat nie został zapisany. |
| `taskkill /PID` bez `/F` | Proces bez okna może zostać zakończony wyłącznie w sposób wymuszony. |
| `CREATE_NO_WINDOW` + helper CTRL+C | Czyste zamknięcie procesu testowego. |
| CTRL_BREAK zamiast CTRL+C | Proces przeżył, a zdarzenie zakończyło helper. |
| Adopcja procesu przez inną JVM | Możliwa; po adopcji zadziałało również łagodne zatrzymanie. |
| `ProcessHandle.info()` obcego procesu | Dostępne: `command()`, `startInstant()`, `user()`; niedostępne: `arguments()`, `commandLine()`. |
| `destroyForcibly()` procesu spoza `ProcessBuilder` | Proces został zakończony. |
| `ATOMIC_MOVE` z nadpisaniem na NTFS | Operacja zadziałała atomowo w środowisku spike’a. |

---

## 13. Otwarte ustalenia

- Potwierdzenie na rzeczywistym serwerze, że CTRL+C kończy zapis świata przed wyjściem procesu.
