package ai.pleos.challenge.arrival

import kotlin.math.*

data class JourneyPoint(val latitude: Double, val longitude: Double, val at: Long, val breakBefore: Boolean = false) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun isFresh(now: Long): Boolean = valid && now - at in 0..90_000
    fun distanceTo(other: JourneyPoint): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLng = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLng / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

data class JourneyStop(
    val id: String,
    val arrivedAt: Long,
    val point: JourneyPoint?,
    val departedAt: Long? = null,
    val name: String = "",
    val note: String = "",
    val favorite: Boolean = false,
    val interrupted: Boolean = false,
    val address: String = "",
) {
    fun durationMs(now: Long): Long = ((departedAt ?: now) - arrivedAt).coerceAtLeast(0)
}

data class Journey(
    val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val route: List<JourneyPoint> = emptyList(),
    val stops: List<JourneyStop> = emptyList(),
    val lastParked: Boolean? = null,
    val breakNext: Boolean = false,
) {
    val distanceKm: Double get() = route.zipWithNext().sumOf { (a, b) -> if (b.breakBefore) 0.0 else a.distanceTo(b) }

    /** Sample every 15s, cap storage, and never draw across a lifecycle gap. */
    fun recordLocation(point: JourneyPoint): Journey {
        if (endedAt != null || !point.valid || point.at < startedAt) return this
        val locatedStops = if (lastParked == true) stops.map { stop ->
            if (stop.point == null && stop.departedAt == null && point.at - stop.arrivedAt in 0..90_000) stop.copy(point = point) else stop
        } else stops
        val previous = route.lastOrNull()
        if (previous != null && point.at <= previous.at) return this
        if (!breakNext && previous != null && point.at - previous.at < 15_000) return copy(stops = locatedStops)
        return copy(route = (route + point.copy(breakBefore = breakNext || (previous != null && point.at - previous.at > 120_000))).takeLast(2400), stops = locatedStops, breakNext = false)
    }

    fun onGear(parked: Boolean, at: Long, fix: JourneyPoint?): Journey {
        if (endedAt != null || lastParked == parked) return this
        val updated = if (parked) {
            stops + JourneyStop("$id-$at", at, fix?.takeIf { it.isFresh(at) })
        } else stops.map { if (it.departedAt == null && !it.interrupted) it.copy(departedAt = at) else it }
        return copy(stops = updated, lastParked = parked)
    }

    /** We cannot know where the car went while this activity was absent. */
    fun resume(): Journey = if (endedAt != null) this else copy(
        breakNext = true, lastParked = null,
        stops = stops.map { if (it.departedAt == null) it.copy(departedAt = route.lastOrNull()?.at?.coerceAtLeast(it.arrivedAt) ?: it.arrivedAt, interrupted = true) else it },
    )

    fun finish(at: Long): Journey = if (endedAt != null) this else copy(endedAt = at,
        stops = stops.map { if (it.departedAt == null) it.copy(departedAt = at) else it })

    fun updateStop(id: String, change: (JourneyStop) -> JourneyStop): Journey =
        copy(stops = stops.map { if (it.id == id) change(it) else it })
}
