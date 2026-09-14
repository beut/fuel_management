package pl.fuelmanagement.tracker.data.photo

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Zapis i odczyt zdjęć paragonów w pamięci wewnętrznej aplikacji (data-model.md ->
 * FuelingEntry.photoPath, FR-001). Zdjęcia nie opuszczają urządzenia (FR-002, FR-014).
 */
class PhotoStorage(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "receipt_photos").apply { mkdirs() }

    /** Tworzy docelowy plik na nowe zdjęcie paragonu i zwraca jego uchwyt. */
    fun createPhotoFile(): File = File(photosDir, "${UUID.randomUUID()}.jpg")

    fun photoFile(photoPath: String): File = File(photoPath)

    fun delete(photoPath: String) {
        photoFile(photoPath).delete()
    }

    /** FR-001: kopiuje zdjęcie wybrane w systemowej galerii do pamięci wewnętrznej aplikacji. */
    fun copyFromUri(uri: Uri): File {
        val target = createPhotoFile()
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }
}
