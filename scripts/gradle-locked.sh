#!/data/data/com.termux/files/usr/bin/bash
# Run ./gradlew under the device-wide Android build lock (Termux dev box only).
#
# Other projects on this device build through a guard that holds
# $HOME/.cache/cleverkeys-build.lock and clears tagged Gradle daemons while it
# does. Taking the same lock makes that guard wait for this build instead of
# racing it, and stops two memory-heavy builds from overlapping.
#
# Usage: scripts/gradle-locked.sh <gradle args...>
# Env:   CC_LOCK_WAIT  seconds to wait for the lock (default 7200)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK="${CC_BUILD_LOCK:-$HOME/.cache/cleverkeys-build.lock}"
WAIT="${CC_LOCK_WAIT:-7200}"

mkdir -p "$(dirname "$LOCK")"
exec 9>"$LOCK"
# Poll with -n: `flock -w` silently ignores its timeout on Termux's util-linux.
deadline=$(( $(date +%s) + WAIT ))
until flock -n 9; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
        echo "gradle-locked: $LOCK still held after ${WAIT}s — giving up" >&2
        exit 75
    fi
    sleep 5
done

cd "$ROOT"
exec ./gradlew "$@" --console=plain
