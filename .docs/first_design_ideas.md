# vDeck — główne założenia projektowe

> Data: 2026-08-17 · Status: ustalenia przed implementacją

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

Początkowo projekt składa się z dwóch modułów Maven pod wspólnym parentem:

| Moduł | Odpowiedzialność |
|---|---|
| `vdeck-runtime` | Czysta Java: cykl życia serwera, procesy, sygnały, logi, snapshoty i granice zależne od platformy. Bez Springa. |
| `vdeck-app` | Aplikacja Spring Boot: web, bezpieczeństwo, konfiguracja, persystencja oraz integracja z runtime. |

Kierunek zależności Maven jest jednostronny: `vdeck-app` zależy od `vdeck-runtime`; runtime nigdy nie
zależy od aplikacji ani od Springa. Aplikacja korzysta z fasady `runtime.api`, a szczegóły cyklu życia
i implementacje Windows pozostają wewnętrzne dla runtime.

W `vdeck-app` obowiązuje package-by-feature. Kod webowy, logika aplikacyjna i persystencja danej
funkcji pozostają w jej pakiecie. Jeden feature nie sięga bezpośrednio do repozytorium ani encji
innego feature’a — komunikacja przechodzi przez jego publiczny serwis/fasadę lub zdarzenie.
Nie tworzymy osobnych modułów Maven dla warstw technicznych.

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

```text
vdeck/                                           ← parent i agregator Maven
├── pom.xml
├── .docs/
│   ├── architectural_decisions.md
│   ├── first_design_ideas.md
│   └── rules/
│       ├── README.md
│       ├── clean-code.md
│       ├── documentation.md
│       ├── maven.md
│       ├── security.md
│       └── unit-tests.md
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
│       │   │   └── StopOutcome
│       │   ├── spi/                             ← wymienne granice zewnętrzne
│       │   │   ├── GameProcessLauncher
│       │   │   ├── GracefulSignal
│       │   │   ├── ProcessReattacher
│       │   │   ├── ProcessDiscovery
│       │   │   ├── WorldPersistenceChannel
│       │   │   └── SaveConfirmation
│       │   ├── lifecycle/                       ← cykl życia i polityka zatrzymania
│       │   │   ├── ServerStateMachine
│       │   │   ├── StopOrchestrator
│       │   │   ├── StopPolicy
│       │   │   └── StopStage
│       │   ├── ownership/                       ← własność i odzyskiwanie procesu
│       │   │   ├── PanelLock
│       │   │   ├── PidTokenStore
│       │   │   ├── ProcessToken
│       │   │   ├── RecoveryCandidate
│       │   │   └── ProcessRecoveryService
│       │   ├── logs/                            ← logi i zdarzenia serwera
│       │   │   ├── LogTailer
│       │   │   ├── LogEventParser
│       │   │   └── LogEvent
│       │   ├── snapshot/                        ← snapshoty i retencja
│       │   │   ├── Snapshot
│       │   │   ├── SnapshotTrustLevel
│       │   │   ├── WorldSnapshotStore
│       │   │   ├── SnapshotValidator
│       │   │   └── RetentionPolicy
│       │   ├── rcon/                            ← opcjonalny kanał RCON
│       │   │   ├── RconClient
│       │   │   └── RconControlChannel
│       │   └── windows/                         ← adapter Windows; moduł dopiero przy 2. OS
│       │       ├── Win32
│       │       ├── WindowsProcessLauncher
│       │       ├── WindowsConsoleCtrlSignal
│       │       ├── WindowsProcessDiscovery
│       │       └── CtrlSender
│       └── test/java/…                          ← testy runtime bez Springa i Valheim
│
└── vdeck-app/                                   ← aplikacja Spring Boot
    ├── pom.xml
    └── src/
        ├── main/java/com/github/pplociennik/vdeck/
        │   ├── VdeckApplication.java
        │   ├── server/                          ← sterowanie i prezentacja stanu runtime
        │   │   ├── ServerController
        │   │   ├── ServerService
        │   │   └── ServerShutdownLifecycle
        │   ├── launch/                          ← profil uruchomieniowy
        │   │   ├── LaunchProfile
        │   │   ├── LaunchProfileService
        │   │   ├── LaunchProfileController
        │   │   └── persistence/
        │   ├── world/                           ← światy, wybór, import i eksport
        │   │   ├── World
        │   │   ├── WorldService
        │   │   ├── WorldController
        │   │   └── persistence/
        │   ├── backup/                          ← backup, restore i retencja
        │   │   ├── Backup
        │   │   ├── BackupService
        │   │   ├── BackupController
        │   │   └── persistence/
        │   ├── players/                         ← gracze i listy dostępu
        │   │   ├── PlayerSession
        │   │   ├── PlayerService
        │   │   ├── PlayerController
        │   │   └── persistence/
        │   ├── logs/                            ← SSE i bufor logów dla UI
        │   │   ├── LogService
        │   │   └── LogStreamController
        │   ├── mods/                            ← BepInEx i RCON
        │   │   ├── ModService
        │   │   └── ModController
        │   ├── security/                        ← dostęp do panelu
        │   │   ├── PanelUser
        │   │   ├── PanelUserService
        │   │   ├── SecurityConfiguration
        │   │   └── persistence/
        │   ├── persistence/                    ← wspólna konfiguracja JPA i SQLite
        │   └── shared/                          ← wyłącznie elementy faktycznie współdzielone
        ├── main/resources/
        │   ├── application.yaml
        │   ├── templates/
        │   │   ├── layout/
        │   │   ├── fragments/
        │   │   └── pages/
        │   └── static/
        │       ├── css/
        │       └── js/
        └── test/java/…                          ← testy funkcji i granic ArchUnit
```

`vdeck-app` zależy od publicznego API `vdeck-runtime`; kod webowy nie odwołuje się bezpośrednio do
pakietów `windows` ani wewnętrznej orkiestracji runtime. Pakiety `launch`, `world`, `backup`,
`players`, `logs`, `mods`, `security` i `server` są granicami domenowymi oraz właścicielami swojego
kodu. Wewnątrz feature’a domyślnie pozostaje płaska, pragmatyczna struktura. Obowiązkowo wydzielana
jest jedynie persystencja, aby encje JPA i repozytoria nie wyciekały do innych feature’ów.

Dalsze podpakiety powstają dopiero po pojawieniu się rzeczywistej złożoności. Nie narzucamy każdemu
feature’owi osobnych portów, adapterów, modeli transportowych, mapperów ani klas use case. Granice
między feature’ami nadal są pilnowane przez ArchUnit i komunikację przez serwis właściciela danych.

---

## 4. Persystencja

Panel używa SQLite oraz Spring Data JPA. Funkcjonalne repozytoria są generycznymi interfejsami
`JpaRepository` lub `CrudRepository` i znajdują się razem z encjami w podpakiecie `persistence`
właściwego feature’a. Wspólny pakiet `persistence` zawiera jedynie konfigurację JPA i SQLite.

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
występować wyłącznie w podpakietach `..persistence..`.

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
listy graczy.

Podstawowy układ danych:

```text
<ApplicationHome>/
├── valheim_server.exe
├── vdeck.jar
├── vdeck.lock
├── server.pid
├── vdeck.db
└── vdeck-logs/
    └── valheim-server.log

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

`stdout` i `stderr` serwera są scalane i kierowane bezpośrednio do trwałego pliku
`vdeck-logs/valheim-server.log`. Panel jedynie śledzi ten plik, dzięki czemu logowanie nie zależy od
życia procesu panelu. Launcher używa `CreateFileW`, `STARTF_USESTDHANDLES` oraz `STARTUPINFOEX` z
`PROC_THREAD_ATTRIBUTE_HANDLE_LIST`, aby proces odziedziczył wyłącznie uchwyt pliku otwartego w
trybie append. Uchwyt rodzica oraz uchwyty `PROCESS_INFORMATION` są zamykane po uruchomieniu procesu.

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
| Użytkownicy | Jeden użytkownik `admin`; brak rejestracji i ról. |
| Hasło początkowe | `admin`, z wymuszoną zmianą przy pierwszym logowaniu. |
| Sesje | Maksymalnie jedna aktywna sesja. |
| Widoki | Thymeleaf, fragmenty HTMX i natywne moduły ES bez obowiązkowego procesu budowania frontendu. |
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
- Ostateczna polityka hasła początkowego i pierwszego logowania.
