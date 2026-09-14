package pl.fuelmanagement.tracker.ui.reports

import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.fuelmanagement.tracker.domain.limit.MonthlySummary
import pl.fuelmanagement.tracker.domain.limit.MonthlySummaryCalculator
import pl.fuelmanagement.tracker.domain.report.MonthComparisonCalculator

private const val HISTORY_MONTHS = 12

data class ReportsUiState(
    val availableMonths: List<YearMonth> = emptyList(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedSummary: MonthlySummary? = null,
    val comparisonMonthA: YearMonth = YearMonth.now(),
    val comparisonMonthB: YearMonth = YearMonth.now().minusMonths(1),
    val comparison: List<MonthlySummary> = emptyList(),
)

/** US4 (FR-011, FR-012): podsumowanie wybranego miesiąca i porównanie miesiąc do miesiąca. */
class ReportsViewModel(
    private val monthlySummaryCalculator: MonthlySummaryCalculator,
    private val monthComparisonCalculator: MonthComparisonCalculator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReportsUiState(availableMonths = (0 until HISTORY_MONTHS).map { YearMonth.now().minusMonths(it.toLong()) }),
    )
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        selectMonth(YearMonth.now())
        compare(_uiState.value.comparisonMonthA, _uiState.value.comparisonMonthB)
    }

    fun selectMonth(month: YearMonth) {
        _uiState.value = _uiState.value.copy(selectedMonth = month)
        viewModelScope.launch {
            val summary = monthlySummaryCalculator.forMonth(month)
            _uiState.value = _uiState.value.copy(selectedSummary = summary)
        }
    }

    fun compare(monthA: YearMonth, monthB: YearMonth) {
        _uiState.value = _uiState.value.copy(comparisonMonthA = monthA, comparisonMonthB = monthB)
        viewModelScope.launch {
            val result = monthComparisonCalculator.compare(listOf(monthA, monthB))
            _uiState.value = _uiState.value.copy(comparison = result)
        }
    }
}
