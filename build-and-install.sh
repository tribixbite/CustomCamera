#!/data/data/com.termux/files/usr/bin/bash

# CustomCamera Build and Install Script for Termux
# Builds the app with automatic version increment and multi-tier installation
# Usage: ./build-and-install.sh [clean]

set -e

BUILD_TYPE="debug"
CLEAN_BUILD="${1:-}"
PACKAGE_NAME="com.customcamera.app"

echo "=== CustomCamera Build and Install Tool ==="
echo "Building $BUILD_TYPE APK with multi-tier auto-install"
echo

# Function to test if ADB is working
test_adb_connection() {
    local timeout=3

    # Quick test - if this succeeds, ADB is working and we can skip wireless setup
    if timeout "$timeout" adb devices >/dev/null 2>&1; then
        local devices=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
        if [ "$devices" -gt 0 ]; then
            return 0  # ADB working, skip wireless connect
        fi
    fi
    return 1  # Need wireless connect
}

# Smart ADB wireless connection with optimized port scanning
connect_adb_wireless() {
    local host_ip="$1"

    # Save shell's errexit state
    case $- in *e*) was_e=1;; esac
    set +e

    # Determine host IP
    if [ -n "$host_ip" ]; then
        HOST="$host_ip"
    else
        # Try to get wlan0 IP first
        HOST=$(ifconfig 2>/dev/null | awk '/wlan0/{getline; if(/inet /) print $2}')

        # Fallback to any non-loopback interface
        if [ -z "$HOST" ]; then
            HOST=$(ifconfig 2>/dev/null | awk '/inet / && !/127.0.0.1/{print $2; exit}')
        fi
    fi

    if [ -z "$HOST" ]; then
        echo "❌ Could not determine device IP address"
        [ -n "$was_e" ] && set -e
        return 1
    fi

    echo "📱 Scanning for ADB on host: $HOST"

    # Disconnect any existing connections
    adb disconnect -a >/dev/null 2>&1

    # Optimized port list - common ports first, reverse order for speed
    # Most ADB wireless implementations use ports in 30000-50000 range
    PORTS="5555"

    # Fast port discovery using nmap if available
    if command -v nmap &>/dev/null; then
        echo "   Quick scanning high ports..."
        # Scan in reverse order - newer ADB tends to use higher ports
        # Use timeout to prevent hanging
        SCANNED_PORTS=$(timeout 10 nmap -p 45000-50000,40000-44999,35000-39999,30000-34999 --open -T4 --host-timeout 2s "$HOST" 2>/dev/null | \
            grep "^[0-9]" | grep "/tcp open" | cut -d'/' -f1 | sort -nr | head -10)

        if [ -n "$SCANNED_PORTS" ]; then
            # Prepend scanned ports (reverse order for speed)
            PORTS="$(echo $SCANNED_PORTS | tr '\n' ' ') $PORTS"
            echo "   Found open ports: $(echo $SCANNED_PORTS | tr '\n' ' ')"
        fi
    else
        echo "   Note: Install nmap for faster scanning: pkg install nmap"
        # Fallback: try common high ports in reverse order
        PORTS="45555 44444 43210 42000 41000 40000 37000 35000 33333 30000 $PORTS"
    fi

    # Try to connect to each port (fast timeout)
    for port in $PORTS; do
        echo -n "   Trying $HOST:$port... "

        # Fast connection attempt with timeout
        if timeout 2 adb connect "$HOST:$port" >/dev/null 2>&1; then
            # Quick verification with shorter retry
            for i in 1 2; do
                sleep 0.3
                if adb devices | grep -q "^$HOST:$port[[:space:]]*device"; then
                    echo "✅ connected!"
                    export ADB_DEVICE="$HOST:$port"
                    [ -n "$was_e" ] && set -e
                    return 0
                fi
            done
            echo "⚠️  failed to verify"
            adb disconnect "$HOST:$port" >/dev/null 2>&1
        else
            echo "❌ no response"
        fi
    done

    echo "❌ No working ADB port found on $HOST"
    [ -n "$was_e" ] && set -e
    return 1
}

# Step 1: Check prerequisites
echo "Step 1: Checking prerequisites..."

if [ ! -f "./gradlew" ]; then
    echo "❌ gradlew not found. Make sure you're in the project root directory."
    exit 1
fi

# ADB is optional - will try other installation methods if not available
if ! command -v adb &>/dev/null; then
    echo "ℹ️  ADB not found (install with: pkg install android-tools)"
    echo "   Will use alternative installation methods"
fi

# Step 2: Update version and clean if requested
echo "Step 2: Updating version numbers..."

# Increment patch version for this build
VERSION_FILE="app/version.properties"
if [ -f "$VERSION_FILE" ]; then
    # Read current version
    CURRENT_PATCH=$(grep "VERSION_PATCH=" "$VERSION_FILE" | cut -d'=' -f2)
    NEW_PATCH=$((CURRENT_PATCH + 1))

    # Update patch version
    sed -i "s/VERSION_PATCH=.*/VERSION_PATCH=$NEW_PATCH/" "$VERSION_FILE"

    # Show version info
    MAJOR=$(grep "VERSION_MAJOR=" "$VERSION_FILE" | cut -d'=' -f2)
    MINOR=$(grep "VERSION_MINOR=" "$VERSION_FILE" | cut -d'=' -f2)
    CODE=$(grep "VERSION_CODE=" "$VERSION_FILE" | cut -d'=' -f2)
    echo "📱 Version: $MAJOR.$MINOR.$NEW_PATCH-build.$CODE"
else
    echo "⚠️  version.properties not found, creating default..."
    echo "VERSION_MAJOR=2" > "$VERSION_FILE"
    echo "VERSION_MINOR=0" >> "$VERSION_FILE"
    echo "VERSION_PATCH=1" >> "$VERSION_FILE"
    echo "VERSION_CODE=1" >> "$VERSION_FILE"
fi

if [ "$CLEAN_BUILD" = "clean" ]; then
    echo "Cleaning previous build..."
    ./gradlew clean
else
    echo "Skipping clean (use 'clean' argument to force clean build)"
fi

# Step 3: Build the APK
echo "Step 3: Building $BUILD_TYPE APK..."
echo "This may take a few minutes on first run..."

if ./gradlew assembleDebug; then
    echo "✅ Build successful!"
else
    echo "❌ Build failed!"
    exit 1
fi

# Step 4: Find the latest APK
echo "Step 4: Locating APK file..."

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at expected location: $APK_PATH"
    echo "Searching for APK files..."
    find . -name "*.apk" -type f -newer /tmp -o -name "*.apk" -type f 2>/dev/null | head -5
    exit 1
fi

echo "✅ APK found: $APK_PATH"
ls -lh "$APK_PATH"

# Step 5: Multi-tier installation methods
echo "Step 5: Installing APK (multi-tier auto-install)..."
echo

APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
echo "✅ APK ready: $APK_SIZE"
echo

# Installation success flag
INSTALLED=false

# Method 1: termux-open (Android Package Installer) - Most reliable
echo "Method 1: Android Package Installer (termux-open)..."
if command -v termux-open &>/dev/null; then
    echo "  Opening package installer..."
    # Use timeout to prevent hanging - termux-open should return quickly
    if timeout 5 termux-open "$APK_PATH" 2>/dev/null; then
        echo "  ✅ Package installer opened!"
        echo
        echo "📱 Complete installation in Android UI:"
        echo "  1. Tap 'Install' button"
        echo "  2. Wait for installation"
        echo "  3. Grant camera permissions when prompted"
        echo "  4. Launch 'Custom Camera' from app drawer"
        INSTALLED=true
    else
        echo "  ⚠️  termux-open timed out or failed, trying next method..."
    fi
else
    echo "  ⚠️  termux-open not available (install termux-api)"
fi
echo

# Exit if already installed
if [ "$INSTALLED" = true ]; then
    # Still try to copy to backup locations for easy updates
    echo "📦 Creating backup copies..."
    mkdir -p /sdcard/CustomCamera 2>/dev/null && cp "$APK_PATH" /sdcard/CustomCamera/latest-debug.apk 2>/dev/null && echo "  ✓ /sdcard/CustomCamera/latest-debug.apk"
    exit 0
fi

# Method 2: ADB (local or wireless)
echo "Method 2: ADB Installation..."
if command -v adb &>/dev/null; then
    # Quick test - if ADB already has connected devices
    if test_adb_connection; then
        echo "  📱 ADB device already connected"
        echo "  Uninstalling old version..."
        adb uninstall "$PACKAGE_NAME" 2>/dev/null || true

        echo "  Installing new APK..."
        if adb install -r "$APK_PATH"; then
            echo
            echo "✅ APK INSTALLED SUCCESSFULLY via ADB!"
            echo "CustomCamera is now on your device."
            echo
            echo "📱 To launch: Find 'Custom Camera' in your app drawer"
            INSTALLED=true
        else
            echo "  ⚠️  ADB install failed"
        fi
    else
        echo "  🔍 No ADB device connected, trying wireless..."
        if connect_adb_wireless; then
            echo "  🔗 Connected to $ADB_DEVICE"

            # Get device info
            MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
            ANDROID=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
            echo "  📱 Device: $MODEL (Android $ANDROID)"

            echo "  Uninstalling old version..."
            adb uninstall "$PACKAGE_NAME" 2>/dev/null || true

            echo "  Installing new APK..."
            if adb install -r "$APK_PATH"; then
                echo
                echo "✅ APK INSTALLED SUCCESSFULLY via ADB Wireless!"
                echo "CustomCamera is now on your device."
                echo
                echo "📱 To launch: Find 'Custom Camera' in your app drawer"
                INSTALLED=true
            else
                echo "  ⚠️  ADB wireless install failed"
            fi
        else
            echo "  ⚠️  Could not connect via ADB wireless"
        fi
    fi
else
    echo "  ⚠️  ADB not installed (install: pkg install android-tools)"
fi
echo

# Exit if installed via ADB
if [ "$INSTALLED" = true ]; then
    exit 0
fi

# Method 3: Copy to /sdcard/Download for manual install
echo "Method 3: Copy to /sdcard/Download..."
DOWNLOAD_PATH="/sdcard/Download/customcamera-debug.apk"
if cp "$APK_PATH" "$DOWNLOAD_PATH" 2>/dev/null; then
    echo "  ✅ APK copied to: $DOWNLOAD_PATH"
    echo
    echo "📱 Manual installation:"
    echo "  1. Open your file manager"
    echo "  2. Go to Downloads folder"
    echo "  3. Tap 'customcamera-debug.apk'"
    echo "  4. Tap 'Install'"

    # Try to open file manager
    if command -v termux-open &>/dev/null; then
        echo
        echo "  Opening file manager..."
        timeout 5 termux-open "$DOWNLOAD_PATH" 2>/dev/null && INSTALLED=true
    fi
else
    echo "  ⚠️  Cannot write to /sdcard/Download (storage permission needed)"
fi
echo

# Exit if opened via file manager
if [ "$INSTALLED" = true ]; then
    exit 0
fi

# Method 4: Copy to Termux storage
echo "Method 4: Copy to Termux storage..."
TERMUX_STORAGE="$HOME/storage/downloads/customcamera-debug.apk"

# Setup storage access if needed
if [ ! -d "$HOME/storage" ]; then
    echo "  Setting up Termux storage access..."
    timeout 10 termux-setup-storage 2>/dev/null || true
    sleep 1
fi

if [ -d "$HOME/storage/downloads" ]; then
    if cp "$APK_PATH" "$TERMUX_STORAGE" 2>/dev/null; then
        echo "  ✅ APK copied to: ~/storage/downloads/"
        echo
        echo "📱 Manual installation:"
        echo "  1. Open Downloads in file manager"
        echo "  2. Tap 'customcamera-debug.apk'"
        echo "  3. Install the app"

        if command -v termux-open &>/dev/null; then
            echo
            echo "  Opening Downloads..."
            timeout 5 termux-open "$TERMUX_STORAGE" 2>/dev/null && INSTALLED=true
        fi
    else
        echo "  ⚠️  Failed to copy to Termux storage"
    fi
else
    echo "  ⚠️  Termux storage not accessible"
    echo "  Run: termux-setup-storage"
fi
echo

# Exit if opened
if [ "$INSTALLED" = true ]; then
    exit 0
fi

# All methods failed - show manual instructions
echo "========================================="
echo "❌ Automatic installation failed"
echo "========================================="
echo
echo "Manual installation required:"
echo
echo "1. Via Termux:"
echo "   termux-open $APK_PATH"
echo
echo "2. Via ADB from PC:"
echo "   adb connect <device_ip>:<port>"
echo "   adb install -r $APK_PATH"
echo
echo "3. Copy manually:"
echo "   The APK is at: $APK_PATH"
echo "   Copy to /sdcard and install via file manager"
echo
exit 1

echo
echo "=== BUILD AND INSTALL COMPLETE ==="
echo "APK: $APK_PATH"
echo "Size: $(du -h "$APK_PATH" | cut -f1)"
echo "Time: $(date)"
echo
echo "💡 Useful commands:"
echo "   • View logs: adb logcat -d | grep customcamera"
echo "   • Uninstall: adb uninstall com.customcamera.app"
echo "   • Reinstall: $0"
echo "   • Clean build: $0 clean"