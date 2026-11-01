package com.customcamera.app

import android.os.SystemClock

/**
 * Polls [condition] until it is true or [timeoutMs] elapses.
 * Camera operations complete asynchronously on the camera HAL, so fixed sleeps
 * would be either flaky or slow.
 */
fun waitUntil(timeoutMs: Long = 10_000, intervalMs: Long = 100, message: String = "condition", condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        SystemClock.sleep(intervalMs)
    }
    throw AssertionError("Timed out after ${timeoutMs}ms waiting for $message")
}
