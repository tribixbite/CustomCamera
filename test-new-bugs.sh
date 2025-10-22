#!/data/data/com.termux/files/usr/bin/bash

# Test script for NEW_BUGS_2025-10-22.md
# Tests PiP photo capture and video recording issues

echo "==================================="
echo "CustomCamera Bug Testing Script"
echo "==================================="
echo ""
echo "This script will guide you through testing 2 bugs:"
echo "  1. PiP photo capture (black feeds)"
echo "  2. Video recording (sampling rate error)"
echo ""
echo "Make sure the app is open before starting."
echo "Press Enter to continue..."
read

# Test 1: Regular photo capture (baseline)
echo ""
echo "=== TEST 1: Regular Photo Capture (Baseline) ==="
echo "1. Make sure PiP mode is OFF (no secondary camera preview)"
echo "2. Tap the CAPTURE button (large circular button)"
echo "3. Wait for capture to complete"
echo "Press Enter when done..."
read

echo "Checking if photo was saved..."
PHOTO_COUNT=$(adb shell run-as com.customcamera.app ls files/ 2>/dev/null | grep -c "CAMERA_ENGINE")
echo "Found $PHOTO_COUNT photos in internal storage"

if [ $PHOTO_COUNT -gt 0 ]; then
    echo "✅ Regular photo capture works"
    echo ""
    echo "Latest photo:"
    adb shell run-as com.customcamera.app ls -lt files/ | grep CAMERA_ENGINE | head -1
else
    echo "❌ No photos found - regular capture may be broken"
fi

# Test 2: PiP photo capture
echo ""
echo "=== TEST 2: PiP Photo Capture ==="
echo "1. Tap the PiP button (top row, right side)"
echo "2. Verify you see TWO camera previews (main + small PiP)"
echo "3. Tap the CAPTURE button"
echo "4. Watch carefully: Do the camera feeds go BLACK?"
echo "5. Does a loading bar appear?"
echo "Press Enter when done..."
read

adb logcat -c
sleep 1

echo ""
echo "Checking logs for PiP capture..."
adb logcat -d | grep -i "dual.*camera\|composite\|pip.*photo" | tail -20
echo ""

echo "Latest photo (should be PiP composite):"
adb shell run-as com.customcamera.app ls -lt files/ | grep CAMERA_ENGINE | head -1

echo ""
echo "Did the camera feeds go black during capture? (y/n)"
read BLACK_FEEDS
echo "Was a loading bar visible? (y/n)"
read LOADING_BAR
echo "Did a photo file get created? (y/n)"
read PHOTO_CREATED

# Test 3: Video recording with PiP OFF
echo ""
echo "=== TEST 3: Video Recording (PiP OFF) ==="
echo "1. Disable PiP mode (tap PiP button again if still on)"
echo "2. Verify you see only ONE camera preview"
echo "Press Enter when PiP is off..."
read

adb logcat -c
sleep 1

echo "3. Now tap the VIDEO RECORDING button (top row)"
echo "4. Note the EXACT error message shown"
echo "Press Enter when done..."
read

echo ""
echo "Capturing video error logs..."
adb logcat -d | grep -E "video\|recording\|sampling\|audio\|Failed to start" | tail -30 > /sdcard/video-error.log

echo ""
echo "Full error message from toast:"
adb logcat -d | grep "Failed to start recording" | tail -5

echo ""
echo "What was the exact error message shown? (type it):"
read VIDEO_ERROR

# Test 4: Video recording with PiP ON (should be blocked)
echo ""
echo "=== TEST 4: Video Recording (PiP ON) - Should Fail ==="
echo "1. Enable PiP mode again"
echo "2. Tap the VIDEO RECORDING button"
echo "Press Enter when done..."
read

echo ""
echo "Expected message: 'Video recording unavailable in PiP mode'"
echo ""
adb logcat -d | grep "concurrent camera mode" | tail -5

# Summary
echo ""
echo "==================================="
echo "TEST RESULTS SUMMARY"
echo "==================================="
echo ""
echo "Test 1 - Regular Photo: $PHOTO_COUNT photos found"
echo "Test 2 - PiP Photo:"
echo "  - Black feeds during capture: $BLACK_FEEDS"
echo "  - Loading bar visible: $LOADING_BAR"
echo "  - Photo created: $PHOTO_CREATED"
echo "Test 3 - Video (PiP OFF):"
echo "  - Error message: $VIDEO_ERROR"
echo "Test 4 - Video (PiP ON): Check above for 'concurrent camera mode' message"
echo ""
echo "Detailed logs saved to: /sdcard/video-error.log"
echo ""
echo "Please share these results!"
echo "==================================="
