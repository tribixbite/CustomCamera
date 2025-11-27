#!/data/data/com.termux/files/usr/bin/bash

# Test Plugin Export Feature - Manual Testing Script
# Session 32 - Testing export/import/browser features

echo "=== Plugin Export/Import Test Script ==="
echo ""

# 1. Wake device and clear logs
echo "[1/8] Waking device and clearing logs..."
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
adb logcat -c

# 2. Launch Settings Activity
echo "[2/8] Launching Settings Activity..."
adb shell am start -n com.customcamera.app/.SettingsActivity
sleep 3

# 3. Check that all sections are created
echo "[3/8] Verifying all 11 sections created..."
adb logcat -d | grep "SettingsActivity.*Section" | tail -15

# 4. Take screenshot of Settings screen
echo "[4/8] Taking screenshot of Settings screen..."
adb shell screencap -p /sdcard/screenshot-settings-main.png
echo "Screenshot saved to: /sdcard/screenshot-settings-main.png"

# 5. Scroll down to Plugin Control section
echo "[5/8] Scrolling to Plugin Control section..."
adb shell input swipe 540 1800 540 400 500
sleep 1
adb shell input swipe 540 1800 540 400 500
sleep 1

# 6. Take screenshot after scrolling
echo "[6/8] Taking screenshot after scroll..."
adb shell screencap -p /sdcard/screenshot-settings-scrolled.png
echo "Screenshot saved to: /sdcard/screenshot-settings-scrolled.png"

# 7. Dump UI hierarchy
echo "[7/8] Dumping UI hierarchy..."
adb shell uiautomator dump
adb pull /sdcard/window_dump.xml ~/window_dump.xml >/dev/null 2>&1

# 8. Search for plugin-related buttons
echo "[8/8] Searching for plugin buttons in UI..."
echo ""
echo "--- Buttons Found ---"
cat ~/window_dump.xml | grep -o 'text="[^"]*"' | sort -u | grep -v "^text=\"\"$" | head -30

echo ""
echo "=== Test Complete ==="
echo ""
echo "Next Steps:"
echo "1. Manually review screenshots: /sdcard/screenshot-settings-*.png"
echo "2. Manually tap 'Export Plugin Configuration' button"
echo "3. Select save location in file picker"
echo "4. Verify JSON file created"
echo ""
