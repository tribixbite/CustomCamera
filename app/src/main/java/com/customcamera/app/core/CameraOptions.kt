package com.customcamera.app.core

/**
 * User-selectable camera options shared by the viewfinder, settings and tests.
 *
 * Every enum carries a stable [key] used for persistence so renaming a constant
 * never invalidates stored preferences.
 */

/**
 * Extension identifiers. Values mirror androidx.camera.extensions.ExtensionMode
 * so this file stays pure Kotlin (a compile-time check lives in CameraController).
 */
object ExtensionIds {
    const val BOKEH = 1
    const val HDR = 2
    const val NIGHT = 3
}

/** Top-level capture modes shown in the mode selector. */
enum class CaptureMode(val key: String, val extensionMode: Int?) {
    /** Night extension (multi-frame low light). Only listed when the device supports it. */
    NIGHT("night", ExtensionIds.NIGHT),

    /** Bokeh/portrait extension. Only listed when the device supports it. */
    PORTRAIT("portrait", ExtensionIds.BOKEH),
    PHOTO("photo", null),
    VIDEO("video", null),

    /**
     * Picture-in-picture video from the front and back cameras at once (CameraX
     * concurrent cameras). Only listed when the device reports a concurrent pair.
     */
    DUAL("dual", null),

    /** Manual ISO / shutter / white balance / focus. */
    PRO("pro", null),

    /** QR / barcode scanner. */
    SCAN("scan", null);

    /** Modes whose shutter records video. */
    val isVideo: Boolean get() = this == VIDEO || this == DUAL

    val isPhotoLike: Boolean get() = !isVideo && this != SCAN
    val usesExtension: Boolean get() = extensionMode != null

    /** Modes whose flash button controls a continuous torch instead of a capture flash. */
    val usesTorch: Boolean get() = this == VIDEO || this == SCAN

    companion object {
        fun fromKey(key: String?): CaptureMode = entries.firstOrNull { it.key == key } ?: PHOTO

        /**
         * Modes to display, in order. Extension modes are dropped when the bound
         * camera does not offer them, and DUAL when the device has no concurrent
         * front/back pair, so users never see a mode that cannot work.
         */
        fun available(supportedExtensions: Set<Int>, dualSupported: Boolean = false): List<CaptureMode> =
            entries.filter {
                when {
                    it == DUAL -> dualSupported
                    it.extensionMode != null -> it.extensionMode in supportedExtensions
                    else -> true
                }
            }
    }
}

/** Photo flash. TORCH is only used in video mode; SCREEN only for front cameras. */
enum class FlashOption(val key: String) {
    OFF("off"), AUTO("auto"), ON("on"), TORCH("torch");

    companion object {
        fun fromKey(key: String?): FlashOption = entries.firstOrNull { it.key == key } ?: OFF

        /**
         * Next flash state when the flash button is tapped.
         * Photo modes cycle OFF → AUTO → ON; video only toggles the torch.
         */
        fun next(current: FlashOption, videoMode: Boolean): FlashOption = if (videoMode) {
            if (current == TORCH) OFF else TORCH
        } else {
            when (current) {
                OFF -> AUTO
                AUTO -> ON
                ON, TORCH -> OFF
            }
        }
    }
}

/** Self-timer delay. */
enum class TimerOption(val key: String, val seconds: Int) {
    OFF("off", 0), THREE("3", 3), FIVE("5", 5), TEN("10", 10);

    fun next(): TimerOption = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): TimerOption = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** Photo aspect ratio; implemented with a CameraX ViewPort so preview and output match. */
enum class AspectOption(val key: String, val label: String, val width: Int, val height: Int) {
    RATIO_4_3("4_3", "3:4", 3, 4),
    RATIO_16_9("16_9", "9:16", 9, 16),
    RATIO_1_1("1_1", "1:1", 1, 1);

    /** Portrait height divided by width, used to size the preview container. */
    val heightOverWidth: Float get() = height.toFloat() / width

    fun next(): AspectOption = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): AspectOption = entries.firstOrNull { it.key == key } ?: RATIO_4_3
    }
}

/** Composition guides drawn over the preview. */
enum class GridOption(val key: String) {
    OFF("off"), THIRDS("thirds"), GOLDEN("golden"), SQUARE("square");

    companion object {
        fun fromKey(key: String?): GridOption = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** What the hardware volume keys do while the viewfinder is open. */
enum class VolumeKeyAction(val key: String) {
    SHUTTER("shutter"), ZOOM("zoom"), VOLUME("volume");

    companion object {
        fun fromKey(key: String?): VolumeKeyAction = entries.firstOrNull { it.key == key } ?: SHUTTER
    }
}

/** Trade-off between shutter lag and image quality for still capture. */
enum class PhotoQualityOption(val key: String) {
    QUALITY("quality"), LATENCY("latency"), ZERO_SHUTTER_LAG("zsl");

    companion object {
        fun fromKey(key: String?): PhotoQualityOption = entries.firstOrNull { it.key == key } ?: QUALITY
    }
}

/** Video resolution preference; the controller falls back to the nearest supported quality. */
enum class VideoQualityOption(val key: String, val label: String) {
    UHD("uhd", "4K"), FHD("fhd", "1080p"), HD("hd", "720p"), SD("sd", "480p");

    companion object {
        fun fromKey(key: String?): VideoQualityOption = entries.firstOrNull { it.key == key } ?: FHD
    }
}
