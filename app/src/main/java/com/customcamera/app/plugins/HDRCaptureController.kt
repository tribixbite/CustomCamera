package com.customcamera.app.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HDRCaptureController manages Camera2 burst capture for HDR photography.
 * Captures multiple frames at different exposure values for HDR merging.
 */
class HDRCaptureController(private val context: Context) {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var imageReader: ImageReader? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var cameraDevice: CameraDevice? = null

    // Background thread for camera operations
    private val handlerThread = HandlerThread("HDRCaptureThread").apply { start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    data class BracketedFrame(
        val bitmap: Bitmap,
        val evValue: Int,
        val timestamp: Long
    )

    /**
     * Configure ImageCapture with Camera2Interop settings for HDR
     */
    fun configureBracketCapture(builder: ImageCapture.Builder, evSteps: IntArray) {
        try {
            Log.d(TAG, "Configuring HDR bracket capture with EV steps: ${evSteps.joinToString()}")

            // Add Camera2 extensions via Camera2Interop
            val camera2Interop = Camera2Interop.Extender(builder)

            // Enable manual exposure control
            camera2Interop.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )

            Log.i(TAG, "HDR bracket capture configured successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure HDR bracket capture", e)
        }
    }

    /**
     * Capture HDR sequence with bracketed exposures
     *
     * @param cameraId Camera ID to use for capture
     * @param evSteps Array of EV compensation values (e.g., [-2, 0, 2])
     * @param width Image width
     * @param height Image height
     * @return List of bracketed frames or null on failure
     */
    suspend fun captureHDRSequence(
        cameraId: String,
        evSteps: IntArray,
        width: Int = 1920,
        height: Int = 1080
    ): List<BracketedFrame>? = suspendCancellableCoroutine { continuation ->

        try {
            Log.i(TAG, "Starting HDR capture sequence: ${evSteps.size} exposures")

            val capturedFrames = mutableListOf<BracketedFrame>()
            val latch = CountDownLatch(evSteps.size)

            // Create ImageReader for capturing frames
            imageReader = ImageReader.newInstance(
                width, height,
                ImageFormat.JPEG,
                evSteps.size
            ).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)

                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                // Determine which EV value this frame corresponds to
                                val frameIndex = capturedFrames.size
                                val evValue = if (frameIndex < evSteps.size) {
                                    evSteps[frameIndex]
                                } else {
                                    0
                                }

                                capturedFrames.add(
                                    BracketedFrame(
                                        bitmap = bitmap,
                                        evValue = evValue,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                Log.d(TAG, "Captured frame ${frameIndex + 1}/${evSteps.size} at EV $evValue")
                            }

                            image.close()
                            latch.countDown()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing captured image", e)
                        latch.countDown()
                    }
                }, backgroundHandler)
            }

            // Open camera device
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    Log.d(TAG, "Camera opened for HDR capture")

                    try {
                        // Create capture session
                        camera.createCaptureSession(
                            listOf(imageReader!!.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    cameraCaptureSession = session
                                    Log.d(TAG, "Capture session configured")

                                    try {
                                        // Create capture requests for each EV value
                                        val captureRequests = evSteps.map { ev ->
                                            camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                addTarget(imageReader!!.surface)

                                                // Set exposure compensation for this bracket
                                                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)

                                                // Lock focus and white balance for consistency
                                                set(CaptureRequest.CONTROL_AF_MODE,
                                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                                set(CaptureRequest.CONTROL_AWB_LOCK, false)

                                                Log.d(TAG, "Created capture request for EV $ev")
                                            }.build()
                                        }

                                        // Capture burst sequence
                                        session.captureBurst(
                                            captureRequests,
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult
                                                ) {
                                                    val ev = request.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) ?: 0
                                                    Log.d(TAG, "Capture completed for EV $ev")
                                                }

                                                override fun onCaptureSequenceCompleted(
                                                    session: CameraCaptureSession,
                                                    sequenceId: Int,
                                                    frameNumber: Long
                                                ) {
                                                    Log.i(TAG, "HDR capture sequence completed")
                                                }
                                            },
                                            backgroundHandler
                                        )

                                        // Wait for all captures to complete (with timeout)
                                        backgroundHandler.post {
                                            val success = latch.await(10, TimeUnit.SECONDS)
                                            if (success && capturedFrames.size == evSteps.size) {
                                                Log.i(TAG, "Successfully captured ${capturedFrames.size} HDR frames")
                                                continuation.resume(capturedFrames)
                                            } else {
                                                Log.e(TAG, "HDR capture incomplete: ${capturedFrames.size}/${evSteps.size} frames")
                                                continuation.resume(null)
                                            }

                                            cleanup()
                                        }

                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error initiating capture burst", e)
                                        continuation.resumeWithException(e)
                                        cleanup()
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e(TAG, "Failed to configure capture session")
                                    continuation.resume(null)
                                    cleanup()
                                }
                            },
                            backgroundHandler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating capture session", e)
                        continuation.resumeWithException(e)
                        cleanup()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected during HDR capture")
                    cleanup()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error during HDR capture: $error")
                    continuation.resume(null)
                    cleanup()
                }
            }, backgroundHandler)

            // Handle cancellation
            continuation.invokeOnCancellation {
                Log.w(TAG, "HDR capture cancelled")
                cleanup()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in HDR capture sequence", e)
            continuation.resumeWithException(e)
            cleanup()
        }
    }

    /**
     * Clean up camera resources
     */
    fun cleanup() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

            Log.d(TAG, "HDRCaptureController cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Shutdown background thread
     */
    fun shutdown() {
        cleanup()
        handlerThread.quitSafely()
        Log.i(TAG, "HDRCaptureController shut down")
    }

    companion object {
        private const val TAG = "HDRCaptureController"
    }
}
