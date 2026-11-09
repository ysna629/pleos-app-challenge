package ai.pleos.challenge.arrival

import org.junit.Assert.*
import org.junit.Test

class JourneyStorageTest {
    @Test fun persistencePreservesRouteBreaksUnlocatedStopsAndUserNotes() {
        val journey = Journey("id", "가족 여행", 1_000, 100_000,
            listOf(JourneyPoint(37.4, 127.1, 2_000, true)),
            listOf(JourneyStop("stop", 3_000, null, 90_000, "우리 식당", "아이와 오기 좋아요", true, true, "성남시 시흥동")))
        assertEquals(journey, journeyFromJson(journeyToJson(journey)))
    }

    @Test fun nearbyPlacesUseRoadAddressThenFallbackAndSortByDistance() {
        val places = KakaoPlaces.parse("""{"documents":[
            {"id":"2","place_name":"카페","category_group_name":"카페","address_name":"지번","road_address_name":"","distance":"100"},
            {"id":"1","place_name":"식당","road_address_name":"도로명","distance":"20"},
            {"id":"1","place_name":"중복","distance":"20"}
        ]}""")
        assertEquals(listOf("1", "2"), places.map { it.id })
        assertEquals("지번", places.last().address)
        assertEquals("도로명", places.first().address)
        assertTrue(KakaoPlaces.parse("""{"documents":[]}""").isEmpty())
    }
}
