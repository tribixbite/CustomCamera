package com.customcamera.app.exceptions

/**
 * Custom exception classes for camera errors
 * providing more granular error recovery strategies.
 */

/**
 * Base exception for all camera-related errors
 */
open class CameraException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Camera initialization failed
 */
class CameraInitializationException(
    message: String,
    cause: Throwable? = null
) : CameraException("Camera initialization failed: $message", cause)

/**
 * Camera binding failed
 */
class CameraBindingException(
    val cameraId: String,
    message: String,
    cause: Throwable? = null
) : CameraException("Camera binding failed for $cameraId: $message", cause)

/**
 * Camera not available or accessible
 */
class CameraUnavailableException(
    val cameraId: String,
    message: String,
    cause: Throwable? = null
) : CameraException("Camera $cameraId unavailable: $message", cause)

/**
 * Plugin-related errors
 */
class PluginException(
    val pluginName: String,
    message: String,
    cause: Throwable? = null
) : CameraException("Plugin '$pluginName' error: $message", cause)

/**
 * Settings and configuration errors
 */
class SettingsException(
    message: String,
    cause: Throwable? = null
) : CameraException("Settings error: $message", cause)

/**
 * Video recording errors
 */
class VideoRecordingException(
    message: String,
    cause: Throwable? = null
) : CameraException("Video recording error: $message", cause)

/**
 * Photo capture errors
 */
class PhotoCaptureException(
    message: String,
    cause: Throwable? = null
) : CameraException("Photo capture error: $message", cause)

/**
 * Image processing errors
 */
class ImageProcessingException(
    val processorName: String,
    message: String,
    cause: Throwable? = null
) : CameraException("Image processing error in $processorName: $message", cause)

/**
 * Permission-related errors
 */
class CameraPermissionException(
    message: String,
    cause: Throwable? = null
) : CameraException("Camera permission error: $message", cause)