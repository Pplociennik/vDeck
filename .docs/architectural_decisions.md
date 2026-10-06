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
