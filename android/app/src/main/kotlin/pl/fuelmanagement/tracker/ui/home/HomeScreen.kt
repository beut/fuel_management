package pl.fuelmanagement.tracker.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.fuelmanagement.tracker.FuelManagementApplication

/** Ekran główny (US2, start destination): pozostałe litry w bieżącym miesiącu (FR-007, FR-009). */
@Composable
fun HomeScreen(
    onAddFueling: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as FuelManagementApplication).container
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(container.monthlySummaryCalculator) } },
    )
    val state by viewModel.uiState.collectAsState()

    // Odświeża podsumowanie po powrocie z dodawania tankowania lub zmiany limitu w Ustawieniach
    // -- HomeScreen wraca do kompozycji za każdym razem, gdy nawigacja wraca na tę trasę.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row {
                TextButton(onClick = onOpenHistory) { Text("Historia") }
                TextButton(onClick = onOpenReports) { Text("Raporty") }
                TextButton(onClick = onOpenSettings) { Text("Ustawienia") }
            }

            val summary = state.summary
            when {
                state.isLoading -> Unit
                summary == null || !state.hasLimitConfigured -> {
                    Text(
                        "Nie ustawiono jeszcze miesięcznego limitu litrów.",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Ustaw limit")
                    }
                }
                else -> {
                    val remaining = summary.remainingLiters ?: 0.0
                    Text(
                        text = "Pozostało do wykorzystania: %.2f l".format(remaining),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Text("Limit miesięczny: %.2f l".format(summary.applicableLimit ?: 0.0))
                    Text("Zatankowano w tym miesiącu: %.2f l".format(summary.totalLiters))
                    if (summary.isOverLimit) {
                        Text(
                            "Limit na ten miesiąc został przekroczony.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Button(onClick = onAddFueling, modifier = Modifier.padding(top = 24.dp)) {
                Text("Dodaj tankowanie")
            }
        }
    }
}
