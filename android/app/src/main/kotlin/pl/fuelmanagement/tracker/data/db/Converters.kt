package pl.fuelmanagement.tracker.data.db

import androidx.room.TypeConverter
import pl.fuelmanagement.tracker.data.db.entities.FuelEntrySource

/**
 * Room nie potrafi automatycznie zapisać dowolnego enuma jako kolumny (klasyczny błąd "Cannot
 * figure out how to save this field into database") -- [FuelEntrySource] wymaga jawnej konwersji
 * do/z `String`. [FuelingEntryEntity.dateEpochDay]/`createdAtMillis` nie wymagają konwertera, bo są
 * już przechowywane wprost jako `Long`.
 */
class Converters {
    @TypeConverter
    fun fromFuelEntrySource(value: FuelEntrySource): String = value.name

    @TypeConverter
    fun toFuelEntrySource(value: String): FuelEntrySource = FuelEntrySource.valueOf(value)
}
