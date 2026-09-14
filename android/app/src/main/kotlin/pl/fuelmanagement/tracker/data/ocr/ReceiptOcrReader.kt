package pl.fuelmanagement.tracker.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LANGUAGE = "pol"
private const val TESSDATA_ASSET_PATH = "tessdata/$LANGUAGE.traineddata"

/**
 * Rozpoznaje ze zdjęcia paragonu, w pełni lokalnie na urządzeniu, liczbę zatankowanych litrów oraz
 * (opcjonalnie, "jeśli uda się odczytać") przebieg pojazdu i kwotę tankowania, przy użyciu
 * Tesseract4Android (research.md -> "Rozpoznawanie liczby litrów z paragonu (OCR)", FR-002). Nie
 * wykonuje żadnych wywołań sieciowych -- dane językowe (`assets/tessdata/pol.traineddata`) są
 * spakowane w APK i kopiowane do prywatnego katalogu aplikacji przy pierwszym użyciu.
 *
 * ZMIANA WZGLĘDEM ML KIT TEXT RECOGNITION: testy na realnych paragonach pokazały, że ML Kit
 * niekontrolowanie segmentuje dwukolumnowy układ paragonu (etykieta po lewej, wartość po prawej) --
 * czasem jako jedną linię na wiersz, czasem jako dwa osobne bloki (cała kolumna etykiet, potem cała
 * kolumna wartości) w kolejności niezgodnej z układem wizualnym, a czasem rozbijając jedno słowo
 * etykiety na kilka tokenów. Publiczne API ML Kit nie daje żadnej kontroli nad tym zachowaniem.
 * Tesseract pozwala jawnie wymusić [TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK] (domyślny tryb tej
 * biblioteki) -- każe traktować obraz jako jeden spójny blok tekstu bez niezależnego wykrywania
 * kolumn, co dla wąskiego, jednokolumnowego zdjęcia paragonu daje bardziej przewidywalną kolejność
 * odczytu niż automatyczna analiza układu strony.
 *
 * Mimo to część paragonów (mocno pogięty papier, słaby kontrast) pozostanie na tyle zniekształcona,
 * że OCR nie wykryje niektórych etykiet -- w takim wypadku [OcrResult.suggestedLiters] MUSI
 * pozostać `null` zamiast zgadywać między nierozróżnialnymi kandydatami (patrz [pickBest]);
 * bezpieczniejszy pusty wynik niż pewna siebie, błędna podpowiedź -- użytkownik wpisuje/koryguje
 * wartość ręcznie na ekranie potwierdzenia (FR-003).
 */
class ReceiptOcrReader(context: Context) {
    private val appContext = context.applicationContext

    /**
     * [suggestedLiters]: `null`, gdy nie udało się jednoznacznie wyodrębnić liczby litrów.
     * [suggestedOdometerKm]/[suggestedAmountPln]: `null`, gdy paragon nie zawiera rozpoznawalnego
     * pola przebiegu/kwoty -- to pola opcjonalne, w przeciwieństwie do litrów.
     * [rawText]: pełny tekst rozpoznany przez Tesseract -- pokazywany na ekranie potwierdzenia jako
     * diagnostyka, gdy automatyczny odczyt jest błędny.
     */
    data class OcrResult(
        val suggestedLiters: Double?,
        val suggestedOdometerKm: Long?,
        val suggestedAmountPln: Double?,
        val rawText: String,
    )

    suspend fun recognize(photoFile: File): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            ?: return@withContext OcrResult(null, null, null, "")
        val dataPath = ensureTessDataReady()
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataPath.absolutePath, LANGUAGE)) {
                return@withContext OcrResult(null, null, null, "")
            }
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            tess.setImage(bitmap)
            val rawText = tess.utF8Text.orEmpty()
            val lines = extractLines(tess)

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
            OcrResult(suggestedLiters, suggestedOdometerKm, suggestedAmountPln, rawText)
        } finally {
            tess.recycle()
            bitmap.recycle()
        }
    }

    /** Kopiuje dane językowe z assets do prywatnego katalogu aplikacji przy pierwszym użyciu (FR-002, FR-014: brak sieci). */
    private fun ensureTessDataReady(): File {
        val dataDir = File(appContext.filesDir, "tesseract")
        val languageFile = File(dataDir, "tessdata/$LANGUAGE.traineddata")
        if (!languageFile.exists()) {
            languageFile.parentFile?.mkdirs()
            appContext.assets.open(TESSDATA_ASSET_PATH).use { input ->
                languageFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dataDir
    }

    private data class Word(val text: String, val box: Rect)
    private data class Line(val text: String, val box: Rect?, val words: List<Word>)
    private data class Candidate(val value: Double, val weight: Int)

    /**
     * Iteruje wyniki Tesseract na poziomie słów (`RIL_WORD`), grupując je w linie (`RIL_TEXTLINE`)
     * -- odpowiednik `Text.Line`/`Text.Element` z poprzedniej implementacji na ML Kit, żeby
     * zachować tę samą logikę dopasowania geometrycznego poniżej.
     */
    private fun extractLines(tess: TessBaseAPI): List<Line> {
        val lines = mutableListOf<Line>()
        var currentWords = mutableListOf<Word>()
        var currentLineBox: Rect? = null
        val iterator = tess.resultIterator ?: return lines
        try {
            iterator.begin()
            do {
                if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE) && currentWords.isNotEmpty()) {
                    lines += Line(currentWords.joinToString(" ") { it.text }, currentLineBox, currentWords)
                    currentWords = mutableListOf()
                }
                if (currentWords.isEmpty()) {
                    currentLineBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                }
                val wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                if (wordText.isNotEmpty()) {
                    val wordBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    currentWords += Word(wordText, wordBox)
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            if (currentWords.isNotEmpty()) {
                lines += Line(currentWords.joinToString(" ") { it.text }, currentLineBox, currentWords)
            }
        } finally {
            iterator.delete()
        }
        return lines
    }

    /**
     * Wyodrębnia liczby-kandydatów na liczbę litrów z pojedynczych słów tekstu paragonu:
     * - słowo z liczbą, po którym w TEJ SAMEJ linii następuje słowo "L" (jednostka), dostaje
     *   najwyższą wagę,
     * - słowo z liczbą leżące w tym samym wierszu na obrazie ([isSameRow]) co dowolne wykryte
     *   wystąpienie słowa kluczowego ("LITR", "ILOŚĆ"/"ILOSC") dostaje wagę pośrednią,
     * - liczby spoza plauzybilnego zakresu pojemności baku (0,5-300 l) są odrzucane, żeby nie
     *   pomylić ilości litrów z ceną jednostkową (zwykle < 10) czy kwotą razem (bywa > 300).
     */
    private fun extractLiterCandidates(lines: List<Line>): List<Candidate> {
        val keywordBoxes = keywordRowBoxes(lines, LITER_KEYWORD_REGEX)
        val candidates = mutableListOf<Candidate>()
        for (line in lines) {
            for ((index, word) in line.words.withIndex()) {
                val match = DECIMAL_REGEX.find(word.text) ?: continue
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
                if (value !in MIN_PLAUSIBLE_LITERS..MAX_PLAUSIBLE_LITERS) continue
                val nextWordIsUnitL = line.words.getOrNull(index + 1)?.text?.trim()?.let(UNIT_L_REGEX::matches) == true
                val hasKeywordNearby = keywordBoxes.any { isSameRow(it, word.box) }
                val weight = when {
                    nextWordIsUnitL -> 3
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
     * kandydatami (np. kwotą a ilością litrów) -- bezpieczniejszy pusty wynik niż pewna siebie,
     * błędna podpowiedź (FR-003, ręczna korekta).
     */
    private fun pickBest(candidates: List<Candidate>): Candidate? =
        candidates.filter { it.weight > 1 }.maxByOrNull { it.weight }

    /**
     * Szuka wartości opcjonalnego pola (przebieg/kwota), które -- w odróżnieniu od litrów -- MUSI
     * być powiązane ze słowem kluczowym, żeby w ogóle zostać zaakceptowane (bez tego wymogu zbyt
     * łatwo pomylić przebieg czy kwotę z dowolną inną liczbą na paragonie, np. numerem
     * autoryzacji). Sprawdza najpierw linię zawierającą słowo kluczowe, a jeśli sama nie zawiera
     * też wartości -- każde słowo leżące w tym samym wierszu na obrazie co wykryte słowo kluczowe.
     */
    private fun findKeywordedValue(
        lines: List<Line>,
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
        for (line in lines) {
            for (word in line.words) {
                if (keywordBoxes.none { isSameRow(it, word.box) }) continue
                val match = valueRegex.find(word.text) ?: continue
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: continue
                if (value in plausibleRange) return value
            }
        }
        return null
    }

    /**
     * Zbiera ramki ([Rect]) wszystkich wystąpień [keywordRegex] w tekście paragonu -- zarówno
     * pojedynczych słów, jak i całych linii dopasowanych PO USUNIĘCIU SPACJI (żeby złapać
     * przypadki, gdy OCR rozbił jedno słowo etykiety na kilka tokenów, np. "War tość:" zamiast
     * "Wartość:") -- używane do dopasowania liczby po wspólnym wierszu na obrazie ([isSameRow]).
     */
    private fun keywordRowBoxes(lines: List<Line>, keywordRegex: Regex): List<Rect> {
        val boxes = mutableListOf<Rect>()
        for (line in lines) {
            if (lineMatchesKeyword(line, keywordRegex)) {
                line.box?.let { boxes += it }
            }
            for (word in line.words) {
                if (keywordRegex.containsMatchIn(word.text.uppercase())) {
                    boxes += word.box
                }
            }
        }
        return boxes
    }

    private fun lineMatchesKeyword(line: Line, keywordRegex: Regex): Boolean =
        keywordRegex.containsMatchIn(line.text.replace(" ", "").uppercase())

    /**
     * `true`, gdy środki pionowe ramek [a] i [b] różnią się o mniej niż połowa wysokości wyższej z
     * nich -- traktujemy to jako "ten sam wiersz na obrazie" niezależnie od tego, do której
     * linii/bloku Tesseract przypisał każde słowo (patrz dokumentacja klasy wyżej).
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
