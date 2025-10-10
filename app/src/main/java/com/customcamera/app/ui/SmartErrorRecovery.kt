package com.customcamera.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.customcamera.app.exceptions.*

/**
 * Smart error recovery system providing user-friendly error messages
 * and actionable recovery options.
 */
class SmartErrorRecovery(
    private val context: Context,
    private val parentView: ViewGroup
) {

    private var errorOverlay: LinearLayout? = null

    var onRetry: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    /**
     * Initialize error recovery system
     */
    fun initialize() {
        createErrorOverlay()
        Log.i(TAG, "Smart error recovery initialized")
    }

    private fun createErrorOverlay() {
        errorOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#EE000000"))
            visibility = View.GONE

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        parentView.addView(errorOverlay)
    }

    /**
     * Show error with recovery options
     */
    fun showError(error: Throwable) {
        val errorInfo = analyzeError(error)
        displayError(errorInfo)
    }

    /**
     * Show error with custom message
     */
    fun showError(title: String, message: String, actions: List<ErrorAction> = emptyList()) {
        val errorInfo = ErrorInfo(
            title = title,
            message = message,
            icon = "⚠️",
            actions = actions.ifEmpty { getDefaultActions() }
        )
        displayError(errorInfo)
    }

    /**
     * Analyze exception and create appropriate error info
     */
    private fun analyzeError(error: Throwable): ErrorInfo {
        return when (error) {
            is CameraBindingException -> ErrorInfo(
                title = "Camera Binding Failed",
                message = "Unable to connect to camera. Another app might be using it.",
                icon = "📷",
                actions = listOf(
                    ErrorAction("Retry", ::retry),
                    ErrorAction("Open Recent Apps", ::openRecents),
                    ErrorAction("Dismiss", ::dismiss)
                )
            )

            is CameraPermissionException -> ErrorInfo(
                title = "Camera Permission Required",
                message = "Please grant camera permission to use this app.",
                icon = "🔒",
                actions = listOf(
                    ErrorAction("Open Settings", ::openAppSettings),
                    ErrorAction("Dismiss", ::dismiss)
                )
            )

            is CameraConfigurationException -> ErrorInfo(
                title = "Configuration Error",
                message = "Camera configuration failed. Try restarting the app.",
                icon = "⚙️",
                actions = listOf(
                    ErrorAction("Retry", ::retry),
                    ErrorAction("Reset Settings", ::resetSettings),
                    ErrorAction("Dismiss", ::dismiss)
                )
            )

            is CaptureFailedException -> ErrorInfo(
                title = "Capture Failed",
                message = "Failed to capture photo. ${error.message ?: ""}",
                icon = "📸",
                actions = listOf(
                    ErrorAction("Retry", ::retry),
                    ErrorAction("Check Storage", ::checkStorage),
                    ErrorAction("Dismiss", ::dismiss)
                )
            )

            is NoCamerasAvailableException -> ErrorInfo(
                title = "No Cameras Found",
                message = "Your device doesn't have any cameras available.",
                icon = "❌",
                actions = listOf(
                    ErrorAction("Retry Detection", ::retry),
                    ErrorAction("Dismiss", ::dismiss)
                )
            )

            else -> ErrorInfo(
                title = "Unexpected Error",
                message = error.message ?: "An unknown error occurred.",
                icon = "⚠️",
                actions = getDefaultActions()
            )
        }
    }

    /**
     * Display error overlay
     */
    private fun displayError(errorInfo: ErrorInfo) {
        errorOverlay?.removeAllViews()

        // Icon
        errorOverlay?.addView(TextView(context).apply {
            text = errorInfo.icon
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        // Title
        errorOverlay?.addView(TextView(context).apply {
            text = errorInfo.title
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // Message
        errorOverlay?.addView(TextView(context).apply {
            text = errorInfo.message
            textSize = 16f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Action buttons
        errorInfo.actions.forEach { action ->
            errorOverlay?.addView(Button(context).apply {
                text = action.label
                textSize = 16f
                setPadding(48, 24, 48, 24)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                setOnClickListener { action.action() }
            })
        }

        show()
        Log.e(TAG, "Error displayed: ${errorInfo.title} - ${errorInfo.message}")
    }

    /**
     * Show the error overlay
     */
    private fun show() {
        errorOverlay?.let { view ->
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    /**
     * Hide the error overlay
     */
    fun hide() {
        errorOverlay?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                errorOverlay?.visibility = View.GONE
                errorOverlay?.removeAllViews()
            }
            ?.start()
    }

    /**
     * Default error actions
     */
    private fun getDefaultActions() = listOf(
        ErrorAction("Retry", ::retry),
        ErrorAction("Dismiss", ::dismiss)
    )

    /**
     * Recovery actions
     */
    private fun retry() {
        hide()
        onRetry?.invoke()
        Log.i(TAG, "User requested retry")
    }

    private fun dismiss() {
        hide()
        onDismiss?.invoke()
        Log.i(TAG, "Error dismissed")
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            hide()
            Log.i(TAG, "Opened app settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }

    private fun openRecents() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Opened recent apps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open recent apps", e)
        }
    }

    private fun checkStorage() {
        try {
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            hide()
            Log.i(TAG, "Opened storage settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open storage settings", e)
        }
    }

    private fun resetSettings() {
        try {
            // Clear settings
            context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            Toast.makeText(context, "Settings reset successfully", Toast.LENGTH_SHORT).show()
            retry()
            Log.i(TAG, "Settings reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset settings", e)
        }
    }

    /**
     * Data classes
     */
    private data class ErrorInfo(
        val title: String,
        val message: String,
        val icon: String,
        val actions: List<ErrorAction>
    )

    private data class ErrorAction(
        val label: String,
        val action: () -> Unit
    )

    companion object {
        private const val TAG = "SmartErrorRecovery"
    }
}
