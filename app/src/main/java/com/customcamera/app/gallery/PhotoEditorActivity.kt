package com.customcamera.app.gallery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.customcamera.app.R
import com.customcamera.app.core.CropAspect
import com.customcamera.app.core.PhotoEdit
import com.customcamera.app.databinding.ActivityPhotoEditorBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.EnumMap

/**
 * Non-destructive crop / rotate / flip editor for one photo. Launch with
 * [intent] (the photo's content Uri as data). On success the activity finishes
 * with RESULT_OK and the NEW photo's Uri as result data; the original is never
 * modified.
 */
class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoEditorBinding
    private val viewModel: PhotoEditorViewModel by viewModels()
    private val chips = EnumMap<CropAspect, Chip>(CropAspect::class.java)

    /** Confirms before throwing away a non-trivial edit; swallows Back while saving. */
    private val backGuard = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (viewModel.saveState.value == PhotoEditorViewModel.SaveState.Saving) return
            MaterialAlertDialogBuilder(this@PhotoEditorActivity)
                .setTitle(R.string.editor_discard_title)
                .setMessage(R.string.editor_discard_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.editor_discard) { _, _ -> finish() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.data ?: run {
            finish()
            return
        }
        binding = ActivityPhotoEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, backGuard)
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.rotateButton.setOnClickListener { binding.cropView.rotateClockwise() }
        binding.flipButton.setOnClickListener { binding.cropView.flipHorizontally() }
        binding.resetButton.setOnClickListener { binding.cropView.reset() }
        buildAspectChips()

        binding.cropView.edit = viewModel.edit
        binding.cropView.onEditChanged = { edit ->
            viewModel.edit = edit
            syncControls(edit)
        }
        syncControls(viewModel.edit)

        val metrics = resources.displayMetrics
        viewModel.load(source, maxOf(metrics.widthPixels, metrics.heightPixels))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.preview.collect(::renderPreview) }
                launch { viewModel.saveState.collect(::renderSaveState) }
            }
        }
    }

    /**
     * Edge-to-edge: the toolbar takes the status bar / cutout. Portrait: the tools
     * below the photo take the nav bar. Landscape (`layout-land`, tools in a column
     * on the end side): the crop area takes the start and bottom edges, the column
     * the end and bottom edges.
     */
    private fun applyInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val sidePanel = resources.getBoolean(R.bool.editor_side_panel)
        val rtl = binding.root.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val cropPadding = resources.getDimensionPixelSize(R.dimen.editor_crop_padding)
        val panel = binding.bottomPanel
        val panelBase = intArrayOf(panel.paddingLeft, panel.paddingRight, panel.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.toolbar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            if (sidePanel) {
                // The panel sits on the end side: right in LTR, left in RTL.
                val cropLeft = if (rtl) 0 else bars.left
                val cropRight = if (rtl) bars.right else 0
                binding.cropView.updatePadding(
                    left = cropPadding + cropLeft, right = cropPadding + cropRight, bottom = cropPadding + bars.bottom,
                )
                panel.updatePadding(
                    left = panelBase[0] + (bars.left - cropLeft), right = panelBase[1] + (bars.right - cropRight),
                    bottom = panelBase[2] + bars.bottom,
                )
            } else {
                binding.cropView.updatePadding(left = cropPadding + bars.left, right = cropPadding + bars.right)
                panel.updatePadding(left = bars.left, right = bars.right, bottom = panelBase[2] + bars.bottom)
            }
            insets
        }
    }

    private fun buildAspectChips() {
        val inflater = LayoutInflater.from(this)
        for (aspect in CropAspect.entries) {
            val chip = inflater.inflate(R.layout.item_aspect_chip, binding.aspectChips, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = aspect
            chip.text = when (aspect) {
                CropAspect.FREE -> getString(R.string.editor_aspect_free)
                CropAspect.ORIGINAL -> getString(R.string.editor_aspect_original)
                else -> aspect.key
            }
            chip.contentDescription = getString(R.string.cd_aspect, chip.text)
            binding.aspectChips.addView(chip)
            chips[aspect] = chip
        }
        binding.aspectChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val aspect = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? CropAspect }
                ?: return@setOnCheckedStateChangeListener
            // Rotation updates the chip to match the edit; only a user choice refits the crop.
            if (aspect != binding.cropView.edit.aspect) binding.cropView.setAspect(aspect)
        }
    }

    /** Reflects [edit] in the chips, Reset/Save enablement and the back guard. */
    private fun syncControls(edit: PhotoEdit) {
        chips[edit.aspect]?.takeUnless { it.isChecked }?.isChecked = true
        val saving = viewModel.saveState.value == PhotoEditorViewModel.SaveState.Saving
        val changed = !edit.isNoOp || edit.aspect != CropAspect.FREE
        binding.resetButton.isEnabled = changed && !saving
        binding.saveButton.isEnabled = !edit.isNoOp && binding.cropView.hasImage && !saving
        backGuard.isEnabled = saving || !edit.isNoOp
    }

    private fun renderPreview(preview: PhotoEditorViewModel.Preview) {
        binding.loading.isVisible = preview == PhotoEditorViewModel.Preview.Loading
        when (preview) {
            is PhotoEditorViewModel.Preview.Ready -> {
                binding.cropView.setImage(preview.bitmap)
                syncControls(binding.cropView.edit)
            }
            PhotoEditorViewModel.Preview.Failed -> {
                Toast.makeText(this, R.string.editor_load_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
            PhotoEditorViewModel.Preview.Loading -> Unit
        }
    }

    private fun renderSaveState(state: PhotoEditorViewModel.SaveState) {
        val saving = state == PhotoEditorViewModel.SaveState.Saving
        binding.savingOverlay.isVisible = saving
        listOf(binding.rotateButton, binding.flipButton, binding.aspectChips, binding.cropView).forEach { it.isEnabled = !saving }
        syncControls(binding.cropView.edit)
        when (state) {
            is PhotoEditorViewModel.SaveState.Saved -> {
                setResult(RESULT_OK, Intent().setData(state.uri))
                finish()
            }
            PhotoEditorViewModel.SaveState.Failed -> {
                Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show()
                viewModel.clearFailure()
            }
            else -> Unit
        }
    }

    /** True once the preview is decoded and shown, i.e. the editor accepts edits. */
    @get:VisibleForTesting
    val isPreviewReady: Boolean
        get() = binding.cropView.hasImage

    /** Picks an aspect preset through the same chip path a tap uses. */
    @VisibleForTesting
    fun selectAspectForTest(aspect: CropAspect) {
        chips.getValue(aspect).isChecked = true
    }

    companion object {
        /** Intent that opens the editor on the photo at [uri]. */
        fun intent(context: Context, uri: Uri): Intent =
            Intent(context, PhotoEditorActivity::class.java).setData(uri)
    }
}
