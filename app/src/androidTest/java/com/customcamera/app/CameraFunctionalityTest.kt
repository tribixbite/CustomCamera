package com.customcamera.app

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import com.customcamera.app.engine.CameraEngine
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Camera Functionality Instrumented Tests
 *
 * Tests that require actual device/emulator with camera hardware.
 * Validates CameraX integration and camera provider setup.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class CameraFunctionalityTest {

    private lateinit var context: Context

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testCameraProviderAvailable() {
        // Verify CameraX provider can be obtained
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        assertNotNull("Camera provider future should not be null", cameraProviderFuture)

        val cameraProvider = cameraProviderFuture.get(10, TimeUnit.SECONDS)
        assertNotNull("Camera provider should not be null", cameraProvider)
    }

    @Test
    fun testAtLeastOneCameraAvailable() {
        // Verify device has at least one camera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get(10, TimeUnit.SECONDS)

        val cameras = cameraProvider.availableCameraInfos
        assertTrue("Device should have at least one camera", cameras.isNotEmpty())
    }

    @Test
    fun testCameraPermissionGranted() {
        // Verify camera permission is granted
        val permission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        )
        assertEquals(
            "Camera permission should be granted",
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            permission
        )
    }

    @Test
    fun testCameraProviderUnbindAll() {
        // Verify we can unbind all use cases
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get(10, TimeUnit.SECONDS)

        // Unbind should not crash
        cameraProvider.unbindAll()

        // Should still be functional
        assertNotNull(cameraProvider)
    }

    @Test
    fun testMultipleCameraProvidersShareInstance() {
        // Verify getInstance returns same provider
        val future1 = ProcessCameraProvider.getInstance(context)
        val future2 = ProcessCameraProvider.getInstance(context)

        val provider1 = future1.get(10, TimeUnit.SECONDS)
        val provider2 = future2.get(10, TimeUnit.SECONDS)

        // Should be the same instance
        assertSame("Camera providers should be same instance", provider1, provider2)
    }

    @Test
    fun testCameraInfoProperties() {
        // Verify camera info has required properties
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get(10, TimeUnit.SECONDS)

        val cameras = cameraProvider.availableCameraInfos
        assertTrue("Should have cameras", cameras.isNotEmpty())

        val firstCamera = cameras.first()
        assertNotNull("Camera info should not be null", firstCamera)
        assertNotNull("Camera selector should not be null", firstCamera.cameraSelector)
    }
}
