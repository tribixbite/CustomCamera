package com.customcamera.app.pip

import android.content.Context
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.camera2.interop.Camera2CameraInfo

/**
 * Data class representing concurrent camera capability information
 */
data class ConcurrentCameraInfo(
    val isSupported: Boolean,
    val availableCombinations: List<Pair<Int, Int>>,
    val recommendedCombination: Pair<Int, Int>?,
    val errorMessage: String?
)

/**
 * Utility class for detecting and validating concurrent camera support
 *
 * CameraX 1.3+ supports concurrent cameras on devices with hardware support.
 * This class checks device capabilities and recommends optimal camera combinations.
 */
class ConcurrentCameraCapability(private val context: Context) {

    /**
     * Check if device supports concurrent camera operation
     *
     * @param provider ProcessCameraProvider instance
     * @return ConcurrentCameraInfo with support status and available combinations
     */
    suspend fun checkSupport(provider: ProcessCameraProvider): ConcurrentCameraInfo {
        Log.d(TAG, "🔍 Starting concurrent camera support check...")
        try {
            // Get all available cameras
            val availableCameras = provider.availableCameraInfos
            Log.i(TAG, "📷 Found ${availableCameras.size} available cameras.")

            if (availableCameras.size < 2) {
                Log.w(TAG, "❌ FAIL: Device has less than 2 cameras.")
                return ConcurrentCameraInfo(
                    isSupported = false,
                    availableCombinations = emptyList(),
                    recommendedCombination = null,
                    errorMessage = "Device has only ${availableCameras.size} camera(s). Dual camera requires at least 2 cameras."
                )
            }

            // Query concurrent camera support from CameraX
            val concurrentCameraInfos = provider.availableConcurrentCameraInfos
            Log.i(TAG, "🔗 CameraX reports ${concurrentCameraInfos.size} concurrent camera combinations.")

            if (concurrentCameraInfos.isEmpty()) {
                Log.w(TAG, "❌ FAIL: provider.availableConcurrentCameraInfos returned an empty list.")
                return ConcurrentCameraInfo(
                    isSupported = false,
                    availableCombinations = emptyList(),
                    recommendedCombination = null,
                    errorMessage = "Device hardware does not support concurrent camera operation. This is a hardware limitation."
                )
            }

            // Build list of valid combinations
            val combinations = mutableListOf<Pair<Int, Int>>()

            concurrentCameraInfos.forEachIndexed { index, concurrentInfo ->
                val cameraInfoList = concurrentInfo
                Log.d(TAG, "🔄 Processing combination #$index with ${cameraInfoList.size} cameras.")

                if (cameraInfoList.size >= 2) {
                    // Map CameraInfo back to camera indices using Camera IDs (more robust than object identity)
                    val firstCamInfo = cameraInfoList[0]
                    val secondCamInfo = cameraInfoList[1]

                    // Extract camera IDs using Camera2 interop
                    val firstCameraId = Camera2CameraInfo.from(firstCamInfo).cameraId
                    val secondCameraId = Camera2CameraInfo.from(secondCamInfo).cameraId

                    // Find indices by matching camera IDs
                    val firstIndex = availableCameras.indexOfFirst {
                        Camera2CameraInfo.from(it).cameraId == firstCameraId
                    }
                    val secondIndex = availableCameras.indexOfFirst {
                        Camera2CameraInfo.from(it).cameraId == secondCameraId
                    }

                    Log.d(TAG, "  📌 Combination #$index: Mapping via camera IDs...")
                    Log.d(TAG, "    - Cam 1 ID: $firstCameraId -> Index: $firstIndex")
                    Log.d(TAG, "    - Cam 2 ID: $secondCameraId -> Index: $secondIndex")

                    if (firstIndex >= 0 && secondIndex >= 0) {
                        combinations.add(Pair(firstIndex, secondIndex))
                        Log.d(TAG, "  ✅ SUCCESS: Valid combination mapped: camera $firstIndex + camera $secondIndex")
                    } else {
                        Log.w(TAG, "  ⚠️ WARN: Failed to map cameras in combination #$index (firstIndex=$firstIndex, secondIndex=$secondIndex)")
                        Log.w(TAG, "           Camera IDs: $firstCameraId, $secondCameraId")
                    }
                }
            }

            if (combinations.isEmpty()) {
                Log.w(TAG, "❌ FAIL: No valid camera combinations could be mapped after processing ${concurrentCameraInfos.size} reported combinations.")
                return ConcurrentCameraInfo(
                    isSupported = false,
                    availableCombinations = emptyList(),
                    recommendedCombination = null,
                    errorMessage = "Could not map concurrent camera combinations to physical cameras."
                )
            }

            // Recommend best combination (prefer back + front)
            val recommended = findRecommendedCombination(provider, combinations)

            Log.i(TAG, "✅ SUCCESS: Concurrent cameras supported with ${combinations.size} valid combinations.")
            if (recommended != null) {
                Log.i(TAG, "🎯 Recommended combination: main=${recommended.first}, pip=${recommended.second}")
            }

            return ConcurrentCameraInfo(
                isSupported = true,
                availableCombinations = combinations,
                recommendedCombination = recommended,
                errorMessage = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION during concurrent camera support check", e)
            return ConcurrentCameraInfo(
                isSupported = false,
                availableCombinations = emptyList(),
                recommendedCombination = null,
                errorMessage = "Error checking support: ${e.message}"
            )
        }
    }

    /**
     * Check if a specific camera combination is supported
     *
     * @param provider ProcessCameraProvider instance
     * @param primaryIndex Index of primary camera
     * @param secondaryIndex Index of secondary camera
     * @return true if combination is supported
     */
    suspend fun isCombinationSupported(
        provider: ProcessCameraProvider,
        primaryIndex: Int,
        secondaryIndex: Int
    ): Boolean {
        val info = checkSupport(provider)
        return info.availableCombinations.contains(Pair(primaryIndex, secondaryIndex))
    }

    /**
     * Find the recommended camera combination
     * Prefers back camera + front camera if available
     */
    private fun findRecommendedCombination(
        provider: ProcessCameraProvider,
        combinations: List<Pair<Int, Int>>
    ): Pair<Int, Int>? {
        if (combinations.isEmpty()) return null

        val availableCameras = provider.availableCameraInfos

        // Try to find back + front combination
        for (combination in combinations) {
            val (firstIdx, secondIdx) = combination

            if (firstIdx >= availableCameras.size || secondIdx >= availableCameras.size) {
                continue
            }

            val firstCamera = availableCameras[firstIdx]
            val secondCamera = availableCameras[secondIdx]

            // Check if one is back facing and one is front facing
            val firstIsBack = try {
                CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                    .filter(listOf(firstCamera))
                    .isNotEmpty()
            } catch (e: Exception) {
                false
            }

            val secondIsFront = try {
                CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()
                    .filter(listOf(secondCamera))
                    .isNotEmpty()
            } catch (e: Exception) {
                false
            }

            if (firstIsBack && secondIsFront) {
                Log.d(TAG, "Found back+front combination: $firstIdx (back) + $secondIdx (front)")
                return Pair(firstIdx, secondIdx)
            }
        }

        // Fallback: return first valid combination
        Log.d(TAG, "No back+front combo found, using first available: ${combinations[0]}")
        return combinations[0]
    }

    companion object {
        private const val TAG = "ConcurrentCameraCapability"
    }
}
