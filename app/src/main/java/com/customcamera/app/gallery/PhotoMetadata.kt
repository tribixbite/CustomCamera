package com.customcamera.app.gallery

import android.graphics.RectF
import android.location.Location
import android.util.Size
import java.util.*

/**
 * PhotoMetadata contains EXIF and custom metadata for captured photos
 */
data class PhotoMetadata(
    val cameraId: String,
    val timestamp: Date,
    val location: Location? = null,
    val exposureSettings: ExposureSettings,
    val imageSize: Size,
    val cropArea: RectF? = null,
    val customData: Map<String, Any> = emptyMap()
)

/**
 * Camera exposure settings at time of capture
 */
data class ExposureSettings(
    val iso: Int,
    val exposureTime: String,
    val exposureCompensation: Float,
    val aperture: Float,
    val focalLength: Float,
    val whiteBalance: String,
    val flashMode: String,
    val focusMode: String
)