package ai.pleos.challenge.arrival

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.Inflater

class RecordLinkTest {
    private fun decode(url: String): JSONObject {
        val bytes = Base64.getUrlDecoder().decode(url.substringAfter('#'))
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArray(8192)
        val size = inflater.inflate(out)
        inflater.end()
        return JSONObject(String(out, 0, size, Charsets.UTF_8))
    }

    private val trip = Trip(1_761_800_000_000, 1_761_803_000_000, 12600f, 12612.4f, "집", "수정구 시흥동", Purpose.BUSINESS, "지하 5층 A4", 37.450201, 127.146499)

    @Test fun tripLinkCarriesTheRecordPhoneAndMapChoice() {
        val url = RecordLink.url(trip, "010-1234-5678", MapApp.KAKAO, "https://example.test/")
        assertTrue(url.startsWith("https://example.test/record.html#"))
        val data = decode(url)
        assertEquals("trip", data.getString("k"))
        assertEquals(126000, data.getInt("a"))
        assertEquals(126124, data.getInt("b"))
        assertEquals("B", data.getString("p"))
        assertEquals("지하 5층 A4", data.getString("spot"))
        assertEquals("수정구 시흥동", data.getString("addr"))
        assertEquals(37.4502, data.getDouble("lat"), 1e-9)
        assertEquals(127.1465, data.getDouble("lng"), 1e-9)
        assertEquals("01012345678", data.getString("ph"))
        assertEquals("K", data.getString("m"))
        // Small enough for a sparse QR.
        assertTrue(url.length < 400)
    }

    @Test fun parkingLinkLeavesOutWhatItDoesNotHave() {
        val note = ParkingNote(1_761_803_000_000, "주차타워 3번")
        val data = decode(RecordLink.url(note, "", MapApp.NAVER))
        assertEquals("park", data.getString("k"))
        assertEquals("주차타워 3번", data.getString("spot"))
        assertFalse(data.has("lat"))
        assertFalse(data.has("ph"))
        assertEquals("N", data.getString("m"))
    }
}
