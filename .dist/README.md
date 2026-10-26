# vDeck

Lokalny panel webowy do zarządzania dedykowanym serwerem Valheim na Windows x64.
Pełna instrukcja obsługi ze zrzutami ekranu to `vDeck-instrukcja.pdf` w paczce ZIP.
Skrócony opis, wymagania i opis plików znajdują się w [README.txt](README.txt).

Wypakuj całą paczkę obok `valheim_server.exe` i uruchom `start-vDeck.bat`.
Panel korzysta z dołączonego Eclipse Temurin JRE 25 i jest dostępny pod
`http://127.0.0.1:8085`. Pierwsze logowanie: `admin` / `admin`, z obowiązkową zmianą hasła.

## Budowanie paczki z repozytorium

Build wymaga JDK 25. W głównym katalogu projektu uruchom:

```powershell
.\mvnw.cmd clean package
```

Wynik: `target/vDeck-dist-<wersja projektu>.zip`. Wersja pochodzi z głównego `pom.xml`.
Moduł Maven `.dist` wykonuje pakowanie po zbudowaniu aplikacji i runnera, również przy buildzie równoległym.

- Maven AntRun pobiera ustaloną wersję Temurin JRE 25 dla Windows x64 z oficjalnego wydania,
  sprawdza przypisaną sumę SHA-256 i rozpakowuje pełne środowisko do `.dist/jre`.
- Asciidoctor Maven Plugin konwertuje instrukcję obsługi z
  `dokumentacja/instrukcja-uzytkownika/instrukcja-uzytkownika.adoc` do `.dist/target/instrukcja/vDeck-instrukcja.pdf`.
  Brakujący obrazek albo zły odnośnik przerywa build.
- Maven Resources kopiuje wykonywalne JAR-y do `.dist/lib`, bez filtrowania plików binarnych.
- Maven Assembly tworzy ZIP zawierający poniższe pliki, bez dodatkowego katalogu nadrzędnego.

```text
start-vDeck.bat
README.md
README.txt
vDeck-instrukcja.pdf
lib/
    vdeck.jar
    vdeck-runner.jar
jre/                       # pełne JRE, w tym bin/java.exe, bin/javaw.exe i legal/
.vDeck/config/
    vDeck.properties
    valheim-server.properties
```

JRE jest pobierane podczas `package`, a nie `test`. Pierwszy build po `clean` wymaga internetu;
kolejny `package` korzysta z pobranego archiwum w `.dist/target`, ponownie sprawdzając SHA-256.
Zmiana wersji JRE wymaga aktualizacji `temurin.version`, `temurin.build` i `temurin.sha256`
w `.dist/pom.xml`. Błąd pobrania lub sumy przerywa build. Przy uszkodzonym archiwum wykonaj `clean package`.

Katalogi `lib` i `jre` są generowane i ignorowane przez Git. `clean` usuwa je, zachowując
skrypt, instrukcje i szablony ustawień. Pliki `pom.xml`, `assembly.xml`, cache pobrań,
logi i baza panelu nie trafiają do ZIP-a.

Do prób uruchomienia wypakuj ZIP poza `.dist`: ten katalog zawiera wersjonowane szablony,
które muszą pozostać z wartościami domyślnymi i pustym hasłem serwera.

Skrypt używa dołączonej Javy niezależnie od `PATH` i `JAVA_HOME`, ustawia katalog roboczy
na katalog instalacji i jawnie przekazuje go jako `vdeck.server-directory` — JAR-y leżą w `lib`,
więc bez tego panel uznałby `lib` za instalację. Adres nasłuchu ustawia zmienna `ADDRESS`
(domyślnie `127.0.0.1`), a port zmienna `PORT` (domyślnie `8085`), obie na początku skryptu.
Adres inny niż `127.0.0.1` wystawia panel HTTP do sieci — tylko w zaufanej sieci. Dodatkowe
argumenty skryptu trafiają do Spring Boota, ale adresu ani portu nie podaje się jako argumentu:
zdublowana właściwość daje np. wartość `8085,8086` i panel nie wystartuje.
Helper CTRL+C używa tego samego JRE przez `java.home`.
