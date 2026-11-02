package ai.pleos.challenge.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import java.util.zip.Inflater

class LogbookLinkTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    private fun at(month: Int, day: Int, hour: Int) =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, seoul).toInstant().toEpochMilli()

    private val trips = listOf(
        Trip(at(11, 3, 8), at(11, 3, 9), 12600f, 12612f, "집", "수정구 시흥동", Purpose.COMMUTE),
        Trip(at(11, 3, 14), at(11, 3, 15), 12612f, 12646.5f, "회사", "수원 \"영통\"", Purpose.BUSINESS),
        Trip(at(11, 8, 10), at(11, 8, 11), 12658.5f, 12701.3f, "집", "", Purpose.PERSONAL),
        Trip(at(10, 30, 10), at(10, 30, 11), 12500f, 12520f, "집", "", Purpose.BUSINESS),
    )

    @Test
    fun summary_ratioCountsCommuteAsBusinessUse() {
        val sum = LogbookLink.summary(LogbookLink.tripsIn(trips, YearMonth.of(2026, 11), seoul))
        assertEquals(89.3f, sum.totalKm, 0.01f)
        assertEquals(46.5f, sum.workKm, 0.01f)
        assertEquals(52.07f, sum.ratio, 0.01f)
        assertEquals(0f, LogbookLink.summary(emptyList()).ratio)
    }

    @Test
    fun months_newestFirst() {
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), LogbookLink.months(trips, seoul))
    }

    @Test
    fun url_roundTripsPayload() {
        val url = LogbookLink.url(trips, YearMonth.of(2026, 11), seoul)
        assertTrue(url.startsWith(LogbookLink.BASE_URL + "#"))
        val bytes = Base64.getUrlDecoder().decode(url.substringAfter('#'))
        val inflater = Inflater(true).apply { setInput(bytes) }
        val out = ByteArray(4096)
        val text = String(out, 0, inflater.inflate(out), Charsets.UTF_8)
        assertEquals(
            "{\"v\":1,\"m\":\"2026-11\",\"t\":[" +
                "[${at(11, 3, 8) / 60_000},126000,126120,\"C\",\"수정구 시흥동\"]," +
                "[${at(11, 3, 14) / 60_000},126120,126465,\"B\",\"수원 \\\"영통\\\"\"]," +
                "[${at(11, 8, 10) / 60_000},126585,127013,\"P\",\"\"]]}",
            text,
        )
    }
}
