package pl.fuelmanagement.tracker.ui.capture

import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.fuelmanagement.tracker.data.db.FuelingEntryRepository
import pl.fuelmanagement.tracker.data.db.entities.FuelEntrySource
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity
import pl.fuelmanagement.tracker.data.db.entities.Money
import pl.fuelmanagement.tracker.data.ocr.ReceiptOcrReader
import pl.fuelmanagement.tracker.data.photo.PhotoStorage
import pl.fuelmanagement.tracker.domain.duplicate.DuplicateEntryChecker

/** Krok przepływu rejestrowania tankowania (US1, FR-001..FR-005a). */
sealed interface CaptureStep {
    data object Capturing : CaptureStep
    data class Confirming(
        val photoFile: File?,
        val suggestedLiters: Double?,
        val suggestedOdometerKm: Long?,
        val suggestedAmountPln: Double?,
        val ocrRawText: String,
        val duplicateWarningLiters: Double? = null,
    ) : CaptureStep
    data object Finished : CaptureStep
}

class CaptureViewModel(
    private val photoStorage: PhotoStorage,
    private val ocrReader: ReceiptOcrReader,
    private val duplicateEntryChecker: DuplicateEntryChecker,
    private val fuelingEntryRepository: FuelingEntryRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<CaptureStep>(CaptureStep.Capturing)
    val step: StateFlow<CaptureStep> = _step.asStateFlow()

    fun onPhotoCaptured(photoFile: File) {
        viewModelScope.launch {
            // FR-002: rozpoznawanie w pełni lokalne, bez wywołań sieciowych.
            val result = ocrReader.recognize(photoFile)
            _step.value = CaptureStep.Confirming(
                photoFile,
                result.suggestedLiters,
                result.suggestedOdometerKm,
                result.suggestedAmountPln,
                result.rawText,
            )
        }
    }

    /** FR-004: pozwala wpisać dane bezpośrednio, bez zdjęcia paragonu. */
    fun onManualEntryWithoutPhoto() {
        _step.value = CaptureStep.Confirming(
            photoFile = null,
            suggestedLiters = null,
            suggestedOdometerKm = null,
            suggestedAmountPln = null,
            ocrRawText = "",
        )
    }

    fun onCancel() {
        val current = _step.value
        if (current is CaptureStep.Confirming) {
            current.photoFile?.let { photoStorage.delete(it.absolutePath) }
        }
        _step.value = CaptureStep.Finished // sygnał dla CaptureScreen, aby wrócić (bez zapisu)
    }

    /**
     * FR-005a: pierwsza próba dla danej wartości sprawdza duplikat i, jeśli wykryty, pokazuje
     * ostrzeżenie zamiast zapisywać; ponowne wywołanie z tą samą wartością (użytkownik nacisnął
     * "Zapisz" po raz drugi mimo ostrzeżenia) zapisuje wpis. [odometerKm]/[amountPln] są
     * opcjonalne (użytkownik mógł je usunąć/nie potwierdzić) i nie wpływają na wykrywanie
     * duplikatu -- to wyłącznie data + litry (data-model.md).
     */
    fun onSubmit(liters: Double, odometerKm: Long?, amountPln: Double?) {
        val current = _step.value as? CaptureStep.Confirming ?: return
        viewModelScope.launch {
            val today = LocalDate.now()
            if (current.duplicateWarningLiters == liters) {
                saveEntry(today, liters, odometerKm, amountPln, current)
                return@launch
            }
            if (duplicateEntryChecker.isPossibleDuplicate(today, liters)) {
                _step.value = current.copy(duplicateWarningLiters = liters)
            } else {
                saveEntry(today, liters, odometerKm, amountPln, current)
            }
        }
    }

    private suspend fun saveEntry(
        date: LocalDate,
        liters: Double,
        odometerKm: Long?,
        amountPln: Double?,
        step: CaptureStep.Confirming,
    ) {
        val source = when {
            step.photoFile == null || step.suggestedLiters == null -> FuelEntrySource.MANUAL
            liters == step.suggestedLiters -> FuelEntrySource.OCR
            else -> FuelEntrySource.OCR_CORRECTED
        }
        fuelingEntryRepository.save(
            FuelingEntryEntity(
                dateEpochDay = date.toEpochDay(),
                centiliters = FuelingEntryEntity.litersToCentiliters(liters),
                photoPath = step.photoFile?.absolutePath,
                source = source,
                odometerKm = odometerKm,
                amountGrosze = amountPln?.let(Money::fromPln),
            ),
        )
        _step.value = CaptureStep.Finished
    }
}
