package ai.pleos.challenge.arrival

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.zip.Deflater

/** A fixed snapshot keeps CSV and QR dates/durations identical while the live trip continues. */
object JourneyExport {
    const val MAX_LINK_BYTES = 1800
    private const val MAX_STOPS_PER_LINK = 500
    private const val MAX_PAYLOAD_BYTES = 200_000

    fun snapshot(journey: Journey, sample: Boolean, at: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) =
        Snapshot(journey, sample, at, zone)

    data class Snapshot(val journey: Journey, val sample: Boolean, val exportedAt: Long, val zone: ZoneId) {
        private fun date(at: Long?): String = at?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone).format(Instant.ofEpochMilli(it))
        }.orEmpty()

        fun filename(): String {
            val title = journey.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(40).ifBlank { "여행" }
            return "${if (sample) "예시_" else ""}여행기록_${date(journey.startedAt).take(10)}_$title.csv"
        }

        fun csv(): String {
            val headers = listOf("기록 구분", "여행명", "여행 시작", "여행 종료", "시간대", "GPS 수집 거리(km)",
                "방문 순서", "장소명", "주소", "도착 시각", "출발 시각", "체류 시간(분)", "기록 상태", "즐겨찾기", "메모", "위도", "경도")
            val rows: List<List<Any?>> = listOf(headers) + journey.stops.mapIndexed { index, stop ->
                listOf(if (sample) "예시" else "실제", journey.title, date(journey.startedAt), date(journey.endedAt), zone.id,
                    String.format(Locale.US, "%.3f", journey.distanceKm), index + 1, stop.name, stop.address,
                    date(stop.arrivedAt), if (stop.interrupted) "" else date(stop.departedAt),
                    if (stop.interrupted) "" else stop.durationMs(exportedAt) / 60_000,
                    if (stop.interrupted) "기록 중단" else if (stop.departedAt == null) "머무는 중" else "완료",
                    if (stop.favorite) "예" else "아니오", stop.note, stop.point?.latitude, stop.point?.longitude)
            }
            return "\uFEFF" + rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { cell(it) } }
        }

        private fun cell(value: Any?): String {
            var text = value?.toString().orEmpty()
            if (value is String && (text.firstOrNull() in listOf('\t', '\r', '\n') || text.trimStart().firstOrNull() in listOf('=', '+', '-', '@'))) text = "'$text"
            return "\"${text.replace("\"", "\"\"")}\""
        }

        /** Whole visits are split into bounded links. Every page retains its original row offset. */
        fun links(base: String = LogbookLink.BASE_URL, csv: Boolean = false): List<String> {
            val uri = java.net.URI(base)
            require(uri.scheme in listOf("http", "https") && uri.host != null && uri.rawQuery == null && uri.rawFragment == null) { "올바른 뷰어 주소가 필요해요." }
            val prefix = base.trimEnd('/') + if (csv) "/journey-csv.html#" else "/journey.html#"
            val groups = mutableListOf<List<JourneyStop>>()
            var current = mutableListOf<JourneyStop>()
            var offset = 0
            for (stop in journey.stops) {
                val trial = current + stop
                // Reserve space for final part counters and compressed-header variation.
                if ((trial.size > MAX_STOPS_PER_LINK || payload(trial, offset, 1, 1).size > MAX_PAYLOAD_BYTES ||
                        link(prefix, trial, offset, 1, 1).length > MAX_LINK_BYTES - 100) && current.isNotEmpty()) {
                    groups.add(current.toList())
                    offset += current.size
                    current = mutableListOf()
                }
                current.add(stop)
            }
            if (current.isNotEmpty() || groups.isEmpty()) groups.add(current.toList())
            offset = 0
            return groups.mapIndexed { index, stops ->
                require(payload(stops, offset, index + 1, groups.size).size <= MAX_PAYLOAD_BYTES) { "한 방문 기록이 너무 커요. CSV 파일로 저장해 주세요." }
                val result = link(prefix, stops, offset, index + 1, groups.size)
                require(result.toByteArray(Charsets.UTF_8).size <= MAX_LINK_BYTES) { "한 방문 기록이 QR에 담기에는 너무 커요. CSV 파일로 저장해 주세요." }
                offset += stops.size
                result
            }
        }

        private fun payload(stops: List<JourneyStop>, offset: Int, part: Int, parts: Int): ByteArray {
            val data = JSONObject().put("v", 1).put("kind", "journey").put("title", journey.title)
                .put("startedAt", journey.startedAt).put("endedAt", journey.endedAt ?: JSONObject.NULL)
                .put("exportedAt", exportedAt).put("zone", zone.id).put("km", journey.distanceKm)
                .put("sample", sample).put("offset", offset).put("part", part).put("parts", parts).put("total", journey.stops.size)
                .put("stops", JSONArray(stops.map { stop -> JSONArray(listOf(
                    stop.arrivedAt, stop.departedAt ?: JSONObject.NULL,
                    stop.point?.latitude ?: JSONObject.NULL, stop.point?.longitude ?: JSONObject.NULL,
                    stop.name, stop.address, stop.favorite, stop.note, stop.interrupted,
                )) }))
            return data.toString().toByteArray(Charsets.UTF_8)
        }

        private fun link(prefix: String, stops: List<JourneyStop>, offset: Int, part: Int, parts: Int): String {
            val compressor = Deflater(Deflater.BEST_COMPRESSION, true)
            val out = ByteArrayOutputStream()
            try {
                compressor.setInput(payload(stops, offset, part, parts))
                compressor.finish()
                val buffer = ByteArray(1024)
                while (!compressor.finished()) out.write(buffer, 0, compressor.deflate(buffer))
            } finally { compressor.end() }
            return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        }
    }
}
