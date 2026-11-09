package ai.pleos.challenge.arrival

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class JourneyShareMode { VIEW, CSV }

@Composable
fun JourneyExportDialog(
    snapshot: JourneyExport.Snapshot, base: String, message: String, mode: JourneyShareMode,
    onSave: (JourneyExport.Snapshot) -> Unit, onDismiss: () -> Unit,
) {
    val csv = mode == JourneyShareMode.CSV
    val result = remember(snapshot, base, mode) { runCatching { snapshot.links(base, csv = csv) } }
    val links = result.getOrDefault(emptyList())
    var index by remember(snapshot, mode) { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (csv) "엑셀 내보내기" else "휴대폰으로 여행 기록 보기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${snapshot.journey.title} · 방문 ${snapshot.journey.stops.size}곳", fontSize = 18.sp)
                Text(if (csv) "QR을 스캔한 뒤 휴대폰에서 CSV를 내려받아 Excel로 열어 주세요." else "QR을 스캔하면 방문 장소·별표·메모를 볼 수 있어요. 각 장소의 카카오맵 링크로 주차 위치를 확인해 보세요.")
                if (snapshot.sample) Text("가상의 예시 데이터예요. 지도는 예시 좌표를 표시합니다.")
                if (links.isNotEmpty()) {
                    val bitmap = remember(links[index]) { runCatching { qrBitmap(links[index], 800).asImageBitmap() } }
                    bitmap.getOrNull()?.let {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(it, contentDescription = "${if (csv) "엑셀 다운로드" else "여행 기록 보기"} QR ${index + 1}/${links.size}", modifier = Modifier.size(320.dp))
                        }
                    }
                    if (bitmap.isFailure) Text("QR을 표시하지 못했어요. 기록이 짧은 날짜로 다시 시도해 주세요.")
                    if (links.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { index-- }, enabled = index > 0) { Text("이전 QR") }
                        Text("${index + 1} / ${links.size}")
                        TextButton(onClick = { index++ }, enabled = index < links.lastIndex) { Text("다음 QR") }
                    }
                    if (links.size > 1) Text(if (csv) "모든 QR을 스캔해 각각 저장해야 전체 방문이 보관돼요." else "방문 기록이 여러 QR로 나뉘었어요. 모든 QR을 차례로 확인해 주세요.")
                } else Text(result.exceptionOrNull()?.message ?: "QR을 만들지 못했어요.")
                if (csv && message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                Text("공유한 사본은 차량의 동의 철회로 삭제되지 않아요. 전체 이동 경로는 포함하지 않아요.", fontSize = 13.sp)
            }
        },
        confirmButton = {
            if (csv) Button(onClick = { onSave(snapshot) }) { Text("차량에 CSV 저장") }
            else TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = { if (csv) TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** One stop's map link as a QR: the phone opens the map itself, with no hosted page. */
@Composable
fun StopMapDialog(stop: JourneyStop, sample: Boolean, app: StopMapApp, onAppChange: (StopMapApp) -> Unit, onDismiss: () -> Unit) {
    val link = stop.mapQrLink(app)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("휴대폰 지도로 열기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stop.name.ifBlank { stop.address.ifBlank { "이 방문의 위치" } }, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StopMapApp.entries.forEach { option ->
                        FilterChip(selected = option == app, onClick = { onAppChange(option) }, label = { Text(option.label) })
                    }
                }
                Text(if (app == StopMapApp.GOOGLE) "카메라로 찍으면 구글맵이 열려요. 앱이 없으면 브라우저에서 열려요." else "카메라로 찍으면 ${app.label} 앱이 바로 열려요. 앱이 설치되어 있어야 해요.")
                val bitmap = remember(link) { link?.let { runCatching { qrBitmap(it, 800).asImageBitmap() } } }?.getOrNull()
                if (bitmap != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(bitmap, contentDescription = "지도 열기 QR", modifier = Modifier.size(320.dp))
                    }
                } else Text("QR을 만들지 못했어요.")
                if (sample) Text("가상의 예시 데이터예요. 지도는 예시 좌표를 표시합니다.")
                Text(if (stop.point?.valid == true) "저장된 주차 좌표를 열어요. 실제 장소의 입구와 다를 수 있어요." else "GPS 좌표가 없어 기록된 주소로 검색해요.", fontSize = 13.sp)
                Text("QR을 열면 이 위치가 선택한 지도 서비스에 전달돼요. 별표와 메모는 담기지 않아요.", fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
