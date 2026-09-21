# Zasady tworzenia interfejsu użytkownika

> Status: obowiązujące dla warstwy UI

Dokument określa wspólne zasady budowania i stylowania interfejsu vDeck. Uzupełnia reguły frontendu
z [`rules/clean-code.md`](rules/clean-code.md) oraz wymagania bezpieczeństwa warstwy webowej z
[`rules/security.md`](rules/security.md). Nie powiela ich; w razie sprzeczności obowiązuje kolejność
pierwszeństwa opisana w [`rules/README.md`](rules/README.md).

## 1. Zasady ogólne

- Interfejs powstaje z semantycznego HTML, arkuszy CSS i tylko takiej ilości JavaScriptu, jaka jest
  potrzebna do zachowania niedostępnego w HTML lub CSS.
- Układ musi działać przy różnych rozmiarach okna i po powiększeniu treści w przeglądarce. Nie
  opieramy podstawowego layoutu na sztywnych wymiarach odpowiadających jednemu ekranowi.
- **Strona nigdy się nie przewija — ani pionowo, ani poziomo.** Powłoka strony zajmuje dokładnie
  jeden widok (`body` z wysokością `100dvh` i `overflow: hidden`), a widok mieści się w tej
  przestrzeni. Gdy treść nie może się zmieścić — na przykład w poziomej orientacji telefonu —
  przewija się **wskazany obszar wewnętrzny**, a nie strona; obszar ten jest jawnie wyznaczony w CSS
  i nigdy nie chowa akcji poza zasięgiem użytkownika. Wyśrodkowanie w pionie nie może obcinać
  początku treści.
- **Każda zmiana UI musi wyglądać i działać dobrze na smartfonie i tablecie.** Obowiązuje w
  szczególności:
  - projektujemy od najwęższego ekranu w górę; punkty przełamania dodajemy tam, gdzie układ
    faktycznie przestaje się mieścić, a nie pod konkretny model urządzenia;
  - żaden widok nie może wymuszać przewijania strony przy szerokości 320 px — w żadnej osi;
  - każdy element dotykowy ma co najmniej 44 × 44 px obszaru aktywnego i odstęp od sąsiadów;
  - pola formularza mają rozmiar tekstu co najmniej 1 rem, aby przeglądarki mobilne nie
    przybliżały widoku po ustawieniu fokusu;
  - wysokości pełnoekranowe liczymy jednostkami dynamicznymi (`dvh`), bo paski przeglądarki
    mobilnej zmieniają wysokość widoku;
  - nie polegamy na `:hover` ani na `background-attachment: fixed`, których urządzenia dotykowe nie
    obsługują w sposób przewidywalny;
  - komponent o stałej wysokości na desktopie musi mieć określone zachowanie po zawinięciu treści
    na wąskim ekranie;
  - **widok musi działać w obu orientacjach — pionowej i poziomej.** W poziomej orientacji telefonu
    zostaje kilkaset pikseli wysokości: układ najpierw oddaje odstępy dekoracyjne, a dopiero gdy to
    nie wystarczy, przewija się obszar wewnętrzny widoku. Punkty przełamania zależne wyłącznie od
    szerokości uzupełniamy o warunek niskiej wysokości widoku tam, gdzie układ tego wymaga.
- Element interaktywny ma właściwy typ HTML, np. `button`, `a`, `input` lub `select`. Nie odtwarzamy
  ich zachowania za pomocą neutralnego `div`.
- Każda akcja musi mieć dostępną nazwę, widoczny stan fokusu oraz jednoznaczny stan `disabled`, jeśli
  chwilowo nie może zostać wykonana.
- Kolor, kształt ani sama ikona nie mogą być jedynym sposobem przekazania ważnego stanu lub błędu.
- Wspólny element używany przez więcej niż jeden widok wydzielamy do fragmentu Thymeleaf zgodnie z
  `rules/clean-code.md`. Nie tworzymy fragmentów ani komponentów na zapas.
- **Cały komponent nadający się do reużycia — na przykład wyspa akcji — jest wydzielony do osobnego
  szablonu w katalogu `templates/common/`**, po jednym pliku na komponent, nazwanym jak komponent
  (`common/island.html`). Widok osadza go przez `th:replace` i nie kopiuje jego znaczników.
- Styl takiego komponentu należy do arkusza globalnego, a nie do arkusza widoku: fragment jest
  dołączany w wielu widokach i nie może wnosić własnego `<link>` do arkusza.
- Zależności frontendowe są przechowywane lokalnie. Panel nie może wymagać dostępu do CDN ani innej
  usługi zewnętrznej, aby wyświetlić podstawowy interfejs.

## 2. Organizacja stylów

Arkusze CSS należą do `vdeck-app/src/main/resources/static/css/`. Szablony nie zawierają atrybutów
`style` ani bloków `<style>`.

- **Wszystkie zmienne CSS są zdefiniowane w jednym pliku ze zmiennymi**, `css/variables.css`. Plik
  zawiera wyłącznie deklaracje custom properties i nie definiuje żadnych reguł wyglądu.
- Każdy pozostały arkusz zaczyna się od `@import url("variables.css");` i korzysta z tokenów zamiast
  wartości dosłownych. Arkusz widoku importuje plik zmiennych samodzielnie, nie polega na tym, że
  zrobił to arkusz globalny.
- Żaden inny arkusz nie definiuje ani nie nadpisuje tokenu globalnego. Wartość używana w więcej niż
  jednym miejscu albo należąca do systemu wizualnego panelu trafia do pliku zmiennych; wartość
  wynikająca z pojedynczej reguły layoutu może pozostać w miejscu użycia.
- Baza dokumentu, layout i reużywalne komponenty — w tym komponenty z `templates/common/` — trafiają
  do arkusza globalnego.
- Styl dotyczący wyłącznie jednego widoku pozostaje w arkuszu tego widoku.
- Klasy CSS opisują rolę albo komponent, nie jego przypadkowy wygląd, np. `.action-button` zamiast
  `.green-button`.
- Każda oprawiona powierzchnia interfejsu — panel, karta widoku, wyspa — korzysta ze wspólnej klasy
  `.surface`. Domyślną teksturą powierzchni jest tekstura panelu; powierzchnia potrzebująca innej
  nadpisuje w swoim modyfikatorze wyłącznie właściwość `--surface-texture` i nie powtarza pozostałych
  reguł tła, ramy ani cienia.
- Do powtarzalnych kolorów, odstępów, promieni i rozmiarów używamy nazwanych custom properties.
- Stany komponentu zapisujemy konsekwentnie, np. `.is-running`, `.has-error` i `:disabled`.
- Każdy element interaktywny otrzymuje czytelne style co najmniej dla `:hover`, `:focus-visible`,
  `:active` i `:disabled`, jeśli dany stan ma zastosowanie.
- Ruch i animacje są krótkie, funkcjonalne oraz respektują `prefers-reduced-motion`.

Szczegółowe reguły nazewnictwa, specyficzności selektorów, komentarzy i reużywania stylów określa
[`rules/clean-code.md`](rules/clean-code.md).

## 3. Teksty interfejsu i tłumaczenia

Interfejs jest w języku polskim, ale żaden tekst nie jest wpisany na stałe w szablonie ani w kodzie
Javy. Obowiązuje [`AD-009`](architectural_decisions.md): teksty pochodzą z pliku
`vdeck-app/src/main/resources/messages.properties`, czytanego z classpathu przez domyślny
`MessageSource` Spring Boota. Panel jest jednojęzyczny — nie ma zewnętrznego katalogu tłumaczeń ani
plików dla innych języków.

- Szablon pobiera tekst wyłącznie przez `#{klucz}`. Dotyczy to również tytułu strony oraz atrybutów
  widocznych lub czytanych przez technologie asystujące, takich jak `aria-label`, `title` i `alt`.
- **Kod Javy nigdy nie przekazuje tekstu — przekazuje klucz.** Klucze tworzące zamknięty zbiór
  komunikatów jednego ekranu albo feature'a zapisujemy jako enum implementujący
  `shared.TranslationKeyIf`, na przykład `PasswordChangeMessage`, zamiast rozsypywać literały po
  kontrolerach.
- **Wyjątek, którego komunikat trafia do użytkownika, przyjmuje ten enum w konstruktorze** i nie
  buduje własnego tekstu. Wyjątek techniczny, którego komunikat trafia wyłącznie do logów, pozostaje
  bez klucza tłumaczenia.
- Plik tłumaczeń jest podzielony na grupy odpowiadające ekranowi albo komponentowi, a każda grupa ma
  nagłówek w komentarzu. Nowy klucz trafia do istniejącej grupy; nowa grupa dostaje własny nagłówek.
- Polskie znaki w pliku tekstów zapisujemy sekwencjami `\uXXXX`, zgodnie z
  [`rules/formating.md`](rules/formating.md) §1.
- Klucz zapisujemy w `lowerCamelCase` z kropkami jako separatorem obszarów, od ogólnego do
  szczegółowego: `changePassword.error.currentPasswordIncorrect`.
- Zestaw tekstów jest częścią aplikacji, więc zmiana brzmienia albo dodanie klucza to zmiana w
  repozytorium i nowe wydanie panelu, a nie edycja pliku w instalacji.
- Klucz bez tłumaczenia renderuje się jako sam klucz — widoczna usterka tekstu jest bezpieczniejsza
  niż błąd całej strony. Odpowiada za to `spring.messages.use-code-as-default-message`.
- Testy nie sprawdzają brzmienia tłumaczeń — operują na kluczach albo na tekstach, które same
  przygotowały. Treść pliku tłumaczeń nie jest kontraktem testu.

## 4. Ikony i sprite SVG

Wszystkie ikony aplikacji przechowujemy w jednym lokalnym spricie:

```text
vdeck-app/src/main/resources/static/icons/icons.svg
```

Nie dodajemy biblioteki ikon, fontu ikon ani frameworka CSS wyłącznie w celu wyświetlania ikon. Nową
ikonę dodajemy jako element `<symbol>` do istniejącego pliku. Nie tworzymy równoległych pojedynczych
plików SVG dla ikon interfejsu.

Każdy symbol:

- ma unikalny identyfikator w `kebab-case` z prefiksem `icon-`, np. `icon-play`;
- definiuje własny `viewBox`;
- nie definiuje `width` ani `height`;
- nie zawiera koloru zapisanego na stałe;
- domyślnie jest jednokolorowy i dziedziczy kolor komponentu przez `currentColor`;
- ma możliwie prostą geometrię i nie zawiera skryptów, stylów ani zewnętrznych odwołań.

Przykładowa zawartość sprite'a:

```svg
<svg xmlns="http://www.w3.org/2000/svg">
    <symbol id="icon-play" viewBox="0 0 16 16">
        <path d="M4 2.5v11l9-5.5-9-5.5z"/>
    </symbol>
    <symbol id="icon-stop" viewBox="0 0 16 16">
        <rect x="3" y="3" width="10" height="10"/>
    </symbol>
</svg>
```

Symbol osadzamy przez `<use>`. Element SVG w widoku otrzymuje wspólną klasę `.icon`, a jego rozmiar
i kolor kontroluje CSS komponentu:

```html
<button class="action-button" type="button" aria-label="Start server">
    <svg class="icon" aria-hidden="true" focusable="false">
        <use th:href="@{/icons/icons.svg#icon-play}"></use>
    </svg>
</button>
```

```css
.icon {
    display: block;
    width: 1.25rem;
    height: 1.25rem;
    fill: currentColor;
    pointer-events: none;
}
```

Nie kopiujemy zawartości `<path>` ze sprite'a do szablonów. Wyjątek wymaga konkretnej potrzeby, której
nie da się spełnić przez `<use>`, oraz krótkiego uzasadnienia w review.

## 5. Dostępność ikon

- Ikona znajdująca się obok widocznego tekstu jest dekoracyjna i otrzymuje `aria-hidden="true"`.
- Przycisk zawierający wyłącznie ikonę musi mieć dostępną nazwę na samym przycisku, zwykle przez
  `aria-label`. Sam identyfikator symbolu ani atrybut `title` nie stanowią wystarczającej nazwy.
- Dekoracyjne SVG otrzymuje `focusable="false"`, aby nie tworzyć dodatkowego punktu fokusu.
- Ikona sygnalizująca stan musi być uzupełniona tekstem dostępnym dla użytkownika lub technologią
  asystującą. Nie rozróżniamy stanów wyłącznie kolorem ikony.
- Rozmiar obszaru aktywnego przycisku nie zależy od rozmiaru samej ikony. Kontrolka musi pozostać
  wygodna do obsługi myszą i dotykiem.

## 6. Dostęp do zasobów statycznych

Sprite jest częścią aplikacji i podlega tym samym zasadom bezpieczeństwa co pozostałe zasoby
statyczne. Konfiguracja Spring Security oraz filtry aplikacji muszą pozwalać pobrać `/icons/icons.svg`
na każdym ekranie, który go używa.

Jeżeli ikony zostaną użyte na ekranie wymuszonej zmiany hasła, filtr ograniczający dostęp przed
zmianą hasła musi jawnie przepuszczać wymagany zasób. Udostępnienie sprite'a nie może jednocześnie
rozszerzać dostępu do endpointów administracyjnych.

## 7. Kontrola przed zakończeniem zmiany UI

Przed uznaniem zmiany interfejsu za kompletną sprawdzamy:

- semantykę HTML, hierarchię nagłówków i dostępne nazwy kontrolek;
- obsługę klawiaturą, widoczność fokusu oraz stany `hover`, `active` i `disabled`;
- zachowanie przy zwężeniu okna i powiększeniu treści;
- wygląd i obsługę na szerokościach smartfonu i tabletu, w orientacji pionowej i poziomej, brak
  przewijania strony w obu osiach przy 320 px oraz rozmiar obszarów dotykowych;
- czy informacja nie jest przekazywana wyłącznie kolorem lub ikoną;
- czy wspólne style i elementy nie zostały zduplikowane między widokami;
- czy reużywalny komponent leży w `templates/common/`, a widoki osadzają go zamiast kopiować;
- czy żaden tekst nie został wpisany na stałe w szablonie ani w kodzie, a nowe klucze trafiły do
  właściwej grupy pliku tłumaczeń;
- czy nowe tokeny trafiły do `css/variables.css`, a każdy zmieniony arkusz importuje ten plik;
- czy każda nowa ikona trafiła do `icons.svg` i jest używana przez `<use>`;
- czy ikona dziedziczy kolor oraz nie zawiera skryptów, stylów lub zewnętrznych zasobów;
- czy sprite jest dostępny na wszystkich ekranach, które go wykorzystują;
- czy widok nie wymaga połączenia z zewnętrznym CDN.
