package ai.pleos.challenge.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLinkTest {
    private val trip = Trip(0, 0, 12600f, 12612f, "", "수정구 시흥동", parking = "지하 5층 A4", endLat = 37.4501234, endLng = 127.1298765)

    @Test
    fun mapLink_perApp() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=37.45012,127.12988", trip.mapLink())
        assertEquals("https://map.kakao.com/link/map/%EC%A3%BC%EC%B0%A8,37.45012,127.12988", trip.mapLink(MapApp.KAKAO))
        assertNull(trip.copy(endLat = null).mapLink())
    }

    @Test
    fun smsBody_appendsLinkOnlyWithFix() {
        assertTrue(trip.smsBody().endsWith("\nhttps://www.google.com/maps/search/?api=1&query=37.45012,127.12988"))
        assertTrue(trip.smsBody(MapApp.KAKAO).contains("map.kakao.com"))
        assertFalse(trip.copy(endLat = null, endLng = null).smsBody().contains("http"))
    }
}
