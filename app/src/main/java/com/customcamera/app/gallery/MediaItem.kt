package com.customcamera.app.gallery

import java.io.File

/**
 * Represents a media item (photo or video) in the gallery
 */
data class MediaItem(
    val file: File,
    val isVideo: Boolean,
    val timestamp: Long,
    val size: Long,
    val duration: Long? = null // For videos
) {
    val name: String get() = file.name
    val path: String get() = file.absolutePath
    val sizeFormatted: String get() = formatFileSize(size)

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
}