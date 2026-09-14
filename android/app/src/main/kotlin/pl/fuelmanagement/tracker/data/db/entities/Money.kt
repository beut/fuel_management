package pl.fuelmanagement.tracker.data.db.entities

/**
 * Konwersja kwoty w PLN (Double) na grosze (Long), tym samym uzasadnieniem co [Centiliters] --
 * unika arytmetyki zmiennoprzecinkowej w przechowywanej wartości [FuelingEntryEntity.amountGrosze].
 */
object Money {
    fun fromPln(pln: Double): Long = Math.round(pln * 100.0)
}
