package com.drivemate.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.drivemate.core.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the car is, from the Android location service (GPS). Pleos' FusedLocation SDK
 * can take its place once it opens; until then this works on the car and the emulator.
 */
class LocationSource(private val context: Context) {

    private val _latest = MutableStateFlow<GeoPoint?>(null)
    val latest: StateFlow<GeoPoint?> = _latest.asStateFlow()

    var started = false
        private set

    private val listener = LocationListener { _latest.value = GeoPoint(it.latitude, it.longitude) }

    fun startIfPermitted() {
        if (started) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(LocationManager::class.java)
        manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { listener.onLocationChanged(it) }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_MS, 0f, listener, Looper.getMainLooper())
        started = true
    }

    private companion object {
        const val UPDATE_MS = 5_000L
    }
}
