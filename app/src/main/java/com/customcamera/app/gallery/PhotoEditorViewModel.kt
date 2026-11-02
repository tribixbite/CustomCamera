package com.customcamera.app.gallery

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.customcamera.app.core.CropAspect
import com.customcamera.app.core.CropRect
import com.customcamera.app.core.EditTransform
import com.customcamera.app.core.PhotoEdit
import com.customcamera.app.util.BitmapDecoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for [PhotoEditorActivity] that must survive configuration changes: the
 * decoded preview, the in-progress save, and the edit itself (also kept in
 * [SavedStateHandle] so it survives process death).
 */
class PhotoEditorViewModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {

    sealed interface Preview {
        data object Loading : Preview
        data class Ready(val bitmap: Bitmap) : Preview
        data object Failed : Preview
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data class Saved(val uri: Uri) : SaveState
        data object Failed : SaveState
    }

    private val _preview = MutableStateFlow<Preview>(Preview.Loading)
    val preview: StateFlow<Preview> = _preview.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val saver = PhotoEditSaver(application)
    private var source: Uri? = null

    /** The current edit, persisted field by field in the saved-state bundle. */
    var edit: PhotoEdit
        get() = PhotoEdit(
            transform = EditTransform(state[KEY_ROTATION] ?: 0, state[KEY_FLIPPED] ?: false),
            crop = state.get<FloatArray>(KEY_CROP)?.takeIf { it.size == 4 }
                ?.let { CropRect(it[0], it[1], it[2], it[3]) } ?: CropRect.FULL,
            aspect = CropAspect.fromKey(state[KEY_ASPECT]),
        )
        set(value) {
            state[KEY_ROTATION] = value.transform.rotation
            state[KEY_FLIPPED] = value.transform.flipped
            state[KEY_CROP] = floatArrayOf(value.crop.left, value.crop.top, value.crop.right, value.crop.bottom)
            state[KEY_ASPECT] = value.aspect.key
        }

    /** Decodes a screen-sized preview of [uri] once; later calls (after rotation) are no-ops. */
    fun load(uri: Uri, maxDimension: Int) {
        if (source != null) return
        source = uri
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                BitmapDecoding.decode(getApplication<Application>().contentResolver, uri, maxDimension)
            }
            _preview.value = bitmap?.let { Preview.Ready(it) } ?: Preview.Failed
        }
    }

    /** Renders and saves the current edit as a new photo. Ignored while a save is running. */
    fun save() {
        val uri = source ?: return
        if (_saveState.value == SaveState.Saving) return
        _saveState.value = SaveState.Saving
        val snapshot = edit
        viewModelScope.launch {
            _saveState.value = try {
                SaveState.Saved(withContext(Dispatchers.IO) { saver.save(uri, snapshot) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                // An Error, not an Exception: without this the coroutine would crash the process.
                Log.w(TAG, "Out of memory saving edited copy of $uri", e)
                SaveState.Failed
            } catch (e: Exception) {
                // Decode/encode/MediaStore failures (including OOM-adjacent decode nulls) end here.
                Log.w(TAG, "Saving edited copy of $uri failed", e)
                SaveState.Failed
            }
        }
    }

    /** Acknowledges a failure once the UI has reported it. */
    fun clearFailure() {
        if (_saveState.value == SaveState.Failed) _saveState.value = SaveState.Idle
    }

    private companion object {
        const val TAG = "PhotoEditor"
        const val KEY_ROTATION = "edit_rotation"
        const val KEY_FLIPPED = "edit_flipped"
        const val KEY_CROP = "edit_crop"
        const val KEY_ASPECT = "edit_aspect"
    }
}
