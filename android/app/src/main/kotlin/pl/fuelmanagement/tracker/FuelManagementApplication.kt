package pl.fuelmanagement.tracker

import android.app.Application
import pl.fuelmanagement.tracker.cleanup.PhotoRetentionWorker

class FuelManagementApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // FR-015: codzienne sprawdzenie i usunięcie zdjęć paragonów starszych niż 12 miesięcy.
        PhotoRetentionWorker.schedule(this)
    }
}
