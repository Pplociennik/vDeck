# Plan implementacji zatrzymania serwera Valheim

> Data: 2026-10-07 · Status: Stop i Kill podłączone do wyspy; testy automatyczne wykonane,
> próba na rzeczywistym serwerze Valheim pozostaje osobną weryfikacją wdrożeniową.

Plan obejmuje łagodne zatrzymanie (Stop), ręcznie potwierdzone wymuszone zatrzymanie (Kill)
oraz kontrolowane zamknięcie panelu. RCON jest całkowicie poza zakresem.
Decyzje użytkownika zapisano jako AD-017 i AD-018 w
[rejestrze decyzji](../architectural_decisions.md). Implementacja obejmuje helper CTRL+C,
snapshoty, drabinę zatrzymania, asynchroniczny shutdown, token procesu, blokadę instalacji,
minimalną jawną adopcję oraz formularze wyspy i odświeżanie statusu.

Poprawki po review wdrożono według [osobnego planu](server-stop-review-fixes.md) (2026-10-12):
AD-019 zastępuje limit historii retencją ostatniego kompletnego snapshotu. Instalacja jest kanonizowana,
token uszkodzony przy pustym skanie trafia do kwarantanny, a tożsamość plików Windows pochodzi z FileIdInfo.

## 1. Punkt wyjścia i zakres

Stan kodu przed rozpoczęciem implementacji:

| Element | Stan obecny | Potrzebna zmiana |
|---|---|---|
| `runtime.api.ServerRuntime` | `start()` i `status()` | Kontrakt asynchronicznego zatrzymania |
| `runtime.api.ServerState` | Pięć stanów związanych ze startem | `STOPPING` i `STOP_FAILED` |
| `runtime.api.ServerStatus` | Stan, PID, czas startu i log | Fabryki zatrzymania i postęp operacji |
| `runtime.api.RunningServer` | `ProcessHandle`, czas panelu i log | Zweryfikowana tożsamość i kontekst świata |
| `runtime.lifecycle.ServerStateMachine` | Jedna kolejka; `operationId` dotyczy startu | Orkiestracja etapów i osobna tożsamość uruchomienia |
| `ServerStateMachine.close()` | `destroy()`, następnie od razu `STOPPED` | Oddzielenie zatrzymania gry od zamykania zasobów |
| `runtime.windows.WindowsProcessLauncher` | `CREATE_NO_WINDOW`, zawieszenie, job, wznowienie | Zachowanie izolacji konsoli i kontrola tożsamości |
| `config.ServerRuntimeConfiguration` | Spring wywołuje `close()` przy niszczeniu beana | Koordynator `SmartLifecycle` |
| `VdeckApplication.main()` | Zawsze uruchamia Springa | Wczesna ścieżka `--ctrl-sender` |
| `feature.server` | Tylko `POST /server/start` | Stop, Kill, walidacja potwierdzenia i błędy procesu |
| `templates/common/island.html` | Stop i Kill są nieaktywnymi funkcjonalnie przyciskami | Formularze, dostępność według stanu i potwierdzenie |
| Snapshoty | Brak | Kopia świata przed zatrzymaniem |

`destroy()` w poprzednim `close()` nie jest łagodnym zatrzymaniem Valheim. Kod zeruje proces bez
potwierdzenia jego wyjścia. Nowa implementacja nie może przejąć tego zachowania.

Zakres obejmuje minimalny mechanizm snapshotów potrzebny do zatrzymania, obsługę CTRL+C, Kill,
prezentację przebiegu i integrację zamykania panelu. RCON jest całkowicie poza zakresem tego planu:
nie planujemy jego wykrywania, kontraktów, adapterów, konfiguracji ani testów.
Restart serwera, harmonogramy, instalowanie modów, pełny ekran zarządzania backupami, restore,
strumieniowanie logów do przeglądarki i odzyskiwanie obcych procesów pozostają poza tym zadaniem.

## 2. Zaakceptowane rozstrzygnięcia

### 2.1 Życie serwera a Windows Job Object — AD-017

Nieudany Stop pozostawia żywy proces. Dotyczy to również zamykania panelu: timeout, błąd snapshotu
lub helpera nigdy nie przyznają zgody na wymuszenie. `close()` zwalnia zasoby bez kończenia serwera.
Launcher stosuje `KILL_ON_JOB_CLOSE` wyłącznie przy nieukończonym tworzeniu procesu, następnie
wyłącza flagę przed przekazaniem procesu runtime. Serwer może przeżyć również awarię panelu.

Z tego powodu zakres obejmuje `.vDeck/vdeck.lock`, bezsekretny `.vDeck/server.pid` oraz recovery.
Start blokuje odnaleziony proces tej instalacji, nieczytelny token lub żywy PID zapisany w tokenie
bez wystarczających metadanych. Adopcja wymaga jednego kandydata, zgodnego tokenu i ponownego
sprawdzenia PID, rzeczywistego czasu utworzenia oraz exe; jest wyłącznie jawna.
Obsługa listy obcych procesów i ich adopcja pozostają przyszłym zakresem.

### 2.2 Lokalizacja snapshotów — AD-018

Zatwierdzony katalog to `<rodzic instalacji>/.vDeck-backups/<nazwa instalacji>`.
Przykład: `D:/Servers/Valheim` → `D:/Servers/.vDeck-backups/Valheim`.
`feature.backup.service.BackupPathService` rozwiązuje tę stałą lokalizację osobno od `PathService`.
Wyklucza nakładanie na instalację lub aktywny zapis i sprawdza istniejących przodków bez dowiązań
i przekierowań rzeczywistej ścieżki. Writer ponawia kontrolę przy zapisie. Błąd nie powoduje
przeniesienia kopii w inne miejsce. Ogólny `PathService` nadal ogranicza ścieżki do instalacji.

### 2.3 Semantyka Kill

Zmiana zaakceptowana 2026-10-07: Kill oznacza wymuszone zatrzymanie po jednym potwierdzeniu
użytkownika dla aktualnego launchId. Runtime próbuje wykonać snapshot, a następnie od razu
wywołuje `destroyForcibly()`, niezależnie od powodzenia backupu. Kill nie wysyła CTRL+C i nie
czeka na łagodne próby. Nie wymaga wcześniejszego błędu kopii ani dodatkowego potwierdzenia.

Ekran ostrzega o utracie danych także wtedy, gdy kopia nie powstanie. Zgoda jest jednorazowa,
nie przechodzi na Stop ani kolejne operacje. Deadline, anulowanie, przerwanie workera i pewna
tożsamość procesu nadal obowiązują. Sukces to dopiero zaobserwowane wyjście.
Stop i nowy shutdown nie uzyskują prawa do wymuszenia, również po błędzie backupu.

## 3. Kontrakt akcji

| Akcja | Warunki | Przebieg | Wynik |
|---|---|---|---|
| Stop | Zarządzany serwer w `RUNNING` lub `STOP_FAILED` | Snapshot, CTRL+C i jedna ponowna próba; bez Kill | `STOPPED` po wyjściu albo `STOP_FAILED` z żywym procesem |
| Kill | Te same stany, aktualne uruchomienie i jawne potwierdzenie ryzyka | Próba snapshotu, potem wymuszenie również po błędzie kopii; bez CTRL+C | `STOPPED` po potwierdzeniu wyjścia albo `STOP_FAILED` |
| Shutdown panelu | Również `STARTING`; brak nowych startów | Ta sama orkiestracja, bez domyślnej zgody na ryzyko ręczne | Callback po wyniku/deadline; żywy serwer i token pozostają po wyjściu panelu |

Dla `STOPPED`, `START_FAILED` i `CRASHED` żądanie z GUI jest odrzucane jako brak zarządzanego
serwera. Shutdown bez procesu kończy się bez operacji. GUI nie rozpoczyna zatrzymania podczas
`STARTING`; shutdown musi obsłużyć ten stan, aby świeżo utworzony proces nie ominął zamknięcia.

Podczas `STOPPING` kolejne Stop, Kill i Start są odrzucane jako operacja w toku. Shutdown dołącza
do aktywnej operacji zamiast rozpoczynać drugą i nie odnawia jej deadline'u ani zgody.
Nie przerywamy Stop kolejnym żądaniem Kill. Po wyniku `STOP_FAILED` użytkownik może ponowić akcję.

Ręczne potwierdzenie dotyczy konkretnego uruchomienia i konkretnej operacji. Nie zapisujemy
trwałego ustawienia „zawsze zezwalaj na Kill”. Plan nie obejmuje automatycznego wymuszenia:
snapshoty mają wyłącznie poziom `BEST_EFFORT`, a Kill wymaga ręcznego
potwierdzenia ryzyka utraty danych także bez aktualnej kopii. Upływ czasu nigdy nie tworzy zgody. Wyłączenie joba po udanym starcie nie jest alternatywną ścieżką Kill.

## 4. Przebiegi zatrzymania i limity

Deadline: 300 sekund od przyjęcia żądania na kolejce runtime, mierzony monotonicznie. Każdy etap
otrzymuje mniejszy z własnego limitu i pozostałego czasu. Snapshot należy do tego samego budżetu.

Stop i nowy shutdown:

1. Opublikować STOPPING i wykonać kompletny snapshot PRE_STOP / BEST_EFFORT. Błąd kopii przerywa
   operację przed sygnałem. Brak źródłowego `.db`/`.fwl` lub worlds_local daje WORLD_SAVE_MISSING.
2. Wysłać CTRL+C przez helper z limitem 10 s i czekać na wyjście do 120 s.
3. Jeśli serwer żyje, powtórzyć CTRL+C raz i czekać do 30 s. Błąd helpera przerywa operację.
4. Brak wyjścia daje STOP_FAILED z zachowaniem procesu, bez wymuszenia.

Potwierdzony Kill:

1. Opublikować STOPPING i podjąć próbę snapshotu PRE_STOP. Błąd kopii jest logowany i nie blokuje
   wymuszenia. Anulowanie lub przerwanie workera nie jest traktowane jako zwykły błąd backupu.
2. Zweryfikować PID, rzeczywisty czas utworzenia i exe oraz aktualne uprawnienie operacji.
3. Od razu wywołać `destroyForcibly()`, bez CTRL+C i bez prób łagodnego zatrzymania.
4. Czekać do 10 s na wyjście; odmowa API, wyjątek lub brak wyjścia daje STOP_FAILED z żywym procesem.

`true` z API nie oznacza STOPPED: wymagana jest obserwacja wyjścia. Nie wykonywać wymuszenia po
anulowaniu, upływie deadline lub bez pewnej tożsamości. Starsza kopia nie jest raportowana jako
udany snapshot nowej operacji. Kill może utracić niezapisany postęp i dane świata bez kopii.
Wysłanie CTRL+C ani wykonanie snapshotu nie dowodzi ukończenia zapisu. RCON jest wyłączony.

## 5. Runtime, kontekst uruchomienia i współbieżność

### 5.1 Publiczny kontrakt i właściciel danych

Rozszerzyć `runtime.api` o zweryfikowaną tożsamość procesu, bezsekretny kontekst świata oraz żądanie
zatrzymania z nazwanymi wariantami: łagodne, ręcznie potwierdzone wymuszenie i shutdown.
Przykładowe nazwy: `ProcessIdentity`, `WorldContext`, `StopRequest`. Proste nośniki mają postać
rekordów; żądanie o różnych dozwolonych kształtach — klasy `final` z prywatnym konstruktorem
i nazwanymi fabrykami. Wszystkie nazwy nowych typów są robocze.

Kontekst świata ustalić podczas startu z tej samej przechwyconej `LaunchConfiguration`, z której
powstają argumenty `-world` i `-savedir`. Do runtime przekazać znormalizowane, zweryfikowane ścieżki
i nazwę świata. Nie odczytywać przy zatrzymaniu bieżącego profilu: użytkownik mógł zmienić go po
starcie, a zmiany dotyczą następnego uruchomienia. Nie przechowywać pełnego sekretnego `LaunchSpec`
ani nie wydobywać świata przez ponowne parsowanie argumentów.

Zewnętrzni konsumenci nadal używają wyłącznie `ServerRuntime` i `ServerService`. Proces, stan,
aktywną operację i jej kontekst przechowuje wyłącznie runtime; przekazane workerom wartości są
niemutowalnym kontekstem jednego zadania, bez prawa podejmowania nowych decyzji o stanie.

Proponowany kontrakt runtime: `stop( StopRequest )` zwraca `CompletionStage< Void >`. Przyjęcie
i odrzucenie rozstrzyga krótka akcja na kolejce; etap kończy się sukcesem dopiero po wyjściu
procesu, a niepowodzenie kończy się wyjątkiem. Serwis aplikacyjny przyjmuje żądanie i zwraca `void`,
a HTTP nie czeka przez cały deadline. Nie wprowadzać obiektów wyniku odrzucenia sprzecznych z AD-014.

### 5.2 Zdarzenia i przejścia stanów

Oddzielić identyfikator uruchomienia od `operationId` zatrzymania. Obecny callback `onExit()` jest
powiązany z identyfikatorem startu; zwiększenie jednego wspólnego licznika przy Stop mogłoby
spowodować odrzucenie prawidłowego wyjścia. Każdy wynik workera zawiera oba identyfikatory.

| Zdarzenie | Nowy stan i własność procesu |
|---|---|
| Przyjęcie Stop/Kill | `STOPPING`; proces pozostaje zarządzany |
| Błąd snapshotu zwykłego Stop, deadline, odmowa Kill lub nieskuteczne wymuszenie | `STOP_FAILED`; zachować żywy proces i kontekst |
| Wyjście bieżącego procesu podczas `STOPPING` | `STOPPED`; zwolnić kontekst i zakończyć operację |
| Wyjście po `STOP_FAILED` związanym z próbą zatrzymania | `STOPPED`; dopisać późne zakończenie, bez ponownego kończenia zakończonego etapu |
| Callback starej operacji albo starego uruchomienia | Zignorować; nie zmieniać aktualnego stanu |

`STOP_FAILED` nie jest stanem pozwalającym na Start. Rozszerzyć fabryki `ServerStatus`, zachowując
PID, czas i log w `STOPPING` oraz `STOP_FAILED`. Stan błędu musi zawierać bezsekretny powód.

Długie I/O wykonują workery; tylko kolejka zdarzeń podejmuje decyzję o następnym etapie i zmienia
stan. Oczekiwanie na helper, proces i pliki nie blokuje kolejki. Timer deadline'u anuluje
operację, a każdy późny callback jest sprawdzany przed wywołaniem kolejnego skutku ubocznego.
Anulowanie zadania nie wystarcza: tuż przed sygnałem, publikacją snapshotu i Kill trzeba sprawdzić
uprawnienie aktywnej operacji. Nie dopuścić do startu następnej instancji, dopóki zadania starej
mogą jeszcze działać na jej plikach. Kolejki i liczba workerów mają skończone limity.

## 6. Windows i tryb `--ctrl-sender`

1. Dodać granicę sygnału w `runtime.spi` i implementację Windows. Win32/FFM pozostają w runtime;
   aplikacja składa osobny bean `ConsoleControlSender` w `config.ConsoleControlConfiguration`.
2. Zgodnie z późniejszą decyzją AD-020 punkt wejścia helpera należy do `runner.VdeckRunner`.
   `VdeckApplication.main()` uruchamia wyłącznie Springa.
   Błędne argumenty helpera kończą proces z niezerowym kodem, bez uruchomienia panelu. Helper
   nie otwiera SQLite, ustawień, portu HTTP ani kontekstu Springa i nie tworzy joba serwera.
3. Helper wykonuje `FreeConsole`, `AttachConsole( pid )`, następnie
   `SetConsoleCtrlHandler( NULL, TRUE )` i `GenerateConsoleCtrlEvent( CTRL_C_EVENT, 0 )`.
   Błędy Win32 zachowują kod `GetLastError`; zasoby i przyłączenie do konsoli są zwalniane.
4. Bean uruchamia osobny `vdeck-runner.jar` dopiero na żądanie, przy użyciu Java z bieżącego
   `java.home` i jawnego `--enable-native-access=ALL-UNNAMED`, bez powłoki, Pythona i PowerShella.
   JAR runnera należy dostarczyć do katalogu instalacji także dla IDE/`spring-boot:run`.
   Utworzenie beana nie uruchamia procesu ani wątku oczekującego.
   Helper nie ma pokazywać okna konsoli. Wyjście helpera przekierować lub czytać strumieniowo
   z limitem, żeby zapełniona rura nie zatrzymała procesu.
5. Zero z helpera oznacza wysłanie zdarzenia, nie zapis świata i nie wyjście serwera. Timeout lub
   niezero powodują kontrolowany retry/failure; nie automatyczną zgodę na Kill.

CTRL+C jest wysyłane do procesów współdzielących docelową konsolę; nie można ograniczyć go do
grupy przez podanie PID w drugim argumencie. Wymagana jest obecna izolacja konsoli serwera.
Po `AttachConsole` handler trzeba ustawić ponownie, ponieważ zmiana konsoli resetuje handlery.
Szczegóły potwierdzają
[GenerateConsoleCtrlEvent](https://learn.microsoft.com/en-us/windows/console/generateconsolectrlevent),
[AttachConsole](https://learn.microsoft.com/en-us/windows/console/attachconsole) i
[SetConsoleCtrlHandler](https://learn.microsoft.com/en-us/windows/console/setconsolectrlhandler).

Tożsamość sprawdzać przed każdym CTRL+C i Kill. Oprócz `ProcessHandle` potrzebne są rzeczywisty
czas utworzenia procesu i znormalizowana ścieżka exe. Obecne `startedAt = clock.instant()` nie
jest dokładnym tokenem czasu utworzenia Windows i nie nadaje się do ścisłego porównania.
Helper ponownie sprawdza tożsamość otrzymaną z zaufanego kontekstu uruchomienia; wymaga to
dodatkowych argumentów tożsamości obok `--ctrl-sender <pid>`. GUI nie podaje dowolnego PID.
Brak możliwości potwierdzenia tożsamości powoduje bezpieczne przerwanie sygnału.

## 7. Minimalny mechanizm snapshotów

Logika kopiowania i publikacji należy do runtime. Konfiguracja katalogu i przyszłe operacje backupów
w aplikacji należą do `feature.backup`, z kontraktami w `service` i implementacjami w `service.impl`.
Nie tworzyć pustych pakietów ani encji tylko dlatego, że występują w docelowym drzewie projektu.

Implementacja realizuje poniższe zabezpieczenia; próba na rzeczywistych plikach Valheim pozostaje checklistą wdrożeniową:

- Zweryfikować faktyczny układ plików aktywnego świata w bieżącym Valheim: wymagane `.db` i `.fwl`,
  obecne wersje `.old`, lokalne katalogi świata i zachowanie podczas pierwszego tworzenia świata.
  Brak źródłowego `.db`/`.fwl` lub worlds_local daje WORLD_SAVE_MISSING; nie tworzyć pozornej kopii.
  Zwykły Stop zachowuje proces, a potwierdzony Kill kontynuuje mimo tego błędu.
- Kopiować cały zestaw strumieniowo w buforze o stałym rozmiarze. Przed i po kopiowaniu porównać
  skład zestawu, rozmiary i mtime wszystkich plików; zmiana odrzuca cały zestaw. Proponowane są
  najwyżej trzy próby, ograniczone wspólnym deadline'em.
- Wykonać walidację strukturalną, policzyć SHA-256 i rozmiary; metadane zawierają świat,
  identyfikator uruchomienia i operacji, czas, powód i poziom zaufania.
- Zapisywać zestaw do tymczasowego katalogu na docelowym systemie plików, utrwalić pliki i metadane
  przez `force`/fsync oraz opublikować komplet przez `ATOMIC_MOVE`. Sprawdzić gwarancje trwałości
  i obsługę katalogów na Windows. Brak wymaganej atomowości jest błędem, nie cichym fallbackiem.
- Sprzątać nieopublikowane zestawy i poprzednią kopię po publikacji nowej. Dowiązania, punkty reparsowania,
  podmiana źródeł i celów oraz brak miejsca muszą mieć bezpieczną ścieżkę błędu.
- AD-019 utrzymuje `snapshot-latest`, z atomową podmianą, rollbackiem i odzyskiwaniem `.old-*`
  przed sprzątaniem pozostałości po awarii. Limit zestawu: 8 GiB, 8192 wpisy i głębokość poniżej 16.
  Kopiuje parę `.db`/`.fwl` z istniejącymi `.old` albo cały katalog `worlds_local/<świat>`.
  Każdy plik i manifest są utrwalane przed `ATOMIC_MOVE`. Trwałość rename po utracie zasilania
  oraz semantyczna poprawność plików nie są gwarantowane przez BEST_EFFORT.
  Jeżeli chronionej kopii nie da się zachować w limicie miejsca, odrzucić
  nową operację z czytelnym powodem zamiast usuwać tę kopię.

`BEST_EFFORT` nigdy nie dowodzi semantycznej poprawności świata. Snapshoty z różnych uruchomień
nie mogą być zamiennie używane jako autoryzacja bieżącego wymuszenia.

## 8. Warstwa aplikacyjna i UI

- Rozszerzyć `ServerService` o przyjęcie Stop i ręcznie potwierdzonego Kill. Implementacja nie
  przechowuje kopii procesu, świata ani stanu. Uprawnienie wynikające z potwierdzenia tworzy
  po walidacji żądania; nie przyjmuje od klienta poziomu zaufania snapshotu.
- Dodać `POST /server/stop`, `POST /server/kill` oraz ekran potwierdzenia
  `GET /server/kill-confirmation`. Każda zmiana stanu wymaga `ADMIN`, zakończonej pierwszej zmiany
  hasła i CSRF. GET jedynie przedstawia ryzyko, nie uruchamia snapshotu ani zakończenia procesu.
- Formularze wskazują identyfikator uruchomienia nadany przez runtime. Porównać go na kolejce,
  aby potwierdzenie ze starej karty nie zatrzymało serwera uruchomionego ponownie. Kill dodatkowo
  wymaga jawnego pola potwierdzenia ryzyka; brak potwierdzenia jest odrzucany także bez JavaScriptu.
- W `feature.server.exc` dodać po jednym wyjątku procesu: `ServerStopException` i
  `ServerKillException`, obejmującym odrzucenie i failure z przyczyną. Walidacja pól launchId i potwierdzenia kończy się
  odpowiednim wyjątkiem. Rozszerzyć istniejący `ServerExceptionHandler` zgodnie z AD-014.
- Po przyjęciu żądania przekierować na `/` z komunikatem „Rozpoczęto zatrzymywanie”, nie
  „Zatrzymano”. Odrzucenia przed przyjęciem obsługuje advice. Błąd asynchroniczny po zakończeniu
  HTTP nie może już wrócić przez ten advice: runtime kończy etap wyjątkowo i publikuje powód
  w statusie, a aplikacja mapuje go na `ServerMessage`. Przyczynę techniczną logować raz na granicy
  zakończenia operacji; odczyty statusu nie logują ponownie tego samego błędu.
- Zaktualizować wyspę akcji, fabryki statusów, tłumaczenia stanów, komunikaty i style. Wszystkie
  teksty pochodzą z `messages.properties`, Java używa `TranslationKeyIf`. Start jest zablokowany
  także podczas `STOPPING` i `STOP_FAILED`. Restart pozostaje poza zakresem.
- Dodać lekki odczyt fragmentu stanu/wyspy przez uwierzytelniony GET i moduł ES, np.
  `server-status.js`. Proponowany polling co 2 sekundy tylko przy widocznej stronie, bez
  nakładających się żądań i bez nieograniczonej historii. Błąd odczytu nie ma tworzyć pozornego
  `STOPPED`. Formularze i ręczne odświeżenie działają również bez JS.
- Ekran potwierdzenia stosuje `.panel-frame`, istniejące komponenty i globalne style. Wspólna
  wyspa nadal jest fragmentem `templates/common/`. Nowe tokeny wyłącznie w `variables.css`,
  ikony wyłącznie w obecnym spricie. Zapewnić klawiaturę, focus, tekstowy stan, 44 × 44 px oraz
  telefon/tablet w obu orientacjach, bez przewijania strony przy 320 px.

Nie dodawać konfigurowalnych timeoutów każdego kroku bez potrzeby.
Jeżeli globalny deadline będzie edytowalny, jego właścicielem jest `VdeckProperty` i
`vDeck.properties`; odczyt przez `PropertyReader`, inicjalizacja przez `SystemPropertiesInitializer`.
Konfiguracja katalogu snapshotów wynika z uzgodnienia w §2.2. Nie przenosić ustawień użytkownika
do `application.yaml`.

### 8.1 Podłączenie kontrolek wyspy

Poniższy kontrakt jest doprecyzowaniem integracji, a nie opisem gotowych endpointów.

| Kontrolka lub żądanie | Powiązanie | Warunek dostępności i efekt |
|---|---|---|
| Stop na wyspie | Formularz `POST /server/stop` | Aktywny `RUNNING` lub `STOP_FAILED`; pole ukryte z identyfikatorem uruchomienia i CSRF |
| Kill na wyspie | Formularz `GET /server/kill-confirmation` | Te same stany; przekazuje identyfikator uruchomienia, nie kończy procesu |
| Potwierdzenie Kill | Formularz `POST /server/kill` | Identyfikator z ekranu potwierdzenia, wymagany checkbox akceptacji ryzyka i CSRF |
| Odczyt wyspy | `GET /server/status` | Zwraca fragment wyspy ze świeżym statusem; wymaga `ADMIN` i zmiany hasła początkowego |

Stop i Kill są nieaktywne w pozostałych stanach; Start pozostaje nieaktywny w `STOPPING`
i `STOP_FAILED`. Dostępność w HTML jest ułatwieniem, a runtime rozstrzyga poprawność stanu
i identyfikatora uruchomienia na swojej kolejce. Stary formularz jest odrzucany, bez snapshotu
i bez sygnału. Wejście na ekran potwierdzenia również sprawdza aktualne uruchomienie, a POST
powtarza kontrolę przed przyjęciem operacji.

Po przyjęciu akcji użytkownik wraca na `/` z komunikatem o rozpoczęciu zatrzymywania.
Polling aktualizuje wyspę i tekstowy powód `STOP_FAILED` bez nadpisywania formularzy ustawień.
Bez JavaScriptu akcje i potwierdzenie pozostają działające, a status aktualizuje odświeżenie strony.
Potwierdzenie opisuje próbę backupu, a następnie natychmiastowe wymuszenie, także po błędzie kopii.
Jedno potwierdzenie obejmuje ryzyko utraty danych bez backupu. Restart pozostaje poza zakresem.

## 9. Kontrolowane zamknięcie panelu

Dodać koordynator, np. `ServerShutdownLifecycle`, implementujący `SmartLifecycle` w aplikacji,
z wiringiem w głównym `config`. Zatrzymanie Springa ma wywołać runtime i callback po ukończeniu
albo deadline. Nie dodawać własnego shutdown hooka. Mechanizm callbacku i kolejność faz opisuje
[dokumentacja Spring](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html).

- Najpierw zablokować nowe polecenia startu i rozpocząć/dołączyć do zatrzymania.
- Ustawić jawną fazę oraz zależności, aby zapis snapshotu i metadanych zakończył się przed
  niszczeniem potrzebnych serwisów i ewentualnego `DataSource`. Sprawdzić kolejność względem
  zatrzymania serwera HTTP, również podczas zamknięcia w trakcie startu gry.
- W `application.yaml` ustawić `spring.lifecycle.timeout-per-shutdown-phase: 6m`, zgodnie
  z `design.md`. To konfiguracja cyklu życia Springa, a nie ustawienie profilu gry.
- Callback wykonać dokładnie raz również po błędzie, przerwaniu i braku procesu.
- `ServerStateMachine.close()` ma idempotentnie zwalniać kolejkę i workery po zakończeniu
  orkiestracji; nie uruchamia ponownie bezwarunkowego `destroy()` i nie ustawia fałszywego
  `STOPPED`. Obsłużyć także przypadek niepełnego startu kontekstu, gdy `SmartLifecycle` nie zadziałał.
- Nigdy nie blokować kolejki zdarzeń oczekiwaniem na jej własny wynik. Callbacki Springa nie
  przechowują niezależnej kopii procesu.

Ścieżka nieudanego shutdownu zachowuje proces i token zgodnie z AD-017.
Kill-on-close jest wyłączany po udanym tworzeniu; wyjście JVM nie wymusza zakończenia serwera.

## 10. Kolejność realizacji i kryteria etapów

1. **Uzgodnienia:** rozstrzygnąć §2.1–2.2, zatwierdzić semantykę Kill; zapisać zaakceptowane
   decyzje. Zweryfikować pliki świata i rzeczywisty zapis przy CTRL+C na kopii instalacji.
2. **Kontekst i snapshoty:** przekazywanie kontekstu przy starcie, poprawny token tożsamości,
   strumieniowe kopie, publikacja, metadane i retencja. Kryterium: brak snapshotu blokuje Stop,
   a potwierdzony Kill kontynuuje po niepowodzeniu próby kopii; zmiana profilu nie zmienia świata kopiowanego dla procesu.
3. **CTRL+C:** FFM, granica sygnału, bean na żądanie i osobny JAR runnera (AD-020). Kryterium:
   helper omija Springa, nie narusza konsoli panelu i raportuje wysłanie zdarzenia.
4. **Runtime:** `STOPPING`/`STOP_FAILED`, deadline, retry, polityka Kill, korelacja zdarzeń,
   potwierdzanie wyjścia i anulowanie. Kryterium: deterministyczne testy wszystkich przejść.
5. **Aplikacja i UI:** endpointy, wyjątki, potwierdzenie, status i odświeżanie. Kryterium:
   pełny ręczny przebieg Stop/Kill oraz odrzucenie żądań ze starego uruchomienia.
6. **Shutdown:** `SmartLifecycle`, fazy, callback, zasoby i uzgodniona obsługa joba. Kryterium:
   zwykłe zamknięcie używa drabiny, a timeout nie jest raportowany jako łagodny sukces.
7. **Weryfikacja i dokumentacja:** testy reactora, pakowanie, próby na Windows/Valheim oraz
   aktualizacja dokumentów i wygenerowanych diagramów. Dopiero wtedy feature jest zakończony.

Sam helper lub endpoint bez wymaganego snapshotu nie jest gotową funkcją Stop. Gotowa funkcja
obejmuje cały powyższy zakres; nie zapewnia automatycznego Kill ani potwierdzenia zapisu przez panel.

## 11. Testy i weryfikacja

### 11.1 Testy jednostkowe

Każda nowa lub zmieniona klasa z zachowaniem ma test w swoim pakiecie. Stosować `underTest`,
świeże przygotowanie, separatory sekcji, angielski Javadoc, nazwy `should..._when...`,
GIVEN/WHEN/THEN i AssertJ. Pasywne typy korzystają tylko z wyjątków reguł testowych.
Zegar monotoniczny, scheduler, `ProcessHandle` i Win32 zastępować kontrolowanymi granicami,
bez statycznego mockowania, snu, realnych procesów i wielominutowego oczekiwania.

Najważniejsze przypadki:

- Runtime: poprawne Stop/Kill; `null`; niedozwolone stany; dwukrotne żądanie; Start podczas
  zatrzymania; wyjście w każdym etapie; naturalne wyjście podczas startu; shutdown w `STARTING`;
  deadline; dokładnie jeden retry; ponowienie po `STOP_FAILED`; odrzucenie spóźnionego workera.
- Korelacja: prawidłowy `onExit()` po zwiększeniu `operationId`; stary exit po nowym starcie;
  stary timer; profil zmieniony po starcie; stare potwierdzenie; nowa operacja nie dziedziczy zgody.
- Polityka: brak snapshotu; `BEST_EFFORT` z ręczną zgodą i bez niej; snapshot innej operacji
  lub uruchomienia; brak automatycznej zgody przy shutdownie; `destroyForcibly()` zwraca `false`,
  rzuca albo zwraca `true` bez wyjścia;
  brak tożsamości i niezgodny czas/plik procesu. Brak potwierdzenia, tożsamości lub czasu zabrania Kill; błąd snapshotu potwierdzonego Kill nie blokuje wymuszenia.
- Snapshoty (`@TempDir`): brak wymaganych plików; zmiany dowolnego pliku lub składu zestawu;
  retry całego zestawu; błędne metadane; brak miejsca; brak atomowego move; anulowanie;
  sprzątanie częściowej kopii; SHA-256; retencja chroniąca ostatnią właściwą kopię; symlinki.
- Helper: poprawne argumenty i błędny PID; pominięcie Springa; kolejność wywołań Win32;
  błąd każdego wywołania; exit/timeout; zwalnianie zasobów; jar i classpath; bezsekretne logi.
- Serwis/controller/advice: walidacja potwierdzenia, właściwe wyjątki i flash, brak sekretów;
  odróżnienie przyjęcia od zakończenia; asynchroniczny błąd widoczny w statusie.
- Koordynator shutdownu: brak procesu, aktywny Stop, błąd, timeout, dokładnie jeden callback,
  prawidłowa kolejność zamykania i idempotentne zwalnianie zasobów.

### 11.2 Integracja i próby ręczne

Testy `*IntegrationTest` potwierdzają MVC/Thymeleaf/Security: anonimowego klienta, brak `ADMIN`,
brak CSRF, konto z wymuszoną zmianą hasła, GET bez skutków ubocznych, walidację i przekierowania.
Przetestować wiring shutdownu z kontrolowanym runtime i tymczasowymi danymi.
Nie uruchamiać rzeczywistego Valheim ani helpera OS w automatycznym zestawie integracyjnym.

Osobna ręczna weryfikacja na Windows 11/Java 25 i kopii świata obejmuje start, Stop,
ponowny start, nieskuteczny CTRL+C, potwierdzony Kill, normalne zamknięcie panelu i awarię panelu.
Sprawdzić wysłanie CTRL+C bez wpływu na panel i terminal oraz ponowne załadowanie zapisanego
świata. Osobno sprawdzić działanie z jar, IDE/`spring-boot:run`, ze spacjami i polskimi znakami
w ścieżkach. Testów niszczących nie wykonywać na świecie użytkownika.

Sprawdzić odmowę Kill bez ręcznego potwierdzenia i próbę snapshotu przed wymuszeniem.
Brak `.db` i brak miejsca nie mogą blokować potwierdzonego Kill; Stop pozostaje
zablokowany przed sygnałem. Te próby wykonywać wyłącznie na kopii instalacji i świata.
Wyniki i ograniczenia trwałości snapshotów zapisać.
UI sprawdzić na telefonie/tablecie w obu orientacjach, przy 320 px, powiększeniu i bez JS.

Polecenia weryfikacji po implementacji:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Przy zmianach POM dodatkowo sprawdzić efektywny model. Spring i jego testy pozostają wyłącznie
w `vdeck-app`, a runtime i dodany zgodnie z AD-020 moduł `vdeck-runner` pozostają czystą Javą.

## 12. Dokumentacja i Definition of Done

W tej samej zmianie co implementacja:

- Uaktualnić `design.md` w zakresie faktycznie dodanych kontraktów, drzewa pakietów, etapów,
  stanów i konfiguracji, zachowując zaakceptowane decyzje i oznaczenie planowanych obszarów.
- Uaktualnić opis stanu projektu w `CLAUDE.md`, endpointy, ograniczenia i sposób uruchamiania.
- Rozwinąć `dokumentacja/dokumentacja-projektowa.md`: osobny opis zatrzymania, polityka Kill,
  snapshoty, konfiguracja, shutdown, błędy i ograniczenia ochrony danych; poprawić spis treści
  oraz indeks diagramów.
- Zmienić diagramy `server/klasy-komponenty.mmd` i `server/przeplyw-stany-serwera.mmd` oraz dodać
  przepływy zatrzymania, snapshotu i shutdownu u właściwego właściciela. Regenerować osadzone SVG
  przez mermaid-cli z `dokumentacja/diagramy/mermaid-config.json`; nie wklejać Mermaid do dokumentu.
- Udokumentować publiczne kontrakty, zasoby, asynchroniczność, deadline i warianty błędów
  w angielskim Javadoc; frontend otrzymuje wymagane angielskie komentarze/JSDoc.

Warunki odbioru: bez snapshotu Stop nie wysyła sygnału; potwierdzony Kill próbuje kopii i wymusza także po jej błędzie; Stop nie uzyskuje domyślnie ręcznej
zgody na Kill; Kill weryfikuje zgodę i tożsamość; `STOPPED` oznacza zaobserwowane wyjście;
`STOP_FAILED` z żywym procesem blokuje Start; stare zdarzenia nie zmieniają nowego uruchomienia;
HTTP i kolejka nie czekają na długie I/O; shutdown spełnia uzgodnienie joba; sekrety nie trafiają
do statusu, helpera i logów; testy, próby Windows i dokumentacja mają znany wynik.


## 13. Wynik implementacji i dalsza weryfikacja

Testy runtime sterują czasem, procesami, native API i workerami; nie kończą rzeczywistych procesów.
Testy MVC obejmują ADMIN, CSRF, stronę potwierdzenia bez skutków ubocznych, brak potwierdzenia,
stary launchId i szybkie zakończenie HTTP przed końcem operacji. Snapshoty mają fixture pary plików
i katalogu świata; nie ma jeszcze prób na zapisach pochodzących z działającego Valheim.
Wymagane przed użyciem produkcyjnym: próba CTRL+C na dedykowanym serwerze Windows, sprawdzenie
ukończenia zapisu, próba ręcznego Kill i restart panelu z żywym serwerem. RCON nie uczestniczy
w żadnej z tych operacji. Lista w §11.2 jest checklistą prób wdrożeniowych, nie wynikiem testów.

Weryfikacja po zmianie semantyki Kill: reactor package zakończony BUILD SUCCESS, 833 testy bez błędów
(239 runtime, 594 app). Testy potwierdzają wymuszenie po udanym snapshotcie, po braku `.db`
i po błędzie backupu, bez CTRL+C; odmowę po anulowaniu, deadline i zmianie tożsamości oraz
zachowanie zwykłego Stop bez wymuszenia. Rzeczywisty proces Valheim nie był kończony przez te testy.
Headless Chromium: widok potwierdzenia Kill w 5 rozmiarach telefonu/tabletu/desktopu, bez
przewijania całej strony, z dostępnym przyciskiem i jedną wymaganą zgodą. Ostrzeżenie opisuje
zakończenie także bez kopii. Wcześniejsza weryfikacja implementacji obejmowała 3 widoki,
15 wariantów rozmiaru i zachowanie fokusu przy pollingu.
