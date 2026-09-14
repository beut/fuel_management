# Research: Śledzenie miesięcznego limitu paliwa służbowego

**Feature**: [spec.md](./spec.md) | **Date**: 2026-09-14

Ten dokument rozstrzyga niewiadome techniczne (`NEEDS CLARIFICATION` z sekcji Technical Context planu) na podstawie wymagań specyfikacji, w szczególności decyzji z sekcji `## Clarifications` (OCR w pełni on-device, ostrzeganie zamiast blokowania duplikatów, limit zmienia się dopiero od kolejnego miesiąca, zdjęcia paragonów usuwane po 12 miesiącach). Częściowo wzorowane na architekturze referencyjnego projektu tego samego autora — [beut/water_storage](https://github.com/beut/water_storage) — natywnej aplikacji Android o bardzo podobnym kształcie (zdjęcie licznika → on-device OCR → lokalna baza Room → wyliczane zużycie względem progu).

## Język i platforma

- **Decision**: Kotlin, natywna aplikacja Android, minimalny poziom API 26 (Android 8.0).
- **Rationale**: Kotlin jest oficjalnie rekomendowanym językiem dla nowych aplikacji Android; API 26 zapewnia dostęp do nowoczesnych bibliotek (CameraX, WorkManager, ML Kit) przy pokryciu zdecydowanej większości aktywnych urządzeń. Ten sam wybór sprawdził się w referencyjnym projekcie water_storage o analogicznym zakresie.
- **Alternatives considered**: Flutter/React Native (cross-platform) — odrzucone, spec wprost wymaga aplikacji na Androida, a natywny dostęp do aparatu i on-device OCR jest prostszy i lepiej wspierany natywnie; niższy minimalny poziom API (np. 21) — odrzucony jako niepotrzebnie zwiększający nakład na kompatybilność wsteczną.

## Interfejs użytkownika

- **Decision**: Jetpack Compose jako toolkit UI.
- **Rationale**: Deklaratywny, oficjalnie rekomendowany przez Google standard dla nowych aplikacji Android; upraszcza budowę ekranów (dodawanie tankowania, historia, raporty, ustawienia limitu) i ich testowanie.
- **Alternatives considered**: Widoki XML (View system) — odrzucone jako starszy wzorzec wymagający więcej kodu bez dodatkowej korzyści dla tego zakresu.

## Rozpoznawanie liczby litrów z paragonu (OCR)

- **Decision**: Google ML Kit Text Recognition (model on-device, pakowany z aplikacją), uruchamiany wyłącznie na urządzeniu; wynik OCR (surowy tekst paragonu) jest następnie parsowany prostą heurystyką (wyszukiwanie liczby w pobliżu słów kluczowych typu „L", „LITRY", „ILOŚĆ") w celu wyodrębnienia liczby litrów.
- **Rationale**: Bezpośrednio realizuje FR-002 (OCR w pełni lokalny, offline) ustalone w sesji `/speckit-clarify`; ML Kit jest darmowy, dobrze udokumentowany, zoptymalizowany pod Android i nie wymaga wysyłania zdjęć poza urządzenie. Ten sam silnik OCR sprawdził się w water_storage do odczytu wartości licznika ze zdjęcia.
- **Alternatives considered**: Usługa OCR w chmurze (np. Cloud Vision API) — odrzucona, ponieważ wymagałaby stałego połączenia z internetem i wysyłania zdjęć paragonów (mogących zawierać częściowy numer karty) poza urządzenie, co jest sprzeczne z decyzją z sesji clarify; Tesseract (tess-two) — odrzucony jako mniej dokładny i trudniejszy w utrzymaniu niż aktywnie rozwijany ML Kit.

## Przechwytywanie zdjęcia paragonu

- **Decision**: CameraX (Jetpack) do zrobienia zdjęcia z podglądem na żywo; dodatkowo systemowy selektor plików (`ActivityResultContracts.PickVisualMedia` / `ACTION_GET_CONTENT`) do wyboru istniejącego zdjęcia z galerii (FR-001).
- **Rationale**: CameraX to oficjalna, rekomendowana biblioteka Android do obsługi aparatu, upraszczająca integrację z ML Kit oraz obsługę różnych urządzeń/orientacji; selektor systemowy pokrywa przypadek „użytkownik ma już zdjęcie paragonu" bez potrzeby własnej implementacji przeglądarki galerii.
- **Alternatives considered**: Wyłącznie intencja systemowa `MediaStore.ACTION_IMAGE_CAPTURE` — odrzucona jako mniej elastyczna przy podglądzie na żywo i integracji z automatycznym odczytem OCR.

## Przechowywanie danych

- **Decision**: Room (SQLite) dla danych strukturalnych (`FuelingEntry`, `MonthlyLimit`); zdjęcia paragonów przechowywane jako pliki w pamięci wewnętrznej aplikacji (app-specific storage), z referencją (ścieżką) zapisaną w rekordzie `FuelingEntry` w Room.
- **Rationale**: Zgodne z wymogiem lokalnego przechowywania danych bez synchronizacji w chmurze (FR-014); skala danych (rzędu kilkudziesięciu wpisów rocznie) jest trywialna dla SQLite. Przechowywanie zdjęć jako plików (a nie BLOB w bazie) jest standardową praktyką na Androidzie i ułatwia zarządzanie pamięcią oraz usuwanie ich po okresie retencji (FR-015).
- **Alternatives considered**: DataStore/Preferences — odrzucone jako nieodpowiednie dla danych relacyjnych (historia tankowań, historia zmian limitu); zewnętrzna baza chmurowa — odrzucona jako niezgodna z wymogiem przechowywania wyłącznie lokalnego.

## Wersjonowanie miesięcznego limitu

- **Decision**: `MonthlyLimit` przechowuje kolejne wartości limitu wraz z miesiącem kalendarzowym, od którego zaczynają obowiązywać (`effectiveFromMonth`, np. `2026-10`). Limit obowiązujący dla danego miesiąca to najnowszy rekord, którego `effectiveFromMonth <= dany miesiąc`. Zmiana limitu w ustawieniach zawsze tworzy nowy rekord z `effectiveFromMonth` ustawionym na najbliższy kolejny miesiąc kalendarzowy względem daty zmiany — nigdy nie nadpisuje wartości obowiązującej w bieżącym miesiącu.
- **Rationale**: Bezpośrednio realizuje decyzję z sesji `/speckit-clarify` (FR-006): zmiana limitu w trakcie miesiąca nie może wpływać na już trwający miesiąc. Model „historia wartości w czasie" (zamiast jednej mutowalnej wartości) eliminuje potrzebę jawnego zadania resetującego — wyliczenie pozostałych litrów (FR-007) zawsze odpytuje właściwy limit dla danego miesiąca w locie.
- **Alternatives considered**: Pojedyncza mutowalna wartość limitu z osobnym polem „nowa wartość od następnego miesiąca" — odrzucone jako bardziej skomplikowane w implementacji i trudniejsze do przetestowania niż prosty model historii wersji; harmonogram (WorkManager) faktycznie podmieniający wartość 1. dnia miesiąca — odrzucony jako niepotrzebny, skoro wybór właściwego rekordu można wyliczyć deterministycznie w zapytaniu bez efektu ubocznego zależnego od czasu wykonania zadania w tle.

## Wykrywanie potencjalnych duplikatów

- **Decision**: Przed zapisaniem nowego `FuelingEntry` system wykonuje zapytanie do Room o istniejące wpisy z identyczną datą i identyczną liczbą litrów; jeśli taki wpis istnieje, UI wyświetla nieblokujące ostrzeżenie z opcją „Zapisz mimo to” / „Anuluj”.
- **Rationale**: Realizuje FR-005a i decyzję z sesji `/speckit-clarify` (ostrzeganie, nie blokowanie). Prosta kontrola na poziomie zapytania SQL, bez dodatkowej tabeli czy usługi.
- **Alternatives considered**: Twarda unikalna reguła bazy danych (constraint) na `(data, litry)` — odrzucona, bo zablokowałaby świadome, prawidłowe powtórzenia (np. dwa tankowania tego samego dnia tą samą ilością litrów, dwoma różnymi pojazdami/kartami w przyszłej wersji).

## Czyszczenie zdjęć paragonów (retencja 12 miesięcy)

- **Decision**: WorkManager (`PeriodicWorkRequest`, uruchamiane raz dziennie) skanuje `FuelingEntry` z niepustym `photoPath` i datą tankowania starszą niż 12 miesięcy, usuwa powiązany plik zdjęcia z pamięci wewnętrznej i czyści pole `photoPath` w rekordzie, pozostawiając resztę danych wpisu nienaruszoną (FR-015).
- **Rationale**: WorkManager to standardowe, zgodne z wytycznymi Android rozwiązanie do zadań cyklicznych odpornych na ograniczenia systemu (Doze, restart urządzenia), nie wymaga działania aplikacji na pierwszym planie ani połączenia z internetem. Analogiczny mechanizm (`PeriodicWorkRequest`) zastosowano w water_storage do przypomnień.
- **Alternatives considered**: Sprawdzanie i czyszczenie „przy okazji” każdego uruchomienia aplikacji (bez WorkManager) — odrzucone, bo przy rzadkim uruchamianiu aplikacji zdjęcia mogłyby być usuwane z dużym opóźnieniem względem 12-miesięcznego terminu; `AlarmManager` bezpośrednio — odrzucony jako niższopoziomowy i mniej odporny na optymalizacje baterii niż WorkManager.

## Architektura aplikacji

- **Decision**: Pojedynczy moduł aplikacji Android (bez wydzielonych modułów Gradle), warstwowa struktura wewnątrz modułu: `data` (Room, pliki zdjęć, ML Kit), `domain` (wyliczanie pozostałego limitu, dobór obowiązującego `MonthlyLimit`, wykrywanie duplikatów, agregacje raportowe), `cleanup` (WorkManager — retencja zdjęć), `ui` (ekrany Compose, ViewModel). Wstrzykiwanie zależności ręczne (prosty kontener/fabryki, tzw. `AppContainer`), bez frameworka DI.
- **Rationale**: Zakres v1 (jedna karta/limit, brak backendu, brak wielu użytkowników) nie uzasadnia nakładu na framework DI (np. Hilt) ani podział na wiele modułów Gradle — zgodnie z zasadą prostoty (YAGNI). Ten sam wzorzec (`AppContainer`, podział `data`/`domain`/`ui`) zastosowano z powodzeniem w water_storage dla analogicznego zakresu.
- **Alternatives considered**: Hilt/Dagger — odrzucone jako nadmiarowe dla obecnego zakresu, do rozważenia, jeśli aplikacja urośnie (np. wsparcie wielu kart paliwowych); podział na moduły Gradle (`:data`, `:domain`, `:ui`) — odrzucony jako przedwczesna optymalizacja dla pojedynczego, niewielkiego modułu aplikacji.

## Testowanie

- **Decision**: JUnit4 + lokalne testy jednostkowe (wyliczanie pozostałego limitu, dobór obowiązującego `MonthlyLimit` dla miesiąca, wykrywanie duplikatów, agregacje raportowe) w `src/test`; Espresso + Compose Testing API dla testów instrumentowanych kluczowych przepływów UI (rejestrowanie tankowania ze zdjęcia, podgląd pozostałego limitu, historia, raport miesięczny) w `src/androidTest`.
- **Rationale**: Standardowy, w pełni wspierany przez Android Gradle Plugin zestaw narzędzi testowych; pozwala niezależnie testować logikę domenową (szybkie testy JVM, bez potrzeby emulatora) i przepływy UI zgodnie z niezależną testowalnością historyjek użytkownika w spec.md.
- **Alternatives considered**: Robolectric jako zamiennik testów instrumentowanych — pominięty w v1 dla uproszczenia zestawu narzędzi; można dodać później dla przyspieszenia CI.

## Cele wydajnościowe i ograniczenia

- **Decision**: Cały przepływ „zdjęcie paragonu → rozpoznane litry → zapisany wpis” (SC-001) musi zamknąć się w czasie zauważalnie krótszym niż limit 30 sekund określony w spec, z przetwarzaniem OCR wykonywanym asynchronicznie w tle (korutyny Kotlin), aby nie blokować UI. Aplikacja musi działać w pełni offline w zakresie OCR i przechowywania danych (FR-002, FR-014).
- **Rationale**: Bezpośrednio z SC-001 i FR-002/FR-014; on-device ML Kit Text Recognition typowo zwraca wynik dla pojedynczego zdjęcia w czasie rzędu pojedynczych sekund na współczesnym sprzęcie, co pozostawia zapas czasowy na interakcję użytkownika (potwierdzenie/korekta odczytu) w ramach 30-sekundowego budżetu.
- **Alternatives considered**: Brak — wartości wynikają wprost z zaakceptowanych kryteriów sukcesu w spec.md.

## Podsumowanie rozstrzygniętych niewiadomych (Technical Context)

| Niewiadoma | Rozstrzygnięcie |
|---|---|
| Language/Version | Kotlin, Android API 26+ |
| Primary Dependencies | Jetpack Compose, CameraX, ML Kit Text Recognition (on-device), Room, WorkManager |
| Storage | Room (SQLite) + pliki zdjęć w pamięci wewnętrznej aplikacji |
| Testing | JUnit4 (unit), Espresso + Compose Testing (instrumented) |
| Target Platform | Android (telefony), API 26+ |
| Project Type | mobile-app (pojedynczy moduł) |
| Performance Goals | Zgodne z SC-001…SC-006 z spec.md |
| Constraints | W pełni offline (FR-002, FR-014); jedna karta/limit na instalację; zmiana limitu obowiązuje od kolejnego miesiąca |
| Scale/Scope | Pojedynczy użytkownik, jedna karta, rzędu kilkudziesięciu wpisów/rok |
