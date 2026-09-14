# Quickstart: Śledzenie miesięcznego limitu paliwa służbowego

**Feature**: [spec.md](./spec.md) | **Data model**: [data-model.md](./data-model.md) | **Research**: [research.md](./research.md)

Ten przewodnik opisuje, jak uruchomić aplikację i ręcznie zweryfikować, że każda historyjka użytkownika ze spec.md działa end-to-end. Nie zawiera pełnego kodu implementacji — szczegóły implementacyjne trafiają do `tasks.md` i fazy implementacji.

## Wymagania wstępne

- Android Studio (najnowsza stabilna wersja) z zainstalowanym Android SDK API 26+.
- Urządzenie fizyczne z Androidem 8.0+ i aparatem, lub emulator z obsługą kamery (dla realnego testu OCR zalecane urządzenie fizyczne — emulatorowa kamera daje słabą jakość obrazu do rozpoznawania tekstu paragonu).
- Repozytorium sklonowane lokalnie; moduł aplikacji Android znajduje się w katalogu `android/` (patrz `plan.md` → Project Structure).

## Uruchomienie aplikacji

```bash
cd android
./gradlew installDebug
```

lub otworzyć katalog `android/` w Android Studio i uruchomić konfigurację `app` na podłączonym urządzeniu/emulatorze.

## Uruchomienie testów

```bash
cd android
./gradlew test                  # testy jednostkowe logiki domenowej (limit, duplikaty, raporty)
./gradlew connectedAndroidTest  # testy instrumentowane UI (Espresso + Compose Testing) na podłączonym urządzeniu/emulatorze
```

## Walidacja historyjek użytkownika

### US1 — Rejestrowanie tankowania na podstawie zdjęcia paragonu (P1)

1. Otwórz aplikację, przejdź do ekranu dodawania tankowania.
2. Naciśnij akcję „Zrób zdjęcie paragonu”.
3. Zrób wyraźne zdjęcie przykładowego paragonu ze stacji benzynowej w dobrym oświetleniu.
4. **Oczekiwany rezultat**: aplikacja pokazuje rozpoznaną liczbę litrów i prosi o potwierdzenie/korektę przed zapisem (FR-002, FR-003); po potwierdzeniu nowy wpis pojawia się w historii tankowań (FR-005).
5. Powtórz, robiąc celowo nieczytelne zdjęcie (np. rozmazane) — **oczekiwany rezultat**: aplikacja sygnalizuje nieudane rozpoznanie i pozwala wpisać liczbę litrów ręcznie (FR-003, FR-004).
6. Dodaj wpis bez zdjęcia, podając datę i litry ręcznie — **oczekiwany rezultat**: wpis zapisuje się poprawnie bez załączonego zdjęcia (FR-004).

### US2 — Podgląd pozostałego limitu litrów w bieżącym miesiącu (P1)

1. W ustawieniach ustaw testowy limit miesięczny (np. 100 l).
2. Zarejestruj kilka wpisów tankowań w bieżącym miesiącu (US1) o znanej sumie litrów.
3. **Oczekiwany rezultat**: ekran główny pokazuje `limit − suma zatankowanych litrów w bieżącym miesiącu` (FR-007), zgodne z ręcznym wyliczeniem.
4. Dodaj kolejne wpisy, aż suma przekroczy ustawiony limit — **oczekiwany rezultat**: aplikacja wyraźnie sygnalizuje przekroczenie limitu, ale nadal pozwala dodać kolejny wpis (FR-009).

### US3 — Automatyczny reset limitu pierwszego dnia miesiąca (P2)

1. Zarejestruj kilka wpisów tankowań z datami w przeszłym miesiącu (np. edytując datę wpisu na poprzedni miesiąc — patrz FR-013).
2. Otwórz ekran główny (dla bieżącej daty systemowej, czyli już w nowym miesiącu).
3. **Oczekiwany rezultat**: pozostały limit dla bieżącego miesiąca odpowiada pełnemu obowiązującemu limitowi, niepomniejszonemu o wpisy z poprzedniego miesiąca (FR-008).
4. Otwórz historię tankowań — **oczekiwany rezultat**: wpisy z poprzedniego miesiąca nadal są widoczne i niezmienione.
5. Zmień wartość limitu w ustawieniach w trakcie bieżącego miesiąca — **oczekiwany rezultat**: pozostały limit dla bieżącego miesiąca pozostaje wyliczony wg dotychczasowej wartości; nowa wartość widoczna jest dopiero po symulowanym przejściu do kolejnego miesiąca (FR-006, zob. `data-model.md` → `MonthlyLimit.effectiveFromMonth`).

### US4 — Podstawowe raportowanie zużycia paliwa (P3)

1. Zarejestruj wpisy tankowań rozłożone na co najmniej dwa różne miesiące.
2. Otwórz ekran historii — **oczekiwany rezultat**: chronologiczna lista wszystkich wpisów z datą i liczbą litrów (FR-010).
3. Otwórz ekran raportów i wybierz jeden z miesięcy — **oczekiwany rezultat**: widoczna suma litrów, obowiązujący limit i wykorzystanie dla tego miesiąca (FR-011).
4. W widoku porównania wybierz dwa miesiące — **oczekiwany rezultat**: zestawienie sumy litrów obu miesięcy obok siebie (FR-012).

## Weryfikacja działania offline (FR-002, FR-014)

1. Włącz tryb samolotowy na urządzeniu testowym.
2. Powtórz scenariusz US1 (zdjęcie paragonu → rozpoznanie liczby litrów → zapis wpisu).
3. **Oczekiwany rezultat**: rozpoznawanie liczby litrów (OCR) oraz zapis danych działają bez żadnego połączenia z internetem.

## Weryfikacja przypadków brzegowych

- Dodaj wpis z taką samą datą i liczbą litrów jak istniejący wpis — aplikacja MUST wyświetlić ostrzeżenie o możliwym duplikacie, ale pozwolić zapisać po potwierdzeniu (FR-005a, `data-model.md` → `FuelingEntry`).
- Nie ustawiaj żadnego limitu i otwórz ekran główny na czystej instalacji — aplikacja MUST poprosić o skonfigurowanie limitu zamiast pokazywać błędną/mylącą wartość.
- Zweryfikuj retencję zdjęć: wpis z datą starszą niż 12 miesięcy MUST mieć automatycznie usunięte zdjęcie paragonu (puste `photoPath`), przy zachowaniu pozostałych danych wpisu (FR-015). Ponieważ odczekanie 12 miesięcy w teście manualnym nie jest praktyczne, zweryfikuj to poprzez: dodanie wpisu ze zdjęciem, ręczną edycję jego daty na sprzed 12+ miesięcy (funkcja edycji z US4/FR-013) lub bezpośrednią modyfikację rekordu w bazie (np. przez `adb shell` / Database Inspector w Android Studio), a następnie ręczne jednorazowe uruchomienie `PhotoRetentionWorker` (np. przez `WorkManager` test driver lub odczekanie na najbliższe uruchomienie okresowego zadania) i sprawdzenie, że `photoPath` zostało wyczyszczone, a plik zdjęcia usunięty.

## Weryfikacja skuteczności OCR (SC-004)

Ponieważ SC-004 (≥80% wpisów z czytelnego zdjęcia ma poprawnie rozpoznaną liczbę litrów bez korekty) nie ma w v1 dedykowanej telemetrii, zweryfikuj go manualnie: zarejestruj tankowanie na podstawie co najmniej 10–20 czytelnych, realnych paragonów (US1) i porównaj, ile z nich zostało zapisanych ze `source = OCR` (bez korekty) wobec `OCR_CORRECTED` — np. przez Database Inspector w Android Studio na tabeli `FuelingEntry`. Udział `OCR` powinien wynosić ≥80%.
