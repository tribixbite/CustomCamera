package com.customcamera.app.video

import android.content.Context
import android.util.Log
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import com.customcamera.app.plugins.VideoQuality

/**
 * VideoQualityManager handles video quality selection and management
 * for different recording scenarios and device capabilities.
 */
class VideoQualityManager(private val context: Context) {

    private val supportedQualities = mutableListOf<VideoQuality>()

    init {
        detectSupportedQualities()
    }

    /**
     * Get list of supported video qualities for the current device
     */
    fun getSupportedQualities(): List<VideoQuality> {
        return supportedQualities.toList()
    }

    /**
     * Get QualitySelector for a specific VideoQuality
     */
    fun getQualitySelector(videoQuality: VideoQuality): QualitySelector {
        val quality = when (videoQuality) {
            VideoQuality.SD -> Quality.SD
            VideoQuality.HD -> Quality.HD
            VideoQuality.FHD -> Quality.FHD
            VideoQuality.UHD -> Quality.UHD
        }

        return QualitySelector.from(quality)
    }

    /**
     * Get the best quality available for the device
     */
    fun getBestQuality(): VideoQuality {
        return when {
            supportedQualities.contains(VideoQuality.UHD) -> VideoQuality.UHD
            supportedQualities.contains(VideoQuality.FHD) -> VideoQuality.FHD
            supportedQualities.contains(VideoQuality.HD) -> VideoQuality.HD
            else -> VideoQuality.SD
        }
    }

    /**
     * Get recommended quality based on use case
     */
    fun getRecommendedQuality(useCase: VideoUseCase): VideoQuality {
        return when (useCase) {
            VideoUseCase.SOCIAL_MEDIA -> {
                // For social media, prefer HD for good quality/file size balance
                if (supportedQualities.contains(VideoQuality.HD)) VideoQuality.HD
                else supportedQualities.firstOrNull() ?: VideoQuality.SD
            }
            VideoUseCase.PROFESSIONAL -> {
                // For professional use, prefer highest quality
                getBestQuality()
            }
            VideoUseCase.STREAMING -> {
                // For streaming, prefer HD for bandwidth considerations
                if (supportedQualities.contains(VideoQuality.HD)) VideoQuality.HD
                else if (supportedQualities.contains(VideoQuality.SD)) VideoQuality.SD
                else supportedQualities.firstOrNull() ?: VideoQuality.SD
            }
            VideoUseCase.QUICK_SHARE -> {
                // For quick sharing, prefer lower quality for smaller file size
                if (supportedQualities.contains(VideoQuality.SD)) VideoQuality.SD
                else supportedQualities.firstOrNull() ?: VideoQuality.HD
            }
        }
    }

    /**
     * Get estimated file size per minute for a given quality
     */
    fun getEstimatedFileSizePerMinute(videoQuality: VideoQuality): Long {
        // Estimated file sizes in MB per minute (approximations)
        return when (videoQuality) {
            VideoQuality.SD -> 25L * 1024 * 1024 // ~25MB
            VideoQuality.HD -> 60L * 1024 * 1024 // ~60MB
            VideoQuality.FHD -> 150L * 1024 * 1024 // ~150MB
            VideoQuality.UHD -> 400L * 1024 * 1024 // ~400MB
        }
    }

    /**
     * Get video resolution for a quality
     */
    fun getResolution(videoQuality: VideoQuality): Pair<Int, Int> {
        return when (videoQuality) {
            VideoQuality.SD -> Pair(720, 480)
            VideoQuality.HD -> Pair(1280, 720)
            VideoQuality.FHD -> Pair(1920, 1080)
            VideoQuality.UHD -> Pair(3840, 2160)
        }
    }

    /**
     * Check if a specific quality is supported
     */
    fun isQualitySupported(videoQuality: VideoQuality): Boolean {
        return supportedQualities.contains(videoQuality)
    }

    /**
     * Get quality information for display
     */
    fun getQualityInfo(videoQuality: VideoQuality): VideoQualityInfo {
        val (width, height) = getResolution(videoQuality)
        val fileSize = getEstimatedFileSizePerMinute(videoQuality)
        val isSupported = isQualitySupported(videoQuality)

        return VideoQualityInfo(
            quality = videoQuality,
            displayName = videoQuality.displayName,
            resolution = "${width}x${height}",
            estimatedFileSizePerMinute = fileSize,
            isSupported = isSupported,
            recommendedUseCase = getRecommendedUseCase(videoQuality)
        )
    }

    private fun detectSupportedQualities() {
        // In a real implementation, this would probe the device capabilities
        // For now, we'll assume most modern devices support these qualities
        supportedQualities.clear()

        // Add qualities in order of preference
        supportedQualities.add(VideoQuality.HD)    // Most widely supported
        supportedQualities.add(VideoQuality.FHD)   // Common on newer devices
        supportedQualities.add(VideoQuality.SD)    // Fallback option
        supportedQualities.add(VideoQuality.UHD)   // Premium devices

        Log.i(TAG, "Detected supported qualities: ${supportedQualities.map { it.displayName }}")
    }

    private fun getRecommendedUseCase(videoQuality: VideoQuality): String {
        return when (videoQuality) {
            VideoQuality.SD -> "Quick sharing, low storage"
            VideoQuality.HD -> "Social media, general recording"
            VideoQuality.FHD -> "High quality recording, YouTube"
            VideoQuality.UHD -> "Professional, future-proofing"
        }
    }

    companion object {
        private const val TAG = "VideoQualityManager"
    }
}

/**
 * Video use cases for quality recommendations
 */
enum class VideoUseCase {
    SOCIAL_MEDIA,
    PROFESSIONAL,
    STREAMING,
    QUICK_SHARE
}

/**
 * Comprehensive video quality information
 */
data class VideoQualityInfo(
    val quality: VideoQuality,
    val displayName: String,
    val resolution: String,
    val estimatedFileSizePerMinute: Long,
    val isSupported: Boolean,
    val recommendedUseCase: String
)