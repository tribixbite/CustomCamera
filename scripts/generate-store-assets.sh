#!/data/data/com.termux/files/usr/bin/bash
# Render Play Store graphics into fastlane/metadata/android/en-US/images.
#
# Requires rsvg-convert. Text is rendered with Roboto Flex through a private
# fontconfig file: on Android/Termux fontconfig lists /system/fonts but fc-match
# never selects them, so without this rsvg silently falls back to DejaVu.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/fastlane/metadata/android/en-US/images"
WORK="${TMPDIR:-/tmp}/cc-store-assets"
FONT_DIR="${CC_FONT_DIR:-/system/fonts}"

mkdir -p "$OUT" "$WORK/fonts" "$WORK/cache"
cp "$FONT_DIR/RobotoFlex-Regular.ttf" "$WORK/fonts/" 2>/dev/null || {
    echo "RobotoFlex-Regular.ttf not found in $FONT_DIR (set CC_FONT_DIR)" >&2
    exit 1
}
cat > "$WORK/fonts.conf" <<CONF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>$WORK/fonts</dir>
  <cachedir>$WORK/cache</cachedir>
</fontconfig>
CONF
export FONTCONFIG_FILE="$WORK/fonts.conf"

python3 "$ROOT/scripts/store_assets.py" "$WORK"
rsvg-convert -w 512 -h 512 "$WORK/icon.svg" -o "$WORK/icon.png"
# Play requires the hi-res icon as a 32-bit PNG (RGBA).
magick "$WORK/icon.png" -define png:color-type=6 "PNG32:$OUT/icon.png"
rsvg-convert -w 1024 -h 500 "$WORK/feature.svg" -o "$OUT/featureGraphic.png"
echo "Wrote $OUT/icon.png and $OUT/featureGraphic.png"
