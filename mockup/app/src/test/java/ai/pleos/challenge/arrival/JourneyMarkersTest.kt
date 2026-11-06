package ai.pleos.challenge.arrival

import org.junit.Assert.*
import org.junit.Test

class JourneyMarkersTest {
    private fun stop(id: String, x: Double, favorite: Boolean = false) =
        JourneyStop(id, 0, JourneyPoint(37.0, x, 0), favorite = favorite)

    @Test fun favoriteIsTheVisibleVisitWhenNoVisitInTheGroupIsSelected() {
        val stops = listOf(stop("one", 127.0), stop("two", 127.0), stop("three", 127.0, true), stop("four", 127.0), stop("five", 128.0))
        val groups = journeyMarkers(stops) { it.longitude * 100 to 0.0 }
        assertEquals(2, groups.size)
        val favorite = groups.first().representative("five")
        assertEquals(2, favorite.index)
        assertEquals("three", favorite.value.id)
        assertTrue(favorite.value.favorite)
        assertEquals(4, groups.first().visits.size)
    }

    @Test fun selectedVisitOwnsItsNumberAndFavoriteRatherThanInheritingAnotherVisitsStar() {
        val group = journeyMarkers(listOf(stop("three", 127.0, true), stop("four", 127.0))) { 0.0 to 0.0 }.single()
        assertEquals("three", group.representative("three").value.id)
        assertTrue(group.representative("three").value.favorite)
        assertEquals("four", group.representative("four").value.id)
        assertFalse(group.representative("four").value.favorite)
    }

    @Test fun nearbyGroupsMergeSoChangingSelectionCannotRecreateOverlap() {
        val stops = listOf(stop("one", 0.0), stop("two", 0.71), stop("three", 0.73))
        val group = journeyMarkers(stops) { it.longitude * 100 to 0.0 }.single()
        assertEquals(3, group.visits.size)
        assertEquals("two", group.representative("two").value.id)
    }

    @Test fun unlocatedVisitsKeepTheirNumberAndDoNotBecomeMapMarkers() {
        val stops = listOf(JourneyStop("missing", 0, null), stop("second", 127.0, true))
        val group = journeyMarkers(stops) { 0.0 to 0.0 }.single()
        assertEquals(1, group.representative(null).index)
        assertEquals("second", group.representative(null).value.id)
    }
}
