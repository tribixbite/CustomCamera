#!/data/data/com.termux/files/usr/bin/bash

# CustomCamera Build and Install Script for Termux
# Builds the app and intelligently connects via ADB wireless
# Usage: ./build-and-install.sh [clean]

set -e

BUILD_TYPE="debug"
CLEAN_BUILD="${1:-}"

echo "=== CustomCamera Build and Install Tool ==="
echo "Building $BUILD_TYPE APK with smart ADB connection"
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
        SCANNED_PORTS=$(nmap -p 45000-50000,40000-44999,35000-39999,30000-34999 --open -T4 --host-timeout 2s "$HOST" 2>/dev/null | \
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

if ! command -v adb &>/dev/null; then
    echo "❌ ADB not found. Install with: pkg install android-tools"
    exit 1
fi

if [ ! -f "./gradlew" ]; then
    echo "❌ gradlew not found. Make sure you're in the project root directory."
    exit 1
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

# Step 5: Smart ADB connection and installation
echo "Step 5: Installing APK via ADB..."

# Quick test - if ADB already has connected devices, use directly
if test_adb_connection; then
    echo "📱 ADB device already connected, installing directly..."

    # Get package name and uninstall old version
    PACKAGE="com.customcamera.app"
    echo "   Uninstalling old version of $PACKAGE..."
    adb uninstall "$PACKAGE" 2>/dev/null || true

    echo "   Installing new APK..."
    if adb install -r "$APK_PATH"; then
        echo
        echo "✅ APK INSTALLED SUCCESSFULLY!"
        echo "CustomCamera has been installed on your device."
        echo
        echo "📱 To launch: Find 'Custom Camera' in your app drawer"
    else
        echo "❌ Installation failed"
        exit 1
    fi
else
    echo "🔍 No ADB device connected, attempting wireless connection..."

    if connect_adb_wireless; then
        echo "🔗 Connected to device at $ADB_DEVICE"
        echo

        # Show brief device info
        MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
        ANDROID=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
        echo "📱 Device: $MODEL (Android $ANDROID)"
        echo

        # Get package name and uninstall old version
        PACKAGE="com.customcamera.app"
        echo "📦 Installing APK..."
        echo "   Package: $PACKAGE"
        echo "   Uninstalling old version..."
        adb uninstall "$PACKAGE" 2>/dev/null || true

        echo "   Installing new version..."
        if adb install -r "$APK_PATH"; then
            echo
            echo "✅ APK INSTALLED SUCCESSFULLY!"
            echo "CustomCamera has been installed on your device."
            echo
            echo "📱 To launch: Find 'Custom Camera' in your app drawer"
            echo "💡 Grant camera permissions when prompted"
        else
            echo "❌ Installation failed"
            exit 1
        fi
    else
        echo
        echo "❌ Could not establish ADB connection"
        echo
        echo "💡 Manual installation options:"
        echo "   1. Enable Developer Options on target device"
        echo "   2. Enable 'Wireless debugging' or 'ADB over network'"
        echo "   3. Note the IP and port, then run:"
        echo "      adb connect <device_ip>:<port>"
        echo "      adb install -r $APK_PATH"
        echo
        echo "   OR copy APK to device:"
        echo "   4. Use file manager to install: $APK_PATH"

        # Try termux-open as fallback
        if command -v termux-open &>/dev/null; then
            echo
            echo "🔧 Attempting to open APK with system installer..."
            termux-open "$APK_PATH" 2>/dev/null || echo "   (termux-open failed)"
        fi

        exit 1
    fi
fi

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