package pl.fuelmanagement.tracker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Źródło pochodzenia liczby litrów (data-model.md -> FuelingEntry.source, FR-003, FR-004, FR-005).
 */
enum class FuelEntrySource {
    OCR,
    OCR_CORRECTED,
    MANUAL,
}

/**
 * Wpis tankowania (data-model.md -> FuelingEntry).
 *
 * [centiliters] przechowuje liczbę zatankowanych litrów jako liczbę całkowitą setnych części litra
 * (np. 45,23 l = 4523), zgodnie z wymaganą dokładnością 0,01 l (data-model.md), bez utraty precyzji
 * charakterystycznej dla arytmetyki zmiennoprzecinkowej. MUST być > 0.
 *
 * [dateEpochDay] to `LocalDate.toEpochDay()` daty tankowania -- ta data (nie [createdAtMillis])
 * wyznacza miesiąc kalendarzowy, do którego wpis jest zaliczany (FR-007).
 *
 * [photoPath] jest `null` dla wpisów dodanych bez zdjęcia (FR-004) lub po automatycznym usunięciu
 * zdjęcia po 12 miesiącach (FR-015).
 *
 * [odometerKm] (przebieg pojazdu w km) i [amountGrosze] (kwota zapłacona za tankowanie, w groszach)
 * są opcjonalne -- wypełniane z OCR paragonu, gdy uda się je jednoznacznie odczytać ("Stan
 * licznika" / "Kwota"), z możliwością ręcznej korekty lub pominięcia na ekranie potwierdzenia; nie
 * wpływają na wyliczenie limitu miesięcznego (to wyłącznie [centiliters]).
 */
@Entity(tableName = "fueling_entries")
data class FuelingEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateEpochDay: Long,
    val centiliters: Long,
    val photoPath: String?,
    val source: FuelEntrySource,
    val odometerKm: Long? = null,
    val amountGrosze: Long? = null,
    val note: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
    val liters: Double get() = centiliters / 100.0
    val amountPln: Double? get() = amountGrosze?.let { it / 100.0 }

    companion object {
        fun litersToCentiliters(liters: Double): Long = Centiliters.fromLiters(liters)
    }
}
