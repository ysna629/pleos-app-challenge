package ai.pleos.challenge.arrival

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One stretch between leaving P and returning to P, recorded silently in the background.
 * [arriveAt] is null while the car is still out of P.
 */
data class Segment(
    val departAt: Long,
    val departKm: Float,
    val departAddress: String = "",
    val arriveAt: Long? = null,
    val arriveKm: Float? = null,
    val arriveAddress: String = "",
) {
    val isClosed: Boolean get() = arriveAt != null && arriveKm != null

    fun toJson(): JSONObject = JSONObject()
        .put("departAt", departAt).put("departKm", departKm.toDouble()).put("departAddress", departAddress)
        .put("arriveAt", arriveAt ?: JSONObject.NULL).put("arriveKm", arriveKm?.toDouble() ?: JSONObject.NULL)
        .put("arriveAddress", arriveAddress)

    companion object {
        fun fromJson(o: JSONObject) = Segment(
            departAt = o.getLong("departAt"),
            departKm = o.getDouble("departKm").toFloat(),
            departAddress = o.optString("departAddress"),
            arriveAt = if (o.isNull("arriveAt")) null else o.getLong("arriveAt"),
            arriveKm = if (o.isNull("arriveKm")) null else o.getDouble("arriveKm").toFloat(),
            arriveAddress = o.optString("arriveAddress"),
        )
    }
}

object Logbook {
    /** Shorter merged drives (moving the car within a car park, P -> R -> P) are not logbook trips. */
    const val MIN_TRIP_KM = 0.3f

    /**
     * Merges every closed segment since the last logbook entry into one trip: first departure to last arrival.
     * A stop at a convenience store therefore does not split the drive. Returns null when there is nothing worth logging.
     */
    fun merge(segments: List<Segment>): Trip? {
        // Skip segments without a usable odometer reading (0 before the first update, or a value that went down).
        val closed = segments.filter { it.isClosed && it.departKm > 0f && it.arriveKm!! >= it.departKm }.sortedBy { it.departAt }
        val first = closed.firstOrNull() ?: return null
        val last = closed.last()
        val trip = Trip(
            startedAt = first.departAt,
            endedAt = last.arriveAt!!,
            startKm = first.departKm,
            endKm = last.arriveKm!!,
            startAddress = first.departAddress,
            endAddress = last.arriveAddress,
        )
        return trip.takeIf { it.distanceKm >= MIN_TRIP_KM }
    }
}

/** Unorganized segments, kept until the user asks for the logbook. */
class SegmentStore(context: Context) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)

    fun load(): List<Segment> {
        val array = JSONArray(prefs.getString("segments", "[]"))
        return (0 until array.length()).map { Segment.fromJson(array.getJSONObject(it)) }
    }

    fun save(segments: List<Segment>) {
        prefs.edit().putString("segments", JSONArray(segments.map { it.toJson() }).toString()).apply()
    }
}
