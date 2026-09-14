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

- **Decision (zaktualizowana po testach na realnych paragonach)**: Tesseract4Android (`cz.adaptech.tesseract4android`, aktywnie rozwijany fork tess-two), dane językowe `pol.traineddata` (wariant "fast", ~4,7 MB) spakowane w `assets/` i kopiowane do prywatnego katalogu aplikacji przy pierwszym użyciu; uruchamiany wyłącznie na urządzeniu, z jawnie wymuszonym `PageSegMode.PSM_SINGLE_BLOCK`. Wynik OCR (tekst pogrupowany w linie/słowa z pozycją geometryczną) jest następnie parsowany heurystyką dopasowania liczby do najbliższego wiersza zawierającego słowo kluczowe ("LITR", "ILOŚĆ", "L") w celu wyodrębnienia liczby litrów.
- **Rationale**: Pierwotna decyzja (ML Kit Text Recognition, patrz niżej) została zweryfikowana na realnych zdjęciach paragonów podczas `/speckit-implement` i okazała się niewystarczająca: ML Kit niekontrolowanie segmentuje dwukolumnowy układ paragonu (etykieta po lewej, wartość po prawej) — obserwowano zarówno podział na dwa niezależne bloki (cała kolumna etykiet, potem cała kolumna wartości, w kolejności niezgodnej z układem wizualnym), jak i rozbicie pojedynczego słowa etykiety na kilka tokenów przez błędnie wstawioną spację — a publiczne API ML Kit nie daje żadnej kontroli nad tym zachowaniem. Tesseract pozwala jawnie wymusić `PSM_SINGLE_BLOCK` (to również jego wartość domyślna), każąc traktować obraz jako jeden spójny blok tekstu bez niezależnego wykrywania kolumn — dla wąskiego, jednokolumnowego zdjęcia paragonu daje to bardziej przewidywalną kolejność odczytu. Dodatkowa korzyść: dane językowe są w całości spakowane w APK od pierwszej instalacji (bez potrzeby jednorazowego pobrania modelu przez Google Play Services, jak w przypadku ML Kit), co jest zgodne z FR-002/FR-014 (praca w pełni offline) w jeszcze mocniejszym sensie.
- **Ograniczenie**: to nadal heurystyka, nie gwarancja — na paragonach na tyle zniekształconych (pogięty papier, słaby kontrast), że OCR w ogóle nie wykrywa potrzebnych etykiet, żadna technika segmentacji tego nie naprawi; w takim wypadku `suggestedLiters` MUSI pozostać `null` zamiast zgadywać między nierozróżnialnymi kandydatami (np. kwotą a ilością litrów) — użytkownik wprowadza wartość ręcznie (FR-003).
- **Alternatives considered**: Google ML Kit Text Recognition — pierwotny wybór, odrzucony po realnych testach z powodu niekontrolowanej segmentacji kolumn (patrz wyżej); usługa OCR w chmurze (np. Cloud Vision API) — odrzucona, ponieważ wymagałaby stałego połączenia z internetem i wysyłania zdjęć paragonów (mogących zawierać częściowy numer karty) poza urządzenie, co jest sprzeczne z decyzją z sesji clarify.

## Przechwytywanie zdjęcia paragonu

- **Decision**: CameraX (Jetpack) do zrobienia zdjęcia z podglądem na żywo; dodatkowo systemowy selektor plików (`ActivityResultContracts.PickVisualMedia` / `ACTION_GET_CONTENT`) do wyboru istniejącego zdjęcia z galerii (FR-001).
- **Rationale**: CameraX to oficjalna, rekomendowana biblioteka Android do obsługi aparatu, upraszczająca podgląd na żywo i obsługę różnych urządzeń/orientacji; zapisuje zdjęcie do pliku, który następnie trafia do `ReceiptOcrReader` (Tesseract) niezależnie od biblioteki aparatu. Selektor systemowy pokrywa przypadek „użytkownik ma już zdjęcie paragonu" bez potrzeby własnej implementacji przeglądarki galerii.
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
