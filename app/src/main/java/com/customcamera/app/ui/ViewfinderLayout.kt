package com.customcamera.app.ui

import androidx.constraintlayout.widget.ConstraintSet
import com.customcamera.app.R
import com.customcamera.app.databinding.ActivityCameraBinding

/**
 * Switches the viewfinder controls between the portrait arrangement declared in
 * `activity_camera.xml` and a landscape one built from it.
 *
 * Landscape (large screens on Android 16+, which ignore the portrait lock, and
 * landscape multi-window): the shutter row becomes a vertical rail on the end
 * side — flip camera at the top, shutter centred, gallery at the bottom — and
 * everything else (top bar, mode tabs, zoom/Pro/scan panels, status chips)
 * spans the area beside the rail. Done with ConstraintSets rather than a
 * `layout-land` resource because the activity handles orientation changes
 * itself: recreating it would rebind the camera and stop a recording.
 *
 * Margins that depend on window insets are not part of either set; the
 * activity re-applies them after every [apply].
 */
internal class ViewfinderLayout(private val binding: ActivityCameraBinding) {

    private val portraitControls = ConstraintSet().apply { clone(binding.controls) }
    private val portraitRow = ConstraintSet().apply { clone(binding.bottomRow) }
    private val landscapeControls by lazy { buildLandscapeControls() }
    private val landscapeRow by lazy { buildLandscapeRow() }

    /** Arrangement currently applied; null until the first [apply]. */
    var landscape: Boolean? = null
        private set

    /** Applies the arrangement; returns true when it changed. */
    fun apply(landscape: Boolean): Boolean {
        if (this.landscape == landscape) return false
        this.landscape = landscape
        (if (landscape) landscapeControls else portraitControls).applyTo(binding.controls)
        (if (landscape) landscapeRow else portraitRow).applyTo(binding.bottomRow)
        return true
    }

    private fun buildLandscapeControls() = ConstraintSet().apply {
        clone(portraitControls)
        val parent = ConstraintSet.PARENT_ID
        val rail = R.id.bottomRow
        val railWidth = binding.root.resources.getDimensionPixelSize(R.dimen.bottom_row_height)

        // Shutter rail: full height on the end side.
        clear(rail)
        constrainWidth(rail, railWidth)
        constrainHeight(rail, ConstraintSet.MATCH_CONSTRAINT)
        connect(rail, ConstraintSet.TOP, parent, ConstraintSet.TOP)
        connect(rail, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)
        connect(rail, ConstraintSet.END, parent, ConstraintSet.END)

        // Mode tabs drop to the bottom edge beside the rail; the panels stay above them.
        clear(R.id.modeTabs, ConstraintSet.BOTTOM)
        connect(R.id.modeTabs, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)

        // Everything that spanned the full width now stops at the rail.
        listOf(
            R.id.topScrim, R.id.topBar, R.id.recordingChip, R.id.torchBar, R.id.countdownText,
            R.id.statusText, R.id.aboveModeArea, R.id.bottomScrim, R.id.modeTabs,
        ).forEach { id ->
            clear(id, ConstraintSet.END)
            connect(id, ConstraintSet.END, rail, ConstraintSet.START)
        }
    }

    private fun buildLandscapeRow() = ConstraintSet().apply {
        clone(portraitRow)
        val parent = ConstraintSet.PARENT_ID
        val shutter = R.id.shutterButton
        // Flip above the shutter, gallery/pause below, both centred on the rail.
        listOf(R.id.flipButton, R.id.leftSlot).forEach { id ->
            clear(id)
            constrainWidth(id, binding.root.resources.getDimensionPixelSize(R.dimen.thumb_size))
            constrainHeight(id, binding.root.resources.getDimensionPixelSize(R.dimen.thumb_size))
            connect(id, ConstraintSet.START, parent, ConstraintSet.START)
            connect(id, ConstraintSet.END, parent, ConstraintSet.END)
        }
        connect(R.id.flipButton, ConstraintSet.TOP, parent, ConstraintSet.TOP)
        connect(R.id.flipButton, ConstraintSet.BOTTOM, shutter, ConstraintSet.TOP)
        connect(R.id.leftSlot, ConstraintSet.TOP, shutter, ConstraintSet.BOTTOM)
        connect(R.id.leftSlot, ConstraintSet.BOTTOM, parent, ConstraintSet.BOTTOM)
    }
}
