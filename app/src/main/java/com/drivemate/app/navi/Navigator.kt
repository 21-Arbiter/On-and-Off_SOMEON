package com.drivemate.app.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.drivemate.core.Place

/**
 * Hands a destination to the car's navigation through Pleos NaviHelper. The navigation
 * app takes over from there (its own screen and voice guidance); trips keep recording
 * behind it.
 */
class Navigator(context: Context) {

    private val navi = NaviHelper(context.applicationContext)
    private var ready = false

    private val events = object : NaviHelperEventListener {
        override fun onRoutingCompleted() {
            Log.i(TAG, "Route ready")
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "Navigation error: $errorCode")
        }
    }

    fun start() {
        try {
            navi.initialize(
                {
                    navi.addListener(events)
                    ready = true
                },
                { Log.w(TAG, "NaviHelper unavailable", it) },
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "NaviHelper not allowed", e)
        }
    }

    /** Sets [place] as the destination; false if the navigation isn't reachable. */
    fun navigateTo(place: Place): Boolean {
        if (!ready) return false
        return try {
            navi.requestRoute(
                RouteInfo(
                    latitude = place.point.lat,
                    longitude = place.point.lon,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = place.name,
                    // Our places aren't in the navigation's POI database: no id, it routes by coordinates.
                    poiId = "",
                    poiSubId = "0",
                    address = place.address,
                ),
            )
            Log.i(TAG, "Route requested to ${place.name}")
            showNavigation()
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Route request failed", e)
            false
        }
    }

    /** Brings the navigation's screen to the front, so the driver sees the route they asked for. */
    private fun showNavigation() {
        try {
            navi.requestNavigationScreen(NavigationScreenRequest.Foreground)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Navigation screen not shown", e)
        }
    }

    private companion object {
        const val TAG = "Navigator"
    }
}
