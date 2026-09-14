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
 * Paragony stacji paliw bywają dwukolumnowe (etykieta po lewej, wartość wyrównana do prawej w tym
 * samym wierszu, np. "Ilość:" ... "18.800"), a ML Kit potrafi je posegmentować zaskakująco różnie
 * -- czasem jako jedną linię na wiersz, czasem jako dwa osobne `TextBlock` (cała kolumna etykiet,
 * potem cała kolumna wartości), a czasem sklejając kilka gęsto upakowanych wierszy w jedną linię z
 * wieloma liczbami naraz. Żadne z tych trzech zachowań nie jest przewidywalne z góry, więc
 * dopasowanie liczby do etykiety odbywa się na poziomie pojedynczych słów/tokenów
 * ([com.google.mlkit.vision.text.Text.Element], nie całych linii) PO POŁOŻENIU GEOMETRYCZNYM (ten
 * sam wiersz na obrazie, wg [android.graphics.Rect] elementu) -- to jedyny poziom granulacji, który
 * poprawnie paruje "Ilość:" z "18.800" niezależnie od tego, jak ML Kit pogrupował resztę tekstu.
 *
 * Paragon zawiera wiele innych liczb poza szukanymi wartościami (cena jednostkowa, numer
 * paragonu/NIP, godzina, numer autoryzacji) -- generyczny model OCR nie rozróżnia ich semantycznie,
 * więc wynik jest heurystyką, nie gwarancją. Gdy nie da się jednoznacznie wybrać kandydata,
 * odpowiednie pole wyniku jest `null` -- bezpieczniejsze niż zgadywanie; użytkownik
 * wprowadza/koryguje wartości ręcznie na ekranie potwierdzenia (FR-003).
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
        val elements = lines.flatMap { it.elements }

        val suggestedLiters = pickBest(extractLiterCandidates(lines, elements))?.value
        val suggestedOdometerKm = findKeywordedValue(
            elements,
            ODOMETER_KEYWORD_REGEX,
            INTEGER_REGEX,
            MIN_PLAUSIBLE_ODOMETER_KM..MAX_PLAUSIBLE_ODOMETER_KM,
        )?.let(Math::round)
        val suggestedAmountPln = findKeywordedValue(
            elements,
            AMOUNT_KEYWORD_REGEX,
            DECIMAL_REGEX,
            MIN_PLAUSIBLE_AMOUNT_PLN..MAX_PLAUSIBLE_AMOUNT_PLN,
        )
        return OcrResult(suggestedLiters, suggestedOdometerKm, suggestedAmountPln, result.text)
    }

    private data class Candidate(val value: Double, val weight: Int)

    /**
     * Wyodrębnia liczby-kandydatów na liczbę litrów z pojedynczych tokenów ("elementów") tekstu
     * paragonu:
     * - token z liczbą, po którym w TEJ SAMEJ linii następuje token "L" (jednostka), dostaje
     *   najwyższą wagę,
     * - token z liczbą leżący w tym samym wierszu na obrazie ([isSameRow]) co jakikolwiek token ze
     *   słowem kluczowym ("LITR", "ILOŚĆ"/"ILOSC") -- niezależnie od tego, do której linii/bloku ML
     *   Kit go przypisał -- dostaje wagę pośrednią,
     * - liczby spoza plauzybilnego zakresu pojemności baku (0,5-300 l) są odrzucane, żeby nie
     *   pomylić ilości litrów z ceną jednostkową (zwykle < 10) czy kwotą razem (bywa > 300).
     */
    private fun extractLiterCandidates(lines: List<Text.Line>, allElements: List<Text.Element>): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        for (line in lines) {
            val elements = line.elements
            for ((index, element) in elements.withIndex()) {
                val match = DECIMAL_REGEX.find(element.text) ?: continue
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
                if (value !in MIN_PLAUSIBLE_LITERS..MAX_PLAUSIBLE_LITERS) continue
                val nextElementIsUnitL = elements.getOrNull(index + 1)?.text?.trim()?.let(UNIT_L_REGEX::matches) == true
                val hasKeywordNearby = LITER_KEYWORD_REGEX.containsMatchIn(element.text.uppercase()) ||
                    hasKeywordInSameRow(allElements, element, LITER_KEYWORD_REGEX)
                val weight = when {
                    nextElementIsUnitL -> 3
                    hasKeywordNearby -> 2
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
     * być powiązane ze słowem kluczowym, żeby w ogóle zostać zaakceptowane (bez tego wymogu zbyt
     * łatwo pomylić przebieg czy kwotę z dowolną inną liczbą na paragonie, np. numerem
     * autoryzacji). Sprawdza najpierw token ze słowem kluczowym, a jeśli nie zawiera on też
     * wartości -- każdy inny token leżący w tym samym wierszu na obrazie ([isSameRow]).
     */
    private fun findKeywordedValue(
        elements: List<Text.Element>,
        keywordRegex: Regex,
        valueRegex: Regex,
        plausibleRange: ClosedFloatingPointRange<Double>,
    ): Double? {
        for (keywordElement in elements) {
            if (!keywordRegex.containsMatchIn(keywordElement.text.uppercase())) continue
            val sameElementMatch = valueRegex.find(keywordElement.text)
            val match = sameElementMatch ?: elements
                .asSequence()
                .filter { it !== keywordElement && isSameRow(keywordElement, it) }
                .mapNotNull { valueRegex.find(it.text) }
                .firstOrNull()
            val value = match?.value?.replace(',', '.')?.toDoubleOrNull() ?: continue
            if (value in plausibleRange) return value
        }
        return null
    }

    /** `true`, gdy jakiś element inny niż [target], leżący w tym samym wierszu ([isSameRow]), zawiera [keywordRegex]. */
    private fun hasKeywordInSameRow(elements: List<Text.Element>, target: Text.Element, keywordRegex: Regex): Boolean =
        elements.any { it !== target && isSameRow(target, it) && keywordRegex.containsMatchIn(it.text.uppercase()) }

    /**
     * `true`, gdy środki pionowe ramek [a] i [b] różnią się o mniej niż połowa wysokości wyższej z
     * nich -- traktujemy to jako "ten sam wiersz na obrazie" niezależnie od tego, do której linii
     * lub `TextBlock` ML Kit przypisał każdy z elementów (patrz dokumentacja klasy wyżej).
     */
    private fun isSameRow(a: Text.Element, b: Text.Element): Boolean {
        val boxA = a.boundingBox ?: return false
        val boxB = b.boundingBox ?: return false
        val centerA = (boxA.top + boxA.bottom) / 2
        val centerB = (boxB.top + boxB.bottom) / 2
        val tolerance = maxOf(boxA.height(), boxB.height()) / 2
        return kotlin.math.abs(centerA - centerB) <= tolerance
    }

    companion object {
        private val LITER_KEYWORD_REGEX = Regex("""LITR|ILOSC|ILOŚĆ|QUANTITY""")
        private val ODOMETER_KEYWORD_REGEX = Regex("""LICZNIK|PRZEBIEG|ODOMETER""")
        private val AMOUNT_KEYWORD_REGEX = Regex("""KWOTA|WARTOSC|WARTOŚĆ|AMOUNT""")
        private val DECIMAL_REGEX = Regex("""\d{1,4}[.,]\d{1,3}""")
        private val INTEGER_REGEX = Regex("""\d{3,7}""")
        private val UNIT_L_REGEX = Regex("""^L\.?$""", RegexOption.IGNORE_CASE)
        private const val MIN_PLAUSIBLE_LITERS = 0.5
        private const val MAX_PLAUSIBLE_LITERS = 300.0
        private const val MIN_PLAUSIBLE_ODOMETER_KM = 1.0
        private const val MAX_PLAUSIBLE_ODOMETER_KM = 2_000_000.0
        private const val MIN_PLAUSIBLE_AMOUNT_PLN = 1.0
        private const val MAX_PLAUSIBLE_AMOUNT_PLN = 5000.0
    }
}
