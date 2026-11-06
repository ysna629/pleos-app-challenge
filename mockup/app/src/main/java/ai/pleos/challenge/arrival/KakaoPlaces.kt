package ai.pleos.challenge.arrival

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NearbyPlace(val id: String, val name: String, val category: String, val address: String, val distanceMeters: Int)

/** No user login required. Requests leave the car only after the user taps nearby search. */
class KakaoPlaces(private val key: String) {
    fun search(point: JourneyPoint, category: String): List<NearbyPlace> {
        require(key.isNotBlank()) { "Kakao API key is not configured" }
        require(point.valid)
        require(category in setOf("FD6", "CE7", "MT1", "AT4"))
        val url = URL("https://dapi.kakao.com/v2/local/search/category.json?category_group_code=$category&x=${point.longitude}&y=${point.latitude}&radius=300&sort=distance&size=8")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("Authorization", "KakaoAK $key")
            check(connection.responseCode == 200) { "장소 검색에 연결하지 못했어요 (${connection.responseCode}). 잠시 후 다시 시도해 주세요." }
            return parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    companion object {
        internal fun parse(json: String): List<NearbyPlace> {
            val array = JSONObject(json).getJSONArray("documents")
            return (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                NearbyPlace(o.getString("id"), o.getString("place_name"), o.optString("category_group_name"),
                    o.optString("road_address_name").ifBlank { o.optString("address_name") },
                    o.optString("distance").toIntOrNull() ?: Int.MAX_VALUE)
            }.distinctBy { it.id }.sortedBy { it.distanceMeters }
        }
    }
}
