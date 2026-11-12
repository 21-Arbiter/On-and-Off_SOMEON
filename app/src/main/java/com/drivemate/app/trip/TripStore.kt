package com.drivemate.app.trip

import android.content.Context
import com.drivemate.core.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** The car's trips with their precise points, kept in a file on the car and nowhere else. */
class TripStore(context: Context) {

    private val file = File(context.filesDir, "trips.json")
    private val _trips = MutableStateFlow(load())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    fun add(new: List<Trip>) = save((_trips.value + new).sortedBy { it.startMs })

    fun clear() = save(emptyList())

    private fun save(trips: List<Trip>) {
        _trips.value = trips
        file.writeText(Json.encodeToString(trips))
    }

    private fun load(): List<Trip> =
        if (file.exists()) runCatching { Json.decodeFromString<List<Trip>>(file.readText()) }.getOrDefault(emptyList()) else emptyList()
}
