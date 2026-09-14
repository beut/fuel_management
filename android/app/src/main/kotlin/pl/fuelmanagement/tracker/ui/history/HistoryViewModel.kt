package pl.fuelmanagement.tracker.ui.history

import java.time.LocalDate
import java.time.format.DateTimeParseException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.fuelmanagement.tracker.data.db.FuelingEntryRepository
import pl.fuelmanagement.tracker.data.db.entities.Centiliters
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity
import pl.fuelmanagement.tracker.data.db.entities.Money

/** FR-010, FR-013: przegląd historii tankowań oraz edycja/usunięcie wpisu. */
class HistoryViewModel(private val repository: FuelingEntryRepository) : ViewModel() {

    val entries: StateFlow<List<FuelingEntryEntity>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * @param odometerText/[amountText] mogą być puste -- czyszczą wtedy odpowiednie opcjonalne
     * pole wpisu. @return `true`, gdy [litersText] i [dateText] są poprawnymi wartościami
     * (litry > 0, data w formacie RRRR-MM-DD) i zapis się powiódł.
     */
    fun updateEntry(
        entry: FuelingEntryEntity,
        litersText: String,
        dateText: String,
        odometerText: String,
        amountText: String,
    ): Boolean {
        val liters = litersText.replace(',', '.').toDoubleOrNull() ?: return false
        if (liters <= 0.0) return false
        val date = try {
            LocalDate.parse(dateText)
        } catch (_: DateTimeParseException) {
            return false
        }
        val odometerKm = odometerText.toLongOrNull()
        val amountPln = amountText.replace(',', '.').toDoubleOrNull()
        viewModelScope.launch {
            repository.update(
                entry.copy(
                    dateEpochDay = date.toEpochDay(),
                    centiliters = Centiliters.fromLiters(liters),
                    odometerKm = odometerKm,
                    amountGrosze = amountPln?.let(Money::fromPln),
                ),
            )
        }
        return true
    }

    fun delete(entry: FuelingEntryEntity) {
        viewModelScope.launch { repository.delete(entry) }
    }
}
