package ai.pleos.challenge.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ParkingTextTest {

    @Test
    fun floor_basementInKoreanWords() {
        assertEquals(-5, ParkingText.parseFloor("지하 오층"))
        assertEquals(-5, ParkingText.parseFloor("지하 오층에"))
        assertEquals(-1, ParkingText.parseFloor("지하 일층이요"))
        assertEquals(-12, ParkingText.parseFloor("지하 십이층"))
    }

    @Test
    fun floor_digitsAndLetters() {
        assertEquals(-3, ParkingText.parseFloor("지하 3층"))
        assertEquals(-2, ParkingText.parseFloor("B2"))
        assertEquals(-4, ParkingText.parseFloor("비사층"))
        assertEquals(2, ParkingText.parseFloor("이층"))
        assertEquals(1, ParkingText.parseFloor("지상 1층"))
    }

    @Test
    fun floor_unparseable() {
        assertNull(ParkingText.parseFloor("모르겠어"))
        assertNull(ParkingText.parseFloor(""))
    }

    @Test
    fun pillar_koreanSpelling() {
        assertEquals("A4", ParkingText.parsePillar("에이 사"))
        assertEquals("A4", ParkingText.parsePillar("에이사번"))
        assertEquals("B12", ParkingText.parsePillar("비 십이번이요"))
        assertEquals("C3", ParkingText.parsePillar("씨 삼"))
        assertEquals("H7", ParkingText.parsePillar("에이치 칠"))
        assertEquals("E2", ParkingText.parsePillar("이 이"))
        assertEquals("A5", ParkingText.parsePillar("에이 오"))
        assertEquals("G21", ParkingText.parsePillar("지 이십일 기둥"))
        assertEquals("K", ParkingText.parsePillar("케이 구역"))
    }

    @Test
    fun pillar_multiLetterAndLeadingZeros() {
        assertEquals("AL02", ParkingText.parsePillar("에이엘 공이"))
        assertEquals("AL02", ParkingText.parsePillar("에이 엘 영 이"))
        assertEquals("AL02", ParkingText.parsePillar("에이 엘 제로 이"))
        assertEquals("B15", ParkingText.parsePillar("비 십오"))
        assertEquals("B015", ParkingText.parsePillar("비 공일오"))
    }

    @Test
    fun pillar_alreadyAlphanumeric() {
        assertEquals("A4", ParkingText.parsePillar("A4"))
        assertEquals("AL02", ParkingText.parsePillar("AL02"))
        assertEquals("B15", ParkingText.parsePillar("B 15"))
        assertEquals("D15", ParkingText.parsePillar("d 15번"))
        assertEquals("A4", ParkingText.parsePillar("에이 4"))
    }

    @Test
    fun pillar_hyphenOnlyWhenSaid() {
        assertEquals("B-15", ParkingText.parsePillar("B-15"))
        assertEquals("C-3", ParkingText.parsePillar("C-3"))
        assertEquals("C-3", ParkingText.parsePillar("씨 다시 삼"))
        assertEquals("B-15", ParkingText.parsePillar("비 하이픈 십오"))
    }

    @Test
    fun pillar_doesNotGuessMergedWords() {
        assertNull(ParkingText.parsePillar("사회"))
        assertNull(ParkingText.parsePillar("의사"))
        assertNull(ParkingText.parsePillar("십오"))
        assertNull(ParkingText.parsePillar(""))
    }

    @Test
    fun sentence_andFormat() {
        val (floor, pillar) = ParkingText.parseSentence("지하 오층 에이 사에 주차했어")
        assertEquals(-5, floor)
        assertEquals("A4", pillar)
        assertEquals("지하 5층 A4", ParkingText.format(floor, pillar))
        assertEquals("지하 2층 AL02", ParkingText.format(-2, "AL02"))
        assertEquals("지하 5층", ParkingText.format(-5, null))
        assertEquals("", ParkingText.format(null, ""))
    }
}

class TripSmsTest {
    private val trip = Trip(
        startedAt = 0,
        endedAt = java.util.GregorianCalendar(2026, 9, 30, 3, 11).timeInMillis,
        startKm = 12435.4f,
        endKm = 12447.8f,
        startAddress = "",
        endAddress = "수정구 시흥동",
        parking = "지하 5층 A4",
    )

    @Test
    fun smsBody_isShort() {
        assertEquals("[도착노트] 지하 5층 A4 · 수정구 시흥동\n10/30 03:11 · 12.4km 업무", trip.smsBody())
        // A single Korean SMS holds 90 bytes in EUC-KR (2 bytes per Hangul).
        assert(trip.smsBody().toByteArray(charset("EUC-KR")).size <= 90)
    }

    @Test
    fun smsBody_withoutParking() {
        assertEquals("[도착노트] 주차 위치 기록 안 함\n10/30 03:11 · 12.4km 업무", trip.copy(parking = "", endAddress = "").smsBody())
    }

    @Test
    fun smsQrText_addressesOwnNumber() {
        assertEquals("SMSTO:01012345678:" + trip.smsBody(), trip.smsQrText("010-1234-5678"))
    }

    @Test
    fun spoken_readsPillarCodesInKorean() {
        assertEquals("지하 5층 에이 4로 저장했어요.", ParkingText.spoken("지하 5층 A4로 저장했어요."))
        assertEquals("지하 2층 비 15, 저장할까요?", ParkingText.spoken("지하 2층 B15, 저장할까요?"))
        assertEquals("에이엘 공이", ParkingText.spoken("AL02"))
        assertEquals("3층 씨 다시 3예요.", ParkingText.spoken("3층 C-3예요."))
        assertEquals("몇 층이에요?", ParkingText.spoken("몇 층이에요?"))
    }

    @Test
    fun placesWithoutFloorOrPillar() {
        assertEquals("주차타워", ParkingText.towerLabel("주차타워에 넣었어"))
        assertEquals("주차타워", ParkingText.towerLabel("기계식 주차장이야"))
        assertEquals("주차타워 3번", ParkingText.towerLabel("삼번 타워"))
        assertEquals("주차타워 12번", ParkingText.towerLabel("주차 타워 12호기"))
        assertNull(ParkingText.towerLabel("지하 5층"))
        assertTrue(ParkingText.isOutdoor("지상 주차장이야"))
        assertTrue(ParkingText.isOutdoor("길가에 댔어"))
        assertTrue(ParkingText.isOutdoor("야외"))
        assertFalse(ParkingText.isOutdoor("지상 2층"))
        assertFalse(ParkingText.isOutdoor("지하 오층"))
    }

    @Test
    fun placeAfterFloor_keepsDescription() {
        assertEquals("B동 앞", ParkingText.placeAfterFloor("지하 1층 B동 앞에"))
        assertEquals("B동 앞", ParkingText.placeAfterFloor("지하 일층 비동 앞에 주차했어"))
        assertEquals("A동 엘리베이터 옆", ParkingText.placeAfterFloor("지하 2층이고 에이동 엘리베이터 옆이야"))
        assertEquals("101동 앞", ParkingText.placeAfterFloor("지하 1층 101동 앞이에요."))
        assertEquals("정문 쪽", ParkingText.placeAfterFloor("2층에 정문 쪽"))
        assertNull(ParkingText.placeAfterFloor("지하 5층이야"))
        assertNull(ParkingText.placeAfterFloor("지하 5층"))
        assertNull(ParkingText.placeAfterFloor("주차타워"))
        // A pillar code still wins over a description.
        assertEquals(-5 to "A4", ParkingText.parseSentence("지하 오층 에이 사에 주차했어"))
    }
}
