package ai.pleos.challenge.arrival

import android.content.Context

/**
 * Explicit consent required by the Pleos App Market privacy review: say what is collected, why, and where it goes,
 * and count only a tap on the agree button as consent. Withdrawing stops collection and deletes stored data.
 */
object Consent {
    /** Bump when the collected items change so everyone is asked again. */
    const val VERSION = 8

    data class Item(val title: String, val detail: String)

    val items = listOf(
        Item("음성 (마이크)", "음성 명령과 주차 위치를 알아듣는 데 써요. 말하기를 눌렀을 때와, 주행 뒤 주차했을 때 앱이 먼저 묻는 질문에 답하는 몇 초 동안만 마이크를 열어요. 차 안에서 글자로 바꾸며, 녹음은 저장하지 않아요."),
        Item("차량 정보", "기어 상태와 계기판 누적 거리로 운행 시작·끝과 주행 거리를 기록해요."),
        Item("위치와 여행 기록", "내비게이션 주소와 GPS 좌표로 출발지·도착지·주차 위치를 기록해요. 동의 후 앱이 실행 중이면 내비게이션 등 다른 화면을 보는 동안에도 이동 경로와 경유지의 주차 시각을 자동으로 수집해요(알림에 '운행을 기록하고 있어요' 표시). 그날의 경로와 들른 곳은 그날만 보관하고, 별표한 곳과 그 메모만 날이 지나도 남겨요. 모두 차량 안에만 저장해요. '주차 위치 주변 검색'을 누르면 해당 주차 좌표를 카카오에 보내 장소 후보를 받아요. 지도 링크를 열면 좌표가 해당 지도 서비스로 전달돼요."),
        Item("휴대폰 번호 (입력한 경우)", "문자 QR의 받는 사람으로만 써요."),
    )

    const val STORAGE = "운행·여행 기록과 음성에서 변환한 메모는 이 차량 안에 보관해요. 녹음은 저장하지 않아요. 주변 검색 시 선택한 주차 좌표만 카카오에 보내며 전체 이동 경로는 보내지 않아요. 문자는 직접 보내기를 누를 때만 휴대폰에서 발송돼요. 운행기록부·여행 기록 QR은 기록을 링크 안에 담아 휴대폰에서 펼쳐요(웹 서버로 전송되지 않음). 예시 여행은 실제 기록과 별개예요."
    const val WITHDRAW = "화면 오른쪽 위 ⚙ 설정의 '동의 철회 및 기록 삭제'를 누르면 바로 수집을 멈추고 저장된 기록을 모두 지워요."
}

class ConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences("consent", Context.MODE_PRIVATE)

    var granted: Boolean
        get() = prefs.getInt("version", 0) == Consent.VERSION
        set(value) = prefs.edit().putInt("version", if (value) Consent.VERSION else 0).apply()
}
