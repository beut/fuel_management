package pl.fuelmanagement.tracker.data.db

import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import pl.fuelmanagement.tracker.data.db.dao.MonthlyLimitDao
import pl.fuelmanagement.tracker.data.db.entities.MonthlyLimitEntity
import pl.fuelmanagement.tracker.domain.limit.MonthlyLimitResolver

/** Repozytorium historii limitu miesięcznego (data-model.md -> MonthlyLimit, FR-006). */
class MonthlyLimitRepository(private val dao: MonthlyLimitDao) {

    fun observeAll(): Flow<List<MonthlyLimitEntity>> = dao.observeAll()

    /** FR-007: limit obowiązujący dla [month], albo `null`, gdy jeszcze nieskonfigurowany. */
    suspend fun getApplicableLimit(month: YearMonth): MonthlyLimitEntity? =
        MonthlyLimitResolver.applicableLimit(dao.getAll(), month)

    /**
     * FR-006: dodaje nową wartość limitu. `effectiveFromMonth` jest wyznaczane przez
     * [MonthlyLimitResolver.effectiveMonthForChange] -- kolejny miesiąc względem dziś, chyba że
     * nie istnieje jeszcze żaden wcześniejszy rekord (wtedy obowiązuje od bieżącego miesiąca).
     *
     * @param centiliters MUST być > 0.
     * @return miesiąc, od którego nowa wartość zacznie obowiązywać.
     */
    suspend fun addLimitChange(centiliters: Long, changeMonth: YearMonth = YearMonth.now()): YearMonth {
        require(centiliters > 0) { "centiliters MUST być > 0" }
        val mostRecent = dao.getMostRecent()
        val effectiveMonth = MonthlyLimitResolver.effectiveMonthForChange(changeMonth, mostRecent != null)
        dao.insert(MonthlyLimitEntity(centiliters = centiliters, effectiveFromMonth = effectiveMonth.toString()))
        return effectiveMonth
    }

    suspend fun getMostRecent(): MonthlyLimitEntity? = dao.getMostRecent()
}
