package com.customcamera.app.pip

import android.content.Context
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector

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
        try {
            // Get all available cameras
            val availableCameras = provider.availableCameraInfos

            if (availableCameras.size < 2) {
                Log.w(TAG, "Device has less than 2 cameras (found: ${availableCameras.size})")
                return ConcurrentCameraInfo(
                    isSupported = false,
                    availableCombinations = emptyList(),
                    recommendedCombination = null,
                    errorMessage = "Device has only ${availableCameras.size} camera(s). Dual camera requires at least 2 cameras."
                )
            }

            Log.i(TAG, "Device has ${availableCameras.size} cameras available")

            // Query concurrent camera support from CameraX
            val concurrentCameraInfos = provider.availableConcurrentCameraInfos

            Log.i(TAG, "CameraX API reports ${concurrentCameraInfos.size} concurrent combinations")

            if (concurrentCameraInfos.isEmpty()) {
                Log.w(TAG, "⚠️ CameraX API reports no concurrent support, but device has ${availableCameras.size} cameras")
                Log.w(TAG, "Will attempt concurrent mode anyway - some devices have buggy capability reporting")

                // Don't give up - try to create combinations manually
                // Many devices support concurrent cameras even if the API doesn't report it correctly
                val manualCombinations = mutableListOf<Pair<Int, Int>>()

                // Try common combinations
                if (availableCameras.size >= 2) {
                    manualCombinations.add(Pair(0, 1)) // First two cameras
                    if (availableCameras.size >= 3) {
                        manualCombinations.add(Pair(0, 2)) // First and third
                    }
                }

                Log.i(TAG, "Created ${manualCombinations.size} manual camera combinations to try")

                val recommended = if (manualCombinations.isNotEmpty()) manualCombinations[0] else null

                return ConcurrentCameraInfo(
                    isSupported = true, // Try anyway
                    availableCombinations = manualCombinations,
                    recommendedCombination = recommended,
                    errorMessage = null
                )
            }

            Log.i(TAG, "Found ${concurrentCameraInfos.size} concurrent camera combinations")

            // Build list of valid combinations
            val combinations = mutableListOf<Pair<Int, Int>>()

            concurrentCameraInfos.forEachIndexed { index, concurrentInfo ->
                val cameraInfoList = concurrentInfo
                if (cameraInfoList.size >= 2) {
                    // Map CameraInfo back to camera indices
                    val firstIndex = availableCameras.indexOf(cameraInfoList[0])
                    val secondIndex = availableCameras.indexOf(cameraInfoList[1])

                    if (firstIndex >= 0 && secondIndex >= 0) {
                        combinations.add(Pair(firstIndex, secondIndex))
                        Log.d(TAG, "Valid combination #$index: camera $firstIndex + camera $secondIndex")
                    }
                }
            }

            if (combinations.isEmpty()) {
                Log.w(TAG, "No valid camera combinations could be mapped")
                return ConcurrentCameraInfo(
                    isSupported = false,
                    availableCombinations = emptyList(),
                    recommendedCombination = null,
                    errorMessage = "Could not map concurrent camera combinations to physical cameras."
                )
            }

            // Recommend best combination (prefer back + front)
            val recommended = findRecommendedCombination(provider, combinations)

            Log.i(TAG, "✅ Concurrent cameras supported with ${combinations.size} valid combinations")
            if (recommended != null) {
                Log.i(TAG, "Recommended combination: main=${recommended.first}, pip=${recommended.second}")
            }

            return ConcurrentCameraInfo(
                isSupported = true,
                availableCombinations = combinations,
                recommendedCombination = recommended,
                errorMessage = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error checking concurrent camera support", e)
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
