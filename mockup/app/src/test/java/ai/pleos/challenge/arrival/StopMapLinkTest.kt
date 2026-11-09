/*
 * 방문 장소의 지도 QR 링크(카카오맵·네이버 지도·구글맵)가 올바르게 만들어지는지 검증하는 테스트.
 *
 * -Author: Haneol Kim
 * -Contact: bestwook7@gmail.com
 */
package ai.pleos.challenge.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class StopMapLinkTest {
    private val point = JourneyPoint(37.4501234, 127.1298765, 0)

    @Test fun kakaoOpensAppAtStoredCoordinates() {
        val stop = JourneyStop("a", 0, point, name = "숲길 식당")
        assertEquals("kakaomap://look?p=37.45012,127.12988", stop.mapQrLink(StopMapApp.KAKAO))
    }

    /** Java's URLEncoder writes spaces as `+`, which would appear literally in the place name. */
    @Test fun naverOpensAppWithEncodedPlaceNameAndCallerId() {
        val stop = JourneyStop("a", 0, point, name = "숲길 식당")
        assertEquals(
            "nmap://place?lat=37.45012&lng=127.12988&name=%EC%88%B2%EA%B8%B8%20%EC%8B%9D%EB%8B%B9&appname=ai.pleos.challenge.arrival",
            stop.mapQrLink(StopMapApp.NAVER),
        )
    }

    /** Naver's scheme requires `name`, and most automatically recorded stops have none. */
    @Test fun naverUsesParkingLabelForUnnamedStop() {
        val stop = JourneyStop("a", 0, point, name = " ")
        assertEquals(
            "nmap://place?lat=37.45012&lng=127.12988&name=%EC%A3%BC%EC%B0%A8%20%EC%9C%84%EC%B9%98&appname=ai.pleos.challenge.arrival",
            stop.mapQrLink(StopMapApp.NAVER),
        )
    }

    @Test fun googleOpensAtStoredCoordinates() {
        val stop = JourneyStop("a", 0, point, name = "숲길 식당")
        assertEquals("https://www.google.com/maps/search/?api=1&query=37.45012,127.12988", stop.mapQrLink(StopMapApp.GOOGLE))
    }

    /** A decimal comma would turn latitude and longitude into four numbers and break the link. */
    @Test fun coordinatesKeepDecimalPointInCommaLocales() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals("kakaomap://look?p=37.45012,127.12988", JourneyStop("a", 0, point).mapQrLink(StopMapApp.KAKAO))
        } finally {
            Locale.setDefault(original)
        }
    }

    /** Underground stops often have no GPS fix, only the navigation address. */
    @Test fun stopWithoutCoordinatesSearchesByAddressAndNameInEveryMap() {
        val stop = JourneyStop("a", 0, null, name = "숲길", address = "가평군")
        val query = "%EA%B0%80%ED%8F%89%EA%B5%B0%20%EC%88%B2%EA%B8%B8"
        assertEquals("kakaomap://search?q=$query", stop.mapQrLink(StopMapApp.KAKAO))
        assertEquals("nmap://search?query=$query&appname=ai.pleos.challenge.arrival", stop.mapQrLink(StopMapApp.NAVER))
        assertEquals("https://www.google.com/maps/search/?api=1&query=$query", stop.mapQrLink(StopMapApp.GOOGLE))
    }

    @Test fun invalidCoordinatesAreNotPutInALink() {
        val stop = JourneyStop("a", 0, JourneyPoint(Double.NaN, 127.0, 0), address = "가평군")
        assertEquals("kakaomap://search?q=%EA%B0%80%ED%8F%89%EA%B5%B0", stop.mapQrLink(StopMapApp.KAKAO))
    }

    @Test fun stopWithNoLocationInformationHasNoLink() {
        val stop = JourneyStop("a", 0, null, name = "", address = "  ")
        assertNull(stop.mapQrLink(StopMapApp.KAKAO))
        assertNull(stop.mapQrLink(StopMapApp.NAVER))
        assertNull(stop.mapQrLink(StopMapApp.GOOGLE))
    }
}
