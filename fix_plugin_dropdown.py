#!/usr/bin/env python3
"""
Fix showInDropdown placement - remove from class properties, keep only in companion objects
"""

import re
import os

PLUGIN_FILES = [
    'AdvancedVideoRecordingPlugin',
    'AutoFocusPlugin',
    'BarcodePlugin',
    'CameraInfoPlugin',
    'CropPlugin',
    'DualCameraPiPPlugin',
    'ExposureAnalysisPlugin',
    'ExposureControlPlugin',
    'GridOverlayPlugin',
    'HDRPlugin',
    'HistogramPlugin',
    'ManualFocusPlugin',
    'MotionDetectionPlugin',
    'NightModePlugin',
    'ObjectDetectionPlugin',
    'ProControlsPlugin',
    'QRScannerPlugin',
    'RAWCapturePlugin',
    'ScanningOverlayPlugin',
    'SharpnessAnalysisPlugin',
    'SmartAdjustmentsPlugin',
    'SmartScenePlugin',
]

def fix_plugin_file(plugin_name):
    """Remove showInDropdown from class properties"""
    plugin_file = f'app/src/main/java/com/customcamera/app/plugins/{plugin_name}.kt'

    if not os.path.exists(plugin_file):
        print(f"❌ File not found: {plugin_file}")
        return False

    with open(plugin_file, 'r') as f:
        content = f.read()

    # Remove the showInDropdown override from class properties (not companion object)
    # Pattern: Find lines with "override val showInDropdown" that are NOT in companion object
    # We'll look for the pattern before the companion object section

    # Split by companion object to separate class properties from companion properties
    parts = content.split('companion object')

    if len(parts) < 2:
        print(f"⚠️  {plugin_name}: No companion object found")
        return False

    # Remove showInDropdown from first part (class properties)
    class_part = parts[0]

    # Remove the override val showInDropdown line and any surrounding whitespace
    class_part = re.sub(r'\n\s+override val showInDropdown: Boolean = (?:true|false)\n', '\n', class_part)

    # Reconstruct the file
    new_content = class_part + 'companion object' + 'companion object'.join(parts[1:])

    if new_content == content:
        print(f"ℹ️  {plugin_name}: No changes needed")
        return False

    with open(plugin_file, 'w') as f:
        f.write(new_content)

    print(f"✅ {plugin_name}: Removed showInDropdown from class properties")
    return True

def main():
    """Fix all plugin files"""
    os.chdir('/data/data/com.termux/files/home/git/swype/CustomCamera')

    print("Fixing showInDropdown placement in plugins\n")
    print("=" * 60)

    fixed_count = 0

    for plugin_name in PLUGIN_FILES:
        result = fix_plugin_file(plugin_name)
        if result:
            fixed_count += 1

    print("=" * 60)
    print(f"\n✅ Fixed: {fixed_count} plugins")
    print(f"📊 Total: {len(PLUGIN_FILES)} plugins processed")

if __name__ == '__main__':
    main()
