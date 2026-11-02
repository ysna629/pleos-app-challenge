package ai.pleos.challenge.arrival

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One logbook row: odometer before/after a drive plus where the car was parked. */
data class Trip(
    val startedAt: Long,
    val endedAt: Long,
    val startKm: Float,
    val endKm: Float,
    val startAddress: String,
    val endAddress: String,
    val purpose: Purpose = Purpose.BUSINESS,
    val parking: String = "",
) {
    val distanceKm: Float get() = (endKm - startKm).coerceAtLeast(0f)
    val minutes: Long get() = (endedAt - startedAt) / 60_000

    /** Plain text a phone camera shows after scanning the QR code. */
    fun shareText(): String = buildString {
        appendLine("[도착노트] ${formatDate(endedAt)}")
        appendLine("운행: ${km(startKm)} → ${km(endKm)} (${km(distanceKm)}, ${purpose.label})")
        if (endAddress.isNotBlank()) appendLine("도착지: $endAddress")
        append("주차 위치: ${parking.ifBlank { "기록 안 함" }}")
    }

    /** Short SMS (aimed at a single 90-byte message): parking spot first, then when and how far. */
    fun smsBody(): String {
        val spot = parking.ifBlank { "주차 위치 기록 안 함" }
        val place = endAddress.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val time = SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(endedAt))
        val distance = String.format(Locale.KOREA, "%.1fkm", distanceKm)
        return "[도착노트] $spot$place\n$time · $distance ${purpose.short}"
    }

    /**
     * QR payload `SMSTO:<phone>:<body>` that opens the messaging app addressed to [phone] with
     * [smsBody] filled in. A recipient is required: on iPhone an `sms:` link without one only
     * offers "add to contacts".
     */
    fun smsQrText(phone: String): String = "SMSTO:${phone.filter(Char::isDigit)}:${smsBody()}"

    fun toJson(): JSONObject = JSONObject()
        .put("startedAt", startedAt).put("endedAt", endedAt)
        .put("startKm", startKm.toDouble()).put("endKm", endKm.toDouble())
        .put("startAddress", startAddress).put("endAddress", endAddress)
        .put("purpose", purpose.name).put("parking", parking)

    companion object {
        fun fromJson(o: JSONObject) = Trip(
            startedAt = o.getLong("startedAt"),
            endedAt = o.getLong("endedAt"),
            startKm = o.getDouble("startKm").toFloat(),
            endKm = o.getDouble("endKm").toFloat(),
            startAddress = o.optString("startAddress"),
            endAddress = o.optString("endAddress"),
            purpose = runCatching { Purpose.valueOf(o.getString("purpose")) }.getOrDefault(Purpose.BUSINESS),
            parking = o.optString("parking"),
        )
    }
}

/** Usage categories of the NTS business-vehicle logbook (업무용승용차 운행기록부). */
enum class Purpose(val label: String, val short: String, val code: Char) {
    COMMUTE("출퇴근", "출퇴근", 'C'),
    BUSINESS("일반 업무", "업무", 'B'),
    PERSONAL("비업무", "비업무", 'P'),
}

fun km(value: Float): String = String.format(Locale.KOREA, "%,.1fkm", value)

fun formatDate(millis: Long): String = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(millis))

/** Keeps the logbook in SharedPreferences; enough for a mockup. */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)

    fun load(): List<Trip> {
        val array = JSONArray(prefs.getString("items", "[]"))
        return (0 until array.length()).map { Trip.fromJson(array.getJSONObject(it)) }
    }

    fun save(trips: List<Trip>) {
        prefs.edit().putString("items", JSONArray(trips.map { it.toJson() }).toString()).apply()
    }

    /** The user's own number; the QR addresses the SMS to it so it lands in their inbox. */
    var phone: String
        get() = prefs.getString("phone", "").orEmpty()
        set(value) = prefs.edit().putString("phone", value).apply()

    /** Deletes trips, segments and the phone number (consent withdrawn). Segments share this file. */
    fun clearAll() = prefs.edit().clear().apply()
}
