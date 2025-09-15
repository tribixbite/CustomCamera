# CustomCamera - Modern Kotlin Camera App

## Overview
A standalone modern camera application built with Kotlin, featuring Samsung/Google-style floating UI, robust camera selection, and graceful error handling for broken cameras.

## Features
- 📸 **Modern SOTA UI** - Samsung/Google-style floating controls
- 🎯 **Camera ID Selection** - Choose specific camera (0, 1, 2+)
- 🛡️ **Graceful Error Handling** - Handles broken cameras without crashes
- ⚡ **Flash Control** - Toggle flash with visual feedback
- 🔄 **Camera Switching** - Runtime switching between available cameras
- 📱 **Material3 Design** - Modern Android design system
- 🎬 **Smooth Animations** - Fluid button interactions

## Installation
```bash
cd ~/git/swype/CustomCamera
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Current Status
- ✅ **Core Functionality**: Working camera app with modern UI
- ⚠️ **Known Issue**: Camera ID selection not being respected (investigating)
- 🔧 **Debug Mode**: Enhanced logging for camera selection debugging

## Architecture
- **Kotlin** - Modern Android development
- **CameraX** - Google's camera library
- **ViewBinding** - Type-safe view access
- **Material3** - Latest design system
- **Activity Result API** - Modern permission handling

## Quick Start
1. Launch app → Shows simple launcher
2. Tap "Open Camera" → Camera selection screen
3. Select desired camera → Auto-highlighted for UX
4. Tap "Continue" → Full camera interface
5. Use floating controls for capture, flash, switching

## Development
See `CLAUDE.md` for complete development documentation and `memory/` folder for detailed task tracking and architecture notes.

---
*Modern Camera App - Built with Claude Code*