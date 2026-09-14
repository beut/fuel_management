package pl.fuelmanagement.tracker.ui.settings

import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.fuelmanagement.tracker.data.db.MonthlyLimitRepository
import pl.fuelmanagement.tracker.data.db.entities.Centiliters

data class SettingsUiState(
    val currentMonthLimitLiters: Double? = null,
    val upcomingChangeLiters: Double? = null,
    val upcomingChangeMonth: YearMonth? = null,
    val lastSaveSucceeded: Boolean? = null,
)

/**
 * US3 (FR-006): ustawienie/zmiana miesięcznego limitu litrów. Zmiana wprowadzona w trakcie
 * trwającego miesiąca zaczyna obowiązywać dopiero od kolejnego miesiąca -- żadna dodatkowa logika
 * resetu nie jest potrzebna: HomeViewModel i MonthlySummaryCalculator już wyznaczają obowiązujący
 * limit per miesiąc kalendarzowy (FR-008).
 */
class SettingsViewModel(private val monthlyLimitRepository: MonthlyLimitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val currentMonth = YearMonth.now()
            val applicable = monthlyLimitRepository.getApplicableLimit(currentMonth)
            val mostRecent = monthlyLimitRepository.getMostRecent()
            val hasUpcomingChange = mostRecent != null && mostRecent.effectiveFromMonth > currentMonth.toString()
            _uiState.value = SettingsUiState(
                currentMonthLimitLiters = applicable?.liters,
                upcomingChangeLiters = if (hasUpcomingChange) mostRecent?.liters else null,
                upcomingChangeMonth = if (hasUpcomingChange) YearMonth.parse(mostRecent!!.effectiveFromMonth) else null,
            )
        }
    }

    /** @return `true`, gdy wartość jest poprawna (> 0) i zapis się powiódł. */
    fun saveLimit(litersText: String): Boolean {
        val liters = litersText.replace(',', '.').toDoubleOrNull() ?: return false
        if (liters <= 0.0) return false
        viewModelScope.launch {
            monthlyLimitRepository.addLimitChange(Centiliters.fromLiters(liters))
            _uiState.value = _uiState.value.copy(lastSaveSucceeded = true)
            refresh()
        }
        return true
    }
}
