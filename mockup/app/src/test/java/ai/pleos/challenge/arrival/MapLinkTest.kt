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
        assertEquals(
            "https://m.map.naver.com/map.naver?lat=37.45012&lng=127.12988&pinTitle=%EC%A3%BC%EC%B0%A8&pinType=site&dlevel=11",
            trip.mapLink(MapApp.NAVER),
        )
        assertNull(trip.copy(endLat = null).mapLink())
    }

    @Test
    fun smsBody_appendsLinkOnlyWithFix() {
        assertTrue(trip.smsBody().endsWith("\nhttps://www.google.com/maps/search/?api=1&query=37.45012,127.12988"))
        assertTrue(trip.smsBody(MapApp.KAKAO).contains("map.kakao.com"))
        assertFalse(trip.copy(endLat = null, endLng = null).smsBody().contains("http"))
    }

    @Test
    fun parkingNote_latestAcrossTripsAndNotes() {
        val older = ParkingNote(1_000, "지하 2층 B15")
        val newer = ParkingNote(9_000, "3층 C-3", "판교역로", 37.4501234, 127.1298765)
        assertEquals("지하 5층 A4", ParkingNote.latest(listOf(trip.copy(endedAt = 5_000)), listOf(older))?.spot)
        assertEquals(newer, ParkingNote.latest(listOf(trip.copy(endedAt = 5_000)), listOf(newer, older)))
        assertNull(ParkingNote.latest(listOf(trip.copy(parking = "")), emptyList()))
        assertTrue(newer.smsBody().startsWith("[도착노트] 3층 C-3 · 판교역로\n"))
        assertTrue(newer.smsBody().endsWith("주차\nhttps://www.google.com/maps/search/?api=1&query=37.45012,127.12988"))
        assertFalse(older.smsBody().contains("http"))
    }
}
