# Data Model: Śledzenie miesięcznego limitu paliwa służbowego

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

Encje odpowiadają sekcji „Key Entities” w spec.md, uszczegółowionej o pola i reguły walidacji wynikające z wymagań funkcjonalnych (FR-001…FR-015) oraz decyzji z sesji `/speckit-clarify` (OCR w pełni on-device, ostrzeganie o duplikatach, limit obowiązujący od kolejnego miesiąca, retencja zdjęć 12 miesięcy).

## FuelingEntry (Wpis tankowania)

Reprezentuje pojedyncze zdarzenie zatankowania paliwa. Źródło: FR-001…FR-005a, FR-013, FR-015.

| Pole | Typ | Reguły |
|---|---|---|
| `id` | Long (PK, auto) | Unikalny identyfikator wpisu |
| `date` | LocalDate (epoch day) | Data tankowania; wymagane; wyznacza miesiąc kalendarzowy, do którego wpis jest zaliczany (FR-007) |
| `liters` | Decimal(0,01) | Liczba zatankowanych litrów, dokładność do 0,01 l; MUST być > 0 |
| `photoPath` | String, nullable | Ścieżka do pliku zdjęcia paragonu w pamięci wewnętrznej aplikacji; `null` dla wpisów dodanych bez zdjęcia (FR-004) lub po automatycznym usunięciu zdjęcia po 12 mies. (FR-015) |
| `source` | Enum(`OCR`, `OCR_CORRECTED`, `MANUAL`) | Czy `liters` pochodzi z automatycznego rozpoznania OCR, ręcznej korekty wyniku OCR, czy pełnego ręcznego wpisu (FR-003, FR-004, FR-005) |
| `odometerKm` | Long, nullable | Przebieg pojazdu w km w momencie tankowania; opcjonalny, wypełniany z OCR paragonu (pole "Stan licznika"), gdy uda się go jednoznacznie odczytać, z możliwością ręcznej korekty/uzupełnienia; nie wpływa na wyliczenie limitu miesięcznego |
| `amountGrosze` | Long, nullable | Kwota zapłacona za tankowanie, w groszach; opcjonalna, wypełniana z OCR paragonu (pole "Kwota"), z możliwością ręcznej korekty/uzupełnienia; nie wpływa na wyliczenie limitu miesięcznego (to wyłącznie `liters`) |
| `note` | String, nullable | Opcjonalna notatka użytkownika |
| `createdAt` | Instant (epoch millis) | Znacznik czasu utworzenia wpisu w bazie; używany tylko technicznie (sortowanie wpisów z tą samą `date`), nie wpływa na przypisanie do miesiąca |

**Reguły walidacji**:

- `liters` musi być liczbą dodatnią.
- Przed zapisem: jeśli istnieje inny `FuelingEntry` z identyczną `date` i identyczną `liters`, UI MUST wyświetlić ostrzeżenie o możliwym duplikacie i pozwolić użytkownikowi zapisać mimo to lub anulować (FR-005a) — nie jest to twarde ograniczenie bazy danych.
- Edycja i usunięcie wpisu (FR-013) są zawsze dozwolone, niezależnie od tego, do jakiego miesiąca (bieżącego czy przeszłego) należy wpis.

**Relacje**: Brak bezpośredniej relacji do `MonthlyLimit` — przynależność do miesiąca wynika z pola `date`, a obowiązujący limit dla tego miesiąca jest wyznaczany dynamicznie (patrz `MonthlyLimit` niżej i `MonthlySummary`).

## MonthlyLimit (Limit miesięczny)

Reprezentuje wartość limitu litrów przypisaną do służbowej karty paliwowej, z historią zmian w czasie. Źródło: FR-006, FR-007.

| Pole | Typ | Reguły |
|---|---|---|
| `id` | Long (PK, auto) | Unikalny identyfikator rekordu limitu |
| `liters` | Decimal(0,01) | Wartość limitu w litrach; MUST być > 0 |
| `effectiveFromMonth` | String (`YYYY-MM`) | Pierwszy miesiąc kalendarzowy, od którego ta wartość obowiązuje; wymagane |
| `createdAt` | Instant (epoch millis) | Znacznik czasu utworzenia rekordu (kiedy użytkownik dokonał zmiany) |

**Reguły walidacji**:

- `liters` musi być liczbą dodatnią.
- Dodanie nowego `MonthlyLimit` przez użytkownika (zmiana limitu) MUST ustawić `effectiveFromMonth` na najbliższy kolejny miesiąc kalendarzowy względem daty zmiany — nigdy na bieżący ani wcześniejszy miesiąc (FR-006, decyzja z `/speckit-clarify`). Wyjątek: pierwsze ustawienie limitu (brak jakiegokolwiek wcześniejszego rekordu) MOŻE mieć `effectiveFromMonth` ustawiony na bieżący miesiąc, aby limit obowiązywał od razu.
- Limit obowiązujący dla danego miesiąca `M` = rekord z najwyższym `effectiveFromMonth` spełniającym `effectiveFromMonth <= M` (jeśli taki nie istnieje, brak skonfigurowanego limitu — patrz Edge Case „brak limitu” w spec.md).

**Relacje**: Wiele rekordów `MonthlyLimit` tworzy historię w czasie; dla dowolnego miesiąca dokładnie jeden rekord jest „obowiązujący” (patrz reguła wyżej).

## MonthlySummary (Podsumowanie miesiąca) — encja wyliczana

Nie jest przechowywana bezpośrednio jako osobna tabela — wyliczana w warstwie domenowej z `FuelingEntry` i `MonthlyLimit` na potrzeby ekranu głównego, historii i raportów. Źródło: FR-007, FR-009, FR-011, FR-012.

| Pole | Typ | Reguła wyliczenia |
|---|---|---|
| `month` | String (`YYYY-MM`) | Miesiąc, którego dotyczy podsumowanie |
| `totalLitersFueled` | Decimal | Suma `FuelingEntry.liters` dla wpisów, których `date` mieści się w `month` |
| `applicableLimit` | Decimal, nullable | Wartość `MonthlyLimit` obowiązująca dla `month` (patrz reguła wyboru w `MonthlyLimit`); `null`, jeśli żaden limit jeszcze nie skonfigurowany na ten miesiąc |
| `remainingLiters` | Decimal, nullable | `applicableLimit − totalLitersFueled`; `null`, jeśli `applicableLimit` jest `null` |
| `isOverLimit` | Boolean | `true`, gdy `remainingLiters < 0` (FR-009) |

**Wykorzystanie**:

- Ekran główny (US2) pokazuje `MonthlySummary` dla bieżącego miesiąca (`month = dzisiejszy YYYY-MM`).
- Ekran raportów (US4) pokazuje `MonthlySummary` dla dowolnego wybranego miesiąca oraz zestawienie `totalLitersFueled` dwóch lub więcej wybranych miesięcy obok siebie (porównanie miesiąc do miesiąca, FR-012).

## Diagram relacji (tekstowy)

```text
MonthlyLimit (historia wartości w czasie, effectiveFromMonth)
        │
        │ (dla danego miesiąca: najnowszy rekord z effectiveFromMonth <= miesiąc)
        ▼
MonthlySummary (wyliczana, nieprzechowywana) ◄──── FuelingEntry (wiele wpisów, pogrupowane wg `date` → miesiąc)
```
