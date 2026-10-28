# Lista zmian wersji: jeden plik na wersję, release notes i panel „Co nowego”

> Status: zrealizowany w VDEC-27 (AD-029). Plan opisuje stan po implementacji; szczegóły działania
> są w `.docs/design.md` §11 i `dokumentacja/dokumentacja-projektowa.md` §5.12.

## Kontekst

Dziś wydanie publikuje `--generate-notes`: angielskie tytuły PR-ów, przydatne programiście, a nie
użytkownikowi. Chcemy:

1. **Jeden plik ze zmianami na wersję**, uzupełniany w trakcie implementacji (po polsku, dla użytkownika).
   Ten plik zastąpił wcześniej rozważane fragmenty `.changes/unreleased/*`.
2. **Ten sam plik jako treść GitHub Release.**
3. **Panel w GUI** pokazywany po aktualizacji, dopóki użytkownik go nie zamknie. Stan trzymamy w bazie.

Decyzje użytkownika:
- Lista jest obejrzana dopiero po kliknięciu „Zamknij”.
- Użytkownik, który przeskoczył wersje, widzi zmiany każdej pominiętej wersji, od najnowszej.
- Po świeżej instalacji panelu nie pokazujemy.
- Panel zawsze pokazuje numer wersji (lub numery wersji) przy jej zmianach.
- Numer zadania: VDEC-27.

## Format i miejsce pliku

`vdeck-app/src/main/resources/release-notes/<X.Y.Z>.md`, bez `-SNAPSHOT`. To ta sama konwencja co
`db/changelog/versions/<wersja>.xml`. Plik jest w classpath, więc panel go czyta, a workflow podaje
ścieżkę do `gh`.

Ograniczony podzbiór Markdownu, poprawny i czytelny na GitHubie:

```markdown
## Nowości
- **Lista zmian po aktualizacji**
  Po pierwszym uruchomieniu nowej wersji panel pokazuje, co się w niej zmieniło.

## Poprawki
- **…**
  …
```

- Dozwolone nagłówki `## Nowości`, `## Zmiany`, `## Poprawki`, w tej kolejności.
- Każda pozycja ma dwie linie:
  - tytuł: jednolinijkowiec `- **Tytuł**`;
  - opis: bardzo krótki, obowiązkowy, jedna wcięta linia (jedno–dwa zdania, limit np. 200 znaków).
- Na GitHubie pozycja renderuje się jako pogrubiony tytuł, a za nim opis.
- Brak HTML-a i linków.

Panel nie renderuje Markdownu: własny parser czyta sekcje i pozycje (tytuł oraz opis), a Thymeleaf
wyświetla je przez `th:text`. Tytuł jest pogrubiony, a opis pod nim drobniejszym tekstem. Treść jest escapowana, więc nie potrzeba nowej zależności. Pusty plik (wersja w toku bez
zmian) jest poprawny.

## Panel w GUI: nowa funkcja `feature.releasenotes`

Według AD-003–AD-005 i `CLAUDE.md`:

- **`domain/ReleaseNotes`** (record: `ReleaseVersion version`, `List< ReleaseNotesSection >`),
  **`ReleaseNotesSection`** (`ReleaseNotesCategory category`, `List< ReleaseNotesEntry >`),
  **`ReleaseNotesEntry`** (`String title`, `String description`), enum **`ReleaseNotesCategory`**
  (`FEATURES`, `CHANGES`, `FIXES` — nagłówek pliku i klucz tłumaczenia) oraz
  **`domain/ReleaseNotesAcknowledgement`** (encja JPA, tabela `release_notes_acknowledgements` z jedną
  kolumną `version VARCHAR(32)` PK; czas potwierdzenia okazał się zbędny).
- **`persistence/ReleaseNotesAcknowledgementRepository`**.
- **`service/ReleaseNotesService`** i **`service/impl/ReleaseNotesServiceImpl`**:
  - `List< ReleaseNotes > findUnacknowledged()` zwraca wersje `> max(acknowledged)` i `<= installed`,
    od najnowszej. Pusta lista oznacza brak panelu.
  - `void acknowledgeInstalled()` zapisuje zainstalowaną wersję.
  - Pliki czyta pakietowy `ReleaseNotesReader` przez `ResourcePatternResolver`
    (`classpath*:release-notes/*.md`, działa w JAR-ze); uszkodzony plik pomija z `WARN`. Format
    sprawdza pakietowy `ReleaseNotesParser`.
- **`exc/ReleaseNotesException`** i **`ReleaseNotesExceptionHandler`** (AD-014): nieudany zapis
  potwierdzenia wraca na `/` z komunikatem, a lista zostaje widoczna.
- **`ReleaseNotesController`**: `POST /release-notes/acknowledge` → `acknowledgeInstalled()` →
  `redirect:/`.
- **`HomeController.showHome`** dostaje `ReleaseNotesService` i dodaje do modelu `releaseNotes`.
- **Szablon `templates/home/index.html`**:
  - szeroka karta `.view-card` w `.panel-frame` pod wyspą (zamiast `<dialog open>`, zgodnie z
    `.docs/ui.md`), z blokiem dla każdej wersji i nagłówkiem `#{releaseNotes.version(${notes.version})}`,
    np. „Wersja 0.4.0”;
  - formularz POST z CSRF i przyciskiem „Zamknij”, bez JS;
  - style w osobnym `static/css/home.css` importującym `variables.css`, układ działający na telefonie
    i tablecie.
- **Teksty interfejsu** (tytuł, „Wersja {0}”, nazwy sekcji, „Zamknij”) w `messages.properties` przez
  enum `TranslationKeyIf`.
  - Nazwy sekcji mapujemy z nagłówka pliku na klucz, więc plik i GUI nie dublują tekstu.

**Zainstalowana wersja:** cel `build-info` w `spring-boot-maven-plugin` (`vdeck-app/pom.xml`) daje
bean `BuildProperties`. Usuwamy z wersji `-SNAPSHOT`, więc build deweloperski pokazuje plik wersji w
toku, co ułatwia sprawdzanie. Bez `BuildProperties` (IDE bez Mavena) albo przy nieparsowalnej wersji
funkcja jest wyłączona: pusta lista, a w logu `INFO` (brak `BuildProperties`) albo `WARN`
(nieparsowalna wersja).

**Porównywanie wersji:** przenosimy `launcher.ReleaseVersion` do `vdeck-runtime`
(`runtime.installation.ReleaseVersion`, publiczny). Launcher i panel korzystają z tej samej klasy,
a jej test przenosimy razem z nią.

**Świeża instalacja:** `config.DataInitializer`, który tworzy domyślnego admina (czyli nowa baza),
wywołuje w tej samej transakcji `releaseNotesService.acknowledgeInstalled()`. Przy aktualizacji admin
już istnieje, więc wpisu brak i panel się pokaże.

**Schemat:** changeset `VDEC-27-create-release-notes-acknowledgements` w
`db/changelog/versions/<bieżąca wersja>.xml`, w bloku z komentarzem zadania (`clean-code.md` §13).
`DatabaseSchemaIntegrationTest` sprawdzi encję.

## Wydanie

- **`.github/workflows/build.yml`, krok „Publish release”:** sprawdza, czy
  `vdeck-app/src/main/resources/release-notes/$VERSION.md` istnieje i zawiera co najmniej jedną
  pozycję `- **`. Jeśli nie, rzuca wyjątek przed `gh release create`. Następnie wywołuje
  `gh release create … --notes-file <plik> --generate-notes --latest`, czyli polski opis plus lista
  PR-ów od GitHuba.
  - GitHub umieszcza treść z `--notes-file` przed wygenerowaną listą PR-ów. Jeśli lista będzie
    przeszkadzać, zostaje samo `--notes-file`.
- **`.env/release.bat`:**
  - przed buildem przerywa wydanie, gdy plik `release-notes\%RELEASE%.md` nie istnieje albo nie ma
    żadnej pozycji `- **`;
  - w commicie „Development NEXT-SNAPSHOT” tworzy pusty `release-notes\%NEXT%.md` z trzema nagłówkami
    do uzupełniania.
  - Skrypt pozostaje ASCII: plik powstaje przez skopiowanie szablonu `.env/release-notes-template.md`,
    a nie przez `echo` polskich znaków. Szablon leży poza classpath, więc panel go nie czyta.
- **Test `ReleaseNotesFilesTest`:**
  - każdy plik w `release-notes/` ma nazwę `X.Y.Z.md` i parsuje się bez błędów (tylko dozwolone
    nagłówki, każda pozycja ma tytuł i niepusty opis w limicie długości);
  - istnieje plik dla bieżącej wersji projektu (bez `-SNAPSHOT`).

## Dokumentacja (Definition of Done)

- Nowa decyzja w `.docs/architectural_decisions.md` (skill `decision`): plik zmian na wersję jako
  źródło release notes i panelu „Co nowego”; uzupełnienie AD-023.
- `.docs/design.md`: nowa funkcja i tabela.
- `.docs/rules/README.md` (DoD) lub `documentation.md`: każda zmiana widoczna dla użytkownika dopisuje
  pozycję do `release-notes/<wersja>.md`.
- `.docs/plans/release-notes.md`: przepisany na to rozwiązanie zamiast fragmentów `.changes/`.
- `CLAUDE.md`: opis funkcji i pliku.
- `dokumentacja/dokumentacja-projektowa.md` plus diagram (Mermaid → SVG).
- Instrukcja użytkownika (AD-022): akapit i zrzut ekranu panelu (`zrzuty-ekranu.mjs`).
- Pierwszy wpis w `release-notes/<bieżąca wersja>.md`: ta funkcja.

## Kluczowe pliki

- Nowe: `vdeck-app/.../feature/releasenotes/**`, `resources/release-notes/*.md`,
  `ReleaseNotesFilesTest`, testy serwisu i kontrolera.
- Zmieniane:
  - `HomeController.java`, `templates/home/index.html`, `static/css/home.css`, `messages.properties`;
  - `config/DataInitializer.java`, `db/changelog/versions/0.0.1.xml`, `vdeck-app/pom.xml` (build-info);
  - `vdeck-launcher/.../ReleaseVersion.java` → `vdeck-runtime/.../installation/ReleaseVersion.java`
    (oraz importy w `GitHubReleaseClient`, `LauncherMain`);
  - `.github/workflows/build.yml`, `.env/release.bat`.

## Weryfikacja

1. `.\mvnw.cmd test`: testy parsera (poprawne pliki, pozycja bez opisu, opis za długi, nieznany
   nagłówek), serwisu (brak wpisów, wpis starszej
   wersji, przeskok kilku wersji, brak `BuildProperties`), kontrolera (POST zapisuje i przekierowuje),
   `DataInitializer` (świeża baza oznacza wersję jako obejrzaną), `ReleaseNotesFilesTest`,
   `DatabaseSchemaIntegrationTest`.
2. Ręcznie w sandboxie (`spring-boot:run` z `-Dvdeck.server-directory`):
   - świeża baza: brak panelu;
   - usunięcie wiersza z `release_notes_acknowledgements` w `vdeck.db`: panel z nagłówkiem „Wersja X.Y.Z”;
   - „Zamknij”: panel znika i nie wraca po restarcie;
   - dwa pliki wersji i brak wpisów: obie wersje, od najnowszej;
   - widok na wąskim i szerokim ekranie.
3. Wydanie na sucho: `release.bat` przerywa przy brakującym lub pustym pliku. Krok workflow można
   sprawdzić lokalnie w `pwsh`, uruchamiając samą walidację pliku.
