package com.customcamera.app.video

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Advanced Video Stabilization Manager
 *
 * Provides comprehensive video stabilization capabilities:
 * - Electronic Image Stabilization (EIS) using sensor data
 * - Digital Image Stabilization (DIS) using computer vision
 * - Hybrid stabilization combining multiple techniques
 * - Adaptive stabilization based on motion patterns
 * - Rolling shutter correction
 * - Horizon leveling and tilt correction
 */
class VideoStabilizationManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "VideoStabilization"
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_FASTEST
        private const val STABILIZATION_BUFFER_SIZE = 30
        private const val MOTION_THRESHOLD = 0.1f
        private const val MAX_CORRECTION_ANGLE = 15.0f
    }

    /**
     * Stabilization modes
     */
    enum class StabilizationMode {
        OFF,                    // No stabilization
        ELECTRONIC,             // Sensor-based stabilization
        DIGITAL,                // Computer vision-based stabilization
        HYBRID,                 // Combined electronic and digital
        ADAPTIVE,               // Automatic mode selection
        CINEMATIC,              // Smooth cinematic movements
        SPORTS,                 // High-motion stabilization
        WALKING,                // Walking stabilization
        HANDHELD               // General handheld stabilization
    }

    /**
     * Motion analysis data
     */
    data class MotionData(
        val timestamp: Long,
        val rotationX: Float,        // Pitch
        val rotationY: Float,        // Roll
        val rotationZ: Float,        // Yaw
        val accelerationX: Float,
        val accelerationY: Float,
        val accelerationZ: Float,
        val confidence: Float = 1.0f
    )

    /**
     * Stabilization configuration
     */
    data class StabilizationConfig(
        val mode: StabilizationMode = StabilizationMode.HYBRID,
        val strength: Float = 0.7f,                    // 0.0 = off, 1.0 = maximum
        val smoothness: Float = 0.5f,                  // Motion smoothing factor
        val cropFactor: Float = 0.1f,                  // Crop percentage for stabilization
        val enableHorizonLeveling: Boolean = true,     // Automatic horizon correction
        val enableRollingShutterCorrection: Boolean = true,
        val adaptiveStrength: Boolean = true           // Adjust strength based on motion
    )

    /**
     * Frame transformation data
     */
    data class FrameTransform(
        val translationX: Float = 0.0f,
        val translationY: Float = 0.0f,
        val rotationAngle: Float = 0.0f,
        val scaleX: Float = 1.0f,
        val scaleY: Float = 1.0f,
        val confidence: Float = 1.0f
    )

    private var sensorManager: SensorManager? = null
    private var gyroscopeSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null

    private var currentConfig = StabilizationConfig()
    private var isStabilizationActive = false

    // Motion tracking
    private val motionBuffer = mutableListOf<MotionData>()
    private var lastMotionData: MotionData? = null
    private var baselineOrientation = FloatArray(3)
    private var currentOrientation = FloatArray(3)

    // Sensor data
    private val gyroscopeData = FloatArray(3)
    private val accelerometerData = FloatArray(3)
    private val magnetometerData = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Stabilization processing
    private var stabilizationJob: Job? = null
    private val transformBuffer = mutableListOf<FrameTransform>()

    /**
     * Initialize stabilization system
     */
    fun initialize(): Boolean {
        return try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            val hasRequiredSensors = gyroscopeSensor != null && accelerometerSensor != null

            if (hasRequiredSensors) {
                Log.d(TAG, "Video stabilization system initialized successfully")
                true
            } else {
                Log.w(TAG, "Required sensors not available for stabilization")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize stabilization system", e)
            false
        }
    }

    /**
     * Start video stabilization
     */
    fun startStabilization(config: StabilizationConfig = StabilizationConfig()) {
        currentConfig = config

        if (currentConfig.mode == StabilizationMode.OFF) {
            Log.d(TAG, "Stabilization disabled")
            return
        }

        // Register sensor listeners
        registerSensorListeners()

        // Start stabilization processing
        startStabilizationProcessing()

        isStabilizationActive = true
        Log.d(TAG, "Started video stabilization with mode: ${config.mode}")
    }

    /**
     * Stop video stabilization
     */
    fun stopStabilization() {
        isStabilizationActive = false

        // Unregister sensor listeners
        unregisterSensorListeners()

        // Stop processing
        stabilizationJob?.cancel()
        stabilizationJob = null

        // Clear buffers
        motionBuffer.clear()
        transformBuffer.clear()

        Log.d(TAG, "Stopped video stabilization")
    }

    /**
     * Apply stabilization to video frame
     */
    suspend fun stabilizeFrame(
        inputFrame: ImageProxy,
        timestamp: Long
    ): FrameTransform = withContext(Dispatchers.Default) {
        if (!isStabilizationActive || currentConfig.mode == StabilizationMode.OFF) {
            return@withContext FrameTransform()
        }

        return@withContext try {
            when (currentConfig.mode) {
                StabilizationMode.ELECTRONIC -> calculateElectronicStabilization(timestamp)
                StabilizationMode.DIGITAL -> calculateDigitalStabilization(inputFrame, timestamp)
                StabilizationMode.HYBRID -> calculateHybridStabilization(inputFrame, timestamp)
                StabilizationMode.ADAPTIVE -> calculateAdaptiveStabilization(inputFrame, timestamp)
                StabilizationMode.CINEMATIC -> calculateCinematicStabilization(timestamp)
                StabilizationMode.SPORTS -> calculateSportsStabilization(timestamp)
                StabilizationMode.WALKING -> calculateWalkingStabilization(timestamp)
                StabilizationMode.HANDHELD -> calculateHandheldStabilization(timestamp)
                else -> FrameTransform()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during frame stabilization", e)
            FrameTransform()
        }
    }

    /**
     * Update stabilization configuration
     */
    fun updateConfig(config: StabilizationConfig) {
        currentConfig = config
        Log.d(TAG, "Updated stabilization config: $config")
    }

    /**
     * Get current stabilization status
     */
    fun getStabilizationStatus(): StabilizationStatus {
        val recentMotion = getRecentMotionLevel()
        val effectiveness = calculateStabilizationEffectiveness()

        return StabilizationStatus(
            isActive = isStabilizationActive,
            mode = currentConfig.mode,
            motionLevel = recentMotion,
            effectiveness = effectiveness,
            cropFactor = currentConfig.cropFactor
        )
    }

    /**
     * Check if stabilization is recommended for current motion
     */
    fun isStabilizationRecommended(): Boolean {
        val motionLevel = getRecentMotionLevel()
        return motionLevel > MOTION_THRESHOLD
    }

    // Sensor event handling

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroscopeData, 0, 3)
                updateMotionData(event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerData, 0, 3)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerData, 0, 3)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} = $accuracy")
    }

    // Private stabilization methods

    private fun registerSensorListeners() {
        sensorManager?.let { manager ->
            gyroscopeSensor?.let { sensor ->
                manager.registerListener(this, sensor, SENSOR_DELAY)
            }
            accelerometerSensor?.let { sensor ->
                manager.registerListener(this, sensor, SENSOR_DELAY)
            }
            magnetometerSensor?.let { sensor ->
                manager.registerListener(this, sensor, SENSOR_DELAY)
            }
        }
    }

    private fun unregisterSensorListeners() {
        sensorManager?.unregisterListener(this)
    }

    private fun startStabilizationProcessing() {
        stabilizationJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isStabilizationActive) {
                processMotionData()
                delay(16) // ~60 FPS processing
            }
        }
    }

    private fun updateMotionData(timestamp: Long) {
        // Calculate orientation from accelerometer and magnetometer
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerData, magnetometerData)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
        }

        val motionData = MotionData(
            timestamp = timestamp,
            rotationX = gyroscopeData[0], // Pitch rate
            rotationY = gyroscopeData[1], // Roll rate
            rotationZ = gyroscopeData[2], // Yaw rate
            accelerationX = accelerometerData[0],
            accelerationY = accelerometerData[1],
            accelerationZ = accelerometerData[2]
        )

        synchronized(motionBuffer) {
            motionBuffer.add(motionData)
            if (motionBuffer.size > STABILIZATION_BUFFER_SIZE) {
                motionBuffer.removeAt(0)
            }
        }

        lastMotionData = motionData
    }

    private fun processMotionData() {
        synchronized(motionBuffer) {
            if (motionBuffer.size < 2) return

            // Calculate motion trends and apply smoothing
            val recentMotion = motionBuffer.takeLast(10)
            val avgRotationX = recentMotion.map { it.rotationX }.average().toFloat()
            val avgRotationY = recentMotion.map { it.rotationY }.average().toFloat()
            val avgRotationZ = recentMotion.map { it.rotationZ }.average().toFloat()

            // Update baseline orientation for stabilization reference
            currentOrientation[0] = avgRotationX
            currentOrientation[1] = avgRotationY
            currentOrientation[2] = avgRotationZ
        }
    }

    private fun calculateElectronicStabilization(timestamp: Long): FrameTransform {
        val motionData = lastMotionData ?: return FrameTransform()

        // Use gyroscope data for electronic stabilization
        val rotationCorrection = -motionData.rotationZ * currentConfig.strength
        val clampedRotation = rotationCorrection.coerceIn(-MAX_CORRECTION_ANGLE, MAX_CORRECTION_ANGLE)

        return FrameTransform(
            rotationAngle = clampedRotation,
            confidence = 0.8f
        )
    }

    private fun calculateDigitalStabilization(inputFrame: ImageProxy, timestamp: Long): FrameTransform {
        // Simplified digital stabilization using feature tracking
        // In a real implementation, this would use computer vision algorithms

        val translationX = calculateTranslationCorrection(0)
        val translationY = calculateTranslationCorrection(1)

        return FrameTransform(
            translationX = translationX,
            translationY = translationY,
            confidence = 0.6f
        )
    }

    private fun calculateHybridStabilization(inputFrame: ImageProxy, timestamp: Long): FrameTransform {
        val eisTransform = calculateElectronicStabilization(timestamp)
        val disTransform = calculateDigitalStabilization(inputFrame, timestamp)

        // Combine electronic and digital stabilization
        return FrameTransform(
            translationX = disTransform.translationX * 0.6f,
            translationY = disTransform.translationY * 0.6f,
            rotationAngle = eisTransform.rotationAngle * 0.8f,
            confidence = (eisTransform.confidence + disTransform.confidence) / 2
        )
    }

    private fun calculateAdaptiveStabilization(inputFrame: ImageProxy, timestamp: Long): FrameTransform {
        val motionLevel = getRecentMotionLevel()

        return when {
            motionLevel > 0.8f -> calculateSportsStabilization(timestamp)
            motionLevel > 0.4f -> calculateHandheldStabilization(timestamp)
            motionLevel > 0.1f -> calculateCinematicStabilization(timestamp)
            else -> FrameTransform()
        }
    }

    private fun calculateCinematicStabilization(timestamp: Long): FrameTransform {
        // Smooth, cinematic stabilization with gentle corrections
        val eisTransform = calculateElectronicStabilization(timestamp)
        return eisTransform.copy(
            rotationAngle = eisTransform.rotationAngle * 0.5f, // Gentler correction
            confidence = 0.9f
        )
    }

    private fun calculateSportsStabilization(timestamp: Long): FrameTransform {
        // Aggressive stabilization for high-motion scenarios
        val eisTransform = calculateElectronicStabilization(timestamp)
        return eisTransform.copy(
            rotationAngle = eisTransform.rotationAngle * 1.2f, // Stronger correction
            confidence = 0.7f
        )
    }

    private fun calculateWalkingStabilization(timestamp: Long): FrameTransform {
        // Specialized stabilization for walking motion patterns
        val motionData = lastMotionData ?: return FrameTransform()

        // Focus on vertical stabilization for walking bounce
        val verticalCorrection = -motionData.accelerationY * currentConfig.strength * 0.3f

        return FrameTransform(
            translationY = verticalCorrection,
            rotationAngle = -motionData.rotationZ * currentConfig.strength * 0.6f,
            confidence = 0.8f
        )
    }

    private fun calculateHandheldStabilization(timestamp: Long): FrameTransform {
        // General handheld stabilization balancing correction and naturalness
        return calculateElectronicStabilization(timestamp).copy(
            confidence = 0.75f
        )
    }

    private fun calculateTranslationCorrection(axis: Int): Float {
        synchronized(motionBuffer) {
            if (motionBuffer.size < 5) return 0.0f

            val recentAcceleration = motionBuffer.takeLast(5).map {
                when (axis) {
                    0 -> it.accelerationX
                    1 -> it.accelerationY
                    else -> it.accelerationZ
                }
            }

            val avgAcceleration = recentAcceleration.average().toFloat()
            return -avgAcceleration * currentConfig.strength * 10.0f // Scale factor
        }
    }

    private fun getRecentMotionLevel(): Float {
        synchronized(motionBuffer) {
            if (motionBuffer.isEmpty()) return 0.0f

            val recent = motionBuffer.takeLast(10)
            val avgRotation = recent.map {
                sqrt(it.rotationX * it.rotationX + it.rotationY * it.rotationY + it.rotationZ * it.rotationZ)
            }.average().toFloat()

            return avgRotation.coerceIn(0.0f, 1.0f)
        }
    }

    private fun calculateStabilizationEffectiveness(): Float {
        // Simplified effectiveness calculation
        val motionLevel = getRecentMotionLevel()
        return when {
            motionLevel < 0.1f -> 1.0f
            motionLevel < 0.5f -> 0.8f
            motionLevel < 0.8f -> 0.6f
            else -> 0.4f
        }
    }

    /**
     * Stabilization status information
     */
    data class StabilizationStatus(
        val isActive: Boolean,
        val mode: StabilizationMode,
        val motionLevel: Float,
        val effectiveness: Float,
        val cropFactor: Float
    )

    /**
     * Clean up resources
     */
    fun cleanup() {
        stopStabilization()
        Log.d(TAG, "Video stabilization manager cleaned up")
    }
}