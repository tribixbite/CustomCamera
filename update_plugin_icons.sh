#!/bin/bash

# Script to update plugin companion object icons with new unique icons

cd ~/git/swype/CustomCamera

# Update Barcode Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_extension$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_barcode/' app/src/main/java/com/customcamera/app/plugins/BarcodePlugin.kt

# Update Histogram Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_info$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_histogram/' app/src/main/java/com/customcamera/app/plugins/HistogramPlugin.kt

# Update Crop Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_settings$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_crop/' app/src/main/java/com/customcamera/app/plugins/CropPlugin.kt

# Update HDR Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_camera$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_hdr/' app/src/main/java/com/customcamera/app/plugins/HDRPlugin.kt

# Update Exposure Control Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_focus$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_exposure/' app/src/main/java/com/customcamera/app/plugins/ExposureControlPlugin.kt

# Update Manual Focus Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_focus$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_manual_focus/' app/src/main/java/com/customcamera/app/plugins/ManualFocusPlugin.kt

# Update Pro Controls Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_settings$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_pro_controls/' app/src/main/java/com/customcamera/app/plugins/ProControlsPlugin.kt

# Update Smart Scene Plugin (AI)
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_extension$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_ai_brain/' app/src/main/java/com/customcamera/app/plugins/SmartScenePlugin.kt

# Update Smart Adjustments Plugin (AI)
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_extension$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_ai_brain/' app/src/main/java/com/customcamera/app/plugins/SmartAdjustmentsPlugin.kt

# Update Object Detection Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_extension$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_object_detection/' app/src/main/java/com/customcamera/app/plugins/ObjectDetectionPlugin.kt

# Update Motion Detection Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_extension$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_motion/' app/src/main/java/com/customcamera/app/plugins/MotionDetectionPlugin.kt

# Update QR Scanner Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_extension$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_qr/' app/src/main/java/com/customcamera/app/plugins/QRScannerPlugin.kt

# Update RAW Capture Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_camera$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_raw/' app/src/main/java/com/customcamera/app/plugins/RAWCapturePlugin.kt

# Update Sharpness Analysis Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_info$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_sharpness/' app/src/main/java/com/customcamera/app/plugins/SharpnessAnalysisPlugin.kt

# Update Auto Focus Plugin
sed -i 's/override val iconResId: Int = com.customcamera.app.R.drawable.ic_focus$/override val iconResId: Int = com.customcamera.app.R.drawable.ic_auto_focus/' app/src/main/java/com/customcamera/app/plugins/AutoFocusPlugin.kt

echo "✅ Updated all plugin icons with new unique vector icons"
