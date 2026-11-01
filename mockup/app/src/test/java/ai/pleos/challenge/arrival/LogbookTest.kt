package ai.pleos.challenge.arrival

import ai.pleos.challenge.arrival.Commands.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogbookTest {
    private fun segment(at: Long, from: Float, to: Float?, place: String = "") =
        Segment(departAt = at, departKm = from, departAddress = "출발$at", arriveAt = to?.let { at + 600_000 }, arriveKm = to, arriveAddress = place)

    @Test
    fun merge_joinsStopsIntoOneTrip() {
        val trip = Logbook.merge(listOf(segment(2_000_000, 12605f, 12612f, "수정구 시흥동"), segment(1_000_000, 12600f, 12605f, "편의점")))!!
        assertEquals(12600f, trip.startKm)
        assertEquals(12612f, trip.endKm)
        assertEquals(12f, trip.distanceKm, 0.001f)
        assertEquals("출발1000000", trip.startAddress)
        assertEquals("수정구 시흥동", trip.endAddress)
    }

    @Test
    fun merge_ignoresOpenSegmentAndShortMoves() {
        assertNull(Logbook.merge(emptyList()))
        assertNull(Logbook.merge(listOf(segment(1, 12600f, 12600f))))
        assertNull(Logbook.merge(listOf(segment(1, 12600f, 12600.2f))))
        assertNull(Logbook.merge(listOf(segment(1, 12600f, null))))
        assertEquals(5f, Logbook.merge(listOf(segment(1, 12600f, 12605f), segment(2_000_000, 12605f, null)))!!.distanceKm, 0.001f)
    }

    @Test
    fun merge_skipsMissingOrDecreasingOdometer() {
        // Departure recorded before the first odometer update would otherwise become a 12,605 km trip.
        assertNull(Logbook.merge(listOf(segment(1, 0f, 12605f))))
        assertNull(Logbook.merge(listOf(segment(1, 12605f, 12600f))))
        assertEquals(7f, Logbook.merge(listOf(segment(1, 0f, 12605f), segment(2_000_000, 12605f, 12612f)))!!.distanceKm, 0.001f)
    }
}

class CommandsTest {
    @Test
    fun intent() {
        assertEquals(Intent.LOGBOOK, Commands.parseIntent("운행일지 정리해줘"))
        assertEquals(Intent.LOGBOOK, Commands.parseIntent("운행 기록 정리"))
        assertEquals(Intent.WHERE_PARKED, Commands.parseIntent("주차 어디 했지"))
        assertEquals(Intent.WHERE_PARKED, Commands.parseIntent("어디 주차했더라"))
        assertEquals(Intent.WHERE_PARKED, Commands.parseIntent("주차 위치 알려줘"))
        assertEquals(Intent.UNKNOWN, Commands.parseIntent("노래 틀어줘"))
        assertEquals(Intent.UNKNOWN, Commands.parseIntent(""))
    }

    @Test
    fun yesNo() {
        assertTrue(Commands.parseYesNo("응")!!)
        assertTrue(Commands.parseYesNo("네 기록해줘")!!)
        assertTrue(Commands.parseYesNo("그래")!!)
        assertTrue(Commands.parseYesNo("어")!!)
        assertTrue(Commands.parseYesNo("저장해 줘")!!)
        assertTrue(Commands.parseYesNo("맞아")!!)
        assertFalse(Commands.parseYesNo("아니")!!)
        assertFalse(Commands.parseYesNo("괜찮아")!!)
        assertFalse(Commands.parseYesNo("아니 됐어")!!)
        assertNull(Commands.parseYesNo("음"))
        assertNull(Commands.parseYesNo(""))
    }

    @Test
    fun purpose() {
        assertEquals(Purpose.BUSINESS, Commands.parsePurpose("업무"))
        assertEquals(Purpose.BUSINESS, Commands.parsePurpose("업무 운행이야"))
        assertEquals(Purpose.COMMUTE, Commands.parsePurpose("출퇴근"))
        assertEquals(Purpose.COMMUTE, Commands.parsePurpose("퇴근길이야"))
        assertEquals(Purpose.PERSONAL, Commands.parsePurpose("개인 용도"))
        assertEquals(Purpose.PERSONAL, Commands.parsePurpose("비업무"))
        assertNull(Commands.parsePurpose("모르겠어"))
    }
}
