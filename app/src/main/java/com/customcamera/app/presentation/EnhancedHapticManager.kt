package com.customcamera.app.presentation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Enhanced Haptic Feedback Manager
 *
 * Provides sophisticated haptic patterns for different interactions:
 * - Button presses
 * - Feature activation
 * - Photo capture
 * - Errors and warnings
 * - Success confirmations
 */
class EnhancedHapticManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Light tap for button presses
     */
    fun lightTap(view: View? = null) {
        view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        ) ?: vibrate(10)
    }

    /**
     * Medium tap for feature toggles
     */
    fun mediumTap(view: View? = null) {
        view?.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        ) ?: vibrate(15)
    }

    /**
     * Strong tap for important actions
     */
    fun strongTap(view: View? = null) {
        view?.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            },
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        ) ?: vibrate(25)
    }

    /**
     * Photo capture shutter feel
     */
    fun photoCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    /**
     * Video recording start/stop
     */
    fun videoToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 50, 30)
            val amplitudes = intArrayOf(0, 100, 0, 100)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 30, 50, 30), -1)
        }
    }

    /**
     * Feature activation
     */
    fun featureActivated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 20, 30, 20)
            val amplitudes = intArrayOf(0, 150, 0, 100)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            vibrate(20)
        }
    }

    /**
     * Feature deactivated
     */
    fun featureDeactivated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(15, 80)
            vibrator?.vibrate(effect)
        } else {
            vibrate(15)
        }
    }

    /**
     * Success confirmation
     */
    fun success() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 40, 30)
            val amplitudes = intArrayOf(0, 100, 0, 150)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 30, 40, 30), -1)
        }
    }

    /**
     * Error notification
     */
    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 100, 50, 100, 50)
            val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 50, 100, 50, 100, 50), -1)
        }
    }

    /**
     * Warning notification
     */
    fun warning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 40, 80, 40)
            val amplitudes = intArrayOf(0, 150, 0, 150)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 40, 80, 40), -1)
        }
    }

    /**
     * Selection change
     */
    fun selectionChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator?.vibrate(effect)
        } else {
            vibrate(5)
        }
    }

    /**
     * Long press recognized
     */
    fun longPressRecognized() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(30, 180)
            vibrator?.vibrate(effect)
        } else {
            vibrate(30)
        }
    }

    /**
     * Simple vibration helper
     */
    private fun vibrate(milliseconds: Long) {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(milliseconds)
    }

    /**
     * Check if haptics are available
     */
    fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }

    companion object {
        private const val TAG = "EnhancedHapticManager"
    }
}
