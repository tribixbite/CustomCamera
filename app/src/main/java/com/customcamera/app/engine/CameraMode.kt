package com.customcamera.app.engine

/**
 * Sealed class representing the current camera operating mode
 *
 * CameraEngine can operate in two modes:
 * - Single: Standard single camera operation (default)
 * - Concurrent: Dual camera operation with PiP (requires hardware support)
 */
sealed class CameraMode {
    /**
     * Single camera mode - standard operation with one camera
     */
    object Single : CameraMode()

    /**
     * Concurrent camera mode - dual camera operation for PiP
     *
     * @param mainCameraIndex Index of the main (primary) camera
     * @param pipCameraIndex Index of the PiP (secondary) camera
     */
    data class Concurrent(
        val mainCameraIndex: Int,
        val pipCameraIndex: Int
    ) : CameraMode()
}
