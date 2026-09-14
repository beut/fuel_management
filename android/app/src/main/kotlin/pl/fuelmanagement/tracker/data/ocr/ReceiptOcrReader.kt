package pl.fuelmanagement.tracker.data.ocr

import android.graphics.BitmapFactory
import android.graphics.Rect
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
 * Realne testy na zdjęciach paragonów (w tym pomiętych/zakrzywionych) pokazały, że ML Kit potrafi
 * posegmentować dwukolumnowy układ (etykieta po lewej, wartość po prawej) na wiele sposobów -- w
 * tej samej linii, w dwóch osobnych `TextBlock` (cała kolumna etykiet, potem cała kolumna
 * wartości), a nawet rozbijając pojedyncze słowo etykiety na kilka tokenów przez błędnie wstawioną
 * spację (np. "Wartość:" odczytane jako dwa tokeny "War" / "tość:"). Dlatego wykrywanie słowa
 * kluczowego działa na DWÓCH poziomach jednocześnie: pojedynczy token ORAZ cała linia (tekst całej
 * linii, bez spacji) -- a dopasowanie liczby do wykrytego słowa kluczowego odbywa się PO POŁOŻENIU
 * GEOMETRYCZNYM (ten sam wiersz na obrazie, wg [android.graphics.Rect]), nie po kolejności w
 * spłaszczonej liście linii, bo ta kolejność okazała się niemiarodajna.
 *
 * Mimo to część paragonów (np. mocno pogięty papier, słaby kontrast) jest na tyle zniekształcona,
 * że OCR w ogóle nie wykrywa niektórych etykiet -- w takim wypadku [OcrResult.suggestedLiters] MUSI
 * pozostać `null` zamiast zgadywać między nierozróżnialnymi kandydatami (patrz [pickBest]);
 * bezpieczniejszy pusty wynik niż pewna siebie, błędna podpowiedź -- użytkownik wpisuje/koryguje
 * wartość ręcznie na ekranie potwierdzenia (FR-003).
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

        val suggestedLiters = pickBest(extractLiterCandidates(lines))?.value
        val suggestedOdometerKm = findKeywordedValue(
            lines,
            elements,
            ODOMETER_KEYWORD_REGEX,
            INTEGER_REGEX,
            MIN_PLAUSIBLE_ODOMETER_KM..MAX_PLAUSIBLE_ODOMETER_KM,
        )?.let(Math::round)
        val suggestedAmountPln = findKeywordedValue(
            lines,
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
     * - token z liczbą leżący w tym samym wierszu na obrazie ([isSameRow]) co dowolne wykryte
     *   wystąpienie słowa kluczowego ("LITR", "ILOŚĆ"/"ILOSC") -- patrz [keywordRowBoxes] -- dostaje
     *   wagę pośrednią,
     * - liczby spoza plauzybilnego zakresu pojemności baku (0,5-300 l) są odrzucane, żeby nie
     *   pomylić ilości litrów z ceną jednostkową (zwykle < 10) czy kwotą razem (bywa > 300).
     */
    private fun extractLiterCandidates(lines: List<Text.Line>): List<Candidate> {
        val keywordBoxes = keywordRowBoxes(lines, LITER_KEYWORD_REGEX)
        val candidates = mutableListOf<Candidate>()
        for (line in lines) {
            val elements = line.elements
            for ((index, element) in elements.withIndex()) {
                val match = DECIMAL_REGEX.find(element.text) ?: continue
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
                if (value !in MIN_PLAUSIBLE_LITERS..MAX_PLAUSIBLE_LITERS) continue
                val nextElementIsUnitL = elements.getOrNull(index + 1)?.text?.trim()?.let(UNIT_L_REGEX::matches) == true
                val elementBox = element.boundingBox
                val hasKeywordNearby = elementBox != null && keywordBoxes.any { isSameRow(it, elementBox) }
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

    /**
     * Spośród kandydatów na litry wybiera tego o najwyższej wadze (bliskość słowa kluczowego /
     * jednostki "l") -- ale TYLKO jeśli ma choć jakieś potwierdzenie kontekstowe (`weight > 1`).
     * Gdy paragon jest na tyle nieczytelny/zniekształcony, że OCR nie rozpoznał żadnej etykiety w
     * pobliżu żadnej liczby (wszyscy kandydaci `weight == 1` -- "jakaś liczba w plauzybilnym
     * zakresie, bez kontekstu"), zwracamy `null` zamiast zgadywać między nierozróżnialnymi
     * kandydatami (np. kwotą a ilością litrów) -- zgodnie z zasadą klasy: bezpieczniejszy pusty
     * wynik niż pewna siebie, błędna podpowiedź (FR-003, ręczna korekta).
     */
    private fun pickBest(candidates: List<Candidate>): Candidate? =
        candidates.filter { it.weight > 1 }.maxByOrNull { it.weight }

    /**
     * Szuka wartości opcjonalnego pola (przebieg/kwota), które -- w odróżnieniu od litrów -- MUSI
     * być powiązane ze słowem kluczowym, żeby w ogóle zostać zaakceptowane (bez tego wymogu zbyt
     * łatwo pomylić przebieg czy kwotę z dowolną inną liczbą na paragonie, np. numerem
     * autoryzacji). Sprawdza najpierw linię zawierającą słowo kluczowe, a jeśli sama nie zawiera
     * też wartości -- każdy token leżący w tym samym wierszu na obrazie co wykryte słowo kluczowe.
     */
    private fun findKeywordedValue(
        lines: List<Text.Line>,
        elements: List<Text.Element>,
        keywordRegex: Regex,
        valueRegex: Regex,
        plausibleRange: ClosedFloatingPointRange<Double>,
    ): Double? {
        for (line in lines) {
            if (!lineMatchesKeyword(line, keywordRegex)) continue
            valueRegex.find(line.text)?.let { match ->
                val value = match.value.replace(',', '.').toDoubleOrNull()
                if (value != null && value in plausibleRange) return value
            }
        }
        val keywordBoxes = keywordRowBoxes(lines, keywordRegex)
        for (element in elements) {
            val box = element.boundingBox ?: continue
            if (keywordBoxes.none { isSameRow(it, box) }) continue
            val match = valueRegex.find(element.text) ?: continue
            val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
            if (value in plausibleRange) return value
        }
        return null
    }

    /**
     * Zbiera ramki ([Rect]) wszystkich wystąpień [keywordRegex] w tekście paragonu -- zarówno
     * pojedynczych tokenów, jak i całych linii dopasowanych PO USUNIĘCIU SPACJI (żeby złapać
     * przypadki, gdy OCR rozbił jedno słowo etykiety na kilka tokenów, np. "War tość:" zamiast
     * "Wartość:") -- używane do dopasowania liczby po wspólnym wierszu na obrazie ([isSameRow]).
     */
    private fun keywordRowBoxes(lines: List<Text.Line>, keywordRegex: Regex): List<Rect> {
        val boxes = mutableListOf<Rect>()
        for (line in lines) {
            if (lineMatchesKeyword(line, keywordRegex)) {
                line.boundingBox?.let { boxes += it }
            }
            for (element in line.elements) {
                if (keywordRegex.containsMatchIn(element.text.uppercase())) {
                    element.boundingBox?.let { boxes += it }
                }
            }
        }
        return boxes
    }

    private fun lineMatchesKeyword(line: Text.Line, keywordRegex: Regex): Boolean =
        keywordRegex.containsMatchIn(line.text.replace(" ", "").uppercase())

    /**
     * `true`, gdy środki pionowe ramek [a] i [b] różnią się o mniej niż połowa wysokości wyższej z
     * nich -- traktujemy to jako "ten sam wiersz na obrazie" niezależnie od tego, do której linii
     * lub `TextBlock` ML Kit przypisał każdy z elementów (patrz dokumentacja klasy wyżej).
     */
    private fun isSameRow(a: Rect, b: Rect): Boolean {
        val centerA = (a.top + a.bottom) / 2
        val centerB = (b.top + b.bottom) / 2
        val tolerance = maxOf(a.height(), b.height()) / 2
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
