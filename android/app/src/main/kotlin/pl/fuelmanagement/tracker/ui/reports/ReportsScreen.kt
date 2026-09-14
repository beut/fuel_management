package pl.fuelmanagement.tracker.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.YearMonth
import pl.fuelmanagement.tracker.FuelManagementApplication
import pl.fuelmanagement.tracker.domain.limit.MonthlySummary

/** US4 (FR-011, FR-012): podsumowanie wybranego miesiąca i porównanie dwóch miesięcy. */
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as FuelManagementApplication).container
    val viewModel: ReportsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ReportsViewModel(container.monthlySummaryCalculator, container.monthComparisonCalculator) }
        },
    )
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }

            Text("Podsumowanie miesiąca", modifier = Modifier.padding(top = 12.dp))
            MonthPicker(
                selected = state.selectedMonth,
                options = state.availableMonths,
                onSelected = viewModel::selectMonth,
            )
            SummaryText(state.selectedSummary)

            Text("Porównanie miesiąc do miesiąca", modifier = Modifier.padding(top = 24.dp))
            Row {
                MonthPicker(
                    selected = state.comparisonMonthA,
                    options = state.availableMonths,
                    onSelected = { viewModel.compare(it, state.comparisonMonthB) },
                )
                MonthPicker(
                    selected = state.comparisonMonthB,
                    options = state.availableMonths,
                    onSelected = { viewModel.compare(state.comparisonMonthA, it) },
                )
            }
            if (state.comparison.all { it.totalLiters == 0.0 }) {
                Text("Brak tankowań w wybranych miesiącach.", modifier = Modifier.padding(top = 8.dp))
            } else {
                state.comparison.forEach { summary ->
                    Text("${summary.month}: %.2f l".format(summary.totalLiters), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryText(summary: MonthlySummary?) {
    if (summary == null) return
    if (summary.totalLiters == 0.0) {
        // Edge Case: brak tankowań w wybranym miesiącu -- czytelny stan pusty zamiast pustej tabeli.
        Text("Brak tankowań w tym miesiącu.", modifier = Modifier.padding(top = 8.dp))
        return
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("Suma litrów: %.2f l".format(summary.totalLiters))
        Text("Limit: " + (summary.applicableLimit?.let { "%.2f l".format(it) } ?: "brak"))
        Text("Wykorzystanie: " + (summary.remainingLiters?.let { "pozostało %.2f l".format(it) } ?: "brak limitu"))
    }
}

@Composable
private fun MonthPicker(selected: YearMonth, options: List<YearMonth>, onSelected: (YearMonth) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) { Text(selected.toString()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { month ->
                DropdownMenuItem(text = { Text(month.toString()) }, onClick = {
                    onSelected(month)
                    expanded = false
                })
            }
        }
    }
}
