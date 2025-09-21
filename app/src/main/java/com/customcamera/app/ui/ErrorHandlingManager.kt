package com.customcamera.app.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.customcamera.app.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*

/**
 * Enhanced Error Handling Manager
 *
 * Provides comprehensive error handling with:
 * - User-friendly error messages
 * - Contextual error recovery suggestions
 * - Different error display methods (Toast, Snackbar, Dialog)
 * - Error logging and analytics
 * - Graceful degradation strategies
 */
class ErrorHandlingManager(private val context: Context) {

    companion object {
        private const val TAG = "ErrorHandlingManager"
        private const val ERROR_DISPLAY_DURATION = 4000L
        private const val CRITICAL_ERROR_TIMEOUT = 10000L
    }

    /**
     * Error severity levels
     */
    enum class ErrorSeverity {
        INFO,           // Informational messages
        WARNING,        // Non-critical issues
        ERROR,          // Standard errors that can be recovered
        CRITICAL        // Critical errors requiring immediate attention
    }

    /**
     * Error categories for better handling
     */
    enum class ErrorCategory {
        CAMERA_PERMISSION,
        CAMERA_HARDWARE,
        PHOTO_CAPTURE,
        VIDEO_RECORDING,
        STORAGE_ACCESS,
        NETWORK_CONNECTION,
        PLUGIN_LOADING,
        SETTINGS_SAVE,
        UNKNOWN
    }

    /**
     * Error display methods
     */
    enum class DisplayMethod {
        TOAST,          // Simple toast message
        SNACKBAR,       // Snackbar with optional action
        DIALOG,         // Alert dialog for important errors
        SILENT          // Log only, no UI display
    }

    /**
     * Handle an error with comprehensive error management
     */
    fun handleError(
        error: Throwable,
        category: ErrorCategory = ErrorCategory.UNKNOWN,
        severity: ErrorSeverity = ErrorSeverity.ERROR,
        displayMethod: DisplayMethod = DisplayMethod.SNACKBAR,
        parentView: View? = null,
        customMessage: String? = null,
        onRetry: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        // Log the error for debugging
        logError(error, category, severity)

        // Get user-friendly message
        val userMessage = customMessage ?: getUserFriendlyMessage(error, category)

        // Display error based on method and severity
        when (displayMethod) {
            DisplayMethod.TOAST -> showToastError(userMessage, severity)
            DisplayMethod.SNACKBAR -> showSnackbarError(userMessage, parentView, onRetry, severity)
            DisplayMethod.DIALOG -> showDialogError(userMessage, onRetry, onDismiss, severity)
            DisplayMethod.SILENT -> { /* Already logged */ }
        }

        // Handle critical errors with special treatment
        if (severity == ErrorSeverity.CRITICAL) {
            handleCriticalError(error, category, userMessage, onRetry)
        }
    }

    /**
     * Quick error handling methods for common scenarios
     */
    fun handleCameraError(error: Throwable, parentView: View? = null, onRetry: (() -> Unit)? = null) {
        handleError(
            error = error,
            category = ErrorCategory.CAMERA_HARDWARE,
            severity = ErrorSeverity.ERROR,
            displayMethod = DisplayMethod.SNACKBAR,
            parentView = parentView,
            onRetry = onRetry
        )
    }

    fun handlePermissionError(parentView: View? = null, onRetry: (() -> Unit)? = null) {
        val message = "Camera permission is required to use this feature. Please grant permission in Settings."
        handleError(
            error = SecurityException("Camera permission denied"),
            category = ErrorCategory.CAMERA_PERMISSION,
            severity = ErrorSeverity.WARNING,
            displayMethod = DisplayMethod.DIALOG,
            parentView = parentView,
            customMessage = message,
            onRetry = onRetry
        )
    }

    fun handleStorageError(error: Throwable, parentView: View? = null, onRetry: (() -> Unit)? = null) {
        handleError(
            error = error,
            category = ErrorCategory.STORAGE_ACCESS,
            severity = ErrorSeverity.ERROR,
            displayMethod = DisplayMethod.SNACKBAR,
            parentView = parentView,
            onRetry = onRetry
        )
    }

    fun handleNetworkError(error: Throwable, parentView: View? = null, onRetry: (() -> Unit)? = null) {
        handleError(
            error = error,
            category = ErrorCategory.NETWORK_CONNECTION,
            severity = ErrorSeverity.WARNING,
            displayMethod = DisplayMethod.SNACKBAR,
            parentView = parentView,
            onRetry = onRetry
        )
    }

    /**
     * Show success message
     */
    fun showSuccess(message: String, parentView: View? = null) {
        parentView?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(context.getColor(android.R.color.holo_green_dark))
                .setTextColor(context.getColor(android.R.color.white))
                .show()
        } ?: Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show informational message
     */
    fun showInfo(message: String, parentView: View? = null, duration: Int = Snackbar.LENGTH_LONG) {
        parentView?.let { view ->
            Snackbar.make(view, message, duration)
                .setBackgroundTint(context.getColor(android.R.color.holo_blue_dark))
                .setTextColor(context.getColor(android.R.color.white))
                .show()
        } ?: Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    // Private implementation methods

    private fun logError(error: Throwable, category: ErrorCategory, severity: ErrorSeverity) {
        val logLevel = when (severity) {
            ErrorSeverity.INFO -> Log.INFO
            ErrorSeverity.WARNING -> Log.WARN
            ErrorSeverity.ERROR -> Log.ERROR
            ErrorSeverity.CRITICAL -> Log.ERROR
        }

        Log.println(logLevel, TAG, "[$category] ${error.message}")
        if (severity == ErrorSeverity.CRITICAL || severity == ErrorSeverity.ERROR) {
            Log.println(logLevel, TAG, Log.getStackTraceString(error))
        }
    }

    private fun getUserFriendlyMessage(error: Throwable, category: ErrorCategory): String {
        return when (category) {
            ErrorCategory.CAMERA_PERMISSION -> {
                "Camera permission is needed to take photos. Please enable it in Settings."
            }
            ErrorCategory.CAMERA_HARDWARE -> {
                when {
                    error.message?.contains("camera", ignoreCase = true) == true -> {
                        "Camera is temporarily unavailable. Please try again or restart the app."
                    }
                    error.message?.contains("bind", ignoreCase = true) == true -> {
                        "Failed to connect to camera. Try switching cameras or restarting the app."
                    }
                    else -> "Camera error occurred. Please try again."
                }
            }
            ErrorCategory.PHOTO_CAPTURE -> {
                when {
                    error.message?.contains("storage", ignoreCase = true) == true -> {
                        "Not enough storage space. Please free up space and try again."
                    }
                    error.message?.contains("permission", ignoreCase = true) == true -> {
                        "Storage permission required to save photos. Please enable it in Settings."
                    }
                    else -> "Failed to capture photo. Please try again."
                }
            }
            ErrorCategory.VIDEO_RECORDING -> {
                when {
                    error.message?.contains("storage", ignoreCase = true) == true -> {
                        "Not enough storage space for video recording. Please free up space."
                    }
                    else -> "Video recording failed. Please try again."
                }
            }
            ErrorCategory.STORAGE_ACCESS -> {
                "Unable to access storage. Please check permissions and available space."
            }
            ErrorCategory.NETWORK_CONNECTION -> {
                "Network connection unavailable. Some features may not work properly."
            }
            ErrorCategory.PLUGIN_LOADING -> {
                "Feature temporarily unavailable. Please try again later."
            }
            ErrorCategory.SETTINGS_SAVE -> {
                "Failed to save settings. Your changes may not be preserved."
            }
            ErrorCategory.UNKNOWN -> {
                "An unexpected error occurred. Please try again."
            }
        }
    }

    private fun showToastError(message: String, severity: ErrorSeverity) {
        val duration = when (severity) {
            ErrorSeverity.CRITICAL -> Toast.LENGTH_LONG
            else -> Toast.LENGTH_SHORT
        }
        Toast.makeText(context, message, duration).show()
    }

    private fun showSnackbarError(
        message: String,
        parentView: View?,
        onRetry: (() -> Unit)?,
        severity: ErrorSeverity
    ) {
        parentView?.let { view ->
            val duration = when (severity) {
                ErrorSeverity.CRITICAL -> Snackbar.LENGTH_INDEFINITE
                ErrorSeverity.ERROR -> Snackbar.LENGTH_LONG
                else -> Snackbar.LENGTH_SHORT
            }

            val snackbar = Snackbar.make(view, message, duration)
                .setBackgroundTint(getErrorColor(severity))
                .setTextColor(context.getColor(android.R.color.white))

            // Add retry action if provided
            onRetry?.let { retry ->
                snackbar.setAction("Retry") { retry() }
                snackbar.setActionTextColor(context.getColor(android.R.color.white))
            }

            snackbar.show()
        } ?: showToastError(message, severity)
    }

    private fun showDialogError(
        message: String,
        onRetry: (() -> Unit)?,
        onDismiss: (() -> Unit)?,
        severity: ErrorSeverity
    ) {
        val title = when (severity) {
            ErrorSeverity.CRITICAL -> "Critical Error"
            ErrorSeverity.ERROR -> "Error"
            ErrorSeverity.WARNING -> "Warning"
            ErrorSeverity.INFO -> "Information"
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(severity != ErrorSeverity.CRITICAL)

        // Add retry button if provided
        onRetry?.let { retry ->
            builder.setPositiveButton("Retry") { _, _ -> retry() }
        }

        // Add dismiss button
        builder.setNegativeButton("OK") { dialog, _ ->
            dialog.dismiss()
            onDismiss?.invoke()
        }

        builder.create().show()
    }

    private fun handleCriticalError(
        error: Throwable,
        category: ErrorCategory,
        message: String,
        onRetry: (() -> Unit)?
    ) {
        Log.e(TAG, "CRITICAL ERROR [$category]: ${error.message}", error)

        // For critical errors, also show a dialog with more options
        if (onRetry == null) {
            // If no retry is available, offer to restart the app or contact support
            AlertDialog.Builder(context)
                .setTitle("Critical Error")
                .setMessage("$message\n\nThe app may need to be restarted.")
                .setPositiveButton("Restart App") { _, _ ->
                    // TODO: Implement app restart logic
                }
                .setNegativeButton("Continue") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun getErrorColor(severity: ErrorSeverity): Int {
        return when (severity) {
            ErrorSeverity.CRITICAL -> context.getColor(android.R.color.holo_red_dark)
            ErrorSeverity.ERROR -> context.getColor(android.R.color.holo_orange_dark)
            ErrorSeverity.WARNING -> context.getColor(android.R.color.holo_orange_light)
            ErrorSeverity.INFO -> context.getColor(android.R.color.holo_blue_dark)
        }
    }
}