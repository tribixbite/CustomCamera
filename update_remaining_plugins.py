#!/usr/bin/env python3
"""
Script to batch update remaining plugin metadata
"""

import os
import re

PLUGINS_DIR = "/data/data/com.termux/files/home/git/swype/CustomCamera/app/src/main/java/com/customcamera/app/plugins"

# Plugin metadata: (filename, name, displayName, description, icon, category, toggleable)
PLUGINS = [
    ("SmartScenePlugin.kt", "SmartScene", "Smart Scene", "AI-powered scene detection", "ic_camera", "AI", "true"),
    ("SmartAdjustmentsPlugin.kt", "SmartAdjustments", "Smart Adjustments", "AI-based camera parameter optimization", "ic_settings", "AI", "true"),
    ("ObjectDetectionPlugin.kt", "ObjectDetection", "Object Detection", "Real-time object recognition", "ic_focus", "AI", "true"),
    ("DualCameraPiPPlugin.kt", "DualCameraPiP", "Dual Camera PiP", "Picture-in-picture with dual cameras", "ic_pip", "CAPTURE", "true"),
    ("RAWCapturePlugin.kt", "RAWCapture", "RAW Capture", "Capture photos in DNG/RAW format", "ic_camera", "CAPTURE", "true"),
    ("AdvancedVideoRecordingPlugin.kt", "AdvancedVideoRecording", "Advanced Video", "Professional video recording features", "ic_videocam", "CAPTURE", "true"),
    ("ExposureControlPlugin.kt", "ExposureControl", "Exposure Control", "Manual exposure compensation and analysis", "ic_settings", "CONTROLS", "true"),
    ("ManualFocusPlugin.kt", "ManualFocus", "Manual Focus", "Precise manual focus control", "ic_focus", "CONTROLS", "true"),
    ("ProControlsPlugin.kt", "ProControls", "Pro Controls", "Professional manual camera controls", "ic_settings", "CONTROLS", "true"),
    ("ManualControlsPluginSimple.kt", "ManualControlsSimple", "Manual Controls", "Basic manual camera controls", "ic_settings", "CONTROLS", "true"),
    ("HistogramPlugin.kt", "Histogram", "Histogram", "Real-time exposure histogram", "ic_info", "ANALYSIS", "true"),
    ("ExposureAnalysisPlugin.kt", "ExposureAnalysis", "Exposure Analysis", "Analyze and optimize exposure", "ic_info", "ANALYSIS", "true"),
    ("CameraInfoPlugin.kt", "CameraInfo", "Camera Info", "Real-time camera information display", "ic_info", "ANALYSIS", "true"),
    ("MotionDetectionPlugin.kt", "MotionDetection", "Motion Detection", "Detect motion and trigger capture", "ic_focus", "ANALYSIS", "true"),
    ("QRScannerPlugin.kt", "QRScanner", "QR Scanner", "Specialized QR code scanning", "ic_focus", "ANALYSIS", "true"),
    ("SharpnessAnalysisPlugin.kt", "SharpnessAnalysis", "Sharpness Analysis", "Analyze image sharpness and focus", "ic_info", "ANALYSIS", "true"),
    ("ScanningOverlayPlugin.kt", "ScanningOverlay", "Scan Overlay", "Visual overlay for barcode scanning", "ic_focus", "ANALYSIS", "false"),
]

def update_plugin(filepath, name, displayName, description, icon, category, toggleable):
    """Update a single plugin file with metadata"""
    if not os.path.exists(filepath):
        print(f"⚠️  File not found: {filepath}")
        return False

    with open(filepath, 'r') as f:
        content = f.read()

    # Check if already has metadata
    if 'override val displayName' in content:
        print(f"✓ Already has metadata: {os.path.basename(filepath)}")
        return True

    # Find the line with "override val name"
    pattern = rf'(    override val name: String = "{name}")'

    if not re.search(pattern, content):
        print(f"❌ Could not find 'override val name' pattern in {os.path.basename(filepath)}")
        return False

    # Create metadata block
    metadata = f'''    override val name: String = "{name}"
    override val displayName: String = "{displayName}"
    override val description: String = "{description}"
    override val iconResId: Int = com.customcamera.app.R.drawable.{icon}
    override val category: com.customcamera.app.engine.plugins.PluginCategory = com.customcamera.app.engine.plugins.PluginCategory.{category}
    override val userToggleable: Boolean = {toggleable}'''

    # Replace the old name line with the full metadata block
    updated_content = re.sub(pattern, metadata, content)

    # Write back
    with open(filepath, 'w') as f:
        f.write(updated_content)

    print(f"✅ Updated: {os.path.basename(filepath)}")
    return True

def main():
    print("🔄 Updating remaining plugin metadata...")
    print()

    updated_count = 0
    failed_count = 0

    for plugin_data in PLUGINS:
        filename, name, displayName, description, icon, category, toggleable = plugin_data
        filepath = os.path.join(PLUGINS_DIR, filename)

        if update_plugin(filepath, name, displayName, description, icon, category, toggleable):
            updated_count += 1
        else:
            failed_count += 1

    print()
    print(f"✅ Successfully updated: {updated_count}")
    if failed_count > 0:
        print(f"❌ Failed: {failed_count}")
    print("🎉 Plugin metadata update complete!")

if __name__ == "__main__":
    main()
