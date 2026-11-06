package ai.pleos.challenge.arrival

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Shares the consent-cleared preferences file; demo data is never persisted. */
class JourneyStore(context: Context) {
    private val prefs = context.getSharedPreferences("trips", Context.MODE_PRIVATE)
    fun load(): List<Journey> = runCatching {
        val array = JSONArray(prefs.getString("journeys", "[]"))
        (0 until array.length()).map { journeyFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(journeys: List<Journey>) {
        prefs.edit().putString("journeys", JSONArray(journeys.map(::journeyToJson)).toString()).apply()
    }
}

private fun pointJson(point: JourneyPoint): JSONArray = JSONArray(listOf(point.latitude, point.longitude, point.at, point.breakBefore))
private fun pointFromJson(a: JSONArray) = JourneyPoint(a.getDouble(0), a.getDouble(1), a.getLong(2), a.optBoolean(3))

internal fun journeyToJson(j: Journey): JSONObject = JSONObject()
    .put("id", j.id).put("title", j.title).put("startedAt", j.startedAt).put("endedAt", j.endedAt ?: JSONObject.NULL)
    .put("route", JSONArray(j.route.map(::pointJson)))
    .put("stops", JSONArray(j.stops.map { s -> JSONObject()
        .put("id", s.id).put("arrivedAt", s.arrivedAt).put("departedAt", s.departedAt ?: JSONObject.NULL)
        .put("point", s.point?.let(::pointJson) ?: JSONObject.NULL)
        .put("name", s.name).put("note", s.note).put("favorite", s.favorite).put("interrupted", s.interrupted).put("address", s.address)
    }))

internal fun journeyFromJson(o: JSONObject): Journey {
    val route = o.getJSONArray("route")
    val stops = o.getJSONArray("stops")
    return Journey(o.getString("id"), o.getString("title"), o.getLong("startedAt"),
        if (o.isNull("endedAt")) null else o.getLong("endedAt"),
        (0 until route.length()).map { pointFromJson(route.getJSONArray(it)) },
        (0 until stops.length()).map { index ->
            val s = stops.getJSONObject(index)
            JourneyStop(s.getString("id"), s.getLong("arrivedAt"), if (s.isNull("point")) null else pointFromJson(s.getJSONArray("point")),
                if (s.isNull("departedAt")) null else s.getLong("departedAt"),
                s.optString("name"), s.optString("note"), s.optBoolean("favorite"), s.optBoolean("interrupted"), s.optString("address"))
        })
}
