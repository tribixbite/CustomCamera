#!/usr/bin/env python3
"""
Fix nullable plugin references in CameraActivityEngine.kt
after changing from lateinit to nullable properties.
"""

import re

file_path = "/data/data/com.termux/files/home/git/swype/CustomCamera/app/src/main/java/com/customcamera/app/CameraActivityEngine.kt"

with open(file_path, 'r') as f:
    content = f.read()

# Fix 1: Replace ::plugin.isInitialized checks with != null checks
plugin_names = [
    'autoFocusPlugin', 'gridOverlayPlugin', 'cameraInfoPlugin',
    'proControlsPlugin', 'exposureControlPlugin', 'cropPlugin',
    'dualCameraPiPPlugin', 'advancedVideoRecordingPlugin'
]

for plugin_name in plugin_names:
    # Replace ::pluginName.isInitialized with pluginName != null
    content = re.sub(
        rf'::({plugin_name})\.isInitialized',
        r'\1 != null',
        content
    )

# Fix 2: Add !! (non-null assertion) to plugin method calls
# Pattern: pluginName.method() -> pluginName!!.method()
# But be careful not to double-apply
for plugin_name in plugin_names:
    # Only add !! if not already present and not using safe call ?.
    # Match: pluginName.something (but not pluginName!!.something or pluginName?.something)
    content = re.sub(
        rf'\b({plugin_name})\.(?!!|\?)',
        r'\1!!.',
        content
    )

# Write back
with open(file_path, 'w') as f:
    f.write(content)

print("✅ Fixed nullable plugin references")
print("   - Replaced ::plugin.isInitialized with plugin != null")
print("   - Added !! assertions to plugin method calls")
