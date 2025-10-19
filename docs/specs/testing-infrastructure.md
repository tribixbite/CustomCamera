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

## Future Enhancements
- Screenshot testing (deferred - pixel-perfect UI validation)
- Performance regression tracking (deferred - benchmarking system)
- Mutation testing (deferred - test quality validation)
- Visual regression testing (deferred - UI change detection)
- Chaos/fuzz testing (deferred - robustness validation)

---

**Created**: 2025-10-19
**Last Updated**: 2025-10-19
**Owner**: CustomCamera Development Team
**Status**: Complete, Production-Ready
