package com.customcamera.app.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.customcamera.app.BuildConfig
import com.customcamera.app.R
import com.customcamera.app.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Hosts [SettingsFragment] under a Material toolbar. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}

/**
 * Preference screen backed by res/xml/preferences.xml. The viewfinder reads
 * the same SharedPreferences through [AppSettings] and rebinds on return.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        findPreference<SwitchPreferenceCompat>(AppSettings.Keys.LOCATION)?.isChecked = granted
        if (!granted) Toast.makeText(requireContext(), R.string.permission_location_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(KEY_VERSION)?.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        findPreference<SwitchPreferenceCompat>(AppSettings.Keys.LOCATION)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true && !hasLocationPermission()) {
                // Turn the switch on only after the user grants permission.
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                false
            } else {
                true
            }
        }

        findPreference<Preference>(KEY_PRIVACY)?.setOnPreferenceClickListener {
            openUrl(getString(R.string.privacy_url))
            true
        }
        findPreference<Preference>(KEY_LICENSES)?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_licenses)
                .setMessage(R.string.licenses_body)
                .setPositiveButton(R.string.close, null)
                .show()
            true
        }
        findPreference<Preference>(KEY_RESET)?.setOnPreferenceClickListener {
            confirmReset()
            true
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Edge-to-edge (enforced at targetSdk 35+): keep the last rows clear of the navigation bar.
        listView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_reset)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.pref_reset) { _, _ ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit { clear() }
                PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences, true)
                onCreatePreferences(null, null)
                Toast.makeText(requireContext(), R.string.pref_reset_done, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_app_for_action, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val KEY_VERSION = "about_version"
        private const val KEY_PRIVACY = "about_privacy"
        private const val KEY_LICENSES = "about_licenses"
        private const val KEY_RESET = "action_reset"
    }
}
