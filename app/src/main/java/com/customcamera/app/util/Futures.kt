package com.customcamera.app.util

import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Direct executor: runs the listener on the completing thread. */
private val directExecutor = Executor { it.run() }

/**
 * Suspends until the future completes, propagating its result or cause.
 * Cancelling the coroutine cancels the future.
 */
suspend fun <T> ListenableFuture<T>.await(): T {
    if (isDone) return unwrap()
    return suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(unwrap())
            } catch (t: Throwable) {
                cont.resumeWithException(t)
            }
        }, directExecutor)
        cont.invokeOnCancellation { cancel(false) }
    }
}

private fun <T> ListenableFuture<T>.unwrap(): T = try {
    get()
} catch (e: ExecutionException) {
    throw e.cause ?: e
}

/** Main-thread executor shortcut. */
fun android.content.Context.mainExecutor(): Executor = ContextCompat.getMainExecutor(this)
