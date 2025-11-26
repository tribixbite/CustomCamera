package com.customcamera.app.presentation

import android.content.Context
import android.view.Gravity
import android.widget.Toast

/**
 * Enhanced Toast Notifications - Professional, icon-based notifications
 *
 * NOTE: As of Android 11, custom Toast views are deprecated.
 * This class now uses simple Toast notifications without custom views.
 * For styled notifications with icons, consider using Snackbar or custom overlays instead.
 *
 * Provides:
 * - Success, error, warning, info notifications
 * - Icons embedded in message text
 * - Consistent messaging
 * - Duration control
 */
object EnhancedToast {

    /**
     * Show success toast
     */
    fun success(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        show(context, message, ToastType.SUCCESS, duration)
    }

    /**
     * Show error toast
     */
    fun error(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.ERROR, duration)
    }

    /**
     * Show warning toast
     */
    fun warning(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        show(context, message, ToastType.WARNING, duration)
    }

    /**
     * Show info toast
     */
    fun info(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        show(context, message, ToastType.INFO, duration)
    }

    /**
     * Show photo capture toast
     */
    fun photoSaved(context: Context, filename: String) {
        success(context, "📸 Photo saved: $filename")
    }

    /**
     * Show video recording toast
     */
    fun videoRecording(context: Context, isRecording: Boolean) {
        if (isRecording) {
            error(context, "🎥 Recording started")
        } else {
            success(context, "✅ Recording stopped")
        }
    }

    /**
     * Show feature activated toast
     */
    fun featureActivated(context: Context, featureName: String) {
        success(context, "✨ $featureName enabled")
    }

    /**
     * Show feature deactivated toast
     */
    fun featureDeactivated(context: Context, featureName: String) {
        info(context, "❌ $featureName disabled")
    }

    /**
     * Show dual camera toast
     */
    fun dualCameraPhoto(context: Context, filename: String) {
        success(context, "📷📷 Dual camera photo saved: $filename")
    }

    /**
     * Main toast display function
     *
     * Note: Custom toast views (toast.view) are deprecated as of Android 11.
     * This implementation uses basic Toast with icon prepended to message text.
     */
    private fun show(context: Context, message: String, type: ToastType, duration: Int) {
        // Prepend icon to message for visual context
        val iconMessage = "${type.icon} $message"

        val toast = Toast.makeText(context, iconMessage, duration)
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 150)
        toast.show()
    }

    /**
     * Toast type configuration
     *
     * Note: backgroundColor and borderColor removed as custom Toast views are deprecated.
     * Icons are now prepended to message text for visual differentiation.
     */
    private enum class ToastType(val icon: String) {
        SUCCESS("✓"),
        ERROR("✖"),
        WARNING("⚠"),
        INFO("ℹ")
    }
}
