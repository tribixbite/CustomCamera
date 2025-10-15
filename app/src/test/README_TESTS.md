# CustomCamera Test Suite

## 🎯 Overview

Comprehensive automated test system for the CustomCamera application, covering unit tests, integration tests, performance tests, and plugin-specific testing.

**Test Coverage:**
- ✅ Plugin lifecycle and processing
- ✅ Camera engine integration
- ✅ Settings reactive architecture
- ✅ Performance benchmarks
- ✅ Concurrency testing
- ✅ Edge case handling

---

## 📊 Test Architecture

### Core Components

**1. PluginTestFramework** (`testing/PluginTestFramework.kt`)
- Comprehensive plugin testing utilities
- Lifecycle verification
- Processing performance measurement
- Concurrency testing
- P95/P99 performance metrics

**2. MockCameraContext** (`testing/MockCameraContext.kt`)
- Mock CameraContext implementation
- Configurable settings
- No real Android dependencies
- Isolated plugin testing

**3. TestImageFactory** (`testing/TestImageFactory.kt`)
- Mock ImageProxy generation
- Test bitmap creation
- YUV test data
- Edge case images
- Performance test batches

---

## 🧪 Test Categories

### Unit Tests

**GridOverlayPluginTest** (`plugins/GridOverlayPluginTest.kt`)
- Plugin metadata verification
- Lifecycle testing
- Enable/disable states
- Grid type cycling
- Resource cleanup

**BarcodePluginTest** (`plugins/BarcodePluginTest.kt`)
- Frame processing
- ML Kit integration
- Performance metrics
- Concurrent processing
- Various image sizes
- Memory leak prevention

**SettingsManagerTest** (`engine/SettingsManagerTest.kt`)
- StateFlow reactivity
- Preference persistence
- Type-safe settings
- Default values
- Multiple setting updates

### Integration Tests

**CameraEngineTest** (`engine/CameraEngineTest.kt`)
- Plugin registration
- Priority sorting
- Mode switching
- Provider management
- Active plugin tracking

---

## 🚀 Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Suite
```bash
./gradlew test --tests "com.customcamera.app.plugins.GridOverlayPluginTest"
./gradlew test --tests "com.customcamera.app.engine.*"
```

### Run Single Test
```bash
./gradlew test --tests "com.customcamera.app.plugins.BarcodePluginTest.testPluginLifecycle"
```

### Run with Code Coverage
```bash
./gradlew testDebugUnitTestCoverage
```

### View Test Report
After running tests, open:
```
app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 📝 Writing New Tests

### Plugin Test Example

```kotlin
class MyPluginTest {
    private lateinit var plugin: MyPlugin
    private lateinit var testFramework: PluginTestFramework
    private lateinit var mockContext: MockCameraContext

    @Before
    fun setup() {
        mockContext = MockCameraContext.createBasic()
        plugin = MyPlugin()
        testFramework = PluginTestFramework()
    }

    @Test
    fun testPluginLifecycle() = runTest {
        val result = testFramework.testPluginLifecycle(
            plugin = plugin,
            context = mockContext
        )

        result.assertSuccess()
        result.assertCompletesWithin(1000)
    }

    @Test
    fun testProcessing() = runTest {
        plugin.initialize(mockContext)
        plugin.enable()

        val mockImage = TestImageFactory.createMockImageProxy()

        val result = testFramework.testPluginProcessing(
            plugin = plugin,
            mockImage = mockImage
        )

        result.assertSuccess()
        result.assertProcessesWithinMs(50)
    }
}
```

### Performance Test Example

```kotlin
@Test
fun testPerformance() = runTest {
    plugin.initialize(mockContext)
    plugin.enable()

    val mockImage = TestImageFactory.createMockImageProxy()

    val metrics = testFramework.measurePluginPerformance(
        plugin = plugin,
        mockImage = mockImage,
        iterations = 100
    )

    // Assert performance requirements
    metrics.assertAverageWithinMs(50)
    metrics.assertP95WithinMs(75)
    metrics.assertSuccessRate(0.95f)

    // Print detailed metrics
    println(metrics.toString())
}
```

### Concurrency Test Example

```kotlin
@Test
fun testConcurrency() = runTest {
    plugin.initialize(mockContext)
    plugin.enable()

    val mockImages = TestImageFactory.createTestImageBatch(
        count = 20,
        size = TestImageFactory.ImageSize.HD
    )

    val result = testFramework.testPluginConcurrency(
        plugin = plugin,
        mockImages = mockImages,
        concurrentThreads = 4
    )

    result.assertAllCompleted()
    result.assertNoErrors()
    result.assertSuccessRate(0.9f)
}
```

---

## 🎨 Test Utilities

### Creating Mock Images

```kotlin
// Basic mock image
val image = TestImageFactory.createMockImageProxy(
    width = 1920,
    height = 1080
)

// Specific size preset
val image = TestImageFactory.createMockImageProxy(
    width = TestImageFactory.ImageSize.FULL_HD.width,
    height = TestImageFactory.ImageSize.FULL_HD.height
)

// Batch of images
val images = TestImageFactory.createTestImageBatch(
    count = 10,
    size = TestImageFactory.ImageSize.HD
)

// Edge case images
val edgeCases = TestImageFactory.createEdgeCaseImages()
```

### Creating Test Bitmaps

```kotlin
// Basic test bitmap
val bitmap = TestImageFactory.createTestBitmap(
    size = TestImageFactory.ImageSize.HD,
    backgroundColor = Color.WHITE,
    addPattern = true
)

// Brightness-specific bitmap
val bitmap = TestImageFactory.createBitmapWithBrightness(
    size = TestImageFactory.ImageSize.HD,
    brightness = 0.5f // 0.0 = black, 1.0 = white
)

// Gradient bitmap
val bitmap = TestImageFactory.createGradientBitmap(
    size = TestImageFactory.ImageSize.HD,
    startColor = Color.BLACK,
    endColor = Color.WHITE
)

// Bitmap with objects
val bitmap = TestImageFactory.createBitmapWithObjects(
    size = TestImageFactory.ImageSize.HD,
    objectType = TestImageFactory.ObjectType.FACE
)
```

### Mock Context Variations

```kotlin
// Basic mock context
val context = MockCameraContext.createBasic()

// With settings manager
val context = MockCameraContext.createWithSettings(settingsManager)

// With camera engine
val context = MockCameraContext.createWithEngine(cameraEngine)

// Fully configured
val context = MockCameraContext.createFull(
    context, lifecycleOwner, settings, engine
)
```

---

## 📈 Performance Metrics

### PerformanceMetrics Data Class

```kotlin
data class PerformanceMetrics(
    val iterations: Int,
    val averageTimeNs: Long,    // Average processing time
    val minTimeNs: Long,         // Fastest processing
    val maxTimeNs: Long,         // Slowest processing
    val medianTimeNs: Long,      // Median time
    val p95TimeNs: Long,         // 95th percentile
    val p99TimeNs: Long,         // 99th percentile
    val successRate: Float,      // Success rate (0.0-1.0)
    val failureRate: Float       // Failure rate (0.0-1.0)
)
```

### Assertion Methods

```kotlin
metrics.assertAverageWithinMs(50)    // Average < 50ms
metrics.assertP95WithinMs(100)       // P95 < 100ms
metrics.assertSuccessRate(0.95f)     // Success >= 95%
```

---

## 🔍 Test Coverage Goals

### Current Coverage
- ✅ Core plugins: Grid, Barcode
- ✅ Camera engine
- ✅ Settings manager
- ✅ Test framework infrastructure

### Planned Coverage
- ⏳ AI plugins (Scene detection, Object recognition)
- ⏳ Video recording plugins
- ⏳ Dual camera PiP
- ⏳ UI components (Overlays, Managers)
- ⏳ Presentation layer (Demo showcase, Performance monitor)
- ⏳ Utils (Image compositing, Haptic feedback)

### Coverage Targets
- **Unit Tests**: 80%+ code coverage
- **Integration Tests**: All major workflows
- **Performance Tests**: All processing plugins
- **UI Tests**: Critical user flows

---

## 🐛 Debugging Tests

### Enable Verbose Logging
```bash
./gradlew test --info
```

### Run Single Test with Stack Traces
```bash
./gradlew test --tests "MyTest" --stacktrace
```

### Android Studio Test Runner
1. Right-click on test class
2. Select "Run 'MyTest'"
3. View results in Test panel

---

## 🎯 Best Practices

### 1. Test Isolation
- Each test should be independent
- Use `@Before` to set up fresh state
- Clean up resources in test or `@After`

### 2. Performance Testing
- Use realistic image sizes
- Test with various device configurations
- Measure P95/P99 for consistency
- Run multiple iterations for accuracy

### 3. Concurrency Testing
- Test with realistic thread counts
- Verify proper synchronization
- Check for race conditions
- Ensure resource cleanup

### 4. Mock Usage
- Mock external dependencies
- Use real implementations for core logic
- Verify mock interactions when needed
- Keep mocks simple and focused

### 5. Assertions
- Use descriptive assertion messages
- Test both success and failure cases
- Verify state changes
- Check resource cleanup

---

## 📦 Dependencies

```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.3.1'
testImplementation 'org.mockito:mockito-inline:5.2.0'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
testImplementation 'androidx.test:core:1.5.0'
testImplementation 'androidx.test.ext:junit:1.1.5'
```

---

## 🚦 CI/CD Integration

### GitHub Actions Workflow (Planned)
```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run Unit Tests
        run: ./gradlew test
      - name: Generate Coverage Report
        run: ./gradlew testDebugUnitTestCoverage
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
```

---

## 📚 Additional Resources

- [Android Testing Guide](https://developer.android.com/training/testing)
- [JUnit Documentation](https://junit.org/junit4/)
- [Mockito Documentation](https://site.mockito.org/)
- [Kotlin Coroutines Testing](https://kotlinlang.org/docs/coroutines-guide.html)

---

## ✨ Future Enhancements

1. **UI Testing with Espresso**
   - Camera interface interactions
   - Settings screen navigation
   - Gallery integration

2. **Instrumented Tests**
   - Real camera hardware tests
   - CameraX integration tests
   - ML Kit on-device tests

3. **Performance Profiling**
   - Memory leak detection
   - CPU profiling
   - Battery impact measurement

4. **Snapshot Testing**
   - UI component screenshots
   - Visual regression testing

5. **Test Data Management**
   - Test image repository
   - Mock data generators
   - Fixture management

---

**Last Updated**: 2025-10-15
**Version**: 1.0
**Status**: Production-ready test framework ✅
