package com.drivemate.app.notice

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The driver's say over notices, kept on the car (and kept when the account is deleted):
 * whether the car tells of news by voice at all ([on]), and whether it goes quiet by itself
 * while someone else sits in the car ([offWithPassenger]). Both start on.
 */
class AlertSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _on = MutableStateFlow(prefs.getBoolean(KEY_ON, true))
    val on: StateFlow<Boolean> = _on.asStateFlow()

    private val _offWithPassenger = MutableStateFlow(prefs.getBoolean(KEY_OFF_WITH_PASSENGER, true))
    val offWithPassenger: StateFlow<Boolean> = _offWithPassenger.asStateFlow()

    fun setOn(on: Boolean) {
        _on.value = on
        prefs.edit { putBoolean(KEY_ON, on) }
    }

    fun setOffWithPassenger(off: Boolean) {
        _offWithPassenger.value = off
        prefs.edit { putBoolean(KEY_OFF_WITH_PASSENGER, off) }
    }

    private companion object {
        const val KEY_ON = "alertsOn"
        const val KEY_OFF_WITH_PASSENGER = "alertsOffWithPassenger"
    }
}
