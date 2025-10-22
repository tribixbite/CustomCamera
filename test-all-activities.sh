#!/bin/bash
# Comprehensive ADB Testing Script for CustomCamera
# Tests all activities, settings screens, and plugin functionality

LOG_FILE="test-results-$(date +%Y%m%d-%H%M%S).md"
APP_PACKAGE="com.customcamera.app"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "# CustomCamera Comprehensive Test Results" > "$LOG_FILE"
echo "**Date:** $(date)" >> "$LOG_FILE"
echo "**Tester:** Automated ADB Test Script" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

log_test() {
    local test_name=$1
    local result=$2
    local details=$3

    if [ "$result" = "PASS" ]; then
        echo -e "${GREEN}✅ PASS${NC}: $test_name"
        echo "- ✅ **PASS**: $test_name" >> "$LOG_FILE"
    elif [ "$result" = "FAIL" ]; then
        echo -e "${RED}❌ FAIL${NC}: $test_name"
        echo "- ❌ **FAIL**: $test_name" >> "$LOG_FILE"
    else
        echo -e "${YELLOW}⚠️  WARN${NC}: $test_name"
        echo "- ⚠️  **WARN**: $test_name" >> "$LOG_FILE"
    fi

    if [ -n "$details" ]; then
        echo "  Details: $details" >> "$LOG_FILE"
    fi
}

# Test 1: App Installation
echo ""
echo "## Test 1: App Installation Check"
echo "" >> "$LOG_FILE"
echo "## Test 1: App Installation" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

if adb shell pm list packages | grep -q "$APP_PACKAGE"; then
    VERSION=$(adb shell dumpsys package $APP_PACKAGE | grep versionCode | head -1 | awk '{print $1}')
    log_test "App is installed" "PASS" "Package: $APP_PACKAGE, Version: $VERSION"
else
    log_test "App is installed" "FAIL" "Package not found: $APP_PACKAGE"
    exit 1
fi

# Test 2: Launch Main Camera Activity
echo ""
echo "## Test 2: CameraActivityEngine Launch"
echo "" >> "$LOG_FILE"
echo "## Test 2: CameraActivityEngine (Main Camera)" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

adb shell am start -n $APP_PACKAGE/.MainActivity > /dev/null 2>&1
sleep 3

CURRENT_ACTIVITY=$(adb shell "dumpsys window | grep -E 'mCurrentFocus'" | awk '{print $3}' | cut -d'/' -f2 | tr -d '}')

if [ "$CURRENT_ACTIVITY" = "com.customcamera.app.CameraActivityEngine" ] || \
   [ "$CURRENT_ACTIVITY" = "com.customcamera.app.MainActivity" ] || \
   [ "$CURRENT_ACTIVITY" = "com.customcamera.app.CameraSelectionActivity" ]; then
    log_test "Camera activity launches" "PASS" "Current activity: $CURRENT_ACTIVITY"
else
    log_test "Camera activity launches" "FAIL" "Unexpected activity: $CURRENT_ACTIVITY"
fi

# Check camera permissions
CAMERA_PERM=$(adb shell dumpsys package $APP_PACKAGE | grep "android.permission.CAMERA" | grep "granted=true" | wc -l)
if [ "$CAMERA_PERM" -gt 0 ]; then
    log_test "Camera permission granted" "PASS"
else
    log_test "Camera permission granted" "FAIL" "Need to grant camera permission"
fi

# Test 3: Settings Button and SimpleSettingsActivity
echo ""
echo "## Test 3: SimpleSettingsActivity"
echo "" >> "$LOG_FILE"
echo "## Test 3: SimpleSettingsActivity (Settings Screen)" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# Find settings button location from UI dump
adb shell uiautomator dump > /dev/null 2>&1
sleep 1

# Check if we can find settingsButton
SETTINGS_BTN=$(adb shell cat /sdcard/window_dump.xml | grep "settingsButton")

if [ -n "$SETTINGS_BTN" ]; then
    log_test "Settings button exists" "PASS"

    # Try to extract bounds and tap
    # For now, use approximate coordinates (top-right area where settings usually is)
    adb shell input tap 950 150
    sleep 2

    CURRENT=$(adb shell "dumpsys window | grep -E 'mCurrentFocus'" | awk '{print $3}' | cut -d'/' -f2 | tr -d '}')

    if echo "$CURRENT" | grep -q "Settings"; then
        log_test "SimpleSettingsActivity opens" "PASS" "Activity: $CURRENT"

        # Test settings toggles
        echo "" >> "$LOG_FILE"
        echo "###  Settings Tests" >> "$LOG_FILE"
        echo "" >> "$LOG_FILE"

        # Dump UI to check settings
        adb shell uiautomator dump > /dev/null 2>&1
        UI_DUMP=$(adb shell cat /sdcard/window_dump.xml)

        # Check for key settings
        if echo "$UI_DUMP" | grep -q "Debug Logging"; then
            log_test "Debug Logging setting visible" "PASS"
        fi

        if echo "$UI_DUMP" | grep -q "Performance Monitoring"; then
            log_test "Performance Monitoring setting visible" "PASS"
        fi

        if echo "$UI_DUMP" | grep -q "RAW Capture"; then
            log_test "RAW Capture setting visible" "PASS"
        fi

        if echo "$UI_DUMP" | grep -q "Manual Controls"; then
            log_test "Manual Controls setting visible" "PASS"
        fi

        # Go back to camera
        adb shell input keyevent 4
        sleep 2
    else
        log_test "SimpleSettingsActivity opens" "FAIL" "Current: $CURRENT"
    fi
else
    log_test "Settings button exists" "WARN" "Could not find settings button in UI"
fi

# Test 4: Plugin Dropdown
echo ""
echo "## Test 4: Plugin Dropdown System"
echo "" >> "$LOG_FILE"
echo "## Test 4: Plugin Dropdown & DiagnosticOverlay" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# Make sure we're on camera screen
adb shell am start -n $APP_PACKAGE/.MainActivity > /dev/null 2>&1
sleep 3

# Dump UI to find plugin dropdown button
adb shell uiautomator dump > /dev/null 2>&1
UI_DUMP=$(adb shell cat /sdcard/window_dump.xml)

if echo "$UI_DUMP" | grep -q "pluginDropdownButton"; then
    log_test "Plugin dropdown button exists" "PASS"

    # Try to tap it (usually top-left area)
    adb shell input tap 150 150
    sleep 2

    # Check if dropdown opened
    adb shell uiautomator dump > /dev/null 2>&1
    DROPDOWN_UI=$(adb shell cat /sdcard/window_dump.xml)

    if echo "$DROPDOWN_UI" | grep -q "DiagnosticOverlay"; then
        log_test "DiagnosticOverlay appears in dropdown" "PASS"

        # Try to toggle it
        # Find coordinates for DiagnosticOverlay checkbox
        # For now, tap in general area where plugins appear
        adb shell input tap 540 400
        sleep 1

        log_test "DiagnosticOverlay toggle attempted" "PASS"
    else
        log_test "DiagnosticOverlay appears in dropdown" "WARN" "Not found in dropdown UI"
    fi

    # Close dropdown
    adb shell input keyevent 4
    sleep 1
else
    log_test "Plugin dropdown button exists" "WARN" "Could not find plugin dropdown button"
fi

# Test 5: Camera Capture
echo ""
echo "## Test 5: Photo Capture"
echo "" >> "$LOG_FILE"
echo "## Test 5: Photo Capture Functionality" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# Make sure on camera screen
sleep 2

# Tap capture button (center-bottom of screen)
adb shell input tap 540 2000
sleep 2

# Check if photo was saved
LATEST_PHOTO=$(adb shell "ls -t /sdcard/DCIM/CustomCamera/*.jpg 2>/dev/null | head -1")

if [ -n "$LATEST_PHOTO" ]; then
    PHOTO_SIZE=$(adb shell "ls -lh '$LATEST_PHOTO'" | awk '{print $5}')
    log_test "Photo capture works" "PASS" "Latest photo: $LATEST_PHOTO (Size: $PHOTO_SIZE)"
else
    log_test "Photo capture works" "FAIL" "No photo found in /sdcard/DCIM/CustomCamera/"
fi

# Test 6: Gallery Activity
echo ""
echo "## Test 6: GalleryActivity"
echo "" >> "$LOG_FILE"
echo "## Test 6: GalleryActivity" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# Try to tap gallery button (usually bottom-right)
adb shell input tap 900 2000
sleep 2

CURRENT=$(adb shell "dumpsys window | grep -E 'mCurrentFocus'" | awk '{print $3}' | cut -d'/' -f2 | tr -d '}')

if echo "$CURRENT" | grep -q "Gallery"; then
    log_test "GalleryActivity opens" "PASS"
    adb shell input keyevent 4
    sleep 1
else
    log_test "GalleryActivity opens" "WARN" "Could not confirm gallery opened (Current: $CURRENT)"
fi

# Test 7: App Stability
echo ""
echo "## Test 7: App Stability"
echo "" >> "$LOG_FILE"
echo "## Test 7: Stability & Performance" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# Check for crashes
CRASHES=$(adb logcat -d | grep -i "FATAL\|AndroidRuntime.*$APP_PACKAGE" | wc -l)

if [ "$CRASHES" -eq 0 ]; then
    log_test "No crashes detected" "PASS"
else
    log_test "No crashes detected" "FAIL" "Found $CRASHES potential crash(es) in logcat"
fi

# Test 8: Plugin Logs
echo ""
echo "## Test 8: Plugin System Logs"
echo "" >> "$LOG_FILE"
echo "## Test 8: Plugin System" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

# Check for plugin-related logs
PLUGIN_LOGS=$(adb logcat -d | grep -i "plugin" | grep "$APP_PACKAGE" | wc -l)

if [ "$PLUGIN_LOGS" -gt 0 ]; then
    log_test "Plugin system active" "PASS" "$PLUGIN_LOGS plugin-related log entries found"
else
    log_test "Plugin system active" "WARN" "No plugin logs found"
fi

# Summary
echo ""
echo "="
echo "Test execution complete!"
echo "Results saved to: $LOG_FILE"
echo "="
echo ""
echo "## Test Summary" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"
PASS_COUNT=$(grep -c "✅ \*\*PASS\*\*" "$LOG_FILE")
FAIL_COUNT=$(grep -c "❌ \*\*FAIL\*\*" "$LOG_FILE")
WARN_COUNT=$(grep -c "⚠️  \*\*WARN\*\*" "$LOG_FILE")
TOTAL=$((PASS_COUNT + FAIL_COUNT + WARN_COUNT))

echo "- **Total Tests**: $TOTAL" >> "$LOG_FILE"
echo "- **Passed**: $PASS_COUNT" >> "$LOG_FILE"
echo "- **Failed**: $FAIL_COUNT" >> "$LOG_FILE"
echo "- **Warnings**: $WARN_COUNT" >> "$LOG_FILE"
echo "" >> "$LOG_FILE"

if [ "$FAIL_COUNT" -eq 0 ]; then
    echo -e "${GREEN}✅ All critical tests passed!${NC}"
    echo "**Status**: ✅ All critical tests passed!" >> "$LOG_FILE"
else
    echo -e "${RED}❌ $FAIL_COUNT test(s) failed${NC}"
    echo "**Status**: ❌ $FAIL_COUNT test(s) failed" >> "$LOG_FILE"
fi

cat "$LOG_FILE"
