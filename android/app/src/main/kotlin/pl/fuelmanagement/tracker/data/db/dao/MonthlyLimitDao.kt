package pl.fuelmanagement.tracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.fuelmanagement.tracker.data.db.entities.MonthlyLimitEntity

@Dao
interface MonthlyLimitDao {
    @Insert
    suspend fun insert(limit: MonthlyLimitEntity): Long

    /** Wszystkie rekordy historii limitu, rosnąco wg miesiąca -- wejście dla [pl.fuelmanagement.tracker.domain.limit.MonthlyLimitResolver]. */
    @Query("SELECT * FROM monthly_limits ORDER BY effectiveFromMonth ASC")
    suspend fun getAll(): List<MonthlyLimitEntity>

    @Query("SELECT * FROM monthly_limits ORDER BY effectiveFromMonth ASC")
    fun observeAll(): Flow<List<MonthlyLimitEntity>>

    /** Najnowszy (wg effectiveFromMonth) rekord, niezależnie czy już obowiązuje -- do wyjątku "pierwszy limit". */
    @Query("SELECT * FROM monthly_limits ORDER BY effectiveFromMonth DESC LIMIT 1")
    suspend fun getMostRecent(): MonthlyLimitEntity?
}
