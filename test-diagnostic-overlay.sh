#!/bin/bash
# DiagnosticOverlay Quick Test Script
# Run this when device reconnects to execute test plan

set -e

echo "🧪 DiagnosticOverlay Integration Test"
echo "======================================"
echo ""

# Check ADB connection
echo "1️⃣ Checking ADB connection..."
if ! adb devices | grep -q "device$"; then
    echo "❌ No device connected. Connect device and try again."
    echo "   Run: adb devices"
    exit 1
fi
echo "✅ Device connected"
echo ""

# Install APK
echo "2️⃣ Installing APK v2.1.41-build.33..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "✅ APK installed"
echo ""

# Launch camera
echo "3️⃣ Launching camera..."
adb shell am start -a com.customcamera.app.TEST_CAMERA
sleep 3
echo "✅ Camera launched"
echo ""

# Instructions for manual testing
echo "📋 MANUAL TEST STEPS:"
echo "===================="
echo ""
echo "On your device:"
echo "  1. Tap the PUZZLE PIECE icon (master plugin button)"
echo "  2. Find 'Diagnostic Overlay' in the dropdown"
echo "  3. Toggle it ON"
echo "  4. Verify overlay appears showing:"
echo "     • Camera state (ID, state, mode)"
echo "     • Sensor info (gyro, accel, mag)"
echo "     • Permissions (camera, audio, vibrate)"
echo "     • Event log (recent events)"
echo ""
echo "Press ENTER when overlay is visible..."
read

# Capture screenshot
echo ""
echo "4️⃣ Capturing overlay screenshot..."
adb exec-out screencap -p > test_overlay_enabled.png
echo "✅ Screenshot saved: test_overlay_enabled.png"
echo ""

# Test PiP mode
echo "5️⃣ Testing PiP mode compatibility..."
echo "On your device:"
echo "  1. Tap the PiP button (dual camera icon)"
echo "  2. Verify overlay updates to show 'Concurrent' mode"
echo "  3. Check overlay doesn't block PiP window"
echo ""
echo "Press ENTER when in PiP mode..."
read

# Capture PiP screenshot
adb exec-out screencap -p > test_overlay_pip.png
echo "✅ PiP screenshot saved: test_overlay_pip.png"
echo ""

# Check logs
echo "6️⃣ Checking diagnostic logs..."
adb logcat -d | grep -i "diagnostic" | tail -20 > diagnostic_logs.txt
echo "✅ Logs saved: diagnostic_logs.txt"
echo ""

# Performance check
echo "7️⃣ Checking performance..."
adb logcat -d | grep -iE "frame|drop|lag" | tail -10
echo ""

# Positioning check
echo "8️⃣ UI Positioning Check..."
echo "On your device, verify ALL buttons are accessible:"
echo "  • Capture button (bottom center)"
echo "  • Gallery button (bottom left)"
echo "  • Switch camera (bottom right)"
echo "  • Flash, Night Mode, PiP (top)"
echo "  • Settings (top right)"
echo "  • Plugin dropdown"
echo ""
echo "Are all buttons accessible? (y/n): "
read positioning_ok

if [ "$positioning_ok" != "y" ]; then
    echo "⚠️ Positioning issue detected - needs fix"
else
    echo "✅ Positioning OK"
fi
echo ""

# Final screenshot
echo "9️⃣ Capturing final state..."
adb exec-out screencap -p > test_overlay_final.png
echo "✅ Final screenshot saved: test_overlay_final.png"
echo ""

# Summary
echo "📊 TEST SUMMARY"
echo "==============="
echo ""
echo "Screenshots captured:"
echo "  • test_overlay_enabled.png - Overlay active"
echo "  • test_overlay_pip.png - PiP mode"
echo "  • test_overlay_final.png - Final state"
echo ""
echo "Logs saved:"
echo "  • diagnostic_logs.txt"
echo ""
echo "Next steps:"
echo "  1. Review screenshots to verify overlay appearance"
echo "  2. Check logs for errors"
echo "  3. Update ACTIVE_TODOS.md with results"
echo ""
echo "✅ Test script complete!"
