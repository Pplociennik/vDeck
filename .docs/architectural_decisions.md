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
