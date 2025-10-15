package com.customcamera.app.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.customcamera.app.R

/**
 * Enhanced Toast Notifications - Professional, icon-based toasts
 *
 * Provides:
 * - Success, error, warning, info toasts
 * - Icons for visual clarity
 * - Consistent styling
 * - Duration control
 * - Position control
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
     */
    private fun show(context: Context, message: String, type: ToastType, duration: Int) {
        val toast = Toast(context)
        toast.duration = duration
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 150)

        // Create custom view
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 25, 40, 25)
            gravity = Gravity.CENTER_VERTICAL

            // Background with rounded corners
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 30f
                setColor(type.backgroundColor)
                setStroke(3, type.borderColor)
            }
            this.background = background
        }

        // Icon
        val iconText = TextView(context).apply {
            text = type.icon
            textSize = 24f
            setPadding(0, 0, 20, 0)
        }
        layout.addView(iconText)

        // Message
        val messageText = TextView(context).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        layout.addView(messageText)

        toast.view = layout
        toast.show()
    }

    /**
     * Toast type configuration
     */
    private enum class ToastType(
        val icon: String,
        val backgroundColor: Int,
        val borderColor: Int
    ) {
        SUCCESS(
            icon = "✓",
            backgroundColor = Color.argb(230, 40, 167, 69),
            borderColor = Color.argb(255, 60, 200, 90)
        ),
        ERROR(
            icon = "✖",
            backgroundColor = Color.argb(230, 220, 53, 69),
            borderColor = Color.argb(255, 255, 80, 100)
        ),
        WARNING(
            icon = "⚠",
            backgroundColor = Color.argb(230, 255, 193, 7),
            borderColor = Color.argb(255, 255, 220, 50)
        ),
        INFO(
            icon = "ℹ",
            backgroundColor = Color.argb(230, 0, 123, 255),
            borderColor = Color.argb(255, 50, 150, 255)
        )
    }
}
