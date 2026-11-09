package ai.pleos.challenge.arrival

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Base64
import java.util.zip.Inflater

class JourneyExportTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val journey = Journey("id", "가족, 나들이", 0, null, stops = listOf(
        JourneyStop("one", 60_000, JourneyPoint(37.45, -127.14, 60_000), 180_000, "숲길 \"식당\"", "첫 줄\n둘째 줄", true, address = "서울"),
        JourneyStop("two", 240_000, null, name = "=1+1", note = "  @SUM(1,2)"),
        JourneyStop("three", 300_000, null, 360_000, interrupted = true),
    ))

    @Test fun csvKeepsKoreanQuotesNewlinesAndNegativeCoordinates() {
        val csv = JourneyExport.snapshot(journey, false, 420_000, seoul).csv()
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"가족, 나들이\""))
        assertTrue(csv.contains("\"숲길 \"\"식당\"\"\""))
        assertTrue(csv.contains("\"첫 줄\n둘째 줄\""))
        assertTrue(csv.contains("\"-127.14\""))
        assertTrue(csv.contains("\"1970-01-01 09:01:00\""))
        assertTrue(csv.contains("\"Asia/Seoul\""))
        assertTrue(csv.contains("\r\n"))
    }

    @Test fun protectsFormulaTextAndDistinguishesUnfinishedAndInterruptedVisits() {
        val csv = JourneyExport.snapshot(journey, true, 420_000, seoul).csv()
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"'  @SUM(1,2)\""))
        assertTrue(csv.contains("\"3\",\"머무는 중\""))
        assertTrue(csv.contains("\"\",\"기록 중단\""))
        assertTrue(csv.contains("\"예시\""))
        assertTrue(JourneyExport.snapshot(journey, true, 420_000, seoul).filename().contains("예시"))
    }

    @Test fun linksRoundTripAndEmptyJourneyHasHeaderOnlyCsv() {
        val snapshot = JourneyExport.snapshot(journey, false, 420_000, seoul)
        val link = snapshot.links("https://example.test/").single()
        val data = decode(link)
        assertEquals("journey", data.getString("kind"))
        assertEquals(1, data.getInt("v"))
        assertEquals("가족, 나들이", data.getString("title"))
        assertEquals(3, data.getJSONArray("stops").length())
        assertEquals(0, data.getInt("offset"))
        assertEquals(420_000L, data.getLong("exportedAt"))
        assertTrue(link.startsWith("https://example.test/journey.html#"))
        assertEquals(1, JourneyExport.snapshot(journey.copy(stops = emptyList()), false, 420_000, seoul).csv().split("\r\n").filter { it.isNotEmpty() }.size)
    }

    @Test fun phoneViewAndCsvHaveSeparateUrlsWithTheSameVisitSnapshot() {
        val snapshot = JourneyExport.snapshot(journey, false, 420_000, seoul)
        val view = snapshot.links("https://example.test/").single()
        val csv = snapshot.links("https://example.test/", csv = true).single()
        assertTrue(view.startsWith("https://example.test/journey.html#"))
        assertTrue(csv.startsWith("https://example.test/journey-csv.html#"))
        assertEquals(decode(view).toString(), decode(csv).toString())
    }

    @Test fun largeTripsSplitIntoQrPagesWithoutDroppingAnyStops() {
        val random = java.util.Random(21)
        val stops = (0 until 180).map { i ->
            JourneyStop("$i", i * 60_000L, null, name = "방문 $i", note = (0 until 200).map { (0xAC00 + random.nextInt(11172)).toChar() }.joinToString(""))
        }
        val links = JourneyExport.snapshot(journey.copy(stops = stops), false, 20_000_000, seoul).links()
        assertTrue(links.size > 1)
        var count = 0
        links.forEachIndexed { index, link ->
            assertTrue(link.toByteArray().size <= JourneyExport.MAX_LINK_BYTES)
            QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 640, 640)
            val data = decode(link)
            assertEquals(count, data.getInt("offset"))
            assertEquals(index + 1, data.getInt("part"))
            assertEquals(links.size, data.getInt("parts"))
            val array = data.getJSONArray("stops")
            for (i in 0 until array.length()) assertEquals("방문 ${count + i}", array.getJSONArray(i).getString(4))
            count += array.length()
        }
        assertEquals(stops.size, count)
    }

    @Test fun highlyCompressibleStopsRespectViewerLimitsToo() {
        val stop = JourneyStop("repeat", 60_000, null, note = "같은 메모".repeat(40))
        val links = JourneyExport.snapshot(journey.copy(stops = List(1001) { stop }), false, 420_000, seoul).links()
        assertTrue(links.size > 1)
        assertEquals(1001, links.sumOf { link ->
            val data = decode(link)
            assertTrue(data.getJSONArray("stops").length() <= 1000)
            assertTrue(data.toString().toByteArray().size <= 262144)
            data.getJSONArray("stops").length()
        })
    }

    private fun decode(url: String): JSONObject {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(Base64.getUrlDecoder().decode(url.substringAfter('#')))
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inflater.finished()) out.write(buf, 0, inflater.inflate(buf))
            JSONObject(out.toString("UTF-8"))
        } finally { inflater.end() }
    }
}
