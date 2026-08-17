# Krytyczne decyzje architektoniczne

> Status: nadrzędny rejestr decyzji projektu vDeck

Rejestr zawiera wyłącznie decyzje krytyczne i trwałe. Wpisy są niezmienne — decyzję zmienia się
przez dodanie nowej i oznaczenie starej jako wycofanej. Szczegóły architektury opisuje
`.docs/first_design_ideas.md`, reguły pracy — `.docs/rules/`.

## AD-001 — Priorytet wydajności i zużycia RAM

- **Status:** obowiązująca (2026-08-17)
- **Decyzja:** vDeck traktuje zużycie RAM oraz złożoność pamięciową i obliczeniową jako nadrzędne kryterium wyboru rozwiązań.
- **Powód:** Aplikacja ma działać płynnie na słabym sprzęcie, obok serwera gry zajmującego większość zasobów maszyny.
- **Konsekwencja:** Zabronione są struktury rosnące bez ograniczenia — logi i wyjście procesów przetwarzamy strumieniowo, w buforach o stałym rozmiarze.
