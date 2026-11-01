package com.customcamera.app.util

import android.media.MediaActionSound
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Shutter sounds and haptics.
 *
 * Some regions (e.g. Japan, Korea) legally require an audible shutter; on
 * API 33+ [MediaActionSound.mustPlayShutterSound] reports this and the user
 * setting is overridden.
 */
class CaptureFeedback {

    private val sound = MediaActionSound().apply {
        load(MediaActionSound.SHUTTER_CLICK)
        load(MediaActionSound.START_VIDEO_RECORDING)
        load(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    private fun soundRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && MediaActionSound.mustPlayShutterSound()

    fun shutter(enabled: Boolean) {
        if (enabled || soundRequired()) sound.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun recordingStarted(enabled: Boolean) {
        if (enabled || soundRequired()) sound.play(MediaActionSound.START_VIDEO_RECORDING)
    }

    fun recordingStopped(enabled: Boolean) {
        if (enabled || soundRequired()) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
    }

    fun haptic(view: View, enabled: Boolean, strong: Boolean = false) {
        if (!enabled) return
        val constant = if (strong && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    fun release() = sound.release()
}
