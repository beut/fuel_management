package pl.fuelmanagement.tracker.data.db.entities

/**
 * Wspólna konwersja litrów (Double, dokładność 0,01 l) na setne części litra (Long), używana przez
 * [FuelingEntryEntity.centiliters] i [MonthlyLimitEntity.centiliters], żeby uniknąć arytmetyki
 * zmiennoprzecinkowej w bazie danych (data-model.md).
 */
object Centiliters {
    fun fromLiters(liters: Double): Long = Math.round(liters * 100.0)
}
