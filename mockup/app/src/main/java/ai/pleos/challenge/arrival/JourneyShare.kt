package ai.pleos.challenge.arrival

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What a voice command asks the journey tab to put in its phone column. */
enum class JourneyShareMode { VIEW, CSV }

/**
 * The day's visits as a link QR, shown in the journey tab's phone column: the phone page for reading them
 * ([csv] false) or the one that downloads a spreadsheet ([csv] true). Long days are split over several QRs.
 */
@Composable
fun JourneyQrShare(
    snapshot: JourneyExport.Snapshot, base: String, csv: Boolean, message: String,
    onSave: (JourneyExport.Snapshot) -> Unit,
) {
    val result = remember(snapshot, base, csv) { runCatching { snapshot.links(base, csv = csv) } }
    val links = result.getOrDefault(emptyList())
    var index by remember(snapshot, csv) { mutableIntStateOf(0) }
    Text("${snapshot.journey.title}, 방문 ${snapshot.journey.stops.size}곳", fontSize = Ui.Body, color = Ui.Ink)
    // The record QR carries the data itself, so it is denser than the others and gets the full column width.
    QrPlate(
        links.getOrNull(index), "${if (csv) "엑셀 다운로드" else "발자취 기록 보기"} QR ${index + 1}/${links.size}",
        empty = result.exceptionOrNull()?.message ?: "QR을 만들지 못했어요.", size = 380.dp,
    )
    if (links.size > 1) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("이전", enabled = index > 0) { index-- }
            Text("${index + 1} / ${links.size}", fontSize = Ui.Body, color = Ui.Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            QuietButton("다음", enabled = index < links.lastIndex) { index++ }
        }
        Note(if (csv) "QR마다 스캔해서 저장해야 전체 방문이 보관돼요." else "방문 기록이 여러 QR로 나뉘었어요. 차례로 스캔해 주세요.", color = Ui.Warn)
    }
    Note(if (csv) "카메라로 스캔해 휴대폰에서 CSV를 받고 엑셀로 열어요." else "카메라로 스캔하면 방문 장소와 별표, 메모가 열려요.")
    if (csv) {
        QuietButton("차량에 CSV 저장") { onSave(snapshot) }
        if (message.isNotBlank()) Note(message, color = Ui.Accent)
    }
    Note("휴대폰으로 가져간 사본은 동의를 철회해도 지워지지 않아요. 전체 이동 경로는 담지 않아요.")
}

/** One stop's map link as a QR: the phone opens the map itself, with no hosted page. */
@Composable
fun StopMapShare(stop: JourneyStop?, app: StopMapApp, onPick: (StopMapApp) -> Unit) {
    if (stop == null) return QrPlate(null, "지도 열기 QR", empty = "들른 곳을 고르면 지도 QR이 나타나요.")
    Text(stop.name.ifBlank { stop.address.ifBlank { "이 방문의 위치" } }, fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink)
    Segmented(StopMapApp.entries.map { it.label }, StopMapApp.entries.indexOf(app)) { onPick(StopMapApp.entries[it]) }
    QrPlate(stop.mapQrLink(app), "지도 열기 QR", empty = "위치와 주소가 없어 지도를 열 수 없어요.")
    Note(
        (if (app == StopMapApp.GOOGLE) "카메라로 스캔하면 구글 지도가 열려요. 앱이 없으면 브라우저에서 열려요."
        else "카메라로 스캔하면 ${app.label} 앱이 열려요. 앱이 설치되어 있어야 해요.") + " 기본 지도 앱은 설정에서 정해요.",
    )
    Note(
        (if (stop.point?.valid == true) "저장된 주차 좌표를 열어요. 실제 입구와 다를 수 있어요." else "GPS 좌표가 없어 기록된 주소로 검색해요.") +
            " 이 위치는 고른 지도 서비스에 전달돼요.",
    )
}
