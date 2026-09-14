package pl.fuelmanagement.tracker.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import pl.fuelmanagement.tracker.FuelManagementApplication
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity

/** FR-010, FR-013: chronologiczna historia tankowań z możliwością edycji/usunięcia wpisu. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as FuelManagementApplication).container
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(container.fuelingEntryRepository) } },
    )
    val entries by viewModel.entries.collectAsState()
    var editingEntry by remember { mutableStateOf<FuelingEntryEntity?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("Wróć") }
            Text("Historia tankowań", modifier = Modifier.padding(top = 12.dp))

            if (entries.isEmpty()) {
                Text("Brak zarejestrowanych tankowań.", modifier = Modifier.padding(top = 24.dp))
            }

            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${entry.date} — %.2f l (${entry.source})".format(entry.liters))
                        val details = listOfNotNull(
                            entry.odometerKm?.let { "przebieg: $it km" },
                            entry.amountPln?.let { "kwota: %.2f zł".format(it) },
                        )
                        if (details.isNotEmpty()) {
                            Text(details.joinToString(" · "))
                        }
                    }
                    TextButton(onClick = { editingEntry = entry }) { Text("Edytuj / usuń") }
                    HorizontalDivider()
                }
            }
        }
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { litersText, odometerText, amountText ->
                if (viewModel.updateEntry(entry, litersText, odometerText, amountText)) editingEntry = null
            },
            onDelete = {
                viewModel.delete(entry)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun EditEntryDialog(
    entry: FuelingEntryEntity,
    onDismiss: () -> Unit,
    onSave: (litersText: String, odometerText: String, amountText: String) -> Unit,
    onDelete: () -> Unit,
) {
    var litersText by remember(entry.id) { mutableStateOf("%.2f".format(entry.liters)) }
    var odometerText by remember(entry.id) { mutableStateOf(entry.odometerKm?.toString() ?: "") }
    var amountText by remember(entry.id) { mutableStateOf(entry.amountPln?.let { "%.2f".format(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wpis z dnia ${entry.date}") },
        text = {
            Column {
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("Litry") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = odometerText,
                    onValueChange = { odometerText = it },
                    label = { Text("Przebieg (km, opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Kwota (PLN, opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(litersText, odometerText, amountText) }) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Usuń") }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    )
}
