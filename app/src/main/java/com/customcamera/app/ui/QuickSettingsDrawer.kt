package com.customcamera.app.ui

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.customcamera.app.R
import com.customcamera.app.engine.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Quick settings drawer that slides out from the side for easy access
 * to common camera settings without leaving the camera interface.
 */
class QuickSettingsDrawer(
    private val context: Context,
    private val parentView: ViewGroup,
    private val settingsManager: SettingsManager
) {

    private var drawerView: LinearLayout? = null
    private var isVisible = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Callbacks for settings changes
    var onGridToggled: ((Boolean) -> Unit)? = null
    var onBarcodeToggled: ((Boolean) -> Unit)? = null
    var onFlashModeChanged: ((String) -> Unit)? = null
    var onCameraSwitch: (() -> Unit)? = null
    var onFullSettingsRequested: (() -> Unit)? = null

    /**
     * Initialize and create the drawer UI
     */
    fun initialize() {
        createDrawerView()
        Log.i(TAG, "Quick settings drawer initialized")
    }

    private fun createDrawerView() {
        drawerView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000")) // Semi-transparent black
            setPadding(32, 48, 32, 48)
            elevation = 16f

            layoutParams = FrameLayout.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.7f).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            ).apply {
                // Start off-screen to the right
                setMargins(0, 0, -((context.resources.displayMetrics.widthPixels * 0.7f).toInt()), 0)
            }

            visibility = View.GONE
        }

        // Add title
        addTitle("Quick Settings")

        // Add settings controls
        addGridToggle()
        addBarcodeToggle()
        addFlashModeSelector()
        addDivider()
        addCameraSwitchButton()
        addFullSettingsButton()
        addCloseButton()

        parentView.addView(drawerView)
    }

    private fun addTitle(title: String) {
        val titleView = TextView(context).apply {
            text = title
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        drawerView?.addView(titleView)
    }

    private fun addGridToggle() {
        coroutineScope.launch {
            val container = createSettingRow("Grid Overlay", "Show composition grid")
            val toggle = Switch(context).apply {
                isChecked = settingsManager.gridOverlay.value
                setOnCheckedChangeListener { _, isChecked ->
                    settingsManager.setGridOverlay(isChecked)
                    onGridToggled?.invoke(isChecked)
                    Log.i(TAG, "Grid toggled: $isChecked")
                }
            }
            container.addView(toggle)
            drawerView?.addView(container)
        }
    }

    private fun addBarcodeToggle() {
        val container = createSettingRow("Barcode Scanning", "Enable QR/barcode detection")
        val toggle = Switch(context).apply {
            isChecked = settingsManager.isPluginEnabled("Barcode")
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setPluginEnabled("Barcode", isChecked)
                onBarcodeToggled?.invoke(isChecked)
                Log.i(TAG, "Barcode scanning toggled: $isChecked")
            }
        }
        container.addView(toggle)
        drawerView?.addView(container)
    }

    private fun addFlashModeSelector() {
        coroutineScope.launch {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val label = TextView(context).apply {
                text = "Flash Mode"
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 8)
            }
            container.addView(label)

            val flashModes = arrayOf("Auto", "On", "Off")
            val currentMode = settingsManager.flashMode.value
            val currentIndex = when (currentMode) {
                "on" -> 1
                "off" -> 2
                else -> 0
            }

            val spinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, flashModes).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(currentIndex)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val mode = when (position) {
                            1 -> "on"
                            2 -> "off"
                            else -> "auto"
                        }
                        settingsManager.setFlashMode(mode)
                        onFlashModeChanged?.invoke(mode)
                        Log.i(TAG, "Flash mode changed: $mode")
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            container.addView(spinner)
            drawerView?.addView(container)
        }
    }

    private fun addDivider() {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            setBackgroundColor(Color.parseColor("#40FFFFFF"))
        }
        drawerView?.addView(divider)
    }

    private fun addCameraSwitchButton() {
        val button = Button(context).apply {
            text = "📷 Switch Camera"
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setOnClickListener {
                onCameraSwitch?.invoke()
                hide()
                Log.i(TAG, "Camera switch requested from drawer")
            }
        }
        drawerView?.addView(button)
    }

    private fun addFullSettingsButton() {
        val button = Button(context).apply {
            text = "⚙️ Full Settings"
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setOnClickListener {
                onFullSettingsRequested?.invoke()
                hide()
                Log.i(TAG, "Full settings requested from drawer")
            }
        }
        drawerView?.addView(button)
    }

    private fun addCloseButton() {
        val button = Button(context).apply {
            text = "✕ Close"
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#40FFFFFF"))
            setOnClickListener {
                hide()
            }
        }
        drawerView?.addView(button)
    }

    private fun createSettingRow(title: String, description: String): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        textContainer.addView(titleView)

        val descView = TextView(context).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        textContainer.addView(descView)

        container.addView(textContainer)
        return container
    }

    /**
     * Show the drawer with slide animation
     */
    fun show() {
        if (isVisible) return

        drawerView?.let { drawer ->
            drawer.visibility = View.VISIBLE
            drawer.animate()
                .translationX(-((context.resources.displayMetrics.widthPixels * 0.7f)))
                .setDuration(300)
                .withStartAction {
                    isVisible = true
                    Log.i(TAG, "Quick settings drawer showing")
                }
                .start()
        }
    }

    /**
     * Hide the drawer with slide animation
     */
    fun hide() {
        if (!isVisible) return

        drawerView?.let { drawer ->
            drawer.animate()
                .translationX(0f)
                .setDuration(300)
                .withEndAction {
                    drawer.visibility = View.GONE
                    isVisible = false
                    Log.i(TAG, "Quick settings drawer hidden")
                }
                .start()
        }
    }

    /**
     * Toggle drawer visibility
     */
    fun toggle() {
        if (isVisible) hide() else show()
    }

    companion object {
        private const val TAG = "QuickSettingsDrawer"
    }
}
