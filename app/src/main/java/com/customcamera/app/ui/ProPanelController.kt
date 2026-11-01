package com.customcamera.app.ui

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.view.View
import androidx.core.view.isVisible
import com.customcamera.app.R
import com.customcamera.app.camera.CameraController
import com.customcamera.app.camera.CameraState
import com.customcamera.app.camera.ManualSettings
import com.customcamera.app.core.ExposureMath
import com.customcamera.app.databinding.PanelProBinding
import kotlin.math.roundToInt

/**
 * Binds the Pro-mode panel (parameter chips + one shared slider) to the
 * [CameraController]'s manual Camera2 controls.
 */
class ProPanelController(
    private val context: Context,
    private val binding: PanelProBinding,
    private val camera: CameraController,
) {
    private enum class Param { ISO, SHUTTER, WB, FOCUS, EV }

    private var selected: Param? = null
    private var isoStops: List<Int> = emptyList()
    private var shutterStops: List<Long> = emptyList()
    private var awbModes: List<Int> = emptyList()
    private var lastState: CameraState? = null

    /** Suppresses listener feedback while the slider is configured programmatically. */
    private var updatingSlider = false

    init {
        binding.proChips.setOnCheckedStateChangeListener { _, ids ->
            selected = when (ids.firstOrNull()) {
                R.id.chipIso -> Param.ISO
                R.id.chipShutter -> Param.SHUTTER
                R.id.chipWb -> Param.WB
                R.id.chipFocus -> Param.FOCUS
                R.id.chipEv -> Param.EV
                else -> null
            }
            configureSlider()
        }
        binding.proSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !updatingSlider) onSlider(value)
        }
        binding.proAuto.setOnClickListener { resetSelectedToAuto() }
    }

    /** Re-reads capabilities and labels from the latest camera state. */
    fun render(state: CameraState) {
        val capsChanged = lastState?.manualCapabilities != state.manualCapabilities
        lastState = state
        val caps = state.manualCapabilities
        if (capsChanged) {
            isoStops = caps.isoRange?.let { ExposureMath.isoStops(it.first, it.last) }.orEmpty()
            shutterStops = caps.exposureRangeNs?.let { ExposureMath.shutterStops(it.first, it.last) }.orEmpty()
            awbModes = listOf(ManualSettings.AWB_AUTO) + caps.awbModes.filter { it != ManualSettings.AWB_AUTO }
            binding.chipIso.isVisible = isoStops.size > 1
            binding.chipShutter.isVisible = shutterStops.size > 1
            binding.chipFocus.isVisible = caps.manualFocus
            binding.chipWb.isVisible = awbModes.size > 1
            binding.chipEv.isVisible = state.evSupported
            binding.proUnsupported.isVisible = !caps.manualSensor
            // Drop a selection whose chip just disappeared.
            val checked = binding.proChips.checkedChipId
            if (checked != View.NO_ID && !binding.root.findViewById<View>(checked).isVisible) binding.proChips.clearCheck()
        }
        refreshLabels()
        if (selected == Param.EV && !updatingSlider) syncSliderValue()
    }

    /** Updates chip text, including live auto readings (called periodically in Pro mode). */
    fun refreshLabels() {
        val state = lastState ?: return
        val m = state.manual
        val live = camera.liveExposure()
        binding.chipIso.text = when {
            m.iso != null -> "ISO ${m.iso}"
            live != null -> context.getString(R.string.pro_value_auto, "ISO ${live.iso}")
            else -> context.getString(R.string.pro_iso)
        }
        binding.chipShutter.text = when {
            m.exposureNs != null -> ExposureMath.formatShutter(m.exposureNs)
            live != null && live.exposureNs > 0 -> context.getString(R.string.pro_value_auto, ExposureMath.formatShutter(live.exposureNs))
            else -> context.getString(R.string.pro_shutter)
        }
        binding.chipWb.text = if (m.awbMode == ManualSettings.AWB_AUTO) context.getString(R.string.pro_wb) else wbLabel(m.awbMode)
        binding.chipFocus.text = m.focusDiopters?.let { ExposureMath.formatFocusDistance(it) } ?: context.getString(R.string.focus_auto)
        binding.chipEv.text = context.getString(R.string.pro_ev_value, ExposureMath.formatEv(state.evIndex, state.evStep))
        updateValueLabel()
    }

    private fun configureSlider() {
        val state = lastState
        val param = selected
        binding.proSliderRow.isVisible = param != null && state != null
        if (param == null || state == null) return
        val count = when (param) {
            Param.ISO -> isoStops.size
            Param.SHUTTER -> shutterStops.size
            Param.WB -> awbModes.size
            Param.FOCUS -> FOCUS_STEPS + 1
            Param.EV -> state.evMax - state.evMin + 1
        }
        updatingSlider = true
        binding.proSlider.valueFrom = 0f
        binding.proSlider.valueTo = (count - 1).coerceAtLeast(1).toFloat()
        binding.proSlider.isEnabled = count > 1
        // EV only acts while exposure is automatic.
        binding.proAuto.isVisible = param != Param.EV
        updatingSlider = false
        syncSliderValue()
    }

    private fun syncSliderValue() {
        val state = lastState ?: return
        val m = state.manual
        val live = camera.liveExposure()
        val index = when (selected ?: return) {
            Param.ISO -> ExposureMath.nearestIsoIndex(isoStops, m.iso ?: live?.iso ?: 100)
            Param.SHUTTER -> ExposureMath.nearestIndex(shutterStops, m.exposureNs ?: live?.exposureNs ?: 16_666_666L)
            Param.WB -> awbModes.indexOf(m.awbMode).coerceAtLeast(0)
            Param.FOCUS -> {
                val d = m.focusDiopters ?: live?.focusDiopters ?: 0f
                (ExposureMath.dioptersToSlider(d, state.manualCapabilities.minFocusDiopters) * FOCUS_STEPS).roundToInt()
            }
            Param.EV -> state.evIndex - state.evMin
        }
        updatingSlider = true
        binding.proSlider.value = index.toFloat().coerceIn(binding.proSlider.valueFrom, binding.proSlider.valueTo)
        updatingSlider = false
        updateValueLabel()
    }

    private fun onSlider(value: Float) {
        val state = lastState ?: return
        val i = value.roundToInt()
        val m = state.manual
        when (selected ?: return) {
            Param.ISO -> camera.setManual(m.copy(iso = isoStops[i]))
            Param.SHUTTER -> camera.setManual(m.copy(exposureNs = shutterStops[i]))
            Param.WB -> camera.setManual(m.copy(awbMode = awbModes[i]))
            Param.FOCUS -> camera.setManual(
                m.copy(focusDiopters = ExposureMath.sliderToDiopters(i.toFloat() / FOCUS_STEPS, state.manualCapabilities.minFocusDiopters)),
            )
            Param.EV -> camera.setExposureIndex(state.evMin + i)
        }
    }

    private fun resetSelectedToAuto() {
        val m = lastState?.manual ?: return
        when (selected ?: return) {
            Param.ISO -> camera.setManual(m.copy(iso = null))
            Param.SHUTTER -> camera.setManual(m.copy(exposureNs = null))
            Param.WB -> camera.setManual(m.copy(awbMode = ManualSettings.AWB_AUTO))
            Param.FOCUS -> camera.setManual(m.copy(focusDiopters = null))
            Param.EV -> camera.setExposureIndex(0)
        }
        syncSliderValue()
    }

    private fun updateValueLabel() {
        val state = lastState ?: return
        val m = state.manual
        binding.proValue.text = when (selected) {
            Param.ISO -> m.iso?.toString() ?: context.getString(R.string.pro_auto)
            Param.SHUTTER -> m.exposureNs?.let { ExposureMath.formatShutter(it) } ?: context.getString(R.string.pro_auto)
            Param.WB -> wbLabel(m.awbMode)
            Param.FOCUS -> m.focusDiopters?.let { ExposureMath.formatFocusDistance(it) } ?: context.getString(R.string.focus_auto)
            Param.EV -> ExposureMath.formatEv(state.evIndex, state.evStep)
            null -> ""
        }
    }

    private fun wbLabel(mode: Int): String = context.getString(
        when (mode) {
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> R.string.wb_incandescent
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> R.string.wb_fluorescent
            CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> R.string.wb_warm_fluorescent
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> R.string.wb_daylight
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> R.string.wb_cloudy
            CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> R.string.wb_twilight
            CameraMetadata.CONTROL_AWB_MODE_SHADE -> R.string.wb_shade
            else -> R.string.wb_auto
        },
    )

    companion object {
        /** Resolution of the focus slider (0 = infinity, max = closest focus). */
        private const val FOCUS_STEPS = 100
    }
}
