package com.customcamera.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.customcamera.app.R
import com.customcamera.app.core.CaptureMode
import com.customcamera.app.ui.CameraActivity

/**
 * Launcher long-press shortcuts (Selfie, Video, Scan).
 *
 * Published dynamically rather than from res/xml because static shortcut XML
 * hard-codes the target package, which breaks the `.debug` applicationId.
 */
object AppShortcuts {

    fun publish(context: Context) {
        val shortcuts = listOf(
            build(context, "selfie", R.string.shortcut_selfie, R.drawable.ic_shortcut_selfie, CaptureMode.PHOTO, front = true),
            build(context, "video", R.string.shortcut_video, R.drawable.ic_shortcut_video, CaptureMode.VIDEO, front = null),
            build(context, "scan", R.string.shortcut_scan, R.drawable.ic_shortcut_scan, CaptureMode.SCAN, front = null),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun build(context: Context, id: String, label: Int, icon: Int, mode: CaptureMode, front: Boolean?): ShortcutInfoCompat {
        val intent = Intent(context, CameraActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(CameraActivity.EXTRA_MODE, mode.key)
        front?.let { intent.putExtra(CameraActivity.EXTRA_FRONT, it) }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
    }
}
