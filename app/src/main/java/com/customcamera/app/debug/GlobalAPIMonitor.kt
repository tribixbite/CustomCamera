package com.customcamera.app.debug

import android.util.Log

/**
 * Singleton holder for the global CameraAPIMonitor instance.
 * This allows DebugActivity to access the monitor from any camera session.
 */
object GlobalAPIMonitor {
    private var instance: CameraAPIMonitor? = null

    /**
     * Set the active API monitor instance
     */
    fun setInstance(monitor: CameraAPIMonitor) {
        instance = monitor
        Log.i(TAG, "Global API monitor instance set")
    }

    /**
     * Get the active API monitor instance
     */
    fun getInstance(): CameraAPIMonitor? {
        return instance
    }

    /**
     * Clear the active instance
     */
    fun clearInstance() {
        instance = null
        Log.i(TAG, "Global API monitor instance cleared")
    }

    private const val TAG = "GlobalAPIMonitor"
}
