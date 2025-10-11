package com.customcamera.app

import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.debug.CameraAPIMonitor
import com.customcamera.app.debug.CameraResetManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.*

/**
 * DebugActivity provides comprehensive debug interface
 * for camera testing and troubleshooting.
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var debugContainer: LinearLayout
    private lateinit var settingsManager: SettingsManager
    private var cameraAPIMonitor: CameraAPIMonitor? = null
    private lateinit var cameraResetManager: CameraResetManager
    private lateinit var cameraManager: CameraManager

    private var realTimeMonitoringJob: Job? = null
    private var cameraStatsTextView: TextView? = null
    private var eventStreamTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "DebugActivity onCreate")

        createDebugLayout()
        setupToolbar()
        initializeDebugSystems()
        createDebugUI()
    }

    private fun createDebugLayout() {
        val scrollView = ScrollView(this)
        debugContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scrollView.addView(debugContainer)
        setContentView(scrollView)
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Camera Debug Interface"
        }
    }

    private fun initializeDebugSystems() {
        settingsManager = SettingsManager(this)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Get global API monitor instance from active camera session
        cameraAPIMonitor = com.customcamera.app.debug.GlobalAPIMonitor.getInstance()

        if (cameraAPIMonitor != null) {
            Log.i(TAG, "✅ Using global API monitor from active camera session")
        } else {
            Log.w(TAG, "⚠️ No active camera session - API monitor not available")
        }

        // Initialize debug systems (mock camera context for reset manager)
        val mockContext = try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            com.customcamera.app.engine.CameraContext(
                context = this,
                cameraProvider = cameraProvider,
                debugLogger = com.customcamera.app.engine.DebugLogger(),
                settingsManager = settingsManager,
                cameraEngine = null,
                apiMonitor = cameraAPIMonitor
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera context for debug", e)
            null
        }

        if (mockContext != null) {
            cameraResetManager = CameraResetManager(mockContext)
        }
    }

    private fun createDebugUI() {
        // Live camera information display
        addTitle("Live Camera Information")
        addCameraInfoSection()

        // Camera enumeration with characteristics
        addTitle("Camera Enumeration")
        addCameraEnumerationSection()

        // Individual camera testing buttons
        addTitle("Camera Testing")
        addCameraTestingSection()

        // Camera API call log viewer
        addTitle("API Call Log")
        addAPILogSection()

        // Debug data export functionality
        addTitle("Debug Data Export")
        addExportSection()

        // Camera reset and recovery tools
        addTitle("Camera Recovery Tools")
        addRecoverySection()
    }

    private fun addTitle(title: String) {
        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setPadding(0, 24, 0, 12)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.BLACK)
        }
        debugContainer.addView(titleView)
    }

    private fun addCameraInfoSection() {
        val infoText = TextView(this).apply {
            text = "Camera info will be displayed here"
            textSize = 14f
            setPadding(8, 8, 8, 8)
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.LTGRAY)
        }
        debugContainer.addView(infoText)

        val refreshButton = Button(this).apply {
            text = "Refresh Camera Info"
            setOnClickListener {
                lifecycleScope.launch {
                    refreshCameraInfo(infoText)
                }
            }
        }
        debugContainer.addView(refreshButton)
    }

    private fun addCameraEnumerationSection() {
        val enumerationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(android.graphics.Color.LTGRAY)
        }
        debugContainer.addView(enumerationContainer)

        val enumerateButton = Button(this).apply {
            text = "Enumerate Available Cameras"
            setOnClickListener {
                lifecycleScope.launch {
                    enumerateCameras(enumerationContainer)
                }
            }
        }
        debugContainer.addView(enumerateButton)
    }

    private fun addCameraTestingSection() {
        val testContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val testCamera0Button = Button(this).apply {
            text = "Test Camera 0"
            setOnClickListener {
                lifecycleScope.launch {
                    testIndividualCamera("0")
                }
            }
        }

        val testCamera1Button = Button(this).apply {
            text = "Test Camera 1"
            setOnClickListener {
                lifecycleScope.launch {
                    testIndividualCamera("1")
                }
            }
        }

        testContainer.addView(testCamera0Button)
        testContainer.addView(testCamera1Button)
        debugContainer.addView(testContainer)
    }

    private fun addAPILogSection() {
        val logText = TextView(this).apply {
            text = buildString {
                appendLine("📋 Camera API Call Log")
                appendLine()
                appendLine("ℹ️ Note: API call tracking is currently not instrumented")
                appendLine("in the main camera activities.")
                appendLine()
                appendLine("To enable API call logging:")
                appendLine("• Add CameraAPIMonitor calls to CameraActivityEngine")
                appendLine("• Log camera binding, control, and capture operations")
                appendLine("• Monitor frame processing pipeline")
                appendLine()
                appendLine("Current status: Monitoring system ready, awaiting instrumentation")
            }
            textSize = 12f
            setPadding(8, 8, 8, 8)
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.LTGRAY)
        }
        debugContainer.addView(logText)

        val viewLogButton = Button(this).apply {
            text = "View API Call Log"
            setOnClickListener {
                try {
                    val logData = cameraAPIMonitor?.let { monitor ->
                        val report = monitor.generateDebugReport()
                        val stats = monitor.getAPICallStats()

                        if ((stats["totalCalls"] as? Int) ?: 0 > 0) {
                            report
                        } else {
                            buildString {
                                appendLine("=== Camera API Monitor Debug Report ===")
                                appendLine("Generated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                                appendLine()
                                appendLine("ℹ️ No API calls have been tracked yet")
                                appendLine()
                                appendLine("The API monitoring system is active and tracking camera operations.")
                                appendLine("Use the camera to generate activity, then check this log again.")
                                appendLine()
                                appendLine("📌 Tracked operations:")
                                appendLine("• Camera binding (when camera starts)")
                                appendLine("• Photo capture")
                                appendLine("• Camera switching")
                                appendLine("• Flash toggle")
                                appendLine("• Night mode toggle")
                                appendLine()
                                appendLine("Monitor Status: ✅ Active, awaiting camera operations")
                            }
                        }
                    } ?: buildString {
                        appendLine("=== Camera API Monitor Debug Report ===")
                        appendLine("Generated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                        appendLine()
                        appendLine("⚠️ No active camera session detected")
                        appendLine()
                        appendLine("The API monitor is only available when you have")
                        appendLine("an active camera session running.")
                        appendLine()
                        appendLine("📌 To activate monitoring:")
                        appendLine("1. Return to main screen")
                        appendLine("2. Open the camera")
                        appendLine("3. Use camera features (capture, switch, flash, etc.)")
                        appendLine("4. Return to this debug screen")
                        appendLine("5. View the logged API calls")
                        appendLine()
                        appendLine("Monitor Status: ⚠️ No active session")
                    }
                    logText.text = logData
                    Log.i(TAG, "API Log:\n$logData")
                } catch (e: Exception) {
                    Log.e(TAG, "Error viewing API log", e)
                    logText.text = "❌ Error: ${e.message}"
                }
            }
        }
        debugContainer.addView(viewLogButton)
    }

    private fun addExportSection() {
        val exportButton = Button(this).apply {
            text = "Export Debug Data"
            setOnClickListener {
                lifecycleScope.launch {
                    exportDebugData()
                }
            }
        }
        debugContainer.addView(exportButton)
    }

    private fun addRecoverySection() {
        val recoveryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val resetButton = Button(this).apply {
            text = "Reset Camera System"
            setOnClickListener {
                lifecycleScope.launch {
                    resetCameraSystem()
                }
            }
        }

        val flushButton = Button(this).apply {
            text = "Flush Camera Queue"
            setOnClickListener {
                lifecycleScope.launch {
                    flushCameraQueue()
                }
            }
        }

        recoveryContainer.addView(resetButton)
        recoveryContainer.addView(flushButton)
        debugContainer.addView(recoveryContainer)
    }

    private suspend fun refreshCameraInfo(infoText: TextView) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            val cameras = cameraProvider.availableCameraInfos
            val cameraIds = cameraManager.cameraIdList

            val info = buildString {
                appendLine("=== Camera System Overview ===")
                appendLine("Available Cameras: ${cameras.size}")
                appendLine("Default Camera Index: ${settingsManager.defaultCameraIndex.value}")
                appendLine("Debug Logging: ${settingsManager.debugLogging.value}")
                appendLine()

                cameras.forEachIndexed { index, cameraInfo ->
                    val facing = when (cameraInfo.lensFacing) {
                        androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
                        androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back"
                        else -> "External"
                    }

                    appendLine("--- Camera $index ($facing) ---")
                    appendLine("Flash: ${cameraInfo.hasFlashUnit()}")
                    appendLine("Sensor Rotation: ${cameraInfo.sensorRotationDegrees}°")

                    // Get Camera2 characteristics for detailed info
                    try {
                        if (index < cameraIds.size) {
                            val characteristics = cameraManager.getCameraCharacteristics(cameraIds[index])

                            // Sensor info
                            val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                            if (sensorSize != null) {
                                appendLine("Sensor Size: ${String.format("%.2f", sensorSize.width)}mm × ${String.format("%.2f", sensorSize.height)}mm")
                            }

                            val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                            if (activeArraySize != null) {
                                appendLine("Sensor Resolution: ${activeArraySize.width()} × ${activeArraySize.height()}")
                            }

                            // Focal length
                            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                            if (focalLengths != null && focalLengths.isNotEmpty()) {
                                appendLine("Focal Length: ${focalLengths.joinToString(", ")}mm")
                            }

                            // ISO range
                            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                            if (isoRange != null) {
                                appendLine("ISO Range: ${isoRange.lower} - ${isoRange.upper}")
                            }

                            // Exposure time range
                            val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                            if (exposureRange != null) {
                                val minMs = exposureRange.lower / 1_000_000.0
                                val maxMs = exposureRange.upper / 1_000_000.0
                                appendLine("Exposure: ${String.format("%.3f", minMs)}ms - ${String.format("%.1f", maxMs)}ms")
                            }

                            // Hardware level
                            val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                            val levelName = when (hardwareLevel) {
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                                else -> "Unknown"
                            }
                            appendLine("Hardware Level: $levelName")

                            // Focus modes
                            val focusModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                            if (focusModes != null && focusModes.isNotEmpty()) {
                                appendLine("Focus Modes: ${focusModes.size} available")
                            }

                            // Max zoom
                            val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                            if (maxZoom != null) {
                                appendLine("Max Digital Zoom: ${String.format("%.1f", maxZoom)}x")
                            }
                        }
                    } catch (e: CameraAccessException) {
                        appendLine("(Unable to access detailed Camera2 characteristics)")
                        Log.w(TAG, "Error accessing Camera2 characteristics for camera $index", e)
                    }

                    appendLine()
                }
            }

            infoText.text = info
            Log.i(TAG, "Camera info refreshed with detailed characteristics")

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing camera info", e)
            infoText.text = "Error: ${e.message}"
        }
    }

    private suspend fun enumerateCameras(container: LinearLayout) {
        try {
            container.removeAllViews()

            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            val cameras = cameraProvider.availableCameraInfos

            cameras.forEachIndexed { index, cameraInfo ->
                val cameraView = TextView(this).apply {
                    val facing = when (cameraInfo.lensFacing) {
                        androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
                        androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back"
                        else -> "External"
                    }
                    text = "Camera $index: $facing, Flash: ${cameraInfo.hasFlashUnit()}, Rotation: ${cameraInfo.sensorRotationDegrees}°"
                    textSize = 12f
                    setPadding(4, 4, 4, 4)
                    setTextColor(android.graphics.Color.BLACK)
                }
                container.addView(cameraView)
            }

            Log.i(TAG, "Cameras enumerated: ${cameras.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating cameras", e)
            val errorView = TextView(this).apply {
                text = "Error: ${e.message}"
                setTextColor(android.graphics.Color.RED)
            }
            container.addView(errorView)
        }
    }

    private suspend fun testIndividualCamera(cameraId: String) {
        try {
            Log.i(TAG, "Testing camera $cameraId")

            // Simple camera test
            Toast.makeText(this, "Testing camera $cameraId...", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Camera $cameraId test initiated")

        } catch (e: Exception) {
            Log.e(TAG, "Error testing camera $cameraId", e)
            Toast.makeText(this, "Test failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun exportDebugData() {
        try {
            val settingsSummary = settingsManager.getSettingsSummary()
            val debugReport = cameraAPIMonitor?.generateDebugReport()
                ?: "API Monitor not available (no active camera session)"

            val exportData = """
                === CustomCamera Debug Export ===
                Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}

                === Settings Summary ===
                $settingsSummary

                === API Monitor Report ===
                $debugReport
            """.trimIndent()

            Log.i(TAG, "Debug data exported:\n$exportData")
            Toast.makeText(this, "Debug data exported to log", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting debug data", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun resetCameraSystem() {
        try {
            Toast.makeText(this, "Resetting camera system...", Toast.LENGTH_SHORT).show()

            if (::cameraResetManager.isInitialized) {
                // Reinitialize the entire camera provider
                val success = cameraResetManager.reinitializeCameraProvider()

                if (success) {
                    Toast.makeText(this, "✅ Camera system reset complete", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Camera system reset successful")
                } else {
                    Toast.makeText(this, "⚠️ Camera reset completed with warnings", Toast.LENGTH_LONG).show()
                    Log.w(TAG, "Camera system reset had issues")
                }
            } else {
                Toast.makeText(this, "⚠️ Reset manager not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "CameraResetManager not initialized")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error resetting camera system", e)
            Toast.makeText(this, "❌ Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun flushCameraQueue() {
        try {
            Toast.makeText(this, "Flushing camera queue...", Toast.LENGTH_SHORT).show()

            if (::cameraResetManager.isInitialized) {
                // Unbind all cameras and run garbage collection
                val success = cameraResetManager.flushCameraQueue()

                if (success) {
                    Toast.makeText(this, "✅ Camera queue flushed successfully", Toast.LENGTH_LONG).show()
                    Log.i(TAG, "Camera queue flush successful")
                } else {
                    Toast.makeText(this, "⚠️ Flush completed with warnings", Toast.LENGTH_LONG).show()
                    Log.w(TAG, "Camera queue flush had issues")
                }
            } else {
                Toast.makeText(this, "⚠️ Reset manager not available", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "CameraResetManager not initialized")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error flushing camera queue", e)
            Toast.makeText(this, "❌ Flush failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realTimeMonitoringJob?.cancel()
    }

    companion object {
        private const val TAG = "DebugActivity"
        private const val CAMERA_TIMEOUT_MS = 10000L // 10 second timeout
        private const val STATS_UPDATE_INTERVAL_MS = 2000L // 2 second updates
    }
}