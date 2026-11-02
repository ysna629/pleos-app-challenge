package ai.pleos.challenge.arrival

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Base64
import java.util.zip.Deflater

/**
 * A month of the logbook packed into a link for the phone. The records go after `#`, which browsers never send
 * to the server, so the static viewer page (`viewer/index.html`) draws the NTS table on the phone itself.
 */
object LogbookLink {
    /** Where `viewer/index.html` is published. */
    const val BASE_URL = "https://ysna629.github.io/arrival-note/"

    data class Summary(val totalKm: Float, val commuteKm: Float, val businessKm: Float) {
        val workKm: Float get() = commuteKm + businessKm
        /** Share of business use (업무사용비율) in percent, the figure the tax deduction is based on. */
        val ratio: Float get() = if (totalKm > 0f) workKm / totalKm * 100f else 0f
    }

    fun monthOf(trip: Trip, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(trip.startedAt).atZone(zone))

    /** Months that have trips, newest first. */
    fun months(trips: List<Trip>, zone: ZoneId = ZoneId.systemDefault()): List<YearMonth> =
        trips.map { monthOf(it, zone) }.distinct().sortedDescending()

    fun tripsIn(trips: List<Trip>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Trip> =
        trips.filter { monthOf(it, zone) == month }.sortedBy { it.startedAt }

    fun summary(trips: List<Trip>): Summary = Summary(
        totalKm = trips.sumOf { it.distanceKm.toDouble() }.toFloat(),
        commuteKm = trips.filter { it.purpose == Purpose.COMMUTE }.sumOf { it.distanceKm.toDouble() }.toFloat(),
        businessKm = trips.filter { it.purpose == Purpose.BUSINESS }.sumOf { it.distanceKm.toDouble() }.toFloat(),
    )

    /**
     * Payload before compression: `{"v":1,"m":"2026-11","t":[[startMinute,startKm×10,endKm×10,"C|B|P","arrival"],…]}`.
     * Distances are tenths of a km so the numbers stay integers.
     */
    fun payload(trips: List<Trip>, month: YearMonth): String = buildString {
        append("{\"v\":1,\"m\":\"").append(month).append("\",\"t\":[")
        trips.forEachIndexed { index, trip ->
            if (index > 0) append(',')
            append('[').append(trip.startedAt / 60_000).append(',')
            append(Math.round(trip.startKm * 10)).append(',').append(Math.round(trip.endKm * 10)).append(',')
            append('"').append(trip.purpose.code).append("\",")
            append('"').append(escape(trip.endAddress)).append("\"]")
        }
        append("]}")
    }

    /** The link for [month]: raw-deflated payload in base64url after `#`. */
    fun url(trips: List<Trip>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): String {
        val bytes = payload(tripsIn(trips, month, zone), month).toByteArray(Charsets.UTF_8)
        return BASE_URL + "#" + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(bytes))
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when {
                c == '"' || c == '\\' -> append('\\').append(c)
                c < ' ' -> append(' ')
                else -> append(c)
            }
        }
    }
}
