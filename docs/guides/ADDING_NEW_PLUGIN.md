# Adding a New Plugin to CustomCamera

This guide explains how to add a new camera plugin using the Provider Pattern (single registration point).

## ✅ Quick Start: 3-Step Process

Adding a plugin now requires changes in **ONE place only**:

### Step 1: Create Your Plugin Class

Create a new file in `app/src/main/java/com/customcamera/app/plugins/`:

```kotlin
package com.customcamera.app.plugins

import com.customcamera.app.engine.plugins.*

/**
 * Your plugin description here
 */
class MyAwesomePlugin : ProcessingPlugin() {  // or UIPlugin(), ControlPlugin()

    override val name: String = "MyAwesome"
    override val displayName: String = "My Awesome Feature"
    override val description: String = "Does something awesome"
    override val iconResId: Int = R.drawable.ic_awesome
    override val category: PluginCategory = PluginCategory.CAPTURE
    override val userToggleable: Boolean = true
    override val version: String = "1.0.0"
    override val priority: Int = 10

    override suspend fun initialize(context: CameraContext) {
        // Setup code here
    }

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        // Your processing logic here
        return ProcessingResult.Success("Processed successfully")
    }

    override fun cleanup() {
        // Cleanup code here
    }

    // ⭐ IMPORTANT: Add PluginProvider companion object
    companion object : PluginProvider {
        private const val TAG = "MyAwesomePlugin"

        override val id: String = "my_awesome"
        override val displayNameRes: Int = R.string.plugin_my_awesome_display_name
        override val descriptionRes: Int = R.string.plugin_my_awesome_description
        override val iconResId: Int = R.drawable.ic_awesome
        override val category: PluginCategory = PluginCategory.CAPTURE
        override val userToggleable: Boolean = true

        override fun isSupported(context: Context): Boolean {
            // Add device capability checks here if needed
            return true
        }

        override fun create(dependencies: PluginDependencies): CameraPlugin {
            return MyAwesomePlugin()
        }
    }
}
```

### Step 2: Add String Resources

Add to `app/src/main/res/values/strings.xml`:

```xml
<!-- My Awesome Plugin -->
<string name="plugin_my_awesome_display_name">My Awesome Feature</string>
<string name="plugin_my_awesome_description">Does something awesome with the camera</string>
```

### Step 3: Register in PluginRegistry

Add your plugin to the list in `PluginRegistry.kt` (line ~45):

```kotlin
private val allProviders: List<PluginProvider> = listOf(
    // OVERLAYS (1)
    GridOverlayPlugin,

    // ... existing plugins ...

    // CAPTURE (8)  // ← Update count
    CropPlugin,
    DualCameraPiPPlugin,
    RAWCapturePlugin,
    AdvancedVideoRecordingPlugin,
    NightModePlugin,
    HDRPlugin,
    MyAwesomePlugin,  // ← Add here!
)
```

**That's it!** Your plugin will now:
- ✅ Auto-register with CameraEngine
- ✅ Appear in Settings UI
- ✅ Appear in Plugin dropdown menu
- ✅ Support enable/disable toggle
- ✅ Save settings across sessions

---

## 📋 Plugin Types

Choose the right base class for your plugin:

### ProcessingPlugin
For plugins that analyze or modify camera frames:
```kotlin
class MyPlugin : ProcessingPlugin() {
    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        // Analyze or modify the frame
        return ProcessingResult.Success()
    }
}
```

**Examples**: BarcodePlugin, HistogramPlugin, QRScannerPlugin

### UIPlugin
For plugins that render overlays on the camera preview:
```kotlin
class MyPlugin : UIPlugin() {
    override fun createOverlay(context: Context): View {
        // Return your custom overlay view
        return MyCustomOverlayView(context)
    }
}
```

**Examples**: GridOverlayPlugin, CropPlugin

### ControlPlugin
For plugins that adjust camera parameters:
```kotlin
class MyPlugin : ControlPlugin() {
    override suspend fun applyControls(camera: Camera): ControlResult {
        // Adjust camera settings
        camera.cameraControl.setLinearZoom(0.5f)
        return ControlResult.Success()
    }
}
```

**Examples**: AutoFocusPlugin, ExposureControlPlugin, ProControlsPlugin

---

## 🎨 Plugin Categories

Assign your plugin to one of these categories:

| Category | Description | Examples |
|----------|-------------|----------|
| `OVERLAYS` | Visual overlays on preview | Grid, Crop frame |
| `ANALYSIS` | Frame analysis features | Barcode, Histogram, Motion |
| `CONTROLS` | Camera control features | Focus, Exposure, Pro controls |
| `AI` | AI-powered features | Scene detection, Object recognition |
| `CAPTURE` | Capture-related features | RAW, HDR, Night mode, Video |

---

## 🛠️ Device Capability Checking

Use `isSupported()` to check device capabilities:

```kotlin
companion object : PluginProvider {
    override fun isSupported(context: Context): Boolean {
        // Check if device has required features
        val packageManager = context.packageManager

        // Example: Check for camera2 API support
        val hasCamera2 = packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_HARDWARE_LEVEL_FULL
        )

        // Example: Check for RAW capture support
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val hasRaw = cameraManager?.cameraIdList?.any { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            )
            capabilities?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
            ) == true
        } ?: false

        return hasCamera2 && hasRaw
    }
}
```

**What happens if not supported?**
- Plugin won't appear in UI (Settings/Dropdown)
- Won't be instantiated or registered
- No runtime errors

---

## 📦 Dependencies

If your plugin needs dependencies (ML Kit, TensorFlow, etc):

```kotlin
override fun create(dependencies: PluginDependencies): CameraPlugin {
    return MyAwesomePlugin().apply {
        // Access context and logger from dependencies
        val context = dependencies.context
        val logger = dependencies.debugLogger

        // Initialize any libraries
        setupMLKit(context)
    }
}
```

**Available in PluginDependencies**:
- `context: Context` - Application context
- `debugLogger: DebugLogger` - Logging utility

**Extension functions**:
- `dependencies.getString(resId)` - Get string resource
- `dependencies.logPlugin(name, operation, details)` - Log plugin activity

---

## 🎯 Best Practices

### 1. **Always use companion object**
```kotlin
companion object : PluginProvider {
    // Metadata + factory method
}
```

### 2. **Use resource IDs, not hardcoded strings**
```kotlin
override val displayNameRes: Int = R.string.plugin_my_awesome_display_name  // ✅ Good
override val displayName: String = "My Awesome"  // ❌ Bad (not localizable)
```

### 3. **Close ImageProxy after processing**
```kotlin
override suspend fun processFrame(image: ImageProxy): ProcessingResult {
    try {
        // Process image
    } finally {
        image.close()  // ⭐ Always close!
    }
}
```

### 4. **Cleanup resources**
```kotlin
override fun cleanup() {
    // Release resources, cancel coroutines, unregister listeners
    myAnalyzer?.close()
    myView = null
}
```

### 5. **Use proper error handling**
```kotlin
override suspend fun processFrame(image: ImageProxy): ProcessingResult {
    return try {
        // Processing logic
        ProcessingResult.Success("Frame processed")
    } catch (e: Exception) {
        Log.e(TAG, "Processing failed", e)
        ProcessingResult.Failure("Processing error: ${e.message}", e)
    }
}
```

---

## 🧪 Testing Your Plugin

### 1. Build and Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Check Logs
```bash
adb logcat -d | grep "🔌 Initializing plugins\|✅ Registered plugin"
```

You should see:
```
I/CameraEngine: 🔌 Initializing plugins from registry...
I/CameraEngine: Found 23 supported plugin providers
I/CameraEngine: ✅ Registered plugin: my_awesome (MyAwesome)
I/CameraEngine: ✅ Successfully registered 23 plugins from registry
```

### 3. Verify in Settings
- Open Settings
- Check your plugin appears under correct category
- Toggle enable/disable
- Restart app - verify setting persists

### 4. Test Functionality
- Enable your plugin
- Use camera interface
- Check logs for plugin activity

---

## 🐛 Troubleshooting

### Plugin doesn't appear in UI
- ✅ Check `isSupported()` returns true on your device
- ✅ Verify added to PluginRegistry.allProviders list
- ✅ Check `userToggleable = true`
- ✅ Build and reinstall app

### Plugin not working
- ✅ Check logs for errors: `adb logcat | grep MyAwesomePlugin`
- ✅ Verify `initialize()` completes without exceptions
- ✅ Check plugin is enabled in Settings
- ✅ Ensure proper lifecycle callbacks implemented

### Build errors
- ✅ Verify companion object implements PluginProvider
- ✅ Check string resources exist in strings.xml
- ✅ Ensure icon resource exists (or use existing)
- ✅ Verify imports are correct

---

## 📚 Reference

**Full Examples**:
- Simple: `GridOverlayPlugin.kt` (UIPlugin)
- Medium: `BarcodePlugin.kt` (ProcessingPlugin with ML Kit)
- Complex: `AutoFocusPlugin.kt` (ControlPlugin with state management)

**Key Files**:
- `PluginProvider.kt` - Provider interface definition
- `PluginDependencies.kt` - Dependency container
- `PluginRegistry.kt` - Plugin registration list
- `CameraPlugin.kt` - Base plugin classes

**Documentation**:
- `PROVIDER_PATTERN_REFACTORING.md` - Architecture overview
- `CLAUDE.md` - Project configuration

---

## 🎉 Summary

**Before Provider Pattern** (dual registration):
1. Create plugin class ✍️
2. Add to PluginRegistry.kt ✍️
3. Add to CameraActivityEngine.kt ✍️
4. Instantiate manually ✍️
5. Add string resources ✍️
6. Register with engine ✍️

**After Provider Pattern** (single registration):
1. Create plugin class with companion object ✍️
2. Add string resources ✍️
3. Add ONE line to PluginRegistry.allProviders ✍️
4. **Done!** ✅

Everything else auto-generates!
