package com.customcamera.app.plugins

import android.content.Context
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.plugins.UIPlugin
import com.customcamera.app.video.VideoControlsOverlay
import com.customcamera.app.video.VideoQualityManager
import com.customcamera.app.video.VideoRecordingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * AdvancedVideoRecordingPlugin provides comprehensive video recording functionality
 * with manual controls, quality management, and professional features.
 */
class AdvancedVideoRecordingPlugin : UIPlugin() {

    override val name: String = "AdvancedVideoRecording"
    override val version: String = "1.0.0"
    override val priority: Int = 25 // High priority for video management

    private var cameraContext: CameraContext? = null
    private var videoControlsOverlay: VideoControlsOverlay? = null
    private var videoQualityManager: VideoQualityManager? = null
    private var videoRecordingManager: VideoRecordingManager? = null

    // Recording state management
    private val _isRecording = MutableStateFlow(false)
    private val _isPaused = MutableStateFlow(false)
    private val _recordingDuration = MutableStateFlow(0L)
    private val _currentQuality = MutableStateFlow(VideoQuality.HD)
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())

    // Public state flows
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()
    val currentQuality: StateFlow<VideoQuality> = _currentQuality.asStateFlow()
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities.asStateFlow()

    // Manual controls state
    private val _manualISOEnabled = MutableStateFlow(false)
    private val _manualShutterEnabled = MutableStateFlow(false)
    private val _manualFocusEnabled = MutableStateFlow(false)
    private val _stabilizationEnabled = MutableStateFlow(true)
    private val _audioRecordingEnabled = MutableStateFlow(true)

    val manualISOEnabled: StateFlow<Boolean> = _manualISOEnabled.asStateFlow()
    val manualShutterEnabled: StateFlow<Boolean> = _manualShutterEnabled.asStateFlow()
    val manualFocusEnabled: StateFlow<Boolean> = _manualFocusEnabled.asStateFlow()
    val stabilizationEnabled: StateFlow<Boolean> = _stabilizationEnabled.asStateFlow()
    val audioRecordingEnabled: StateFlow<Boolean> = _audioRecordingEnabled.asStateFlow()

    // Current recording session
    private var activeRecording: Recording? = null
    private var recordingStartTime: Long = 0
    private var videoCapture: VideoCapture<Recorder>? = null

    override suspend fun initialize(context: CameraContext) {
        this.cameraContext = context
        Log.i(TAG, "AdvancedVideoRecordingPlugin initialized")

        // Initialize video managers
        videoQualityManager = VideoQualityManager(context.context)
        videoRecordingManager = VideoRecordingManager(context.context)

        // Load supported video qualities
        _availableQualities.value = videoQualityManager?.getSupportedQualities() ?: emptyList()

        loadSettings(context)

        context.debugLogger.logPlugin(
            name,
            "initialized",
            mapOf(
                "availableQualities" to _availableQualities.value.size,
                "defaultQuality" to _currentQuality.value.name,
                "stabilizationEnabled" to _stabilizationEnabled.value,
                "audioEnabled" to _audioRecordingEnabled.value
            )
        )
    }

    override suspend fun onCameraReady(camera: Camera) {
        Log.i(TAG, "Camera ready for advanced video recording setup")

        // Set up video capture use case
        setupVideoCapture(camera)

        cameraContext?.debugLogger?.logPlugin(
            name,
            "camera_ready",
            mapOf("cameraId" to camera.cameraInfo.toString())
        )
    }

    override fun createUIView(context: CameraContext): View? {
        // Create video controls overlay
        videoControlsOverlay = VideoControlsOverlay(context.context).apply {
            setVideoPlugin(this@AdvancedVideoRecordingPlugin)
        }
        return videoControlsOverlay
    }

    /**
     * Start video recording with current settings
     */
    suspend fun startRecording(): Result<Unit> {
        if (_isRecording.value) {
            return Result.failure(IllegalStateException("Recording already in progress"))
        }

        return try {
            // Get VideoCapture from camera engine instead of plugin's own instance
            val videoCapture = cameraContext?.cameraEngine?.getVideoCapture() ?: return Result.failure(
                IllegalStateException("Video capture not available from camera engine")
            )

            val outputFile = createVideoFile()
            val outputOptions = FileOutputOptions.Builder(outputFile).build()

            // Configure recording with manual controls
            val pendingRecording = videoCapture.output
                .prepareRecording(cameraContext!!.context, outputOptions)
                .apply {
                    if (_audioRecordingEnabled.value) {
                        withAudioEnabled()
                    }
                }

            activeRecording = pendingRecording.start(
                ContextCompat.getMainExecutor(cameraContext!!.context)
            ) { recordEvent ->
                handleRecordingEvent(recordEvent)
            }

            _isRecording.value = true
            recordingStartTime = System.currentTimeMillis()

            Log.i(TAG, "✅ Video recording started")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "recording_started",
                mapOf(
                    "quality" to _currentQuality.value.name,
                    "audioEnabled" to _audioRecordingEnabled.value,
                    "stabilizationEnabled" to _stabilizationEnabled.value
                )
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start video recording", e)
            Result.failure(e)
        }
    }

    /**
     * Stop video recording
     */
    fun stopRecording(): Result<Unit> {
        return try {
            activeRecording?.stop()
            activeRecording = null

            _isRecording.value = false
            _isPaused.value = false
            _recordingDuration.value = 0L

            Log.i(TAG, "✅ Video recording stopped")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "recording_stopped",
                mapOf("duration" to (System.currentTimeMillis() - recordingStartTime))
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop video recording", e)
            Result.failure(e)
        }
    }

    /**
     * Pause video recording
     */
    fun pauseRecording(): Result<Unit> {
        return try {
            activeRecording?.pause()
            _isPaused.value = true

            Log.i(TAG, "Video recording paused")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause video recording", e)
            Result.failure(e)
        }
    }

    /**
     * Resume video recording
     */
    fun resumeRecording(): Result<Unit> {
        return try {
            activeRecording?.resume()
            _isPaused.value = false

            Log.i(TAG, "Video recording resumed")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume video recording", e)
            Result.failure(e)
        }
    }

    /**
     * Set video quality
     */
    fun setVideoQuality(quality: VideoQuality) {
        if (_currentQuality.value != quality) {
            _currentQuality.value = quality

            // If recording, need to restart with new quality
            if (_isRecording.value) {
                Log.w(TAG, "Cannot change quality during recording")
                return
            }

            saveSettings()
            Log.i(TAG, "Video quality set to: ${quality.name}")

            cameraContext?.debugLogger?.logPlugin(
                name,
                "quality_changed",
                mapOf("newQuality" to quality.name)
            )
        }
    }

    /**
     * Toggle manual ISO control
     */
    fun setManualISOEnabled(enabled: Boolean) {
        _manualISOEnabled.value = enabled
        saveSettings()
        Log.i(TAG, "Manual ISO ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Toggle manual shutter speed control
     */
    fun setManualShutterEnabled(enabled: Boolean) {
        _manualShutterEnabled.value = enabled
        saveSettings()
        Log.i(TAG, "Manual shutter speed ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Toggle manual focus control
     */
    fun setManualFocusEnabled(enabled: Boolean) {
        _manualFocusEnabled.value = enabled
        saveSettings()
        Log.i(TAG, "Manual focus ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Toggle video stabilization
     */
    fun setStabilizationEnabled(enabled: Boolean) {
        _stabilizationEnabled.value = enabled
        saveSettings()
        Log.i(TAG, "Video stabilization ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Toggle audio recording
     */
    fun setAudioRecordingEnabled(enabled: Boolean) {
        _audioRecordingEnabled.value = enabled
        saveSettings()
        Log.i(TAG, "Audio recording ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get current recording status
     */
    fun getRecordingStatus(): Map<String, Any> {
        return mapOf(
            "isRecording" to _isRecording.value,
            "isPaused" to _isPaused.value,
            "duration" to _recordingDuration.value,
            "quality" to _currentQuality.value.name,
            "manualISOEnabled" to _manualISOEnabled.value,
            "manualShutterEnabled" to _manualShutterEnabled.value,
            "manualFocusEnabled" to _manualFocusEnabled.value,
            "stabilizationEnabled" to _stabilizationEnabled.value,
            "audioEnabled" to _audioRecordingEnabled.value,
            "availableQualities" to _availableQualities.value.map { it.name }
        )
    }

    override fun cleanup() {
        Log.i(TAG, "Cleaning up AdvancedVideoRecordingPlugin")

        if (_isRecording.value) {
            stopRecording()
        }

        videoControlsOverlay = null
        videoQualityManager = null
        videoRecordingManager = null
        videoCapture = null
        cameraContext = null
    }

    /**
     * Set up video capture use case
     */
    private fun setupVideoCapture(camera: Camera) {
        try {
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    videoQualityManager?.getQualitySelector(_currentQuality.value)
                        ?: QualitySelector.from(Quality.HD)
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            Log.i(TAG, "Video capture configured for quality: ${_currentQuality.value.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup video capture", e)
        }
    }

    /**
     * Handle recording events
     */
    private fun handleRecordingEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.d(TAG, "Recording started event")
            }
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    Log.e(TAG, "Recording finalized with error: ${event.error}")
                } else {
                    Log.i(TAG, "Recording finalized successfully: ${event.outputResults.outputUri}")
                }
                _isRecording.value = false
                _isPaused.value = false
            }
            is VideoRecordEvent.Status -> {
                // Update recording duration
                _recordingDuration.value = event.recordingStats.recordedDurationNanos / 1_000_000
            }
            is VideoRecordEvent.Pause -> {
                Log.d(TAG, "Recording paused event")
                _isPaused.value = true
            }
            is VideoRecordEvent.Resume -> {
                Log.d(TAG, "Recording resumed event")
                _isPaused.value = false
            }
        }
    }

    /**
     * Create video output file
     */
    private fun createVideoFile(): File {
        val timestamp = System.currentTimeMillis()
        val fileName = "video_${timestamp}.mp4"

        return File(
            cameraContext!!.context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES),
            fileName
        )
    }

    /**
     * Load settings from preferences
     */
    private fun loadSettings(context: CameraContext) {
        try {
            val settings = context.settingsManager

            _currentQuality.value = VideoQuality.valueOf(
                settings.getPluginSetting(name, "quality", VideoQuality.HD.name)
            )
            _manualISOEnabled.value = settings.getPluginSetting(name, "manualISO", "false").toBoolean()
            _manualShutterEnabled.value = settings.getPluginSetting(name, "manualShutter", "false").toBoolean()
            _manualFocusEnabled.value = settings.getPluginSetting(name, "manualFocus", "false").toBoolean()
            _stabilizationEnabled.value = settings.getPluginSetting(name, "stabilization", "true").toBoolean()
            _audioRecordingEnabled.value = settings.getPluginSetting(name, "audioRecording", "true").toBoolean()

            Log.i(TAG, "Settings loaded: quality=${_currentQuality.value.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults", e)
        }
    }

    /**
     * Save current settings to preferences
     */
    private fun saveSettings() {
        val settings = cameraContext?.settingsManager ?: return

        settings.setPluginSetting(name, "quality", _currentQuality.value.name)
        settings.setPluginSetting(name, "manualISO", _manualISOEnabled.value.toString())
        settings.setPluginSetting(name, "manualShutter", _manualShutterEnabled.value.toString())
        settings.setPluginSetting(name, "manualFocus", _manualFocusEnabled.value.toString())
        settings.setPluginSetting(name, "stabilization", _stabilizationEnabled.value.toString())
        settings.setPluginSetting(name, "audioRecording", _audioRecordingEnabled.value.toString())
    }

    companion object {
        private const val TAG = "AdvancedVideoRecordingPlugin"
    }
}

/**
 * Video quality options
 */
enum class VideoQuality(val displayName: String) {
    SD("480p"),
    HD("720p"),
    FHD("1080p"),
    UHD("4K")
}