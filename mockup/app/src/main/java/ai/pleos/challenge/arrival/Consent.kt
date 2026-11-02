package ai.pleos.challenge.arrival

import android.content.Context

/**
 * Explicit consent required by the Pleos App Market privacy review: say what is collected, why, and where it goes,
 * and count only a tap on the agree button as consent. Withdrawing stops collection and deletes stored data.
 */
object Consent {
    /** Bump when the collected items change so everyone is asked again. */
    const val VERSION = 3

    data class Item(val title: String, val detail: String)

    val items = listOf(
        Item("음성 (마이크)", "음성 명령과 주차 위치를 알아듣는 데 써요. 차 안에서 글자로 바꾸며, 녹음은 저장하지 않아요."),
        Item("차량 정보", "기어 상태와 계기판 누적 거리로 운행 시작·끝과 주행 거리를 기록해요."),
        Item("위치", "내비게이션의 현재 주소로 출발지와 도착지를, GPS 좌표로 주차한 곳을 기록해요. 좌표는 문자 속 지도 링크를 만드는 데만 쓰며, 휴대폰에서 링크를 열 때 그 지도 서비스로 전달돼요."),
        Item("휴대폰 번호 (입력한 경우)", "문자 QR의 받는 사람으로만 써요."),
    )

    const val STORAGE = "모든 기록은 이 차량 안에만 저장되고 외부 서버로 보내지 않아요. 문자는 직접 보내기를 누를 때만 내 휴대폰에서 발송돼요. 월간 운행기록부 QR은 기록을 링크 안에 담아 내 휴대폰 브라우저에서만 펼쳐요(웹 서버로 전송되지 않음)."
    const val WITHDRAW = "화면 위쪽의 '동의 철회'를 누르면 바로 수집을 멈추고 저장된 기록을 모두 지워요."
}

class ConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences("consent", Context.MODE_PRIVATE)

    var granted: Boolean
        get() = prefs.getInt("version", 0) == Consent.VERSION
        set(value) = prefs.edit().putInt("version", if (value) Consent.VERSION else 0).apply()
}
