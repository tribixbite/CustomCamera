# Sprint 2 Quick Start Guide

**Status**: Ready for implementation (100% analyzed)
**Version Target**: v2.3.1
**Duration**: 4-5 weeks (22-36 hours total effort)
**Priority**: Awaiting v2.2.11 user feedback

---

## Quick Reference

### Analysis Documents (Session 27)
1. **STARTUP_PERFORMANCE_ANALYSIS.md** (408 lines) - Startup optimization
2. **BITMAP_MEMORY_ANALYSIS.md** (547 lines) - Memory optimization
3. **APK_SIZE_ANALYSIS.md** (553 lines) - APK size optimization
4. **SESSION27_SUMMARY.md** (445 lines) - Session retrospective
5. **PHASE10_PLANNING.md** (updated) - Complete Sprint 2 roadmap

**Total**: 1,936 lines of implementation guidance

---

## Expected Results

### Before Sprint 2 (v2.3.0)
```
Startup time:    1.6-3.8 seconds
Memory usage:    200-500 MB peak
APK size:        77 MB universal
Download time:   15-30 seconds
```

### After Sprint 2 (v2.3.1)
```
Startup time:    0.8-1.5 seconds  (50-60% faster)
Memory usage:    100-200 MB peak  (50% reduction)
APK size:        40-50 MB/device  (35-40% per-device)
Download time:   8-15 seconds     (50% faster)
```

---

## Week 1-2: Quick Wins (6-10 hours, LOW risk)

### Priority Order
1. **Startup P0**: Parallel isSupported() checks → 1600-1800ms savings (2h)
2. **Startup P1**: ML Kit lazy loading → 300-500ms savings (1-2h)
3. **APK T3**: ML Kit on-demand models → 7 MB reduction (1-2h)
4. **Startup P4**: Debug logging guards → 30-50ms savings (30min)

**File**: `memory/STARTUP_PERFORMANCE_ANALYSIS.md` (lines 171-292)

### Expected Week 1-2 Results
- Cold start: 1.6-3.8s → 1.0-2.0s (40-50% improvement)
- APK size: 77 MB → 70 MB (9% reduction)
- Risk: LOW (proven patterns, isolated changes)

---

## Week 3-4: Major Changes (12-18 hours, MEDIUM risk)

### Priority Order
1. **Memory Tier 1**: ImageProxy LruCache + AI cache → 50% memory reduction (8-12h)
2. **APK T1**: APK splits → 30-39% per-device reduction (2-3 sessions)
3. **Startup P2**: Lazy SharedPreferences → 100-150ms savings (2-3h)

**Files**:
- Memory: `BITMAP_MEMORY_ANALYSIS.md` (lines 1-547)
- APK: `APK_SIZE_ANALYSIS.md` (lines 1-553)
- Startup: `STARTUP_PERFORMANCE_ANALYSIS.md` (lines 224-248)

### Expected Week 3-4 Results
- Memory: 200-500 MB → 100-200 MB peak
- APK: 70 MB → 40-50 MB per architecture
- Cold start: 1.0-2.0s → 0.8-1.5s (additional 20-25% improvement)
- Risk: MEDIUM (requires careful testing)

---

## Week 5: Finalization (4-6 hours)

### Tasks
1. Comprehensive testing (all plugins, all modes)
2. Performance benchmarking (baseline vs optimized)
3. Memory leak verification (LeakCanary)
4. Documentation updates (CHANGELOG, README)
5. Version bump: v2.3.0 → v2.3.1

---

## Implementation Checklists

### Startup Optimization Checklist
- [ ] P0: Parallel isSupported() in PluginRegistry.kt (line 91-94)
- [ ] P1: Lazy ML Kit in BarcodePlugin.kt (line 38)
- [ ] P1: Lazy ML Kit in QRScannerPlugin.kt (line 41)
- [ ] P4: Debug guards in CameraEngine.kt (lines 80-104)
- [ ] P2: Lazy settings in SettingsManager.kt (lines 19-38)
- [ ] P3: Parallel plugin init in PluginManager.kt (lines 49-56)

### Memory Optimization Checklist
- [ ] Fix AISceneRecognitionManager leak (11.3 MB)
- [ ] Fix HDRProcessor weight matrix leak (18 MB)
- [ ] Fix PhotoPreviewOverlay partial leak
- [ ] Implement ImageProxy LruCache
- [ ] Implement AI analysis bitmap cache
- [ ] Implement preview thumbnail cache
- [ ] Test memory usage with profiler

### APK Optimization Checklist
- [ ] T3: Update ML Kit dependencies (remove bundled models)
- [ ] T3: Test model downloads on first use
- [ ] T1: Configure APK splits in build.gradle.kts
- [ ] T1: Test split APKs on multiple devices
- [ ] T1: Update CI/CD for multi-APK builds
- [ ] T2: Enable R8 (Sprint 3, HIGH risk)
- [ ] T4: Resource optimization (Sprint 3, LOW risk)

---

## Testing Strategy

### Performance Benchmarks
```bash
# Measure cold start time
adb shell am start -W com.customcamera.app

# Measure installed size
adb shell pm list packages -s com.customcamera.app
adb shell du -sh /data/app/com.customcamera.app*

# Monitor memory usage
adb shell dumpsys meminfo com.customcamera.app

# Verify APK size
ls -lh app/build/outputs/apk/release/app-*-release.apk
```

### Success Criteria
- ✅ Cold start ≤ 1.5s
- ✅ Memory usage ≤ 200 MB peak
- ✅ APK size ≤ 50 MB (per architecture)
- ✅ All 23 plugins functional
- ✅ No regressions (photo/video capture)
- ✅ Zero crashes

---

## Files Requiring Modification

### Week 1-2 (Quick Wins)
```
app/src/main/java/com/customcamera/app/engine/plugins/PluginRegistry.kt
app/src/main/java/com/customcamera/app/plugins/BarcodePlugin.kt
app/src/main/java/com/customcamera/app/plugins/QRScannerPlugin.kt
app/src/main/java/com/customcamera/app/engine/CameraEngine.kt
app/build.gradle.kts (ML Kit dependencies)
```

### Week 3-4 (Major Changes)
```
app/src/main/java/com/customcamera/app/ai/AISceneRecognitionManager.kt
app/src/main/java/com/customcamera/app/processors/HDRProcessor.kt
app/src/main/java/com/customcamera/app/ui/PhotoPreviewOverlay.kt
app/src/main/java/com/customcamera/app/engine/cache/BitmapCache.kt (NEW)
app/src/main/java/com/customcamera/app/engine/SettingsManager.kt
app/build.gradle.kts (APK splits)
```

---

## Risk Mitigation

### Low Risk Items (Week 1-2)
- ML Kit lazy loading: Only affects enabled plugins
- Debug logging: Release builds unaffected
- On-demand models: ML Kit handles downloads automatically

### Medium Risk Items (Week 3-4)
- LruCache: Requires careful memory profiling and testing
- APK splits: Build complexity, but Google Play handles distribution
- Lazy settings: Only affects non-critical UI

### High Risk Items (Sprint 3)
- R8 minification: Requires extensive ProGuard rules and testing
- Plugin reflection: PluginRegistry uses reflection (keep rules needed)
- ML Kit obfuscation: Special rules required

**Mitigation**: Start with low-risk items, build confidence, then proceed to medium/high risk

---

## Blockers & Dependencies

### External Dependencies
- Google Play Services (ML Kit model hosting)
- Android build tools (R8, APK splits)
- GitHub Actions (multi-APK CI/CD)

### Internal Dependencies
- ✅ Sprint 1 complete (v2.3.0 production ready)
- ⏳ User feedback from v2.2.11 testing
- ⏳ User approval to proceed with Sprint 2

### Current Blockers
- **None** (all analysis complete, ready to implement)

---

## Success Metrics

### Quantitative Goals
- 50-60% faster cold start (1.6-3.8s → 0.8-1.5s)
- 50% memory reduction (200-500 MB → 100-200 MB)
- 35-40% APK reduction per device (77 MB → 40-50 MB)
- Zero regressions (all existing features work)

### Qualitative Goals
- "Instant" app launch perception (<1s)
- Smoother camera preview (fewer GC pauses)
- Support for low-end devices
- Competitive with Google Camera (45 MB)

---

## Next Steps

1. ⏳ **Await v2.2.11 user feedback**
   - User completes TESTING_CHECKLIST_v2.2.11.md
   - Report results for critical MediaStore fixes

2. ⏳ **Sprint 2 Go/No-Go Decision**
   - Review user feedback
   - Prioritize items based on user needs
   - Confirm Sprint 2 timeline

3. ⏳ **Begin Sprint 2 Week 1**
   - Start with P0 (parallel isSupported checks)
   - Quick wins for immediate user impact

---

## Additional Resources

### Analysis Documents
- `memory/STARTUP_PERFORMANCE_ANALYSIS.md` - Complete startup analysis
- `BITMAP_MEMORY_ANALYSIS.md` - Memory optimization guide
- `BITMAP_HOTSPOTS_SUMMARY.txt` - Executive memory summary
- `BITMAP_ANALYSIS_INDEX.md` - Memory analysis navigation
- `APK_SIZE_ANALYSIS.md` - APK optimization roadmap
- `memory/SESSION27_SUMMARY.md` - Session 27 retrospective
- `PHASE10_PLANNING.md` - Complete Phase 10 roadmap

### Code References
- `CameraEngine.kt:89` - ProcessCameraProvider initialization
- `PluginRegistry.kt:91-94` - isSupported() sequential calls
- `AutoFocusPlugin.kt:390-412` - Expensive capability checks
- `DualCameraCompositor.kt:243` - Bitmap compositing
- `AISceneRecognitionManager.kt` - Memory leak location

---

**Document Version**: 1.0
**Created**: 2025-11-26 (Session 27)
**Status**: Ready for Sprint 2 implementation
**Next Review**: After v2.2.11 user feedback
