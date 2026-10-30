# vDeck

[![Build](https://github.com/Pplociennik/vDeck/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Pplociennik/vDeck/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Pplociennik/vDeck)](https://github.com/Pplociennik/vDeck/releases/latest)

Lokalny panel webowy do zarządzania dedykowanym serwerem **Valheim** na Windows x64.
vDeck instaluje się obok `valheim_server.exe`, działa na tej samej maszynie i jest obsługiwany
z przeglądarki: pozwala edytować ustawienia serwera, uruchamiać go, zatrzymywać z kopią świata
i obserwować jego stan oraz konsolę.

> Projekt jest we wczesnej fazie rozwoju. Interfejs panelu jest dostępny wyłącznie po polsku.

## Główne założenia

- **Lokalność i bezpieczeństwo dostępu** — panel domyślnie nasłuchuje tylko na `127.0.0.1:8085`
  i wymaga logowania. Pierwsze logowanie (`admin` / `admin`) wymusza ustawienie własnego hasła.
- **Bezpieczeństwo danych świata** — przed każdym zatrzymaniem (Stop) vDeck wykonuje kompletną kopię
  plików świata, a dopiero potem wysyła do serwera sygnał CTRL+C. Wymuszone zakończenie (Kill)
  wymaga ręcznego potwierdzenia. Nieudany Stop pozostawia serwer uruchomiony.
- **Jeden serwer na instalację** — blokada instalacji, plik `.vDeck/server.pid` i weryfikacja
  tożsamości procesu chronią przed podwójnym uruchomieniem i pozwalają przejąć serwer działający
  po ponownym starcie panelu.
- **Samodzielna dystrybucja** — paczka ZIP zawiera pełne Eclipse Temurin JRE 25, więc nie trzeba
  instalować Javy. Kolejne wersje instalują się automatycznie przy uruchomieniu, w transakcji
  z dziennikiem i możliwością wycofania, z zachowaniem ustawień, bazy i światów.
- **Konfiguracja w plikach** — wszystkie ustawienia edytowalne przez użytkownika leżą
  w `.vDeck/config/` (`vDeck.properties`, `valheim-server.properties`, `vDeck-start.properties`),
  a wszystkie katalogi tworzone przez vDeck znajdują się wewnątrz `.vDeck`.

Obecnie panel obsługuje m.in.: ustawienia serwera (Ogólne, Dostęp, Świat i zapis, Kopie automatyczne)
i ustawienia gry, Start, Stop, ponowne uruchomienie i Kill, podgląd stanu serwera i konsoli, listy
graczy (bany, administratorzy, biała lista) oraz listę zmian po aktualizacji. Zarządzanie światami
i kopiami, restore, RCON, mody i harmonogramy nie są jeszcze zaimplementowane.

## Architektura

Projekt jest reaktorem Maven złożonym z modułów:

| Moduł | Odpowiedzialność |
|---|---|
| `vdeck-runtime` | Czysta Java (bez Springa): cykl życia procesu serwera, launcher Windows przez FFM, snapshoty świata, wspólny kontrakt startu i aktualizacji. |
| `vdeck-runner` | Wykonywalny helper wysyłający CTRL+C do procesu serwera. |
| `vdeck-launcher` | Program startowy: blokada instalacji, sprawdzenie i przygotowanie aktualizacji z GitHub Releases, uruchomienie panelu. |
| `vdeck-updater` | Dziennikowana podmiana plików, scalanie ustawień i recovery przerwanej aktualizacji (bez sieci). |
| `vdeck-app` | Aplikacja Spring Boot: panel webowy, bezpieczeństwo, konfiguracja, persystencja. |
| `.dist` | Moduł pakujący paczkę `vDeck-dist-<wersja>.zip`. |

Stos: Java 25, Maven 3.9.16 (wrapper), Spring Boot 4.1.0, Spring MVC + Thymeleaf, Spring Security,
Spring Data JPA + SQLite, Liquibase, Log4j2, Lombok, JUnit 5 + AssertJ + Mockito.

## Wymagania

### Uruchomienie

- Windows x64.
- Zainstalowany Valheim Dedicated Server (`valheim_server.exe`).
- Przeglądarka internetowa i prawo zapisu w katalogu instalacji serwera.
- Wolne miejsce na dane świata i jego kopię.
- Dostęp do internetu tylko dla automatycznych aktualizacji (opcjonalnie, `update.enabled`).

### Budowanie

- Windows x64 (testy wywołują natywne API Windows przez FFM).
- JDK 25.
- Dostęp do internetu przy pierwszym `package` — build pobiera Temurin JRE 25 i sprawdza jego SHA-256.

## Instalacja

1. Pobierz `vDeck-dist-<wersja>.zip` z [najnowszego wydania](https://github.com/Pplociennik/vDeck/releases/latest).
2. Wypakuj całą zawartość do katalogu Valheim Dedicated Server, tak aby `start-vDeck.bat` leżał
   obok `valheim_server.exe`.
3. Uruchom `start-vDeck.bat`. Przeglądarka otworzy `http://127.0.0.1:8085`.
4. Zaloguj się jako `admin` / `admin` i ustaw własne hasło.

Pełna instrukcja obsługi ze zrzutami ekranu (`vDeck-instrukcja.pdf`) jest częścią paczki ZIP.

## Budowanie z repozytorium

```powershell
.\mvnw.cmd test       # testy całego reaktora
.\mvnw.cmd package    # target/vDeck-dist-<wersja>.zip z JAR-ami, JRE 25 i instrukcją PDF
```

Szczegóły pakowania opisuje [`.dist/README.md`](.dist/README.md). Każdy push i pull request do `main`
uruchamia testy w GitHub Actions; push do `main` z wersją bez `-SNAPSHOT` publikuje nowe wydanie.

## Dokumentacja

- [Dokumentacja projektowa](dokumentacja/dokumentacja-projektowa.md) — opis zaimplementowanego
  systemu wraz z diagramami.
- [Decyzje architektoniczne](.docs/architectural_decisions.md) i [założenia projektowe](.docs/design.md).
- [Reguły pracy w repozytorium](.docs/rules/README.md).
- [Instrukcja użytkownika (źródło AsciiDoc)](dokumentacja/instrukcja-uzytkownika/instrukcja-uzytkownika.adoc).

## Changelog

Pełne opisy wydań znajdują się w [GitHub Releases](https://github.com/Pplociennik/vDeck/releases);
źródłem list zmian są pliki [`vdeck-app/src/main/resources/release-notes`](vdeck-app/src/main/resources/release-notes).

### 0.1.1 (w przygotowaniu)

#### Nowości

- **Wyłączanie sprawdzania pliku serwera gry** — wpis `server.file.check=false` w `vDeck.properties`
  pozwala uruchomić panel bez `valheim_server.exe` w katalogu.

### [0.1.0](https://github.com/Pplociennik/vDeck/releases/tag/v0.1.0) — 2026-10-30

#### Nowości

- **Lista zmian po aktualizacji** — po aktualizacji panel jednorazowo pokazuje na stronie głównej,
  co zmieniło się w nowej wersji.
- **Opisy zestawów ustawień świata** — nad listą „Zestaw ustawień świata” panel opisuje wszystkie
  zestawy: co każdy zmienia w walce, karze za śmierć, zasobach, najazdach, portalach i mapie.
- **Wybór zachowania serwera przy zamknięciu panelu** — ustawienie `panel.close.strategy` decyduje,
  czy panel zatrzymuje serwer (domyślnie, w razie potrzeby wymuszając), tylko próbuje go zatrzymać,
  czy zostawia go uruchomionego.
- **Logi panelu w plikach** — każde uruchomienie panelu zapisuje log w `.vDeck\logs\panel`; stare logi
  są usuwane po 30 dniach, co zmienisz w `vDeck.properties`.
- **Wersja panelu w menu bocznym** — na dole menu bocznego widać numer zainstalowanej wersji vDeck.
- **Zamykanie panelu z menu bocznego** — przycisk z ikoną zasilania na dole menu bocznego zamyka panel
  po potwierdzeniu, postępując z serwerem zgodnie z `panel.close.strategy`.
- **Ponowne uruchamianie serwera** — przycisk „Uruchom serwer ponownie” na wyspie zatrzymuje serwer
  z kopią świata i od razu uruchamia go z aktualnymi ustawieniami.
- **Panel „Stan serwera”** — przycisk na górze menu bocznego pokazuje stan, czas działania i ustawienia
  serwera z odnośnikami do paneli, w których je zmienisz.
- **Listy graczy: bany, administratorzy i biała lista** — pozycja „Gracze” w menu bocznym otwiera trzy
  panele, w których dodasz gracza Steam lub Xbox do listy albo go usuniesz; zmiana działa po restarcie
  serwera.

#### Zmiany

- **Ustawienia serwera podzielone na panele** — pozycja „Serwer” w menu bocznym rozwija się w cztery
  panele: Ogólne, Dostęp, Świat i zapis oraz Kopie automatyczne; każdy zapisuje swoje pola.
- **Zero dni zachowuje wszystkie logi serwera gry** — ustawienie `game.log.retention.days=0` zachowuje
  teraz wszystkie logi serwera gry zamiast tylko dzisiejszych.

#### Poprawki

- **Bezpieczniejsze tworzenie bazy danych** — panel nie przerywa startu błędem, gdy tabela w bazie
  danych już istnieje — pomija wtedy jej ponowne tworzenie.

### [0.0.1](https://github.com/Pplociennik/vDeck/releases/tag/v0.0.1) — 2026-10-28

#### Nowości

- **Lista zmian po aktualizacji** — po aktualizacji panel jednorazowo pokazuje na stronie głównej,
  co zmieniło się w nowej wersji.

---

<sub>Panel vDeck jest tworzony ze wsparciem Claude Code oraz OpenAI Codex, pod nadzorem doświadczonego
inżyniera oprogramowania.</sub>
