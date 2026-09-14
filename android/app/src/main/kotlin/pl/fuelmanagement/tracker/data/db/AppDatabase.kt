package pl.fuelmanagement.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pl.fuelmanagement.tracker.data.db.dao.FuelingEntryDao
import pl.fuelmanagement.tracker.data.db.dao.MonthlyLimitDao
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity
import pl.fuelmanagement.tracker.data.db.entities.MonthlyLimitEntity

/**
 * Uwaga dot. typów kolumn: [FuelingEntryEntity.dateEpochDay] i
 * [FuelingEntryEntity.createdAtMillis]/[MonthlyLimitEntity.createdAtMillis] są przechowywane wprost
 * jako `Long` (epoch day / epoch millis), więc nie wymagają konwertera. `FuelEntrySource` (enum)
 * wymaga jawnego [Converters], zarejestrowanego poniżej.
 */
@Database(
    entities = [FuelingEntryEntity::class, MonthlyLimitEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fuelingEntryDao(): FuelingEntryDao
    abstract fun monthlyLimitDao(): MonthlyLimitDao

    companion object {
        private const val DATABASE_NAME = "fuel_limit_tracker.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
