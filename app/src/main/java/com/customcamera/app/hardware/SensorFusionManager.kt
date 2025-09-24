package com.customcamera.app.hardware

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureResult
import android.media.Image
import android.util.Log
import com.customcamera.app.engine.DebugLogger
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Sensor Fusion Manager
 *
 * Fuses data from multiple device sensors to enhance image quality:
 * - Gyroscope for optical image stabilization
 * - Accelerometer for shake detection and compensation
 * - Magnetometer for compass-based alignment
 * - Ambient light sensor for exposure optimization
 * - Proximity sensor for smart flash control
 * - Temperature sensors for thermal management
 *
 * Provides intelligent sensor fusion algorithms for professional photography.
 */
class SensorFusionManager(
    private val context: Context,
    private val debugLogger: DebugLogger
) : SensorEventListener {

    companion object {
        private const val TAG = "SensorFusionManager"

        // Sensor fusion parameters
        private const val GYRO_SAMPLE_RATE = SensorManager.SENSOR_DELAY_FASTEST
        private const val ACCELEROMETER_SAMPLE_RATE = SensorManager.SENSOR_DELAY_GAME
        private const val STABILIZATION_THRESHOLD = 0.1f
        private const val SHAKE_DETECTION_THRESHOLD = 2.5f
        private const val ORIENTATION_SMOOTHING_FACTOR = 0.8f

        // Fusion algorithms
        private const val COMPLEMENTARY_FILTER_ALPHA = 0.98f
        private const val KALMAN_FILTER_Q = 0.001f
        private const val KALMAN_FILTER_R = 0.1f

        // Temperature management
        private const val THERMAL_THROTTLE_TEMP = 45.0f
        private const val THERMAL_SHUTDOWN_TEMP = 55.0f
    }

    // Sensor data structures
    data class MotionData(
        val timestamp: Long,
        val gyroX: Float,
        val gyroY: Float,
        val gyroZ: Float,
        val accelX: Float,
        val accelY: Float,
        val accelZ: Float,
        val magnetX: Float = 0f,
        val magnetY: Float = 0f,
        val magnetZ: Float = 0f
    )

    data class OrientationData(
        val pitch: Float,
        val roll: Float,
        val yaw: Float,
        val confidence: Float,
        val timestamp: Long
    )

    data class StabilizationData(
        val translationX: Float,
        val translationY: Float,
        val rotationAngle: Float,
        val scaleFactorX: Float = 1.0f,
        val scaleFactorY: Float = 1.0f,
        val confidence: Float,
        val stabilizationMatrix: Matrix
    )

    data class EnvironmentalData(
        val ambientLight: Float,
        val proximity: Float,
        val temperature: Float,
        val humidity: Float,
        val pressure: Float,
        val timestamp: Long
    )

    // Fusion modes
    enum class FusionMode {
        BASIC_STABILIZATION,
        ADVANCED_STABILIZATION,
        COMPUTATIONAL_PHOTOGRAPHY,
        PROFESSIONAL_MODE,
        MOTION_TRACKING
    }

    enum class StabilizationMode {
        OPTICAL_ONLY,
        DIGITAL_ONLY,
        HYBRID_OIS_EIS,
        AI_ENHANCED
    }

    // Sensor manager and sensors
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

    // Sensor fusion state
    private var currentFusionMode = FusionMode.ADVANCED_STABILIZATION
    private var currentStabilizationMode = StabilizationMode.HYBRID_OIS_EIS
    private var isActive = false
    private val fusionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Sensor data buffers
    private val motionDataBuffer = ArrayDeque<MotionData>(100)
    private val environmentalDataBuffer = ArrayDeque<EnvironmentalData>(50)
    private var lastGyroData = FloatArray(3)
    private var lastAccelData = FloatArray(3)
    private var lastMagnetData = FloatArray(3)

    // Orientation fusion state
    private var orientationQuaternion = FloatArray(4) { 0f }
    private var fusedOrientation = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val inclinationMatrix = FloatArray(9)

    // Kalman filter state for advanced fusion
    private val kalmanState = FloatArray(6) // [x, y, z, vx, vy, vz]
    private val kalmanCovariance = Array(6) { FloatArray(6) }

    // Environmental data
    private var currentAmbientLight = 0f
    private var currentProximity = 0f
    private var currentTemperature = 0f

    /**
     * Initialize sensor fusion system
     */
    suspend fun initialize(fusionMode: FusionMode = FusionMode.ADVANCED_STABILIZATION) {
        currentFusionMode = fusionMode

        try {
            // Initialize Kalman filter
            initializeKalmanFilter()

            // Initialize orientation quaternion
            orientationQuaternion[3] = 1.0f // w component

            debugLogger.logInfo("Sensor fusion initialized", mapOf(
                "fusionMode" to fusionMode.name,
                "stabilizationMode" to currentStabilizationMode.name,
                "gyroscope" to (gyroscope != null),
                "accelerometer" to (accelerometer != null),
                "magnetometer" to (magnetometer != null)
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sensor fusion", e)
            debugLogger.logError("Sensor fusion initialization failed", e)
        }
    }

    /**
     * Start sensor fusion
     */
    fun startFusion() {
        if (isActive) return

        try {
            // Register sensor listeners
            gyroscope?.let { sensor ->
                sensorManager.registerListener(this, sensor, GYRO_SAMPLE_RATE)
            }
            accelerometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, ACCELEROMETER_SAMPLE_RATE)
            }
            magnetometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            lightSensor?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            proximitySensor?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
            temperatureSensor?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }

            isActive = true
            debugLogger.logInfo("Sensor fusion started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sensor fusion", e)
            debugLogger.logError("Sensor fusion start failed", e)
        }
    }

    /**
     * Stop sensor fusion
     */
    fun stopFusion() {
        if (!isActive) return

        try {
            sensorManager.unregisterListener(this)
            isActive = false
            debugLogger.logInfo("Sensor fusion stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop sensor fusion", e)
        }
    }

    /**
     * Process image with sensor fusion stabilization
     */
    suspend fun processImageWithStabilization(
        image: Bitmap,
        captureResult: CaptureResult? = null
    ): Bitmap? = withContext(Dispatchers.Default) {

        if (!isActive) return@withContext null

        try {
            val startTime = System.currentTimeMillis()

            // Get current stabilization data
            val stabilizationData = calculateStabilization()

            // Apply stabilization transformation
            val stabilizedImage = applyStabilizationTransform(image, stabilizationData)

            // Apply additional fusion enhancements
            val enhancedImage = when (currentFusionMode) {
                FusionMode.COMPUTATIONAL_PHOTOGRAPHY -> {
                    applyComputationalEnhancements(stabilizedImage, captureResult)
                }
                FusionMode.PROFESSIONAL_MODE -> {
                    applyProfessionalEnhancements(stabilizedImage, captureResult)
                }
                FusionMode.MOTION_TRACKING -> {
                    applyMotionTracking(stabilizedImage)
                }
                else -> stabilizedImage
            }

            val processingTime = System.currentTimeMillis() - startTime
            debugLogger.logInfo("Image processed with sensor fusion", mapOf(
                "processingTimeMs" to processingTime,
                "stabilizationConfidence" to stabilizationData.confidence,
                "fusionMode" to currentFusionMode.name
            ))

            enhancedImage

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image with stabilization", e)
            debugLogger.logError("Image stabilization failed", e)
            null
        }
    }

    /**
     * Get current orientation data
     */
    fun getCurrentOrientation(): OrientationData {
        return OrientationData(
            pitch = fusedOrientation[1],
            roll = fusedOrientation[2],
            yaw = fusedOrientation[0],
            confidence = calculateOrientationConfidence(),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get environmental recommendations for camera settings
     */
    fun getEnvironmentalRecommendations(): Map<String, Any> {
        return mapOf(
            "recommendedISO" to calculateOptimalISO(),
            "recommendedExposure" to calculateOptimalExposure(),
            "flashRecommendation" to shouldUseFlash(),
            "stabilizationNeeded" to isStabilizationNeeded(),
            "thermalStatus" to getThermalStatus(),
            "ambientLight" to currentAmbientLight,
            "proximity" to currentProximity
        )
    }

    // Sensor event handling
    override fun onSensorChanged(event: SensorEvent?) {
        if (!isActive || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroData = event.values.clone()
                processGyroscopeData(event.values, event.timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelData = event.values.clone()
                processAccelerometerData(event.values, event.timestamp)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lastMagnetData = event.values.clone()
                processMagnetometerData(event.values, event.timestamp)
            }
            Sensor.TYPE_LIGHT -> {
                currentAmbientLight = event.values[0]
                processAmbientLightData(event.values[0], event.timestamp)
            }
            Sensor.TYPE_PROXIMITY -> {
                currentProximity = event.values[0]
                processProximityData(event.values[0], event.timestamp)
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                currentTemperature = event.values[0]
                processTemperatureData(event.values[0], event.timestamp)
            }
        }

        // Update fusion calculations
        updateOrientationFusion()
        updateMotionBuffer()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        debugLogger.logDebug("Sensor accuracy changed: ${sensor?.name} = $accuracy")
    }

    // Private fusion algorithms

    private fun initializeKalmanFilter() {
        // Initialize Kalman filter for motion estimation
        for (i in kalmanState.indices) {
            kalmanState[i] = 0f
            for (j in kalmanCovariance[i].indices) {
                kalmanCovariance[i][j] = if (i == j) 1.0f else 0.0f
            }
        }
    }

    private fun processGyroscopeData(values: FloatArray, timestamp: Long) {
        // Apply complementary filter for gyroscope integration
        val dt = if (timestamp > 0) (timestamp - System.nanoTime()) / 1e9f else 0.016f

        // Update orientation using gyroscope data
        val wx = values[0] * dt
        val wy = values[1] * dt
        val wz = values[2] * dt

        // Quaternion update
        val magnitude = sqrt(wx * wx + wy * wy + wz * wz)
        if (magnitude > 0) {
            val s = sin(magnitude / 2)
            val c = cos(magnitude / 2)

            val dq = floatArrayOf(
                (wx / magnitude) * s,
                (wy / magnitude) * s,
                (wz / magnitude) * s,
                c
            )

            // Multiply quaternions
            multiplyQuaternions(orientationQuaternion, dq)
        }
    }

    private fun processAccelerometerData(values: FloatArray, timestamp: Long) {
        // Use accelerometer for gravity vector estimation
        val gravity = FloatArray(3)
        val linear = FloatArray(3)

        // Low-pass filter for gravity
        val alpha = 0.8f
        gravity[0] = alpha * gravity[0] + (1 - alpha) * values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * values[2]

        // High-pass filter for linear acceleration
        linear[0] = values[0] - gravity[0]
        linear[1] = values[1] - gravity[1]
        linear[2] = values[2] - gravity[2]

        // Update Kalman filter with acceleration data
        updateKalmanFilter(linear)
    }

    private fun processMagnetometerData(values: FloatArray, timestamp: Long) {
        // Magnetometer data for compass heading
        // Used for absolute orientation reference
    }

    private fun processAmbientLightData(lightLevel: Float, timestamp: Long) {
        val environmentalData = EnvironmentalData(
            ambientLight = lightLevel,
            proximity = currentProximity,
            temperature = currentTemperature,
            humidity = 0f, // Not available on all devices
            pressure = 0f, // Not available on all devices
            timestamp = timestamp
        )

        environmentalDataBuffer.addLast(environmentalData)
        if (environmentalDataBuffer.size > 50) {
            environmentalDataBuffer.removeFirst()
        }
    }

    private fun processProximityData(proximity: Float, timestamp: Long) {
        // Proximity data for smart flash and UI interactions
    }

    private fun processTemperatureData(temperature: Float, timestamp: Long) {
        // Temperature data for thermal management
        if (temperature > THERMAL_THROTTLE_TEMP) {
            debugLogger.logInfo("High device temperature detected: ${temperature}°C")
        }
    }

    private fun updateOrientationFusion() {
        // Complementary filter combining gyroscope and accelerometer
        if (lastAccelData.isNotEmpty() && lastGyroData.isNotEmpty() && lastMagnetData.isNotEmpty()) {
            val success = SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, lastAccelData, lastMagnetData)

            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // Apply complementary filter
                for (i in 0..2) {
                    fusedOrientation[i] = COMPLEMENTARY_FILTER_ALPHA * fusedOrientation[i] +
                                        (1 - COMPLEMENTARY_FILTER_ALPHA) * orientation[i]
                }
            }
        }
    }

    private fun updateMotionBuffer() {
        val motionData = MotionData(
            timestamp = System.currentTimeMillis(),
            gyroX = lastGyroData.getOrElse(0) { 0f },
            gyroY = lastGyroData.getOrElse(1) { 0f },
            gyroZ = lastGyroData.getOrElse(2) { 0f },
            accelX = lastAccelData.getOrElse(0) { 0f },
            accelY = lastAccelData.getOrElse(1) { 0f },
            accelZ = lastAccelData.getOrElse(2) { 0f },
            magnetX = lastMagnetData.getOrElse(0) { 0f },
            magnetY = lastMagnetData.getOrElse(1) { 0f },
            magnetZ = lastMagnetData.getOrElse(2) { 0f }
        )

        motionDataBuffer.addLast(motionData)
        if (motionDataBuffer.size > 100) {
            motionDataBuffer.removeFirst()
        }
    }

    private fun updateKalmanFilter(acceleration: FloatArray) {
        // Simplified Kalman filter for position and velocity estimation
        // Predict step
        for (i in 0..2) {
            kalmanState[i] += kalmanState[i + 3] * 0.016f // dt = 16ms
            kalmanState[i + 3] += acceleration[i] * 0.016f
        }

        // Update covariance (simplified)
        for (i in kalmanCovariance.indices) {
            for (j in kalmanCovariance[i].indices) {
                kalmanCovariance[i][j] += KALMAN_FILTER_Q
            }
        }
    }

    private fun calculateStabilization(): StabilizationData {
        // Calculate stabilization parameters from motion data
        if (motionDataBuffer.isEmpty()) {
            return StabilizationData(
                translationX = 0f,
                translationY = 0f,
                rotationAngle = 0f,
                confidence = 0f,
                stabilizationMatrix = Matrix()
            )
        }

        val recentMotion = motionDataBuffer.takeLast(10)

        // Calculate average motion for stabilization
        val avgGyroX = recentMotion.map { it.gyroX }.average().toFloat()
        val avgGyroY = recentMotion.map { it.gyroY }.average().toFloat()
        val avgGyroZ = recentMotion.map { it.gyroZ }.average().toFloat()

        // Convert motion to stabilization corrections
        val translationX = -avgGyroY * 100f // Scale factor for stabilization
        val translationY = avgGyroX * 100f
        val rotationAngle = -avgGyroZ * 180f / PI.toFloat()

        // Calculate confidence based on motion consistency
        val motionVariance = calculateMotionVariance(recentMotion)
        val confidence = (1.0f / (1.0f + motionVariance)).coerceIn(0f, 1f)

        // Create stabilization matrix
        val matrix = Matrix()
        matrix.postTranslate(translationX, translationY)
        matrix.postRotate(rotationAngle)

        return StabilizationData(
            translationX = translationX,
            translationY = translationY,
            rotationAngle = rotationAngle,
            confidence = confidence,
            stabilizationMatrix = matrix
        )
    }

    private fun calculateMotionVariance(motionData: List<MotionData>): Float {
        if (motionData.size < 2) return 0f

        val gyroXValues = motionData.map { it.gyroX }
        val gyroYValues = motionData.map { it.gyroY }
        val gyroZValues = motionData.map { it.gyroZ }

        val varianceX = calculateVariance(gyroXValues)
        val varianceY = calculateVariance(gyroYValues)
        val varianceZ = calculateVariance(gyroZValues)

        return (varianceX + varianceY + varianceZ) / 3f
    }

    private fun calculateVariance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }

    private fun applyStabilizationTransform(image: Bitmap, stabilization: StabilizationData): Bitmap {
        val matrix = stabilization.stabilizationMatrix

        // Apply only if stabilization is needed and confidence is high
        return if (stabilization.confidence > 0.5f &&
                  (abs(stabilization.translationX) > 1f || abs(stabilization.translationY) > 1f || abs(stabilization.rotationAngle) > 0.5f)) {
            Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
        } else {
            image
        }
    }

    private fun applyComputationalEnhancements(image: Bitmap, captureResult: CaptureResult?): Bitmap {
        // Apply computational photography enhancements based on sensor data
        // This would include multi-frame noise reduction, HDR, etc.
        return image
    }

    private fun applyProfessionalEnhancements(image: Bitmap, captureResult: CaptureResult?): Bitmap {
        // Apply professional-grade enhancements
        return image
    }

    private fun applyMotionTracking(image: Bitmap): Bitmap {
        // Apply motion tracking and prediction
        return image
    }

    private fun calculateOrientationConfidence(): Float {
        // Calculate confidence in orientation data
        if (motionDataBuffer.isEmpty()) return 0f

        val recentMotion = motionDataBuffer.takeLast(5)
        val motionStability = 1f - calculateMotionVariance(recentMotion)

        return motionStability.coerceIn(0f, 1f)
    }

    private fun calculateOptimalISO(): Int {
        // Calculate optimal ISO based on ambient light
        return when {
            currentAmbientLight > 1000f -> 100
            currentAmbientLight > 500f -> 200
            currentAmbientLight > 100f -> 400
            currentAmbientLight > 50f -> 800
            currentAmbientLight > 10f -> 1600
            else -> 3200
        }.coerceIn(100, 6400)
    }

    private fun calculateOptimalExposure(): Float {
        // Calculate optimal exposure compensation based on light conditions
        return when {
            currentAmbientLight > 1000f -> -0.5f
            currentAmbientLight < 10f -> 1.0f
            else -> 0f
        }.coerceIn(-3f, 3f)
    }

    private fun shouldUseFlash(): Boolean {
        return currentAmbientLight < 50f && currentProximity > 1f
    }

    private fun isStabilizationNeeded(): Boolean {
        if (motionDataBuffer.isEmpty()) return false

        val recentMotion = motionDataBuffer.takeLast(5)
        val avgMotion = recentMotion.map { abs(it.gyroX) + abs(it.gyroY) + abs(it.gyroZ) }.average()

        return avgMotion > STABILIZATION_THRESHOLD
    }

    private fun getThermalStatus(): String {
        return when {
            currentTemperature > THERMAL_SHUTDOWN_TEMP -> "Critical"
            currentTemperature > THERMAL_THROTTLE_TEMP -> "Warning"
            else -> "Normal"
        }
    }

    private fun multiplyQuaternions(q1: FloatArray, q2: FloatArray) {
        val result = FloatArray(4)
        result[0] = q1[3] * q2[0] + q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1]
        result[1] = q1[3] * q2[1] - q1[0] * q2[2] + q1[1] * q2[3] + q1[2] * q2[0]
        result[2] = q1[3] * q2[2] + q1[0] * q2[1] - q1[1] * q2[0] + q1[2] * q2[3]
        result[3] = q1[3] * q2[3] - q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2]

        System.arraycopy(result, 0, q1, 0, 4)
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopFusion()
        fusionScope.cancel()
        debugLogger.logInfo("SensorFusionManager cleanup complete")
    }
}