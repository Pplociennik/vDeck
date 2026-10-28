# vDeck — główne założenia projektowe

> Data: 2026-08-17 · Aktualizacja: 2026-10-12 · Status: żywy opis uzgodnionej architektury

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

Projekt składa się z pięciu modułów kodu i modułu pakującego pod wspólnym parentem:

| Moduł | Odpowiedzialność |
|---|---|
| `vdeck-runtime` | Czysta Java: cykl życia serwera, procesy, sygnały, logi, snapshoty i granice zależne od platformy. Bez Springa. |
| `vdeck-runner` | Czysta Java: osobny wykonywalny punkt wejścia helpera CTRL+C (AD-020). |
| `vdeck-launcher` | Czysta Java: jedyny program uruchamiany przez skrypt startowy; blokady, sprawdzenie i przygotowanie aktualizacji, start panelu (AD-024, AD-026). |
| `vdeck-updater` | Czysta Java: dziennikowana podmiana plików, scalanie ustawień i recovery, uruchamiane z niezależnego executora (AD-024, AD-025). |
| `vdeck-app` | Aplikacja Spring Boot: web, bezpieczeństwo, konfiguracja, persystencja oraz integracja z runtime. |
| `.dist` (`vdeck-dist`) | Końcowy moduł `pom`: szablony instalacji, dołączone JRE i budowanie dystrybucji ZIP. |

`vdeck-app`, `vdeck-runner`, `vdeck-launcher` i `vdeck-updater` zależą od `vdeck-runtime`; nie zależą
od siebie. Launcher uruchamia updater i panel wyłącznie jako osobne procesy. Runtime nigdy nie
zależy od aplikacji, runnera, launchera, updatera ani od Springa. Aplikacja korzysta z fasady `runtime.api`, a szczegóły cyklu życia
i implementacje Windows pozostają wewnętrzne dla runtime.

Moduł dystrybucji zależy od czterech wykonywalnych artefaktów, aby Maven pakował je dopiero po
zakończeniu ich buildów, także przy pracy równoległej. Zależności te służą wyłącznie pakowaniu.

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
obszary runtime, w tym przyszłe RCON i automatyzację, opisują dalsze sekcje dokumentu.

```text
vdeck/                                           ← parent i agregator Maven
├── pom.xml
├── .dist/                                      ← moduł vdeck-dist i szablon dystrybucji
│   ├── pom.xml
│   ├── assembly.xml
│   ├── start-vDeck.bat
│   ├── README.md
│   ├── README.txt
│   ├── .vDeck/config/                          ← domyślne pliki properties, także vDeck-start.properties
│   ├── lib/                                    ← generowane JAR-y panelu, runnera, launchera i updatera
│   └── jre/                                   ← pobrane i zweryfikowane JRE 25
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
│   ├── diagramy/                                ← źródła diagramów Mermaid (.mmd)
│   │   ├── <feature>/                           ← jeden katalog = jeden feature
│   │   │   └── img/                             ← obrazki .svg osadzane w dokumentacji
│   │   └── wspolne/                             ← diagramy przekrojowe i infrastruktury
│   │       └── img/
│   └── instrukcja-uzytkownika/                  ← instrukcja obsługi (AsciiDoc → PDF w ZIP-ie)
│       ├── instrukcja-uzytkownika.adoc
│       ├── motyw/                               ← motyw PDF
│       ├── rysunki/                             ← źródła .mmd rysunków instrukcji
│       ├── img/                                 ← zrzuty ekranu i wygenerowane rysunki
│       └── zrzuty/                              ← skrypt Playwright odtwarzający zrzuty
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
│       │   │   ├── LaunchSpec
│       │   │   ├── ProcessIdentity
│       │   │   └── ControlSenderCommand
│       │   ├── spi/                             ← wymienne granice zewnętrzne
│       │   │   └── GameProcessLauncher
│       │   ├── lifecycle/                       ← serializowane zmiany stanu
│       │   │   └── ServerStateMachine
│       │   ├── installation/                    ← kontrakt startu i aktualizacji (AD-024)
│       │   │   ├── InstallationLock             ← vdeck.lock i bramka startup.lock
│       │   │   ├── InstallationOwnership
│       │   │   ├── HandoffProtocol              ← potwierdzane przekazanie blokady
│       │   │   ├── SuccessorStarter
│       │   │   ├── UpdateLayout                 ← stałe ścieżki protokołu recovery
│       │   │   ├── UpdateJournalHeader
│       │   │   ├── DistributionItem             ← zamknięta lista plików paczki
│       │   │   ├── DurableFiles
│       │   │   ├── RunLogFile / RunLogRetention
│       │   │   ├── ServerActivityCheck
│       │   │   ├── ReleaseVersion               ← MAJOR.MINOR.PATCH (launcher i panel)
│       │   │   ├── ConsoleMessageIf / ConsoleMessage ← klucze tekstów konsoli
│       │   │   ├── I18n                         ← teksty z console-messages.properties
│       │   │   └── UserConsole
│       │   └── windows/                         ← adapter Windows; moduł dopiero przy 2. OS
│       │       ├── Win32
│       │       ├── WindowsCommandLine
│       │       ├── WindowsConsoleControlSender
│       │       └── WindowsProcessLauncher
│       └── test/java/…                          ← testy runtime bez Springa i Valheim
│
├── vdeck-runner/                                ← wykonywalny helper, bez Springa
│   ├── pom.xml
│   └── src/main/java/com/github/pplociennik/vdeck/runner/
│       └── VdeckRunner
│
├── vdeck-launcher/                              ← lib/vdeck-launcher.jar, bez Springa
│   ├── pom.xml
│   └── src/main/java/com/github/pplociennik/vdeck/launcher/
│       ├── LauncherMain                         ← bootstrap bez loggera, log uruchomienia
│       ├── VdeckLauncher                        ← orkiestracja startu
│       ├── StartSettings                        ← vDeck-start.properties
│       ├── GitHubReleaseClient
│       ├── ReleaseDownloader
│       ├── PackageExtractor                     ← zip4j i kontrola wersji paczki
│       ├── UpdateExecutorPreparation
│       ├── UpdatePreparation                    ← sufit 60 minut (AD-027)
│       ├── UpdateCleanup
│       └── LauncherMessage                      ← klucze tekstów konsoli launchera
│
├── vdeck-updater/                               ← lib/vdeck-updater.jar, uruchamiany z executora
│   ├── pom.xml
│   └── src/main/java/com/github/pplociennik/vdeck/updater/
│       ├── UpdaterMain                          ← bootstrap bez loggera, log uruchomienia
│       ├── VdeckUpdater                         ← apply, --recover, przekazanie do launchera
│       ├── UpdatePlan
│       ├── UpdateJournal
│       ├── FileReplacer
│       ├── UpdateRollback
│       ├── TransactionFiles
│       ├── PropertiesMerger
│       └── UpdaterMessage                       ← klucze tekstów konsoli aktualizatora
│
└── vdeck-app/                                   ← aplikacja Spring Boot
    ├── pom.xml
    └── src/
        ├── main/java/com/github/pplociennik/vdeck/
        │   ├── VdeckApplication.java
        │   ├── config/                          ← konfiguracja i inicjalizacja aplikacji
        │   │   ├── ConsoleControlConfiguration
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
        │   │   │   └── HomeController           ← dodaje niepotwierdzone listy zmian
        │   │   ├── releasenotes/                ← panel „Co nowego w vDeck” (AD-029)
        │   │   │   ├── ReleaseNotesController   ← POST /release-notes/acknowledge
        │   │   │   ├── ReleaseNotesExceptionHandler
        │   │   │   ├── ReleaseNotesMessage
        │   │   │   ├── domain/
        │   │   │   │   ├── ReleaseNotes / ReleaseNotesSection / ReleaseNotesEntry
        │   │   │   │   ├── ReleaseNotesCategory ← Nowości, Zmiany, Poprawki
        │   │   │   │   └── ReleaseNotesAcknowledgement
        │   │   │   ├── exc/
        │   │   │   │   └── ReleaseNotesException
        │   │   │   ├── persistence/
        │   │   │   │   └── ReleaseNotesAcknowledgementRepository
        │   │   │   └── service/
        │   │   │       ├── ReleaseNotesService
        │   │   │       └── impl/
        │   │   │           ├── ReleaseNotesServiceImpl
        │   │   │           ├── ReleaseNotesReader ← classpath*:release-notes/*.md
        │   │   │           └── ReleaseNotesParser
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
        │   │   │           ├── ServerLogDirectories ← akcje na katalogach logów gry
        │   │   │           ├── ServerLogRetention   ← usuwanie starych katalogów dziennych
        │   │   │           └── ServerStartValidator
        │   │   ├── launch/                      ← profil uruchomieniowy (plik, AD-013)
        │   │   │   ├── ServerSettingsController ← cztery panele gałęzi „Serwer”
        │   │   │   ├── GameSettingsController   ← ekran „Ustawienia gry”
        │   │   │   ├── LaunchProfileExceptionHandler ← @ControllerAdvice obu kontrolerów
        │   │   │   ├── LaunchProfileMessage
        │   │   │   ├── domain/
        │   │   │   │   ├── LaunchProperty
        │   │   │   │   ├── LaunchProfile
        │   │   │   │   ├── LaunchConfiguration
        │   │   │   │   ├── WorldPreset
        │   │   │   │   ├── WorldModifier
        │   │   │   │   └── WorldKey
        │   │   │   ├── dto/
        │   │   │   │   ├── LaunchProfileForm    ← wspólny kontrakt formularzy paneli
        │   │   │   │   ├── ServerIdentityForm
        │   │   │   │   ├── ServerAccessForm
        │   │   │   │   ├── WorldSaveForm
        │   │   │   │   ├── ServerBackupsForm
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
        │   │   ├── logs/                        ← konsola: odpytywany log bieżącego uruchomienia
        │   │   │   ├── ConsoleController
        │   │   │   ├── dto/
        │   │   │   │   └── LogChunk
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
        │       ├── lock/
        │       │   └── InstallationLockService  ← przejęcie blokady od launchera przed Springiem
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
        │   ├── release-notes/                   ← <X.Y.Z>.md, lista zmian wersji (AD-029)
        │   ├── templates/
        │   │   ├── common/                      ← reużywalne komponenty: island, password-rules, side-menu
        │   │   ├── home/
        │   │   │   └── index.html
        │   │   ├── launch/
        │   │   │   ├── server-settings.html     ← wspólny widok paneli gałęzi „Serwer”
        │   │   │   ├── server-settings-panels.html ← pola każdego panelu jako fragmenty
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

Implementacja zatrzymania rozszerza powyższe poddrzewa o:

- runtime/api: `ProcessIdentity`, `ControlSenderCommand`, `WorldContext`, `StopRequest`, `StopFailure`;
- runtime/lifecycle: `ManagedLaunch`, `ManagedLaunchStore` i pakietowy `StopEnvironment`;
- runtime/spi: `ConsoleControlSender` oraz `WorldSnapshotStore`;
- runtime/snapshot: `FileWorldSnapshotStore`;
- runtime/windows: `JvmConsoleControlSender`, `WindowsConsoleControlSender`;
- runner: `VdeckRunner`;
- app/config: `ConsoleControlConfiguration`, `ServerShutdownLifecycle`;
- app/feature/backup: `service.BackupPathService` i `service.impl.BackupPathServiceImpl`;
- app/feature/server/exc: `ServerStopException`, `ServerKillException`;
- templates/server: `kill-confirmation.html`; static/js: `server-status.js`.

Konsola (feature `logs`, opisany w drzewie powyżej) dodaje szablon `templates/logs/console.html`,
`static/js/console.js` i `static/css/console.css`.

Panel „Co nowego w vDeck” (feature `releasenotes`, AD-029) jest osadzony w `templates/home/index.html`
i ma style w `static/css/home.css`. Zainstalowaną wersję panel czyta z `BuildProperties`, które
generuje cel `build-info` wtyczki `spring-boot-maven-plugin` w `vdeck-app/pom.xml`.


## 4. Persystencja

Panel używa SQLite oraz Spring Data JPA. Funkcjonalne repozytoria są generycznymi interfejsami
`JpaRepository` lub `CrudRepository` i znajdują się w podpakiecie `persistence` właściwego feature’a.
Encja stanowiąca model feature'a znajduje się w jego podpakiecie `domain`, tak jak
`feature.authentication.domain.PanelUser`. Wspólny pakiet `persistence` zawiera jedynie konfigurację
JPA i SQLite.

Schemat bazy tworzy i zmienia wyłącznie Liquibase (AD-028), uruchamiany automatycznie przez Spring
Boot przy starcie panelu, przed inicjalizacją JPA. Hibernate nie zarządza schematem:

```yaml
spring:
  datasource:
    url: jdbc:sqlite:vdeck.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: none
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    analytics-enabled: false
```

Changesety są w XML. `db.changelog-master.xml` dołącza w kolejności wydań po jednym pliku
`versions/<wersja>.xml` na wersję projektu; plik wydanej wersji jest niezmienny, a układ changesetów
opisuje `rules/clean-code.md` §13. Każdy changeset ma `preConditions` z `onFail="MARK_RAN"`, więc
obiekt istniejący już w bazie nie przerywa startu. Liquibase nie wysyła telemetrii (`analytics-enabled: false`).
Flyway nie jest używany. Dla SQLite wykorzystywany jest dialekt z
`org.hibernate.orm:hibernate-community-dialects`.

Tabela `release_notes_acknowledgements` (encja `ReleaseNotesAcknowledgement`, klucz `version`
`VARCHAR(32)`) przechowuje wersje, których listę zmian administrator zamknął (AD-029). Świeża baza
dostaje wpis zainstalowanej wersji od `DataInitializer`, razem z kontem `admin`.

Granice pakietów są chronione testami architektonicznymi. Zależności od `jakarta.persistence` mogą
występować w encjach należących do `..domain..` oraz w technicznych podpakietach `..persistence..`.

---

## 5. Katalog instalacji i danych

W dystrybucji produkcyjnej `start-vDeck.bat` znajduje się w tym samym katalogu co `valheim_server.exe`,
a JAR-y panelu, runnera, launchera i updatera w jego podkatalogu `lib`.
`mvnw clean package` buduje `target/vDeck-dist-${project.version}.zip`, zgodnie z AD-021.
ZIP nie ma katalogu nadrzędnego: jego całą zawartość wypakowuje się do instalacji Valheim.
Zawiera cztery JAR-y w `lib`, `start-vDeck.bat`, README.md i README.txt, instrukcję obsługi
`vDeck-instrukcja.pdf`, domyślne pliki w `.vDeck/config` oraz pełne Eclipse Temurin JRE 25 dla
Windows x64 w `jre`.

Instrukcja obsługi powstaje z `dokumentacja/instrukcja-uzytkownika/instrukcja-uzytkownika.adoc`
(AD-022). W fazie `prepare-package` modułu `.dist` Asciidoctor Maven Plugin z AsciidoctorJ PDF
generuje `.dist/target/instrukcja/vDeck-instrukcja.pdf`, wstawiając wersję z `pom.xml`. Ostrzeżenie
Asciidoctora, np. brakujący obrazek albo zły odnośnik, przerywa build. Zrzuty ekranu i rysunki są
wersjonowanymi plikami w `img/`. Zrzuty odtwarza skrypt Playwright `zrzuty/zrzuty-ekranu.mjs`,
uruchamiany ręcznie na świeżo wypakowanej paczce, a nie przez Maven.

Maven AntRun pobiera przypięte w `.dist/pom.xml` wydanie JRE, weryfikuje SHA-256 i odtwarza
katalog `jre`. Archiwum pobrania jest buforowane w `.dist/target` do następnego `clean`.
Maven Resources kopiuje `vdeck.jar`, `vdeck-runner.jar`, `vdeck-launcher.jar` i `vdeck-updater.jar`
z katalogów `target` modułów do `.dist/lib` bez filtrowania; następujący po nim Maven Assembly tworzy ZIP z jawnej listy plików.
Szablony są wersjonowane, a JAR-y, JRE i `node_modules` skryptu zrzutów ignorowane przez Git. `clean` usuwa wygenerowane pliki,
zachowując szablony. Zwykłe `test` nie pobiera JRE i nie buduje paczki.

BAT korzysta z `jre/bin/java.exe` niezależnie od `PATH` i `JAVA_HOME` i uruchamia wyłącznie launcher
(AD-026). Ustawia katalog roboczy na swój katalog i przekazuje jawne `-Dvdeck.server-directory` równe
katalogowi skryptu, bo `ApplicationHome` wskazałby `lib`. Jeżeli istnieje `.vDeck/update/apply.journal`,
zamiast launchera uruchamia `.vDeck/update/executor/jre/bin/java.exe -jar
.vDeck/update/executor/vdeck-updater.jar --recover` (stały kontrakt protokołu recovery 1). Wywołanie
Javy jest jednym blokiem nawiasowym zakończonym w tej samej linii `call exit /b %%^ERRORLEVEL%%`,
więc `cmd.exe` nie czyta pliku ponownie po jego podmianie, a kod wyjścia Javy wraca do wywołującego.
Argumenty skryptu trafiają po `--` do panelu. Brak Javy lub JAR-a daje czytelny błąd i nie uruchamia
systemowej Javy. Adres, port i automatyczne aktualizacje ustawia `.vDeck/config/vDeck-start.properties`
(`panel.address`, `panel.port`, `update.enabled`), czytany przez launcher. Launcher uruchamia panel
z `--enable-native-access=ALL-UNNAMED`, `-Dvdeck.server-directory`, `-Dvdeck.handoff=<id>`,
`-Dvdeck.open-browser=true`, `--server.address` i `--server.port` jako oddzielnymi argumentami procesu.
Gotowy panel otwiera wtedy swój adres w domyślnej przeglądarce, chyba że `panel.open.browser` w
`vDeck.properties` (domyślnie `true`) ma wartość `false`; uruchomienie z IDE i testy nie ustawiają
`vdeck.open-browser` i niczego nie otwierają.
Plik BAT ma końce linii CRLF i zawiera wyłącznie ASCII: `cmd.exe` błędnie parsuje wielobajtowe
znaki UTF-8, dlatego komentarze są angielskie, a polskie komunikaty nie mają znaków diakrytycznych.
Helper uruchamia `javaw.exe` z tego samego JRE przez `java.home`.

Katalog instalacji jest ustalany przez
`new ApplicationHome(VdeckApplication.class).getDir().toPath().toRealPath()`, a nie na podstawie
bieżącego katalogu procesu. Jest on również jawnym katalogiem roboczym procesu Valheim. Skrypt
dystrybucji, uruchomienie z IDE i testy korzystają z nadpisania `vdeck.server-directory`.

Wybrana instalacja jest kanonizowana przez `toRealPath()` w `PathService` i na wejściu runtime.
Spacje, polskie znaki i przekierowania powyżej instalacji nie są odrzucane. Kontrola katalogów tokenu
oraz świata obejmuje części od kanonicznej instalacji w dół. Odrzuca dowiązania, junctions,
`isOther()` i różnicę względem rzeczywistej ścieżki. Katalog backupów jest wyprowadzany z kanonicznej
instalacji jako `.vDeck/<snapshot.directory>`; jego resolver sprawdza `.vDeck` i niżej.

Przed `SpringApplication.run()` wykonywany jest preflight. Sprawdza obecność zwykłego pliku
`valheim_server.exe`, możliwość zapisu locka, tokenu PID, bazy i logów oraz poprawność wszystkich
skonfigurowanych ścieżek po ich normalizacji. Błąd kończy start przed utworzeniem kontekstu Springa
i pozostawia czytelny komunikat w konsoli. Zaimplementowane jest sprawdzenie `valheim_server.exe`
(`ServerExecutableCheck`, pierwszy krok `VdeckApplication.main()`, przed przejęciem blokady od
launchera, kod wyjścia 1). Jest domyślnie włączone, a wyłącza je właściwość systemowa
`-Dvdeck.server-file-check=false`, przeznaczona dla uruchomień deweloperskich i zrzutów ekranu.

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
| `.vDeck/config/valheim-server.properties` w katalogu instalacji | Użytkownik | Profil uruchomieniowy serwera Valheim (AD-013). | Ręcznie oraz z paneli gałęzi „Serwer” i ekranu „Ustawienia gry”. |

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
serwera i gry oraz zbudowanie nowej konfiguracji są serializowane przez serwis profilu;
nowa konfiguracja jest publikowana po udanym zapisie. Zapis nie wywołuje `reload()`: nowy profil
powstaje z właśnie zapisanych wartości ekranu i niezmienionych ustawień drugiego ekranu z bieżącej
konfiguracji, bez ponownego odczytu ustawień. Profile zmieniamy przez serwis właściciela,
aby zapis i odświeżenie konfiguracji pozostały jednym procesem. Widoki otrzymują wyłącznie
bezsekretny `LaunchProfile`; walidator startu i generator komendy otrzymują tę samą
`LaunchConfiguration`. `reload()` mapuje wartości już obecne w pamięci, nie czyta ponownie dysku.
Ręczna zmiana pliku wymaga restartu panelu. Błędny profil nie blokuje dostępu do ekranu jego edycji;
gotowość do startu, w tym pliki, katalogi i porty, jest sprawdzana dopiero przed startem serwera.

Teksty interfejsu nie są kanałem konfiguracji i nie leżą w katalogu instalacji. Zgodnie z `AD-009`
panel jest jednojęzyczny, a jego teksty pochodzą z pliku `resources/messages.properties` dołączonego
do aplikacji i czytanego przez domyślny `MessageSource` Spring Boota.

Komunikaty konsoli launchera i aktualizatora podlegają tej samej zasadzie, ale działają bez Springa:
leżą w jednym pliku `vdeck-runtime/src/main/resources/console-messages.properties`, a `UserConsole`
przyjmuje wyłącznie klucz (`ConsoleMessage`, `LauncherMessage`, `UpdaterMessage` implementujące
`ConsoleMessageIf`) i argumenty. Tekst rozwiązuje `I18n` przez `ResourceBundle` i `MessageFormat`
z polskim formatem liczb; brak klucza wyświetla sam klucz zamiast przerywać start.

Pierwszą właściwością jest `game.log.directory` — względna ścieżka katalogu logów serwera gry,
domyślnie `.vDeck/log` (AD-016). Każda skonfigurowana ścieżka przechodzi przez `PathService`: musi być
względna, jest normalizowana i po rozwiązaniu do postaci rzeczywistej musi pozostać wewnątrz katalogu
instalacji. Ścieżka absolutna, wychodząca poza katalog instalacji albo prowadząca poza niego przez
dowiązanie lub punkt reparsowania jest odrzucana.

Druga właściwość, `panel.game.settings.advanced` (domyślnie `false`), zapamiętuje, czy ekran
„Ustawienia gry” pokazuje sekcję „Zaawansowane” (AD-012). To preferencja wyświetlania panelu, a nie
część profilu uruchomieniowego: jej zmiana nie wpływa na opcje przekazywane serwerowi.

`game.log.retention.enabled` (domyślnie `true`) włącza retencję logów gry; `false` zachowuje
wszystkie logi, a wartość inna niż `true`/`false` używa domyślnej. Przy włączonej retencji
`game.log.retention.days` (domyślnie `14`) określa retencję dziennych katalogów logów; przed startem
usuwane są katalogi starsze niż bieżąca data minus tę liczbę dni. Zero zachowuje bieżący dzień;
wartość ujemna lub nienumeryczna przywraca domyślne 14 dni. Katalog bieżącego dnia jest tworzony
tylko wtedy, gdy jeszcze nie istnieje. `server.start.script` (domyślnie
`start_headless_server.bat`) wskazuje skrypt producenta czytany wyłącznie po `SteamAppId` (AD-015).
Nie jest kanałem wykonywania poleceń. Panel generuje własny skrypt lustrzany
`.vDeck/vDeck-server-start.bat` bez zapisanego hasła, ale nigdy go nie uruchamia.
`snapshot.directory` (domyślnie `shutdown-snapshots`) to nazwa jednego podkatalogu `.vDeck` na snapshoty świata
(AD-018); wartość z separatorem, `.` albo `..` przerywa start serwera.

Podstawowy układ danych:

```text
<katalog instalacji>/
├── valheim_server.exe
├── lib/
│   ├── vdeck.jar
│   ├── vdeck-runner.jar              ← wykonywalny helper z modułu vdeck-runner
│   ├── vdeck-launcher.jar            ← program startowy (AD-024)
│   └── vdeck-updater.jar             ← podmiana i recovery (AD-024)
├── start-vDeck.bat                   ← launcher albo recovery przez dołączoną Javę
├── README.md
├── README.txt
├── jre/                             ← Eclipse Temurin JRE 25, Windows x64
├── .vDeck/
│   ├── config/                        ← wszystkie pliki ustawień (AD-010)
│   │   ├── vDeck.properties
│   │   ├── valheim-server.properties
│   │   └── vDeck-start.properties     ← adres, port, update.enabled (AD-026)
│   ├── vdeck.lock                    ← blokada instalacji FileLock
│   ├── startup.lock                  ← bramka startu launchera i updatera
│   ├── handoff/                      ← jednorazowe deskryptory przekazania blokady
│   ├── update/                       ← download, staged, merged, executor, backup, apply.journal
│   ├── logs/launcher|updater/<dzień>/ ← log każdego uruchomienia, retencja 30 dni
│   ├── server.pid                    ← bezsekretny token uruchomienia
│   ├── vDeck-server-start.bat          ← regenerowany skrypt lustrzany bez hasła
│   └── log/                           ← domyślny `game.log.directory`
│       └── 2026-09-29/
│           └── 2026-09-29_12-00-00.log ← osobny plik na start
├── valheim-data/                      ← katalog domyślny; zmienia go `server.save.directory`
└── vdeck.db

<katalog danych Valheim>/
└── światy i listy graczy

<instalacja>/.vDeck/<snapshot.directory>/  ← domyślnie shutdown-snapshots
└── snapshot-latest/                 ← ostatni kompletny zestaw i metadane
```

Snapshoty są przechowywane poza instalacją Valheim, aby nie podlegały operacjom aktualizacji ani
weryfikacji plików serwera. AD-018 ustanawia osobny bezpieczny `BackupPathService` dla stałego
zewnętrznego katalogu; pozostałe ścieżki nadal rozwiązuje `PathService` wewnątrz instalacji.

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
Skrypt jest zapisywany w UTF-8 i wybiera stronę kodową 65001; cytuje operatory powłoki,
podwaja literalne procenty i wyłącza delayed expansion, zachowując nazwy z polskimi znakami.
Jego argumenty niesekretne pochodzą z poprawnego profilu bez osadzonych podwójnych cudzysłowów.

Panel nie przekierowuje strumieni konsoli serwera. Plik logu wskazuje jawnie opcja
`-logFile <ścieżka>` przekazana w wierszu poleceń, kierująca zapis do
`<game.log.directory>/<rrrr-MM-dd>/<rrrr-MM-dd_HH-mm-ss>.log` (AD-016). Domyślny katalog to
`.vDeck/log`; przy istniejącym pliku dodawany jest przyrostek `-1`, `-2` itd. Ścieżka jest
rozwiązywana przez `PathService` i przekazywana jako absolutna. Właścicielem pliku jest proces
serwera, a panel jedynie go śledzi, dzięki czemu logowanie nie zależy od życia procesu panelu.

Launcher nie dziedziczy więc żadnych uchwytów: `stdout` i `stderr` procesu pozostają domyślne, a
`STARTUPINFO` nie ustawia `STARTF_USESTDHANDLES`. Uchwyty `PROCESS_INFORMATION` są zamykane
bezpośrednio po uruchomieniu procesu. Każdy start otrzymuje nową ścieżkę logu; konsola
(`feature.logs`) wiąże śledzenie z konkretnym uruchomieniem przez nazwę pliku zamiast zakładać
nadpisywanie stałego pliku przy restarcie.

Łagodne zatrzymanie odbywa się przez osobny, krótkotrwały proces-helper wykonujący:

```text
FreeConsole → AttachConsole(pid) → SetConsoleCtrlHandler(NULL, TRUE)
            → GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0)
```

Izoluje to procesowe ustawienia konsoli i obsługę CTRL+C od głównej JVM. Zgodnie z AD-020 helper
jest osobnym artefaktem `vdeck-runner.jar`, budowanym wraz z panelem przez `mvnw package`.
Zawiera runtime i zależności wykonawcze, bez Springa. Plik z `vdeck-runner/target/vdeck-runner.jar`
należy umieścić jako `lib/vdeck-runner.jar` w katalogu instalacji także podczas uruchamiania panelu
z IDE lub `spring-boot:run`.
Punkt wejścia panelu uruchamia wyłącznie Springa.

`ConsoleControlConfiguration` tworzy singleton `ConsoleControlSender` z implementacją
`JvmConsoleControlSender`. Konfiguracja rozwiązuje `lib/vdeck-runner.jar` przez
`PathService.getAbsolutePath()` i przekazuje gotową listę argumentów uruchomienia do konstruktora.
Bean nie tworzy procesu ani wątku przy starcie panelu. `ServerRuntimeConfiguration` wstrzykuje go
do runtime; worker zatrzymania wywołuje go po wymaganym snapshotcie. Każdy sygnał uruchamia nowy
krótkotrwały proces. Brak lub uszkodzenie JAR-a ujawnia się przy żądaniu sygnału jako błąd helpera:
Stop zachowuje serwer zgodnie z AD-017. Panel uruchamia launcher (AD-024), opisany w sekcji 7.

Tryb techniczny jest już zaimplementowany jako pierwszy element funkcji Stop. Jego pełny kontrakt
to `--ctrl-sender <pid> <creation-instant> <absolute-executable>`: czas jest rzeczywistym czasem
utworzenia procesu, a ścieżka exe jest rozwiązywana do postaci rzeczywistej. `runner.VdeckRunner`
obsługuje wyłącznie helper. `ControlSenderCommand` waliduje argumenty,
`ProcessIdentity` sprawdza proces, a `WindowsConsoleControlSender` wykonuje sygnał i odłączenie
od konsoli. Błędny argument daje kod wyjścia `2`, błąd sygnału lub tożsamości — `1`, a wysłanie
zdarzenia — `0`; ten ostatni nie potwierdza zapisu świata ani wyjścia serwera.
`JvmConsoleControlSender` uruchamia helper przez listę argumentów `ProcessBuilder`, bez powłoki,
z odrzuconymi stdout/stderr i zamkniętym stdin. Timeout do 10 sekund obejmuje wyłącznie helper;
jego awaryjne sprzątanie nigdy nie kończy serwera.

Zgodnie z AD-017 job chroni wyłącznie nieukończony start. Po udanym utworzeniu i odczycie czasu
procesu launcher wyłącza `KILL_ON_JOB_CLOSE` przed przekazaniem własności runtime. Żywy serwer
może przeżyć panel; `close()` nie wysyła żadnego sygnału ani nie kończy procesu.
Runtime sprawdza pełną `ProcessIdentity` przed publikacją STARTING i własności procesu. Niepowodzenie
tej weryfikacji sprząta świeżo utworzony proces bez zmiany opublikowanego stanu. Po udanym przejęciu
błąd zapisu tokenu zachowuje własność, `onExit()` i timer, więc Stop oraz Kill nadal działają.

Panel udostępnia również Kill realizowany przez `ProcessHandle.destroyForcibly()`. Użycie tej
możliwości podlega zasadom bezpieczeństwa danych opisanym niżej.

Zamknięcie panelu zatrzymuje zarządzany serwer przez asynchroniczne `SmartLifecycle.stop(Runnable)`,
nie przez własny shutdown hook. Koordynator wysyła polecenie do kolejki zdarzeń i wywołuje callback
Springa po zakończeniu operacji albo po przekroczeniu jej deadline’u. Ma jawną fazę zamykania i
zależność od runtime, który zachowuje kolejkę, writer i token aż do zakończenia fazy.
Token procesu jest plikiem i nie zależy od `DataSource`.

Globalny deadline zatrzymania wynosi domyślnie 5 minut, a konfiguracja vDeck ustawia
`spring.lifecycle.timeout-per-shutdown-phase` na 6 minut. Aktualizacja lub kontrolowany restart
panelu próbuje więc zatrzymać serwer Valheim. Niepowodzenie pozostawia żywy serwer i token;
ponowny panel wymaga jawnej adopcji, nie tworzy automatycznie drugiej instancji.

---

## 7. Jedyność instancji i odzyskiwanie

`.vDeck/vdeck.lock` używa `FileLock` i zapewnia, że w danym katalogu najwyżej jeden proces — panel,
launcher albo updater — może zmieniać instalację. System operacyjny zwalnia blokadę po śmierci procesu,
również gdy nie wykona się kontrolowane zamknięcie. Panel uruchomiony przez launcher przejmuje ją
przed startem Springa w `shared.lock.InstallationLockService` i przekazuje do `ManagedLaunchStore`; panel uruchomiony bez launchera (IDE)
zdobywa ją sam w runtime.

### Start, przekazanie blokady i aktualizacja (AD-024)

Każdy start zdobywa najpierw `.vDeck/startup.lock`, potem `vdeck.lock`, bez czekania; zajęta blokada
oznacza komunikat i wyjście bez sprzątania. Właściciel obu blokad usuwa stare deskryptory
`.vDeck/handoff`, stosuje 30-dniową retencję logów uruchomień i rozpoznaje dziennik aktualizacji:
stan nieterminalny lub nieczytelny przekazuje do recovery w executorze, stan terminalny archiwizuje
dziennik w `update/backup` i dopiero potem usuwa `executor`, `staged`, `merged` i `download`.

Przekazanie blokady (`HandoffProtocol`, `SuccessorStarter`): poprzednik uruchamia następcę
z `--handoff <id>`, publikuje trwały deskryptor z rolą oraz tożsamościami obu procesów (PID, czas
utworzenia, rzeczywisty plik wykonywalny), zwalnia tylko `vdeck.lock` i czeka 10 s na potwierdzenie.
Następca weryfikuje rolę, własną tożsamość i tożsamość rzeczywistego rodzica, przejmuje blokadę, nigdy
nie dotyka bramki i zapisuje potwierdzenie. Brak potwierdzenia kończy własny proces następcy
i obserwuje jego wyjście przed odzyskaniem blokady. Launcher, updater i recovery po przejęciu czekają
na rzeczywiste wyjście poprzednika i zdobywają bramkę przed zmianami; panel jej nie zdobywa, bo
launcher utrzymuje ją do końca pracy panelu.

Przygotowanie (`UpdatePreparation`) działa w jednym wątku pod monotonicznym sufitem bezpieczeństwa
60 minut (AD-027): API `releases/latest` (pełna odpowiedź w 15 s, limit 1 MiB, dokładna nazwa zasobu,
rozmiar ≤ 512 MiB, `digest` `sha256:`, URL HTTPS na `github.com` w tym repozytorium), pobranie do
`.part` z SHA-256 w locie (nagłówki w 30 s, przerwanie po 30 s bez danych przez strażnika zamykającego
strumień, postęp w konsoli co 10 s; wolne, ale postępujące pobieranie trwa do sufitu), rozpakowanie biblioteką zip4j (odrzuca wpisy wychodzące poza katalog) do
`.tmp-staged-<uuid>`, zrzut plików na dysk i kontrola kompletu plików `DistributionItem`, wersji oraz
protokołu w manifestach, a na końcu kopia JRE i updatera do `executor`. Każda publikacja
to atomowy rename poprzedzony kontrolą sufitu. Po przekroczeniu sufitu wątek główny zamyka klienta
HTTP (`shutdownNow`), przerywa wątek i czeka na jego zakończenie, dopiero potem sprząta i uruchamia
bieżącą wersję. Nowsza wersja jest instalowana tylko przy `update.enabled=true`, wersji wydania
(nie `-SNAPSHOT`) i braku `server.pid` oraz procesu `valheim_server.exe` tej instalacji.

Updater (nowa wersja, z executora) po przejęciu blokad sprawdza ponownie serwer i kompletność paczki,
usuwa poprzedni backup, scala ustawienia do `update/merged` (AD-025) i utrwala dziennik `PREPARED`.
Stany transakcji: `PREPARED → APPLYING → COMMITTED` albo `→ ROLLING_BACK → ROLLED_BACK`. Każda
operacja zapisuje zamiar przed akcją: katalog (`lib`, `jre`) to rename instalacja → backup i staged →
instalacja; plik (BAT, README, PDF, ustawienia) to trwała kopia oryginału do backupu i jeden atomowy
rename nowej treści na miejsce starej, więc plik nigdy nie znika. Dziennik zapisuje się przez plik
tymczasowy, `force(true)` i `ATOMIC_MOVE`. `COMMITTED` następuje po sprawdzeniu kompletności i skrótów
nowego zestawu. Rollback idzie od końca, rozpoznaje stan z rzeczywistych plików (położenie katalogów,
skróty plików), nie usuwa pliku o nieoczekiwanej tożsamości i jest idempotentny także po przerwaniu.
Niejednoznaczny stan albo uszkodzony dziennik zatrzymuje start z instrukcją naprawy. Po stanie
terminalnym updater przekazuje instalację launcherowi z `--after-update`, który nie sprawdza już
aktualizacji. Gwarancja dotyczy przerwania procesu; odporności na zanik zasilania nie deklarujemy
bezwarunkowo, choć zapisy dziennika i kopie są wymuszane na dysk przed użyciem.

`.vDeck/server.pid` identyfikuje zarządzany serwer za pomocą PID, czasu startu i ścieżki do pliku
wykonywalnego. Sam PID nie jest wystarczający, ponieważ Windows może wykorzystać go ponownie.

Po zdobyciu locka panel sprawdza ograniczony do 16 KiB token i skanuje `ProcessHandle.allProcesses()`.
Proces o `info().command()` wskazującym dokładnie exe tej instalacji blokuje Start. Żywy zapisany PID
blokuje go również wtedy, gdy metadane są niedostępne. Nieczytelny lub niepoprawny token nie przerywa
skanu. Jeśli nie ma procesu tej instalacji, token trafia atomowo do kwarantanny
`server.pid.invalid-<czas>-<uuid>` z WARN, a Start zostaje odblokowany. Błąd kwarantanny przerywa Start.
Przy żywym procesie bez poprawnego tokenu pozostaje RECOVERY_REQUIRED bez opcji adopcji.
Odczyt stanu zleca pojedyncze
ponowne sprawdzenie recovery, aby zniknięcie procesu mogło odblokować GUI.

Aktualna implementacja oferuje tylko jawną adopcję pojedynczego procesu ze zgodnym tokenem:
PID, czas utworzenia i rzeczywisty exe muszą zostać ponownie potwierdzone. Token przechowuje także
launchId, świat, saveDirectory i log bez sekretów. Zmiany profilu po starcie nie zmieniają tych danych.
Ścieżki tokenu muszą pozostać w instalacji; backup root jest wyznaczany ponownie przez AD-018.
Odczyt tokenu nie wymaga istnienia katalogu zapisu; sprawdza jego przynależność do instalacji.
Istnienie i brak przekierowań są kontrolowane przy snapshotcie. Adopcja przechodzi do RUNNING
i uzbraja `onExit()`. Nie wysyła sygnałów ani nie tworzy kopii. `ServerAdoptException` rozróżnia
ADOPT_REJECTED (INFO bez przyczyny) i ADOPT_FAILED (ERROR z przyczyną); zniknięcie procesu jest odrzuceniem.

Proces bez zgodnego tokenu, wiele kandydatów lub niepewna tożsamość dają RECOVERY_REQUIRED bez
opcji adopcji. Pełna lista i obsługa obcych procesów są przyszłym zakresem. Token jest usuwany
po obserwacji wyjścia albo potwierdzeniu braku żywego zapisanego PID i kandydatów tej instalacji.

---

## 8. Stan i współbieżność

Podstawowa ścieżka stanów to:

```text
STOPPED → STARTING → RUNNING → STOPPING → STOPPED
```

Stany błędów to `START_FAILED`, `CRASHED` i `STOP_FAILED`. `STOP_FAILED` oznacza, że zatrzymanie zawiodło, a proces jest nadal zarządzany: błąd może dotyczyć
snapshotu, tożsamości, helpera, oczekiwania, deadline albo ręcznie dopuszczonego wymuszenia.
`RECOVERY_REQUIRED` oznacza konieczność rozstrzygnięcia procesów znalezionych po starcie panelu.

Wszystkie zdarzenia zmieniające stan — HTTP, harmonogram, parser logów, `onExit()` i zamykanie panelu
— trafiają do jednej kolejki zdarzeń. Tylko jej wątek zmienia stan i aktualne `RunningServer`.
Długie operacje I/O wykonują workery, a wynik wraca do kolejki z `operationId`. Wynik jest przyjmowany
wyłącznie wtedy, gdy dotyczy nadal aktywnej operacji; spóźnione callbacki są ignorowane.

`onExit()` podczas `STOPPING` lub `STOP_FAILED` prowadzi do `STOPPED`, podczas `STARTING` do `START_FAILED`, a podczas
`RUNNING` do `CRASHED`. Snapshot po crashu musi zakończyć się przed ewentualnym auto-restartem.
Sprzeczne polecenia są odrzucane jako „Operacja w toku”.

---

## 9. RCON — przyszły zakres, wyłączony z bieżącej implementacji

Żaden element Stop, Kill, snapshotów ani shutdownu nie sprawdza i nie używa RCON.

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

Zaimplementowane przebiegi bez RCON:

```text
Stop / nowy shutdown:
0. kompletny snapshot PRE_STOP                  BEST_EFFORT; błąd przerywa przed sygnałem
1. CTRL+C przez izolowany helper                do 10 s
2. oczekiwanie na wyjście                       do 120 s
3. jednorazowe ponowienie CTRL+C i oczekiwanie   helper do 10 s, wyjście do 30 s
4. brak wyjścia                                 STOP_FAILED; proces pozostaje uruchomiony

Ręcznie potwierdzony Kill:
0. próba snapshotu PRE_STOP                     błąd kopii jest logowany; nie blokuje Kill
1. weryfikacja tożsamości i uprawnienia          PID, rzeczywisty czas utworzenia i exe
2. destroyForcibly                              bez CTRL+C i bez czekania na próby łagodne
3. oczekiwanie na obserwację wyjścia             do 10 s
```

Każda operacja podlega jednemu monotonicznemu deadline 5 minut, obejmującemu próbę kopii.
Timeout i nieudany Stop kończą się STOP_FAILED z zachowaniem procesu, nigdy automatycznym
wymuszeniem (AD-017). Kill wymaga jawnego potwierdzenia ryzyka dla konkretnego launchId.
Potwierdzenie obejmuje zakończenie także bez backupu; nie ma dodatkowego checkboxa ani warunku
wcześniejszego błędu snapshotu. Runtime próbuje wykonać kopię w każdej operacji Kill, a po błędzie
kopii kontynuuje wymuszenie. Nie ignoruje anulowania, przerwania workera ani wyczerpania deadline.
Brak pewnej tożsamości nadal zabrania wymuszenia. `destroyForcibly() == true` nie zastępuje
obserwacji wyjścia: żywy proces po odmowie API lub czasie oczekiwania pozostaje w STOP_FAILED.
W zwykłym Stop błąd helpera przerywa operację; retry następuje po udanym wysłaniu pierwszego
CTRL+C i wyczerpaniu czasu na wyjście. Shutdown dołącza do aktywnej operacji bez odnawiania
zgody lub deadline; nowy shutdown sam nigdy nie przyznaje zgody na Kill.

`WorldContext` pochodzi z konfiguracji przechwyconej przy starcie. Snapshoty mają wyłącznie
BEST_EFFORT: kopia żywych plików nie dowodzi zakończonego ani spójnego semantycznie zapisu.
Writer kopiuje `worlds_local/<świat>.db` i `.fwl` wraz z obecnymi `.old`, albo cały istniejący
katalog `worlds_local/<świat>`. Kontroluje członkostwo, rozmiary, mtime i rzeczywistą tożsamość plików
przed i po kopii. SPI `FileIdentityReader` ma implementację Windows przez FFM:
`GetFileInformationByHandleEx(FileIdInfo)` zwraca numer woluminu i 128-bitowy FileId. `CreateFileW`
używa współdzielenia READ, WRITE i DELETE. Brak identyfikatora przerywa próbę, bez fallbacku do
`fileKey()` albo czasu utworzenia. Niestabilny zestaw i błędy I/O kopii dają do trzech prób całości.
Nieznany lub pusty zestaw
jest odrzucany. Brak źródłowego katalogu lub wymaganego `.db`/`.fwl` jest rozpoznawany przez
`WorldSaveMissingException`; zwykły Stop publikuje WORLD_SAVE_MISSING i odmowę WARN, bez
sygnału. Panel wyjaśnia brak źródłowego zapisu świata, a nie wcześniejszego backupu. Pozostałe
błędy kopii zatrzymują Stop z SNAPSHOT. Kill loguje niepowodzenie kopii i kontynuuje wymuszenie.
Nie wykonuje się przywracania ani semantycznego parsowania plików Valheim.

Staging leży w zatwierdzonym katalogu AD-018. Każdy plik i manifest są utrwalane przez `force(true)`;
zestaw publikuje `ATOMIC_MOVE`, bez fallbacku. Manifest zawiera powód PRE_STOP, czas, operację,
świat, BEST_EFFORT, rozmiary i SHA-256. Rename nie ma gwarancji trwałości po utracie zasilania.
Kontrola ścieżek odrzuca dowiązania, junctions i redirection. Kopiowanie używa bufora 64 KiB,
limitu 8 GiB na zestaw i 8192 wpisów; głębokość drzewa jest ograniczona poniżej 16.
AD-019 zachowuje ostatni kompletny zestaw w `snapshot-latest`. Po przygotowaniu `.tmp-<uuid>`
poprzedni zestaw jest atomowo przenoszony do `.old-<uuid>`, a nowy publikowany jako `snapshot-latest`.
Nieudana publikacja przywraca poprzednią kopię; jeśli rollback się nie uda, `.old-*` pozostaje do
odzyskania. Po sukcesie poprzednia kopia jest usuwana; błąd usuwania daje WARN. Na początku następnej
operacji writer odzyskuje `.old-*`, gdy brakuje `snapshot-latest`, i sprząta pozostałe `.tmp-*` oraz
`.old-*`. Wolne miejsce musi pomieścić nowy zestaw obok starego. Nie ma limitu 32 zatrzymań.
Starsze katalogi `snapshot-<operationId>` nie są automatycznie kasowane; ekran backupów i zarządzanie
historycznymi kopiami pozostają przyszłym zakresem.

Stan zmienia wyłącznie kolejka; snapshot i helper wykonuje jeden worker z ograniczoną kolejką.
Worker otrzymuje niezmienny kontekst, deadline i cancellation guard. Stare wyniki są odrzucane,
dalsze etapy anulowane po wyjściu, a nowy Start czeka na opróżnienie zaległego workera.
Timery operacji są anulowane po wyniku i usuwane z kolejki. Odświeżanie recovery współdzieli
jeden znacznik pending, aby odczyty nie tworzyły nieograniczonej kolejki skanów.

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
| Nawigacja | Menu boczne jako wspólny fragment `common/side-menu.html`: domyślnie ukryte, otwierane okrągłym przyciskiem w lewym dolnym rogu, z pozycjami pogrupowanymi w tytułowane sekcje; w obrębie sekcji pozycje tworzą drzewo o co najwyżej trzech poziomach działające jak akordeon. Drzewo jest zapisane w znacznikach fragmentu, bez modelu w Javie. Menu nie jest osadzane na ekranie wymuszonej zmiany hasła początkowego. Obok przycisku menu, stycznie z prawej strony, leży mniejszy okrągły przycisk strony głównej — osobny fragment `common/home-button.html`, osadzany przez te same widoki. |
| Profil uruchomieniowy | Panele otwierane z sekcji „Konfiguracja” menu bocznego (AD-030): cztery panele rozwijanej gałęzi „Serwer” — „Ogólne” (`/server-settings/server` — nazwa, port, widoczność, crossplay), „Dostęp” (`/server-settings/access` — hasło), „Świat i zapis” (`/server-settings/world`) i „Kopie automatyczne” (`/server-settings/inner-server-backups`) — oraz „Ustawienia gry” (`/game-settings` — zestaw ustawień świata na górze, a modyfikatory i klucze świata w sekcji „Zaawansowane”, domyślnie ukrytej, z zapamiętywaną widocznością). Pola odpowiadają opcjom `valheim_server.exe` z instrukcji serwera dedykowanego, są pogrupowane w sekcje i każde ma opis z dozwolonymi wartościami. Każdy panel zapisuje atomowo wyłącznie własne ustawienia; hasło serwera nigdy nie wraca do przeglądarki. Zmiany obowiązują od następnego uruchomienia serwera. |
| Lista zmian | Na stronie głównej panel „Co nowego w vDeck” (AD-029) pokazuje, od najnowszej, zmiany każdej wersji nowszej niż ostatnio potwierdzona i nie nowszej od zainstalowanej, z numerem każdej wersji. Bez żadnego potwierdzenia (aktualizacja z wersji sprzed tej funkcji) pokazuje tylko wersję zainstalowaną. Panel znika dopiero po „Zamknij” (`POST /release-notes/acknowledge`), które zapisuje zainstalowaną wersję. Świeża instalacja potwierdza swoją wersję przy starcie i panelu nie pokazuje. Treść pochodzi z `release-notes/<X.Y.Z>.md` w classpath: sekcje `## Nowości`, `## Zmiany`, `## Poprawki` w tej kolejności, każda pozycja to `- **Tytuł**` (do 80 znaków) i jedna wcięta linia opisu (do 200 znaków). Panel nie renderuje Markdownu, tylko wyświetla te pola przez `th:text`; uszkodzony plik jest pomijany z ostrzeżeniem w logu, a `ReleaseNotesFilesTest` zatrzymuje go w buildzie. Bez `BuildProperties` (uruchomienie z IDE) panel jest wyłączony. Ten sam plik jest opisem GitHub Release: workflow podaje go jako `--notes-file` z `--generate-notes`, a `.env/release.bat` i workflow przerywają wydanie, gdy plik nie opisuje żadnej zmiany. `release.bat` zakłada pusty plik następnej wersji z szablonu `.env/release-notes-template.md`. |
| Konsola | Ekran `/console` z sekcji „Procesy” menu bocznego pokazuje log bieżącego uruchomienia. `console.js` co sekundę pobiera `GET /console/log?offset=N`; serwer czyta plik z `ServerStatus.getLogFile()` od podanego bajtu (najwyżej 256 KiB, do ostatniego pełnego wiersza), a bez pozycji zwraca ostatnie 256 KiB. Bez SSE i bez bufora w pamięci. |
| Bezpieczeństwo żądań | Włączone CSRF; token jest dodawany do `hx-post` przez `htmx:configRequest`. |

Natywny JavaScript pozostaje bez build-stepu, dopóki jego rozmiar i zależności nie uzasadnią
wprowadzenia bundlera. Konsola nie przechowuje historii logu w pamięci: każde odpytanie czyta plik,
a nowy klient zaczyna od końcówki pliku.

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
| `ATOMIC_MOVE` bez `REPLACE_EXISTING` na Windows | Zastępuje istniejący plik; brak nadpisania wymaga jawnej kontroli istnienia. |
| Podmiana `lib` i `jre` przez updater z executora | Zadziałała w teście paczek 1.0.0 → 1.0.1; recovery przez BAT odtworzyło brakujące `lib` i `jre`. |
| `HttpClient.shutdownNow()` przy zatrzymanym body | Przerywa zablokowany odczyt; potwierdza test integracyjny na serwerze loopback. |

---

## 13. Otwarte ustalenia

- Potwierdzenie na rzeczywistym serwerze, że CTRL+C kończy zapis świata przed wyjściem procesu.
