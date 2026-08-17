# Reguły konfiguracji Maven

> Status: obowiązujące dla całego repozytorium

Pliki `pom.xml` są częścią kodu projektu. Muszą być krótkie, czytelne i zawierać wyłącznie informacje
potrzebne do zbudowania danego modułu. Konfiguracja Maven nie może stawać się magazynem pustych
metadanych, nieużywanych właściwości ani zależności dodanych „na przyszłość”.

## 1. Minimalna zawartość

Każdy element POM-u musi mieć aktualne zastosowanie. Nie dodajemy:

- pustych elementów, np. pustych `name`, `description`, `url`, `licenses`, `developers` lub `scm`;
- właściwości, które nie są nigdzie używane;
- zależności potrzebnych dopiero w hipotetycznej przyszłości;
- pluginów bez wykonywanego celu albo potrzebnej konfiguracji;
- repozytoriów innych niż Maven Central bez konkretnego uzasadnienia;
- profili, które tylko komplikują zachowanie standardowego buildu;
- konfiguracji powtarzającej wartości domyślne Mavena lub parenta bez potrzeby.

Jeżeli opcjonalna sekcja nie wnosi informacji do buildu ani publikacji, usuwamy ją w całości zamiast
pozostawiać pusty szkielet wygenerowany przez narzędzie.

## 2. Układ sekcji

Sekcje POM-u utrzymujemy w stałej, przewidywalnej kolejności, o ile występują:

1. `modelVersion`;
2. `parent`;
3. współrzędne projektu: `groupId`, `artifactId`, `version`, `packaging`;
4. podstawowe metadane projektu, ale tylko jeśli są wypełnione i użyteczne;
5. `properties`;
6. `modules` w POM-ie agregującym;
7. `dependencyManagement`;
8. `dependencies`;
9. `build`, w tym `pluginManagement` i `plugins`;
10. `profiles`, wyłącznie jeśli są rzeczywiście potrzebne.

Między kolejnymi głównymi sekcjami pozostawiamy jedną albo dwie puste linie. Odstęp ma wizualnie
oddzielać odpowiedzialności, ale nie może rozciągać prostego POM-u na niepotrzebnie wiele ekranów.

Wewnątrz listy jednorodnych elementów, np. zależności, grupujemy wpisy tematycznie. Pojedyncza pusta
linia może oddzielać zależności produkcyjne, runtime, narzędziowe i testowe. Nie wstawiamy pustej linii
między każdy wpis bez wyraźnego powodu.

Wcięcia i łamanie linii muszą być konsekwentne w całym reactorze Maven. Nie formatujemy jednego
modułu w całkowicie odmienny sposób od parenta i pozostałych modułów.

## 3. Wersje w `properties`

Każda **jawnie określona** wersja zewnętrznej zależności, BOM-u albo pluginu musi znajdować się w
sekcji `properties`. Definicja zależności lub pluginu odwołuje się następnie do tej właściwości.

```xml
<properties>
    <java.version>25</java.version>
    <example-library.version>1.2.3</example-library.version>
    <example-plugin.version>4.5.6</example-plugin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>example-library</artifactId>
        <version>${example-library.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>com.example.build</groupId>
            <artifactId>example-plugin</artifactId>
            <version>${example-plugin.version}</version>
        </plugin>
    </plugins>
</build>
```

Nazwy właściwości są jednoznaczne i kończą się na `.version`, np. `archunit.version` albo
`sqlite-jdbc.version`. Gdy kilka artefaktów należy do jednej wersjonowanej rodziny, korzystamy z
jednej właściwości rodziny zamiast tworzyć kopie tej samej wersji.

Nie wpisujemy literału wersji bezpośrednio w `dependency`, `plugin`, `dependencyManagement` ani
`pluginManagement`.

## 4. Wersje zarządzane przez parent lub BOM

Jeżeli wersją zależności albo pluginu zarządza Spring Boot parent, zaimportowany BOM lub
`dependencyManagement` wspólnego parenta, nie deklarujemy jej ponownie w module i nie tworzymy
zbędnej właściwości wersji. Brak lokalnego `<version>` jest w takim przypadku świadomym użyciem
centralnego zarządzania, a nie naruszeniem reguły z poprzedniej sekcji.

Wersję nadpisujemy wyłącznie z konkretnego, udokumentowanego powodu, np. naprawy bezpieczeństwa albo
potwierdzonej niekompatybilności. Nadpisana wersja trafia do `properties`, a krótki komentarz wyjaśnia,
dlaczego nie korzystamy z wersji zarządzanej.

Wyjątek techniczny stanowią wersje modelu, projektu i parenta, których Maven wymaga w ich własnych
elementach. Reguła `properties` dotyczy wersji zależności, BOM-ów i pluginów, nie `modelVersion`,
`project.version` ani deklaracji `parent.version`.

Zależności między modułami tego samego reactora używają centralnego `dependencyManagement` albo
`${project.version}`. Nie utrzymujemy osobnych właściwości wersji dla każdego modułu vDeck.

## 5. Parent i moduły

Wspólny parent odpowiada za informacje rzeczywiście wspólne dla wszystkich modułów:

- wersję Javy i ustawienia kompilatora;
- wspólne zarządzanie wersjami zależności;
- wspólną konfigurację pluginów;
- reguły testów oraz przetwarzania adnotacji;
- listę modułów, jeśli pełni również rolę agregatora.

Moduł potomny zawiera tylko własny `artifactId`, zależności i konfigurację różniącą go od parenta.
Nie kopiuje ustawień odziedziczonych tylko po to, aby były widoczne lokalnie.

Konfigurację pluginu wspólną, ale niewykonywaną w każdym module, umieszczamy w `pluginManagement`.
Plugin trafia do `plugins` modułu dopiero wtedy, gdy ma być w nim faktycznie uruchamiany.

## 6. Zależności

Każda zależność musi odpowiadać rzeczywistemu użyciu w kodzie lub buildzie. Przed jej dodaniem należy
sprawdzić, czy funkcji nie zapewnia już JDK, Spring Boot albo istniejąca zależność.

Stosujemy najwęższy poprawny scope:

- domyślny `compile` tylko dla API potrzebnego podczas kompilacji i działania;
- `runtime` dla implementacji potrzebnych wyłącznie podczas działania;
- `test` dla bibliotek używanych tylko przez testy;
- `provided` tylko wtedy, gdy środowisko wykonawcze rzeczywiście dostarcza zależność.

`optional` stosujemy zgodnie z semantyką Mavena, nie jako zamiennik poprawnego scope. Wykluczenia
transitive dependencies wymagają konkretnego powodu i nie mogą maskować nierozwiązanej
niekompatybilności wersji.

Zależności porządkujemy w stabilnych grupach. W obrębie grupy stosujemy konsekwentną kolejność,
najlepiej według `groupId` i `artifactId`, chyba że kolejność funkcjonalna jest wyraźnie czytelniejsza.

## 7. Pluginy i proces budowania

Plugin dodajemy tylko wtedy, gdy wykonuje potrzebną część buildu, weryfikacji albo pakowania.
Konfiguracja powinna być możliwie mała i korzystać z ustawień parenta.

W przypadku `maven-compiler-plugin` każda lista `annotationProcessorPaths` musi odpowiadać procesorom
rzeczywiście używanym w danym przebiegu. Jeżeli procesor, np. Lombok, jest potrzebny zarówno dla kodu
produkcyjnego, jak i testowego, odpowiednia konfiguracja musi obejmować `default-compile` oraz
`default-testCompile`.

Nie uruchamiamy tej samej pracy przez kilka pluginów. Nie wiążemy ciężkich zadań z domyślnym cyklem
życia, jeśli nie są potrzebne przy każdym zwykłym `test` lub `package`.

## 8. Profile

Profile Maven są ostatecznością dla różnic procesu budowania, a nie mechanizmem konfiguracji
zachowania aplikacji. Konfiguracja infrastruktury runtime należy do Spring Boot i `application.yaml`,
argumentów lub zmiennych środowiskowych. Ustawienia aplikacyjne edytowalne przez użytkownika nie
należą ani do profili, ani do `application.yaml` — ich kanałem jest zewnętrzny plik `vDeck.properties`
opisany w `first_design_ideas.md` §5.

Profil musi mieć jasno określony cel i nie może powodować, że standardowy build bez profilu jest
niepełny albo niewiarygodny. Nie tworzymy profili zależnych od konkretnego komputera programisty.

## 9. Komentarze

Komentarz XML wyjaśnia „dlaczego”, np. wymagane obejście błędu pluginu, świadome nadpisanie wersji lub
ograniczenie Spring Boot 4. Nie komentujemy oczywistego znaczenia standardowych sekcji.

Komentarz tymczasowy powinien wskazywać warunek, po którego spełnieniu można go usunąć. Nie
pozostawiamy zakomentowanych zależności ani pluginów — historię zapewnia system kontroli wersji.

## 10. Weryfikacja zmian POM

Po zmianie POM-u uruchamiamy Maven Wrapper odpowiednio do zakresu zmiany. Co najmniej sprawdzamy, czy
Maven potrafi zbudować efektywny model i rozwiązać reactor. Dla zmiany zależności albo pluginu
uruchamiamy również testy modułu lub całego projektu.

Pomocne polecenia:

```powershell
.\mvnw.cmd help:effective-pom
.\mvnw.cmd test
.\mvnw.cmd package
```

`help:effective-pom` służy do diagnostyki dziedziczenia i zarządzanych wersji. Nie zapisujemy jego
wyniku jako źródłowego `pom.xml`.

## 11. Kontrola przed zakończeniem zmiany

Przed uznaniem zmiany Maven za kompletną należy sprawdzić:

- czy każdy element POM-u jest potrzebny i używany;
- czy nie pozostały puste metadane lub nieużywane właściwości;
- czy główne sekcje są uporządkowane i oddzielone jedną albo dwiema pustymi liniami;
- czy każda jawna wersja zależności, BOM-u lub pluginu pochodzi z `properties`;
- czy nie powielono wersji już zarządzanej przez parent lub BOM;
- czy scope i `optional` każdej zależności odpowiadają rzeczywistemu użyciu;
- czy konfiguracja wspólna znajduje się w parent albo `pluginManagement`;
- czy moduły potomne nie kopiują odziedziczonych ustawień;
- czy komentarze wyjaśniają tylko nieoczywiste decyzje;
- czy Maven Wrapper potwierdza poprawność efektywnego modelu oraz właściwych testów.

