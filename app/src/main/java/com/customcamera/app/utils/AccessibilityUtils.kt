package com.customcamera.app.utils

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Utility class for enhancing accessibility throughout the CustomCamera app.
 * Provides comprehensive accessibility support for vision-impaired users.
 */
object AccessibilityUtils {

    /**
     * Check if accessibility services are enabled
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return accessibilityManager.isEnabled
    }

    /**
     * Check if TalkBack or similar screen reader is active
     */
    fun isScreenReaderActive(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return accessibilityManager.isTouchExplorationEnabled
    }

    /**
     * Set up comprehensive accessibility for camera buttons
     */
    fun setupCameraButtonAccessibility(
        button: ImageButton,
        description: String,
        hint: String? = null,
        isToggleable: Boolean = false,
        isToggled: Boolean = false
    ) {
        button.contentDescription = description

        ViewCompat.setAccessibilityDelegate(button, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                // Set role information
                info.className = "Button"

                // Add hint if provided
                hint?.let { info.setHintText(it) }

                // Handle toggle state
                if (isToggleable) {
                    info.isCheckable = true
                    info.isChecked = isToggled
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            if (isToggled) "Turn off $description" else "Turn on $description"
                        )
                    )
                } else {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            description
                        )
                    )
                }
            }
        })

        // Enable focus for accessibility
        button.isFocusable = true
        button.isFocusableInTouchMode = false
    }

    /**
     * Set up accessibility for camera capture button with special handling
     */
    fun setupCaptureButtonAccessibility(button: View) {
        button.contentDescription = "Capture photo"

        ViewCompat.setAccessibilityDelegate(button, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                info.className = "Button"
                info.setHintText("Double-tap to take a photo")
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        "Take photo"
                    )
                )
            }
        })

        button.isFocusable = true
        button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /**
     * Set up accessibility for camera switch button
     */
    fun setupCameraSwitchAccessibility(button: View, cameraName: String) {
        button.contentDescription = "Switch to $cameraName"

        ViewCompat.setAccessibilityDelegate(button, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                info.className = "Button"
                info.setHintText("Switch between front and back camera")
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        "Switch to $cameraName"
                    )
                )
            }
        })
    }

    /**
     * Set up accessibility for camera preview area
     */
    fun setupPreviewAccessibility(previewView: View) {
        previewView.contentDescription = "Camera preview"

        ViewCompat.setAccessibilityDelegate(previewView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                info.className = "ImageView"
                info.setHintText("Tap to focus, double-tap to toggle grid overlay")
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        "Focus camera"
                    )
                )
            }
        })

        previewView.isFocusable = true
        previewView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /**
     * Set up accessibility for settings and controls
     */
    fun setupControlAccessibility(
        view: View,
        controlName: String,
        currentValue: String? = null,
        hint: String? = null
    ) {
        val description = if (currentValue != null) {
            "$controlName: $currentValue"
        } else {
            controlName
        }

        view.contentDescription = description
        // Note: setAccessibilityHint requires API 19+, using content description instead
        view.contentDescription = if (hint != null) "$controlName - $hint" else controlName

        view.isFocusable = true
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /**
     * Announce important status changes to screen readers
     */
    fun announceStatusChange(view: View, message: String) {
        if (isAccessibilityEnabled(view.context)) {
            view.announceForAccessibility(message)
        }
    }

    /**
     * Set up accessibility for live regions (status updates)
     */
    fun setupLiveRegion(view: View, mode: Int = View.ACCESSIBILITY_LIVE_REGION_POLITE) {
        ViewCompat.setAccessibilityLiveRegion(view, mode)
    }

    /**
     * Update accessibility description for dynamic content
     */
    fun updateAccessibilityDescription(view: View, newDescription: String) {
        view.contentDescription = newDescription
        if (view.isFocused && isAccessibilityEnabled(view.context)) {
            view.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    /**
     * Set up accessibility for navigation
     */
    fun setupNavigationAccessibility(view: View, destination: String) {
        view.contentDescription = "Navigate to $destination"

        ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                info.className = "Button"
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        "Open $destination"
                    )
                )
            }
        })
    }

    /**
     * Camera-specific accessibility announcements
     */
    object CameraAnnouncements {
        fun photoTaken(view: View) {
            announceStatusChange(view, "Photo captured successfully")
        }

        fun videoStarted(view: View) {
            announceStatusChange(view, "Video recording started")
        }

        fun videoStopped(view: View, duration: String? = null) {
            val message = if (duration != null) {
                "Video recording stopped. Duration: $duration"
            } else {
                "Video recording stopped"
            }
            announceStatusChange(view, message)
        }

        fun flashToggled(view: View, isOn: Boolean) {
            announceStatusChange(view, if (isOn) "Flash turned on" else "Flash turned off")
        }

        fun cameraswitched(view: View, cameraName: String) {
            announceStatusChange(view, "Switched to $cameraName")
        }

        fun focusChanged(view: View, isLocked: Boolean) {
            announceStatusChange(view, if (isLocked) "Focus locked" else "Focus unlocked")
        }

        fun settingsChanged(view: View, settingName: String, newValue: String) {
            announceStatusChange(view, "$settingName changed to $newValue")
        }

        fun errorOccurred(view: View, errorMessage: String) {
            announceStatusChange(view, "Error: $errorMessage")
        }
    }

    /**
     * Set up heading structure for screen readers
     */
    fun setupHeading(textView: TextView, level: Int = 1) {
        ViewCompat.setAccessibilityDelegate(textView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isHeading = true
                info.setHintText("Heading level $level")
            }
        })
    }

    /**
     * Set up grouped controls for easier navigation
     */
    fun setupControlGroup(
        containerView: View,
        groupName: String,
        controls: List<View>
    ) {
        containerView.contentDescription = "$groupName controls"

        ViewCompat.setAccessibilityDelegate(containerView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "ViewGroup"
                info.setHintText("Contains ${controls.size} controls")
            }
        })

        // Set up traversal order
        controls.forEachIndexed { index, control ->
            // Note: setAccessibilityTraversalAfter requires API 22+
            // Manual ordering handled through view hierarchy
        }
    }

    /**
     * Enable high contrast mode support
     */
    fun enableHighContrastSupport(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                // Add custom actions for high contrast users
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                        "View detailed information"
                    )
                )
            }
        })
    }
}