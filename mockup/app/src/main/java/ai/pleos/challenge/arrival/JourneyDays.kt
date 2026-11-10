package ai.pleos.challenge.arrival

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One automatically observed collection per local calendar day. Never guesses across missing time. */
object JourneyDays {
    fun onGear(journeys: List<Journey>, parked: Boolean, at: Long, fix: JourneyPoint?, zone: ZoneId = ZoneId.systemDefault()): List<Journey> {
        // A delayed cached location must not close a collection started by a newer observation.
        if (journeys.any { it.endedAt == null && it.startedAt > at }) return journeys
        val day = dayOf(at, zone)
        fun sameDay(journey: Journey) = dayOf(journey.startedAt, zone) == day
        val current = journeys.firstOrNull { it.endedAt == null && sameDay(it) }
        val history = journeys.map { journey ->
            if (journey.endedAt == null && journey.id != current?.id) {
                val last = maxOf(journey.startedAt, journey.route.lastOrNull()?.at ?: 0,
                    journey.stops.maxOfOrNull { it.departedAt ?: it.arrivedAt } ?: 0)
                journey.resume().finish(last)
            } else journey
        }
        val base = current ?: Journey("day-$day-$at", "${day.monthValue}월 ${day.dayOfMonth}일의 발자취", at)
        val updated = base.onGear(parked, at, fix)
        return if (current == null) listOf(updated) + history else history.map { if (it.id == current.id) updated else it }
    }

    /** The day being recorded now: only today's collection, so yesterday's open one does not pose as today's. */
    fun today(journeys: List<Journey>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Journey? =
        journeys.firstOrNull { it.endedAt == null && dayOf(it.startedAt, zone) == dayOf(now, zone) }

    /**
     * Past days are never shown again as a whole, only the places starred on them. Dropping the rest keeps the
     * store from growing by a day's route every day.
     */
    fun keepStarred(journeys: List<Journey>): List<Journey> = journeys.mapNotNull { journey ->
        if (journey.endedAt == null) journey
        else journey.copy(route = emptyList(), stops = journey.stops.filter { it.favorite }).takeIf { it.stops.isNotEmpty() }
    }

    private fun dayOf(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}

/** A starred stop with the day it belongs to, for the list of places to come back to. */
data class SavedPlace(val journeyId: String, val stop: JourneyStop)

/** Every starred stop across all days, newest first. */
fun savedPlaces(journeys: List<Journey>): List<SavedPlace> =
    journeys.flatMap { journey -> journey.stops.filter { it.favorite }.map { SavedPlace(journey.id, it) } }
        .sortedByDescending { it.stop.arrivedAt }
