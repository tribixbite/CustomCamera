package com.customcamera.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Keeps a recent device location while the viewfinder is visible so captures
 * can be geotagged without waiting for a fix at shutter time.
 *
 * Uses the platform LocationManager (no Play Services dependency). Only runs
 * when the user enabled location tagging and granted permission; the location
 * is written into the photo's EXIF / video metadata on-device and never sent anywhere.
 */
class LocationTracker(context: Context) : LocationListener {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false

    @Volatile
    var lastLocation: Location? = null
        private set

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // hasPermission() is checked first.
    fun start() {
        if (running || !hasPermission()) return
        running = true
        val providers = manager.getProviders(true).filter {
            it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER || it == LocationManager.PASSIVE_PROVIDER
        }
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { lastLocation = it }
        providers.forEach {
            runCatching { manager.requestLocationUpdates(it, UPDATE_INTERVAL_MS, UPDATE_DISTANCE_M, this, Looper.getMainLooper()) }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        manager.removeUpdates(this)
    }

    /** Most recent fix if it is fresh enough to describe where the photo was taken. */
    fun currentOrNull(): Location? = lastLocation?.takeIf { System.currentTimeMillis() - it.time < MAX_AGE_MS }

    override fun onLocationChanged(location: Location) {
        val previous = lastLocation
        if (previous == null || location.time >= previous.time || location.accuracy < previous.accuracy) {
            lastLocation = location
        }
    }

    @Deprecated("Required for API < 29 implementations")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    companion object {
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val UPDATE_DISTANCE_M = 10f
        private const val MAX_AGE_MS = 10 * 60_000L
    }
}
