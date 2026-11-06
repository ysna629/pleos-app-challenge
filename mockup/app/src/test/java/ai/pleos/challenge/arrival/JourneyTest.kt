package ai.pleos.challenge.arrival

import org.junit.Assert.*
import org.junit.Test

class JourneyTest {
    private fun fix(at: Long, lat: Double = 37.45, lng: Double = 127.14) = JourneyPoint(lat, lng, at)

    @Test fun parkingCreatesOneStopAndDepartureClosesIt() {
        val journey = Journey("family", "가족 나들이", 1_000)
            .recordLocation(fix(2_000))
            .onGear(true, 3_000, fix(2_000))
            .onGear(true, 4_000, fix(2_000))
            .onGear(false, 603_000, null)
        assertEquals(1, journey.stops.size)
        assertEquals(600_000L, journey.stops.single().durationMs(700_000))
        assertEquals(603_000L, journey.stops.single().departedAt)
    }

    @Test fun noFreshLocationStillRecordsAnUnlocatedStop() {
        val journey = Journey("family", "나들이", 1_000).onGear(true, 200_000, fix(1_000))
        assertNull(journey.stops.single().point)
        assertTrue(journey.route.isEmpty())
    }

    @Test fun invalidAndFutureCoordinatesAreRejected() {
        assertFalse(fix(1_000, Double.NaN).isFresh(1_000))
        assertFalse(fix(1_000, 91.0).isFresh(1_000))
        assertFalse(fix(2_000).isFresh(1_000))
        assertFalse(fix(1_000).isFresh(200_000))
    }

    @Test fun routeSamplingLimitsNoiseAndRetainsAnActualPath() {
        val journey = Journey("family", "나들이", 1_000)
            .recordLocation(fix(1_000))
            .recordLocation(fix(2_000))
            .recordLocation(fix(16_000, 37.451))
        assertEquals(2, journey.route.size)
        assertTrue(journey.distanceKm > 0.1)
    }

    @Test fun resumedJourneyBreaksTheLineAndStartsANewObservedStop() {
        val journey = Journey("family", "나들이", 1_000)
            .recordLocation(fix(1_000))
            .onGear(true, 2_000, fix(1_000))
            .resume()
            .recordLocation(fix(100_000, 38.0))
            .onGear(true, 101_000, fix(100_000, 38.0))
        assertTrue(journey.route.last().breakBefore)
        assertEquals(0.0, journey.distanceKm, 0.0001)
        assertEquals(2, journey.stops.size)
        assertTrue(journey.stops.first().interrupted)
    }

    @Test fun finishingStopsFurtherCaptureAndClosesCurrentVisit() {
        val journey = Journey("family", "나들이", 1_000)
            .onGear(true, 2_000, fix(1_000))
            .finish(10_000)
        assertEquals(10_000L, journey.endedAt)
        assertEquals(10_000L, journey.stops.single().departedAt)
        assertEquals(journey, journey.recordLocation(fix(20_000)))
        assertEquals(journey, journey.onGear(true, 20_000, fix(20_000)))
    }

    @Test fun stopUpdatesDoNotChangeAnotherJourney() {
        val journey = Journey("family", "나들이", 1_000).onGear(true, 2_000, fix(1_000))
        val id = journey.stops.single().id
        val saved = journey.updateStop(id) { it.copy(name = "식당", favorite = true, note = "아이와 오기 좋아요") }
        assertEquals("식당", saved.stops.single().name)
        assertTrue(saved.stops.single().favorite)
        assertEquals("", journey.stops.single().name)
    }

    @Test fun lateFixAttachesOnlyToTheCurrentParkedStop() {
        val point = fix(16_000)
        val parked = Journey("family", "나들이", 1_000).onGear(true, 2_000, null).recordLocation(point)
        assertEquals(point, parked.stops.single().point)
        val departed = Journey("family", "나들이", 1_000).onGear(true, 2_000, null)
            .onGear(false, 10_000, null).recordLocation(point)
        assertNull(departed.stops.single().point)
    }
}
