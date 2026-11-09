/*
 * 방문 장소를 휴대폰 지도에서 바로 여는 QR 링크를 만든다. 서버나 웹페이지 없이 동작한다.
 *
 * -Author: Haneol Kim
 * -Contact: bestwook7@gmail.com
 */
package ai.pleos.challenge.arrival

import java.net.URLEncoder
import java.util.Locale

/** Maps a stop's QR can open. Kakao and Naver use app links; Google's link falls back to the browser by itself. */
enum class StopMapApp(val label: String) {
    KAKAO("카카오맵"),
    NAVER("네이버 지도"),
    GOOGLE("구글맵"),
}

/** Naver's scheme requires the caller's application id on every link. */
private const val NAVER_APP_NAME = "ai.pleos.challenge.arrival"

/**
 * The link for this stop's QR: its recorded parking position, or a search for its address and name
 * without a usable GPS fix. Null when there is nothing to locate.
 */
fun JourneyStop.mapQrLink(app: StopMapApp): String? {
    val located = point?.takeIf { it.valid }
    if (located != null) {
        val lat = String.format(Locale.US, "%.5f", located.latitude)
        val lng = String.format(Locale.US, "%.5f", located.longitude)
        return when (app) {
            StopMapApp.KAKAO -> "kakaomap://look?p=$lat,$lng"
            StopMapApp.NAVER -> "nmap://place?lat=$lat&lng=$lng&name=${encoded(name.ifBlank { "주차 위치" })}&appname=$NAVER_APP_NAME"
            StopMapApp.GOOGLE -> "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        }
    }
    val query = listOf(address, name).filter { it.isNotBlank() }.joinToString(" ")
    if (query.isEmpty()) return null
    return when (app) {
        StopMapApp.KAKAO -> "kakaomap://search?q=${encoded(query)}"
        StopMapApp.NAVER -> "nmap://search?query=${encoded(query)}&appname=$NAVER_APP_NAME"
        StopMapApp.GOOGLE -> "https://www.google.com/maps/search/?api=1&query=${encoded(query)}"
    }
}

/** URLEncoder writes spaces as `+`, which these links would show as a literal plus sign. */
private fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
