package pl.fuelmanagement.tracker.domain.duplicate

import java.time.LocalDate
import pl.fuelmanagement.tracker.data.db.FuelingEntryRepository
import pl.fuelmanagement.tracker.data.db.entities.FuelingEntryEntity

/**
 * FR-005a: "System MUSI ostrzec użytkownika przed zapisaniem nowego wpisu, jeśli w bazie istnieje
 * już wpis z taką samą datą i taką samą liczbą litrów, pozostawiając użytkownikowi decyzję o
 * zapisaniu mimo ostrzeżenia." Nie jest to twarde ograniczenie bazy danych (data-model.md) --
 * jedynie sygnał dla UI, czy pokazać ostrzeżenie przed zapisem.
 */
class DuplicateEntryChecker(private val repository: FuelingEntryRepository) {
    suspend fun isPossibleDuplicate(date: LocalDate, liters: Double): Boolean =
        repository.hasExactDuplicate(date, FuelingEntryEntity.litersToCentiliters(liters))
}
