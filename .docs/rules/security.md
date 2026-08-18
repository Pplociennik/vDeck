# Reguły bezpieczeństwa i logowania

> Status: obowiązujące dla całego repozytorium

vDeck zarządza procesem systemowym, plikami świata, kopiami bezpieczeństwa, poświadczeniami panelu
oraz opcjonalnym kanałem RCON. Fakt, że panel jest przeznaczony do lokalnego uruchomienia, nie znosi
wymagań bezpieczeństwa. Każda granica HTTP, systemu plików, procesu, bazy danych i zewnętrznego moda
jest traktowana jako granica zaufania.

## 1. Bezpieczne wartości domyślne

- Funkcja administracyjna jest domyślnie niedostępna bez uwierzytelnienia i autoryzacji.
- Początkowe hasło `admin` podlega wymuszonej zmianie przy pierwszym logowaniu zgodnie z głównymi
  założeniami projektowymi. Do czasu zmiany użytkownik nie może wykonywać pozostałych operacji
  administracyjnych.
- Hasło panelu ma od 12 do 128 znaków i zawiera co najmniej jedną cyfrę, jedną małą literę, jedną
  wielką literę oraz jeden znak specjalny, czyli znak niebędący literą, cyfrą ani białym znakiem.
  Reguła jest egzekwowana po stronie serwera; atrybuty formularza są wyłącznie ułatwieniem dla
  użytkownika.
- Nie wyłączamy CSRF dla wygody implementacji. Żądania zmieniające stan wymagają poprawnego tokenu.
- Nie otwieramy panelu na zewnętrzny interfejs sieciowy bez jawnej konfiguracji użytkownika.
- Błąd konfiguracji bezpieczeństwa powoduje bezpieczne przerwanie operacji lub startu, a nie
  uruchomienie w trybie mniej bezpiecznym.
- Uprawnienia procesu i plików ograniczamy do minimum potrzebnego do wykonania danej operacji.

## 2. Sekrety i dane wrażliwe

Za sekrety uznajemy w szczególności hasło użytkownika panelu, hasło serwera Valheim, dane dostępowe
RCON, identyfikatory sesji, tokeny CSRF, klucze oraz connection strings zawierające poświadczenia.

- Haseł użytkowników nie przechowujemy w postaci jawnej ani odwracalnie zaszyfrowanej. Utrwalamy
  wyłącznie wynik właściwego, adaptacyjnego algorytmu haszującego przeznaczonego do haseł.
- Sekret zapisujemy tylko wtedy, gdy jest niezbędny do działania funkcji. Ograniczamy jego czas życia,
  widoczność i dostęp do pliku lub rekordu, w którym się znajduje.
- Sekretów nie umieszczamy w kodzie źródłowym, test fixtures, dokumentacji, komentarzach, komunikatach
  wyjątków, nazwach plików ani przykładowych konfiguracjach przeznaczonych do commita.
- UI nie zwraca zapisanego sekretu do przeglądarki. Prezentuje co najwyżej informację, że wartość jest
  skonfigurowana, oraz pozwala ją zastąpić.
- Obiekty zawierające sekrety nie mogą ujawniać ich przez `toString()`, automatycznie generowane metody
  Lombok, serializację ani przypadkowe logowanie całego obiektu.
- Argumentów uruchomieniowych zawierających hasła nie logujemy i nie pokazujemy w całości. Widoki
  diagnostyczne oraz informacje o znalezionych procesach muszą redagować wartości sekretów.
- Sekretu nie przekazujemy w URL ani parametrze query, ponieważ trafia on do historii, logów i danych
  pośredników HTTP.

Jeżeli integracja techniczna wymaga przechowywania sekretu w formie możliwej do odczytania, projekt
feature'a musi jawnie opisać sposób ochrony danych w spoczynku, uprawnienia dostępu oraz zachowanie
podczas eksportu, backupu i usuwania konfiguracji.

## 3. Uwierzytelnianie, sesja i autoryzacja

- Hasła porównujemy wyłącznie za pomocą mechanizmu biblioteki bezpieczeństwa; nie implementujemy
  własnej kryptografii ani porównywania hashy.
- Logowanie ma kontrolę liczby kolejnych nieudanych prób lub opóźnienie utrudniające automatyczne
  zgadywanie hasła. Komunikat błędu nie ujawnia, która część poświadczeń była poprawna.
- Identyfikator sesji zmieniamy po pomyślnym uwierzytelnieniu. Cookie sesji jest `HttpOnly` i ma
  odpowiednią politykę `SameSite`; atrybut `Secure` jest obowiązkowy przy HTTPS.
- Ograniczenie do jednej aktywnej sesji jest egzekwowane po stronie serwera.
- Każdy endpoint zmieniający stan weryfikuje autoryzację po stronie serwera. Ukrycie przycisku,
  modal potwierdzający albo zabezpieczenie JavaScript nie stanowią autoryzacji.
- Operacje podwyższonego ryzyka, w tym force kill, restore, usunięcie danych i zmiana konfiguracji
  bezpieczeństwa, wymagają jawnego potwierdzenia zgodnego z ich kontraktem domenowym.
- Wylogowanie unieważnia sesję po stronie serwera, a zakończenie lub wygaśnięcie sesji nie pozostawia
  działających uprzywilejowanych żądań bez właściciela.

## 4. Walidacja danych, ścieżek i operacji procesowych

- Wszystkie dane pochodzące z HTTP, konfiguracji, logów Valheim, RCON, nazw plików i informacji o
  procesach są niezaufane do czasu walidacji.
- Walidacja opiera się na dozwolonym formacie, zakresie i długości. Samo usuwanie kilku znanych
  niebezpiecznych znaków nie jest wystarczające.
- Ścieżki normalizujemy i rozwiązujemy do postaci rzeczywistej przed operacją. Po normalizacji muszą
  nadal należeć do jawnie dozwolonego katalogu.
- Nie podążamy za nieoczekiwanymi dowiązaniami ani punktami reparsowania poza dozwolony katalog.
- Argumenty procesu przekazujemy jako oddzielne wartości właściwego API. Nie budujemy polecenia przez
  konkatenację tekstu i nie uruchamiamy go przez powłokę, jeżeli nie jest to bezwzględnie konieczne.
- PID sam w sobie nie potwierdza tożsamości procesu. Przed adopcją, sygnałem lub zakończeniem procesu
  ponownie weryfikujemy uzgodniony token tożsamości oraz informacje o pliku wykonywalnym.
- Operacje zapisu i podmiany krytycznych plików stosują plik tymczasowy, walidację i atomową podmianę,
  gdy wspiera ją system plików. Nie zastępujemy poprawnego pliku częściowo zapisanym wynikiem.
- Dane przesłane przez użytkownika mają limit rozmiaru. Nazwa i rozszerzenie pliku nie są dowodem jego
  prawidłowego formatu.

## 5. Bezpieczeństwo warstwy webowej

- Dane użytkownika, logi serwera oraz komunikaty zewnętrznych procesów są kodowane przed umieszczeniem
  w HTML. Nie używamy nieescapowanego HTML dla danych pochodzących z tych źródeł.
- Nie używamy skryptów ani stylów inline. Polityka Content Security Policy powinna być możliwa do
  włączenia bez przebudowy widoków.
- Komunikat dla użytkownika opisuje problem i możliwe działanie, ale nie ujawnia stack trace,
  wewnętrznych ścieżek, zapytań SQL ani konfiguracji bezpieczeństwa.
- Szczegóły techniczne trafiają do bezpiecznych logów aplikacji wraz z identyfikatorem operacji.
- Odpowiedzi zawierające dane wrażliwe nie są cache'owane po stronie przeglądarki ani pośredników.
- Zależności frontendowe i backendowe dodajemy tylko z zaufanego źródła i wyłącznie wtedy, gdy są
  rzeczywiście potrzebne.

## 6. Loggery i zakres logowania

Każda klasa wykonująca operacje aplikacyjne, domenowe, procesowe, sieciowe lub I/O posiada logger
Log4j2 przypisany do tej klasy. Preferujemy adnotację Lombok `@Log4j2`; jawne pole typu
`org.apache.logging.log4j.Logger` tworzone przez `LogManager` jest dopuszczalne, gdy ma konkretną
przewagę. Kod aplikacji nie wprowadza równoległej fasady logowania. Nie dodajemy loggerów do pasywnych
DTO, rekordów, enumów, adnotacji ani klas zawierających wyłącznie dane. Nie używamy `System.out` ani
`System.err`.

Logowanie ma umożliwiać odtworzenie przebiegu istotnej operacji bez uruchamiania debuggera:

- początek operacji, jej najważniejsze etapy i końcowy wynik;
- zmiany stanu serwera oraz przyczyna przejścia;
- identyfikator operacji, stabilny identyfikator zarządzanego procesu i czas wykonania;
- wybrana bezpieczna strategia, wariant fallback, retry i timeout;
- liczba przetworzonych elementów, nazwa rodzaju zasobu i poziom zaufania snapshotu;
- odrzucenie spóźnionego callbacku lub polecenia sprzecznego z bieżącym stanem;
- wynik operacji administracyjnej istotnej dla bezpieczeństwa lub danych.

Stosujemy poziomy konsekwentnie:

- `INFO` — start i zakończenie aplikacji, operacje użytkownika, istotne etapy długich procesów, zmiany
  stanu, adopcja procesu, snapshot/restore, zmiana konfiguracji oraz końcowy wynik operacji;
- `DEBUG` — szczegóły decyzji, bezpieczne parametry techniczne, rozpoznane warianty, odrzucone
  możliwości, dane pomocne w diagnozie i komunikacja między etapami procesu;
- `WARN` — sytuacja nieoczekiwana, z której aplikacja potrafi się bezpiecznie odzyskać, retry,
  fallback, zbliżenie do limitu zasobów albo odrzucone ryzykowne wejście;
- `ERROR` — operacja zakończona niepowodzeniem, utrata wymaganej zdolności, niespójność lub sytuacja
  wymagająca interwencji użytkownika.

Domyślny poziom produkcyjny to `INFO`; `DEBUG` można włączyć diagnostycznie bez zmiany kodu. Komunikaty
logów są pisane po angielsku, zawierają konkretną nazwę operacji i wynik oraz używają parametrów
Log4j2 zamiast konkatenacji napisów.

Wyjątek logujemy razem ze stack trace dokładnie na granicy, która kończy jego obsługę albo zamienia go
w końcowy wynik. Nie logujemy tego samego wyjątku na każdym poziomie stosu. Oczekiwany wariant
kontraktu nie jest automatycznie błędem `ERROR`.

## 7. Bezpieczna treść i trwałość logów

- Nigdy nie logujemy sekretów, pełnych cookie, nagłówków autoryzacyjnych, zawartości formularzy haseł,
  pełnej command line procesu ani całych obiektów mogących zawierać dane wrażliwe.
- Wartość wrażliwą zastępujemy stałym oznaczeniem, np. `<redacted>`. Nie wystarcza częściowe ukrycie,
  jeśli pozostały fragment pozwala odtworzyć sekret.
- Dane pochodzące od użytkownika lub procesu zewnętrznego nie mogą tworzyć fałszywych wpisów przez
  znaki nowej linii lub sterujące. Logujemy je parametrycznie i w bezpiecznej, znormalizowanej formie.
- Log aplikacji i surowy log procesu Valheim mają rozdzielone odpowiedzialności. Nie kopiujemy każdej
  linii Valheim do `INFO` aplikacji.
- Dla cyklicznych zdarzeń i pętli stosujemy agregację, liczniki albo `DEBUG`, aby nie tworzyć lawiny
  identycznych wpisów.
- Pliki logów mają rotację, limit całkowitego rozmiaru i retencję. Brak miejsca na dysku jest
  wykrywany i zgłaszany bez nadpisywania krytycznych danych.
- Dostęp do pobierania i podglądu logów podlega tej samej autoryzacji co pozostałe funkcje panelu.

## 8. Obsługa błędów i zależności

- Nie przechwytujemy wyjątku tylko po to, aby go zalogować i kontynuować w nieznanym stanie.
- Błąd nie może pozostawić zasobu, blokady, uchwytu procesu ani stanu maszyny życia bez jawnego
  właściciela i ścieżki odzyskania.
- Timeouty i limity są jawne. Po ich przekroczeniu operacja przechodzi do zdefiniowanego stanu, a
  wykonujący ją worker nie może później bezwarunkowo nadpisać nowszego wyniku.
- Aktualizacja zależności związanej z uwierzytelnianiem, parsowaniem, bazą, siecią lub systemem
  operacyjnym wymaga sprawdzenia informacji o bezpieczeństwie i zmian niekompatybilnych.
- Devtools, szczegółowe strony błędów i diagnostyczne endpointy nie są dostępne w dystrybucji
  produkcyjnej, jeżeli ujawniają wewnętrzny stan aplikacji.

## 9. Kontrola przed zakończeniem zmiany

Przed uznaniem zmiany za kompletną należy sprawdzić:

- czy wszystkie nowe granice wejścia walidują dane i egzekwują autoryzację;
- czy ścieżki i procesy są ponownie weryfikowane bezpośrednio przed operacją;
- czy żaden sekret nie trafia do kodu, HTML, komunikatu błędu, `toString()` ani logów;
- czy klasa wykonująca operacje ma logger i wystarczające wpisy `INFO` oraz `DEBUG` do odtworzenia
  przebiegu jej istotnych przypadków;
- czy poziomy logowania odpowiadają znaczeniu zdarzeń i nie generują niekontrolowanego szumu;
- czy nie zalogowano tego samego wyjątku wielokrotnie;
- czy niepowodzenie pozostawia system w jawnym, bezpiecznym stanie;
- czy operacje administracyjne są chronione również po stronie serwera;
- czy nowe dane trwałe, backupy i logi mają określone uprawnienia, retencję oraz sposób usuwania;
- czy testy obejmują odrzucenie nieprawidłowych danych, brak autoryzacji i redakcję sekretów tam,
  gdzie dana klasa odpowiada za takie zachowanie.
