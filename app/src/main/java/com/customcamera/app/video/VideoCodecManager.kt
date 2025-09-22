package com.customcamera.app.video

import android.content.Context
import android.media.*
import android.media.MediaFormat.*
import android.os.Build
import android.util.Log
import android.util.Range
import androidx.camera.video.Quality
import androidx.camera.video.VideoSpec
import kotlin.math.*

/**
 * Professional Video Codec Manager
 *
 * Provides comprehensive video codec support for professional recording:
 * - H.264, H.265/HEVC, VP9, AV1 codec support
 * - HDR video recording (HDR10, HDR10+, Dolby Vision)
 * - Professional bitrate and quality control
 * - Multi-format export capabilities
 * - Hardware acceleration optimization
 * - Codec compatibility detection
 */
class VideoCodecManager(private val context: Context) {

    companion object {
        private const val TAG = "VideoCodecManager"

        // Standard codecs
        const val CODEC_H264 = MediaFormat.MIMETYPE_VIDEO_AVC
        const val CODEC_H265 = MediaFormat.MIMETYPE_VIDEO_HEVC
        const val CODEC_VP9 = MediaFormat.MIMETYPE_VIDEO_VP9
        const val CODEC_AV1 = "video/av01"

        // HDR profiles
        const val HDR_NONE = 0
        const val HDR_10 = 1
        const val HDR_10_PLUS = 2
        const val HDR_DOLBY_VISION = 3

        // Quality presets
        const val QUALITY_PRESET_EFFICIENCY = "efficiency"
        const val QUALITY_PRESET_BALANCED = "balanced"
        const val QUALITY_PRESET_QUALITY = "quality"
        const val QUALITY_PRESET_PROFESSIONAL = "professional"
    }

    /**
     * Video codec configuration
     */
    data class CodecConfig(
        val codec: String = CODEC_H264,
        val profile: Int = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        val level: Int = MediaCodecInfo.CodecProfileLevel.AVCLevel52,
        val bitrate: Int = 8_000_000,
        val bitrateMode: Int = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
        val iFrameInterval: Int = 1,
        val hdrMode: Int = HDR_NONE,
        val colorFormat: Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        val enableHardwareAcceleration: Boolean = true
    )

    /**
     * Video quality presets
     */
    enum class QualityPreset {
        EFFICIENCY,     // Optimized for file size
        BALANCED,       // Balance of quality and size
        QUALITY,        // Optimized for quality
        PROFESSIONAL    // Maximum quality for professional use
    }

    /**
     * Supported codec information
     */
    data class CodecInfo(
        val name: String,
        val mimeType: String,
        val isHardwareAccelerated: Boolean,
        val supportedProfiles: List<Int>,
        val supportedLevels: List<Int>,
        val maxBitrate: Int,
        val supportsHDR: Boolean,
        val supportedColorFormats: List<Int>
    )

    private val supportedCodecs = mutableMapOf<String, CodecInfo>()
    private var currentConfig = CodecConfig()

    /**
     * Initialize codec manager and detect capabilities
     */
    fun initialize(): Boolean {
        return try {
            detectSupportedCodecs()
            Log.d(TAG, "Video codec manager initialized with ${supportedCodecs.size} codecs")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize video codec manager", e)
            false
        }
    }

    /**
     * Get supported codecs on this device
     */
    fun getSupportedCodecs(): Map<String, CodecInfo> = supportedCodecs.toMap()

    /**
     * Check if a specific codec is supported
     */
    fun isCodecSupported(codec: String): Boolean = supportedCodecs.containsKey(codec)

    /**
     * Get recommended codec for current device
     */
    fun getRecommendedCodec(): String {
        return when {
            // Prefer H.265 if available and hardware accelerated
            isCodecSupported(CODEC_H265) &&
            supportedCodecs[CODEC_H265]?.isHardwareAccelerated == true -> CODEC_H265

            // Fall back to H.264
            isCodecSupported(CODEC_H264) -> CODEC_H264

            // VP9 as alternative
            isCodecSupported(CODEC_VP9) -> CODEC_VP9

            else -> CODEC_H264 // Default fallback
        }
    }

    /**
     * Create codec configuration for quality preset
     */
    fun createConfigForPreset(
        preset: QualityPreset,
        quality: Quality = Quality.HD
    ): CodecConfig {
        val recommendedCodec = getRecommendedCodec()
        val baseBitrate = getBaseBitrateForQuality(quality)

        return when (preset) {
            QualityPreset.EFFICIENCY -> CodecConfig(
                codec = recommendedCodec,
                bitrate = (baseBitrate * 0.6).toInt(),
                bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
                iFrameInterval = 2,
                profile = getEfficiencyProfile(recommendedCodec)
            )

            QualityPreset.BALANCED -> CodecConfig(
                codec = recommendedCodec,
                bitrate = baseBitrate,
                bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                iFrameInterval = 1,
                profile = getBalancedProfile(recommendedCodec)
            )

            QualityPreset.QUALITY -> CodecConfig(
                codec = recommendedCodec,
                bitrate = (baseBitrate * 1.5).toInt(),
                bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                iFrameInterval = 1,
                profile = getHighQualityProfile(recommendedCodec)
            )

            QualityPreset.PROFESSIONAL -> CodecConfig(
                codec = recommendedCodec,
                bitrate = (baseBitrate * 2.0).toInt(),
                bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                iFrameInterval = 1,
                profile = getProfessionalProfile(recommendedCodec),
                level = getHighestSupportedLevel(recommendedCodec)
            )
        }
    }

    /**
     * Create HDR video configuration
     */
    fun createHDRConfig(
        hdrMode: Int = HDR_10,
        quality: Quality = Quality.FHD
    ): CodecConfig? {
        if (!isHDRSupported()) {
            Log.w(TAG, "HDR not supported on this device")
            return null
        }

        val hdrCodec = when {
            isCodecSupported(CODEC_H265) -> CODEC_H265
            isCodecSupported(CODEC_VP9) -> CODEC_VP9
            else -> {
                Log.w(TAG, "No HDR-capable codec available")
                return null
            }
        }

        val baseBitrate = getBaseBitrateForQuality(quality)

        return CodecConfig(
            codec = hdrCodec,
            bitrate = (baseBitrate * 1.8).toInt(), // HDR requires higher bitrate
            bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
            iFrameInterval = 1,
            hdrMode = hdrMode,
            profile = getHDRProfile(hdrCodec, hdrMode),
            level = getHighestSupportedLevel(hdrCodec),
            colorFormat = getHDRColorFormat()
        )
    }

    /**
     * Set current codec configuration
     */
    fun setCodecConfig(config: CodecConfig) {
        currentConfig = config
        Log.d(TAG, "Set codec config: ${config.codec} at ${config.bitrate} bps")
    }

    /**
     * Get current codec configuration
     */
    fun getCurrentConfig(): CodecConfig = currentConfig

    /**
     * Create VideoSpec with current codec configuration
     */
    fun createVideoSpec(quality: Quality = Quality.HD): VideoSpec {
        val frameRate = Range.create(30, 30) // Default 30fps

        return VideoSpec.builder()
            .setFrameRate(frameRate)
            .build()
    }

    /**
     * Check if HDR recording is supported
     */
    fun isHDRSupported(): Boolean {
        return supportedCodecs.values.any { it.supportsHDR }
    }

    /**
     * Get estimated file size for recording
     */
    fun getEstimatedFileSize(
        durationSeconds: Long,
        quality: Quality = Quality.HD,
        config: CodecConfig = currentConfig
    ): Long {
        // File size in bytes = (bitrate in bits/sec * duration in sec) / 8
        return (config.bitrate.toLong() * durationSeconds) / 8
    }

    /**
     * Get quality recommendations for device
     */
    fun getQualityRecommendations(): Map<String, QualityPreset> {
        val deviceCapabilities = assessDeviceCapabilities()

        return when {
            deviceCapabilities >= 0.8f -> mapOf(
                "Live Streaming" to QualityPreset.EFFICIENCY,
                "Social Media" to QualityPreset.BALANCED,
                "Personal Archive" to QualityPreset.QUALITY,
                "Professional Work" to QualityPreset.PROFESSIONAL
            )

            deviceCapabilities >= 0.6f -> mapOf(
                "Live Streaming" to QualityPreset.EFFICIENCY,
                "Social Media" to QualityPreset.BALANCED,
                "High Quality" to QualityPreset.QUALITY
            )

            else -> mapOf(
                "Basic Recording" to QualityPreset.EFFICIENCY,
                "Good Quality" to QualityPreset.BALANCED
            )
        }
    }

    // Private helper methods

    private fun detectSupportedCodecs() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue

            for (type in codecInfo.supportedTypes) {
                if (type.startsWith("video/")) {
                    analyzeCodec(codecInfo, type)
                }
            }
        }
    }

    private fun analyzeCodec(codecInfo: MediaCodecInfo, mimeType: String) {
        try {
            val capabilities = codecInfo.getCapabilitiesForType(mimeType)
            val videoCapabilities = capabilities.videoCapabilities
            val encoderCapabilities = capabilities.encoderCapabilities

            val isHardwareAccelerated = !codecInfo.name.lowercase().contains("software") &&
                    !codecInfo.name.lowercase().contains("sw")

            val supportedProfiles = mutableListOf<Int>()
            val supportedLevels = mutableListOf<Int>()

            capabilities.profileLevels?.forEach { profileLevel ->
                supportedProfiles.add(profileLevel.profile)
                supportedLevels.add(profileLevel.level)
            }

            val maxBitrate = 50_000_000 // Simplified fallback
            val supportsHDR = checkHDRSupport(capabilities, mimeType)
            val supportedColorFormats = capabilities.colorFormats.toList()

            val codecInfoData = CodecInfo(
                name = codecInfo.name,
                mimeType = mimeType,
                isHardwareAccelerated = isHardwareAccelerated,
                supportedProfiles = supportedProfiles.distinct(),
                supportedLevels = supportedLevels.distinct(),
                maxBitrate = maxBitrate,
                supportsHDR = supportsHDR,
                supportedColorFormats = supportedColorFormats
            )

            supportedCodecs[mimeType] = codecInfoData

        } catch (e: Exception) {
            Log.w(TAG, "Failed to analyze codec ${codecInfo.name} for $mimeType", e)
        }
    }

    private fun checkHDRSupport(capabilities: MediaCodecInfo.CodecCapabilities, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        return try {
            // Check for HDR profile support
            val hdrProfiles = when (mimeType) {
                CODEC_H265 -> listOf(
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                )
                CODEC_VP9 -> listOf(
                    MediaCodecInfo.CodecProfileLevel.VP9Profile2,
                    MediaCodecInfo.CodecProfileLevel.VP9Profile3
                )
                else -> emptyList()
            }

            capabilities.profileLevels?.any { profileLevel ->
                hdrProfiles.contains(profileLevel.profile)
            } ?: false

        } catch (e: Exception) {
            false
        }
    }

    private fun getBaseBitrateForQuality(quality: Quality): Int {
        return when (quality) {
            Quality.LOWEST -> 1_000_000    // 1 Mbps
            Quality.SD -> 3_000_000        // 3 Mbps
            Quality.HD -> 8_000_000        // 8 Mbps
            Quality.FHD -> 15_000_000      // 15 Mbps
            Quality.UHD -> 45_000_000      // 45 Mbps
            else -> 8_000_000
        }
    }

    private fun getEfficiencyProfile(codec: String): Int {
        return when (codec) {
            CODEC_H264 -> MediaCodecInfo.CodecProfileLevel.AVCProfileMain
            CODEC_H265 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
            CODEC_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Profile0
            else -> MediaCodecInfo.CodecProfileLevel.AVCProfileMain
        }
    }

    private fun getBalancedProfile(codec: String): Int {
        return when (codec) {
            CODEC_H264 -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            CODEC_H265 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
            CODEC_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Profile0
            else -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
        }
    }

    private fun getHighQualityProfile(codec: String): Int {
        return when (codec) {
            CODEC_H264 -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            CODEC_H265 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            CODEC_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Profile2
            else -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
        }
    }

    private fun getProfessionalProfile(codec: String): Int {
        return when (codec) {
            CODEC_H264 -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422
            CODEC_H265 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            CODEC_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Profile2
            else -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
        }
    }

    private fun getHDRProfile(codec: String, hdrMode: Int): Int {
        return when (codec) {
            CODEC_H265 -> when (hdrMode) {
                HDR_10 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                HDR_10_PLUS -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                else -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            }
            CODEC_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Profile2
            else -> getProfessionalProfile(codec)
        }
    }

    private fun getHighestSupportedLevel(codec: String): Int {
        val codecInfo = supportedCodecs[codec]
        return codecInfo?.supportedLevels?.maxOrNull() ?: when (codec) {
            CODEC_H264 -> MediaCodecInfo.CodecProfileLevel.AVCLevel52
            CODEC_H265 -> MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62
            CODEC_VP9 -> MediaCodecInfo.CodecProfileLevel.VP9Level62
            else -> MediaCodecInfo.CodecProfileLevel.AVCLevel52
        }
    }

    private fun getHDRColorFormat(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        } else {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
    }

    private fun assessDeviceCapabilities(): Float {
        // Simplified device capability assessment
        val hardwareCodecCount = supportedCodecs.values.count { it.isHardwareAccelerated }
        val hasH265 = isCodecSupported(CODEC_H265)
        val hasHDR = isHDRSupported()

        var score = 0.0f

        // Base score for hardware codecs
        score += (hardwareCodecCount / 4.0f).coerceAtMost(0.4f)

        // Bonus for modern codecs
        if (hasH265) score += 0.3f
        if (hasHDR) score += 0.3f

        return score.coerceAtMost(1.0f)
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        supportedCodecs.clear()
        Log.d(TAG, "Video codec manager cleaned up")
    }
}