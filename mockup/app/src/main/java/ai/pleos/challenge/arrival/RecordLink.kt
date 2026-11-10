package ai.pleos.challenge.arrival

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * One trip or one parking note as a phone page link (`viewer/record.html`). The page shows the record and lets
 * the driver open it in a map app or send it as a text, instead of the QR dropping straight into the SMS composer.
 * Like the monthly link the record travels in the `#` fragment, so it never reaches a server.
 */
object RecordLink {
    const val PAGE = "record.html"

    fun url(trip: Trip, phone: String, map: MapApp, base: String = LogbookLink.BASE_URL): String = url(
        JSONObject().put("v", 1).put("k", "trip")
            .put("s", trip.startedAt).put("e", trip.endedAt)
            .put("a", Math.round(trip.startKm * 10)).put("b", Math.round(trip.endKm * 10))
            .put("p", trip.purpose.code.toString()).put("spot", trip.parking).put("addr", trip.endAddress),
        trip.endLat, trip.endLng, phone, map, base,
    )

    fun url(note: ParkingNote, phone: String, map: MapApp, base: String = LogbookLink.BASE_URL): String = url(
        JSONObject().put("v", 1).put("k", "park").put("e", note.savedAt).put("spot", note.spot).put("addr", note.address),
        note.lat, note.lng, phone, map, base,
    )

    private fun url(data: JSONObject, lat: Double?, lng: Double?, phone: String, map: MapApp, base: String): String {
        if (lat != null && lng != null) data.put("lat", round(lat)).put("lng", round(lng))
        phone.filter(Char::isDigit).takeIf { it.length >= 10 }?.let { data.put("ph", it) }
        data.put("m", map.name.first().toString())
        val bytes = data.toString().toByteArray(Charsets.UTF_8)
        return base.trimEnd('/') + "/" + PAGE + "#" + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(bytes))
    }

    private fun round(value: Double): Double = Math.round(value * 100_000) / 100_000.0

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
}
