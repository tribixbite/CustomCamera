#!/bin/bash
# Script to add metadata to all plugins

PLUGINS_DIR="/data/data/com.termux/files/home/git/swype/CustomCamera/app/src/main/java/com/customcamera/app/plugins"

# Define plugin metadata: filename|displayName|description|icon|category|toggleable
declare -a PLUGINS=(
    # CONTROLS
    "AutoFocusPlugin.kt|Auto Focus|Automatic focus control with tap-to-focus|ic_focus|CONTROLS|true"
    "ExposureControlPlugin.kt|Exposure Control|Manual exposure compensation and analysis|ic_settings|CONTROLS|true"
    "ManualFocusPlugin.kt|Manual Focus|Precise manual focus control|ic_focus|CONTROLS|true"
    "ManualControlsPluginSimple.kt|Manual Controls|Basic manual camera controls|ic_settings|CONTROLS|true"
    "ProControlsPlugin.kt|Pro Controls|Professional manual camera controls|ic_settings|CONTROLS|true"

    # CAPTURE
    "AdvancedVideoRecordingPlugin.kt|Advanced Video|Professional video recording features|ic_videocam|CAPTURE|true"
    "RAWCapturePlugin.kt|RAW Capture|Capture photos in DNG/RAW format|ic_camera|CAPTURE|true"
    "HDRPlugin.kt|HDR Mode|High dynamic range photography|ic_camera|CAPTURE|true"
    "NightModePlugin.kt|Night Mode|Low-light and long exposure photography|ic_night_mode|CAPTURE|true"
    "DualCameraPiPPlugin.kt|Dual Camera PiP|Picture-in-picture with dual cameras|ic_pip|CAPTURE|true"

    # ANALYSIS
    "CameraInfoPlugin.kt|Camera Info|Real-time camera information display|ic_info|ANALYSIS|true"
    "HistogramPlugin.kt|Histogram|Real-time exposure histogram|ic_info|ANALYSIS|true"
    "MotionDetectionPlugin.kt|Motion Detection|Detect motion and trigger capture|ic_focus|ANALYSIS|true"
    "QRScannerPlugin.kt|QR Scanner|Specialized QR code scanning|ic_focus|ANALYSIS|true"
    "ScanningOverlayPlugin.kt|Scan Overlay|Visual overlay for barcode scanning|ic_focus|ANALYSIS|false"
    "SharpnessAnalysisPlugin.kt|Sharpness Analysis|Analyze image sharpness and focus|ic_info|ANALYSIS|true"
    "ExposureAnalysisPlugin.kt|Exposure Analysis|Analyze and optimize exposure|ic_info|ANALYSIS|true"

    # AI
    "SmartScenePlugin.kt|Smart Scene|AI-powered scene detection|ic_camera|AI|true"
    "ObjectDetectionPlugin.kt|Object Detection|Real-time object recognition|ic_focus|AI|true"
    "SmartAdjustmentsPlugin.kt|Smart Adjustments|AI-based camera parameter optimization|ic_settings|AI|true"
)

for plugin_data in "${PLUGINS[@]}"; do
    IFS='|' read -r filename displayName description icon category toggleable <<< "$plugin_data"
    filepath="$PLUGINS_DIR/$filename"

    if [ ! -f "$filepath" ]; then
        echo "⚠️  File not found: $filename"
        continue
    fi

    # Check if already has metadata
    if grep -q "override val displayName" "$filepath"; then
        echo "✓ Already has metadata: $filename"
        continue
    fi

    echo "📝 Adding metadata to: $filename"

    # Find the line with "override val name"
    name_line=$(grep -n "override val name" "$filepath" | head -1 | cut -d: -f1)

    if [ -z "$name_line" ]; then
        echo "❌ Could not find 'override val name' in $filename"
        continue
    fi

    # Create metadata lines to insert
    metadata="    override val displayName: String = \"$displayName\"
    override val description: String = \"$description\"
    override val iconResId: Int = com.customcamera.app.R.drawable.$icon
    override val category: com.customcamera.app.engine.plugins.PluginCategory = com.customcamera.app.engine.plugins.PluginCategory.$category
    override val userToggleable: Boolean = $toggleable"

    # Insert after the name line
    sed -i "${name_line}a\\
$metadata" "$filepath"

    echo "✅ Updated: $filename"
done

echo ""
echo "🎉 Plugin metadata update complete!"
