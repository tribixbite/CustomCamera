# CustomCamera - Complete Documentation Index

**Last Updated**: 2025-11-27
**Version**: 2.4.1 (build 42)
**Status**: Production-Ready (All 23 Plugin Specs Complete ✅)

## 📚 Quick Navigation

### Essential Documents
- [README.md](../README.md) - Project overview and getting started
- [CLAUDE.md](../CLAUDE.md) - Claude Code configuration and workflow
- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture and design patterns

### Project Status
- [ACTIVE_TODOS.md](../memory/ACTIVE_TODOS.md) - Current priorities and active work
- [PROJECT_QUALITY_AUDIT.md](../PROJECT_QUALITY_AUDIT.md) - Quality assessment and recommendations

---

## 🏗️ Architecture & Design

### Core Systems
- [Core Camera System](specs/core-camera-system.md) - CameraX integration, lifecycle, configuration
- [Plugin System](specs/plugin-system.md) - Plugin architecture, registration, lifecycle
- [Settings System](specs/systems/settings-system.md) ⭐ NEW - StateFlow reactive settings
- [Engine Architecture](specs/systems/camera-engine.md) ⭐ NEW - CameraEngine coordination

### System Specifications
- [UX Interaction System](specs/ux-interaction-system.md) - Gestures, haptics, user feedback
- [Testing Infrastructure](specs/testing-infrastructure.md) - Test strategy, automation
- [CI/CD Automation](specs/cicd-automation.md) - Build pipeline, release process

---

## 🔌 Plugin Documentation (23/23 Plugins Complete ✅)

### Control Plugins (7)
1. [AutoFocusPlugin](specs/plugins/autofocus-plugin.md) ⭐ NEW - Continuous AF, tap-to-focus
2. [ExposureControlPlugin](specs/plugins/exposure-control-plugin.md) ⭐ NEW - EV compensation, lock
3. [ManualFocusPlugin](specs/plugins/manual-focus-plugin.md) ⭐ NEW - Focus distance control
4. [ProControlsPlugin](specs/plugins/pro-controls-plugin.md) ⭐ NEW - ISO, shutter, manual controls
5. [NightModePlugin](specs/plugins/night-mode-plugin.md) ⭐ NEW - Low-light optimization
6. [DualCameraPiPPlugin](specs/plugins/dual-camera-pip-plugin.md) ⭐ NEW - Concurrent cameras
7. [AdvancedVideoRecordingPlugin](specs/plugins/advanced-video-plugin.md) ⭐ NEW - Video quality, stabilization

### UI & Overlay Plugins (5)
8. [GridOverlayPlugin](specs/plugins/grid-overlay-plugin.md) ⭐ NEW - Composition grids
9. [HistogramPlugin](specs/plugins/histogram-plugin.md) ⭐ NEW - Exposure histogram
10. [ScanningOverlayPlugin](specs/plugins/scanning-overlay-plugin.md) ⭐ NEW - QR/barcode UI
11. [DiagnosticOverlayPlugin](specs/plugins/diagnostic-overlay-plugin.md) ⭐ NEW - Debug info
12. [CropPlugin](specs/plugins/crop-plugin.md) ⭐ NEW - Pre-shot crop

### Analysis & Processing Plugins (6)
13. [CameraInfoPlugin](specs/plugins/camera-info-plugin.md) ⭐ NEW - Frame statistics
14. [ExposureAnalysisPlugin](specs/plugins/exposure-analysis-plugin.md) ⭐ NEW - Exposure analysis
15. [SharpnessAnalysisPlugin](specs/plugins/sharpness-analysis-plugin.md) ⭐ NEW - Focus quality
16. [MotionDetectionPlugin](specs/plugins/motion-detection-plugin.md) ⭐ NEW - Motion triggers
17. [BarcodePlugin](specs/plugins/barcode-plugin.md) ⭐ NEW - Multi-format scanning
18. [QRScannerPlugin](specs/plugins/qr-scanner-plugin.md) ⭐ NEW - QR code scanning

### AI-Powered Plugins (3)
19. [SmartScenePlugin](specs/plugins/smart-scene-plugin.md) ⭐ NEW - Scene classification
20. [ObjectDetectionPlugin](specs/plugins/object-detection-plugin.md) ⭐ NEW - Object recognition
21. [SmartAdjustmentsPlugin](specs/plugins/smart-adjustments-plugin.md) ⭐ NEW - AI optimization

### Advanced Capture Plugins (2)
22. [RAWCapturePlugin](specs/plugins/raw-capture-plugin.md) ⭐ NEW - DNG/RAW format
23. [HDRPlugin](specs/plugins/hdr-plugin.md) ⭐ NEW - Multi-exposure HDR

---

## 📱 Feature Documentation

### Advanced Features
- [Advanced Capture Features](specs/advanced-capture-features.md) - HDR, RAW, Night Mode
- [AI-Powered Features](specs/ai-powered-features.md) - ML Kit integration, scene detection
- [Video Features](specs/features/video-system.md) ⭐ NEW - Recording, stabilization
- [Dual Camera System](specs/features/dual-camera-pip.md) ⭐ NEW - PiP implementation

### User Experience
- [Gesture System](specs/features/gesture-controls.md) ⭐ NEW - Multi-tap, pinch, long-press
- [Haptic Feedback](specs/features/haptic-system.md) ⭐ NEW - Vibration patterns
- [Settings UI](specs/features/settings-ui.md) ⭐ NEW - Configuration interface

---

## 🧪 Testing & Quality

### Testing Documentation
- [Device Testing Checklist](../DEVICE_TESTING_CHECKLIST.md) - Manual testing procedures
- [Settings Testing](../memory/SETTINGS_TESTING_CHECKLIST.md) - Settings verification
- [Test Infrastructure](specs/testing-infrastructure.md) - Automated testing setup
- [Test Results](archive/test-results/) - Historical test reports

### Quality Assurance
- [Quality Improvements Summary](../QUALITY_IMPROVEMENTS_SUMMARY.md) - Quality initiatives
- [Plugin Audit Report](archive/PLUGIN_AUDIT_REPORT.md) - Plugin implementation audit
- [Bug Reports](bugs/) - Issue tracking and fixes

---

## 📖 Session History & Progress

### Recent Sessions (2025)
- [2025-11-06: Settings System Complete](sessions/SESSION_2025-11-06_SETTINGS_SYSTEM.md) ⭐ LATEST - StateFlow migration
- [2025-11-04: Quality Improvements](sessions/SESSION_2025-11-04_QUALITY_IMPROVEMENTS.md) - Documentation organization
- [2025-10-23: Video UI Complete](sessions/SESSION_2025-10-23_SUMMARY.md) - Material3 video controls
- [All Sessions](sessions/) - Complete session history

### Implementation Summaries
- [Settings Fixes Summary](../SETTINGS_FIXES_SUMMARY.md) - Settings system overhaul
- [Build System Upgrade](../BUILD_SYSTEM_UPGRADE_SUMMARY.md) - CameraX 1.5.0, Kotlin 2.1.20
- [Phase 8 Summary](../PHASE8_SUMMARY.md) - Debug infrastructure

---

## 🔧 Development Guides

### Setup & Configuration
- [Build Guide](../CLAUDE.md#build-commands) - Gradle commands, build configuration
- [ADB Testing Guide](guides/ADB_TESTING_GUIDE.md) - Device testing procedures
- [Manual Testing Guide](MANUAL_TESTING_GUIDE.md) - UI/UX testing

### Development Workflows
- [Plugin Development](specs/plugin-system.md#creating-plugins) - How to create plugins
- [Settings Development](../SETTINGS_ARCHITECTURE.md#migration-checklist) - Adding new settings
- [Testing Workflow](specs/testing-infrastructure.md) - Test creation and execution

### Specialized Guides
- [Video Stabilization Guide](../VIDEO_STABILIZATION_GUIDE.md) - Stabilization modes
- [Conference Demo Guide](../CONFERENCE_DEMO_GUIDE.md) - Demo showcase features
- [Diagnostic Overlay Test Plan](../DIAGNOSTIC_OVERLAY_TEST_PLAN.md) - Debug overlay testing

---

## 🐛 Issues & Bug Tracking

### Bug Reports
- [Bug Report Template](bugs/BUG_REPORT_TEMPLATE.md) ⭐ NEW - Issue reporting format
- [Active Issues](bugs/ACTIVE_ISSUES.md) ⭐ NEW - Current bugs
- [Fixed Issues](bugs/FIXED_ISSUES.md) ⭐ NEW - Resolved bugs

### Historical Bug Fixes
- [Camera Fix Forensics](archive/CAMERA_FIX_FORENSICS.md) - System-wide camera fix
- [Bug Fixes 2025-10-21](archive/BUG_FIXES_2025-10-21.md) - Historical fixes
- [Archived Bug Reports](archive/bugs/) - Obsolete reports

---

## 🎯 Planning & Roadmap

### Active Planning
- [Active TODOs](../memory/ACTIVE_TODOS.md) - Current priorities
- [Master Task List](../memory/todo.md) - Comprehensive task tracking
- [Missing Settings Audit](../memory/MISSING_SETTINGS_AUDIT.md) - Settings gaps
- [Plugin UI Audit](../memory/PLUGIN_UI_AUDIT.md) - Plugin UX analysis

### Future Enhancements
- [Future Features](specs/features/future-enhancements.md) ⭐ NEW - Planned improvements
- [Technical Debt](specs/systems/technical-debt.md) ⭐ NEW - Refactoring opportunities

---

## 📐 Architecture Deep Dives

### Core Architecture
- [CameraEngine Design](specs/systems/camera-engine.md) ⭐ NEW - Engine internals
- [Plugin Architecture](specs/plugin-system.md) - Plugin system design
- [Settings Architecture](../SETTINGS_ARCHITECTURE.md) - StateFlow reactive system
- [Provider Pattern](specs/systems/provider-pattern.md) ⭐ NEW - Plugin registration

### Data Flow
- [Image Processing Pipeline](specs/systems/image-pipeline.md) ⭐ NEW - Frame processing
- [State Management](specs/systems/state-management.md) ⭐ NEW - StateFlow patterns
- [Memory Management](specs/systems/memory-management.md) ⭐ NEW - Resource cleanup

---

## 🔍 Reference Documentation

### API Reference
- [CameraEngine API](specs/systems/camera-engine.md#api-reference) - Engine interface
- [Plugin API](specs/plugin-system.md#plugin-api) - Plugin interfaces
- [Settings API](../SETTINGS_ARCHITECTURE.md#usage-patterns) - Settings access

### Code Conventions
- [Kotlin Style Guide](specs/systems/kotlin-conventions.md) ⭐ NEW - Code standards
- [Architecture Patterns](specs/systems/architecture-patterns.md) ⭐ NEW - Design patterns
- [Testing Conventions](specs/systems/testing-conventions.md) ⭐ NEW - Test standards

---

## 📊 Metrics & Analytics

### Build Information
- **Version**: 2.1.42-build.33
- **APK Size**: 76MB
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Kotlin**: 2.1.20
- **CameraX**: 1.5.0
- **AGP**: 8.6.0

### Code Statistics
- **Total Plugins**: 23/23 (100% complete)
- **Settings**: 16/16 reactive (100% StateFlow)
- **Test Files**: 8 test suites, 216+ tests
- **Documentation Files**: 60+ markdown files

---

## 🔗 External Resources

### Official Documentation
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [CameraX 1.5.0 Release Notes](https://developer.android.com/jetpack/androidx/releases/camera#1.5.0)
- [ML Kit Documentation](https://developers.google.com/ml-kit)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Material Design 3](https://m3.material.io/)

### GitHub Resources
- [Repository](https://github.com/tribixbite/CustomCamera)
- [Issues](https://github.com/tribixbite/CustomCamera/issues)
- [Releases](https://github.com/tribixbite/CustomCamera/releases)
- [CI/CD Workflows](../.github/workflows/)

---

## 📝 Document Status Legend

- ⭐ NEW - Created in this documentation update
- ✅ Complete - Fully documented and reviewed
- 🚧 In Progress - Being actively updated
- 📅 Planned - Scheduled for creation
- 🗄️ Archived - Historical reference only

---

## 🆘 Getting Help

### Documentation Issues
If you find missing, outdated, or incorrect documentation:
1. Check [ACTIVE_TODOS.md](../memory/ACTIVE_TODOS.md) for known gaps
2. Review [SESSION_HISTORY.md](SESSION_HISTORY.md) for context
3. File an issue on GitHub with the `documentation` label

### Contributing to Docs
1. Use [SPEC_TEMPLATE.md](specs/SPEC_TEMPLATE.md) for new specifications
2. Follow existing documentation structure and formatting
3. Update this TABLE_OF_CONTENTS.md when adding new documents
4. Link related documents bidirectionally

---

**Maintained By**: Claude Code
**Documentation Standard**: Comprehensive spec-driven development
**Last Audit**: 2025-11-12
**Plugin Specs**: 23/23 Complete (100%)
