package pl.fuelmanagement.tracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.fuelmanagement.tracker.data.db.FuelingEntryRepository
import pl.fuelmanagement.tracker.data.db.entities.Centiliters
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity

/** FR-010, FR-013: przegląd historii tankowań oraz edycja/usunięcie wpisu. */
class HistoryViewModel(private val repository: FuelingEntryRepository) : ViewModel() {

    val entries: StateFlow<List<FuelingEntryEntity>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** @return `true`, gdy nowa wartość jest poprawna (> 0) i zapis się powiódł. */
    fun updateLiters(entry: FuelingEntryEntity, litersText: String): Boolean {
        val liters = litersText.replace(',', '.').toDoubleOrNull() ?: return false
        if (liters <= 0.0) return false
        viewModelScope.launch { repository.update(entry.copy(centiliters = Centiliters.fromLiters(liters))) }
        return true
    }

    fun delete(entry: FuelingEntryEntity) {
        viewModelScope.launch { repository.delete(entry) }
    }
}
