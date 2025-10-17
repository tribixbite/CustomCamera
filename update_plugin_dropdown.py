#!/usr/bin/env python3
"""
Update plugin companion objects with showInDropdown property

Phase 8.4: Remove dual activation methods by setting proper visibility flags
"""

import re
import os

# Map of plugin name -> showInDropdown value
PLUGIN_VISIBILITY = {
    # Plugins with dedicated buttons - should NOT be in dropdown
    'NightModePlugin': False,
    'DualCameraPiPPlugin': False,

    # Always-active control plugins - should NOT be in dropdown
    'AutoFocusPlugin': False,
    'ExposureControlPlugin': False,
    'ManualFocusPlugin': False,
    'ProControlsPlugin': False,

    # User-toggleable features WITHOUT dedicated buttons - should be in dropdown
    'GridOverlayPlugin': True,
    'BarcodePlugin': True,
    'CropPlugin': True,
    'QRScannerPlugin': True,
    'SmartScenePlugin': True,
    'ObjectDetectionPlugin': True,
    'MotionDetectionPlugin': True,
    'HDRPlugin': True,
    'RAWCapturePlugin': True,
    'HistogramPlugin': True,
    'CameraInfoPlugin': True,
    'ExposureAnalysisPlugin': True,
    'SharpnessAnalysisPlugin': True,
    'SmartAdjustmentsPlugin': True,
    'ScanningOverlayPlugin': True,
    'AdvancedVideoRecordingPlugin': True,
}

def update_plugin_file(plugin_name, show_in_dropdown):
    """Update a plugin file to add showInDropdown override"""
    plugin_file = f'app/src/main/java/com/customcamera/app/plugins/{plugin_name}.kt'

    if not os.path.exists(plugin_file):
        print(f"❌ File not found: {plugin_file}")
        return False

    with open(plugin_file, 'r') as f:
        content = f.read()

    # Check if plugin has companion object
    if 'companion object' not in content:
        print(f"⚠️  {plugin_name}: No companion object found")
        return False

    # Check if showInDropdown already exists
    if 'override val showInDropdown' in content:
        print(f"ℹ️  {plugin_name}: showInDropdown already exists, skipping")
        return False

    # Find the companion object and add showInDropdown after userToggleable
    # Pattern: look for "override val userToggleable" and add after it
    pattern = r'(override val userToggleable: Boolean = \w+)'

    replacement = f'\\1\n\n        override val showInDropdown: Boolean = {str(show_in_dropdown).lower()}'

    new_content = re.sub(pattern, replacement, content)

    if new_content == content:
        # Try alternative pattern without explicit value
        pattern = r'(override val userToggleable: Boolean\s*\n)'
        replacement = f'\\1\n        override val showInDropdown: Boolean = {str(show_in_dropdown).lower()}\n'
        new_content = re.sub(pattern, replacement, content)

    if new_content == content:
        print(f"⚠️  {plugin_name}: Could not find insertion point")
        return False

    with open(plugin_file, 'w') as f:
        f.write(new_content)

    print(f"✅ {plugin_name}: showInDropdown = {show_in_dropdown}")
    return True

def main():
    """Update all plugins with showInDropdown property"""
    os.chdir('/data/data/com.termux/files/home/git/swype/CustomCamera')

    print("Phase 8.4: Updating plugin visibility flags\n")
    print("=" * 60)

    updated_count = 0
    skipped_count = 0

    for plugin_name, show_in_dropdown in PLUGIN_VISIBILITY.items():
        result = update_plugin_file(plugin_name, show_in_dropdown)
        if result:
            updated_count += 1
        else:
            skipped_count += 1

    print("=" * 60)
    print(f"\n✅ Updated: {updated_count} plugins")
    print(f"⚠️  Skipped: {skipped_count} plugins")
    print(f"📊 Total: {len(PLUGIN_VISIBILITY)} plugins processed")

    print("\n📝 Summary:")
    print(f"  - Plugins with dedicated buttons (showInDropdown=false): {sum(1 for v in PLUGIN_VISIBILITY.values() if not v)}")
    print(f"  - Plugins for dropdown menu (showInDropdown=true): {sum(1 for v in PLUGIN_VISIBILITY.values() if v)}")

if __name__ == '__main__':
    main()
