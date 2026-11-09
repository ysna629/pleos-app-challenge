package ai.pleos.challenge.arrival

/** Keyword matching for the short voice answers the app asks for. Good enough for a mockup. */
object Commands {
    enum class Intent { LOGBOOK, PARK_ONLY, WHERE_PARKED, JOURNEY, SAVE_PLACE, SHOW_JOURNEY_QR, EXPORT_JOURNEY_CSV, UNKNOWN }

    fun parseIntent(raw: String): Intent {
        val text = raw.replace(" ", "")
        return when {
            listOf("엑셀", "CSV", "csv", "씨에스브이").any(text::contains) -> Intent.EXPORT_JOURNEY_CSV
            listOf("휴대폰", "핸드폰", "큐알", "QR", "qr").any(text::contains) -> Intent.SHOW_JOURNEY_QR
            // Before SAVE_PLACE ("기억해"), WHERE_PARKED ("주차위치") and LOGBOOK ("기록"), which these phrases also contain.
            listOf("주차위치기록", "주차기록", "주차위치저장", "주차저장", "주차위치남겨", "주차위치기억", "주차메모", "주차위치메모", "주차위치만", "주차만", "여기주차").any(text::contains) -> Intent.PARK_ONLY
            listOf("여기저장", "이곳저장", "장소저장", "여기기억", "여기를기억", "이곳기억", "기억해", "찜해", "괜찮은데").any(text::contains) -> Intent.SAVE_PLACE
            listOf("여행기록", "여행지도", "나들이", "여행보여").any(text::contains) -> Intent.JOURNEY
            listOf("어디주차", "주차어디", "주차위치", "차어디", "어디세웠", "어디댔").any(text::contains) -> Intent.WHERE_PARKED
            listOf("운행일지", "운행기록", "일지", "정리", "기록").any(text::contains) -> Intent.LOGBOOK
            else -> Intent.UNKNOWN
        }
    }

    /** true for yes, false for no, null when unclear. "아니" is checked first so "아니 괜찮아" is a no. */
    fun parseYesNo(raw: String): Boolean? {
        val text = raw.replace(" ", "")
        if (text.isEmpty()) return null
        if (listOf("아니", "괜찮아", "됐어", "안해", "필요없", "싫어", "노").any(text::startsWith) ||
            listOf("아니", "괜찮", "됐어", "안할", "안해", "필요없").any(text::contains)
        ) return false
        if (text.trimEnd('.', '!') == "어") return true
        if (listOf("네", "예", "응", "그래", "좋아", "기록", "해줘", "할게", "맞아", "맞습니다", "저장", "오케이").any(text::contains)) return true
        return null
    }

    fun parsePurpose(raw: String): Purpose? {
        val text = raw.replace(" ", "")
        return when {
            listOf("출퇴근", "출근", "퇴근").any(text::contains) -> Purpose.COMMUTE
            listOf("비업무", "개인", "사적", "업무아니", "업무아냐").any(text::contains) -> Purpose.PERSONAL
            listOf("업무", "일반", "회사", "출장", "외근", "미팅").any(text::contains) -> Purpose.BUSINESS
            else -> null
        }
    }
}
