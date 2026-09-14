# Implementation Plan: Śledzenie miesięcznego limitu paliwa służbowego

**Branch**: `001-fuel-limit-tracker` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-fuel-limit-tracker/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Natywna aplikacja na Androida do śledzenia miesięcznego limitu paliwa na służbowej karcie. Użytkownik robi zdjęcie paragonu ze stacji benzynowej, aplikacja lokalnie (on-device, offline, Tesseract4Android) rozpoznaje liczbę zatankowanych litrów, a po potwierdzeniu/korekcie zapisuje wpis w lokalnej bazie danych. Ekran główny pokazuje, ile litrów pozostało do wykorzystania w bieżącym miesiącu (limit obowiązujący w tym miesiącu minus suma zatankowanych litrów od 1. dnia miesiąca) — reset następuje samoczynnie, bo wartość jest zawsze liczona względem bieżącego miesiąca kalendarzowego, bez potrzeby jawnego zadania resetującego. Zmiana limitu wchodzi w życie dopiero od kolejnego miesiąca, więc limit jest przechowywany jako historia wartości w czasie. Aplikacja udostępnia historię tankowań, podsumowanie miesięczne i porównanie miesiąc do miesiąca. Zdjęcia paragonów są automatycznie usuwane po 12 miesiącach. Dane przechowywane są wyłącznie lokalnie (Room + pliki zdjęć), v1 obsługuje jedną kartę/jeden limit na instalację, bez backendu i bez synchronizacji w chmurze.

## Technical Context

**Language/Version**: Kotlin, Android API 26+ (Android 8.0+)

**Primary Dependencies**: Jetpack Compose (UI), CameraX (przechwytywanie zdjęcia paragonu), Tesseract4Android — OCR w pełni on-device z jawną kontrolą segmentacji strony (`PSM_SINGLE_BLOCK`), dane językowe `pol.traineddata` spakowane w APK (zastąpił pierwotnie wybrany ML Kit Text Recognition po testach na realnych paragonach — patrz research.md), Room (lokalna baza danych), WorkManager (okresowe zadanie porządkowe usuwające zdjęcia paragonów starsze niż 12 miesięcy, FR-015)

**Storage**: Room (SQLite) dla danych strukturalnych (`FuelingEntry`, `MonthlyLimit`); zdjęcia paragonów jako pliki w pamięci wewnętrznej aplikacji, referencjonowane ścieżką z Room

**Testing**: JUnit4 dla testów jednostkowych logiki domenowej (wyliczanie pozostałego limitu, dobór obowiązującego limitu dla danego miesiąca, wykrywanie potencjalnych duplikatów) w `src/test`; Espresso + Compose Testing API dla testów instrumentowanych kluczowych przepływów UI (`src/androidTest`)

**Target Platform**: Android (telefony), API 26+

**Project Type**: mobile-app (pojedynczy moduł Android, bez osobnego backendu — patrz FR-002/FR-014: przetwarzanie i przechowywanie w pełni lokalne)

**Performance Goals**: Zgodnie z SC-001…SC-006 w spec.md — m.in. rejestracja tankowania (zdjęcie → zapisany wpis) w < 30 s; pozostałe litry widoczne natychmiast po otwarciu aplikacji; historia i porównanie miesięcy dostępne w ≤ 3 dotknięciach ekranu

**Constraints**: Aplikacja MUST działać w pełni offline w zakresie OCR i przechowywania danych (FR-002, FR-014); v1 obsługuje wyłącznie jedną służbową kartę/jeden limit na instalację (Assumptions); zmiana wartości limitu MUST obowiązywać dopiero od kolejnego miesiąca kalendarzowego (FR-006, decyzja z sesji `/speckit-clarify`), co wymaga przechowywania historii wartości limitu w czasie, a nie pojedynczej wartości bieżącej

**Scale/Scope**: Pojedynczy użytkownik, jedna karta paliwowa, rzędu kilkunastu-kilkudziesięciu wpisów tankowań rocznie — trywialna skala dla lokalnej bazy SQLite

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` zawiera wyłącznie nieuzupełniony szablon (placeholdery `[PRINCIPLE_1_NAME]` itd., brak ratyfikowanej wersji) — projekt nie ma jeszcze zdefiniowanych, wiążących zasad konstytucyjnych. W związku z tym brak jest specyficznych dla projektu bramek do wymuszenia na tym etapie; plan kieruje się ogólnymi dobrymi praktykami (prostota architektury — patrz research.md → „Architektura aplikacji", niezależna testowalność historyjek użytkownika, brak zbędnych zależności).

**Status**: PASS (brak zdefiniowanych zasad do naruszenia). Rekomendacja: uruchomić `/speckit-constitution`, gdy pojawią się pierwsze konkretne zasady projektu (np. wymagania dot. testowania, przechowywania danych, prywatności), i ponownie zweryfikować ten plan względem nich.

**Post-Phase 1 re-check**: Projekt (data-model.md, research.md) nie wprowadza elementów wymagających uzasadnienia w Complexity Tracking — architektura pozostaje jednomodułowa, bez dodatkowych warstw ponad to, co wynika wprost z wymagań. Status: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-fuel-limit-tracker/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify + /speckit-clarify)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

Brak katalogu `contracts/`: aplikacja nie eksponuje żadnego zewnętrznego interfejsu (API, protokołu) dla innych systemów ani użytkowników poza własnym UI — całe przetwarzanie (OCR, przechowywanie danych) jest lokalne na urządzeniu (FR-002, FR-014), więc krok „Define interface contracts" jest pomijany zgodnie z regułą „skip if project is purely internal".

### Source Code (repository root)

```text
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/pl/fuelmanagement/tracker/
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/           # Room: encje, DAO, baza (FuelingEntry, MonthlyLimit)
│   │   │   │   │   ├── photo/        # zapis/odczyt plików zdjęć paragonów w pamięci wewnętrznej
│   │   │   │   │   └── ocr/          # integracja z Tesseract4Android (parsowanie litrów/przebiegu/kwoty z tekstu paragonu)
│   │   │   │   ├── domain/
│   │   │   │   │   ├── limit/        # wyliczanie pozostałego limitu, dobór obowiązującego MonthlyLimit dla miesiąca
│   │   │   │   │   ├── duplicate/    # wykrywanie potencjalnego duplikatu wpisu (FR-005a)
│   │   │   │   │   └── report/       # agregacje do podsumowania miesiąca i porównania miesiąc do miesiąca
│   │   │   │   ├── cleanup/          # WorkManager: okresowe usuwanie zdjęć paragonów starszych niż 12 mies. (FR-015)
│   │   │   │   └── ui/
│   │   │   │       ├── home/         # ekran główny: pozostałe litry w bieżącym miesiącu, akcja dodania tankowania
│   │   │   │       ├── capture/      # przechwycenie/wybór zdjęcia paragonu + ekran potwierdzenia/korekty odczytu OCR
│   │   │   │       ├── history/      # historia tankowań, edycja/usunięcie wpisu
│   │   │   │       ├── reports/      # podsumowanie miesięczne i porównanie miesiąc do miesiąca
│   │   │   │       └── settings/     # ustawienie/zmiana miesięcznego limitu litrów
│   │   │   └── res/                  # zasoby Android (stringi, ikony, motyw)
│   │   ├── test/kotlin/...           # testy jednostkowe (domain: limit, duplicate, report)
│   │   └── androidTest/kotlin/...    # testy instrumentowane (Espresso + Compose Testing) dla US1–US4
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

**Structure Decision**: Pojedynczy moduł aplikacji Android (`android/app`) z wewnętrznym podziałem warstwowym `data` / `domain` / `cleanup` / `ui`, bez wydzielonych modułów Gradle ani osobnego backendu — architektura (podział `data`/`domain`/`ui`, ręczne DI, CameraX + on-device OCR + Room) analogiczna do wzorca z referencyjnego projektu [water_storage](https://github.com/beut/water_storage) (monitor zbiornika na podstawie zdjęć licznika), choć silnik OCR odbiegł od tego wzorca (Tesseract4Android zamiast ML Kit — patrz research.md), uzasadniona ograniczonym zakresem v1 (jedna karta/limit, brak wielu użytkowników, przetwarzanie w pełni lokalne).

## Complexity Tracking

*Brak — Constitution Check nie zgłosił naruszeń wymagających uzasadnienia.*
