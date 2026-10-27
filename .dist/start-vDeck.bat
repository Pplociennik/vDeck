@echo off
rem Starts vDeck with the Java runtime bundled in this package.
rem The script must stay in the Valheim installation directory, next to valheim_server.exe.
rem Keep this file pure ASCII: cmd.exe misparses multi-byte characters in batch files, so the
rem Polish messages below are written without diacritics.
rem
rem The panel address, port and automatic updates are set in .vDeck\config\vDeck-start.properties.
rem An update may replace this file while it runs, so the Java start is one parenthesized block
rem followed by "call exit /b" on the same line: cmd.exe has parsed it completely before Java starts,
rem never reads this file again and still returns the Java exit code (expanded only by the call).
setlocal EnableExtensions DisableDelayedExpansion

rem The script directory is the installation directory and the working directory.
cd /d "%~dp0"
if errorlevel 1 goto directory_error

rem Recovery contract (AD-024, recovery protocol 1): an existing journal means an interrupted update.
rem It is recovered by the independent updater copy in .vDeck\update\executor, even when the
rem installed jre or lib is missing. The journal is never parsed here.
set "VDECK_JAVA=jre\bin\java.exe"
set "VDECK_JAR=lib\vdeck-launcher.jar"
set "VDECK_MODE="
if exist ".vDeck\update\apply.journal" (
    set "VDECK_JAVA=.vDeck\update\executor\jre\bin\java.exe"
    set "VDECK_JAR=.vDeck\update\executor\vdeck-updater.jar"
    set "VDECK_MODE=--recover"
)
if not exist "%VDECK_JAVA%" goto missing_runtime
if not exist "%VDECK_JAR%" goto missing_runtime

rem Only the bundled JRE is used; system Java, PATH and JAVA_HOME are ignored.
rem -Xmx32m, SerialGC, TieredStopAtLevel  small footprint: the launcher mostly waits for the panel
rem -Dvdeck.server-directory               installation directory; required because the JARs live in lib
rem --                                     any extra script arguments are passed on to the panel
(
    "%VDECK_JAVA%" -Xmx32m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 "-Dvdeck.server-directory=%~dp0." -jar "%VDECK_JAR%" %VDECK_MODE% -- %*
) & call exit /b %%^ERRORLEVEL%%

rem Incomplete package, or an interrupted update without its recovery copy: explain, pause, exit 1.
:missing_runtime
if defined VDECK_MODE (
    echo Wykryto przerwana aktualizacje vDeck, ale brakuje kopii potrzebnej do jej cofniecia.
    echo Nie uruchamiaj panelu. Sposob naprawy opisuje instrukcja w rozdziale o aktualizacjach.
) else (
    echo Brakuje plikow vDeck w katalogach jre lub lib. Wypakuj cala paczke ZIP.
)
pause
exit /b 1

:directory_error
echo Nie mozna otworzyc katalogu, w ktorym znajduje sie skrypt.
pause
exit /b 1
