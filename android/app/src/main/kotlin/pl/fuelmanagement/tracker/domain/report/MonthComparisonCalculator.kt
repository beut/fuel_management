package pl.fuelmanagement.tracker.domain.report

import java.time.YearMonth
import pl.fuelmanagement.tracker.domain.limit.MonthlySummary
import pl.fuelmanagement.tracker.domain.limit.MonthlySummaryCalculator

/** FR-012: buduje zestawienie [MonthlySummary] dla dwóch lub więcej wybranych miesięcy. */
class MonthComparisonCalculator(private val monthlySummaryCalculator: MonthlySummaryCalculator) {
    suspend fun compare(months: List<YearMonth>): List<MonthlySummary> =
        months.map { monthlySummaryCalculator.forMonth(it) }
}
