package pl.fuelmanagement.tracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity

@Dao
interface FuelingEntryDao {
    @Insert
    suspend fun insert(entry: FuelingEntryEntity): Long

    @Update
    suspend fun update(entry: FuelingEntryEntity)

    @Delete
    suspend fun delete(entry: FuelingEntryEntity)

    @Query("SELECT * FROM fueling_entries WHERE id = :id")
    suspend fun getById(id: Long): FuelingEntryEntity?

    /** FR-010: chronologiczna historia wszystkich wpisów, malejąco wg daty tankowania. */
    @Query("SELECT * FROM fueling_entries ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<FuelingEntryEntity>>

    /**
     * FR-007: wpisy z danego miesiąca kalendarzowego, wyznaczonego przez zakres dni epoki
     * [startEpochDayInclusive]..[endEpochDayInclusive] (pierwszy i ostatni dzień miesiąca).
     */
    @Query(
        "SELECT * FROM fueling_entries " +
            "WHERE dateEpochDay BETWEEN :startEpochDayInclusive AND :endEpochDayInclusive " +
            "ORDER BY dateEpochDay ASC",
    )
    suspend fun getForDateRange(startEpochDayInclusive: Long, endEpochDayInclusive: Long): List<FuelingEntryEntity>

    /** FR-005a: wykrycie wpisu z identyczną datą i identyczną liczbą litrów (możliwy duplikat). */
    @Query("SELECT * FROM fueling_entries WHERE dateEpochDay = :dateEpochDay AND centiliters = :centiliters LIMIT 1")
    suspend fun findExactMatch(dateEpochDay: Long, centiliters: Long): FuelingEntryEntity?

    /** FR-015: wpisy ze zdjęciem, których data tankowania jest starsza niż podana granica retencji. */
    @Query("SELECT * FROM fueling_entries WHERE photoPath IS NOT NULL AND dateEpochDay < :cutoffEpochDayExclusive")
    suspend fun getWithPhotoOlderThan(cutoffEpochDayExclusive: Long): List<FuelingEntryEntity>
}
