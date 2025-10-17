package com.customcamera.app

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.customcamera.app.databinding.ActivitySimpleSettingsRecyclerviewBinding
import com.customcamera.app.engine.SettingsManager
import com.customcamera.app.engine.plugins.PluginCategory
import com.customcamera.app.engine.plugins.PluginRegistry
import com.customcamera.app.ui.settings.SettingsAdapter
import com.customcamera.app.ui.settings.SettingsListItem
import kotlinx.coroutines.launch

/**
 * Settings activity with RecyclerView for smooth scrolling performance
 */
class SimpleSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleSettingsRecyclerviewBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var settingsAdapter: SettingsAdapter
    private var availableCameras: List<Pair<Int, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SimpleSettingsActivity onCreate")

        // Initialize ViewBinding
        binding = ActivitySimpleSettingsRecyclerviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Camera Settings"
        }

        // Initialize settings and adapter
        try {
            settingsManager = SettingsManager(this)
            pluginRegistry = PluginRegistry(this)

            // Setup RecyclerView
            setupRecyclerView()

            // Detect available cameras and build settings list
            lifecycleScope.launch {
                try {
                    detectAvailableCameras()
                    buildAndSubmitSettingsList()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to detect cameras", e)
                    buildAndSubmitSettingsList() // Still build UI even if camera detection fails
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun detectAvailableCameras() {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            val cameras = cameraProvider.availableCameraInfos

            availableCameras = cameras.mapIndexed { index, cameraInfo ->
                val facing = when (cameraInfo.lensFacing) {
                    androidx.camera.core.CameraSelector.LENS_FACING_FRONT -> "Front"
                    androidx.camera.core.CameraSelector.LENS_FACING_BACK -> "Back"
                    else -> "External"
                }
                Pair(index, "Camera $index ($facing)")
            }

            Log.i(TAG, "Detected ${availableCameras.size} cameras")
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting cameras", e)
            availableCameras = emptyList()
        }
    }

    /**
     * Setup RecyclerView with adapter and layout manager
     */
    private fun setupRecyclerView() {
        // Create adapter with callbacks
        settingsAdapter = SettingsAdapter(
            onCameraSelected = { cameraIndex ->
                settingsManager.setDefaultCameraIndex(cameraIndex)
                Toast.makeText(
                    this,
                    "Main camera set to: ${availableCameras.getOrNull(cameraIndex)?.second ?: "Camera $cameraIndex"}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "Main camera changed to index: $cameraIndex")
            },
            onPluginToggled = { pluginId, isEnabled ->
                settingsManager.setPluginEnabled(pluginId, isEnabled)

                // Find plugin display name for toast
                val plugin = pluginRegistry.getPluginsByCategory().values
                    .flatten()
                    .find { it.name == pluginId }

                Toast.makeText(
                    this,
                    "${plugin?.displayName ?: pluginId} ${if (isEnabled) "enabled" else "disabled"}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.i(TAG, "$pluginId plugin changed to: $isEnabled")
            }
        )

        // Set up RecyclerView
        binding.settingsRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@SimpleSettingsActivity)
            adapter = settingsAdapter
        }
    }

    /**
     * Build list of settings items and submit to adapter
     */
    private fun buildAndSubmitSettingsList() {
        try {
            val items = mutableListOf<SettingsListItem>()

            // Camera Selection Section
            if (availableCameras.isNotEmpty()) {
                items.add(SettingsListItem.CategoryHeader("Camera Selection"))

                val selectedCameraIndex = settingsManager.defaultCameraIndex.value
                availableCameras.forEach { (index, name) ->
                    items.add(
                        SettingsListItem.CameraItem(
                            cameraIndex = index,
                            cameraName = name,
                            isSelected = index == selectedCameraIndex
                        )
                    )
                }

                items.add(SettingsListItem.SectionDivider)
            }

            // Plugin Settings grouped by category
            val pluginsByCategory = pluginRegistry.getPluginsByCategory()

            // Category display names
            val categoryNames = mapOf(
                PluginCategory.OVERLAYS to "Overlay Plugins",
                PluginCategory.ANALYSIS to "Analysis Plugins",
                PluginCategory.CONTROLS to "Control Plugins",
                PluginCategory.AI to "AI-Powered Features",
                PluginCategory.CAPTURE to "Capture Features"
            )

            // Iterate through categories in order
            val orderedCategories = listOf(
                PluginCategory.OVERLAYS,
                PluginCategory.ANALYSIS,
                PluginCategory.CONTROLS,
                PluginCategory.AI,
                PluginCategory.CAPTURE
            )

            for (category in orderedCategories) {
                val plugins = pluginsByCategory[category] ?: continue
                if (plugins.isEmpty()) continue

                // Add category header
                items.add(SettingsListItem.CategoryHeader(categoryNames[category] ?: category.name))

                // Add plugin items
                for (pluginInfo in plugins) {
                    items.add(
                        SettingsListItem.PluginItem.fromPluginInfo(
                            pluginInfo,
                            settingsManager.isPluginEnabled(pluginInfo.name)
                        )
                    )
                }

                // Add section divider after each category
                items.add(SettingsListItem.SectionDivider)
            }

            // Submit to adapter
            settingsAdapter.submitList(items)

            Log.i(TAG, "Built settings list with ${items.size} items (${pluginsByCategory.values.sumOf { it.size }} plugins)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to build settings list", e)
            Toast.makeText(this, "Settings error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "SimpleSettingsActivity"
    }
}