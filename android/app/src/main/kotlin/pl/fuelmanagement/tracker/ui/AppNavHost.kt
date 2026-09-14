package pl.fuelmanagement.tracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.fuelmanagement.tracker.ui.capture.CaptureScreen
import pl.fuelmanagement.tracker.ui.history.HistoryScreen
import pl.fuelmanagement.tracker.ui.home.HomeScreen
import pl.fuelmanagement.tracker.ui.reports.ReportsScreen
import pl.fuelmanagement.tracker.ui.settings.SettingsScreen

/** Szkielet nawigacji Compose spinający wszystkie ekrany aplikacji (Phase 2: Foundational). */
object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    // FR-007: HomeScreen jako start destination -- pozostały limit widoczny natychmiast po starcie.
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddFueling = { navController.navigate(Routes.CAPTURE) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.REPORTS) {
            ReportsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
