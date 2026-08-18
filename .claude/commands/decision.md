---
description: Dopisuje zwięzłą decyzję architektoniczną do .docs/architectural_decisions.md
argument-hint: [treść decyzji, np. "stan serwera trzymamy w jednym serializowanym event queue"]
allowed-tools: Read, Edit, Write, Grep, Glob
---

Zarejestruj nową decyzję architektoniczną w `.docs/architectural_decisions.md`.

Decyzja podana przez użytkownika: $ARGUMENTS

## Krok 1 — kontekst

1. Przeczytaj `.docs/architectural_decisions.md`.
2. Jeżeli treść decyzji jest pusta lub na tyle ogólna, że nie da się z niej napisać jednego
   konkretnego zdania w trybie orzekającym — zadaj użytkownikowi pytania doprecyzowujące
   (czego dotyczy, co zostało wybrane, co zostało odrzucone) i dopiero potem pisz wpis.
   Nie zgaduj i nie dopisuj wpisu „na zapas”.
3. Przeczytaj `.docs/design.md` oraz odpowiedni plik z `.docs/rules/` **tylko wtedy**,
   gdy decyzja dotyka opisanego tam obszaru i musisz sprawdzić sprzeczność. Nie czytaj całego
   `.docs` rutynowo.

## Krok 2 — walidacja

- **Duplikat:** jeżeli istniejący wpis mówi to samo, nie dodawaj nowego. Zaproponuj doprecyzowanie
  istniejącego wpisu i poczekaj na zgodę użytkownika.
- **Sprzeczność:** jeżeli decyzja unieważnia istniejący wpis lub ustalenie z `design.md`,
  nazwij sprzeczność wprost i poproś o akceptację przed zapisem. Po akceptacji:
  stary wpis dostaje `**Status:** wycofana (YYYY-MM-DD, zastąpiona przez AD-NNN)` — nie usuwaj go,
  a nowy wpis dostaje wiersz `**Zastępuje:** AD-NNN`.
- **Zakres:** rejestr trzyma wyłącznie decyzje krytyczne i trwałe — granice modułów, kierunek
  zależności, cykl życia serwera, persystencję, bezpieczeństwo danych, kluczowe wybory
  technologiczne. Szczegół implementacyjny, konwencja kodu lub plan feature'a tu nie trafia;
  w takim wypadku powiedz użytkownikowi, gdzie to miejsce (`.docs/rules/*`, plan feature'a)
  i nie zapisuj wpisu.
- Jeżeli decyzja wymaga zmiany w `design.md`, zgłoś to użytkownikowi jako osobny krok,
  ale sam tego pliku w tej komendzie nie modyfikuj.

## Krok 3 — format wpisu

Jeżeli plik jest pusty, utwórz najpierw dokładnie taki nagłówek:

```markdown
# Krytyczne decyzje architektoniczne

> Status: nadrzędny rejestr decyzji projektu vDeck

Rejestr zawiera wyłącznie decyzje krytyczne i trwałe. Wpisy są niezmienne — decyzję zmienia się
przez dodanie nowej i oznaczenie starej jako wycofanej. Szczegóły architektury opisuje
`.docs/design.md`, reguły pracy — `.docs/rules/`.
```

Nowy wpis dopisz **na końcu pliku**, w tym formacie:

```markdown
## AD-NNN — <tytuł: maks. 6 słów, rzeczownikowy>

- **Status:** obowiązująca (<dzisiejsza data YYYY-MM-DD>)
- **Decyzja:** <jedno zdanie, tryb orzekający, konkretne nazwy typów/modułów/technologii>
- **Powód:** <jedno zdanie: realny problem lub ryzyko, które to rozwiązuje>
- **Konsekwencja:** <jedno zdanie: co jest teraz zabronione albo wymagane>
```

Zasady, których pilnujesz bezwzględnie:

- `NNN` to kolejny wolny numer, trzy cyfry, z zerami wiodącymi. Numeru nie ponawiasz po wycofanym wpisie.
- Cały wpis mieści się w **maks. 500 znakach**. Jeżeli nie mieści się — decyzja jest za szeroka;
  poproś użytkownika o rozbicie jej na osobne wpisy zamiast skracać ją do ogólnika.
- Każde z pól `Decyzja`, `Powód`, `Konsekwencja` to **dokładnie jedno zdanie**. Bez list zagnieżdżonych,
  bez akapitów, bez bloków kodu, bez opisu wariantów rozważanych i odrzuconych.
- Piszesz po polsku, konkretnie, bez wypełniaczy („w celu zapewnienia”, „należy pamiętać”,
  „z uwagi na fakt”). Nazwy klas, pakietów, plików i properties zapisujesz dokładnie jak w kodzie.
- Wiersz `**Zastępuje:**` dodajesz tylko wtedy, gdy wpis faktycznie coś wycofuje.

## Krok 4 — potwierdzenie

Po zapisie wypisz użytkownikowi wyłącznie dodany wpis oraz — jeżeli wystąpiły — informację
o wycofanym wpisie i o potrzebie aktualizacji `.docs/design.md`.
