package com.customcamera.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import kotlinx.coroutines.*

/**
 * Adaptive Theme Manager
 *
 * Provides dynamic theming capabilities:
 * - Automatic dark/light mode detection
 * - Custom color schemes
 * - Dynamic color adaptation (Android 12+)
 * - Battery-saving dark themes
 * - Accessibility-aware color choices
 * - Camera interface specific themes
 */
class AdaptiveThemeManager(private val context: Context) {

    companion object {
        private const val TAG = "AdaptiveThemeManager"
        private const val PREFS_NAME = "adaptive_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_CAMERA_THEME = "camera_theme"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Theme modes
     */
    enum class ThemeMode {
        SYSTEM,         // Follow system theme
        LIGHT,          // Always light
        DARK,           // Always dark
        AUTO_BATTERY,   // Dark when battery saving
        AUTO_TIME       // Dark at night, light during day
    }

    /**
     * Camera interface themes
     */
    enum class CameraTheme {
        MINIMAL,        // Clean minimal interface
        PROFESSIONAL,   // Pro camera styling
        MODERN,         // Modern Material Design
        CLASSIC,        // Traditional camera app style
        CYBERPUNK,      // High-tech futuristic theme
        NATURE          // Earth tones and organic colors
    }

    /**
     * Color schemes for different contexts
     */
    data class ColorScheme(
        @ColorInt val primary: Int,
        @ColorInt val primaryVariant: Int,
        @ColorInt val secondary: Int,
        @ColorInt val background: Int,
        @ColorInt val surface: Int,
        @ColorInt val onPrimary: Int,
        @ColorInt val onSecondary: Int,
        @ColorInt val onBackground: Int,
        @ColorInt val onSurface: Int,
        @ColorInt val error: Int,
        @ColorInt val onError: Int,
        val isDark: Boolean
    )

    /**
     * Get current theme mode
     */
    fun getCurrentThemeMode(): ThemeMode {
        val modeString = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.valueOf(modeString ?: ThemeMode.SYSTEM.name)
    }

    /**
     * Set theme mode
     */
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }

    /**
     * Check if dynamic colors are enabled (Android 12+)
     */
    fun isDynamicColorsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            prefs.getBoolean(KEY_DYNAMIC_COLORS, true)
        } else {
            false
        }
    }

    /**
     * Enable/disable dynamic colors
     */
    fun setDynamicColorsEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DYNAMIC_COLORS, enabled)
            .apply()
    }

    /**
     * Check if high contrast mode is enabled
     */
    fun isHighContrastEnabled(): Boolean {
        return prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    }

    /**
     * Enable/disable high contrast mode
     */
    fun setHighContrastEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_HIGH_CONTRAST, enabled)
            .apply()
    }

    /**
     * Get current camera theme
     */
    fun getCurrentCameraTheme(): CameraTheme {
        val themeString = prefs.getString(KEY_CAMERA_THEME, CameraTheme.MODERN.name)
        return CameraTheme.valueOf(themeString ?: CameraTheme.MODERN.name)
    }

    /**
     * Set camera theme
     */
    fun setCameraTheme(theme: CameraTheme) {
        prefs.edit()
            .putString(KEY_CAMERA_THEME, theme.name)
            .apply()
    }

    /**
     * Determine if dark theme should be used
     */
    fun shouldUseDarkTheme(): Boolean {
        return when (getCurrentThemeMode()) {
            ThemeMode.SYSTEM -> isSystemDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO_BATTERY -> isBatterySaverEnabled()
            ThemeMode.AUTO_TIME -> isNightTime()
        }
    }

    /**
     * Get adaptive color scheme based on current settings
     */
    fun getAdaptiveColorScheme(): ColorScheme {
        val isDark = shouldUseDarkTheme()
        val isHighContrast = isHighContrastEnabled()
        val cameraTheme = getCurrentCameraTheme()

        return when (cameraTheme) {
            CameraTheme.MINIMAL -> getMinimalColorScheme(isDark, isHighContrast)
            CameraTheme.PROFESSIONAL -> getProfessionalColorScheme(isDark, isHighContrast)
            CameraTheme.MODERN -> getModernColorScheme(isDark, isHighContrast)
            CameraTheme.CLASSIC -> getClassicColorScheme(isDark, isHighContrast)
            CameraTheme.CYBERPUNK -> getCyberpunkColorScheme(isDark, isHighContrast)
            CameraTheme.NATURE -> getNatureColorScheme(isDark, isHighContrast)
        }
    }

    /**
     * Get camera UI specific colors
     */
    fun getCameraUIColors(): CameraUIColors {
        val colorScheme = getAdaptiveColorScheme()
        val isDark = colorScheme.isDark

        return CameraUIColors(
            captureButton = if (isDark) Color.WHITE else Color.BLACK,
            captureButtonRing = colorScheme.primary,
            focusIndicator = colorScheme.primary,
            gridLines = Color.argb(128, 255, 255, 255),
            settingsBackground = Color.argb(180, 0, 0, 0),
            settingsText = Color.WHITE,
            warningColor = Color.argb(255, 255, 193, 7),
            errorColor = colorScheme.error,
            successColor = Color.argb(255, 76, 175, 80),
            overlayBackground = Color.argb(120, 0, 0, 0),
            buttonBackground = Color.argb(100, 255, 255, 255),
            buttonBackgroundPressed = Color.argb(150, 255, 255, 255)
        )
    }

    /**
     * Apply theme to camera activity
     */
    suspend fun applyCameraTheme(): ColorScheme = withContext(Dispatchers.Default) {
        val colorScheme = getAdaptiveColorScheme()

        // Apply dynamic colors if supported and enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDynamicColorsEnabled()) {
            applyDynamicColors(colorScheme)
        } else {
            colorScheme
        }
    }

    // Private helper methods

    private fun isSystemDarkTheme(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun isBatterySaverEnabled(): Boolean {
        // Fallback to system theme if battery info not available
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                powerManager.isPowerSaveMode
            } else {
                isSystemDarkTheme()
            }
        } catch (e: Exception) {
            isSystemDarkTheme()
        }
    }

    private fun isNightTime(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour < 7 || hour >= 19 // 7 PM to 7 AM is considered night
    }

    private fun getMinimalColorScheme(isDark: Boolean, isHighContrast: Boolean): ColorScheme {
        return if (isDark) {
            ColorScheme(
                primary = if (isHighContrast) Color.WHITE else Color.argb(255, 187, 134, 252),
                primaryVariant = if (isHighContrast) Color.WHITE else Color.argb(255, 103, 58, 183),
                secondary = if (isHighContrast) Color.WHITE else Color.argb(255, 3, 218, 198),
                background = if (isHighContrast) Color.BLACK else Color.argb(255, 18, 18, 18),
                surface = if (isHighContrast) Color.BLACK else Color.argb(255, 33, 33, 33),
                onPrimary = Color.BLACK,
                onSecondary = Color.BLACK,
                onBackground = if (isHighContrast) Color.WHITE else Color.argb(255, 224, 224, 224),
                onSurface = if (isHighContrast) Color.WHITE else Color.argb(255, 224, 224, 224),
                error = Color.argb(255, 244, 67, 54),
                onError = Color.WHITE,
                isDark = true
            )
        } else {
            ColorScheme(
                primary = if (isHighContrast) Color.BLACK else Color.argb(255, 103, 58, 183),
                primaryVariant = if (isHighContrast) Color.BLACK else Color.argb(255, 63, 81, 181),
                secondary = if (isHighContrast) Color.BLACK else Color.argb(255, 0, 150, 136),
                background = if (isHighContrast) Color.WHITE else Color.argb(255, 250, 250, 250),
                surface = Color.WHITE,
                onPrimary = Color.WHITE,
                onSecondary = Color.WHITE,
                onBackground = if (isHighContrast) Color.BLACK else Color.argb(255, 33, 33, 33),
                onSurface = if (isHighContrast) Color.BLACK else Color.argb(255, 33, 33, 33),
                error = Color.argb(255, 211, 47, 47),
                onError = Color.WHITE,
                isDark = false
            )
        }
    }

    private fun getProfessionalColorScheme(isDark: Boolean, isHighContrast: Boolean): ColorScheme {
        return if (isDark) {
            ColorScheme(
                primary = Color.argb(255, 255, 193, 7),  // Amber
                primaryVariant = Color.argb(255, 255, 152, 0),
                secondary = Color.argb(255, 96, 125, 139),  // Blue Grey
                background = Color.argb(255, 33, 33, 33),
                surface = Color.argb(255, 66, 66, 66),
                onPrimary = Color.BLACK,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 224, 224, 224),
                onSurface = Color.argb(255, 224, 224, 224),
                error = Color.argb(255, 244, 67, 54),
                onError = Color.WHITE,
                isDark = true
            )
        } else {
            ColorScheme(
                primary = Color.argb(255, 255, 152, 0),  // Orange
                primaryVariant = Color.argb(255, 239, 108, 0),
                secondary = Color.argb(255, 55, 71, 79),  // Blue Grey
                background = Color.argb(255, 245, 245, 245),
                surface = Color.WHITE,
                onPrimary = Color.BLACK,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 33, 33, 33),
                onSurface = Color.argb(255, 33, 33, 33),
                error = Color.argb(255, 211, 47, 47),
                onError = Color.WHITE,
                isDark = false
            )
        }
    }

    private fun getModernColorScheme(isDark: Boolean, isHighContrast: Boolean): ColorScheme {
        return if (isDark) {
            ColorScheme(
                primary = Color.argb(255, 187, 134, 252),  // Purple
                primaryVariant = Color.argb(255, 103, 58, 183),
                secondary = Color.argb(255, 3, 218, 198),  // Teal
                background = Color.argb(255, 18, 18, 18),
                surface = Color.argb(255, 33, 33, 33),
                onPrimary = Color.BLACK,
                onSecondary = Color.BLACK,
                onBackground = Color.argb(255, 224, 224, 224),
                onSurface = Color.argb(255, 224, 224, 224),
                error = Color.argb(255, 244, 67, 54),
                onError = Color.WHITE,
                isDark = true
            )
        } else {
            ColorScheme(
                primary = Color.argb(255, 103, 58, 183),  // Purple
                primaryVariant = Color.argb(255, 63, 81, 181),
                secondary = Color.argb(255, 0, 150, 136),  // Teal
                background = Color.argb(255, 250, 250, 250),
                surface = Color.WHITE,
                onPrimary = Color.WHITE,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 33, 33, 33),
                onSurface = Color.argb(255, 33, 33, 33),
                error = Color.argb(255, 211, 47, 47),
                onError = Color.WHITE,
                isDark = false
            )
        }
    }

    private fun getClassicColorScheme(isDark: Boolean, isHighContrast: Boolean): ColorScheme {
        return if (isDark) {
            ColorScheme(
                primary = Color.argb(255, 76, 175, 80),  // Green
                primaryVariant = Color.argb(255, 56, 142, 60),
                secondary = Color.argb(255, 255, 193, 7),  // Amber
                background = Color.argb(255, 48, 48, 48),
                surface = Color.argb(255, 66, 66, 66),
                onPrimary = Color.BLACK,
                onSecondary = Color.BLACK,
                onBackground = Color.argb(255, 224, 224, 224),
                onSurface = Color.argb(255, 224, 224, 224),
                error = Color.argb(255, 244, 67, 54),
                onError = Color.WHITE,
                isDark = true
            )
        } else {
            ColorScheme(
                primary = Color.argb(255, 56, 142, 60),  // Green
                primaryVariant = Color.argb(255, 27, 94, 32),
                secondary = Color.argb(255, 255, 152, 0),  // Orange
                background = Color.argb(255, 248, 248, 248),
                surface = Color.WHITE,
                onPrimary = Color.WHITE,
                onSecondary = Color.BLACK,
                onBackground = Color.argb(255, 33, 33, 33),
                onSurface = Color.argb(255, 33, 33, 33),
                error = Color.argb(255, 211, 47, 47),
                onError = Color.WHITE,
                isDark = false
            )
        }
    }

    private fun getCyberpunkColorScheme(isDark: Boolean, isHighContrast: Boolean): ColorScheme {
        return if (isDark) {
            ColorScheme(
                primary = Color.argb(255, 0, 255, 255),  // Cyan
                primaryVariant = Color.argb(255, 0, 188, 212),
                secondary = Color.argb(255, 233, 30, 99),  // Pink
                background = Color.argb(255, 13, 13, 13),
                surface = Color.argb(255, 26, 26, 26),
                onPrimary = Color.BLACK,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 0, 255, 255),
                onSurface = Color.argb(255, 0, 255, 255),
                error = Color.argb(255, 255, 20, 147),
                onError = Color.WHITE,
                isDark = true
            )
        } else {
            ColorScheme(
                primary = Color.argb(255, 0, 188, 212),  // Cyan
                primaryVariant = Color.argb(255, 0, 151, 167),
                secondary = Color.argb(255, 156, 39, 176),  // Purple
                background = Color.argb(255, 240, 240, 240),
                surface = Color.WHITE,
                onPrimary = Color.WHITE,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 33, 33, 33),
                onSurface = Color.argb(255, 33, 33, 33),
                error = Color.argb(255, 211, 47, 47),
                onError = Color.WHITE,
                isDark = false
            )
        }
    }

    private fun getNatureColorScheme(isDark: Boolean, isHighContrast: Boolean): ColorScheme {
        return if (isDark) {
            ColorScheme(
                primary = Color.argb(255, 139, 195, 74),  // Light Green
                primaryVariant = Color.argb(255, 104, 159, 56),
                secondary = Color.argb(255, 121, 85, 72),  // Brown
                background = Color.argb(255, 37, 50, 43),
                surface = Color.argb(255, 55, 71, 79),
                onPrimary = Color.BLACK,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 200, 230, 201),
                onSurface = Color.argb(255, 200, 230, 201),
                error = Color.argb(255, 244, 67, 54),
                onError = Color.WHITE,
                isDark = true
            )
        } else {
            ColorScheme(
                primary = Color.argb(255, 104, 159, 56),  // Green
                primaryVariant = Color.argb(255, 51, 105, 30),
                secondary = Color.argb(255, 121, 85, 72),  // Brown
                background = Color.argb(255, 250, 250, 250),
                surface = Color.WHITE,
                onPrimary = Color.WHITE,
                onSecondary = Color.WHITE,
                onBackground = Color.argb(255, 33, 33, 33),
                onSurface = Color.argb(255, 33, 33, 33),
                error = Color.argb(255, 211, 47, 47),
                onError = Color.WHITE,
                isDark = false
            )
        }
    }

    private fun applyDynamicColors(baseScheme: ColorScheme): ColorScheme {
        // This would integrate with Android 12+ dynamic color system
        // For now, return the base scheme as dynamic color integration
        // requires more complex Material You theming setup
        return baseScheme
    }

    /**
     * Camera UI specific color definitions
     */
    data class CameraUIColors(
        @ColorInt val captureButton: Int,
        @ColorInt val captureButtonRing: Int,
        @ColorInt val focusIndicator: Int,
        @ColorInt val gridLines: Int,
        @ColorInt val settingsBackground: Int,
        @ColorInt val settingsText: Int,
        @ColorInt val warningColor: Int,
        @ColorInt val errorColor: Int,
        @ColorInt val successColor: Int,
        @ColorInt val overlayBackground: Int,
        @ColorInt val buttonBackground: Int,
        @ColorInt val buttonBackgroundPressed: Int
    )
}