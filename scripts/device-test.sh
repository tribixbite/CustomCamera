#!/data/data/com.termux/files/usr/bin/bash
# Run the on-device test suite against one adb device, then clean up.
#
# Usage: scripts/device-test.sh <serial> [--keep]
#   Builds nothing: run `scripts/gradle-locked.sh :app:assembleDebug
#   :app:assembleDebugAndroidTest :app:assembleStaging` first.
#   --keep leaves the debug app installed afterwards.
#
# Steps: install debug + test APKs, run all instrumented tests, launch the
# R8-shrunk staging build and check it survives a cold start, then uninstall
# what this script installed. Captures made by the tests are deleted by the tests.
set -euo pipefail

SERIAL="${1:?usage: device-test.sh <serial> [--keep]}"
KEEP="${2:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk"
ID=tribixbite.customcamera
adb_() { adb -s "$SERIAL" "$@"; }

echo "== $(adb_ shell getprop ro.product.model) / API $(adb_ shell getprop ro.build.version.sdk)"
adb_ install -r -g "$APK/debug/app-debug.apk" >/dev/null
adb_ install -r -t "$APK/androidTest/debug/app-debug-androidTest.apk" >/dev/null

echo "== instrumented tests"
out=$(adb_ shell am instrument -w -r "$ID.debug.test/androidx.test.runner.AndroidJUnitRunner")
echo "$out" | awk '/^INSTRUMENTATION_STATUS: test=/{t=$2} /^INSTRUMENTATION_STATUS_CODE: -2/{print "  FAIL " t} /^OK \(|^Tests run|^FAILURES/'
echo "$out" | rg -q '^OK \(' && result=0 || result=1
if [ "$result" -ne 0 ]; then
    # Keep the first lines of every failure's stack: intermittent failures are otherwise lost.
    # A stack is reported before its test's status code; print it only for failures (-2), not skips (-4).
    echo "$out" | awk '
        /^INSTRUMENTATION_STATUS: stack=/ { stack = ""; n = 8 }
        n > 0 { stack = stack "    " $0 "\n"; n-- }
        /^INSTRUMENTATION_STATUS_CODE: -2/ { printf "%s", stack; stack = "" }
        /^INSTRUMENTATION_STATUS_CODE: / { stack = "" }'
    log="${TMPDIR:-/tmp}/customcamera-instrument-$(date +%Y%m%d-%H%M%S).log"
    echo "$out" > "$log" && echo "  full output: $log"
fi

echo "== staging (R8) cold start"
adb_ install -r -g "$APK/staging/app-staging.apk" >/dev/null
# Shared device: filter logcat from now on instead of clearing other agents' logs.
since=$(adb_ shell "date +'%m-%d %H:%M:%S.000'")
adb_ shell am start -W -n "$ID.staging/com.customcamera.app.ui.CameraActivity" | rg 'TotalTime|Status'
sleep 4
if adb_ shell pidof "$ID.staging" >/dev/null && ! adb_ logcat -d -T "$since" | rg -q "Process: $ID.staging"; then
    echo "  staging alive, no crash"
else
    echo "  staging CRASHED"; adb_ logcat -d -T "$since" | rg -A15 'FATAL EXCEPTION' | head -30; result=1
fi
adb_ shell am force-stop "$ID.staging"

echo "== cleanup"
adb_ uninstall "$ID.staging" >/dev/null || true
adb_ uninstall "$ID.debug.test" >/dev/null || true
[ "$KEEP" = "--keep" ] || adb_ uninstall "$ID.debug" >/dev/null || true
exit $result
