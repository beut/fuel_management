package pl.fuelmanagement.tracker.data.db

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import pl.fuelmanagement.tracker.data.db.dao.FuelingEntryDao
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity

/** Repozytorium wpisów tankowania (data-model.md -> FuelingEntry, FR-005, FR-010, FR-013). */
class FuelingEntryRepository(private val dao: FuelingEntryDao) {

    /** @param entry.centiliters MUST być > 0 (data-model.md -> FuelingEntry.liters regułą walidacji). */
    suspend fun save(entry: FuelingEntryEntity): Long {
        require(entry.centiliters > 0) { "centiliters MUST być > 0" }
        return dao.insert(entry)
    }

    suspend fun update(entry: FuelingEntryEntity) = dao.update(entry)

    suspend fun delete(entry: FuelingEntryEntity) = dao.delete(entry)

    suspend fun getById(id: Long): FuelingEntryEntity? = dao.getById(id)

    fun observeAll(): Flow<List<FuelingEntryEntity>> = dao.observeAll()

    /** FR-007: wpisy z podanego miesiąca kalendarzowego (np. `YearMonth.now()`). */
    suspend fun getForMonth(month: YearMonth): List<FuelingEntryEntity> =
        dao.getForDateRange(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    /** FR-005a: `true`, gdy istnieje już wpis z identyczną datą i identyczną liczbą litrów. */
    suspend fun hasExactDuplicate(date: LocalDate, centiliters: Long): Boolean =
        dao.findExactMatch(date.toEpochDay(), centiliters) != null

    /** FR-015: wpisy ze zdjęciem starsze niż [olderThan] (wyłącznie), dla zadania retencji. */
    suspend fun getWithPhotoOlderThan(olderThan: LocalDate): List<FuelingEntryEntity> =
        dao.getWithPhotoOlderThan(olderThan.toEpochDay())
}
