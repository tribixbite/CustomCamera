# CustomCamera R8 rules.
#
# CameraX, ML Kit, Material and AndroidX ship their own consumer rules, so the
# app needs very few. Do NOT add a blanket -keep for com.customcamera.app.**:
# that disables shrinking/obfuscation for all app code.

# Keep line numbers so Play Console crash stack traces can be deobfuscated with
# the uploaded mapping.txt, but hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preference fragments are instantiated reflectively from XML by class name.
-keep class com.customcamera.app.settings.SettingsFragment { <init>(); }

# Strip verbose/debug logging from shrunk builds (warnings and errors are kept).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
