package com.drivemate.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArraySet

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** Reads the gear, and whether anyone sits beside or behind the driver, through the Pleos Vehicle SDK. */
class PleosVehicleSource(private val context: Context) {

    private val _gear = MutableStateFlow(Gear.UNKNOWN)
    val gear: StateFlow<Gear> = _gear.asStateFlow()

    private val _passenger = MutableStateFlow(false)
    /** Someone in a seat other than the driver's (false where the car can't tell). */
    val passenger: StateFlow<Boolean> = _passenger.asStateFlow()

    private val occupied = MutableStateFlow(emptySet<SeatValue>())

    /** The passenger seats the car can tell about. */
    private val watched = CopyOnWriteArraySet<SeatValue>()

    /**
     * The SDK (2.3.2) reports SEAT_OCCUPANCY as "the value is 1", and in Android Automotive 1 is
     * VACANT (OCCUPIED is 2), which is what the emulator's Virtual Console sends: so true means empty.
     */
    private fun seated(seat: SeatValue, vacant: Boolean?) {
        val yes = vacant == false
        Log.i(TAG, "Seat $seat occupied: $yes")
        occupied.update { if (yes) it + seat else it - seat }
        _passenger.value = occupied.value.isNotEmpty()
    }

    private val seatListeners = PASSENGER_SEATS.associateWith { seat ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) = seated(seat, vacant = occupied)

            override fun onFailed(e: Exception) {
                Log.w(TAG, "Seat $seat listener failed", e)
            }
        }
    }

    private var vehicle: Vehicle? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _gear.value = gear.toGear()
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Gear listener failed", e)
        }
    }

    fun start() {
        if (vehicle != null) return
        val v = Vehicle(context).also { it.initialize() }
        vehicle = v
        v.getGear().run {
            getCurrentGearSelectionState({ gear -> _gear.value = gear.toGear() }, { Log.w(TAG, "Gear read failed", it) })
            registerGearSelection(gearListener)
        }
        v.getSeat().run {
            checkSeatOccupancyCapabilities({ areas ->
                val seats = areas.flatMap { it.values.toList() }.filter { it in seatListeners }
                Log.i(TAG, "Seat occupancy for $seats")
                seats.forEach { seat ->
                    watched += seat
                    getCurrentSeatOccupancy(SeatArea(seat), { seated(seat, vacant = it) }, { Log.w(TAG, "Seat $seat read failed", it) })
                    registerSeatOccupancy(SeatArea(seat), seatListeners.getValue(seat))
                }
            }, { Log.w(TAG, "No seat occupancy", it) })
        }
    }

    fun stop() {
        vehicle?.run {
            getGear().unregisterGearSelection(gearListener)
            watched.forEach { seat -> getSeat().unregisterSeatOccupancy(SeatArea(seat), seatListeners.getValue(seat)) }
            release()
        }
        watched.clear()
        occupied.value = emptySet()
        _passenger.value = false
        vehicle = null
    }

    private companion object {
        const val TAG = "PleosVehicleSource"

        /** The driver sits front left. */
        val PASSENGER_SEATS = listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    }
}

private fun VehicleGear?.toGear(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.PARK
    VehicleGear.GEAR_REVERSE -> Gear.REVERSE
    VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
    null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    else -> Gear.DRIVE // GEAR_DRIVE and manual GEAR_1..GEAR_9
}
