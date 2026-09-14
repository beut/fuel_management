package pl.fuelmanagement.tracker

import android.content.Context
import pl.fuelmanagement.tracker.data.db.AppDatabase
import pl.fuelmanagement.tracker.data.db.FuelingEntryRepository
import pl.fuelmanagement.tracker.data.db.MonthlyLimitRepository
import pl.fuelmanagement.tracker.data.ocr.ReceiptOcrReader
import pl.fuelmanagement.tracker.data.photo.PhotoStorage
import pl.fuelmanagement.tracker.domain.duplicate.DuplicateEntryChecker
import pl.fuelmanagement.tracker.domain.limit.MonthlySummaryCalculator
import pl.fuelmanagement.tracker.domain.report.MonthComparisonCalculator

/**
 * Ręczny kontener zależności (research.md -> "Architektura aplikacji"): zakres v1 (jedna
 * karta/limit, brak backendu) nie uzasadnia nakładu na framework DI (np. Hilt).
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val photoStorage = PhotoStorage(context)
    val ocrReader = ReceiptOcrReader()

    val fuelingEntryRepository = FuelingEntryRepository(database.fuelingEntryDao())
    val monthlyLimitRepository = MonthlyLimitRepository(database.monthlyLimitDao())

    val duplicateEntryChecker = DuplicateEntryChecker(fuelingEntryRepository)
    val monthlySummaryCalculator = MonthlySummaryCalculator(fuelingEntryRepository, monthlyLimitRepository)
    val monthComparisonCalculator = MonthComparisonCalculator(monthlySummaryCalculator)
}
