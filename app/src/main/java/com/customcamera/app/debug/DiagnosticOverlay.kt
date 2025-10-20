package com.customcamera.app.debug

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.core.CameraState
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time diagnostic overlay showing camera and sensor status
 *
 * Displays:
 * - Camera state (CLOSED, OPENING, OPEN, etc.)
 * - Sensor availability (magnetometer critical for Samsung)
 * - Permissions status
 * - Camera lifecycle events
 * - Error messages with timestamps
 */
class DiagnosticOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    private val container: LinearLayout
    private val headerText: TextView
    private val statusText: TextView
    private val sensorText: TextView
    private val eventsText: TextView
    private val events = mutableListOf<String>()
    private val maxEvents = 20

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        setBackgroundColor(Color.argb(220, 0, 0, 0))
        setPadding(16, 16, 16, 16)

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        // Header
        headerText = TextView(context).apply {
            text = "🔍 CAMERA DIAGNOSTICS"
            textSize = 16f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        container.addView(headerText)

        // Status section
        statusText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
            setPadding(0, 0, 0, 16)
        }
        container.addView(statusText)

        // Sensor section
        sensorText = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
            setPadding(0, 0, 0, 16)
        }
        container.addView(sensorText)

        // Events section
        val eventsHeader = TextView(context).apply {
            text = "📋 RECENT EVENTS"
            textSize = 14f
            setTextColor(Color.CYAN)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        container.addView(eventsHeader)

        eventsText = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        container.addView(eventsText)

        addView(container)

        // Initial sensor check
        updateSensorInfo()
    }

    /**
     * Update camera state display
     */
    fun updateCameraState(cameraId: String, state: CameraState) {
        val stateIcon = when (state.type) {
            CameraState.Type.PENDING_OPEN -> "⏳"
            CameraState.Type.OPENING -> "🔓"
            CameraState.Type.OPEN -> "✅"
            CameraState.Type.CLOSING -> "🔒"
            CameraState.Type.CLOSED -> "❌"
        }

        val stateColor = when (state.type) {
            CameraState.Type.OPEN -> Color.GREEN
            CameraState.Type.CLOSED -> Color.RED
            CameraState.Type.OPENING -> Color.YELLOW
            else -> Color.GRAY
        }

        val errorInfo = state.error?.let { error ->
            val errorType = when (error.code) {
                CameraState.ERROR_STREAM_CONFIG -> "STREAM_CONFIG"
                CameraState.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
                CameraState.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS"
                CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "RECOVERABLE"
                CameraState.ERROR_CAMERA_DISABLED -> "DISABLED"
                CameraState.ERROR_CAMERA_FATAL_ERROR -> "FATAL"
                CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "DND_MODE"
                else -> "UNKNOWN(${error.code})"
            }
            "\n  ❌ ERROR: $errorType\n  Cause: ${error.cause?.message ?: "Unknown"}"
        } ?: ""

        statusText.text = """
            📷 CAMERA: $cameraId
            State: $stateIcon ${state.type.name}$errorInfo

            🔐 PERMISSIONS:
            ${getPermissionStatus()}
        """.trimIndent()

        statusText.setTextColor(stateColor)

        // Log event
        logEvent("$stateIcon Camera $cameraId: ${state.type.name}")
        state.error?.let { error ->
            logEvent("  ❌ Error: ${error.code}")
        }
    }

    /**
     * Update sensor information
     */
    fun updateSensorInfo() {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            sensorText.text = "❌ SensorManager not available!"
            sensorText.setTextColor(Color.RED)
            return
        }

        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)

        val magStatus = if (magnetometer != null) {
            "✅ ${magnetometer.name}"
        } else {
            "❌ NOT AVAILABLE\n    ⚠️  Samsung AI Manager may cause camera delays"
        }

        val accStatus = if (accelerometer != null) {
            "✅ ${accelerometer.name}"
        } else {
            "❌ NOT AVAILABLE"
        }

        val gyroStatus = if (gyroscope != null) {
            "✅ ${gyroscope.name}"
        } else {
            "⚠️  Not available"
        }

        sensorText.text = """
            🔬 SENSORS (${allSensors.size} total):
            Magnetometer: $magStatus
            Accelerometer: $accStatus
            Gyroscope: $gyroStatus
        """.trimIndent()

        // Color based on magnetometer availability
        sensorText.setTextColor(if (magnetometer == null) Color.YELLOW else Color.GREEN)
    }

    /**
     * Get permission status string
     */
    private fun getPermissionStatus(): String {
        val camera = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val audio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val storage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        return """
            Camera: ${if (camera) "✅ Granted" else "❌ DENIED"}
            Audio: ${if (audio) "✅ Granted" else "⚠️ Denied"}
            Storage: ${if (storage) "✅ Granted" else "⚠️ Denied"}
        """.trimIndent()
    }

    /**
     * Log an event with timestamp
     */
    fun logEvent(message: String) {
        val timestamp = timeFormat.format(Date())
        events.add(0, "$timestamp $message")

        // Keep only recent events
        if (events.size > maxEvents) {
            events.removeAt(events.size - 1)
        }

        updateEventsDisplay()
    }

    /**
     * Update events display
     */
    private fun updateEventsDisplay() {
        eventsText.text = events.joinToString("\n")
    }

    /**
     * Log camera lifecycle event
     */
    fun logLifecycleEvent(event: String) {
        logEvent("🔄 $event")
    }

    /**
     * Log camera binding attempt
     */
    fun logCameraBinding(cameraId: String, useCaseCount: Int) {
        logEvent("📸 Binding $cameraId with $useCaseCount use cases")
    }

    /**
     * Log error
     */
    fun logError(error: String) {
        logEvent("❌ ERROR: $error")
    }

    /**
     * Clear events
     */
    fun clearEvents() {
        events.clear()
        updateEventsDisplay()
    }

    /**
     * Show/hide overlay
     */
    fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
    }

    fun toggle() {
        visibility = if (visibility == VISIBLE) GONE else VISIBLE
    }
}
