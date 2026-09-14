package pl.fuelmanagement.tracker.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import pl.fuelmanagement.tracker.FuelManagementApplication
import pl.fuelmanagement.tracker.util.Logging

private const val RETENTION_MONTHS = 12L
private const val UNIQUE_WORK_NAME = "photo-retention"

/**
 * FR-015: "System MUSI automatycznie usuwać zdjęcie paragonu powiązane z wpisem tankowania po
 * upływie 12 miesięcy od daty tankowania, zachowując przy tym wszystkie pozostałe dane wpisu."
 * Usuwa plik zdjęcia i czyści `photoPath` w bazie; pozostałe pola wpisu ([date], [liters], [source],
 * [note]) pozostają nienaruszone (research.md -> "Czyszczenie zdjęć paragonów").
 */
class PhotoRetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FuelManagementApplication).container
        val cutoff = LocalDate.now().minusMonths(RETENTION_MONTHS)

        return try {
            val expired = container.fuelingEntryRepository.getWithPhotoOlderThan(cutoff)
            for (entry in expired) {
                val photoPath = entry.photoPath ?: continue
                container.photoStorage.delete(photoPath)
                container.fuelingEntryRepository.update(entry.copy(photoPath = null))
            }
            Result.success()
        } catch (exception: Exception) {
            Logging.e("PhotoRetentionWorker nie powiódł się", exception)
            Result.retry()
        }
    }

    companion object {
        /** Rejestruje codzienne sprawdzenie retencji zdjęć; bezpieczne do wywołania wielokrotnie. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PhotoRetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
