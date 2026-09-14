package pl.fuelmanagement.tracker.data.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.tasks.await

/**
 * Rozpoznaje ze zdjęcia paragonu, w pełni lokalnie na urządzeniu, liczbę zatankowanych litrów oraz
 * (opcjonalnie, "jeśli uda się odczytać") przebieg pojazdu i kwotę tankowania, przy użyciu modelu
 * ML Kit Text Recognition spakowanego z aplikacją (research.md -> "Rozpoznawanie liczby litrów z
 * paragonu (OCR)", FR-002). Nie wykonuje żadnych wywołań sieciowych.
 *
 * Paragon zawiera wiele innych liczb poza szukanymi wartościami (cena jednostkowa, numer
 * paragonu/NIP, godzina, numer autoryzacji) -- generyczny model OCR nie rozróżnia ich semantycznie,
 * więc wynik jest heurystyką bliskości słów kluczowych, nie gwarancją. Gdy nie da się jednoznacznie
 * wybrać kandydata, odpowiednie pole wyniku jest `null` -- bezpieczniejsze niż zgadywanie;
 * użytkownik wprowadza/koryguje wartości ręcznie na ekranie potwierdzenia (FR-003).
 */
class ReceiptOcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * [suggestedLiters]: `null`, gdy nie udało się jednoznacznie wyodrębnić liczby litrów.
     * [suggestedOdometerKm]/[suggestedAmountPln]: `null`, gdy paragon nie zawiera rozpoznawalnego
     * pola przebiegu/kwoty -- to pola opcjonalne, w przeciwieństwie do litrów.
     * [rawText]: pełny tekst rozpoznany przez ML Kit -- pokazywany na ekranie potwierdzenia jako
     * diagnostyka, gdy automatyczny odczyt jest błędny.
     */
    data class OcrResult(
        val suggestedLiters: Double?,
        val suggestedOdometerKm: Long?,
        val suggestedAmountPln: Double?,
        val rawText: String,
    )

    suspend fun recognize(photoFile: File): OcrResult {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            ?: return OcrResult(null, null, null, "")
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        bitmap.recycle()

        val lines = result.textBlocks.flatMap { it.lines }
        val suggestedLiters = pickBest(extractLiterCandidates(lines))?.value
        val suggestedOdometerKm = findKeywordedValue(
            lines,
            ODOMETER_KEYWORD_REGEX,
            INTEGER_REGEX,
            MIN_PLAUSIBLE_ODOMETER_KM..MAX_PLAUSIBLE_ODOMETER_KM,
        )?.let(Math::round)
        val suggestedAmountPln = findKeywordedValue(
            lines,
            AMOUNT_KEYWORD_REGEX,
            DECIMAL_REGEX,
            MIN_PLAUSIBLE_AMOUNT_PLN..MAX_PLAUSIBLE_AMOUNT_PLN,
        )
        return OcrResult(suggestedLiters, suggestedOdometerKm, suggestedAmountPln, result.text)
    }

    private data class Candidate(val value: Double, val weight: Int)

    /**
     * Wyodrębnia liczby-kandydatów na liczbę litrów z rozpoznanych linii tekstu paragonu:
     * - linie zawierające słowo kluczowe ("LITR", "ILOŚĆ"/"ILOSC", samodzielne "L") tuż przy
     *   liczbie dostają wyższą wagę niż liczby znalezione bez takiego kontekstu,
     * - jeśli sam paragon rozdzieli etykietę i wartość na dwie osobne linie (np. przez szeroki
     *   odstęp między kolumnami, który ML Kit potraktuje jako dwie linie zamiast jednej), liczba w
     *   linii bezpośrednio PO linii z etykietą dostaje tę samą podwyższoną wagę co dopasowanie w
     *   tej samej linii -- pokrywa oba warianty, jakie realnie generuje OCR na paragonach stacji,
     * - liczby spoza plauzybilnego zakresu pojemności baku (0,5-300 l) są odrzucane, żeby nie
     *   pomylić ilości litrów z ceną jednostkową (zwykle < 10) czy kwotą razem (bywa > 300).
     */
    private fun extractLiterCandidates(lines: List<Text.Line>): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        for ((index, line) in lines.withIndex()) {
            val hasKeywordHere = LITER_KEYWORD_REGEX.containsMatchIn(line.text.uppercase())
            val previousLineHasKeyword =
                index > 0 && LITER_KEYWORD_REGEX.containsMatchIn(lines[index - 1].text.uppercase())
            for (match in DECIMAL_REGEX.findAll(line.text)) {
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
                if (value !in MIN_PLAUSIBLE_LITERS..MAX_PLAUSIBLE_LITERS) continue
                val followedByUnitL = UNIT_L_AFTER_NUMBER_REGEX.containsMatchIn(
                    line.text.substring(match.range.last + 1).trimStart().take(2),
                )
                val weight = when {
                    followedByUnitL -> 3
                    hasKeywordHere || previousLineHasKeyword -> 2
                    else -> 1
                }
                candidates += Candidate(value, weight)
            }
        }
        return candidates
    }

    /** Spośród kandydatów na litry wybiera tego o najwyższej wadze (bliskość słowa kluczowego / jednostki "l"). */
    private fun pickBest(candidates: List<Candidate>): Candidate? =
        candidates.maxByOrNull { it.weight }

    /**
     * Szuka wartości opcjonalnego pola (przebieg/kwota), które -- w odróżnieniu od litrów -- MUSI
     * być poprzedzone słowem kluczowym, żeby w ogóle zostać zaakceptowane (bez tego wymogu zbyt
     * łatwo pomylić przebieg czy kwotę z dowolną inną liczbą na paragonie, np. numerem
     * autoryzacji). Sprawdza tę samą linię co słowo kluczowe, a jeśli nic tam nie znajdzie -- linię
     * bezpośrednio następną (na wypadek rozbicia etykiety i wartości na dwie linie przez OCR).
     */
    private fun findKeywordedValue(
        lines: List<Text.Line>,
        keywordRegex: Regex,
        valueRegex: Regex,
        plausibleRange: ClosedFloatingPointRange<Double>,
    ): Double? {
        for ((index, line) in lines.withIndex()) {
            if (!keywordRegex.containsMatchIn(line.text.uppercase())) continue
            val match = valueRegex.find(line.text) ?: lines.getOrNull(index + 1)?.let { valueRegex.find(it.text) }
            val value = match?.value?.replace(',', '.')?.toDoubleOrNull() ?: continue
            if (value in plausibleRange) return value
        }
        return null
    }

    companion object {
        private val LITER_KEYWORD_REGEX = Regex("""LITR|ILOSC|ILOŚĆ|QUANTITY""")
        private val ODOMETER_KEYWORD_REGEX = Regex("""LICZNIK|PRZEBIEG|ODOMETER""")
        private val AMOUNT_KEYWORD_REGEX = Regex("""KWOTA|WARTOSC|WARTOŚĆ|AMOUNT""")
        private val DECIMAL_REGEX = Regex("""\d{1,4}[.,]\d{1,3}""")
        private val INTEGER_REGEX = Regex("""\d{3,7}""")
        private val UNIT_L_AFTER_NUMBER_REGEX = Regex("""^L\b""", RegexOption.IGNORE_CASE)
        private const val MIN_PLAUSIBLE_LITERS = 0.5
        private const val MAX_PLAUSIBLE_LITERS = 300.0
        private const val MIN_PLAUSIBLE_ODOMETER_KM = 1.0
        private const val MAX_PLAUSIBLE_ODOMETER_KM = 2_000_000.0
        private const val MIN_PLAUSIBLE_AMOUNT_PLN = 1.0
        private const val MAX_PLAUSIBLE_AMOUNT_PLN = 5000.0
    }
}
