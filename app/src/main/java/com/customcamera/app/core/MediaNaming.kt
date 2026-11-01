package com.customcamera.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * File naming and storage-location conventions for captured media.
 *
 * Names follow the DCIM convention most gallery apps sort well:
 * `IMG_20261101_143005_123.jpg`, `VID_20261101_143005_123.mp4`.
 * Milliseconds keep burst-speed captures unique.
 */
object MediaNaming {

    /** Folder under DCIM/ (and Movies/ on API < 29 fallbacks) that holds our captures. */
    const val ALBUM = "CustomCamera"

    /** MediaStore RELATIVE_PATH for photos and videos (API 29+). */
    const val RELATIVE_PATH = "DCIM/$ALBUM"

    enum class Kind(val prefix: String, val extension: String, val mimeType: String) {
        PHOTO("IMG", "jpg", "image/jpeg"),
        RAW("IMG", "dng", "image/x-adobe-dng"),
        VIDEO("VID", "mp4", "video/mp4"),
    }

    private const val PATTERN = "yyyyMMdd_HHmmss_SSS"

    /**
     * Display name without extension, e.g. `IMG_20261101_143005_123`.
     * The time zone is injectable so tests are deterministic.
     */
    fun baseName(kind: Kind, time: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat(PATTERN, Locale.US).apply { this.timeZone = timeZone }
        return "${kind.prefix}_${format.format(time)}"
    }

    /** Full file name including extension. */
    fun fileName(kind: Kind, time: Date, timeZone: TimeZone = TimeZone.getDefault()): String =
        "${baseName(kind, time, timeZone)}.${kind.extension}"

    /** True when a display name was produced by this app (used to filter legacy queries). */
    fun isOwnName(displayName: String): Boolean =
        Regex("""^(IMG|VID)_\d{8}_\d{6}_\d{3}\.(jpg|dng|mp4)$""").matches(displayName)
}
