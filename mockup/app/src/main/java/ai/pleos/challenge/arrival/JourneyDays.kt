package ai.pleos.challenge.arrival

import java.time.Instant
import java.time.ZoneId

/** One automatically observed collection per local calendar day. Never guesses across missing time. */
object JourneyDays {
    fun onGear(journeys: List<Journey>, parked: Boolean, at: Long, fix: JourneyPoint?, zone: ZoneId = ZoneId.systemDefault()): List<Journey> {
        // A delayed cached location must not close a collection started by a newer observation.
        if (journeys.any { it.endedAt == null && it.startedAt > at }) return journeys
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        fun sameDay(journey: Journey) = Instant.ofEpochMilli(journey.startedAt).atZone(zone).toLocalDate() == day
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
}
