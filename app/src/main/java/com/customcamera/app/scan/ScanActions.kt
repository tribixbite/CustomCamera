package com.customcamera.app.scan

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.customcamera.app.R
import com.customcamera.app.core.ScanContent

/**
 * Presentation and actions for scanned codes: label, icon, primary action.
 */
object ScanActions {

    data class Presentation(
        @param:StringRes val typeLabel: Int,
        @param:DrawableRes val icon: Int,
        val displayText: String,
        @param:StringRes val primaryAction: Int?,
    )

    fun present(context: Context, content: ScanContent): Presentation = when (content) {
        is ScanContent.Url -> Presentation(R.string.scan_type_url, R.drawable.ic_link, content.url, R.string.scan_open)
        is ScanContent.Wifi -> Presentation(
            R.string.scan_type_wifi, R.drawable.ic_wifi,
            context.getString(R.string.scan_wifi_detail, content.ssid, content.security),
            R.string.scan_connect,
        )
        is ScanContent.Email -> Presentation(R.string.scan_type_email, R.drawable.ic_email, content.address, R.string.scan_email)
        is ScanContent.Phone -> Presentation(R.string.scan_type_phone, R.drawable.ic_phone, content.number, R.string.scan_call)
        is ScanContent.Sms -> Presentation(
            R.string.scan_type_sms, R.drawable.ic_email,
            listOfNotNull(content.number, content.message).joinToString("\n"), R.string.scan_message,
        )
        is ScanContent.Geo -> Presentation(R.string.scan_type_geo, R.drawable.ic_place, "${content.latitude}, ${content.longitude}", R.string.scan_map)
        is ScanContent.Text -> Presentation(R.string.scan_type_text, R.drawable.ic_text, content.raw, null)
    }

    /** Runs the primary action (open link, dial, join Wi-Fi, ...). */
    fun performPrimary(context: Context, content: ScanContent) {
        when (content) {
            is ScanContent.Url -> launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(content.url)))
            is ScanContent.Phone -> launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(content.number)}")))
            is ScanContent.Sms -> launch(
                context,
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(content.number)}")).putExtra("sms_body", content.message),
            )
            is ScanContent.Email -> launch(
                context,
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(content.address))
                    content.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    content.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                },
            )
            is ScanContent.Geo -> launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${content.latitude},${content.longitude}?q=${content.latitude},${content.longitude}")))
            is ScanContent.Wifi -> connectWifi(context, content)
            is ScanContent.Text -> copy(context, content.raw)
        }
    }

    /**
     * API 30+: hands the network to the system "Add Wi-Fi networks" dialog, which
     * needs no Wi-Fi permissions. Older versions copy the password and open Wi-Fi settings.
     */
    private fun connectWifi(context: Context, wifi: ScanContent.Wifi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
            val password = wifi.password
            val security = wifi.security.uppercase()
            if (password != null) {
                when {
                    security.contains("SAE") || security.contains("WPA3") -> builder.setWpa3Passphrase(password)
                    security.startsWith("WPA") -> builder.setWpa2Passphrase(password)
                    // WEP is not supported by suggestions; fall through to the settings path.
                    else -> return legacyWifi(context, wifi)
                }
            }
            val suggestion = try {
                builder.build()
            } catch (e: IllegalStateException) {
                return legacyWifi(context, wifi)
            } catch (e: IllegalArgumentException) {
                return legacyWifi(context, wifi)
            }
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
                Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion),
            )
            launch(context, intent)
        } else {
            legacyWifi(context, wifi)
        }
    }

    private fun legacyWifi(context: Context, wifi: ScanContent.Wifi) {
        wifi.password?.let {
            copy(context, it, showToast = false)
            Toast.makeText(context, R.string.scan_wifi_password_copied, Toast.LENGTH_LONG).show()
        }
        launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun copy(context: Context, text: String, showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
        // Android 13+ shows its own clipboard confirmation.
        if (showToast && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, Intent.createChooser(send, null))
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_for_action, Toast.LENGTH_SHORT).show()
        }
    }
}
