#!/usr/bin/env python3
"""
Plugin Provider Pattern Migration Script

Automates migration of plugins to use PluginProvider companion objects.
"""

import os
import re
from pathlib import Path

# Plugin metadata from PluginRegistry.kt
PLUGIN_METADATA = {
    "GridOverlay": {
        "id": "grid_overlay",
        "display_name_res": "plugin_grid_overlay_display_name",
        "description_res": "plugin_grid_overlay_description",
        "icon": "ic_extension",
        "category": "OVERLAYS"
    },
    "Crop": {
        "id": "crop",
        "display_name_res": "plugin_crop_display_name",
        "description_res": "plugin_crop_description",
        "icon": "ic_camera",
        "category": "CAPTURE"
    },
    "Barcode": {
        "id": "barcode",
        "display_name_res": "plugin_barcode_display_name",
        "description_res": "plugin_barcode_description",
        "icon": "ic_focus",
        "category": "ANALYSIS"
    },
    "Histogram": {
        "id": "histogram",
        "display_name_res": "plugin_histogram_display_name",
        "description_res": "plugin_histogram_description",
        "icon": "ic_info",
        "category": "ANALYSIS"
    },
    "CameraInfo": {
        "id": "camera_info",
        "display_name_res": "plugin_camera_info_display_name",
        "description_res": "plugin_camera_info_description",
        "icon": "ic_info",
        "category": "ANALYSIS"
    },
    "ExposureAnalysis": {
        "id": "exposure_analysis",
        "display_name_res": "plugin_exposure_analysis_display_name",
        "description_res": "plugin_exposure_analysis_description",
        "icon": "ic_info",
        "category": "ANALYSIS"
    },
    "MotionDetection": {
        "id": "motion_detection",
        "display_name_res": "plugin_motion_detection_display_name",
        "description_res": "plugin_motion_detection_description",
        "icon": "ic_focus",
        "category": "ANALYSIS"
    },
    "QRScanner": {
        "id": "qr_scanner",
        "display_name_res": "plugin_qr_scanner_display_name",
        "description_res": "plugin_qr_scanner_description",
        "icon": "ic_focus",
        "category": "ANALYSIS"
    },
    "SharpnessAnalysis": {
        "id": "sharpness_analysis",
        "display_name_res": "plugin_sharpness_analysis_display_name",
        "description_res": "plugin_sharpness_analysis_description",
        "icon": "ic_info",
        "category": "ANALYSIS"
    },
    "AutoFocus": {
        "id": "auto_focus",
        "display_name_res": "plugin_auto_focus_display_name",
        "description_res": "plugin_auto_focus_description",
        "icon": "ic_focus",
        "category": "CONTROLS"
    },
    "ExposureControl": {
        "id": "exposure_control",
        "display_name_res": "plugin_exposure_control_display_name",
        "description_res": "plugin_exposure_control_description",
        "icon": "ic_settings",
        "category": "CONTROLS"
    },
    "ManualFocus": {
        "id": "manual_focus",
        "display_name_res": "plugin_manual_focus_display_name",
        "description_res": "plugin_manual_focus_description",
        "icon": "ic_focus",
        "category": "CONTROLS"
    },
    "ProControls": {
        "id": "pro_controls",
        "display_name_res": "plugin_pro_controls_display_name",
        "description_res": "plugin_pro_controls_description",
        "icon": "ic_settings",
        "category": "CONTROLS"
    },
    "ManualControls": {
        "id": "manual_controls",
        "display_name_res": "plugin_manual_controls_display_name",
        "description_res": "plugin_manual_controls_description",
        "icon": "ic_settings",
        "category": "CONTROLS"
    },
    "SmartScene": {
        "id": "smart_scene",
        "display_name_res": "plugin_smart_scene_display_name",
        "description_res": "plugin_smart_scene_description",
        "icon": "ic_camera",
        "category": "AI"
    },
    "SmartAdjustments": {
        "id": "smart_adjustments",
        "display_name_res": "plugin_smart_adjustments_display_name",
        "description_res": "plugin_smart_adjustments_description",
        "icon": "ic_settings",
        "category": "AI"
    },
    "ObjectDetection": {
        "id": "object_detection",
        "display_name_res": "plugin_object_detection_display_name",
        "description_res": "plugin_object_detection_description",
        "icon": "ic_focus",
        "category": "AI"
    },
    "DualCameraPiP": {
        "id": "dual_camera_pip",
        "display_name_res": "plugin_dual_camera_pip_display_name",
        "description_res": "plugin_dual_camera_pip_description",
        "icon": "ic_pip",
        "category": "CAPTURE"
    },
    "RAWCapture": {
        "id": "raw_capture",
        "display_name_res": "plugin_raw_capture_display_name",
        "description_res": "plugin_raw_capture_description",
        "icon": "ic_camera",
        "category": "CAPTURE"
    },
    "AdvancedVideoRecording": {
        "id": "advanced_video",
        "display_name_res": "plugin_advanced_video_display_name",
        "description_res": "plugin_advanced_video_description",
        "icon": "ic_videocam",
        "category": "CAPTURE"
    },
    "NightMode": {
        "id": "night_mode",
        "display_name_res": "plugin_night_mode_display_name",
        "description_res": "plugin_night_mode_description",
        "icon": "ic_night_mode",
        "category": "CAPTURE"
    },
    "HDR": {
        "id": "hdr",
        "display_name_res": "plugin_hdr_display_name",
        "description_res": "plugin_hdr_description",
        "icon": "ic_camera",
        "category": "CAPTURE"
    }
}

def generate_provider_companion(plugin_name, metadata):
    """Generate PluginProvider companion object code"""

    template = f"""    companion object : com.customcamera.app.engine.plugins.PluginProvider {{
        private const val TAG = "{plugin_name}Plugin"

        // PluginProvider implementation
        override val id: String = "{metadata['id']}"

        override val displayNameRes: Int = com.customcamera.app.R.string.{metadata['display_name_res']}

        override val descriptionRes: Int = com.customcamera.app.R.string.{metadata['description_res']}

        override val iconResId: Int = com.customcamera.app.R.drawable.{metadata['icon']}

        override val category: com.customcamera.app.engine.plugins.PluginCategory =
            com.customcamera.app.engine.plugins.PluginCategory.{metadata['category']}

        override val userToggleable: Boolean = true

        override fun isSupported(context: android.content.Context): Boolean {{
            // TODO: Add device capability checking if needed
            return true
        }}

        override fun create(dependencies: com.customcamera.app.engine.plugins.PluginDependencies): com.customcamera.app.engine.plugins.CameraPlugin {{
            return {plugin_name}Plugin()
        }}
    }}"""

    return template

def migrate_plugin_file(file_path, plugin_name):
    """Migrate a single plugin file to use PluginProvider"""

    if plugin_name not in PLUGIN_METADATA:
        print(f"⚠️  Skipping {plugin_name} - no metadata found")
        return False

    with open(file_path, 'r') as f:
        content = f.read()

    # Check if already migrated
    if 'PluginProvider' in content:
        print(f"✅ {plugin_name} already migrated")
        return False

    metadata = PLUGIN_METADATA[plugin_name]

    # Find companion object
    companion_pattern = r'    companion object \{[^}]*private const val TAG[^}]*\}'
    companion_match = re.search(companion_pattern, content, re.DOTALL)

    if not companion_match:
        print(f"❌ {plugin_name} - couldn't find companion object pattern")
        return False

    # Replace companion object with PluginProvider version
    new_companion = generate_provider_companion(plugin_name, metadata)
    new_content = content[:companion_match.start()] + new_companion + content[companion_match.end():]

    # Write back
    with open(file_path, 'w') as f:
        f.write(new_content)

    print(f"✅ Migrated {plugin_name}")
    return True

def main():
    """Main migration script"""
    plugins_dir = Path("/data/data/com.termux/files/home/git/swype/CustomCamera/app/src/main/java/com/customcamera/app/plugins")

    print("🚀 Starting Plugin Provider Pattern Migration")
    print("=" * 60)

    migrated_count = 0
    skipped_count = 0
    error_count = 0

    for plugin_file in sorted(plugins_dir.glob("*Plugin.kt")):
        plugin_name = plugin_file.stem.replace("Plugin", "")

        try:
            if migrate_plugin_file(plugin_file, plugin_name):
                migrated_count += 1
            else:
                skipped_count += 1
        except Exception as e:
            print(f"❌ Error migrating {plugin_name}: {e}")
            error_count += 1

    print("=" * 60)
    print(f"📊 Migration Summary:")
    print(f"   ✅ Migrated: {migrated_count}")
    print(f"   ⏭️  Skipped: {skipped_count}")
    print(f"   ❌ Errors: {error_count}")
    print("=" * 60)

if __name__ == "__main__":
    main()
