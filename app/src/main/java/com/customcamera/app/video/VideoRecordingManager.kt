package com.customcamera.app.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import androidx.camera.video.FileOutputOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * VideoRecordingManager handles video file management, naming,
 * and storage organization for recorded videos.
 */
class VideoRecordingManager(private val context: Context) {

    private val videoDirectory: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "CustomCamera").apply {
            if (!exists()) {
                mkdirs()
                Log.i(TAG, "Created video directory: $absolutePath")
            }
        }
    }

    /**
     * Create a new video file with timestamp-based naming
     */
    fun createVideoFile(quality: com.customcamera.app.plugins.VideoQuality? = null): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val qualityPrefix = quality?.let { "_${it.name}" } ?: ""
        val fileName = "VID_${timestamp}${qualityPrefix}.mp4"

        return File(videoDirectory, fileName)
    }

    /**
     * Create FileOutputOptions for video recording
     */
    fun createOutputOptions(quality: com.customcamera.app.plugins.VideoQuality? = null): FileOutputOptions {
        val file = createVideoFile(quality)
        return FileOutputOptions.Builder(file).build()
    }

    /**
     * Get all recorded videos
     */
    fun getRecordedVideos(): List<VideoFileInfo> {
        if (!videoDirectory.exists()) {
            return emptyList()
        }

        return videoDirectory.listFiles { file ->
            file.isFile && file.extension.lowercase() == "mp4"
        }?.map { file ->
            VideoFileInfo(
                file = file,
                name = file.nameWithoutExtension,
                size = file.length(),
                lastModified = file.lastModified(),
                duration = getVideoDuration(file)
            )
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }

    /**
     * Delete a video file
     */
    fun deleteVideo(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                Log.i(TAG, "Deleted video: ${file.name}")
            } else {
                Log.w(TAG, "Failed to delete video: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting video: ${file.name}", e)
            false
        }
    }

    /**
     * Get storage info for video directory
     */
    fun getStorageInfo(): VideoStorageInfo {
        val totalSpace = videoDirectory.totalSpace
        val freeSpace = videoDirectory.freeSpace
        val usedSpace = totalSpace - freeSpace

        val videoFiles = getRecordedVideos()
        val videoCount = videoFiles.size
        val totalVideoSize = videoFiles.sumOf { it.size }

        return VideoStorageInfo(
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            videoCount = videoCount,
            totalVideoSize = totalVideoSize,
            videoDirectory = videoDirectory.absolutePath
        )
    }

    /**
     * Clean up old videos based on criteria
     */
    fun cleanupOldVideos(
        maxFiles: Int = 50,
        maxAge: Long = 30 * 24 * 60 * 60 * 1000L // 30 days
    ): CleanupResult {
        val videos = getRecordedVideos()
        val currentTime = System.currentTimeMillis()

        var deletedCount = 0
        var deletedSize = 0L

        // Delete videos older than maxAge
        videos.filter { currentTime - it.lastModified > maxAge }
            .forEach { video ->
                if (deleteVideo(video.file)) {
                    deletedCount++
                    deletedSize += video.size
                }
            }

        // Delete excess videos if we have more than maxFiles
        val remainingVideos = getRecordedVideos()
        if (remainingVideos.size > maxFiles) {
            val videosToDelete = remainingVideos.drop(maxFiles)
            videosToDelete.forEach { video ->
                if (deleteVideo(video.file)) {
                    deletedCount++
                    deletedSize += video.size
                }
            }
        }

        Log.i(TAG, "Cleanup completed: deleted $deletedCount files, freed ${deletedSize / (1024 * 1024)}MB")

        return CleanupResult(
            deletedFiles = deletedCount,
            freedSpace = deletedSize
        )
    }

    /**
     * Export video to external storage
     */
    fun exportVideo(file: File, destinationDir: File): Boolean {
        return try {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val destinationFile = File(destinationDir, file.name)
            file.copyTo(destinationFile, overwrite = true)

            Log.i(TAG, "Exported video to: ${destinationFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export video: ${file.name}", e)
            false
        }
    }

    /**
     * Get estimated recording time remaining based on available space and quality
     */
    fun getEstimatedRecordingTime(quality: com.customcamera.app.plugins.VideoQuality): Long {
        val freeSpace = videoDirectory.freeSpace
        val qualityManager = VideoQualityManager(context)
        val fileSizePerMinute = qualityManager.getEstimatedFileSizePerMinute(quality)

        return if (fileSizePerMinute > 0) {
            (freeSpace / fileSizePerMinute) // Minutes
        } else {
            0L
        }
    }

    /**
     * Get video duration in milliseconds using MediaMetadataRetriever
     */
    private fun getVideoDuration(file: File): Long {
        if (!file.exists()) {
            return 0L
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video duration from ${file.name}", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release MediaMetadataRetriever", e)
            }
        }
    }

    companion object {
        private const val TAG = "VideoRecordingManager"
    }
}

/**
 * Information about a recorded video file
 */
data class VideoFileInfo(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val duration: Long // in milliseconds
)

/**
 * Storage information for video directory
 */
data class VideoStorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val videoCount: Int,
    val totalVideoSize: Long,
    val videoDirectory: String
)

/**
 * Result of cleanup operation
 */
data class CleanupResult(
    val deletedFiles: Int,
    val freedSpace: Long
)