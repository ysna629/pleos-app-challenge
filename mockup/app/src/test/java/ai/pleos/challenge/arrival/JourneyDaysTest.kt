package ai.pleos.challenge.arrival

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class JourneyDaysTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun automaticallyCollectsStopsWithoutStartingATrip() {
        val now = at("2026-11-06T01:00:00Z")
        val first = JourneyDays.onGear(emptyList(), false, now, null, zone)
        val arrived = JourneyDays.onGear(first, true, now + 60_000, null, zone)
        val duplicate = JourneyDays.onGear(arrived, true, now + 90_000, null, zone)
        assertEquals(1, duplicate.size)
        assertEquals(1, duplicate.single().stops.size)
        assertEquals("11월 6일의 발자취", duplicate.single().title)
    }

    @Test fun newDayStartsANewRecordAndDoesNotInventOvernightStay() {
        val now = at("2026-11-06T14:59:00Z")
        val first = JourneyDays.onGear(emptyList(), true, now, null, zone)
        val next = JourneyDays.onGear(first, true, now + 120_000, null, zone)
        assertEquals(2, next.size)
        assertNull(next.first().endedAt)
        assertEquals(now, next.last().endedAt)
        assertTrue(next.last().stops.single().interrupted)
    }

    @Test fun cachedObservationCannotRollANewDayBackwards() {
        val midnight = at("2026-11-06T15:00:01Z")
        val today = JourneyDays.onGear(emptyList(), true, midnight, null, zone)
        assertEquals(today, JourneyDays.onGear(today, true, midnight - 60_000, null, zone))
    }

    @Test fun sameDayResumeKeepsOneCollectionAndPreservesHistory() {
        val now = at("2026-11-06T01:00:00Z")
        val old = Journey("older", "기존 여행", now - 86_400_000, now - 80_000_000)
        val first = JourneyDays.onGear(listOf(old), true, now, null, zone)
        val resumed = first.map { it.resume() }
        val next = JourneyDays.onGear(resumed, true, now + 300_000, null, zone)
        assertEquals(2, next.size)
        assertEquals(2, next.first().stops.size)
        assertEquals(old, next.last())
    }
}
