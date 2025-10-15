package com.customcamera.app

import android.app.Application
import leakcanary.LeakCanary

/**
 * LeakCanary Configuration for Debug Builds
 *
 * Automatically detects memory leaks during development.
 * Only included in debug builds.
 */
class LeakCanaryConfig {

    companion object {
        /**
         * Configure LeakCanary for the application
         */
        fun configure(application: Application) {
            LeakCanary.config = LeakCanary.config.copy(
                // Dump heap when leaks are detected
                dumpHeap = true,

                // Number of retained objects before dumping heap
                retainedVisibleThreshold = 5
            )
        }
    }
}

/**
 * Custom Application class to initialize LeakCanary
 * (Add to AndroidManifest.xml if using)
 */
class DebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LeakCanaryConfig.configure(this)
    }
}
