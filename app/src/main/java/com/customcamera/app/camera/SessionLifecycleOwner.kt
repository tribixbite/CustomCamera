package com.customcamera.app.camera

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A LifecycleOwner that mirrors [parent] until [close] moves it to DESTROYED.
 *
 * CameraX keeps one LifecycleCamera per (lifecycle owner, camera pair) and reuses it
 * on every later bind, and the CompositionSettings of a concurrent (DUAL) session are
 * read only when that LifecycleCamera is created (LifecycleCameraProviderImpl,
 * camera-lifecycle 1.6.2). Rebinding with a moved inset under the activity's own owner
 * therefore kept the old layout until the app restarted. Binding each DUAL session to
 * a fresh owner, and destroying the previous one so CameraX drops its LifecycleCamera,
 * applies the new layout. Main thread only, like LifecycleRegistry.
 */
internal class SessionLifecycleOwner(private val parent: LifecycleOwner) : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    // Adding the observer replays the parent's events up to its current state.
    private val observer = LifecycleEventObserver { _, event -> registry.handleLifecycleEvent(event) }

    init {
        parent.lifecycle.addObserver(observer)
    }

    override val lifecycle: Lifecycle get() = registry

    fun close() {
        parent.lifecycle.removeObserver(observer)
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }
}
