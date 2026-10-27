# Reguły formatowania kodu

> Status: obowiązujący

Dokument opisuje wyłącznie **wygląd** kodu. Nazewnictwo, projektowanie klas, null-safety i Lombok
opisuje `clean-code.md`, wymagany zakres komentarzy dokumentacyjnych — `documentation.md`, a strukturę
klas testowych — `unit-tests.md`. Gdy tamte dokumenty mówią o strukturze, a ten o odstępach, oba
obowiązują jednocześnie.

Formatowanie jest ustawieniem projektu, nie preferencją osoby. Nie przeformatowujemy plików ani
fragmentów niezwiązanych z bieżącą zmianą, ponieważ zaciemnia to diff.

## 1. Wcięcia, długość linii i pliki

- Wcięcie to **4 spacje**. Nie używamy znaków tabulacji w kodzie Java.
- Maksymalna długość linii to **125 znaków**. Tekst ciągły w Javadoc i komentarzach łamiemy wcześniej,
  w okolicach 110 znaków, żeby pozostał czytelny w recenzji obok kodu.
- Pliki zapisujemy w UTF-8, z zakończeniami linii LF i jedną pustą linią na końcu pliku.
- W plikach `.properties` polskie znaki diakrytyczne zapisujemy sekwencjami `\uXXXX`, a nie
  literalnie: tekst „Zmień hasło” zapisujemy jako `Zmie\u0144 has\u0142o`. Treść pliku pozostaje
  wtedy czysto ASCII i nie zależy od kodowania, jakim odczyta go edytor lub narzędzie.
- Nie zostawiamy białych znaków na końcu linii ani więcej niż jednej pustej linii z rzędu.

## 2. Odstępy wewnątrz nawiasów

Cechą charakterystyczną tego projektu są **spacje wewnętrzne** w nawiasach zawierających treść:

```java
public PathServiceImpl( @Nonnull ApplicationHome applicationHome, @Nullable String configuredDirectory ) {
    if ( !Files.isDirectory( realPath ) ) {
        throw new PathResolutionException( "Base directory '%s' is not a directory".formatted( realPath ) );
    }
    for ( VdeckProperty property : values() ) {
        defaults.put( property, property.defaultValue );
    }
}
```

Zasady szczegółowe:

- Nawias okrągły z treścią otrzymuje spację po `(` i przed `)` — dotyczy deklaracji metod, wywołań,
  `if`, `for`, `while`, `switch`, `catch` oraz try-with-resources.
- **Pusta lista argumentów nie ma spacji:** `values()`, `toRealPath()`, `getName()`.
- Nawias klamrowy inicjalizatora z treścią również ma spacje wewnętrzne:
  `@ValueSource( strings = { "", "   " } )`.
- Nawias ostry z parametrem typu ma spacje wewnętrzne: `Map< SystemPropertyIf, String >`. Operator
  diamentowy pozostaje bez spacji: `new LinkedHashMap<>()`.
- Adnotacja z argumentami stosuje tę samą zasadę: `@Configuration( proxyBeanMethods = false )`,
  `@ExtendWith( MockitoExtension.class )`.
- Nie wstawiamy spacji przed przecinkiem ani przed średnikiem.

## 3. Nawiasy klamrowe i instrukcje

- Klamra otwierająca pozostaje na końcu linii deklaracji, klamra zamykająca w osobnej linii.
- `else`, `catch` i `finally` umieszczamy w tej samej linii co poprzednia klamra zamykająca:
  `} catch ( IOException exception ) {`.
- Blok klamrowy stosujemy zawsze, również dla jednoinstrukcyjnego `if`. Nie zapisujemy instrukcji
  warunkowej w jednej linii.
- Wyrażenie warunkowe łamane na kilka linii przenosi operator `?` i `:` na początek kolejnej linii,
  z wcięciem o jeden poziom:

```java
Path configured = overridden
        ? toPath( configuredDirectory.strip() )
        : applicationHome.getDir().toPath();
```

- Argumenty wywołania niemieszczące się w linii przenosimy w całości do kolejnej linii z wcięciem
  ciągłym równym **8 spacjom**, a nie wyrównujemy ich do nawiasu otwierającego.

## 4. Importy

Kolejność grup, rozdzielonych pojedynczą pustą linią:

1. importy statyczne projektu i bibliotek innych niż `java`;
2. pozostałe importy w kolejności alfabetycznej, przy czym pakiety `java` i `javax` tworzą osobną
   grupę na końcu;
3. importy statyczne `java` na samym końcu.

W praktyce daje to układ:

```java
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.system.ApplicationHome;

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;
```

Nie używamy importów z gwiazdką niezależnie od liczby klas z jednego pakietu. Nie zostawiamy importów
nieużywanych.

## 5. Kolejność elementów w klasie

Obowiązująca kolejność:

1. stałe `static final`;
2. pola instancji;
3. konstruktory;
4. **metody publiczne**;
5. metody chronione i pakietowe;
6. metody prywatne;
7. typy zagnieżdżone.

**Metody publiczne zawsze znajdują się na górze klasy, bezpośrednio po konstruktorach.** Publiczny
kontrakt typu ma być widoczny natychmiast po otwarciu pliku, bez przewijania przez szczegóły
implementacji. Metoda prywatna nigdy nie wyprzedza metody publicznej, nawet jeśli jest przez nią
wywoływana i nawet jeśli jest `static`. Kolejność metod prywatnych odpowiada kolejności ich pierwszego
użycia, o ile nie zaburza to czytelności.

W obrębie tej samej widoczności metody statyczne i instancyjne nie są rozdzielane sztucznie; decyduje
kolejność wynikająca z powyższych reguł.

Klasy testowe mają własny, obowiązkowy podział na sekcje z separatorami, opisany w `unit-tests.md` §3.
Ta sekcja go nie zmienia i nie stosuje się do klas testowych.

## 6. Puste linie

- Pojedyncza pusta linia rozdziela kolejne pola i kolejne metody.
- Wewnątrz metody pojedyncza pusta linia rozdziela logiczne bloki, żeby człowiek widział kroki bez
  czytania każdej linii. Osobnym blokiem jest zwykle:
  - walidacja argumentów (`requireNonNull`) i warunki wczesnego wyjścia;
  - przygotowanie danych lub zasobów do jednego kroku;
  - wywołanie zmieniające stan albo wykonujące I/O;
  - obsługa wyniku, logowanie i `return`;
  - instrukcja złożona (`if`, `for`, `try`, `switch`) z liniami, które bezpośrednio jej dotyczą.
- Linie należące do jednego kroku zostają razem; nie wstawiamy pustej linii po każdej instrukcji.
  Metoda z jednym krokiem nie potrzebuje pustych linii.
- Komentarz `//` opisujący blok stoi bezpośrednio nad nim, a pusta linia nad komentarzem.
- Pusta linia występuje po deklaracji pakietu i po bloku importów.
- Nie zostawiamy pustej linii bezpośrednio po klamrze otwierającej klasę lub metodę ani bezpośrednio
  przed klamrą zamykającą.

```java
Path download( @Nonnull ReleaseAsset asset, @Nonnull Path directory, @Nonnull Deadline deadline )
        throws IOException {
    requireNonNull( asset, "asset must not be null" );
    requireNonNull( directory, "directory must not be null" );
    requireNonNull( deadline, "deadline must not be null" );

    Path part = directory.resolve( asset.name() + ".part" );
    Path target = directory.resolve( asset.name() );
    Files.deleteIfExists( part );
    Files.deleteIfExists( target );

    log.info( "Downloading {} ({} bytes)", asset.name(), asset.size() );
    HttpResponse< InputStream > response = send( request( asset, deadline ) );

    String digest = copy( response.body(), part, asset.size(), deadline );
    verify( asset, digest );
    return publish( part, target );
}
```

## 7. Adnotacje

- Adnotacje typu, metody, konstruktora i pola umieszczamy w osobnych liniach nad deklaracją:

```java
@Bean
@Nonnull
PathService pathService( @Value( "${vdeck.server-directory:}" ) @Nullable String configuredDirectory ) {
```

- Adnotacje nullability parametrów zapisujemy w linii parametru, przed jego typem.
- Adnotację nullability wyniku zapisujemy bezpośrednio przed typem zwracanym, po modyfikatorach:
  `public static @Nonnull Map< SystemPropertyIf, String > defaults()`.

## 8. Javadoc i komentarze

- Javadoc otwieramy `/**`, każdą linię prowadzimy znakiem `*` wyrównanym do pierwszej gwiazdki.
- Opis znacznika `@param` i `@throws` przenosimy do **kolejnej linii** i wcinamy o dodatkowe 8 spacji.
  Znacznik `@return` mieści opis w tej samej linii:

```java
/**
 * Resolves a relative path against the installation directory.
 *
 * @param relativePath
 *         configured path relative to the installation directory
 * @return absolute path inside the installation directory
 * @throws PathResolutionException
 *         if the value is absolute, invalid, or leaves the installation directory
 */
```

- Bloki znaczników rozdzielamy pustą linią opisu od treści opisowej, ale nie rozdzielamy pustą linią
  samych znaczników.
- Komentarz jednoliniowy zaczynamy od `//` i spacji, umieszczamy nad opisywanym kodem, nie na końcu
  linii.

## 9. Kontrola przed zakończeniem zmiany

- czy zmienione pliki używają 4 spacji i nie zawierają tabulatorów;
- czy żadna linia nie przekracza 125 znaków;
- czy nawiasy z treścią mają spacje wewnętrzne, a puste listy argumentów ich nie mają;
- czy metody publiczne znajdują się nad wszystkimi metodami o węższej widoczności;
- czy logiczne bloki wewnątrz metod są rozdzielone pojedynczą pustą linią;
- czy importy są pogrupowane, posortowane i nie zawierają nieużywanych ani gwiazdkowych;
- czy pliki `.properties` nie zawierają znaków spoza ASCII;
- czy nie przeformatowano fragmentów niezwiązanych z zakresem zmiany.
