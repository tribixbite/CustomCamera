# CustomCamera - Professional Plugin-Based Camera App

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg" alt="Language">
  <img src="https://img.shields.io/badge/Architecture-Plugin%20System-orange.svg" alt="Architecture">
  <img src="https://img.shields.io/badge/Camera-CameraX-red.svg" alt="Camera">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License">
</p>

A modern, extensible Android camera application built with a powerful plugin architecture that enables professional-grade camera controls, real-time processing, and advanced features through a modular design.

## 🌟 Features

### 📷 Core Camera Functionality
- **Multi-Camera Support** - Automatic detection and seamless switching between available cameras
- **Professional Controls** - Manual exposure compensation, ISO control, and advanced settings
- **Intelligent Focus** - Continuous autofocus with tap-to-focus capability
- **Real-time Processing** - Live camera frame analysis and information extraction
- **Composition Assistance** - Multiple grid overlays (rule of thirds, golden ratio, center cross)

### 🔧 Plugin Architecture
- **Modular Design** - Extensible plugin system with three specialized plugin types
- **Hot-swappable Plugins** - Enable/disable features without app restart
- **Performance Optimized** - Parallel plugin execution with real-time monitoring
- **Type Safety** - Strongly typed plugin interfaces with comprehensive error handling

### 🎨 User Experience
- **Modern UI** - Material3 design with floating camera controls
- **Intuitive Gestures** - Tap-to-focus, double-tap grid toggle
- **Professional Feedback** - Real-time exposure analysis and recommendations
- **Seamless Integration** - All plugins work together harmoniously

## 🏗️ Architecture

### Plugin System Overview

The CustomCamera app is built around a sophisticated plugin architecture that separates concerns and enables easy feature extension:

```
┌─────────────────────────────────────────────────────────────┐
│                    CameraEngine                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              PluginManager                          │    │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │    │
│  │  │ControlPlugin│ │  UIPlugin   │ │ProcessPlugin│   │    │
│  │  └─────────────┘ └─────────────┘ └─────────────┘   │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              CameraContext                          │    │
│  │  • DebugLogger  • SettingsManager  • CameraProvider│    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Core Components

#### 🎯 CameraEngine
Central coordination engine that manages camera lifecycle, plugin registration, and provides unified interfaces for camera operations.

```kotlin
class CameraEngine(context: Context, lifecycleOwner: LifecycleOwner) {
    suspend fun initialize(): Result<Unit>
    suspend fun bindCamera(config: CameraConfig): Result<Camera>
    fun registerPlugin(plugin: CameraPlugin)
    fun processFrame(image: ImageProxy)
}
```

#### 🔌 Plugin System

**Three Plugin Types:**

1. **ControlPlugin** - Camera control operations (focus, exposure, ISO)
2. **UIPlugin** - User interface overlays and controls
3. **ProcessingPlugin** - Real-time frame analysis and processing

```kotlin
abstract class CameraPlugin {
    abstract val name: String
    abstract suspend fun initialize(context: CameraContext)
    abstract suspend fun onCameraReady(camera: Camera)
    abstract fun cleanup()
}
```

#### ⚙️ Supporting Systems

- **DebugLogger** - Comprehensive logging with real-time monitoring
- **SettingsManager** - Reactive settings with StateFlow integration
- **PluginManager** - Parallel execution with performance tracking

## 🚀 Quick Start

### Prerequisites
- Android Studio Arctic Fox (2020.3.1) or newer
- Android SDK API 21+ (Android 5.0)
- Kotlin 1.8.0+
- CameraX 1.3.0+

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/CustomCamera.git
cd CustomCamera
```

2. **Open in Android Studio**
```bash
# Or open the project folder in Android Studio
./gradlew assembleDebug
```

3. **Install on device**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Basic Usage

1. **Launch the app** - Select from available cameras or skip to use default
2. **Take photos** - Tap capture button or use volume keys
3. **Advanced controls** - Tap settings button for camera information and controls
4. **Toggle grid** - Double-tap preview for composition grid overlay
5. **Focus control** - Tap anywhere on preview for manual focus

## 📱 Implemented Plugins

### 🎯 AutoFocusPlugin (ControlPlugin)
Professional focus control with multiple modes:

```kotlin
class AutoFocusPlugin : ControlPlugin() {
    enum class AutoFocusMode { CONTINUOUS, MANUAL, SINGLE }

    suspend fun performTapToFocus(x: Float, y: Float): ControlResult
    suspend fun lockFocus(): ControlResult
    fun setAutoFocusMode(mode: AutoFocusMode)
}
```

**Features:**
- Continuous autofocus with camera center point
- Tap-to-focus with touch event handling
- Focus lock capability for stable shots
- Settings persistence and comprehensive logging

### 🎨 GridOverlayPlugin (UIPlugin)
Composition assistance with multiple grid types:

```kotlin
class GridOverlayPlugin : UIPlugin() {
    enum class GridType {
        RULE_OF_THIRDS, GOLDEN_RATIO, CENTER_CROSS,
        DIAGONAL_LINES, SQUARE_GRID
    }

    fun showGrid()
    fun hideGrid()
    fun setGridType(type: GridType)
}
```

**Features:**
- Rule of thirds, golden ratio, center cross grids
- Custom GridOverlayView with dynamic Canvas drawing
- Real-time visibility control and grid type switching
- Settings integration with instant updates

### 📊 CameraInfoPlugin (ProcessingPlugin)
Real-time camera analysis and monitoring:

```kotlin
class CameraInfoPlugin : ProcessingPlugin() {
    override suspend fun processFrame(image: ImageProxy): ProcessingResult
    fun getCameraInfo(): Map<String, Any>
    fun getProcessingStats(): Map<String, Any>
}
```

**Features:**
- Real-time frame processing and information extraction
- Performance monitoring with configurable intervals
- Camera characteristics collection and logging
- Frame statistics tracking and analysis

### 🎛️ ProControlsPlugin (ControlPlugin)
Professional manual camera controls:

```kotlin
class ProControlsPlugin : ControlPlugin() {
    fun setManualModeEnabled(enabled: Boolean)
    fun setExposureCompensation(exposureIndex: Int)
    fun setISO(iso: Int)
    fun createControlsUI(context: Context): View?
}
```

**Features:**
- Professional UI controls for exposure and ISO adjustment
- Real-time control value display and feedback
- Manual mode toggle with auto/manual switching
- Settings persistence across camera sessions

### 📸 ExposureControlPlugin (ControlPlugin)
Advanced exposure management system:

```kotlin
class ExposureControlPlugin : ControlPlugin() {
    suspend fun setExposureCompensation(index: Int): ControlResult
    suspend fun lockExposure(): ControlResult
    suspend fun performExposureBracketing(steps: IntArray): ControlResult
    fun analyzeExposure(): ExposureAnalysis?
}
```

**Features:**
- Exposure compensation with camera capability detection
- Exposure lock/unlock functionality for stable shots
- Exposure bracketing for HDR-like captures
- Real-time exposure analysis with recommendations
- EV (exposure value) calculations and display

## 🔧 Development

### Creating Custom Plugins

#### 1. Control Plugin Example
```kotlin
class MyControlPlugin : ControlPlugin() {
    override val name = "MyControl"
    override val version = "1.0.0"

    override suspend fun initialize(context: CameraContext) {
        // Plugin initialization
    }

    override suspend fun onCameraReady(camera: Camera) {
        // Configure camera controls
    }

    override suspend fun applyControls(camera: Camera): ControlResult {
        // Apply your custom controls
        return ControlResult.Success("Controls applied")
    }

    override fun getCurrentSettings(): Map<String, Any> {
        return mapOf("setting1" to "value1")
    }
}
```

#### 2. UI Plugin Example
```kotlin
class MyUIPlugin : UIPlugin() {
    override val name = "MyUI"

    override fun createUIView(context: CameraContext): View? {
        // Return your custom UI view
        return MyCustomView(context.context)
    }

    override fun onUIEvent(event: UIEvent) {
        when (event) {
            is UIEvent.Show -> showUI()
            is UIEvent.Hide -> hideUI()
        }
    }
}
```

#### 3. Processing Plugin Example
```kotlin
class MyProcessingPlugin : ProcessingPlugin() {
    override val name = "MyProcessor"

    override suspend fun processFrame(image: ImageProxy): ProcessingResult {
        // Analyze the camera frame
        val analysis = analyzeFrame(image)

        return ProcessingResult.Success(
            data = mapOf("result" to analysis),
            metadata = ProcessingMetadata(/* ... */)
        )
    }
}
```

### Plugin Registration

```kotlin
// In your Activity
private fun initializeCameraEngine() {
    cameraEngine = CameraEngine(this, this)

    // Register your custom plugins
    cameraEngine.registerPlugin(MyControlPlugin())
    cameraEngine.registerPlugin(MyUIPlugin())
    cameraEngine.registerPlugin(MyProcessingPlugin())
}
```

### Building and Testing

```bash
# Build debug version
./gradlew assembleDebug

# Run tests
./gradlew test

# Check code style
./gradlew lint

# Type checking
./gradlew compileDebugKotlin
```

## 📁 Project Structure

```
CustomCamera/
├── app/
│   ├── src/main/java/com/customcamera/app/
│   │   ├── engine/                          # Core engine components
│   │   │   ├── CameraEngine.kt              # Central camera coordinator
│   │   │   ├── CameraContext.kt             # Shared plugin context
│   │   │   ├── DebugLogger.kt               # Comprehensive logging
│   │   │   ├── SettingsManager.kt           # Reactive settings
│   │   │   └── plugins/                     # Plugin system
│   │   │       ├── CameraPlugin.kt          # Base plugin classes
│   │   │       └── PluginManager.kt         # Plugin lifecycle management
│   │   ├── plugins/                         # Implemented plugins
│   │   │   ├── AutoFocusPlugin.kt           # Focus control
│   │   │   ├── GridOverlayPlugin.kt         # Composition grids
│   │   │   ├── CameraInfoPlugin.kt          # Frame analysis
│   │   │   ├── ProControlsPlugin.kt         # Manual controls
│   │   │   └── ExposureControlPlugin.kt     # Exposure management
│   │   ├── MainActivity.kt                  # App entry point
│   │   ├── CameraSelectionActivity.kt       # Camera selection
│   │   ├── CameraActivity.kt                # Original camera (legacy)
│   │   └── CameraActivityEngine.kt          # Plugin-enabled camera
│   └── src/main/res/                        # Resources
├── memory/
│   └── todo.md                             # Development task tracking
├── README.md                               # This file
└── build.gradle                            # Build configuration
```

## 🎯 Roadmap

### Phase 4 Options (Next Development Cycle)
- [ ] **Dual Camera PiP System** - Simultaneous front/rear camera capture
- [ ] **Computer Vision Integration** - Barcode/QR scanning with ML Kit
- [ ] **Custom Pre-Shot Crop System** - Interactive crop before capture
- [ ] **Video Recording Controls** - Manual controls for video capture
- [ ] **Advanced Settings UI** - Complete settings management interface

### Future Enhancements
- [ ] **Night Mode** - Low-light optimization with extended exposure
- [ ] **Portrait Mode** - Depth-based background blur effects
- [ ] **RAW Capture** - Professional RAW photo format support
- [ ] **Histogram Display** - Real-time exposure histogram overlay
- [ ] **Focus Peaking** - Manual focus assistance visualization
- [ ] **Time-lapse Recording** - Automated time-lapse capture
- [ ] **Burst Mode** - High-speed continuous capture
- [ ] **HDR Processing** - Real-time HDR image processing

## 🤝 Contributing

We welcome contributions! Please see our contributing guidelines:

### Development Setup
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes following our coding standards
4. Test thoroughly on multiple devices
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to your branch (`git push origin feature/amazing-feature`)
7. Create a Pull Request

### Coding Standards
- Use Kotlin coding conventions
- Add comprehensive documentation for new plugins
- Include unit tests for new functionality
- Follow the existing plugin architecture patterns
- Ensure all builds pass before submitting

## 📋 Requirements

- **Minimum SDK:** Android API 21 (Android 5.0)
- **Target SDK:** Android API 34 (Android 14)
- **Language:** Kotlin 1.8.0+
- **Architecture:** MVVM with Plugin System
- **Camera:** CameraX 1.3.0+
- **UI:** Material Design 3
- **Permissions:** CAMERA, RECORD_AUDIO

### Device Compatibility
- Supports devices with multiple cameras
- Graceful fallback for single-camera devices
- Handles broken/unavailable cameras gracefully
- Works on phones and tablets
- Optimized for various screen sizes

## 🐛 Known Issues

- Deprecated `systemUiVisibility` warnings (Android 11+) - Will be updated to WindowInsetsController
- ISO control limited by CameraX API - Full manual control requires Camera2 API
- Some advanced features require specific camera hardware support

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **CameraX Team** - For the excellent camera API
- **Material Design** - For UI components and guidelines
- **Kotlin Team** - For the amazing programming language
- **Android Architecture Components** - For lifecycle management
- **Open Source Community** - For inspiration and feedback

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/yourusername/CustomCamera/issues)
- **Documentation:** [Wiki](https://github.com/yourusername/CustomCamera/wiki)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/CustomCamera/discussions)

---

<p align="center">
  <strong>🤖 Built with Claude Code - Professional AI-Powered Development</strong>
</p>

<p align="center">
  Made with ❤️ for the Android camera development community
</p>