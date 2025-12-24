# Advanced Flashlight Mini-App Specification

## Overview
Comprehensive flash/torch control research for implementing an advanced flashlight mini-app in CustomCamera.

**Research Date**: 2025-12-24
**Target Device**: Samsung Galaxy S24 Ultra (Android 14+)

---

## 1. Android Camera2 API Flash Control

### Core Methods (CameraManager)

```kotlin
// Get camera manager
val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

// Basic torch on/off
cameraManager.setTorchMode(cameraId, true)   // Turn on
cameraManager.setTorchMode(cameraId, false)  // Turn off

// Torch strength control (Android 13+, API 33)
cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel)
cameraManager.getTorchStrengthLevel(cameraId)
```

### Camera Characteristics Keys

```kotlin
// Get max torch strength level (Android 13+)
val characteristics = cameraManager.getCameraCharacteristics(cameraId)
val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
val defaultLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
```

### Torch State Callback

```kotlin
cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
        // Torch state changed
    }

    override fun onTorchModeUnavailable(cameraId: String) {
        // Torch unavailable (camera in use)
    }

    override fun onTorchStrengthLevelChanged(cameraId: String, newLevel: Int) {
        // Strength level changed (Android 13+)
    }
}, handler)
```

### Flash Modes (CaptureRequest)

```kotlin
// During camera capture session
CaptureRequest.FLASH_MODE_OFF      // No flash
CaptureRequest.FLASH_MODE_SINGLE   // Single flash
CaptureRequest.FLASH_MODE_TORCH    // Continuous torch
```

---

## 2. CameraX Flash Control

### Basic Torch Control

```kotlin
// Using CameraControl
val camera = cameraProvider.bindToLifecycle(...)
camera.cameraControl.enableTorch(true)   // Turn on
camera.cameraControl.enableTorch(false)  // Turn off

// Check torch state
val torchState = camera.cameraInfo.torchState.value
```

### Torch Strength (CameraX 1.3+, Android 13+)

```kotlin
// Set torch strength level
camera.cameraControl.setTorchStrength(level)

// Get max strength level
val maxLevel = camera.cameraInfo.maxTorchStrength
```

### Flash Modes (ImageCapture)

```kotlin
imageCapture.flashMode = ImageCapture.FLASH_MODE_AUTO
imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
imageCapture.flashMode = ImageCapture.FLASH_MODE_SCREEN  // Screen flash for front camera
```

---

## 3. Samsung-Specific Flash Control

### System Settings Brightness Level

```kotlin
// Samsung uses format: 1000 + level (1-5)
// Level 1001 = brightness 1 (lowest)
// Level 1005 = brightness 5 (highest)

Settings.System.putInt(contentResolver, "Flashlight_brightness_level", 1003)
val level = Settings.System.getInt(contentResolver, "Flashlight_brightness_level", 1001)

// Actual brightness = level - 1000 (1-5 range)
```

### ADB Commands

```bash
# Set brightness level (1001-1005)
adb shell "settings put system Flashlight_brightness_level 1003"

# Get current brightness level
adb shell "settings get system Flashlight_brightness_level"

# Toggle flashlight via Quick Settings tile
adb shell "cmd statusbar click-tile Flashlight"
```

### Samsung Broadcasts (Requires system permissions)

```kotlin
// Turn off flashlight (may require system permission)
sendBroadcast(Intent("com.sec.android.systemui.action.FLASHLIGHT_OFF"))
```

---

## 4. Hardware-Level sysfs Control (Root Required)

### Torch LED Nodes

```bash
# Device has multiple torch LEDs (torch_0, torch_1, torch_3)
# Path: /sys/devices/platform/soc/.../qcom,flash_led@ee00/leds/

# Read current brightness
cat /sys/class/leds/led:torch_0/brightness

# Set brightness (root required)
echo 255 > /sys/class/leds/led:torch_0/brightness
```

### Flash LED Nodes (For camera flash)

```bash
# Flash LEDs have strobe, timeout, and brightness controls
# Path: led:flash_0, led:flash_2

# Control files:
# - flash_strobe      # Trigger flash
# - flash_brightness  # Set brightness
# - flash_timeout     # Set timeout
# - max_flash_brightness
# - max_flash_timeout
```

### Samsung Camera Flash sysfs

```bash
# Samsung-specific path
/sys/class/camera/flash/rear_flash
```

---

## 5. SOS/Strobe Pattern Implementation

### Morse Code Patterns

```kotlin
object MorsePatterns {
    // Timing constants (ms)
    const val DOT_DURATION = 200L
    const val DASH_DURATION = 600L
    const val SYMBOL_GAP = 200L    // Between dots/dashes
    const val LETTER_GAP = 600L    // Between letters
    const val WORD_GAP = 1400L     // Between words

    // SOS pattern: ... --- ...
    val SOS = listOf(
        DOT_DURATION, SYMBOL_GAP,
        DOT_DURATION, SYMBOL_GAP,
        DOT_DURATION, LETTER_GAP,
        DASH_DURATION, SYMBOL_GAP,
        DASH_DURATION, SYMBOL_GAP,
        DASH_DURATION, LETTER_GAP,
        DOT_DURATION, SYMBOL_GAP,
        DOT_DURATION, SYMBOL_GAP,
        DOT_DURATION, WORD_GAP
    )
}
```

### Pattern Executor

```kotlin
class FlashPatternExecutor(private val cameraManager: CameraManager) {
    private var isRunning = false
    private var job: Job? = null

    fun playPattern(pattern: List<Long>, cameraId: String, repeat: Boolean = true) {
        job = CoroutineScope(Dispatchers.Default).launch {
            isRunning = true
            do {
                var flashOn = true
                for (duration in pattern) {
                    if (!isRunning) break
                    cameraManager.setTorchMode(cameraId, flashOn)
                    delay(duration)
                    flashOn = !flashOn
                }
            } while (repeat && isRunning)
            cameraManager.setTorchMode(cameraId, false)
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
    }
}
```

### Strobe Effects

```kotlin
data class StrobeConfig(
    val frequency: Float,    // Hz (flashes per second)
    val dutyCycle: Float     // 0.0-1.0 (on-time ratio)
)

fun calculateStrobeTiming(config: StrobeConfig): Pair<Long, Long> {
    val periodMs = (1000f / config.frequency).toLong()
    val onTime = (periodMs * config.dutyCycle).toLong()
    val offTime = periodMs - onTime
    return onTime to offTime
}

// Common strobe frequencies
val STROBE_SLOW = StrobeConfig(2f, 0.5f)      // 2 Hz
val STROBE_MEDIUM = StrobeConfig(5f, 0.5f)    // 5 Hz
val STROBE_FAST = StrobeConfig(10f, 0.5f)     // 10 Hz
val STROBE_EMERGENCY = StrobeConfig(4f, 0.3f) // 4 Hz, short flashes
```

---

## 6. ADB Control Reference

### Quick Settings Tile Control

```bash
# Toggle flashlight (most reliable method)
adb shell "cmd statusbar click-tile Flashlight"

# List all QS tiles
adb shell "settings get secure sysui_qs_tiles"
```

### Settings Control

```bash
# Samsung brightness (1001-1005)
adb shell "settings put system Flashlight_brightness_level 1005"
adb shell "settings get system Flashlight_brightness_level"

# List all flash-related settings
adb shell "settings list system | grep -i flash"
```

### Broadcast Intents (May have permission restrictions)

```bash
# Standard Android toggle (may not work on all devices)
adb shell "am broadcast -a android.intent.action.TOGGLE_FLASHLIGHT"
```

---

## 7. Implementation Architecture

### FlashlightMiniApp Class Structure

```kotlin
class FlashlightMiniApp(private val context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val flashCameraId = findFlashCamera()
    private val patternExecutor = FlashPatternExecutor(cameraManager)

    // State
    private val _torchState = MutableStateFlow(false)
    val torchState: StateFlow<Boolean> = _torchState.asStateFlow()

    private val _brightnessLevel = MutableStateFlow(3)  // 1-5
    val brightnessLevel: StateFlow<Int> = _brightnessLevel.asStateFlow()

    // Core controls
    fun toggleTorch()
    fun setTorch(enabled: Boolean)
    fun setBrightness(level: Int)  // 1-5

    // Pattern modes
    fun startSOS()
    fun startStrobe(frequency: Float)
    fun startMorseCode(text: String)
    fun stopPattern()

    // Advanced (Android 13+)
    fun setTorchStrength(level: Int)
    fun getMaxTorchStrength(): Int

    // Cleanup
    fun release()
}
```

### UI Components

1. **Main Toggle Button** - Large on/off button
2. **Brightness Slider** - 5-level brightness control
3. **Pattern Buttons** - SOS, Strobe, Custom
4. **Frequency Control** - For strobe mode
5. **Morse Input** - Text-to-morse converter
6. **Screen Flash** - Use screen as light source

---

## 8. Feature Priority Matrix

| Feature | Priority | Complexity | API Level |
|---------|----------|------------|-----------|
| Basic Torch On/Off | P0 | Low | 21+ |
| Samsung 5-Level Brightness | P0 | Low | Any |
| Torch State Callback | P0 | Low | 23+ |
| SOS Pattern | P1 | Medium | 21+ |
| Strobe Effect | P1 | Medium | 21+ |
| Android 13 Torch Strength | P1 | Low | 33+ |
| Custom Morse Code | P2 | Medium | 21+ |
| Screen Flash Mode | P2 | Medium | 21+ |
| Widget Support | P2 | High | 21+ |
| Pattern Presets | P3 | Low | 21+ |

---

## 9. Testing Checklist

### Basic Functionality
- [ ] Torch on/off toggle
- [ ] Brightness level 1-5 (Samsung)
- [ ] Android 13+ strength levels
- [ ] State persistence across app restart

### Pattern Modes
- [ ] SOS pattern timing accuracy
- [ ] Strobe frequency accuracy
- [ ] Pattern interrupt/stop
- [ ] Concurrent camera use handling

### Edge Cases
- [ ] Camera in use by other app
- [ ] Low battery behavior
- [ ] Thermal throttling
- [ ] Screen off behavior
- [ ] Background service handling

---

## 10. References

### Official Documentation
- [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/CameraManager)
- [Android Torch Strength Control](https://source.android.com/docs/core/camera/torch-strength-control)
- [CameraX Overview](https://developer.android.com/training/camerax)

### Third-Party Resources
- [Samsung Flashlight Brightness Guide](https://www.sammobile.com/news/how-to-adjust-flash-brightness-samsung-phones/)
- [MorseLight GitHub](https://github.com/ranjan-malav/MorseLight)
- [Camera2 Flashlight Tutorial](https://www.codeproject.com/Articles/1112813/Android-Flash-Light-Application-Tutorial-Using-Cam)

---

*Generated by research session — Claude Opus 4.5*
