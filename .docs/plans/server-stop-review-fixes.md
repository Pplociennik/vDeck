# Poprawki po review commita VDEC-8 (zatrzymanie serwera)

> Data: 2026-10-07 · Aktualizacja: 2026-10-12 · Status: wdrożone · Dotyczy commita `edb4c45`
> „[VDEC-8] Prepared server stopping actions.”

Dokument zbiera decyzje użytkownika dotyczące zgłoszeń z review commita `edb4c45` oraz
poprawki wdrożone dla zaakceptowanych punktów. Opisy problemów i przykładowy kod poniżej odnoszą się
do stanu z review; szczegóły aktualnego zachowania opisują `.docs/design.md` i dokumentacja projektowa.

## Podsumowanie decyzji

| #   | Zgłoszenie                                                  | Decyzja                                                            |
| --- | ----------------------------------------------------------- | ------------------------------------------------------------------ |
| 1   | Pomocnik CTRL+C uruchamiany przez `javaw.exe`               | Odrzucone: test ręczny potwierdził, że Stop działa                 |
| 2   | Wyjątek z `ProcessIdentity.capture` po utworzeniu procesu   | Wdrożone (§2)                                                    |
| 3   | Uszkodzony token odzyskiwania blokuje Start na zawsze       | Wdrożone (§3)                                                    |
| 4   | Twardy limit 32 snapshotów bez sprzątania                   | Wdrożone: ostatni kompletny snapshot, AD-019 (§4)                   |
| 5   | Panel nie startuje przy przekierowaniu ścieżki instalacji   | Wdrożone: kanonizacja i zaufane korzenie (§5) |
| 6   | Mylące komunikaty przy starcie w `ServerServiceImpl.launch` | Na razie bez zmian, zostanie zmienione później                     |
| 7   | Przegląd procesów co 2 s przy `RECOVERY_REQUIRED`           | Na razie bez zmian                                                 |
| 8   | `adopt()` niezgodne z AD-014                                | Wdrożone (§8)                                                    |
| 9   | `fileKey()` jest `null` na Windows                          | Wdrożone (§9)                                                    |
| 10  | Zdublowana kontrola katalogów w `BackupPathServiceImpl`     | Na razie bez zmian                                                 |

Kolejność wdrożenia: **2 → 3 → 5 → 4 → 8 → 9**. Punkty 2 i 3 są małe i usuwają
stany bez wyjścia. Punkt 5 zmienia sygnaturę `checkDirectory`, której używa też punkt 4.
Dokumentacja i diagramy są aktualizowane na końcu.

## 2. Wyjątek z `capture` po utworzeniu procesu

**Problem.** `ServerStateMachine.start( launchSpec, world )` najpierw wywołuje `doStart`, które
ustawia `current` i `status = STARTING`, a dopiero potem `ProcessIdentity.capture`. Jeśli
`capture` rzuci wyjątek, `launch` zostaje `null`. Stop, Kill i `shutdown()` wymagają
`launch != null`, więc działającym serwerem nie da się sterować. Po restarcie panel pokazuje
`RECOVERY_REQUIRED(null)` bez przycisku przejęcia.

**Poprawka.** Tożsamość procesu jest ustalana przed opublikowaniem stanu. Jeśli to się nie
uda, świeżo utworzony proces zostaje zakończony. AD-017 na to pozwala: sprzątanie nieudanego,
nieprzekazanego startu należy do launchera, a serwer nie zdążył jeszcze nic zapisać.

```java
// doStart(launchSpec, world), na wątku cyklu życia
RunningServer server = launcher.launch( launchSpec );
ProcessIdentity identity;
try {
    identity = ProcessIdentity.capture( server.handle(), launchSpec.executable() );
} catch ( RuntimeException exception ) {
    // Not handed over yet: no world progress exists, so cleaning up the failed start is allowed (AD-017).
    server.handle().destroyForcibly();
    throw exception;
}
current = server;
status = ServerStatus.starting( server, UUID.randomUUID().toString() );
launch = new ManagedLaunch( status.getLaunchId(), identity, world, server.logFile() );
// dalej: launchStore.write, onExit, confirmRunning, bez zmian
```

`doStart` dostaje wariant z `WorldContext`, a sprawdzenie tożsamości trafia między
`launcher.launch` a `current = server`. Błąd zapisu tokenu jest obsługiwany poprawnie już dziś:
`launch` jest ustawiony, więc Stop i Kill działają.

**Testy:** `capture` rzuca wyjątek → `destroyForcibly` wywołane, stan bez zmian;
`capture` kończy się sukcesem → `launch != null`.

## 3. Uszkodzony token blokuje Start na zawsze

**Problem.** `refreshRecovery()` przy błędzie odczytu tokenu ustawia `RECOVERY_REQUIRED(null)`
i wychodzi przed przeglądem procesów. Dodatkowo `ManagedLaunchStore.read()` wymaga, żeby
`saveDirectory` istniał (`checkDirectory( save )`). Sam zmieniony lub usunięty katalog zapisu
unieważnia więc cały token. Jedyne wyjście to ręcznie usunąć `.vDeck/server.pid`, nawet gdy
żaden `valheim_server.exe` nie działa.

**Poprawka, dwie części:**

1. **Błąd odczytu nie przerywa przeglądu procesów.** Przy błędzie `saved = null`, a przegląd
   idzie dalej:
   - jest żywy `valheim_server.exe` tej instalacji → `RECOVERY_REQUIRED(null)`, jak dziś.
     To słuszna blokada, bo działa nieznany serwer;
   - nie ma takiego procesu → plik trafia do kwarantanny
     (`server.pid` → `server.pid.invalid-<znacznik czasu>`), log `WARN`, stan `STOPPED`.
     Kwarantanna zamiast usuwania zachowuje ślad do diagnozy.
2. **`read()` nie sprawdza istnienia `saveDirectory`.** Zostaje sprawdzenie, czy ścieżka mieści
   się w instalacji (`startsWith`). Istnienie katalogu i brak przekierowań sprawdza snapshot
   w chwili Stop: `checkSourceDirectory` rzuca wtedy `WorldSaveMissingException`, który daje
   `STOP_FAILED(WORLD_SAVE_MISSING)`.

## 4. Tylko ostatni snapshot

**Zmiana decyzji.** `design.md` mówi dziś: „Historia ma twardy limit 32 wpisów. Pierwsza
implementacja nie usuwa istniejących snapshotów”. Nowa decyzja użytkownika: przechowywany
jest wyłącznie ostatni snapshot. Przy wdrożeniu trzeba zaktualizować `design.md` i dopisać
decyzję do `architectural_decisions.md`.

**Problem obecny.** Limit 32 wpisów liczy też pozostałe katalogi `.tmp-*`, a nic nie usuwa
starych snapshotów. Po około 32 zatrzymaniach każdy łagodny Stop kończy się
`STOP_FAILED(SNAPSHOT)` i zostaje tylko Kill.

**Poprawka w `FileWorldSnapshotStore`.** Stały cel `snapshot-latest` w miejsce
`snapshot-<operationId>`. Podmiana następuje dopiero po udanej kopii, więc błąd nigdy nie
zostawia instalacji bez kopii:

1. kopia do `.tmp-<uuid>`, jak dziś;
2. jeśli `snapshot-latest` istnieje: `ATOMIC_MOVE` na `.old-<uuid>`;
3. `ATOMIC_MOVE` z `.tmp-<uuid>` na `snapshot-latest`. Jeśli ten krok się nie uda,
   `.old-<uuid>` wraca na `snapshot-latest`;
4. usunięcie `.old-<uuid>`. Jeśli usuwanie się nie uda, wystarczy `WARN`, bo nowy snapshot
   jest już opublikowany.

Dodatkowo:

- usunąć `MAX_SNAPSHOTS` i liczenie wpisów w `ensureCapacity`. Zostaje limit bajtów i wolnego
  miejsca;
- na początku `create()` sprzątnąć pozostałości `.tmp-*` i `.old-*` po awarii. Jeśli istnieje
  `.old-*`, a nie ma `snapshot-latest` (awaria między krokami 2 i 3), najpierw przywrócić
  `.old-*`;
- przy sprawdzaniu wolnego miejsca liczyć tylko nowy zestaw, bo stary jest usuwany dopiero po
  publikacji. Przez chwilę na dysku są dwie kopie;
- operation id zostaje w `snapshot.properties`, więc wiadomo, z którego zatrzymania pochodzi
  kopia.

## 5. Ścieżka instalacji

**Problem.** Spacje i polskie znaki same w sobie nie są problemem. `Path` je obsługuje,
`ProcessBuilder` sam cytuje argumenty, a `Properties` je escapuje. `WindowsPath.equals`
ignoruje też wielkość liter, więc `d:\` i `D:\` są równe. Panel wywraca się na **różnicy
między ścieżką podaną a rzeczywistą**: krótkie nazwy 8.3 (`PROGRA~1`), dyski `subst`, dyski
sieciowe, junction albo symlink gdziekolwiek **powyżej** instalacji. Winne jest
`!part.toRealPath().equals( part )` w `FileWorldSnapshotStore.checkDirectory`, sprawdzane dla
każdego przodka aż do korzenia dysku. `ManagedLaunchStore` wywołuje je w konstruktorze beana
runtime, więc wyjątek wywraca cały kontekst Springa. `BackupPathServiceImpl.resolveRoot`
stosuje tę samą kontrolę do łańcucha katalogu backupów.

**Poprawka:** zaufać katalogowi, który administrator świadomie wybrał, i pilnować tylko tego,
co jest **pod nim**.

1. **Kanonizacja raz, na wejściu.** W `PathService` (oraz tam, gdzie runtime dostaje
   `installation`) katalog instalacji jest zamieniany na rzeczywistą ścieżkę:
   `installation = configured.toRealPath()`. Każda dalsza ścieżka jest rozwiązywana od tej
   formy.
2. **`checkDirectory( root, directory )` zamiast `checkDirectory( directory )`.** Sprawdza
   tylko części od `root` w dół. Każda z nich ma być katalogiem, nie symlinkiem i nie
   `isOther()` (tak Java na Windows zgłasza junction i inne punkty reparsowania). Sprawdzenie
   rzeczywistej ścieżki zostaje, ale porównuje z formą kanoniczną.

   ```java
   public static void checkDirectory( @Nonnull Path root, @Nonnull Path directory ) throws IOException {
       Path normalized = directory.normalize();
       if ( !normalized.startsWith( root ) ) {
           throw new IOException( "Directory leaves its trusted root" );
       }
       for ( Path part = normalized; part.startsWith( root ); part = part.getParent() ) {
           BasicFileAttributes attributes = Files.readAttributes( part, BasicFileAttributes.class, NOFOLLOW_LINKS );
           if ( !attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()
                   || !part.toRealPath().equals( part ) ) {
               throw new IOException( "Redirected or invalid directory" );
           }
           if ( part.equals( root ) ) {
               break;
           }
       }
   }
   ```

3. **Dwa zaufane korzenie:** instalacja (dla tokenu, `.vDeck` i zapisów świata) oraz katalog
   backupów (dla snapshotów). Katalog backupów (`<rodzic>/.vDeck-backups/<nazwa>`, AD-018)
   jest wyprowadzany z **kanonicznej** instalacji. Wtedy `BackupPathServiceImpl.resolveRoot`
   sprawdza tylko `.vDeck-backups` i niżej, a nie przodków rodzica.
4. **Test regresji:** instalacja w katalogu tymczasowym o nazwie ze spacją i polskimi znakami
   (`"Valheim Serwer ąę"`), a do tego, jeśli to możliwe, junction powyżej instalacji, przez
   `mklink /J` w teście z `@EnabledOnOs( WINDOWS )`. Panel ma wystartować, a junction **pod**
   instalacją ma zostać odrzucony.

## 8. `adopt()` a AD-014

**Problem.** `ServerServiceImpl.adopt()` opakowuje każdy błąd, także zwykłe odrzucenie (np.
nieaktualne id uruchomienia), w `ServerStopException( STOP_REJECTED, exception )` z przyczyną.
Handler loguje to jako `ERROR` ze stack trace i pokazuje komunikat „stop odrzucony” przy
próbie przejęcia. Zniknięcie procesu objawia się jako `NoSuchElementException` z
`orElseThrow()`.

**Poprawka**, analogicznie do `kill()`:

- nowy wyjątek procesu `ServerAdoptException` w `feature.server.exc`, wzorowany na
  `ServerKillException`;
- nowe klucze `ServerMessage.ADOPT_REJECTED` (`server.error.adoptRejected`) i `ADOPT_FAILED`
  (`server.error.adoptFailed`) plus teksty w `messages.properties`;
- `ServerServiceImpl.adopt`:

  ```java
  try {
      serverRuntime.adopt( launchId );
  } catch ( IllegalArgumentException | IllegalStateException exception ) {
      throw new ServerAdoptException( ServerMessage.ADOPT_REJECTED, null );
  } catch ( RuntimeException exception ) {
      throw new ServerAdoptException( ServerMessage.ADOPT_FAILED, exception );
  }
  ```

- w `ServerStateMachine.adopt` `orElseThrow()` zamienić na
  `orElseThrow( () -> new IllegalStateException( "Saved server process no longer exists" ) )`,
  żeby zniknięcie procesu było zwykłym odrzuceniem;
- `ServerExceptionHandler`: dopisać `ServerAdoptException` do handlera stop/kill albo dodać
  osobny `@ExceptionHandler`. Odrzucenie idzie na `INFO` bez stack trace, błąd na `ERROR`.

## 9. `fileKey()` jest `null` na Windows

**Problem.** `Stamp` w `FileWorldSnapshotStore` używa `BasicFileAttributes.fileKey()`, który na
Windows zwraca `null`. Sprawdzenie stałości tożsamości pliku porównuje więc `"null"` z
`"null"` i niczego nie wykrywa. Podmiana `.db` przez atomowy rename w trakcie kopiowania
przechodzi niezauważona i może powstać snapshot mieszający pliki z różnych zapisów świata.

**Uwaga:** czasu utworzenia pliku nie da się użyć jako zamiennika. Windows ma tzw. file
tunneling: jeśli w ciągu około 15 s plik zostanie usunięty lub przemianowany, a na jego miejscu
powstanie nowy o tej samej nazwie, nowy plik dziedziczy czas utworzenia starego. To dokładnie
scenariusz, który ta kontrola ma wykrywać.

**Poprawka:** prawdziwy identyfikator pliku przez FFM, bo projekt ma już `Win32`:

- w `runtime.windows` dodać `GetFileInformationByHandleEx( FileIdInfo )`, które zwraca numer
  seryjny woluminu i 128-bitowe `FileId` (działa też na ReFS). Plik otwierać przez
  `CreateFileW` z `FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE`, żeby nie blokować
  Valheima;
- interfejs SPI `FileIdentityReader` (`String identify( Path file )`) z implementacją
  Windows. `FileWorldSnapshotStore` dostaje go w konstruktorze, a `Stamp.key` przechowuje ten
  identyfikator zamiast `fileKey()`;
- zasada fail-closed: jeśli identyfikatora nie da się odczytać, próba snapshotu się nie udaje.
  Nie ma cichego powrotu do `"null"`, bo przywróciłby obecną lukę. Zostaje istniejący
  mechanizm 3 prób.

## Zaktualizowana dokumentacja

- `.docs/design.md`: retencja snapshotów (§4), kontrola ścieżek względem zaufanego korzenia
  (§5);
- `.docs/architectural_decisions.md`: nowa decyzja o przechowywaniu tylko ostatniego snapshotu;
- `dokumentacja/dokumentacja-projektowa.md` oraz diagramy
  `dokumentacja/diagramy/backup/przeplyw-snapshot.mmd` i
  `dokumentacja/diagramy/server/przeplyw-odzyskiwanie.mmd`, z ponownie wygenerowanymi `.svg`.

## Weryfikacja wdrożenia (2026-10-12)

- `.\mvnw.cmd test` — BUILD SUCCESS: 265 testów runtime i 605 testów aplikacji,
  bez błędów, niepowodzeń i pominięć.
- Regresje obejmują sprzątanie startu po błędzie tożsamości, zachowanie własności przy błędzie zapisu
  tokenu, kwarantannę przy pustym skanie i blokadę przy żywym procesie, odczyt tokenu po usunięciu
  saveDirectory oraz osobne odrzucenia i błędy adopcji.
- Snapshoty: ponad 32 operacje, rollback po anulowaniu i błędzie publikacji, odzyskanie `.old-*`,
  sprzątanie pozostałości, zachowanie starej kopii przy błędzie, retry i brak wiarygodnego FileId.
- Windows: rzeczywisty odczyt FileId wykrywa atomową podmianę przy jednakowym rozmiarze i mtime.
  Pełny kontekst Springa startuje z instalacji „Valheim Serwer ąę” przez junction powyżej niej;
  junction pod instalacją pozostaje odrzucany.
- Pięć zmienionych diagramów wygenerowano przez mermaid-cli z konfiguracją repozytorium.
  `git diff --check` nie wykazuje błędów białych znaków.

Starsze katalogi `snapshot-<operationId>` nie są objęte automatyczną migracją ani usuwaniem.
Próba Stop na rzeczywistym Valheim pozostaje weryfikacją wdrożeniową opisaną w planie bazowym.
