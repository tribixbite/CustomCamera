# Testing Infrastructure Specification

## Feature Overview
**Feature Name**: Comprehensive Testing Infrastructure
**Priority**: P0
**Status**: Complete
**Target Version**: 2.0.0+

### Summary
World-class automated testing system with plugin testing framework, mock utilities, UI tests, instrumented tests, memory leak detection, and CI/CD integration.

### Motivation
Ensure code quality, prevent regressions, validate performance, and enable confident refactoring through comprehensive automated testing at all levels.

## Requirements

### Functional Requirements
1. **FR-1**: Plugin test framework with lifecycle verification
2. **FR-2**: Plugin performance measurement (avg, P95, P99)
3. **FR-3**: Plugin concurrency testing
4. **FR-4**: Mock image generation (YUV, bitmaps, patterns)
5. **FR-5**: Mock camera context for isolated testing
6. **FR-6**: UI tests with Espresso
7. **FR-7**: Instrumented tests on real devices
8. **FR-8**: Memory leak detection with LeakCanary
9. **FR-9**: Code coverage reporting
10. **FR-10**: Assertion helpers for all result types

### Non-Functional Requirements
1. **NFR-1**: Performance - Test suite completes < 5 minutes
2. **NFR-2**: Coverage - > 70% code coverage target
3. **NFR-3**: Reliability - < 1% flaky test rate
4. **NFR-4**: Maintainability - Clear test documentation, easy to add new tests

### User Stories
- **As a** developer, **I want** plugin test framework, **so that** I can validate plugin behavior
- **As a** developer, **I want** performance tests, **so that** I can ensure 60fps target
- **As a** QA engineer, **I want** UI tests, **so that** I can verify user flows
- **As a** team lead, **I want** coverage reports, **so that** I can track test quality

## Technical Design

### Architecture
```
Testing Infrastructure
├── Unit Tests (JUnit 4)
│   ├── PluginTestFramework
│   ├── TestImageFactory
│   └── SimpleMockCameraContext
├── UI Tests (Espresso)
│   ├── MainActivityUITest
│   └── CameraActivityUITest
├── Instrumented Tests (AndroidJUnit4)
│   ├── CameraFunctionalityTest
│   └── MemoryLeakTest
└── CI/CD (GitHub Actions)
    ├── Build & Test
    ├── Coverage Report
    └── Performance Validation
```

### Component Breakdown

#### 1. PluginTestFramework
**Responsibilities**:
- Test plugin lifecycle (init → bind → process → cleanup)
- Measure plugin performance (iterations, timing)
- Test plugin concurrency (multi-thread execution)
- Provide assertion helpers

**Key Classes**:
- `PluginTestFramework.kt` - Main framework class
- `PluginLifecycleTest.kt` - Lifecycle validation
- `PluginPerformanceTest.kt` - Performance measurement
- `PluginConcurrencyTest.kt` - Concurrent execution

**Features**:
- Lifecycle step verification
- Performance metrics (avg, min, max, P95, P99)
- Concurrency testing with thread pools
- Success rate calculation
- Timing assertions

#### 2. TestImageFactory
**Responsibilities**:
- Generate mock ImageProxy instances
- Create test bitmaps with patterns
- Generate YUV plane data
- Create object-specific images (faces, barcodes, text)
- Edge case image generation

**Key Classes**:
- `TestImageFactory.kt` - Image generation
- `MockImageProxy.kt` - ImageProxy implementation
- `YUVPlaneGenerator.kt` - YUV data creation

**Capabilities**:
- Brightness levels (dark, medium, bright)
- Gradient patterns (horizontal, vertical, radial)
- Object-specific images (face, barcode, text)
- Edge cases (black, white, noise)
- Batch generation for load testing

#### 3. SimpleMockCameraContext
**Responsibilities**:
- Provide test CameraContext implementation
- Mock dependencies (lifecycle, context, engine)
- Enable isolated plugin testing

**Key Classes**:
- `SimpleMockCameraContext.kt` - Mock context factory
- `MockLifecycleOwner.kt` - Lifecycle mock
- `MockCameraEngine.kt` - Engine mock

#### 4. UI Test Suite (Espresso)
**Responsibilities**:
- Test main screen UI
- Test camera interface interactions
- Validate button states and visibility
- Test navigation flows

**Test Classes**:
- `MainActivityUITest.kt` (5 tests)
- `CameraActivityUITest.kt` (12 tests)

**Coverage**:
- Button clicks and visibility
- Text field validation
- Navigation flows
- Error state UI

#### 5. Instrumented Test Suite
**Responsibilities**:
- Test camera initialization on real device
- Test photo capture functionality
- Test permission handling
- Memory leak detection

**Test Classes**:
- `CameraFunctionalityTest.kt` (6 tests)
- `MemoryLeakTest.kt` (5 tests)

**Coverage**:
- Real camera operations
- Permission grant/deny flows
- Memory leak scenarios
- Real device compatibility

#### 6. Memory Leak Detection
**Responsibilities**:
- Integrate LeakCanary
- Detect activity leaks
- Detect ImageProxy leaks
- Detect context leaks
- Validate proper cleanup

**Key Classes**:
- `MemoryLeakTest.kt` - Leak detection tests
- LeakCanary integration (debug builds)

### Data Structures
```kotlin
// Test result types
data class LifecycleTestResult(
    val steps: List<String>,
    val success: Boolean,
    val durationMs: Long,
    val error: Exception?
)

data class PerformanceMetrics(
    val iterations: Int,
    val avgMs: Double,
    val minMs: Long,
    val maxMs: Long,
    val p95Ms: Long,
    val p99Ms: Long,
    val successRate: Float
)

data class ConcurrencyTestResult(
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val avgMs: Double,
    val errors: List<Exception>
)

// Mock image configuration
data class TestImageConfig(
    val width: Int = 640,
    val height: Int = 480,
    val format: Int = ImageFormat.YUV_420_888,
    val pattern: ImagePattern = ImagePattern.SOLID_COLOR,
    val brightness: Float = 0.5f
)

enum class ImagePattern {
    SOLID_COLOR, GRADIENT_HORIZONTAL, GRADIENT_VERTICAL,
    GRADIENT_RADIAL, CHECKERBOARD, NOISE, FACE, BARCODE, TEXT
}
```

### API/Interface Design
```kotlin
// PluginTestFramework
class PluginTestFramework {
    fun testPluginLifecycle(
        plugin: CameraPlugin,
        context: CameraContext,
        camera: Camera
    ): LifecycleTestResult

    fun measurePluginPerformance(
        plugin: ProcessingPlugin,
        image: ImageProxy,
        iterations: Int = 100
    ): PerformanceMetrics

    fun testPluginConcurrency(
        plugin: ProcessingPlugin,
        images: List<ImageProxy>,
        threads: Int = 4
    ): ConcurrencyTestResult
}

// Assertion helpers
fun LifecycleTestResult.assertSuccess()
fun LifecycleTestResult.assertContainsStep(step: String)
fun LifecycleTestResult.assertCompletesWithin(ms: Long)

fun PerformanceMetrics.assertAverageWithinMs(maxMs: Long)
fun PerformanceMetrics.assertP95WithinMs(maxMs: Long)
fun PerformanceMetrics.assertSuccessRate(minRate: Float)

fun ConcurrencyTestResult.assertAllCompleted()
fun ConcurrencyTestResult.assertNoErrors()
fun ConcurrencyTestResult.assertSuccessRate(minRate: Float)

// TestImageFactory
object TestImageFactory {
    fun createMockImageProxy(config: TestImageConfig): ImageProxy
    fun createTestBitmap(config: TestImageConfig): Bitmap
    fun createYUVPlanes(width: Int, height: Int, brightness: Float): Array<Plane>
    fun createFaceImage(): ImageProxy
    fun createBarcodeImage(): ImageProxy
    fun createBatchImages(count: Int, config: TestImageConfig): List<ImageProxy>
}

// SimpleMockCameraContext
object SimpleMockCameraContext {
    fun create(): CameraContext
    fun createWithEngine(engine: CameraEngine): CameraContext
    fun createWithLifecycle(lifecycle: Lifecycle): CameraContext
}
```

### State Management
- **Test Results**: Collected during test execution
- **Mock State**: Reset between tests
- **LeakCanary**: Enabled in debug builds only
- **Coverage Data**: Generated by Gradle task

## Implementation Plan

### Phase 1: Test Dependencies (Complete)
**Duration**: 0.5 day
**Deliverables**:
- [x] JUnit 4.13.2 added
- [x] Mockito 5.3.1 added
- [x] Coroutines Test 1.7.3 added
- [x] Espresso dependencies added
- [x] LeakCanary integration

### Phase 2: Plugin Test Framework (Complete)
**Duration**: 1.5 days
**Deliverables**:
- [x] PluginTestFramework class
- [x] Lifecycle verification
- [x] Performance measurement
- [x] Concurrency testing
- [x] Assertion helpers

### Phase 3: Mock Utilities (Complete)
**Duration**: 1 day
**Deliverables**:
- [x] TestImageFactory
- [x] SimpleMockCameraContext
- [x] Mock image patterns
- [x] YUV plane generation

### Phase 4: Example Plugin Tests (Complete)
**Duration**: 1 day
**Deliverables**:
- [x] GridOverlayPluginTest (8 tests)
- [x] AutoFocusPluginTest (5 tests)
- [x] TestImageFactoryTest

### Phase 5: UI Test Suite (Complete)
**Duration**: 1.5 days
**Deliverables**:
- [x] MainActivityUITest (5 tests)
- [x] CameraActivityUITest (12 tests)
- [x] Espresso configuration

### Phase 6: Instrumented Tests (Complete)
**Duration**: 1 day
**Deliverables**:
- [x] CameraFunctionalityTest (6 tests)
- [x] MemoryLeakTest (5 tests)
- [x] Real device testing

### Phase 7: Documentation (Complete)
**Duration**: 0.5 day
**Deliverables**:
- [x] app/src/test/README_TESTS.md
- [x] Testing guide
- [x] Best practices
- [x] CI/CD integration notes

## Testing Strategy

### Test Pyramid
- **Unit Tests (70%)**: Fast, isolated, comprehensive coverage
- **Integration Tests (20%)**: Component interaction validation
- **UI/E2E Tests (10%)**: Critical user flows

### Test Categories

#### Unit Tests (38+ tests)
- Plugin lifecycle tests
- Performance measurement tests
- Mock utility tests
- Data structure tests
- Logic validation

#### UI Tests (17 tests)
- MainActivity UI interactions
- CameraActivityEngine UI flows
- Button state validation
- Navigation testing

#### Instrumented Tests (11 tests)
- Camera functionality on device
- Permission handling
- Memory leak detection
- Real hardware validation

### Coverage Targets
- **Overall**: > 70%
- **Core Camera**: > 85%
- **Plugin System**: > 80%
- **UI Components**: > 60%

## Dependencies

### Test Dependencies
```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.3.1'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
testImplementation 'androidx.test:core:1.5.0'

androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
androidTestImplementation 'androidx.test.espresso:espresso-contrib:3.5.1'

debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.12'
```

### Internal Dependencies
- All app modules (for testing)
- Plugin system
- Camera engine
- UI components

### Breaking Changes
- None (testing infrastructure only)

## Security Considerations
- **Test Data**: No production data in tests
- **Mocks**: No real camera access in unit tests
- **LeakCanary**: Debug builds only, not in release

## Error Handling

### Test Failure Scenarios
1. **Plugin test fails**: Log detailed error, mark test as failed
2. **Mock creation fails**: Provide clear error message
3. **Device test timeout**: Retry with longer timeout
4. **Memory leak detected**: Fail test, log leak trace
5. **Flaky test**: Retry up to 3 times, then mark as unstable

### CI/CD Integration
- Tests run on every pull request
- Coverage report generated
- Failed tests block merge
- Performance regression detection

## Documentation Updates
- [x] app/src/test/README_TESTS.md created
- [x] Testing guide in CLAUDE.md
- [x] Architecture docs include testing
- [x] Session history documents test infrastructure

## Success Metrics
- **Test Count**: 38+ automated tests
- **Coverage**: > 70% code coverage achieved
- **Speed**: Test suite < 5 minutes
- **Reliability**: < 1% flaky test rate
- **Confidence**: Team comfortable refactoring with tests

## Test Execution

### Running Tests
```bash
# All unit tests
./gradlew test

# Specific test
./gradlew test --tests "GridOverlayPluginTest"

# With coverage
./gradlew testDebugUnitTestCoverage

# UI tests (requires device/emulator)
./gradlew connectedAndroidTest

# Specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.customcamera.app.CameraFunctionalityTest
```

### Coverage Report
```bash
./gradlew testDebugUnitTestCoverage
# Report: app/build/reports/coverage/test/debug/index.html
```

## Implementation Notes

### Plugin Testing Pattern
```kotlin
@Test
fun testGridOverlayLifecycle() {
    val framework = PluginTestFramework()
    val plugin = GridOverlayPlugin(mockContext)
    val camera = mockCamera

    val result = framework.testPluginLifecycle(plugin, mockContext, camera)

    result.assertSuccess()
    result.assertContainsStep("INIT_SUCCESS")
    result.assertContainsStep("BIND_SUCCESS")
    result.assertCompletesWithin(1000)
}
```

### Performance Testing Pattern
```kotlin
@Test
fun testGridOverlayPerformance() {
    val framework = PluginTestFramework()
    val plugin = GridOverlayPlugin(mockContext)
    val image = TestImageFactory.createMockImageProxy()

    val metrics = framework.measurePluginPerformance(plugin, image, iterations = 100)

    metrics.assertAverageWithinMs(50)
    metrics.assertP95WithinMs(75)
    metrics.assertSuccessRate(0.95f)

    println(metrics) // Detailed performance report
}
```

### Memory Leak Testing
```kotlin
@Test
fun testCameraEngineCleanup() {
    val scenario = ActivityScenario.launch(CameraActivityEngine::class.java)

    scenario.use {
        // Interact with camera
        it.onActivity { activity ->
            activity.setupCamera()
            activity.capturePhoto()
        }
    }

    // LeakCanary automatically detects leaks after activity destroyed
    IdlingRegistry.getInstance().register(LeakCanaryIdlingResource())

    // Verify no leaks detected
    assertNoLeaks()
}
```

## ADB Test Intent System

### Overview
**Status**: Implemented ✅ (2025-11-13)
**Purpose**: Autonomous device testing via Android Debug Bridge (ADB) without manual interaction

The ADB Test Intent System enables fully automated testing of camera functionality through intent-based triggers, allowing for reproducible test scenarios and CI/CD integration.

### Test Intents

#### 1. TEST_CAMERA - Launch Camera
**Intent Action**: `com.customcamera.app.TEST_CAMERA`

**Purpose**: Launch CameraActivityEngine directly for testing

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_CAMERA -n com.customcamera.app/.CameraActivityEngine
```

**Behavior**:
- Launches camera activity
- Uses default camera (usually camera 0)
- Initializes all plugins
- Skips camera selection screen

**Implementation**: `CameraActivityEngine.kt:189`, `AndroidManifest.xml:55-58`

---

#### 2. TEST_PIP - Enable Dual Camera PiP
**Intent Action**: `com.customcamera.app.TEST_PIP`

**Purpose**: Automatically enable Picture-in-Picture dual camera mode

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_PIP -n com.customcamera.app/.CameraActivityEngine
```

**Workflow**:
1. Launches camera normally
2. Waits 3 seconds for camera initialization
3. Automatically enables DualCameraPiP plugin
4. Configures concurrent camera mode

**Implementation**: `CameraActivityEngine.kt:189-201`, `AndroidManifest.xml:59-62`

**Expected Logs**:
```
I ConcurrentCameraCapability: Found X concurrent camera combinations
I DualCameraPiPPlugin: PiP mode enabled
I CameraActivityEngine: 🧪 PiP mode enabled via test intent
```

---

#### 3. TEST_CAPTURE - Automated Photo Capture
**Intent Action**: `com.customcamera.app.TEST_CAPTURE`

**Purpose**: Automatically capture a photo for testing

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_CAPTURE -n com.customcamera.app/.CameraActivityEngine
```

**Workflow**:
1. Launches camera
2. Disables PiP mode if active (for cleaner test scenario)
3. Waits 5 seconds for camera binding (2s PiP + 3s camera)
4. Validates ImageCapture availability
5. Captures photo automatically
6. Saves to `/sdcard/DCIM/Camera/TIMESTAMP.jpg`

**Implementation**: `CameraActivityEngine.kt:202-228`, `AndroidManifest.xml:63-66`

**Critical Fix History**:
- **Commit 7872cccd**: Increased delay from 2s to 5s total
- **Issue**: Camera binding in PiP mode requires additional time
- **Solution**: Disable PiP + 5s total delay ensures reliable capture

**Verification**:
```bash
# List recent photos
adb shell ls -lt /sdcard/DCIM/Camera/ | head -5

# Pull photo for inspection
adb pull /sdcard/DCIM/Camera/$(adb shell ls -t /sdcard/DCIM/Camera/*.jpg | head -1) test_photo.jpg
```

---

#### 4. TEST_VIDEO - Automated Video Recording
**Intent Action**: `com.customcamera.app.TEST_VIDEO`

**Purpose**: Automatically record a test video

**Usage**:
```bash
adb shell am start -a com.customcamera.app.TEST_VIDEO -n com.customcamera.app/.CameraActivityEngine
```

**Workflow**:
1. Launches camera
2. Disables PiP mode (video recording unavailable in PiP)
3. Waits 3s for camera binding
4. **Enables AdvancedVideoRecordingPlugin** (defaults to disabled)
5. **Rebinds camera to activate VideoCapture UseCase**
6. Waits 2s for encoder initialization
7. Records for 6 seconds
8. Stops recording automatically
9. Saves to `/sdcard/DCIM/Camera/video_TIMESTAMP.mp4`

**Implementation**: `CameraActivityEngine.kt:229-288`, `AndroidManifest.xml:65-68`

**Critical Fix History**:
- **Commit 83b04687**: Fixed video save location (private → public DCIM)
- **Commit 21eb934d**: Added camera rebind after plugin.enable()
- **Issue**: Plugin state changes don't automatically trigger camera rebinding
- **Solution**: Explicit `bindCamera()` call after `plugin.enable()` activates VideoCapture UseCase

**Key Learning**: Plugin state changes require explicit camera rebinding to update active UseCases. This pattern applies to all plugins that affect camera configuration.

**Verification**:
```bash
# List recent videos
adb shell ls -lt /sdcard/DCIM/Camera/video_*.mp4 | head -3

# Check video file size (should be ~10-20MB for 6s recording)
adb shell ls -lh /sdcard/DCIM/Camera/video_*.mp4 | head -1

# Pull video for inspection
adb pull /sdcard/DCIM/Camera/$(adb shell ls -t /sdcard/DCIM/Camera/video_*.mp4 | head -1) test_video.mp4
```

---

### Dynamic Coordinate System

**Status**: Implemented ✅ (2025-11-13 - commit 70ee2d0e)
**Purpose**: Device-independent UI interaction testing

**Problem**: Hardcoded tap coordinates (540×800, 540×2200) only worked on 1080×2400 screens

**Solution**: Percentage-based coordinate calculation

**Implementation** (`test-comprehensive-automated.sh`):
```bash
# Query device screen size
get_screen_dimensions() {
    size=$(adb shell wm size | grep "Physical size")
    SCREEN_WIDTH=$(echo "$size" | cut -dx -f1)
    SCREEN_HEIGHT=$(echo "$size" | cut -dx -f2)
}

# Calculate tap coordinates from percentages
calc_tap_coord() {
    x_percent=$1  # 0-100
    y_percent=$2  # 0-100
    x=$((SCREEN_WIDTH * x_percent / 100))
    y=$((SCREEN_HEIGHT * y_percent / 100))
    echo "$x $y"
}

# Tap at percentage-based position
tap_at_percent() {
    coords=$(calc_tap_coord $1 $2)
    adb shell input tap $coords
}
```

**Usage Examples**:
```bash
# Tap capture button (center-x 50%, bottom-y 92%)
tap_at_percent 50 92

# Tap camera selection (center-x 50%, upper-y 33%)
tap_at_percent 50 33

# Tap screen center for gestures
tap_at_percent 50 50
```

**Benefits**:
- Works on any screen size (phone, tablet, foldable)
- No hardcoded pixel coordinates
- Cached screen dimensions (query once per run)
- Clear percentage-based positioning

---

### Automated Test Script

**File**: `test-comprehensive-automated.sh`
**Version**: 2.1 (Dynamic Screen Coordinates)
**Purpose**: Full app testing with 40+ test cases

**Features**:
- ✅ Dynamic screen coordinate calculation (device-independent)
- ✅ All 4 test intents
- ✅ Plugin verification (23 plugins)
- ✅ Settings persistence checks
- ✅ Screenshot capture
- ✅ Markdown + JSON test reports

**Usage**:
```bash
# Full test suite (~20 minutes)
./test-comprehensive-automated.sh

# View results
cat test-results-comprehensive-TIMESTAMP.md
```

**Test Categories**:
1. **Basic Launch**: App startup, camera initialization
2. **Photo Capture**: TEST_CAPTURE intent verification
3. **Video Recording**: TEST_VIDEO intent verification
4. **PiP Mode**: TEST_PIP intent verification
5. **Plugin Toggles**: All 23 plugins enable/disable
6. **Settings Persistence**: State saving/loading
7. **UI Interactions**: Dynamic coordinate taps

**Output**:
- `test-results-comprehensive-TIMESTAMP.md` - Human-readable report
- `test-results-comprehensive-TIMESTAMP.json` - Machine-readable results
- `screenshots/` - UI state captures

---

### Integration with Existing Testing Infrastructure

The ADB Test Intent System complements the existing unit test infrastructure:

**Unit Tests** (PluginTestFramework):
- Fast, isolated plugin testing
- No device required
- Covers logic and edge cases
- Runs in < 1 minute

**ADB Integration Tests** (Test Intent System):
- Full app testing on real device
- Camera hardware validation
- End-to-end workflow verification
- Runs in ~20 minutes

**CI/CD Integration**:
```yaml
# .github/workflows/ci.yml
- name: Run ADB Tests
  run: |
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    ./test-comprehensive-automated.sh
    cat test-results-comprehensive-*.md
```

**Test Coverage**:
- **Unit Tests**: Plugin logic, image processing, utility functions
- **ADB Tests**: Camera initialization, photo/video capture, plugin UI, settings
- **Combined Coverage**: >85% of critical functionality

---

### Documentation

**Comprehensive Guide**: `docs/TESTING_GUIDE.md` (585 lines)
- Complete test intent documentation
- Troubleshooting guides
- Best practices for test automation
- Adding new test intents

**Quick Reference**:
```bash
# Launch camera
adb shell am start -a com.customcamera.app.TEST_CAMERA

# Enable PiP
adb shell am start -a com.customcamera.app.TEST_PIP

# Capture photo
adb shell am start -a com.customcamera.app.TEST_CAPTURE

# Record video
adb shell am start -a com.customcamera.app.TEST_VIDEO

# Run full test suite
./test-comprehensive-automated.sh
```

---

## Future Enhancements
- Screenshot testing (deferred - pixel-perfect UI validation)
- Performance regression tracking (deferred - benchmarking system)
- Mutation testing (deferred - test quality validation)
- Visual regression testing (deferred - UI change detection)
- Chaos/fuzz testing (deferred - robustness validation)
- **CI/CD ADB Test Integration** (planned - automated device testing in GitHub Actions)

---

**Created**: 2025-10-19
**Last Updated**: 2025-11-16
**Owner**: CustomCamera Development Team
**Status**: Complete, Production-Ready
