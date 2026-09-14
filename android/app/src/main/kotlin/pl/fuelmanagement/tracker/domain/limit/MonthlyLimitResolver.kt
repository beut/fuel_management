package pl.fuelmanagement.tracker.domain.limit

import java.time.YearMonth
import pl.fuelmanagement.tracker.data.db.entities.MonthlyLimitEntity

/**
 * Czysta funkcja domenowa (bez zależności od bazy danych, testowalna jednostkowo w `src/test`):
 * wybiera limit obowiązujący dla danego miesiąca z pełnej historii wartości (data-model.md ->
 * MonthlyLimit, FR-006).
 */
object MonthlyLimitResolver {

    /**
     * Limit obowiązujący dla [month] = rekord z najwyższym `effectiveFromMonth` spełniającym
     * `effectiveFromMonth <= month` (data-model.md). `null`, jeśli żaden limit jeszcze nie
     * skonfigurowano na ten miesiąc.
     */
    fun applicableLimit(limits: List<MonthlyLimitEntity>, month: YearMonth): MonthlyLimitEntity? {
        val monthKey = month.toString()
        return limits
            .filter { it.effectiveFromMonth <= monthKey }
            .maxByOrNull { it.effectiveFromMonth }
    }

    /**
     * Miesiąc, od którego zacznie obowiązywać nowo dodana wartość limitu (FR-006): najbliższy
     * kolejny miesiąc kalendarzowy względem [changeMonth] -- z wyjątkiem [hasAnyExistingLimit] ==
     * `false` (pierwsze ustawienie limitu), kiedy to MOŻE obowiązywać już od [changeMonth]
     * (data-model.md -> MonthlyLimit regułą walidacji, wyjątek "pierwsze ustawienie limitu").
     */
    fun effectiveMonthForChange(changeMonth: YearMonth, hasAnyExistingLimit: Boolean): YearMonth =
        if (hasAnyExistingLimit) changeMonth.plusMonths(1) else changeMonth
}
