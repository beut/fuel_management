package pl.fuelmanagement.tracker.ui.home

import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.fuelmanagement.tracker.domain.limit.MonthlySummary
import pl.fuelmanagement.tracker.domain.limit.MonthlySummaryCalculator

data class HomeUiState(
    val summary: MonthlySummary? = null,
    val isLoading: Boolean = true,
) {
    /** Edge Case ze spec.md: brak skonfigurowanego limitu -- pokaż zachętę do Ustawień zamiast wartości. */
    val hasLimitConfigured: Boolean get() = summary?.applicableLimit != null
}

/** Ekran główny (US2): pozostałe litry w bieżącym miesiącu (FR-007, FR-009). */
class HomeViewModel(private val monthlySummaryCalculator: MonthlySummaryCalculator) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val summary = monthlySummaryCalculator.forMonth(YearMonth.now())
            _uiState.value = HomeUiState(summary = summary, isLoading = false)
        }
    }
}
