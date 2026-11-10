package ai.pleos.challenge.arrival

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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
    val endLat: Double? = null,
    val endLng: Double? = null,
) {
    val distanceKm: Float get() = (endKm - startKm).coerceAtLeast(0f)
    val minutes: Long get() = (endedAt - startedAt) / 60_000

    /** Plain text a phone camera shows after scanning the QR code. */
    fun shareText(): String = buildString {
        appendLine("[Route94] ${formatDate(endedAt)}")
        appendLine("운행: ${km(startKm)} → ${km(endKm)} (${km(distanceKm)}, ${purpose.label})")
        if (endAddress.isNotBlank()) appendLine("도착지: $endAddress")
        append("주차 위치: ${parking.ifBlank { "기록 안 함" }}")
    }

    /** Map link for where the car was parked, or null without a GPS fix. */
    fun mapLink(map: MapApp = MapApp.GOOGLE): String? {
        if (endLat == null || endLng == null) return null
        return map.link(String.format(Locale.US, "%.5f", endLat), String.format(Locale.US, "%.5f", endLng))
    }

    /**
     * SMS body: parking spot first, then when and how far, then a map link when there is a GPS fix.
     * Without the link it fits a single 90-byte message; the link turns it into an LMS.
     */
    fun smsBody(map: MapApp = MapApp.GOOGLE): String {
        val spot = parking.ifBlank { "주차 위치 기록 안 함" }
        val place = endAddress.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val time = SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(endedAt))
        val distance = String.format(Locale.KOREA, "%.1fkm", distanceKm)
        val link = mapLink(map)?.let { "\n$it" } ?: ""
        return "[Route94] $spot$place\n$time · $distance ${purpose.short}$link"
    }

    /**
     * QR payload `SMSTO:<phone>:<body>` that opens the messaging app addressed to [phone] with
     * [smsBody] filled in. A recipient is required: on iPhone an `sms:` link without one only
     * offers "add to contacts".
     */
    fun smsQrText(phone: String, map: MapApp = MapApp.GOOGLE): String = "SMSTO:${phone.filter(Char::isDigit)}:${smsBody(map)}"

    fun toJson(): JSONObject = JSONObject()
        .put("startedAt", startedAt).put("endedAt", endedAt)
        .put("startKm", startKm.toDouble()).put("endKm", endKm.toDouble())
        .put("startAddress", startAddress).put("endAddress", endAddress)
        .put("purpose", purpose.name).put("parking", parking)
        .put("endLat", endLat ?: JSONObject.NULL).put("endLng", endLng ?: JSONObject.NULL)

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
            endLat = if (o.isNull("endLat")) null else o.getDouble("endLat"),
            endLng = if (o.isNull("endLng")) null else o.getDouble("endLng"),
        )
    }
}

/** A parking spot saved on its own, for a drive the driver does not want in the logbook. */
data class ParkingNote(
    val savedAt: Long,
    val spot: String,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun mapLink(map: MapApp = MapApp.GOOGLE): String? {
        if (lat == null || lng == null) return null
        return map.link(String.format(Locale.US, "%.5f", lat), String.format(Locale.US, "%.5f", lng))
    }

    /** Same shape as [Trip.smsBody] without the distance, which only a logbook trip has. */
    fun smsBody(map: MapApp = MapApp.GOOGLE): String {
        val place = address.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val time = SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(savedAt))
        val link = mapLink(map)?.let { "\n$it" } ?: ""
        return "[Route94] $spot$place\n$time 주차$link"
    }

    fun smsQrText(phone: String, map: MapApp = MapApp.GOOGLE): String = "SMSTO:${phone.filter(Char::isDigit)}:${smsBody(map)}"

    fun toJson(): JSONObject = JSONObject()
        .put("savedAt", savedAt).put("spot", spot).put("address", address)
        .put("lat", lat ?: JSONObject.NULL).put("lng", lng ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = ParkingNote(
            savedAt = o.getLong("savedAt"),
            spot = o.optString("spot"),
            address = o.optString("address"),
            lat = if (o.isNull("lat")) null else o.getDouble("lat"),
            lng = if (o.isNull("lng")) null else o.getDouble("lng"),
        )

        /** Every spot, newest first, whether it was saved with a logbook trip or on its own. */
        fun all(trips: List<Trip>, notes: List<ParkingNote>): List<ParkingNote> =
            (trips.filter { it.parking.isNotBlank() }.map { ParkingNote(it.endedAt, it.parking, it.endAddress, it.endLat, it.endLng) } + notes)
                .sortedByDescending { it.savedAt }

        fun latest(trips: List<Trip>, notes: List<ParkingNote>): ParkingNote? = all(trips, notes).firstOrNull()
    }
}

/** Usage categories of the NTS business-vehicle logbook (업무용승용차 운행기록부). */
enum class Purpose(val label: String, val short: String, val code: Char) {
    COMMUTE("출퇴근", "출퇴근", 'C'),
    BUSINESS("일반 업무", "업무", 'B'),
    PERSONAL("비업무", "비업무", 'P'),
}

/**
 * The 기본 정보 block of the logbook form (`viewer/index.html`): company, business number, car model, plate, department,
 * driver. Any field may stay blank; the phone page then shows an empty cell. Keys match the page's field ids.
 */
data class LogbookProfile(
    val company: String = "", val bizNo: String = "", val model: String = "",
    val plate: String = "", val dept: String = "", val name: String = "",
) {
    /** Only what was filled in, keyed as the viewer page expects. */
    fun fields(): Map<String, String> = mapOf(
        "company" to company, "bizNo" to bizNo, "model" to model, "plate" to plate, "dept" to dept, "name" to name,
    ).filterValues { it.isNotBlank() }

    fun toJson(): JSONObject = JSONObject().apply { fields().forEach { (k, v) -> put(k, v) } }

    companion object {
        /** Settings-screen order and labels, the same as on the form. */
        val LABELS = listOf("company" to "상호(법인명)", "bizNo" to "사업자등록번호", "model" to "차종", "plate" to "자동차등록번호", "dept" to "부서", "name" to "성명")

        fun fromJson(o: JSONObject) = LogbookProfile(
            o.optString("company"), o.optString("bizNo"), o.optString("model"), o.optString("plate"), o.optString("dept"), o.optString("name"),
        )
    }

    fun get(key: String): String = fields()[key].orEmpty()

    fun with(key: String, value: String): LogbookProfile = when (key) {
        "company" -> copy(company = value); "bizNo" -> copy(bizNo = value); "model" -> copy(model = value)
        "plate" -> copy(plate = value); "dept" -> copy(dept = value); "name" -> copy(name = value)
        else -> this
    }
}

/** Map apps whose web links open on both iPhone and Android, from an SMS or a camera scan. */
enum class MapApp(val label: String) {
    KAKAO("카카오맵") {
        override fun link(lat: String, lng: String) = "https://map.kakao.com/link/map/${URLEncoder.encode("주차", "UTF-8")},$lat,$lng"
    },
    // The SMS needs an https link to be tappable, so not the nmap:// scheme the journey QR uses.
    NAVER("네이버 지도") {
        override fun link(lat: String, lng: String) =
            "https://m.map.naver.com/map.naver?lat=$lat&lng=$lng&pinTitle=${URLEncoder.encode("주차", "UTF-8")}&pinType=site&dlevel=11"
    },
    GOOGLE("구글 지도") {
        override fun link(lat: String, lng: String) = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
    };

    abstract fun link(lat: String, lng: String): String
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

    fun loadParking(): List<ParkingNote> {
        val array = JSONArray(prefs.getString("parking", "[]"))
        return (0 until array.length()).map { ParkingNote.fromJson(array.getJSONObject(it)) }
    }

    fun saveParking(notes: List<ParkingNote>) {
        prefs.edit().putString("parking", JSONArray(notes.map { it.toJson() }).toString()).apply()
    }

    /** The user's own number; the QR addresses the SMS to it so it lands in their inbox. */
    var phone: String
        get() = prefs.getString("phone", "").orEmpty()
        set(value) = prefs.edit().putString("phone", value).apply()

    /** The logbook form's header, typed once in settings. */
    var profile: LogbookProfile
        get() = runCatching { LogbookProfile.fromJson(JSONObject(prefs.getString("profile", "{}").orEmpty())) }.getOrDefault(LogbookProfile())
        set(value) = prefs.edit().putString("profile", value.toJson().toString()).apply()

    /** Deletes trips, parking notes, segments and the phone number (consent withdrawn). Segments share this file. */
    fun clearAll() = prefs.edit().clear().apply()
}
