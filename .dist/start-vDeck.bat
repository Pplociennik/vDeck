@echo off
rem Starts the vDeck panel with the Java runtime bundled in this package.
rem The script must stay in the Valheim installation directory, next to valheim_server.exe.
rem Keep this file pure ASCII: cmd.exe misparses multi-byte characters in batch files, so the
rem Polish messages below are written without diacritics.
setlocal EnableExtensions DisableDelayedExpansion

rem IP address the panel listens on. 127.0.0.1 keeps it reachable from this computer only.
rem Use this computer's LAN address or 0.0.0.0 only on a trusted network: the panel serves plain
rem HTTP, so passwords would cross the network unencrypted.
set "ADDRESS=127.0.0.1"

rem HTTP port of the panel. Change this value if port 8085 is already in use.
set "PORT=8085"

rem The script directory is the installation directory and the working directory.
pushd "%~dp0"
if errorlevel 1 goto directory_error

rem Only the bundled JRE is used; system Java, PATH and JAVA_HOME are ignored.
rem javaw.exe is required by the CTRL+C helper that stops the server.
if not exist "jre\bin\java.exe" goto missing_jre
if not exist "jre\bin\javaw.exe" goto missing_jre
if not exist "lib\vdeck.jar" goto missing_panel
if not exist "lib\vdeck-runner.jar" goto missing_runner

echo Uruchamianie vDeck. Panel: http://%ADDRESS%:%PORT%
echo Pozostaw to okno otwarte podczas pracy panelu.
rem --enable-native-access     allows the Windows API calls that start and stop the server
rem -Dvdeck.server-directory   installation directory; required because the JARs live in lib
rem --server.address           listen address taken from the ADDRESS variable above
rem --server.port              port taken from the PORT variable above
rem trailing arguments         any extra script arguments are passed on to the panel
"jre\bin\java.exe" --enable-native-access=ALL-UNNAMED "-Dvdeck.server-directory=%~dp0." -jar "lib\vdeck.jar" --server.address=%ADDRESS% --server.port=%PORT% %*
set "VDECK_EXIT_CODE=%ERRORLEVEL%"
popd
if "%VDECK_EXIT_CODE%"=="0" exit /b 0
echo vDeck zakonczyl prace z kodem %VDECK_EXIT_CODE%. Sprawdz komunikaty powyzej.
pause
exit /b %VDECK_EXIT_CODE%

rem Incomplete package: print the reason, pause so the window stays open, exit with code 1.
:missing_jre
echo Brakuje dolaczonego srodowiska Java w katalogu jre. Wypakuj cala paczke ZIP.
goto startup_error

:missing_panel
echo Brakuje pliku lib\vdeck.jar. Wypakuj cala paczke ZIP.
goto startup_error

:missing_runner
echo Brakuje pliku lib\vdeck-runner.jar. Jest potrzebny do zatrzymywania serwera.
goto startup_error

:startup_error
popd
pause
exit /b 1

:directory_error
echo Nie mozna otworzyc katalogu, w ktorym znajduje sie skrypt.
pause
exit /b 1
