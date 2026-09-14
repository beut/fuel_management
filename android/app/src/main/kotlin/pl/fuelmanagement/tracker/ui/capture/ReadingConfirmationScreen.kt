package pl.fuelmanagement.tracker.ui.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Ekran potwierdzenia/wpisania liczby litrów (FR-003, FR-004, FR-005a): pokazuje wynik OCR gdy
 * zdjęcie zostało zrobione/wybrane (lub pusty formularz przy ręcznym wpisie bez zdjęcia --
 * [hasPhoto] = false), pozwala go poprawić lub wpisać ręcznie. [initialDate] to data tankowania
 * rozpoznana z paragonu, albo dzisiejsza, gdy nieczytelna (zawsze edytowalna, US1). [onSubmit] jest
 * wywoływane przy każdym naciśnięciu "Zapisz" -- decyzję, czy to pierwsza próba, czy potwierdzenie
 * mimo ostrzeżenia o duplikacie ([duplicateWarningLiters]), podejmuje wywołujący
 * (`CaptureViewModel`), bo wymaga to asynchronicznego zapytania do bazy (FR-005a).
 */
@Composable
fun ReadingConfirmationScreen(
    suggestedLiters: Double?,
    suggestedOdometerKm: Long?,
    suggestedAmountPln: Double?,
    initialDate: LocalDate,
    hasPhoto: Boolean,
    ocrRawText: String,
    duplicateWarningLiters: Double?,
    onSubmit: (date: LocalDate, liters: Double, odometerKm: Long?, amountPln: Double?) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(suggestedLiters?.let { formatLiters(it) } ?: "") }
    var odometerText by remember { mutableStateOf(suggestedOdometerKm?.toString() ?: "") }
    var amountText by remember { mutableStateOf(suggestedAmountPln?.let { formatLiters(it) } ?: "") }
    var dateText by remember { mutableStateOf(initialDate.toString()) }
    var showRawOcrText by remember { mutableStateOf(false) }

    val parsedLiters = text.replace(',', '.').toDoubleOrNull()
    val parsedOdometerKm = odometerText.toLongOrNull()
    val parsedAmountPln = amountText.replace(',', '.').toDoubleOrNull()
    val parsedDate = try {
        LocalDate.parse(dateText)
    } catch (_: DateTimeParseException) {
        null
    }
    val canSubmit = parsedLiters != null && parsedLiters > 0.0 && parsedDate != null
    val showDuplicateWarning = duplicateWarningLiters != null && duplicateWarningLiters == parsedLiters

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            when {
                !hasPhoto -> "Wpisz liczbę zatankowanych litrów ręcznie."
                suggestedLiters == null -> "Nie udało się jednoznacznie odczytać liczby litrów ze zdjęcia. Wpisz ją ręcznie."
                else -> "Rozpoznana liczba zatankowanych litrów. Popraw, jeśli jest niepoprawna."
            },
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Litry") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            isError = parsedDate == null,
            label = { Text("Data tankowania (RRRR-MM-DD)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = odometerText,
            onValueChange = { odometerText = it },
            label = { Text("Przebieg (km, opcjonalnie)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Kwota tankowania (PLN, opcjonalnie)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        if (showDuplicateWarning) {
            Text(
                "Wpis z taką samą datą i liczbą litrów już istnieje. Naciśnij \"Zapisz\" ponownie, aby zapisać mimo to.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (hasPhoto) {
            TextButton(onClick = { showRawOcrText = !showRawOcrText }, modifier = Modifier.padding(top = 12.dp)) {
                Text(if (showRawOcrText) "Ukryj tekst rozpoznany przez OCR" else "Pokaż tekst rozpoznany przez OCR")
            }
            if (showRawOcrText) {
                Text(
                    text = ocrRawText.ifBlank { "(OCR nie rozpoznał żadnego tekstu na zdjęciu)" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }

        Row(modifier = Modifier.padding(top = 24.dp)) {
            TextButton(onClick = onCancel) { Text("Anuluj") }
            Button(
                enabled = canSubmit,
                onClick = {
                    if (parsedLiters != null && parsedDate != null) {
                        onSubmit(parsedDate, parsedLiters, parsedOdometerKm, parsedAmountPln)
                    }
                },
            ) { Text("Zapisz") }
        }
    }
}

private fun formatLiters(liters: Double): String = String.format("%.2f", liters)
