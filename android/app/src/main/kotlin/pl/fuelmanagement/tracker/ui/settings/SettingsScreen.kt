package pl.fuelmanagement.tracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import pl.fuelmanagement.tracker.FuelManagementApplication

/** US3 (FR-006): ustawienie/zmiana miesięcznego limitu litrów. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as FuelManagementApplication).container
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.monthlyLimitRepository) } },
    )
    val state by viewModel.uiState.collectAsState()

    var limitText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentMonthLimitLiters) {
        if (limitText.isEmpty()) {
            limitText = state.currentMonthLimitLiters?.let { "%.2f".format(it) } ?: ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }

            Text(
                text = state.currentMonthLimitLiters?.let { "Obowiązujący limit w tym miesiącu: %.2f l".format(it) }
                    ?: "Brak jeszcze ustawionego limitu.",
                modifier = Modifier.padding(top = 16.dp),
            )
            if (state.upcomingChangeLiters != null && state.upcomingChangeMonth != null) {
                Text("Od ${state.upcomingChangeMonth}: %.2f l".format(state.upcomingChangeLiters))
            }

            Text(
                "Nowa wartość limitu (l). Zacznie obowiązywać od kolejnego miesiąca " +
                    "(chyba że to pierwszy ustawiany limit -- wtedy od razu w tym miesiącu).",
                modifier = Modifier.padding(top = 24.dp),
            )
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                isError = showError,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = { showError = !viewModel.saveLimit(limitText) },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Zapisz limit") }
        }
    }
}
