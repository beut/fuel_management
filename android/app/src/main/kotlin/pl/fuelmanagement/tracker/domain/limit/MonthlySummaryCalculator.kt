package pl.fuelmanagement.tracker.domain.limit

import java.time.YearMonth
import pl.fuelmanagement.tracker.data.db.FuelingEntryRepository
import pl.fuelmanagement.tracker.data.db.MonthlyLimitRepository

/** Podsumowanie miesiąca (data-model.md -> MonthlySummary) -- encja wyliczana, nieprzechowywana. */
data class MonthlySummary(
    val month: YearMonth,
    val totalLiters: Double,
    val applicableLimit: Double?,
    val remainingLiters: Double?,
    val isOverLimit: Boolean,
)

/**
 * Wylicza [MonthlySummary] dla dowolnego miesiąca (FR-007, FR-009, FR-011), łącząc sumę wpisów
 * tankowania z [FuelingEntryRepository] i limit obowiązujący dla tego miesiąca z
 * [MonthlyLimitRepository]/[MonthlyLimitResolver].
 */
class MonthlySummaryCalculator(
    private val fuelingEntryRepository: FuelingEntryRepository,
    private val monthlyLimitRepository: MonthlyLimitRepository,
) {
    suspend fun forMonth(month: YearMonth): MonthlySummary {
        val totalCentiliters = fuelingEntryRepository.getForMonth(month).sumOf { it.centiliters }
        val totalLiters = totalCentiliters / 100.0

        val applicableLimitEntity = monthlyLimitRepository.getApplicableLimit(month)
        val applicableLimit = applicableLimitEntity?.liters
        // data-model.md -> MonthlySummary.remainingLiters: null, jeśli applicableLimit jest null.
        val remainingLiters = applicableLimit?.let { it - totalLiters }

        return MonthlySummary(
            month = month,
            totalLiters = totalLiters,
            applicableLimit = applicableLimit,
            remainingLiters = remainingLiters,
            // FR-009: isOverLimit = true, gdy remainingLiters < 0.
            isOverLimit = (remainingLiters ?: 0.0) < 0.0,
        )
    }
}
