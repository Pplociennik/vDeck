# Reguły testów integracyjnych

> Status: obowiązujące dla nowych i modyfikowanych testów integracyjnych

## 1. Zakres

Test integracyjny potwierdza współpracę co najmniej dwóch rzeczywistych elementów systemu, na
przykład konfiguracji Spring Security, MVC, persystencji i bazy danych. Nie używamy pełnego kontekstu
Springa do testowania logiki, którą można wiarygodnie objąć szybkim testem jednostkowym.

Klasy testów integracyjnych kończą się nazwą `IntegrationTest`. Testy danego feature'a pozostają w
pakiecie odpowiadającym temu feature'owi.

## 2. Izolacja

- Test nie korzysta ze współdzielonej bazy produkcyjnej, plików użytkownika, prawdziwych procesów ani
  zewnętrznej sieci.
- Zasoby plikowe i baza są tworzone w izolowanym katalogu tymczasowym. Test nie pozostawia po sobie
  danych w katalogu projektu ani instalacji.
- Każdy test jest niezależny od kolejności i stanu utworzonego przez inne metody. Gdy kontekst lub
  baza przenoszą stan między metodami, stan jest jawnie czyszczony albo kontekst odtwarzany.
- Konfiguracja testowa nadpisuje wyłącznie właściwości niezbędne do uruchomienia badanego scenariusza.

## 3. Struktura i asercje

Metody testowe stosują sekcje `// GIVEN`, `// WHEN`, `// THEN`, a wymagany Javadoc jest pisany po
angielsku zgodnie z `documentation.md`. Dla obiektów i wartości używamy AssertJ. Dopasowania
udostępniane bezpośrednio przez testowany adapter, takie jak result matchers MockMvc, są dozwolone,
gdy czytelniej opisują kontrakt HTTP.

Test powinien sprawdzać obserwowalny kontrakt, a nie prywatne szczegóły implementacji. Dla mechanizmu
bezpieczeństwa obejmuje to odpowiednio brak dostępu anonimowego, autoryzację, CSRF, przekierowania,
stan sesji i trwały efekt operacji.

## 4. Zależności i uruchamianie

Zależności testowe Springa deklarujemy tylko w module, który ich używa, z zakresem `test`. Test musi
działać przez wrapper Maven razem z pełnym zestawem testów repozytorium. Znane ostrzeżenie narzędzia
nie jest usprawiedliwieniem dla pominięcia błędu ani nieudanego testu.
