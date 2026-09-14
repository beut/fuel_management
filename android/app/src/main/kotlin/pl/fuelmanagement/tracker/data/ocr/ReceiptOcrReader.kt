package pl.fuelmanagement.tracker.data.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.tasks.await

/**
 * Rozpoznaje liczbę zatankowanych litrów ze zdjęcia paragonu w pełni lokalnie na urządzeniu, przy
 * użyciu modelu ML Kit Text Recognition spakowanego z aplikacją (research.md -> "Rozpoznawanie
 * liczby litrów z paragonu (OCR)", FR-002). Nie wykonuje żadnych wywołań sieciowych.
 *
 * Paragon zawiera wiele innych liczb poza ilością litrów (cena jednostkowa, kwota razem, numer
 * paragonu/NIP, godzina) -- generyczny model OCR nie rozróżnia ich semantycznie, więc wynik jest
 * heurystyką bliskości słów kluczowych ("L", "LITR", "ILOŚĆ"), nie gwarancją. Gdy nie da się
 * jednoznacznie wybrać kandydata, [OcrResult.suggestedLiters] jest `null` -- bezpieczniejsze niż
 * zgadywanie; użytkownik wprowadza/koryguje wartość ręcznie na ekranie potwierdzenia (FR-003).
 */
class ReceiptOcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * [suggestedLiters]: `null`, gdy nie udało się jednoznacznie wyodrębnić liczby litrów.
     * [rawText]: pełny tekst rozpoznany przez ML Kit -- pokazywany na ekranie potwierdzenia jako
     * diagnostyka, gdy automatyczny odczyt jest błędny.
     */
    data class OcrResult(val suggestedLiters: Double?, val rawText: String)

    suspend fun recognize(photoFile: File): OcrResult {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return OcrResult(null, "")
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        bitmap.recycle()
        return OcrResult(pickBest(extractCandidates(result))?.value, result.text)
    }

    private data class Candidate(val value: Double, val weight: Int)

    /**
     * Wyodrębnia liczby-kandydatów na liczbę litrów z rozpoznanych linii tekstu paragonu:
     * - linie zawierające słowo kluczowe ("LITR", "ILOŚĆ"/"ILOSC", samodzielne "L") tuż przy
     *   liczbie dostają wyższą wagę niż liczby znalezione bez takiego kontekstu,
     * - liczby spoza plauzybilnego zakresu pojemności baku (0,5-300 l) są odrzucane, żeby nie
     *   pomylić ilości litrów z ceną jednostkową (zwykle < 10) czy kwotą razem (bywa > 300).
     */
    private fun extractCandidates(result: Text): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        for (line in result.textBlocks.flatMap { it.lines }) {
            val upperLine = line.text.uppercase()
            val hasKeyword = KEYWORD_REGEX.containsMatchIn(upperLine)
            for (match in NUMBER_REGEX.findAll(line.text)) {
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
                if (value !in MIN_PLAUSIBLE_LITERS..MAX_PLAUSIBLE_LITERS) continue
                val followedByUnitL = UNIT_L_AFTER_NUMBER_REGEX.containsMatchIn(
                    line.text.substring(match.range.last + 1).trimStart().take(2),
                )
                val weight = when {
                    followedByUnitL -> 3
                    hasKeyword -> 2
                    else -> 1
                }
                candidates += Candidate(value, weight)
            }
        }
        return candidates
    }

    /** Spośród kandydatów wybiera tego o najwyższej wadze (bliskość słowa kluczowego / jednostki "l"). */
    private fun pickBest(candidates: List<Candidate>): Candidate? =
        candidates.maxByOrNull { it.weight }

    companion object {
        private val KEYWORD_REGEX = Regex("""LITR|ILOSC|ILOŚĆ|QUANTITY""")
        private val NUMBER_REGEX = Regex("""\d{1,3}[.,]\d{1,3}""")
        private val UNIT_L_AFTER_NUMBER_REGEX = Regex("""^L\b""", RegexOption.IGNORE_CASE)
        private const val MIN_PLAUSIBLE_LITERS = 0.5
        private const val MAX_PLAUSIBLE_LITERS = 300.0
    }
}
