@echo off
rem Releases a vDeck version: checks that the version's change list describes at least one change,
rem builds the project with tests, sets the release version in all POMs, commits "Release <version>"
rem and pushes it, then sets the next <version>-SNAPSHOT, creates its empty change list, commits
rem "Development <version>-SNAPSHOT" and pushes it.
rem Keep this file pure ASCII: cmd.exe misparses multi-byte characters in batch files, so the
rem Polish messages below are written without diacritics.
setlocal

cd /d "%~dp0.."
set "MVNW=%CD%\mvnw.cmd"

where git >nul 2>&1 || (echo Nie znaleziono polecenia git. & goto :fail)

for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set "BRANCH=%%b"
git diff --quiet HEAD -- || (echo Repozytorium ma niezatwierdzone zmiany. Zatwierdz je lub odloz przed wydaniem. & goto :fail)

echo Odczytywanie biezacej wersji projektu...
set "CURRENT="
for /f "delims=" %%v in ('call "%MVNW%" -q help:evaluate -Dexpression^=project.version -DforceStdout') do set "CURRENT=%%v"
if not defined CURRENT (echo Nie udalo sie odczytac biezacej wersji projektu. & goto :fail)

echo.
echo Biezaca galaz:  %BRANCH%
echo Biezaca wersja: %CURRENT%
echo.

:ask_release
set "RELEASE="
set /p "RELEASE=Podaj numer wydawanej wersji (X.Y.Z): "
call :validate "%RELEASE%" || goto :ask_release

:ask_next
set "NEXT="
set /p "NEXT=Podaj numer nastepnej wersji (X.Y.Z, bez -SNAPSHOT): "
call :validate "%NEXT%" || goto :ask_next
if "%NEXT%"=="%RELEASE%" (echo Nastepna wersja musi sie roznic od wydawanej. & goto :ask_next)

set "REMOTE=origin"
set /p "REMOTE=Zdalne repozytorium [origin]: "
if not defined REMOTE set "REMOTE=origin"
git remote get-url "%REMOTE%" >nul 2>&1 || (echo Nie znaleziono zdalnego repozytorium %REMOTE%. & goto :fail)

git rev-parse -q --verify "refs/tags/v%RELEASE%" >nul && (echo Tag v%RELEASE% juz istnieje. & goto :fail)

rem The version's change list becomes the GitHub release description and the panel's "Co nowego" panel.
set "NOTES_DIR=vdeck-app\src\main\resources\release-notes"
set "NOTES=%NOTES_DIR%\%RELEASE%.md"
if not exist "%NOTES%" (echo Brak pliku zmian %NOTES%. Utworz go i opisz zmiany przed wydaniem. & goto :fail)
findstr /b /l /c:"- **" "%NOTES%" >nul || (echo Plik zmian %NOTES% nie opisuje zadnej zmiany. & goto :fail)

echo.
echo Podsumowanie:
echo   galaz:            %BRANCH% -^> %REMOTE%
echo   wydanie:          %RELEASE%   (commit "Release %RELEASE%")
echo   lista zmian:      %NOTES%
echo   nastepna wersja:  %NEXT%-SNAPSHOT   (commit "Development %NEXT%-SNAPSHOT", pusty plik zmian %NEXT%.md)
if /i "%BRANCH%"=="main" echo   Push wydania na main uruchomi publikacje GitHub Release (AD-023).
echo.
set "CONFIRM="
set /p "CONFIRM=Kontynuowac? [t/N]: "
if /i not "%CONFIRM%"=="t" (echo Przerwano. & goto :fail)

echo.
echo === Budowanie projektu z testami ===
call "%MVNW%" clean verify || (echo Budowanie nie powiodlo sie. & goto :fail)

echo.
echo === Ustawianie wersji %RELEASE% ===
call :set_version "%RELEASE%" || goto :fail
call :commit_and_push "Release %RELEASE%" || goto :fail

echo.
echo === Ustawianie wersji %NEXT%-SNAPSHOT ===
call :set_version "%NEXT%-SNAPSHOT" || goto :fail
call :create_next_notes || goto :fail
call :commit_and_push "Development %NEXT%-SNAPSHOT" || goto :fail

echo.
echo Wydano wersje %RELEASE%. Biezaca wersja: %NEXT%-SNAPSHOT.
endlocal
exit /b 0

:validate
echo %~1| findstr /r "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul || (
    echo Niepoprawny numer wersji "%~1". Oczekiwano formatu X.Y.Z, np. 1.2.0.
    exit /b 1
)
exit /b 0

:set_version
call "%MVNW%" -q versions:set "-DnewVersion=%~1" -DgenerateBackupPoms=false -DprocessAllModules=true || (
    echo Nie udalo sie ustawic wersji %~1.
    exit /b 1
)
exit /b 0

:create_next_notes
rem The template is copied rather than echoed, because this file stays ASCII and the headings are Polish.
set "NEXT_NOTES=%NOTES_DIR%\%NEXT%.md"
if not exist "%NEXT_NOTES%" (
    copy /y ".env\release-notes-template.md" "%NEXT_NOTES%" >nul || (
        echo Nie udalo sie utworzyc pliku zmian %NEXT_NOTES%.
        exit /b 1
    )
)
git add -- "%NEXT_NOTES%" || exit /b 1
exit /b 0

:commit_and_push
git add -u -- pom.xml "*/pom.xml" || exit /b 1
git commit -m "%~1" || (echo Nie udalo sie utworzyc commita "%~1". & exit /b 1)
git push "%REMOTE%" HEAD || (echo Nie udalo sie wypchnac commita "%~1". Wypchnij go recznie. & exit /b 1)
exit /b 0

:fail
echo.
echo Wydanie przerwane.
endlocal
exit /b 1
