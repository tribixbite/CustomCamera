package com.customcamera.app.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import com.google.android.material.snackbar.Snackbar

/**
 * Enhanced error presentation system for professional user feedback
 * with contextual error messages, visual feedback, and actionable responses.
 */
object ErrorPresentation {

    /**
     * Error severity levels with visual styling
     */
    enum class ErrorSeverity(
        val colorRes: Int,
        val iconRes: Int,
        val priority: Int
    ) {
        INFO(R.color.info_color, R.drawable.ic_info, 1),
        WARNING(R.color.warning_color, R.drawable.ic_warning, 2),
        ERROR(R.color.error_color, R.drawable.ic_error, 3),
        CRITICAL(R.color.error_color, R.drawable.ic_critical_error, 4)
    }

    /**
     * Error context for better user guidance
     */
    data class ErrorContext(
        val title: String,
        val message: String,
        val severity: ErrorSeverity = ErrorSeverity.ERROR,
        val actionText: String? = null,
        val action: (() -> Unit)? = null,
        val dismissText: String = "OK",
        val technicalDetails: String? = null,
        val showTechnicalDetails: Boolean = false
    )

    /**
     * Show contextual Snackbar with appropriate styling
     */
    fun showSnackbar(
        view: View,
        context: ErrorContext,
        duration: Int = Snackbar.LENGTH_LONG
    ): Snackbar {
        val snackbar = Snackbar.make(view, context.message, duration)

        // Style the snackbar based on severity
        val snackbarView = snackbar.view
        val backgroundColor = ContextCompat.getColor(view.context, context.severity.colorRes)
        snackbarView.setBackgroundColor(backgroundColor)

        // Add action if provided
        context.actionText?.let { actionText ->
            snackbar.setAction(actionText) {
                context.action?.invoke()
            }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, android.R.color.white))
        }

        // Add visual feedback animation
        when (context.severity) {
            ErrorSeverity.ERROR, ErrorSeverity.CRITICAL -> {
                AnimationUtils.shake(snackbarView, 5f)
            }
            ErrorSeverity.WARNING -> {
                AnimationUtils.pulse(snackbarView, 1)
            }
            else -> {
                AnimationUtils.fadeIn(snackbarView, 300L)
            }
        }

        snackbar.show()
        return snackbar
    }

    /**
     * Show enhanced Toast with severity styling
     */
    fun showToast(
        context: Context,
        errorContext: ErrorContext,
        duration: Int = Toast.LENGTH_LONG
    ): Toast {
        val toast = Toast.makeText(context, errorContext.message, duration)

        // Customize toast view for better visibility
        toast.view?.let { toastView ->
            val backgroundColor = ContextCompat.getColor(context, errorContext.severity.colorRes)
            toastView.setBackgroundColor(backgroundColor)

            // Find TextView and update styling
            val messageView = toastView.findViewById<TextView>(android.R.id.message)
            messageView?.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }

        toast.show()
        return toast
    }

    /**
     * Show comprehensive error dialog with technical details
     */
    fun showErrorDialog(
        context: Context,
        errorContext: ErrorContext,
        onDismiss: (() -> Unit)? = null
    ): AlertDialog {
        val builder = AlertDialog.Builder(context, R.style.Theme_CustomCamera)
            .setTitle(errorContext.title)
            .setMessage(buildDialogMessage(errorContext))
            .setIcon(errorContext.severity.iconRes)

        // Add primary action
        errorContext.actionText?.let { actionText ->
            builder.setPositiveButton(actionText) { _, _ ->
                errorContext.action?.invoke()
            }
        }

        // Add dismiss button
        builder.setNegativeButton(errorContext.dismissText) { dialog, _ ->
            dialog.dismiss()
            onDismiss?.invoke()
        }

        // Add technical details button if available
        if (errorContext.showTechnicalDetails && !errorContext.technicalDetails.isNullOrEmpty()) {
            builder.setNeutralButton("Technical Details") { _, _ ->
                showTechnicalDetailsDialog(context, errorContext)
            }
        }

        val dialog = builder.create()
        dialog.show()

        return dialog
    }

    /**
     * Show technical details in a separate dialog
     */
    private fun showTechnicalDetailsDialog(
        context: Context,
        errorContext: ErrorContext
    ) {
        AlertDialog.Builder(context, R.style.Theme_CustomCamera)
            .setTitle("Technical Details")
            .setMessage(errorContext.technicalDetails)
            .setPositiveButton("Copy to Clipboard") { _, _ ->
                copyToClipboard(context, errorContext.technicalDetails ?: "")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Show inline error message in a view container
     */
    fun showInlineError(
        container: ViewGroup,
        errorContext: ErrorContext
    ): View {
        // Remove any existing error views
        removeInlineError(container)

        val context = container.context
        val errorView = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(context), 12.dpToPx(context), 16.dpToPx(context), 12.dpToPx(context))
            setBackgroundColor(ContextCompat.getColor(context, errorContext.severity.colorRes))
            tag = "inline_error"
        }

        // Error icon
        val iconView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                24.dpToPx(context),
                24.dpToPx(context)
            ).apply {
                marginEnd = 12.dpToPx(context)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            setImageResource(errorContext.severity.iconRes)
            setColorFilter(ContextCompat.getColor(context, android.R.color.white))
        }

        // Error message
        val messageView = TextView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = errorContext.message
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 14f
        }

        errorView.addView(iconView)
        errorView.addView(messageView)

        // Add action button if provided
        errorContext.actionText?.let { actionText ->
            val actionButton = android.widget.Button(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 12.dpToPx(context)
                }
                text = actionText
                textSize = 12f
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setOnClickListener {
                    errorContext.action?.invoke()
                    removeInlineError(container)
                }
            }
            errorView.addView(actionButton)
        }

        container.addView(errorView, 0)
        AnimationUtils.slideInFromRight(errorView)

        return errorView
    }

    /**
     * Remove inline error message
     */
    fun removeInlineError(container: ViewGroup) {
        val existingError = container.findViewWithTag<View>("inline_error")
        existingError?.let { errorView ->
            AnimationUtils.slideOutToLeft(errorView) {
                container.removeView(errorView)
            }
        }
    }

    /**
     * Camera-specific error contexts
     */
    object CameraErrors {
        fun cameraNotAvailable() = ErrorContext(
            title = "Camera Unavailable",
            message = "The selected camera is not available. Please try a different camera or restart the app.",
            severity = ErrorSeverity.ERROR,
            actionText = "Select Different Camera",
            technicalDetails = "Camera provider returned null or camera ID not found in available cameras list."
        )

        fun permissionDenied() = ErrorContext(
            title = "Camera Permission Required",
            message = "Camera access is required to take photos and videos. Please grant permission in Settings.",
            severity = ErrorSeverity.WARNING,
            actionText = "Open Settings",
            technicalDetails = "CAMERA permission denied by user or system policy."
        )

        fun captureError(exception: Exception) = ErrorContext(
            title = "Capture Failed",
            message = "Failed to capture photo. Please try again.",
            severity = ErrorSeverity.ERROR,
            actionText = "Retry",
            technicalDetails = "Capture exception: ${exception.message}\n\nStack trace:\n${exception.stackTraceToString()}",
            showTechnicalDetails = true
        )

        fun cameraInitError(exception: Exception) = ErrorContext(
            title = "Camera Initialization Failed",
            message = "Unable to initialize camera. Please check if another app is using the camera.",
            severity = ErrorSeverity.CRITICAL,
            actionText = "Try Again",
            technicalDetails = "Camera initialization exception: ${exception.message}\n\nStack trace:\n${exception.stackTraceToString()}",
            showTechnicalDetails = true
        )

        fun storageError() = ErrorContext(
            title = "Storage Error",
            message = "Unable to save photo. Please check available storage space.",
            severity = ErrorSeverity.ERROR,
            actionText = "Check Storage",
            technicalDetails = "Insufficient storage space or write permission denied."
        )

        fun settingsError(exception: Exception) = ErrorContext(
            title = "Settings Error",
            message = "Unable to load camera settings. Using default configuration.",
            severity = ErrorSeverity.WARNING,
            technicalDetails = "Settings exception: ${exception.message}",
            showTechnicalDetails = true
        )
    }

    /**
     * Helper functions
     */
    private fun buildDialogMessage(errorContext: ErrorContext): String {
        return if (errorContext.showTechnicalDetails && !errorContext.technicalDetails.isNullOrEmpty()) {
            "${errorContext.message}\n\nTap 'Technical Details' for more information."
        } else {
            errorContext.message
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Error Details", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}