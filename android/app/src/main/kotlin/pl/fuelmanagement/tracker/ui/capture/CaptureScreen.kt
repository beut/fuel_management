package pl.fuelmanagement.tracker.ui.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.fuelmanagement.tracker.FuelManagementApplication

/**
 * Ekran-koordynator przepływu dodawania tankowania (US1): przełącza między przechwyceniem zdjęcia
 * (aparat/galeria/ręcznie) a potwierdzeniem/korektą rozpoznanej liczby litrów. Wraca do ekranu
 * poprzedniego ([onDone]) po zapisaniu wpisu lub anulowaniu (FR-001..FR-005a).
 */
@Composable
fun CaptureScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as FuelManagementApplication).container
    val viewModel: CaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CaptureViewModel(
                    container.photoStorage,
                    container.ocrReader,
                    container.duplicateEntryChecker,
                    container.fuelingEntryRepository,
                )
            }
        },
    )
    val step by viewModel.step.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onPhotoCaptured(container.photoStorage.copyFromUri(uri))
    }

    LaunchedEffect(step) {
        if (step is CaptureStep.Finished) onDone()
    }

    when (val currentStep = step) {
        is CaptureStep.Capturing -> CameraCaptureScreen(
            photoStorage = container.photoStorage,
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onPickFromGallery = { galleryLauncher.launch("image/*") },
            onManualEntryWithoutPhoto = viewModel::onManualEntryWithoutPhoto,
            onCancel = onDone,
        )
        is CaptureStep.Confirming -> ReadingConfirmationScreen(
            suggestedLiters = currentStep.suggestedLiters,
            hasPhoto = currentStep.photoFile != null,
            ocrRawText = currentStep.ocrRawText,
            duplicateWarningLiters = currentStep.duplicateWarningLiters,
            onSubmit = viewModel::onSubmit,
            onCancel = viewModel::onCancel,
        )
        CaptureStep.Finished -> Unit // LaunchedEffect powyżej obsłuży powrót
    }
}
