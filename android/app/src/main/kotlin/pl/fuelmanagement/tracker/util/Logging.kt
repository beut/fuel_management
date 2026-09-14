package pl.fuelmanagement.tracker.util

import android.util.Log

/** Cienka warstwa nad android.util.Log, żeby ujednolicić tag i mieć jedno miejsce do rozszerzenia. */
object Logging {
    private const val TAG = "FuelLimitTracker"

    fun d(message: String) = Log.d(TAG, message)

    fun w(message: String, throwable: Throwable? = null) = Log.w(TAG, message, throwable)

    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
