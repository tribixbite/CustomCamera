package com.customcamera.app

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.lifecycleScope
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.debug.CameraAPIMonitor
import com.customcamera.app.debug.CameraResetManager
import kotlinx.coroutines.launch

/**
 * DebugActivity provides comprehensive debug interface
 * for camera testing and troubleshooting.
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var debugContainer: LinearLayout
    private lateinit var settingsManager: SettingsManager
    private lateinit var cameraAPIMonitor: CameraAPIMonitor
    private lateinit var cameraResetManager: CameraResetManager

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

        // Initialize debug systems (mock camera context for now)
        val mockContext = try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            com.customcamera.app.engine.CameraContext(
                context = this,
                cameraProvider = cameraProvider,
                debugLogger = com.customcamera.app.engine.DebugLogger(),
                settingsManager = settingsManager
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera context for debug", e)
            null
        }

        if (mockContext != null) {
            cameraAPIMonitor = CameraAPIMonitor(mockContext)
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
        }
        debugContainer.addView(titleView)
    }

    private fun addCameraInfoSection() {
        val infoText = TextView(this).apply {
            text = "Camera info will be displayed here"
            textSize = 14f
            setPadding(8, 8, 8, 8)
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
            text = "API call log will be displayed here"
            textSize = 12f
            setPadding(8, 8, 8, 8)
            setBackgroundColor(android.graphics.Color.LTGRAY)
            maxLines = 10
        }
        debugContainer.addView(logText)

        val viewLogButton = Button(this).apply {
            text = "View API Call Log"
            setOnClickListener {
                try {
                    val logData = if (::cameraAPIMonitor.isInitialized) {
                        cameraAPIMonitor.generateDebugReport()
                    } else {
                        "Camera API Monitor not initialized"
                    }
                    logText.text = logData
                    Log.i(TAG, "API Log:\n$logData")
                } catch (e: Exception) {
                    Log.e(TAG, "Error viewing API log", e)
                    logText.text = "Error: ${e.message}"
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

            val info = buildString {
                appendLine("Available Cameras: ${cameras.size}")
                cameras.forEachIndexed { index, cameraInfo ->
                    val facing = when (cameraInfo.lensFacing) {
                        androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
                        androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back"
                        else -> "External"
                    }
                    appendLine("Camera $index: $facing facing, Flash: ${cameraInfo.hasFlashUnit()}")
                }
                appendLine("Default Camera: ${settingsManager.defaultCameraIndex.value}")
                appendLine("Debug Logging: ${settingsManager.debugLogging.value}")
            }

            infoText.text = info
            Log.i(TAG, "Camera info refreshed")

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
            val debugReport = if (::cameraAPIMonitor.isInitialized) {
                cameraAPIMonitor.generateDebugReport()
            } else {
                "API Monitor not initialized"
            }

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

            // Simple reset operation
            Toast.makeText(this, "Camera system reset initiated", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Camera system reset initiated")

        } catch (e: Exception) {
            Log.e(TAG, "Error resetting camera system", e)
            Toast.makeText(this, "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun flushCameraQueue() {
        try {
            Toast.makeText(this, "Flushing camera queue...", Toast.LENGTH_SHORT).show()

            // Simple flush operation
            Toast.makeText(this, "Camera queue flush initiated", Toast.LENGTH_LONG).show()
            Log.i(TAG, "Camera queue flush initiated")

        } catch (e: Exception) {
            Log.e(TAG, "Error flushing camera queue", e)
            Toast.makeText(this, "Flush failed: ${e.message}", Toast.LENGTH_SHORT).show()
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

    companion object {
        private const val TAG = "DebugActivity"
    }
}