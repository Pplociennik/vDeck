vDeck — lokalny panel serwera Valheim

vDeck pozwala zarządzać dedykowanym serwerem Valheim przez przeglądarkę:
edytować ustawienia, uruchamiać i zatrzymywać serwer oraz obserwować jego stan.
Przed zatrzymaniem wykonuje kopię plików świata. Projekt jest we wczesnej fazie rozwoju.

WYMAGANIA
- Windows x64.
- Zainstalowany Valheim Dedicated Server, w tym valheim_server.exe.
- Przeglądarka internetowa i prawo zapisu w katalogu instalacji.
- Wolne miejsce na dane świata i nową kopię obok poprzedniej.

Pełna instrukcja obsługi ze zrzutami ekranu: vDeck-instrukcja.pdf.

Paczka zawiera Eclipse Temurin JRE 25. Nie trzeba instalować Javy ani ustawiać JAVA_HOME.
Pliki serwera Valheim nie są częścią tej paczki.

PIERWSZE URUCHOMIENIE
1. Wypakuj całą zawartość ZIP-a do katalogu Valheim Dedicated Server.
   start-vDeck.bat ma leżeć obok valheim_server.exe.
   Zachowaj podkatalogi lib (pliki programu), jre oraz .vDeck, w tym .vDeck\config.
2. Uruchom start-vDeck.bat. Skrypt korzysta wyłącznie z dołączonej Javy.
3. Poczekaj na uruchomienie panelu. Przeglądarka sama otworzy http://127.0.0.1:8085;
   jeżeli się nie otworzy, wpisz ten adres ręcznie.
4. Zaloguj się: użytkownik admin, hasło początkowe admin.
   Panel od razu wymaga ustawienia własnego hasła.
5. Uzupełnij panele menu „Serwer” (Ogólne, Dostęp, Świat i zapis, Kopie automatyczne)
   oraz ekran „Ustawienia gry”. Hasło serwera gry jest niezależne od hasła panelu
   i w dostarczonym profilu pozostaje puste.
6. Uruchom serwer przyciskiem Start. Pozostaw okno panelu otwarte.

USTAWIENIA I DANE
- .vDeck\config\vDeck-start.properties — adres i port panelu oraz automatyczne aktualizacje.
- .vDeck\config\vDeck.properties — ustawienia panelu i logów gry.
- .vDeck\config\valheim-server.properties — profil uruchomieniowy serwera Valheim.
- vdeck.db — konto administratora panelu; baza powstaje przy pierwszym uruchomieniu.
- valheim-data — domyślny katalog zapisu świata; można go zmienić w panelu.
- .vDeck\log — domyślne logi serwera gry (game.log.retention.*, domyślnie 14 dni).
- .vDeck\logs\panel — log każdego uruchomienia panelu (panel.log.retention.*, domyślnie
  30 dni; days=0 zachowuje wszystkie logi).
- .vDeck\shutdown-snapshots — kopie świata z zatrzymania; nazwę podkatalogu ustawia
  snapshot.directory w vDeck.properties.
- .vDeck\logs\launcher, .vDeck\logs\updater — przebieg uruchomień i aktualizacji (30 dni).
- .vDeck\update\backup — poprzednia wersja i ustawienia sprzed ostatniej aktualizacji.

Ścieżki w ustawieniach muszą być względne i prowadzić do katalogów wewnątrz instalacji.
Ręczne zmiany plików properties wykonuj przy wyłączonym panelu.

ZATRZYMYWANIE
Zatrzymaj serwer przyciskiem Stop i poczekaj na wynik. Panel zamkniesz przez Ctrl+C
w jego oknie; zamykanie może potrwać kilka minut. Nieudany Stop pozostawia serwer
uruchomiony. Przy zamknięciu panel postępuje z serwerem zgodnie z panel.close.strategy
w vDeck.properties: stop (domyślnie) zatrzymuje go, a gdy to się nie uda, wymusza
zakończenie; graceful tylko zatrzymuje; leave zostawia serwer uruchomiony. Wymuszone Kill wymaga potwierdzenia i może spowodować utratę postępu.
Kopia działającego świata nie gwarantuje spójnego zapisu gry.

AKTUALIZACJE
Paczka służy do pierwszej instalacji. Kolejne wersje vDeck instalują się same przy
uruchomieniu start-vDeck.bat, gdy serwer Valheim i panel są zatrzymane. Ustawienia,
baza vdeck.db, światy i kopie zostają zachowane. Bez internetu albo przy błędzie
pobierania uruchamia się dotychczasowa wersja. Wyłączenie: update.enabled=false
w .vDeck\config\vDeck-start.properties. Przerwaną aktualizację vDeck cofa sam przy
następnym uruchomieniu; naprawę w razie problemu opisuje rozdział „Aktualizacje vDeck”
w vDeck-instrukcja.pdf.

GDY PANEL NIE STARTUJE
Wypakuj całą paczkę i sprawdź komunikaty w oknie. Port 8085 musi być wolny.
Aby użyć innego portu, otwórz .vDeck\config\vDeck-start.properties w Notatniku
i zmień wartość panel.port=8085. Adres panelu zmieni się odpowiednio.

DOSTĘP Z INNYCH KOMPUTERÓW
Domyślnie panel działa tylko na tym komputerze (panel.address=127.0.0.1
w .vDeck\config\vDeck-start.properties).
Aby otworzyć go z innego urządzenia, wpisz tam adres IP tego komputera w sieci lokalnej
lub 0.0.0.0. Rób to wyłącznie w zaufanej sieci: panel używa HTTP bez szyfrowania,
więc hasła są przesyłane otwartym tekstem. Nie udostępniaj panelu w internecie.

GRA ZE ZNAJOMYMI PRZEZ INTERNET
Nie otwieraj portów na routerze: wystawia to komputer na cały internet. Zamiast tego
użyj prywatnej sieci Tailscale (zalecane) lub Hamachi. Szczegóły i instrukcje krok po
kroku znajdziesz w rozdziale „Gra ze znajomymi przez internet” w vDeck-instrukcja.pdf.

DOŁĄCZONE ŚRODOWISKO JAVA
Eclipse Temurin: https://adoptium.net/temurin/releases/?version=25
Dystrybucja JRE zachowuje katalog jre\legal oraz pozostałe informacje licencyjne
producenta. Nie usuwaj ich przy dalszym przekazywaniu paczki.
