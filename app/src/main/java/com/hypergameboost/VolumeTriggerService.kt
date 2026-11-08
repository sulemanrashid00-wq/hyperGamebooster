package com.hypergameboost

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * VolumeTriggerService - ASUS ROG & Black Shark Hardware AirTriggers Engine
 * - Intercepts physical Volume Up & Volume Down keys during gameplay
 * - Translates Volume Up into instantaneous L1 Tap (e.g., ADS / Scope / Peek)
 * - Translates Volume Down into instantaneous R1 Tap (e.g., Fire / Weapon Trigger)
 * - Seamlessly passes through volume keys when trigger mode is disabled
 */
class VolumeTriggerService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used; purely key event filter
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!CrosshairSettings.isVolumeTriggerEnabled) {
            return super.onKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val x = CrosshairSettings.l1TargetX
                    val y = CrosshairSettings.l1TargetY
                    scope.launch {
                        Shell.run("input tap $x $y")
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val x = CrosshairSettings.r1TargetX
                    val y = CrosshairSettings.r1TargetY
                    scope.launch {
                        Shell.run("input tap $x $y")
                    }
                }
                return true
            }

            else -> return super.onKeyEvent(event)
        }
    }

    companion object {
        @Volatile var isRunning: Boolean = false
    }
}
