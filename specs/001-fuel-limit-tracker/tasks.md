---

description: "Task list template for feature implementation"
---

# Tasks: Śledzenie miesięcznego limitu paliwa służbowego

**Input**: Design documents from `/specs/001-fuel-limit-tracker/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: Nie zażądano jawnie testów automatycznych ani podejścia TDD w spec.md — poniższa lista nie zawiera dedykowanych zadań testowych; walidacja end-to-end odbywa się przez `quickstart.md` (T040). Framework testowy (JUnit4 + Espresso/Compose Testing z plan.md) jest skonfigurowany w Setup, gotowy do użycia, jeśli testy zostaną dodane później.

**Organization**: Zadania są pogrupowane wg historyjek użytkownika (US1–US4 ze spec.md), aby umożliwić niezależną implementację i testowanie każdej z nich.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Może być wykonane równolegle (inny plik, brak zależności od niedokończonych zadań)
- **[Story]**: Do której historyjki użytkownika należy zadanie (US1, US2, US3, US4)
- Ścieżki plików podane wprost w opisach

## Path Conventions

Projekt mobilny (Android, pojedynczy moduł) — zgodnie z `plan.md` → Project Structure:

- `android/app/src/main/kotlin/pl/fuelmanagement/tracker/` — kod aplikacji
- `android/app/src/main/res/` — zasoby (stringi, ikony)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicjalizacja projektu Android i podstawowej struktury

- [X] T001 Utworzyć strukturę projektu Gradle (`android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradlew`, `android/app/`) zgodnie z `plan.md` → Project Structure
- [X] T002 Skonfigurować `android/app/build.gradle.kts`: `applicationId`/`namespace = "pl.fuelmanagement.tracker"`, `minSdk = 26`, `compileSdk`/`targetSdk = 34`, `compose = true`, oraz zależności z research.md: Jetpack Compose (BOM + material3 + navigation-compose + lifecycle-viewmodel-compose), CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), `com.google.mlkit:text-recognition`, Room (`room-runtime`, `room-ktx`, `room-compiler` przez KSP), `androidx.work:work-runtime-ktx`, `kotlinx-coroutines-android`
- [X] T003 [P] Skonfigurować `android/app/src/main/AndroidManifest.xml`: uprawnienie `CAMERA`, deklaracja `FuelManagementApplication` jako `android:name`, deklaracja `MainActivity` jako launcher activity; potwierdzić, że manifest NIE deklaruje uprawnienia `INTERNET` (FR-014 — dane i przetwarzanie wyłącznie lokalne)
- [X] T004 [P] Skonfigurować `android/.editorconfig` (styl kodu Kotlin) oraz `android/app/src/main/res/values/colors.xml` z bazowym motywem Material3

**Checkpoint**: Projekt kompiluje się i uruchamia pustą aplikację

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Rdzeń infrastruktury (baza danych, repozytoria, logika wyliczania limitu, DI, nawigacja), od którego zależą wszystkie historyjki użytkownika

**⚠️ CRITICAL**: Żadna historyjka użytkownika nie może być zaimplementowana przed ukończeniem tej fazy

- [X] T005 [P] Utworzyć encję Room `FuelingEntryEntity` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/entities/FuelingEntryEntity.kt` z polami z data-model.md: `id` (Long, PK auto), `date` (LocalDate/epoch day, wymagane), `liters` (Decimal 0,01 — reguła: "liters musi być liczbą dodatnią"), `photoPath` (String, nullable), `source` (Enum `OCR`/`OCR_CORRECTED`/`MANUAL`), `note` (String, nullable), `createdAt` (Instant epoch millis)
- [X] T006 [P] Utworzyć encję Room `MonthlyLimitEntity` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/entities/MonthlyLimitEntity.kt` z polami z data-model.md: `id` (Long, PK auto), `liters` (Decimal 0,01 — reguła: "liters musi być liczbą dodatnią"), `effectiveFromMonth` (String `YYYY-MM`, wymagane), `createdAt` (Instant epoch millis)
- [X] T007 [P] Utworzyć `FuelingEntryDao` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/dao/FuelingEntryDao.kt`: insert/update/delete, zapytanie o wszystkie wpisy malejąco wg `date` (FR-010), zapytanie o wpisy z `date` w podanym miesiącu `YYYY-MM` (FR-007), zapytanie o wpis z dokładnie taką samą `date` i `liters` (FR-005a) — zależy od T005
- [X] T008 [P] Utworzyć `MonthlyLimitDao` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/dao/MonthlyLimitDao.kt`: insert, zapytanie zwracające rekord o najwyższym `effectiveFromMonth` spełniającym `effectiveFromMonth <= podany miesiąc` — reguła z data-model.md: "Limit obowiązujący dla danego miesiąca M = rekord z najwyższym effectiveFromMonth spełniającym effectiveFromMonth <= M" — zależy od T006
- [X] T009 Utworzyć `AppDatabase` (Room) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/AppDatabase.kt` rejestrującą `FuelingEntryEntity`, `MonthlyLimitEntity`, `FuelingEntryDao`, `MonthlyLimitDao`, wraz z klasą `Converters` (`@TypeConverter`) obsługującą konwersję `LocalDate`↔epoch day, `Instant`↔epoch millis oraz enum `FuelingEntryEntity.source`↔String, wymaganą przez pola z T005/T006 — zależy od T005, T006, T007, T008
- [X] T010 [P] Utworzyć `FuelingEntryRepository` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/FuelingEntryRepository.kt` opakowujący `FuelingEntryDao` (CRUD, zapytanie o miesiąc, sprawdzenie duplikatu) — zależy od T009
- [X] T011 [P] Utworzyć `MonthlyLimitRepository` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/db/MonthlyLimitRepository.kt` opakowujący `MonthlyLimitDao`, z metodą `addLimitChange(liters)` ustawiającą `effectiveFromMonth` zgodnie z regułą z data-model.md: "Dodanie nowego MonthlyLimit... MUST ustawić effectiveFromMonth na najbliższy kolejny miesiąc kalendarzowy względem daty zmiany — nigdy na bieżący ani wcześniejszy miesiąc", z wyjątkiem "pierwsze ustawienie limitu (brak jakiegokolwiek wcześniejszego rekordu) MOŻE mieć effectiveFromMonth ustawiony na bieżący miesiąc" — zależy od T009
- [X] T012 [P] Zaimplementować czystą funkcję domenową `MonthlyLimitResolver` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/domain/limit/MonthlyLimitResolver.kt` wybierającą obowiązujący `MonthlyLimit` dla danego miesiąca z listy rekordów, wg tej samej reguły co T008 (bez zależności od bazy danych — testowalna jednostkowo)
- [X] T013 Zaimplementować `MonthlySummaryCalculator` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/domain/limit/MonthlySummaryCalculator.kt` wyliczający `MonthlySummary` per data-model.md: `totalLitersFueled` = suma `liters` wpisów z danego miesiąca; `remainingLiters` = `applicableLimit − totalLitersFueled` (`null`, jeśli `applicableLimit` jest `null`); `isOverLimit` = `true`, gdy `remainingLiters < 0` — zależy od T012
- [X] T014 Utworzyć `AppContainer` (ręczny kontener DI) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/AppContainer.kt` łączący `AppDatabase`, `FuelingEntryRepository`, `MonthlyLimitRepository`, `MonthlyLimitResolver`, `MonthlySummaryCalculator` — zależy od T009, T010, T011, T012, T013
- [X] T015 Utworzyć `FuelManagementApplication` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/FuelManagementApplication.kt` inicjalizującą `AppContainer` — zależy od T014
- [X] T016 Utworzyć `MainActivity` i szkielet `AppNavHost` (puste trasy Compose dla home/capture/history/reports/settings) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/MainActivity.kt` i `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/AppNavHost.kt` — zależy od T015

**Checkpoint**: Fundament gotowy — implementacja historyjek użytkownika może się rozpocząć

---

## Phase 3: User Story 1 - Rejestrowanie tankowania na podstawie zdjęcia paragonu (Priority: P1) 🎯 MVP

**Goal**: Użytkownik robi zdjęcie paragonu (lub wybiera z galerii / wpisuje ręcznie), aplikacja lokalnie rozpoznaje liczbę litrów, użytkownik potwierdza/koryguje, wpis zapisuje się w bazie z ostrzeżeniem o ewentualnym duplikacie.

**Independent Test**: Zrobić zdjęcie przykładowego paragonu, potwierdzić odczytaną liczbę litrów i sprawdzić, że wpis pojawia się w historii tankowań (spec.md → US1 Acceptance Scenarios).

### Implementation for User Story 1

- [X] T017 [P] [US1] Utworzyć `PhotoStorage` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/photo/PhotoStorage.kt`: zapisuje zrobione/wybrane zdjęcie paragonu jako plik w pamięci wewnętrznej aplikacji (research.md → "Przechowywanie danych"), zwraca ścieżkę do `FuelingEntryEntity.photoPath` (FR-001)
- [X] T018 [P] [US1] Utworzyć `ReceiptOcrReader` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/data/ocr/ReceiptOcrReader.kt` używający ML Kit Text Recognition (w pełni on-device, offline — FR-002) do wyodrębnienia surowego tekstu ze zdjęcia paragonu, a następnie sparsowania liczby litrów heurystyką bliskości słów kluczowych (research.md → "Rozpoznawanie liczby litrów z paragonu")
- [X] T019 [US1] Zaimplementować `DuplicateEntryChecker` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/domain/duplicate/DuplicateEntryChecker.kt` sprawdzający przez `FuelingEntryRepository`, czy istnieje wpis z identyczną `date` i identyczną `liters`, zgodnie z FR-005a: "System MUSI ostrzec użytkownika przed zapisaniem nowego wpisu, jeśli w bazie istnieje już wpis z taką samą datą i taką samą liczbą litrów, pozostawiając użytkownikowi decyzję o zapisaniu mimo ostrzeżenia" — zależy od T010
- [X] T020 [US1] Utworzyć `CameraCaptureScreen` (Compose) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/capture/CameraCaptureScreen.kt` z podglądem na żywo CameraX do zrobienia zdjęcia paragonu oraz akcją wyboru zdjęcia z galerii (FR-001); gdy użytkownik odmówi uprawnienia do aparatu lub galerii, wyświetlić komunikat wyjaśniający oraz przycisk kierujący do ustawień systemowych aplikacji, z zachowaniem możliwości przejścia do ręcznego wpisu (T021) bez zdjęcia (Edge Case: „brak uprawnień do aparatu/galerii")
- [X] T021 [US1] Utworzyć `ReadingConfirmationScreen` (Compose) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/capture/ReadingConfirmationScreen.kt`: wyświetla rozpoznaną liczbę litrów jako edytowalne pole do potwierdzenia/korekty przed zapisem (FR-003); obsługuje ścieżkę pełnego ręcznego wpisu (data + litry, bez zdjęcia) gdy OCR zawiedzie lub zdjęcie jest niedostępne (FR-004); pokazuje ostrzeżenie o duplikacie z T019 z akcjami „Zapisz mimo to” / „Anuluj” (FR-005a)
- [X] T022 [US1] Utworzyć `CaptureViewModel` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/capture/CaptureViewModel.kt` spinający: zdjęcie/wybór zdjęcia (T017) → rozpoznanie OCR (T018) → potwierdzenie/korekta użytkownika → sprawdzenie duplikatu (T019) → zapis przez `FuelingEntryRepository` (T010), ustawiając `FuelingEntryEntity.source` na `OCR`, `OCR_CORRECTED` lub `MANUAL` odpowiednio do ścieżki (FR-005) — zależy od T017, T018, T019, T010
- [X] T023 [US1] Podpiąć przepływ dodawania tankowania do `AppNavHost` i dodać akcję „Dodaj tankowanie” na `HomeScreen` (FR-001) — zależy od T016, T020, T021, T022

**Checkpoint**: User Story 1 w pełni funkcjonalna i testowalna niezależnie

---

## Phase 4: User Story 2 - Podgląd pozostałego limitu litrów w bieżącym miesiącu (Priority: P1)

**Goal**: Ekran główny natychmiast pokazuje, ile litrów pozostało do wykorzystania w bieżącym miesiącu, z wyraźnym sygnałem przekroczenia limitu.

**Independent Test**: Ustawić limit miesięczny i kilka wpisów tankowań z konkretnymi datami/litrami, sprawdzić, czy wyświetlona pozostała wartość odpowiada wyliczeniu limit − suma litrów od początku miesiąca (spec.md → US2 Independent Test).

### Implementation for User Story 2

- [X] T024 [US2] Utworzyć `HomeViewModel` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/home/HomeViewModel.kt` wyliczający `MonthlySummary` dla bieżącego miesiąca kalendarzowego przez `MonthlySummaryCalculator` (T013), `MonthlyLimitRepository` (T011) i `FuelingEntryRepository` (T010), udostępniając `remainingLiters` i `isOverLimit` (FR-007) — zależy od T013, T010, T011
- [X] T025 [US2] Utworzyć `HomeScreen` (Compose) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/home/HomeScreen.kt` wyświetlający pozostałe litry na bieżący miesiąc oraz wyraźnie odróżnialny stan przekroczenia limitu, gdy `isOverLimit == true` (FR-007, FR-009) — zależy od T024
- [X] T026 [US2] Dodać w `HomeScreen` stan pusty z zachętą do przejścia do Ustawień, gdy `applicableLimit == null` (Edge Case ze spec.md: „użytkownik jeszcze nie ustawił limitu miesięcznego") — zależy od T025
- [X] T027 [US2] Ustawić `HomeScreen` jako start destination w `AppNavHost` (FR-007) — zależy od T016, T025

**Checkpoint**: User Stories 1 i 2 działają niezależnie

---

## Phase 5: User Story 3 - Automatyczny reset limitu pierwszego dnia miesiąca (Priority: P2)

**Goal**: Zmiana limitu w ustawieniach obowiązuje dopiero od kolejnego miesiąca; bieżący miesiąc rozliczany jest wg dotychczasowej wartości, a przejście do nowego miesiąca automatycznie odsłania pełny limit bez utraty historii.

**Independent Test**: Ustawić datę systemową na koniec miesiąca z zarejestrowanymi tankowaniami, przesunąć na 1. dzień kolejnego miesiąca i zweryfikować, że pozostały limit wraca do pełnej wartości, a wcześniejsze wpisy nadal widoczne (spec.md → US3 Independent Test).

### Implementation for User Story 3

- [X] T028 [US3] Utworzyć `SettingsScreen` (Compose) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/settings/SettingsScreen.kt` z formularzem ustawienia/zmiany miesięcznego limitu litrów (FR-006)
- [X] T029 [US3] Utworzyć `SettingsViewModel` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/settings/SettingsViewModel.kt`: przy zmianie limitu wywołuje `MonthlyLimitRepository.addLimitChange` (T011), które ustawia `effectiveFromMonth` na kolejny miesiąc kalendarzowy, stosując wyjątek z data-model.md (bieżący miesiąc) tylko, gdy nie istnieje żaden wcześniejszy rekord `MonthlyLimit` (FR-006) — zależy od T011
- [X] T030 [US3] Podpiąć `SettingsScreen` do `AppNavHost` z punktem wejścia z `HomeScreen` (FR-006). Uwaga: żadna dodatkowa logika resetu nie jest potrzebna — `HomeViewModel` (T024) i `MonthlySummaryCalculator` (T013) już wyznaczają obowiązujący limit i sumę tankowań per miesiąc kalendarzowy, więc reset następuje automatycznie z chwilą zmiany miesiąca w dacie urządzenia (FR-008) — zależy od T016, T024, T028, T029

**Checkpoint**: User Stories 1, 2 i 3 działają niezależnie

---

## Phase 6: User Story 4 - Podstawowe raportowanie zużycia paliwa (Priority: P3)

**Goal**: Historia wszystkich tankowań, podsumowanie wybranego miesiąca i porównanie zużycia między miesiącami.

**Independent Test**: Zasilić bazę danymi z co najmniej dwóch różnych miesięcy i sprawdzić, czy historia, podsumowanie miesięczne oraz porównanie miesiąc do miesiąca poprawnie odzwierciedlają te dane (spec.md → US4 Independent Test).

### Implementation for User Story 4

- [X] T031 [P] [US4] Utworzyć `HistoryScreen` (Compose) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/history/HistoryScreen.kt` z chronologiczną (malejąco) listą wszystkich wpisów tankowań z datą i liczbą litrów oraz akcjami edycji/usunięcia przy każdym wpisie (FR-010, FR-013)
- [X] T032 [US4] Utworzyć `HistoryViewModel` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/history/HistoryViewModel.kt` ładujący wpisy przez `FuelingEntryRepository` (T010) i udostępniający operacje edycji i usunięcia wpisu (FR-013) — zależy od T010
- [X] T033 [P] [US4] Zaimplementować `MonthComparisonCalculator` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/domain/report/MonthComparisonCalculator.kt` budujący `MonthlySummary` (T013) dla każdego z co najmniej dwóch wybranych miesięcy i zwracający je jako listę do zestawienia obok siebie (FR-012) — zależy od T013
- [X] T034 [US4] Utworzyć `ReportsScreen` (Compose) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/reports/ReportsScreen.kt` z wyborem miesiąca pokazującym `MonthlySummary` (suma litrów, limit, wykorzystanie) dla wybranego miesiąca (FR-011) oraz widokiem porównania renderującym wynik `MonthComparisonCalculator` dla dwóch wybranych miesięcy (FR-012); gdy `totalLitersFueled == 0` dla wybranego miesiąca, wyświetlić czytelny stan pusty (np. „Brak tankowań w tym miesiącu") zamiast pustego wykresu/tabeli (Edge Case: „brak tankowań w danym miesiącu")
- [X] T035 [US4] Utworzyć `ReportsViewModel` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/ui/reports/ReportsViewModel.kt` spinający `MonthlySummaryCalculator` (T013) i `MonthComparisonCalculator` (T033) z `ReportsScreen` — zależy od T033
- [X] T036 [US4] Podpiąć `HistoryScreen` i `ReportsScreen` do `AppNavHost` z punktami wejścia z `HomeScreen` (FR-010, FR-011, FR-012) — zależy od T016, T031, T032, T034, T035

**Checkpoint**: Wszystkie historyjki użytkownika (US1–US4) działają niezależnie

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Ulepszenia dotyczące wielu historyjek / wymagań systemowych bez dedykowanej historyjki użytkownika (FR-015)

- [X] T037 [P] Zaimplementować `PhotoRetentionWorker` (WorkManager `CoroutineWorker`) w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/cleanup/PhotoRetentionWorker.kt`: dla każdego `FuelingEntry` z niepustym `photoPath` i `date` starszą niż 12 miesięcy usuwa plik zdjęcia i czyści `photoPath`, pozostawiając resztę pól bez zmian, zgodnie z FR-015: "System MUSI automatycznie usuwać zdjęcie paragonu... po upływie 12 miesięcy od daty tankowania, zachowując przy tym wszystkie pozostałe dane wpisu" — zależy od T010, T017
- [X] T038 [P] Zarejestrować `PhotoRetentionWorker` jako codzienny `PeriodicWorkRequest` w `FuelManagementApplication.onCreate` w `android/app/src/main/kotlin/pl/fuelmanagement/tracker/FuelManagementApplication.kt` (research.md → "Czyszczenie zdjęć paragonów") — zależy od T037, T015
- [X] T039 [P] Dodać polskie zasoby tekstowe UI dla wszystkich ekranów (home, capture, history, reports, settings) w `android/app/src/main/res/values/strings.xml`
- [X] T040 Przeprowadzić walidację `quickstart.md` end-to-end dla US1–US4, w tym test działania OCR offline i przypadków brzegowych (ostrzeżenie o duplikacie, brak skonfigurowanego limitu, retencja zdjęć) — zależy od wszystkich poprzednich zadań. **STATUS: częściowo wykonano** — doinstalowano lokalnie (bez roota) JDK 17, potwierdzono zaakceptowane licencje Android SDK i uruchomiono `./gradlew assembleDebug`: **build kończy się sukcesem** (`app-debug.apk` wygenerowany). Po drodze naprawiono dwa realne błędy wykryte dopiero przez kompilator: (1) `„Zapisz"` mieszające cudzysłów otwierający z prostym w literale Kotlina — przedwcześnie zamykało string i psuło parsowanie pliku (`ReadingConfirmationScreen.kt`); (2) przestarzałe `Divider()` zamienione na `HorizontalDivider()`. Wcześniejsza poprawka braku `TypeConverter` dla `FuelEntrySource` (patrz `data/db/Converters.kt`) również zweryfikowana jako poprawna — `kspDebugKotlin` przechodzi. **Nadal nie wykonano**: uruchomienia na realnym urządzeniu/emulatorze (brak w tym środowisku) — przepływy US1–US4 z `quickstart.md`, a zwłaszcza jakość heurystyki OCR na prawdziwych paragonach, wymagają weryfikacji przez użytkownika w Android Studio.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Brak zależności — można zacząć od razu
- **Foundational (Phase 2)**: Zależy od ukończenia Setup — BLOKUJE wszystkie historyjki użytkownika
- **User Stories (Phase 3–6)**: Wszystkie zależą od ukończenia fazy Foundational
  - US1 (P1) i US2 (P1) mogą być realizowane równolegle po Foundational
  - US3 (P2) zależy technicznie na `HomeViewModel`/`HomeScreen` z US2 (T024) do podpięcia punktu wejścia w T030, poza tym niezależna
  - US4 (P3) zależy tylko od Foundational (T010, T013), niezależna od US1–US3 poza wspólnym punktem wejścia z `HomeScreen`
- **Polish (Phase 7)**: T037–T039 zależą tylko od Foundational (T010, T015, T017 z US1); T040 zależy od wszystkich poprzednich zadań

### User Story Dependencies

- **User Story 1 (P1)**: Może zacząć się po Foundational (Phase 2) — brak zależności od innych historyjek
- **User Story 2 (P1)**: Może zacząć się po Foundational (Phase 2) — brak zależności od innych historyjek
- **User Story 3 (P2)**: Może zacząć się po Foundational (Phase 2); integruje punkt wejścia z US2 (T024) w T030, ale jest niezależnie testowalna
- **User Story 4 (P3)**: Może zacząć się po Foundational (Phase 2) — brak zależności od innych historyjek

### Within Each User Story

- Modele/logika domenowa przed ekranami UI
- Ekrany UI przed podpięciem do nawigacji
- Historyjka kompletna przed przejściem do kolejnej wg priorytetu

### Parallel Opportunities

- T003, T004 (Setup) mogą być wykonywane równolegle po T001/T002
- T005, T006 (encje Room) mogą być wykonywane równolegle
- T007, T008 (DAO) mogą być wykonywane równolegle (każde zależy tylko od swojej encji)
- T010, T011 (repozytoria) mogą być wykonywane równolegle po T009
- T012 (MonthlyLimitResolver) może być rozwijany równolegle z T005–T011 (czysta logika, bez zależności od bazy danych)
- Po ukończeniu Foundational: US1, US2 i US4 mogą być realizowane równolegle (różni deweloperzy); US3 wymaga jedynie ukończonego T024 z US2 przed T030
- T017, T018 (US1) mogą być wykonywane równolegle
- T031, T033 (US4) mogą być wykonywane równolegle
- T037, T038, T039 (Polish) mogą być wykonywane równolegle

---

## Parallel Example: Foundational Phase

```bash
# Uruchom encje Room równolegle:
Task: "Utworzyć encję Room FuelingEntryEntity w data/db/entities/FuelingEntryEntity.kt"
Task: "Utworzyć encję Room MonthlyLimitEntity w data/db/entities/MonthlyLimitEntity.kt"

# Po ukończeniu encji, uruchom DAO równolegle:
Task: "Utworzyć FuelingEntryDao w data/db/dao/FuelingEntryDao.kt"
Task: "Utworzyć MonthlyLimitDao w data/db/dao/MonthlyLimitDao.kt"
```

## Parallel Example: User Story 1

```bash
Task: "Utworzyć PhotoStorage w data/photo/PhotoStorage.kt"
Task: "Utworzyć ReceiptOcrReader w data/ocr/ReceiptOcrReader.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Ukończyć Phase 1: Setup
2. Ukończyć Phase 2: Foundational (KRYTYCZNE — blokuje wszystkie historyjki)
3. Ukończyć Phase 3: User Story 1 (rejestrowanie tankowania)
4. Ukończyć Phase 4: User Story 2 (podgląd pozostałego limitu)
5. **STOP i ZWALIDUJ**: te dwie historyjki razem stanowią minimalną użyteczną wersję aplikacji (rejestrowanie + podgląd limitu)
6. Wdróż/zademonstruj, jeśli gotowe

### Incremental Delivery

1. Setup + Foundational → fundament gotowy
2. Dodaj US1 → przetestuj niezależnie
3. Dodaj US2 → przetestuj niezależnie → **MVP gotowe**
4. Dodaj US3 (reset limitu) → przetestuj niezależnie → Wdróż/Zademonstruj
5. Dodaj US4 (raportowanie) → przetestuj niezależnie → Wdróż/Zademonstruj
6. Polish (retencja zdjęć, teksty UI, walidacja quickstart) → finalne wydanie v1

---

## Notes

- [P] = różne pliki, brak zależności od niedokończonych zadań
- Etykieta [Story] mapuje zadanie na konkretną historyjkę użytkownika dla identyfikowalności
- Każda historyjka użytkownika powinna być niezależnie kompletna i testowalna
- Commituj po każdym zadaniu lub logicznej grupie zadań
- Zatrzymaj się przy każdym checkpoincie, aby zwalidować historyjkę niezależnie
- Unikaj: niejasnych zadań, konfliktów w tym samym pliku, zależności między historyjkami łamiących ich niezależność

---

## Post-implementation: FR-016 (przebieg + kwota tankowania)

Po ukończeniu T001–T040 użytkownik poprosił bezpośrednio (poza formalną sesją `/speckit-clarify`/`/speckit-tasks`) o dodanie do wpisu opcjonalnego przebiegu i kwoty tankowania, odczytywanych przez OCR "jeśli uda się odczytać". Zaimplementowano jako FR-016 (spec.md) bez pełnego przebiegu przez `/speckit-plan`/`/speckit-tasks`, ponieważ zmiana jest addytywna i niewielka (dwa nowe nullable pola, bez wpływu na logikę limitu/US1–US4):

- `data-model.md` → `FuelingEntry.odometerKm`/`amountGrosze` udokumentowane.
- `data/db/entities/FuelingEntryEntity.kt`, `Money.kt` — nowe pola + konwersja grosze.
- `data/db/AppDatabase.kt` — wersja bazy 1 → 2 (`fallbackToDestructiveMigration`, aplikacja wciąż przed wydaniem).
- `data/ocr/ReceiptOcrReader.kt` — rozpoznawanie "Stan licznika"/"Kwota" obok litrów (wymaga słowa kluczowego, w odróżnieniu od litrów, żeby nie zgadywać).
- `ui/capture/*`, `ui/history/*` — pola edytowalne na ekranie potwierdzenia i w historii.

Zweryfikowane przez `./gradlew assembleDebug` (BUILD SUCCESSFUL); nie przetestowane na realnym paragonie/urządzeniu w tej sesji.
