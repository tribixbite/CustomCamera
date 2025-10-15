package com.customcamera.app.testing

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.customcamera.app.engine.CameraContext
import com.customcamera.app.engine.CameraEngine
import com.customcamera.app.engine.DebugLogger
import com.customcamera.app.engine.SettingsManager
import org.mockito.Mockito.mock

/**
 * Simple Mock Camera Context Factory
 *
 * Creates CameraContext instances with mock dependencies for testing.
 * Since CameraContext is a data class, we can't inherit from it.
 * Instead, we provide factory methods to create instances with mocks.
 */
object SimpleMockCameraContext {

    /**
     * Create basic CameraContext with mocked dependencies
     */
    fun createBasic(): CameraContext {
        val mockContext = mock(Context::class.java)
        val mockProvider = mock(ProcessCameraProvider::class.java)
        val mockLogger = mock(DebugLogger::class.java)
        val mockSettings = mock(SettingsManager::class.java)

        return CameraContext(
            context = mockContext,
            cameraProvider = mockProvider,
            debugLogger = mockLogger,
            settingsManager = mockSettings,
            cameraEngine = null,
            apiMonitor = null
        )
    }

    /**
     * Create CameraContext with specific settings manager
     */
    fun createWithSettings(settings: SettingsManager): CameraContext {
        val mockContext = mock(Context::class.java)
        val mockProvider = mock(ProcessCameraProvider::class.java)
        val mockLogger = mock(DebugLogger::class.java)

        return CameraContext(
            context = mockContext,
            cameraProvider = mockProvider,
            debugLogger = mockLogger,
            settingsManager = settings,
            cameraEngine = null,
            apiMonitor = null
        )
    }

    /**
     * Create CameraContext with specific camera engine
     */
    fun createWithEngine(engine: CameraEngine): CameraContext {
        val mockContext = mock(Context::class.java)
        val mockProvider = mock(ProcessCameraProvider::class.java)
        val mockLogger = mock(DebugLogger::class.java)
        val mockSettings = mock(SettingsManager::class.java)

        return CameraContext(
            context = mockContext,
            cameraProvider = mockProvider,
            debugLogger = mockLogger,
            settingsManager = mockSettings,
            cameraEngine = engine,
            apiMonitor = null
        )
    }

    /**
     * Create fully configured CameraContext
     */
    fun createFull(
        context: Context,
        provider: ProcessCameraProvider,
        logger: DebugLogger,
        settings: SettingsManager,
        engine: CameraEngine? = null
    ): CameraContext {
        return CameraContext(
            context = context,
            cameraProvider = provider,
            debugLogger = logger,
            settingsManager = settings,
            cameraEngine = engine,
            apiMonitor = null
        )
    }
}
