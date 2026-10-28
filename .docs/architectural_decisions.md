# Krytyczne decyzje architektoniczne

> Status: nadrzędny rejestr decyzji projektu vDeck

Rejestr zawiera wyłącznie decyzje krytyczne i trwałe. Wpisy są niezmienne — decyzję zmienia się
przez dodanie nowej i oznaczenie starej jako wycofanej. Szczegóły architektury opisuje
`.docs/design.md`, reguły pracy — `.docs/rules/`.

## AD-001 — Priorytet wydajności i zużycia RAM

- **Status:** obowiązująca (2026-08-17)
- **Decyzja:** vDeck traktuje zużycie RAM oraz złożoność pamięciową i obliczeniową jako nadrzędne kryterium wyboru rozwiązań.
- **Powód:** Aplikacja ma działać płynnie na słabym sprzęcie, obok serwera gry zajmującego większość zasobów maszyny.
- **Konsekwencja:** Zabronione są struktury rosnące bez ograniczenia — logi i wyjście procesów przetwarzamy strumieniowo, w buforach o stałym rozmiarze.

## AD-002 — Rozdział kanałów konfiguracji

- **Status:** obowiązująca (2026-08-17)
- **Decyzja:** Ustawienia edytowalne przez użytkownika trzymamy w zewnętrznym `vDeck.properties` w katalogu instalacji, a `application.yaml` obejmuje wyłącznie infrastrukturę Spring Boot.
- **Powód:** Plik w `src/main/resources` jest zamknięty w jarze i nie może być edytowany po instalacji.
- **Konsekwencja:** Ustawienia aplikacyjne opisuje enum implementujący `SystemPropertyIf`, a nie `application.yaml`.

## AD-003 — Rozdział pakietów uwierzytelniania i infrastruktury bezpieczeństwa

- **Status:** wycofana przez AD-005 (2026-08-18)
- **Decyzja:** Feature kont użytkowników i zmiany hasła należy do pakietu
  `com.github.pplociennik.vdeck.authentication`. Jego model, w tym encje JPA, znajduje się w
  `authentication.domain`, DTO warstwy webowej w `authentication.dto`, a repozytoria w
  `authentication.persistence`. Przekrojowa konfiguracja i filtry Spring Security należą do
  głównego pakietu `com.github.pplociennik.vdeck.security`. Inicjalizatory danych uruchamiane przy
  starcie aplikacji, takie jak `DataInitializer`, należą do głównego pakietu
  `com.github.pplociennik.vdeck.config`.
- **Powód:** Nazwa `security` opisuje techniczną infrastrukturę zabezpieczeń, a `authentication`
  właściciela funkcji użytkownika i jego danych. Jawne wskazanie pełnych pakietów usuwa
  niejednoznaczność między podpakietem feature'a a pakietem głównym.
- **Konsekwencja:** Encje JPA nie muszą znajdować się w podpakiecie `persistence`; dla feature'ów z
  jawnym modelem domenowym umieszczamy je w `domain`, natomiast interfejsy Spring Data pozostają w
  `persistence`. Ta decyzja zastępuje sprzeczne wskazania z `.docs/design.md`.

## AD-004 — Minimalna widoczność i kontrakty serwisów

- **Status:** obowiązująca (2026-08-18)
- **Decyzja:** Każdy serwis aplikacyjny udostępnia interfejs w podpakiecie `service`, a jego
  implementacja znajduje się w `service.impl`. Konsumenci wstrzykują interfejs, nigdy konkretną
  implementację. Typy i elementy otrzymują najmniejszą widoczność wystarczającą do działania;
  w szczególności komponenty Springa i ich konstruktory pozostają package-private, gdy framework i
  granice pakietów nie wymagają `public`.
- **Powód:** Interfejs jest jawnym kontraktem między konsumentem i serwisem, a ograniczona widoczność
  zapobiega przypadkowemu rozszerzaniu API feature'a.
- **Konsekwencja:** Nazwa podpakietu implementacji to dokładnie `impl`. Widoczność `public` wymaga
  rzeczywistego konsumenta spoza pakietu albo wymagania języka/frameworka; prywatne szczegóły
  pozostają `private`.

## AD-005 — Wspólny pakiet nadrzędny dla feature'ów

- **Status:** obowiązująca (2026-08-18)
- **Decyzja:** Wszystkie pionowe funkcjonalności aplikacji znajdują się pod pakietem
  `com.github.pplociennik.vdeck.feature`, po jednej funkcjonalności w
  `com.github.pplociennik.vdeck.feature.<nazwa>`. Feature uwierzytelniania należy zatem do
  `com.github.pplociennik.vdeck.feature.authentication`. Pakiety przekrojowej infrastruktury, takie
  jak `config`, `security`, `persistence` i `shared`, pozostają bezpośrednio pod pakietem bazowym.
- **Powód:** Wspólny korzeń odróżnia kompletne pionowe funkcjonalności od technicznej infrastruktury
  aplikacji i usuwa niejednoznaczność nazw takich jak `authentication` oraz `security`.
- **Konsekwencja:** Nie tworzymy nowych feature'ów bezpośrednio pod
  `com.github.pplociennik.vdeck`. Pakiet `feature` zawiera wyłącznie pionowe funkcjonalności, nigdy
  wspólne narzędzia ani konfigurację. AD-005 zastępuje lokalizację `authentication` ustaloną w
  AD-003; pozostałe rozstrzygnięcia AD-003 dotyczące `domain`, `dto`, `persistence`, głównego
  `security` i głównego `config` pozostają w mocy.

## AD-006 — Centralny pakiet konfiguracji Spring

- **Status:** obowiązująca (2026-08-18)
- **Decyzja:** Wszystkie klasy oznaczone `@Configuration`, niezależnie od konfigurowanego obszaru,
  należą do głównego pakietu `com.github.pplociennik.vdeck.config`. Klasa startowa
  `VdeckApplication` pozostaje w pakiecie bazowym, ponieważ wyznacza korzeń skanowania komponentów i
  nie jest traktowana jako konfiguracja konkretnego obszaru.
- **Powód:** Jeden jawny composition root ułatwia odnalezienie sposobu składania aplikacji i
  zapobiega rozproszeniu konfiguracji Springa między feature'ami oraz pakietami infrastruktury.
- **Konsekwencja:** `SecurityConfiguration`, `PathConfiguration` i
  `SystemPropertiesConfiguration` znajdują się w głównym `config`. Konfiguracja może znać konkretną
  implementację tworzonego beana, ale wszyscy konsumenci serwisu wstrzykują jego interfejs. Typ
  implementacji lub technicznego komponentu otrzymuje `public` tylko wtedy, gdy centralna
  konfiguracja musi utworzyć go z innego pakietu. AD-006 zastępuje wcześniejszą lokalizację
  `SecurityConfiguration` w głównym `security`.

## AD-007 — Współdzielony mechanizm właściwości systemowych

- **Status:** obowiązująca (2026-08-18)
- **Decyzja:** Typy opisujące i obsługujące właściwości systemowe należą do głównego pakietu
  `shared`: `SystemPropertyIf` i `VdeckProperty` znajdują się bezpośrednio w `shared`, kontrakt
  `SystemPropertiesService` w `shared.service`, a implementacja w `shared.service.impl`.
  `SystemPropertiesConfiguration` pozostaje w głównym `config`, ponieważ jest klasą Spring
  `@Configuration`.
- **Powód:** Mechanizm pliku właściwości i bezpiecznego dostępu do wartości jest współdzieloną
  infrastrukturą, a nie konfiguracją Springa ani własnością pojedynczego feature'a.
- **Konsekwencja:** Pakiet `config` składa beany i inicjalizuje aplikację, ale nie jest właścicielem
  modeli ani serwisów właściwości systemowych. Konsumenci wstrzykują `SystemPropertiesService` przez
  interfejs z `shared.service`.

## AD-008 — Zewnętrzny katalog tłumaczeń

- **Status:** wycofana przez AD-009 (2026-08-19)
- **Decyzja:** Teksty interfejsu, domyślnie polskie, pochodzą z plików `messages*.properties`
  ładowanych przez `MessageSource` z katalogu z właściwości `vdeck.translations-directory`, domyślnie
  `lang` w katalogu uruchomienia.
- **Powód:** Tłumaczeń zamkniętych w jarze nie da się poprawić bez nowej wersji.
- **Konsekwencja:** Szablony i kod nie zawierają tekstów wpisanych na stałe.

## AD-009 — Tłumaczenia jako resource bundle w aplikacji

- **Status:** obowiązująca (2026-08-19)
- **Decyzja:** Panel ma jeden język — polski — i nie przewiduje jego zmiany. Teksty interfejsu
  pochodzą z pojedynczego pliku `messages.properties` w `vdeck-app/src/main/resources`, czytanego
  przez domyślny `MessageSource` Spring Boota. Nie istnieje zewnętrzny katalog tłumaczeń, właściwość
  `vdeck.translations-directory`, własna klasa `@Configuration` dla `MessageSource` ani inicjalizator
  kopiujący plik przy starcie. `application.yaml` ustawia jedynie
  `spring.messages.use-code-as-default-message`, dzięki czemu brak klucza renderuje się jako sam
  klucz zamiast przerywać żądanie.
- **Powód:** Zewnętrzny katalog, kopiowanie pliku przy starcie i łączenie dwóch źródeł tekstu to
  koszt utrzymania i ryzyko rozjazdu kluczy, ponoszone wyłącznie dla nieplanowanej wielojęzyczności
  i ręcznej edycji tekstów. Zgodnie z YAGNI i KISS ta możliwość nie jest dziś potrzebna.
- **Konsekwencja:** AD-009 zastępuje AD-008 w całości w zakresie źródła tekstów. Utrzymane zostają
  pozostałe reguły tekstów interfejsu: szablony pobierają tekst wyłącznie przez `#{klucz}`, kod Javy
  przekazuje klucz, a zamknięte zbiory komunikatów opisują typy wyliczeniowe implementujące
  `TranslationKeyIf`. Zmiana tekstu wymaga nowego wydania panelu.

## AD-010 — Katalog plików ustawień `.vDeck/config`

- **Status:** obowiązująca (2026-09-21)
- **Decyzja:** Wszystkie edytowalne przez użytkownika pliki `.properties` panelu — `vDeck.properties`,
  `valheim-server.properties` i każdy przyszły plik ustawień — znajdują się w podkatalogu
  `.vDeck/config` katalogu instalacji. Żaden plik ustawień nie leży bezpośrednio obok `vdeck.jar`.
  Lokalizację pliku wyznacza wyłącznie `PathService.getConfigurationFile( nazwaPliku )`, które
  przyjmuje samą nazwę pliku i rozwiązuje ją w `PathService.CONFIGURATION_DIRECTORY`.
- **Powód:** Katalog instalacji dzieli pliki panelu z plikami serwera Valheim. Jeden wydzielony katalog
  konfiguracji oddziela ustawienia od plików wykonywalnych i danych, upraszcza ich kopiowanie i
  zapobiega rozproszeniu kolejnych plików ustawień w katalogu instalacji.
- **Konsekwencja:** AD-002 pozostaje w mocy — pliki są nadal zewnętrzne i leżą w katalogu instalacji;
  AD-010 ustala jedynie ich podkatalog. `SystemPropertiesServiceImpl` tworzy brakujący katalog razem
  z plikiem. Plik `vDeck.properties` pozostawiony w katalogu instalacji przez wcześniejszą wersję
  deweloperską nie jest migrowany ani czytany.

## AD-011 — Profil uruchomieniowy w pliku ustawień

- **Status:** wycofana (2026-09-22, zastąpiona przez AD-013)
- **Decyzja:** Profil uruchomieniowy serwera Valheim — opcje wiersza poleceń `valheim_server.exe`
  opisane w instrukcji serwera dedykowanego — jest przechowywany w pliku
  `.vDeck/config/valheim-server.properties`, obsługiwanym przez ten sam mechanizm co `vDeck.properties`
  (AD-002, AD-007): enum `LaunchProperty` implementujący `SystemPropertyIf` i osobny bean
  `SystemPropertiesService`. Feature `feature.launch` nie ma encji JPA ani repozytorium. Profil jest
  edytowany na dwóch ekranach: „Serwer” (`/server-settings` — tożsamość serwera, dostęp, świat i zapis,
  kopie automatyczne) oraz „Ustawienia gry” (`/game-settings` — zestaw ustawień świata, modyfikatory i
  klucze świata); każdy ekran zapisuje wyłącznie własne ustawienia i nie zmienia ustawień drugiego.
- **Powód:** Profil jest konfiguracją edytowalną przez użytkownika, a nie danymi domenowymi panelu;
  plik tekstowy można przejrzeć, skopiować i poprawić ręcznie bez narzędzi bazodanowych.
- **Konsekwencja:** Ustawienie może być obowiązkowe albo opcjonalne (`SystemPropertyIf.isMandatory()`);
  pusta wartość opcjonalnego ustawienia oznacza, że opcja nie jest przekazywana serwerowi. Opcja
  `-logFile` nie należy do profilu — panel wyznacza ją z `game.log.directory`. Hasło serwera jest
  przechowywane jawnym tekstem, bo serwer wymaga go w wierszu poleceń; chronią je wyłącznie
  uprawnienia konta systemowego do katalogu instalacji, a interfejs nigdy go nie wyświetla.

## AD-012 — Wzorzec wyglądu i układu paneli

- **Status:** obowiązująca (2026-09-21)
- **Decyzja:** Każdy panel interfejsu stosuje wzorzec ustalony dla ekranów ustawień „Serwer” i
  „Ustawienia gry”:
  - **Rama o stałej wysokości.** Panel jest osadzony w ramie `.panel-frame` o wysokości
    `--panel-frame-block-size` (obecnie `70dvh`), wyśrodkowanej w miejscu pod wyspą akcji, tak że nad
    panelem i pod nim zostaje wolne miejsce. Karta wypełnia ramę i przewija treść wewnątrz, z cienkim
    paskiem przewijania w kolorze akcentu; strona nigdy się nie przewija. Gdy widok jest niższy niż
    rama, rama kurczy się do dostępnego miejsca.
  - **Tekstura.** Domyślną teksturą panelu jest `config-background.jpg` (`--panel-texture`). Własną
    teksturę zachowują wyłącznie ekrany hasła (`.surface--password`), wyspa i menu boczne.
  - **Sekcje.** Pola są pogrupowane w tytułowane sekcje (`fieldset.form-section`); tytuł to
    przygaszony tekst na środku poziomej linii, jak w menu bocznym, a sekcje dzieli większy odstęp niż
    pola wewnątrz nich.
  - **Opis każdego pola.** Pod każdym polem stoi opis powiązany przez `aria-describedby`: znaczenie
    ustawienia, jego **dozwolone albo możliwe wartości** (zakres albo format) oraz wartość domyślna.
    Wyjątkiem jest lista rozwijana: jej opis nie wymienia możliwych wartości, bo pokazuje je sama
    lista, ani wartości domyślnej, gdy lista oznacza ją jako domyślną. Opis pola wyboru (checkbox) nie
    podaje wartości ani wartości domyślnej — mówi tylko, co oznacza zaznaczenie. Opcje list wyboru pokazują nazwę polską i wartość techniczną, np. „Trudno (hard)”.
  - **Układ responsywny.** Na telefonie i każdym ekranie pionowym sekcje układają się w jednej kolumnie.
    Na ekranie poziomym karta się rozszerza: od 60 rem szerokości do 76 rem, od 96 rem do 118 rem.
    Formularz z kolumnami sekcji (`.settings-form--columns`) stawia wtedy dwie kolumny obok siebie, a
    każda kolumna układa swoje sekcje jedna pod drugą; pola każdej sekcji rozkładają się na tyle kolumn,
    ile się zmieści, nie węższych niż czytelne minimum.
  - **Sekcja zaawansowana.** Ustawienia rzadko zmieniane albo wymagające znajomości gry trafiają do
    sekcji „Zaawansowane” pod ustawieniami podstawowymi. Sekcja jest domyślnie ukryta; pokazuje ją
    wyśrodkowane pole wyboru „Konfiguracja zaawansowana” nad nią. Stan pola jest zapamiętywany w
    `vDeck.properties` od razu po kliknięciu, osobnym żądaniem, niezależnie od zapisu formularza, więc
    obowiązuje przy każdej kolejnej wizycie. Ukrycie sekcji niczego nie wyłącza: jej pola są nadal
    wysyłane, a zapisane wartości nadal obowiązują.
  - **Komunikat i akcja.** Komunikat wyniku (`role="status"` albo `role="alert"`) i przycisk
    zapisu wraz z towarzyszącą mu notą są wyśrodkowane w poziomie; przycisk ma stałą, wygodną szerokość
    zamiast rozciągać się na całą kartę. Zapis kończy się przekierowaniem z potwierdzeniem, a
    odrzucenie zachowuje wpisane wartości — poza sekretami.
- **Powód:** Wspólna wysokość i położenie sprawiają, że przechodzenie między panelami nie zmienia
  układu strony. Opis z dozwolonymi wartościami przy każdym polu pozwala skonfigurować serwer bez
  sięgania do instrukcji, a układ kolumnowy wykorzystuje szerokie ekrany zamiast wymuszać długie
  przewijanie. Ukrycie ustawień zaawansowanych skraca panel dla administratora, który ich nie
  potrzebuje, a zapamiętany wybór oszczędza mu powtarzania go przy każdej wizycie.
- **Konsekwencja:** Nowy panel korzysta z `.panel-frame`, `.view-card` oraz klas formularza i ustawień
  z `base.css` (`.view-card--wide`, `.settings-form`, `.settings-column`, `.settings-fields`,
  `.settings-message`, `.settings-footer`, `.settings-advanced-toggle`) i nie wprowadza własnego
  sposobu przewijania, wysokości ani stylu sekcji. Reguły te trafiły do `base.css`, gdy wzorzec
  przejął drugi panel; nie są kopiowane do arkuszy widoków. Każda sekcja zaawansowana ma własne
  ustawienie widoczności w `vDeck.properties`. Ekrany hasła stosują ramę, zachowują własną teksturę i
  jako krótkie formularze jednokolumnowe nie wymagają układu kolumnowego.

## AD-013 — Jeden serwis plików ustawień

- **Status:** obowiązująca (2026-09-22)
- **Zastępuje:** AD-011
- **Decyzja:** Jeden bean `SystemPropertiesService` z wstrzykniętym `PathService` obsługuje wszystkie pliki ustawień, przyjmując nazwę pliku w każdej operacji.
- **Powód:** Osobny bean na plik wymagał kwalifikatora przy każdym konsumencie.
- **Konsekwencja:** Nie tworzymy beanów per plik — pliki ładuje przy starcie `SystemPropertiesInitializer`; reszta AD-011 obowiązuje.

## AD-014 — Wyjątki procesów i ControllerAdvice

- **Status:** obowiązująca (2026-09-22)
- **Decyzja:** Odrzucenie lub niepowodzenie głównego procesu kończy wyjątek z `exc` feature'a obsługiwany przez jego `@ControllerAdvice`; resztę loguje na `WARN` `shared.web.DefaultExceptionHandler`.
- **Powód:** Obiekty wyniku i lokalne `catch` rozpraszały obsługę błędów.
- **Konsekwencja:** Serwisy i kontrolery nie zwracają wyniku odrzucenia, a wyjątek łapią tylko, by zamienić go na wyjątek procesu.

## AD-015 — Bezpośredni start i skrypt lustrzany

- **Status:** obowiązująca (2026-09-29)
- **Decyzja:** Panel uruchamia `valheim_server.exe` przez FFM `CreateProcessW` z własnym środowiskiem, a `.vDeck/vDeck-server-start.bat` generuje bez sekretu.
- **Powód:** Uruchomienie nie zależy od interpretacji skryptu przez powłokę.
- **Konsekwencja:** Skrypt lustrzany nie jest kanałem startu ani elementem GUI, a skrypt z `server.start.script` jest wyłącznie czytany po `SteamAppId`.

## AD-016 — Układ i retencja logów gry

- **Status:** obowiązująca (2026-09-29)
- **Decyzja:** Każdy start zapisuje przez `-logFile` osobny log w `<game.log.directory>/<rrrr-MM-dd>/<rrrr-MM-dd_HH-mm-ss>.log`.
- **Powód:** Historia uruchomień nie ginie przy ponownym starcie.
- **Konsekwencja:** Panel nie przechwytuje konsoli, domyślny katalog to `.vDeck/log`, a dzienną retencją steruje `game.log.retention.days` z wartością domyślną 14; `game.log.retention.enabled=false` (domyślnie `true`) wyłącza retencję i zachowuje wszystkie logi.

## AD-017 — Nieudany Stop pozostawia serwer uruchomiony

- **Status:** obowiązująca (2026-10-07), zaakceptowana przez użytkownika.
- **Decyzja:** Niepowodzenie łagodnego zatrzymania, także podczas zamykania panelu, nie upoważnia
  do wymuszonego zakończenia serwera. `STOP_FAILED` zachowuje żywy proces. Wymuszenie wymaga
  osobnej, ręcznie potwierdzonej akcji Kill dla konkretnego uruchomienia. Kill próbuje wykonać
  snapshot, a następnie od razu wymusza zakończenie procesu, niezależnie od powodzenia kopii
  (zmiana zaakceptowana 2026-10-07). Kill nie wysyła CTRL+C, nie czeka na próby łagodne i nie
  wymaga dodatkowej zgody na brak backupu. Potwierdzenie obejmuje ryzyko utraty danych także
  bez kopii; dotyczy jednej operacji i nie przechodzi na Stop ani kolejne żądania. Deadline,
  anulowanie i zweryfikowana tożsamość procesu nadal obowiązują.
- **Powód:** Zakończenie panelu i upływ timeoutu nie są zgodą na utratę niezapisanego postępu.
- **Konsekwencja:** Job Object nie może zabijać uruchomionego serwera przy wyjściu panelu.
  Serwer może przeżyć panel; przed nowym startem należy rozpoznać pozostałe procesy tej instalacji
  i umożliwić ich jawne odzyskanie. Sprzątanie nieudanego, nieprzekazanego startu pozostaje osobną
  odpowiedzialnością launchera. Nie wolno raportować `STOPPED` bez zaobserwowania wyjścia procesu.

## AD-018 — Katalog snapshotów wewnątrz `.vDeck`

- **Status:** obowiązująca (2026-10-07, zmieniona 2026-10-26), zaakceptowana przez użytkownika.
- **Decyzja:** Snapshoty trafiają do `<instalacja>/.vDeck/<snapshot.directory>`. Nazwę jednego
  podkatalogu `.vDeck` podaje `snapshot.directory` w `vDeck.properties` (domyślnie `shutdown-snapshots`).
  Lokalizację obsługuje osobny resolver `BackupPathService` należący do `feature.backup`.
  Wcześniejsza lokalizacja `<rodzic instalacji>/.vDeck-backups/<nazwa instalacji>` została wycofana.
- **Powód:** Każdy katalog tworzony przez vDeck musi być podkatalogiem `.vDeck`; panel nie tworzy
  niczego obok instalacji.
- **Konsekwencja:** Ścieżka przechodzi przez `PathService.getAbsolutePath()`, a resolver odrzuca
  wartość, która nie jest pojedynczym katalogiem bezpośrednio pod `.vDeck`, nakładanie się z
  aktywnym zapisem świata oraz dowiązania i punkty reparsowania od `.vDeck` w dół. Katalog jest
  rozwiązywany przy starcie serwera, zapisywany w tokenie `server.pid` i odczytywany przy adopcji,
  więc późniejsza zmiana ustawienia nie przekierowuje kopii działającego serwera. Runtime odmawia
  snapshotu poza `.vDeck`. Brak dostępu przerywa snapshot; nie powoduje cichego wyboru innego
  katalogu. Kopie z dawnego `.vDeck-backups` nie są przenoszone automatycznie.

## AD-019 — Tylko ostatni kompletny snapshot

- **Status:** obowiązująca (2026-10-12), na podstawie decyzji użytkownika w planie
  `plans/server-stop-review-fixes.md` z 2026-10-07.
- **Decyzja:** Każda instalacja przechowuje ostatni kompletny snapshot w `snapshot-latest`.
  Nowy zestaw powstaje w `.tmp-<uuid>`. Dopiero po sprawdzeniu kopii poprzedni zestaw trafia
  atomowo do `.old-<uuid>`, a nowy na jego miejsce. Nieudana publikacja przywraca poprzedni zestaw.
- **Powód:** Limit 32 wpisów bez sprzątania uniemożliwiał kolejne łagodne zatrzymania serwera.
- **Konsekwencja:** Nie ma limitu liczby zatrzymań wynikającego z historii. Po publikacji poprzednia
  kopia jest usuwana; błąd sprzątania daje WARN. Następna operacja usuwa pozostałości po awarii,
  najpierw przywracając `.old-*`, jeżeli brakuje `snapshot-latest`. Limity 8 GiB i 8192 wpisów,
  wymóg wolnego miejsca na nowy zestaw oraz pozostałe zasady AD-017 i AD-018 pozostają w mocy.
  Katalogi `snapshot-<operationId>` wcześniejszej wersji nie są automatycznie usuwane.

## AD-020 — Helper CTRL+C uruchamiany na żądanie przez bean panelu

- **Status:** obowiązująca (2026-10-12), zaakceptowana przez użytkownika.
- **Decyzja:** Panel tworzy singleton `ConsoleControlSender` bez własnego wątku oczekującego.
  `JvmConsoleControlSender` uruchamia osobny proces dopiero na żądanie runtime. Punkt wejścia
  helpera należy do modułu `vdeck-runner`, bez Springa; `VdeckApplication.main()` uruchamia
  wyłącznie panel. Runner i panel zależą od runtime, runtime nie zależy od żadnego z nich.
- **Powód:** Izolacja procesowych ustawień konsoli pozostaje konieczna, ale uruchomienie panelu
  nie powinno znać protokołu helpera ani zależeć od logiki wysyłania CTRL+C.
- **Konsekwencja:** Build dostarcza osobny `vdeck-runner.jar`, instalowany jako
  `lib/vdeck-runner.jar` w katalogu instalacji wskazywanym przez PathService (AD-021). Konfiguracja Springa przekazuje gotowe polecenie beanowi, a runtime
  korzysta z `ConsoleControlSender`. Helper zachowuje weryfikację tożsamości i kody wyjścia 0/1/2.
  AD-017 pozostaje w mocy. Runner obecnie obsługuje tylko helper; uruchamianie panelu i updater
  bez podwyższania uprawnień są przyszłym zakresem, wymagającym osobnego planu.

## AD-021 — Dystrybucja ZIP z dołączonym JRE 25

- **Status:** obowiązująca (2026-10-12), zaakceptowana przez użytkownika; adres i port panelu oraz
  rola skryptu BAT zastąpione przez AD-024 i AD-026 (2026-10-27).
- **Decyzja:** Katalog `.dist` zawiera skrypt BAT, README w formatach Markdown i tekstowym oraz
  szablony `.vDeck/config/*.properties`. Końcowy moduł Maven `vdeck-dist` kopiuje do jego
  podkatalogu `lib` oba wykonywalne JAR-y przez Maven Resources i pakuje zawartość przez Maven Assembly do
  `target/vDeck-dist-${project.version}.zip` w głównym katalogu projektu.
- **Powód:** Jedna paczka ma zawierać komplet plików potrzebnych do uruchomienia panelu,
  bez ręcznego kopiowania helpera i instalowania Javy przez użytkownika.
- **Konsekwencja:** Paczka dla Windows x64 zawiera pełne Eclipse Temurin JRE 25 w `jre`, wraz
  z informacjami licencyjnymi. Build pobiera przypięte wydanie i sprawdza zapisaną SHA-256 przed
  rozpakowaniem. BAT używa wyłącznie dołączonej Javy, ustawia katalog roboczy na instalację
  i bierze adres nasłuchu ze zmiennej `ADDRESS` (domyślnie `127.0.0.1`), a port ze zmiennej `PORT`
  (domyślnie `8085`). Zmiana adresu na sieciowy jest świadomą decyzją użytkownika (panel używa HTTP).
  Ponieważ JAR-y leżą w `lib`, BAT zawsze przekazuje `vdeck.server-directory` z katalogiem
  instalacji; bezpośrednie `java -jar lib\vdeck.jar` bez tego nadpisania nie jest wspieranym
  sposobem uruchomienia. Helper używa tego samego JRE przez `java.home`.
  Generowane JAR-y i JRE nie są wersjonowane. Szablony zachowują domyślne ustawienia i puste
  hasło serwera; ZIP nie zawiera bazy, logów ani danych użytkownika. Valheim jest instalowany osobno.

## AD-022 — Instrukcja obsługi PDF zawsze aktualna

- **Status:** obowiązująca (2026-10-12), zaakceptowana przez użytkownika.
- **Decyzja:** Paczka dystrybucyjna zawiera polską instrukcję obsługi `vDeck-instrukcja.pdf`
  ze zrzutami ekranu i rysunkami. Źródłem jest
  `dokumentacja/instrukcja-uzytkownika/instrukcja-uzytkownika.adoc` (AsciiDoc), a moduł `vdeck-dist`
  generuje z niego PDF w każdym `package` przez Asciidoctor Maven Plugin i AsciidoctorJ PDF.
  **Instrukcja musi być zawsze aktualna:** każda zmiana zachowania widocznego dla użytkownika
  aktualizuje w tej samej zmianie jej treść, zrzuty ekranu i rysunki.
- **Powód:** Użytkownik końcowy dostaje jeden wygodny dokument razem z programem. Tekstowe źródło
  w repozytorium da się przeglądać w diffie i budować bez zewnętrznych narzędzi, a nieaktualna
  instrukcja wprowadzałaby użytkownika w błąd.
- **Konsekwencja:** Instrukcja opisuje tylko funkcje zaimplementowane; przyciski przyszłych funkcji
  oznacza jako niedostępne. Etykiety podaje dokładnie jak `messages.properties`. Ostrzeżenie
  Asciidoctora, np. brakujący obrazek, przerywa build. Zrzuty ekranu odtwarza ręcznie skrypt
  Playwright `zrzuty/zrzuty-ekranu.mjs` na świeżo wypakowanej paczce; rysunki pochodzą z plików
  `.mmd` w `rysunki/`, renderowanych przez mermaid-cli do PNG. Zmiana UI bez odświeżenia zrzutów
  albo zmiana zachowania bez poprawienia instrukcji jest niekompletna (`.docs/rules/documentation.md`).

## AD-023 — Wydania przez GitHub Actions

- **Status:** obowiązująca (2026-10-26)
- **Decyzja:** `.github/workflows/build.yml` na `windows-latest` testuje każdy push i PR, a push na `main` z wersją bez `-SNAPSHOT` i bez tagu `v<wersja>` publikuje Release z `vDeck-dist-<wersja>.zip`.
- **Powód:** Paczka ma powstawać powtarzalnie z przetestowanego `main`, nie lokalnie.
- **Konsekwencja:** Wydanie wymaga ręcznej zmiany wersji we wszystkich POM-ach; `-SNAPSHOT` nie trafia do Releases.

## AD-024 — Automatyczna aktualizacja przy starcie: launcher, updater i recovery

- **Status:** obowiązująca (2026-10-27), zaakceptowana przez użytkownika; deadline 5 minut
  wycofany (2026-10-27, zastąpiony przez AD-027).
- **Decyzja:** Skrypt startowy uruchamia wyłącznie launcher (`vdeck-launcher`, czysta Java). Przy
  każdym starcie launcher zdobywa bramkę `.vDeck/startup.lock` i blokadę `.vDeck/vdeck.lock`,
  a przy `update.enabled=true`, wersji wydania i zatrzymanym serwerze sprawdza najnowszy Release
  GitHuba. Nowszą paczkę pobiera przez HTTPS, weryfikuje SHA-256 z pola `digest` API,
  bezpiecznie rozpakowuje do `.vDeck/update/staged` i kopiuje JRE oraz updater do niezależnego
  `.vDeck/update/executor`. Podmianę wykonuje osobny moduł `vdeck-updater`, uruchomiony z executora,
  w dziennikowanej transakcji `apply.journal` (zamiar przed każdą operacją, atomowe rename,
  idempotentny rollback). Procesy przekazują sobie blokadę instalacji przez jednorazowy, potwierdzany
  deskryptor w `.vDeck/handoff`; panel przejmuje ją przed startem Springa. Istniejący dziennik
  sprawia, że skrypt uruchamia recovery z executora, także bez zainstalowanego `jre` i `lib`.
- **Powód:** Ręczna aktualizacja nadpisywała ustawienia użytkownika. Działający JVM nie może
  podmienić własnego JRE i JAR-ów w Windows, więc podmiana musi działać z osobnej kopii, a każde
  przerwanie musi dać się cofnąć bez instalowanego środowiska.
- **Konsekwencja:** Aktualizacja odbywa się tylko przy starcie, przy zatrzymanym panelu i serwerze,
  nigdy nie obniża wersji i nie wymaga uprawnień administratora. Błąd sprawdzania lub pobierania
  (deadline 5 minut, aktywne przerwanie zablokowanego odczytu) nie blokuje startu bieżącej wersji;
  start blokują tylko nieudane recovery, błędne ustawienia startu i zajęta instalacja. Oryginały
  ostatniej transakcji zostają w `.vDeck/update/backup` do następnej aktualizacji. Repozytorium
  ma włączone immutable releases, a workflow oznacza wydanie jako `--latest`. Każde uruchomienie
  launchera i updatera zapisuje własny log w `.vDeck/logs/launcher|updater/<dzień>/` (retencja 30 dni).
  Launcher czekający na panel działa z `-Xmx32m` (zmierzone ok. 60 MB pamięci roboczej),
  co jest świadomym kosztem wobec AD-001. Brak podpisu paczki: zaufanie opiera się na HTTPS,
  repozytorium zapisanym w kodzie i skrócie z API.

## AD-025 — Scalanie plików ustawień przy aktualizacji

- **Status:** obowiązująca (2026-10-27), zaakceptowana przez użytkownika.
- **Decyzja:** Updater scala każdy plik `.vDeck/config/*.properties` z szablonem nowej wersji:
  klucz obecny w obu zachowuje wartość użytkownika (także pustą), klucz tylko w szablonie dostaje
  wartość domyślną, a klucz nieobecny w szablonie znika. Kolejność i komentarze pochodzą z szablonu.
- **Powód:** Użytkownik nie może tracić konfiguracji, a pliki nie mogą gromadzić martwych kluczy.
- **Konsekwencja:** Szablony w `.dist/.vDeck/config` muszą zawierać dokładnie klucze i wartości
  domyślne enumów `VdeckProperty`, `LaunchProperty` oraz ustawień startu; pilnują tego testy.
  Wynik jest ASCII z sekwencjami `\uXXXX`, a log zawiera tylko nazwy kluczy, nigdy wartości.

## AD-026 — Ustawienia startu w `vDeck-start.properties`

- **Status:** obowiązująca (2026-10-27), zaakceptowana przez użytkownika.
- **Decyzja:** Adres (`panel.address`), port (`panel.port`) i włączenie automatycznych aktualizacji
  (`update.enabled`) leżą w `.vDeck/config/vDeck-start.properties`, czytanym przez launcher.
  `start-vDeck.bat` nie zawiera ustawień: sprawdza tylko istnienie dziennika aktualizacji
  i wywołuje launcher albo recovery w jednym, wcześniej sparsowanym bloku.
- **Powód:** Edytowany przez użytkownika BAT byłby nadpisywany przy aktualizacji, a jego zmiana
  w trakcie działania jest niebezpieczna dla `cmd.exe`.
- **Konsekwencja:** Kontrakt BAT-a (ścieżki dziennika i executora, tryb `--recover`) jest stały dla
  protokołu recovery 1; paczka z innym protokołem jest odrzucana przed podmianą. Błędna wartość
  ustawienia startu zatrzymuje start z komunikatem wskazującym klucz.

## AD-027 — Limit bezczynności pobierania aktualizacji

- **Status:** obowiązująca (2026-10-27)
- **Zastępuje:** AD-024 (deadline 5 minut)
- **Decyzja:** `ReleaseDownloader` przerywa pobieranie po 30 s bez danych i pokazuje postęp w konsoli, a sufit `UpdatePreparation.BUDGET` wynosi 60 minut.
- **Powód:** Stały limit 5 minut blokował aktualizację na wolnym łączu.
- **Konsekwencja:** Wolne, ale postępujące pobieranie trwa do 60 minut, a każde przerwanie uruchamia bieżącą wersję.

## AD-028 — Schemat bazy przez Liquibase

- **Status:** obowiązująca (2026-10-28)
- **Decyzja:** Schemat `vdeck.db` zmieniają tylko changesety Liquibase w XML, stosowane przez Spring Boot przy starcie, a Hibernate ma `ddl-auto: none`.
- **Powód:** `ddl-auto: update` nie przekształca ani nie usuwa kolumn i nie zostawia historii schematu.
- **Konsekwencja:** Zmiana encji wymaga changesetu w pliku XML bieżącej wersji, a plików wydanych wersji nie zmieniamy.
