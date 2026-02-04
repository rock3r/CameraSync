package dev.sebastiano.camerasync.fakes

import android.content.Context
import dev.sebastiano.camerasync.util.BatteryOptimizationChecker

/**
 * Fake implementation of [BatteryOptimizationChecker] for testing.
 *
 * By default, returns true (ignoring battery optimizations). Can be configured to return false for
 * testing warning scenarios.
 */
class FakeBatteryOptimizationChecker(private var isIgnoring: Boolean = true) :
    BatteryOptimizationChecker {

    override fun isIgnoringBatteryOptimizations(context: Context): Boolean = isIgnoring

    fun setIgnoringBatteryOptimizations(ignoring: Boolean) {
        isIgnoring = ignoring
    }
}
