package pl.fuelmanagement.tracker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Limit miesięczny przypisany do służbowej karty paliwowej (data-model.md -> MonthlyLimit, FR-006).
 *
 * [centiliters] to wartość limitu w setnych częściach litra (patrz [FuelingEntryEntity.centiliters]).
 * MUST być > 0.
 *
 * [effectiveFromMonth] to pierwszy miesiąc kalendarzowy (format `YYYY-MM`), od którego ta wartość
 * obowiązuje. Limit obowiązujący dla danego miesiąca `M` to rekord o najwyższym
 * [effectiveFromMonth] spełniającym `effectiveFromMonth <= M` (data-model.md). Zmiana limitu w
 * ustawieniach zawsze TWORZY nowy rekord z [effectiveFromMonth] ustawionym na najbliższy kolejny
 * miesiąc względem daty zmiany -- nigdy nie nadpisuje wartości obowiązującej w już trwającym
 * miesiącu (FR-006, decyzja z sesji /speckit-clarify), z wyjątkiem pierwszego ustawienia limitu
 * (brak jakiegokolwiek wcześniejszego rekordu), które MOŻE obowiązywać od bieżącego miesiąca.
 */
@Entity(tableName = "monthly_limits")
data class MonthlyLimitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val centiliters: Long,
    val effectiveFromMonth: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val liters: Double get() = centiliters / 100.0
}
