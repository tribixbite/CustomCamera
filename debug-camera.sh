#!/bin/bash

# Automated Camera Debug Script
# Builds, installs, launches app, captures logs, and analyzes issues

set -e

echo "=========================================="
echo "🔍 Automated Camera Debug Tool"
echo "=========================================="
echo ""

PACKAGE_NAME="com.customcamera.app"
MAIN_ACTIVITY="com.customcamera.app.MainActivity"
LOG_FILE="camera_debug_$(date +%Y%m%d_%H%M%S).log"

# Step 1: Build and install
echo "📦 Step 1: Building and installing APK..."
./build-and-install.sh > /dev/null 2>&1 || {
    echo "❌ Build failed! Check build-and-install.sh output"
    exit 1
}
echo "✅ APK installed"
echo ""

# Step 2: Clear logcat
echo "🗑️  Step 2: Clearing old logs..."
adb logcat -c
echo "✅ Logs cleared"
echo ""

# Step 3: Stop any existing app instance
echo "🛑 Step 3: Stopping existing app instance..."
adb shell am force-stop $PACKAGE_NAME 2>/dev/null || true
sleep 1
echo "✅ App stopped"
echo ""

# Step 4: Start logcat capture in background
echo "📝 Step 4: Starting log capture..."
adb logcat -v time > "$LOG_FILE" &
LOGCAT_PID=$!
echo "✅ Logging to: $LOG_FILE (PID: $LOGCAT_PID)"
echo ""

# Step 5: Launch app
echo "🚀 Step 5: Launching app..."
adb shell am start -n $PACKAGE_NAME/$MAIN_ACTIVITY
sleep 2
echo "✅ App launched"
echo ""

# Step 6: Wait for camera initialization
echo "⏳ Step 6: Waiting for camera initialization (10 seconds)..."
sleep 10
echo "✅ Wait complete"
echo ""

# Step 7: Stop logcat
echo "🛑 Step 7: Stopping log capture..."
kill $LOGCAT_PID 2>/dev/null || true
sleep 1
echo "✅ Logs captured"
echo ""

# Step 8: Analyze logs
echo "=========================================="
echo "📊 LOG ANALYSIS"
echo "=========================================="
echo ""

# Extract relevant sections
echo "🔍 Extracting CameraEngine logs..."
grep -E "CameraEngine|customcamera" "$LOG_FILE" > "${LOG_FILE}.filtered" || {
    echo "⚠️  No CameraEngine logs found!"
}

# Show device info
echo ""
echo "📱 DEVICE INFO:"
echo "----------------------------------------"
grep "Device:" "${LOG_FILE}.filtered" | head -1 || echo "  Not found"
grep "Android:" "${LOG_FILE}.filtered" | head -1 || echo "  Not found"
echo ""

# Show sensors
echo "🔬 SENSOR STATUS:"
echo "----------------------------------------"
grep "Available sensors:" "${LOG_FILE}.filtered" | head -1 || echo "  Not found"
grep "Magnetometer:" "${LOG_FILE}.filtered" | head -1 || echo "  ❌ Magnetometer check not found"
grep "Accelerometer:" "${LOG_FILE}.filtered" | head -1 || echo "  Not found"
grep "Gyroscope:" "${LOG_FILE}.filtered" | head -1 || echo "  Not found"
echo ""

# Show permissions
echo "🔐 PERMISSIONS:"
echo "----------------------------------------"
grep "Permissions:" "${LOG_FILE}.filtered" -A 3 | head -4 || echo "  Not found"
echo ""

# Show camera detection
echo "📷 CAMERAS DETECTED:"
echo "----------------------------------------"
grep "Found.*cameras" "${LOG_FILE}.filtered" | head -1 || echo "  Not found"
grep "Camera.*:" "${LOG_FILE}.filtered" | grep -E "BACK|FRONT" | head -4 || echo "  No camera details found"
echo ""

# Show camera state transitions
echo "🔄 CAMERA STATE TRANSITIONS:"
echo "----------------------------------------"
grep -E "Camera state changed|Camera.*state:" "${LOG_FILE}.filtered" | tail -20 || echo "  No state changes found"
echo ""

# Show errors
echo "❌ ERRORS:"
echo "----------------------------------------"
grep -E "ERROR|FAILED|Exception" "${LOG_FILE}.filtered" | grep -v "ERROR_" | tail -10 || echo "  ✅ No errors found"
echo ""

# Critical analysis
echo "=========================================="
echo "🎯 CRITICAL ANALYSIS"
echo "=========================================="
echo ""

# Check for magnetometer
if grep -q "Magnetometer:.*NOT AVAILABLE" "${LOG_FILE}.filtered"; then
    echo "❌ CRITICAL: Magnetometer sensor NOT available!"
    echo "   This is likely causing Samsung AI Manager issues"
    echo "   Solution: App may need to handle missing magnetometer gracefully"
    echo ""
fi

# Check for permission issues
if grep -q "Camera:.*DENIED" "${LOG_FILE}.filtered"; then
    echo "❌ CRITICAL: Camera permission DENIED!"
    echo "   Solution: Grant camera permission in Android settings"
    echo ""
fi

# Check if camera opened
if grep -q "Camera OPEN" "${LOG_FILE}.filtered"; then
    echo "✅ SUCCESS: Camera opened successfully"
    echo ""
else
    echo "❌ CRITICAL: Camera never reached OPEN state"
    echo "   Last known states:"
    grep -E "Camera.*state:" "${LOG_FILE}.filtered" | tail -5
    echo ""
    echo "   Checking for specific errors..."

    if grep -q "STREAM_CONFIG" "${LOG_FILE}.filtered"; then
        echo "   → Stream configuration error detected"
    fi
    if grep -q "CAMERA_IN_USE" "${LOG_FILE}.filtered"; then
        echo "   → Camera already in use by another app"
    fi
    if grep -q "MAX_CAMERAS_IN_USE" "${LOG_FILE}.filtered"; then
        echo "   → Maximum number of cameras already in use"
    fi
    if grep -q "CAMERA_DISABLED" "${LOG_FILE}.filtered"; then
        echo "   → Camera is disabled (possibly by policy)"
    fi
    if grep -q "CAMERA_FATAL" "${LOG_FILE}.filtered"; then
        echo "   → Fatal camera error"
    fi
    echo ""
fi

# Show summary
echo "=========================================="
echo "📄 FULL LOGS"
echo "=========================================="
echo "Complete logs saved to:"
echo "  Full log:     $LOG_FILE"
echo "  Filtered log: ${LOG_FILE}.filtered"
echo ""
echo "To view full logs:"
echo "  cat $LOG_FILE"
echo ""
echo "To view filtered logs:"
echo "  cat ${LOG_FILE}.filtered"
echo ""
echo "To search for specific text:"
echo "  grep 'search_term' $LOG_FILE"
echo ""

echo "=========================================="
echo "✅ Debug analysis complete!"
echo "=========================================="
