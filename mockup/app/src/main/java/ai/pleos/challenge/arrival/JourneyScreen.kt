package ai.pleos.challenge.arrival

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

private val JourneyAccent = Color(0xFF5EC8B8)
private val JourneyPanel = Color(0xFF1A252F)
private val JourneyMuted = Color(0xFF9CACBA)
private val JourneyGold = Color(0xFFF1CD84)

/** The same app, the same vehicle signals; sample changes remain in Compose memory only. */
@Composable
fun JourneyScreen(
    journeys: List<Journey>, parked: Boolean?, preview: Boolean, onPreviewChange: (Boolean) -> Unit,
    onSpeak: () -> Unit, listening: Boolean, voiceStatus: String,
    shareRequest: JourneyShareMode?, onShareRequestHandled: () -> Unit,
    onUpdate: (String, String, (JourneyStop) -> JourneyStop) -> Unit,
    onVoiceNote: (String, String) -> Unit,
    recording: Boolean, partial: String, feedback: String,
    viewerBase: String, exportMessage: String,
    onSaveCsv: (JourneyExport.Snapshot) -> Unit,
    onOpenExport: () -> Unit, busy: Boolean,
    stopMapApp: StopMapApp, onStopMapAppChange: (StopMapApp) -> Unit,
) {
    var sample by remember { mutableStateOf(sampleJourney()) }
    var selectedJourney by remember { mutableStateOf<String?>(null) }
    val journey = if (preview) sample else journeys.firstOrNull { it.id == selectedJourney } ?: journeys.firstOrNull()
    var selectedStopId by remember(journey?.id, preview) { mutableStateOf<String?>(null) }
    val stop = journey?.stops?.firstOrNull { it.id == selectedStopId } ?: journey?.stops?.lastOrNull()
    val canEdit = parked == true && !busy
    var mode by remember { mutableStateOf(JourneyShareMode.VIEW) }
    var export by remember { mutableStateOf<JourneyExport.Snapshot?>(null) }
    LaunchedEffect(parked) { if (parked != true) export = null }

    LaunchedEffect(shareRequest, parked, busy, journey?.id) {
        if (shareRequest != null && parked == true && !busy) {
            journey?.let { onOpenExport(); mode = shareRequest; export = JourneyExport.snapshot(it, preview) }
            onShareRequestHandled()
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("여행 기록", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("우연히 들른 곳도, 다시 찾고 싶은 곳으로.", color = JourneyMuted, fontSize = 17.sp)
            }
            FilterChip(selected = preview, onClick = { onPreviewChange(!preview) }, label = { Text(if (preview) "예시 여행 보는 중" else "예시 여행 보기") })
            OutlinedButton(onClick = { journey?.let { onOpenExport(); mode = JourneyShareMode.VIEW; export = JourneyExport.snapshot(it, preview) } }, enabled = canEdit && journey != null) {
                Text("휴대폰으로 보기", fontSize = 17.sp)
            }
            OutlinedButton(onClick = { journey?.let { onOpenExport(); mode = JourneyShareMode.CSV; export = JourneyExport.snapshot(it, preview) } }, enabled = canEdit && journey != null) {
                Text("엑셀 내보내기", fontSize = 17.sp)
            }
            Button(onClick = onSpeak, enabled = !busy) { Text("🎤 말하기", fontSize = 17.sp) }
        }
        if (listening || voiceStatus.isNotBlank()) Text(if (listening) partial.ifBlank { "듣고 있어요…" } else voiceStatus, color = JourneyGold, fontSize = 17.sp)
        if (!preview) Text("이동과 경유지를 자동으로 기록해요 · 예: 여기 기억해 줘 / 휴대폰으로 보여 줘 / 엑셀로 내보내 줘", color = JourneyMuted, fontSize = 14.sp)
        if (preview) {
            Text("미리보기 · 가상의 장소와 이동 경로예요. 실제 운행기록부에는 저장되지 않아요.", color = JourneyGold, fontSize = 15.sp)
        } else if (parked != true) {
            Text(if (parked == null) "기어 확인 중 · P가 확인되면 기록을 수정하거나 공유할 수 있어요." else "주행 중에는 경로와 도착만 조용히 기록해요. 장소 저장과 메모는 정차 후에 해 주세요.", color = JourneyGold, fontSize = 15.sp)
        }

        if (journey == null) {
            JourneyCard(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("발견한 장소를 잊지 않도록", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("차량 연결 후 이동 경로와 주차한 곳을 자동으로 모아요.\n마음에 든 곳은 별표와 한마디로 남겨 보세요.", color = JourneyMuted, fontSize = 20.sp)
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { onPreviewChange(true) }) { Text("가족 나들이 예시 둘러보기", fontSize = 20.sp) }
                }
            }
        } else {
            if (!preview && journeys.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(journeys, key = { _, j -> j.id }) { _, j ->
                        FilterChip(selected = j.id == journey.id, onClick = { selectedJourney = j.id }, label = { Text("${day(j.startedAt)} ${if (j.endedAt == null) "기록 중" else "여행"}") })
                    }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                JourneyCard(Modifier.weight(1.15f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(journey.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${day(journey.startedAt)} · ${if (journey.endedAt == null) "여행 중" else "여행 완료"}", color = JourneyMuted, fontSize = 16.sp)
                        }
                        Text(if (preview) "SAMPLE" else if (journey.endedAt == null) "● 기록 중" else "기록 완료", color = JourneyAccent, fontSize = 15.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        JourneyStat("%.1f km".format(Locale.KOREA, journey.distanceKm), "수집된 GPS 경로")
                        JourneyStat("${journey.stops.size}곳", "방문 후보")
                        JourneyStat("${journey.stops.count { it.favorite }}곳", "다시 갈 곳")
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF10212D), RoundedCornerShape(14.dp))) {
                        RouteOverview(journey, stop?.id, { selectedStopId = it }, Modifier.fillMaxSize())
                        Text("N ↑", Modifier.align(Alignment.TopEnd).padding(16.dp), color = JourneyMuted)
                        if (journey.route.isEmpty() && journey.stops.none { it.point != null }) {
                            Text("위치를 기다리고 있어요\nGPS가 잡히면 경로가 나타나요.", Modifier.align(Alignment.Center).padding(24.dp), color = JourneyMuted, fontSize = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("GPS 경로 미리보기 · 도로 지도 배경은 포함하지 않아요. 번호를 누르면 방문 기록을 선택해요. 같은 위치의 다른 기록은 아래 버튼에서 확인하세요.", color = JourneyMuted, fontSize = 13.sp)
                    if (journey.route.any { it.breakBefore }) Text("앱이 닫혀 있던 구간은 연결하지 않아요.", color = JourneyGold, fontSize = 13.sp)
                }

                JourneyCard(Modifier.weight(1f).fillMaxHeight()) {
                    Text("우리의 발자취", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("자동으로 모으고, 마음에 든 곳만 남겨요.", color = JourneyMuted, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(journey.stops, key = { _, s -> s.id }) { index, s ->
                            Row(Modifier.fillMaxWidth().background(if (s.id == stop?.id) Color(0xFF293E48) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { selectedStopId = s.id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", color = JourneyAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name.ifBlank { s.address.ifBlank { "어디에 들르셨나요?" } }, color = Color.White, fontSize = 18.sp)
                                    Text("${clock(s.arrivedAt)} · ${if (s.passedBy) "지나가며 저장" else if (s.interrupted) "기록 중단" else if (s.departedAt == null && journey.endedAt == null) "머무는 중" else "${s.durationMs(s.departedAt ?: s.arrivedAt) / 60_000}분 머묾"}", color = JourneyMuted, fontSize = 14.sp)
                                }
                                Text(if (s.favorite) "★" else "○", color = if (s.favorite) JourneyGold else JourneyMuted, fontSize = 22.sp)
                            }
                        }
                        if (journey.stops.isEmpty()) item {
                            Text("P로 주차하면 첫 방문 후보가 생겨요.", color = JourneyMuted, fontSize = 18.sp, modifier = Modifier.padding(vertical = 20.dp))
                        }
                    }
                    if (stop != null) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFF344450))
                        StopDetails(
                            stop, preview, canEdit, recording, partial, feedback, stopMapApp, onStopMapAppChange,
                            onUpdate ={ change -> if (canEdit) { if (preview) sample = sample.updateStop(stop.id, change) else onUpdate(journey.id, stop.id, change) } },
                            onVoice = { if (canEdit) { if (preview) sample = sample.updateStop(stop.id) { it.copy(note = "아이도 좋아했어요. 다음 나들이에 또 오기!") } else onVoiceNote(journey.id, stop.id) } },
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                }
            }
        }
    }
    export?.let { snapshot ->
        JourneyExportDialog(snapshot, viewerBase, exportMessage, mode, onSaveCsv) { export = null }
    }

}

@Composable
private fun StopDetails(
    stop: JourneyStop, preview: Boolean, canEdit: Boolean, recording: Boolean, partial: String, feedback: String,
    mapApp: StopMapApp, onMapAppChange: (StopMapApp) -> Unit,
    onUpdate:((JourneyStop) -> JourneyStop) -> Unit, onVoice: () -> Unit, modifier: Modifier,
) {
    var category by remember(stop.id) { mutableStateOf("FD6") }
    var request by remember(stop.id) { mutableIntStateOf(0) }
    var candidates by remember(stop.id) { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var searching by remember(stop.id) { mutableStateOf(false) }
    var message by remember(stop.id) { mutableStateOf("") }
    var manual by remember(stop.id) { mutableStateOf(false) }
    var note by remember(stop.id, stop.note) { mutableStateOf(stop.note) }
    var mapQr by remember(stop.id) { mutableStateOf(false) }
    LaunchedEffect(canEdit) { if (!canEdit) mapQr = false }

    LaunchedEffect(stop.id, request) {
        if (request == 0) return@LaunchedEffect
        candidates = emptyList()
        message = ""
        if (preview) {
            candidates = listOf(NearbyPlace("sample-1", "숲길 가족식당", "가상 음식점", "미리보기용 장소", 35),
                NearbyPlace("sample-2", "느린 오후 카페", "가상 카페", "미리보기용 장소", 80))
        } else if (stop.point == null) message = "이 방문에는 GPS 좌표가 없어요. 메모로 기억을 남겨 주세요."
        else if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) message = "아직 장소 검색이 연결되지 않았어요. 위치는 기록되어 있고 메모를 남길 수 있어요."
        else {
            searching = true
            try {
                candidates = withContext(Dispatchers.IO) { KakaoPlaces(BuildConfig.KAKAO_REST_API_KEY).search(stop.point, category) }
                if (candidates.isEmpty()) message = "300m 안에 이 종류의 장소가 없어요. 다른 종류를 골라 보세요."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = "장소를 불러오지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요." }
            finally { searching = false }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stop.name.ifBlank { "이곳을 기억할까요?" }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onUpdate { it.copy(favorite = !it.favorite) } }, enabled = canEdit) {
                Text(if (stop.favorite) "★ 저장됨" else "☆ 다시 갈 곳", color = JourneyGold, fontSize = 17.sp)
            }
        }
        if (stop.point == null) Text("위치 정보 없음 · 메모는 남길 수 있어요.", color = JourneyGold, fontSize = 14.sp)
        if (stop.address.isNotBlank()) Text(stop.address, color = JourneyMuted, fontSize = 14.sp)
        if (stop.note.isNotBlank() && !manual) Text("“${stop.note}”", color = JourneyAccent, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onVoice, enabled = canEdit) { Text(if (preview) "예시 음성 메모" else "음성 메모", fontSize = 17.sp) }
            OutlinedButton(onClick = { manual = !manual }, enabled = canEdit) { Text("메모 수정", fontSize = 17.sp) }
        }
        if (stop.mapQrLink(mapApp) != null) {
            OutlinedButton(onClick = { mapQr = true }, enabled = canEdit) { Text("휴대폰 지도로 열기", fontSize = 17.sp) }
        }
        if (recording) Text("듣고 있어요… $partial", color = JourneyAccent, fontSize = 17.sp)
        if (!preview && feedback.isNotBlank()) Text(feedback, color = JourneyMuted, fontSize = 15.sp)
        if (manual) {
            OutlinedTextField(value = note, onValueChange = { note = it.take(240) }, enabled = canEdit, label = { Text("이곳에서의 한마디") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onUpdate { it.copy(note = note.trim()) }; manual = false }, enabled = canEdit) { Text("메모 저장") }
        }
        Text("근처 장소에서 확인", color = JourneyMuted, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("FD6" to "음식점", "CE7" to "카페", "AT4" to "명소").forEach { (code, label) ->
                FilterChip(selected = category == code, onClick = { category = code }, enabled = canEdit && !searching, label = { Text(label) })
            }
        }
        OutlinedButton(onClick = { request++ }, enabled = canEdit && !searching && (preview || stop.point != null)) {
            Text(if (searching) "장소 찾는 중…" else if (preview) "예시 장소 후보 보기" else "주차 위치 주변 검색", fontSize = 17.sp)
        }
        if (!preview) Text("검색할 때 이 주차 위치를 카카오에 전달해요. 카카오계정 로그인 없이 사용할 수 있어요.", color = JourneyMuted, fontSize = 13.sp)
        if (message.isNotBlank()) Text(message, color = JourneyGold, fontSize = 15.sp)
        candidates.forEach { place ->
            OutlinedButton(onClick = { onUpdate { it.copy(name = place.name) }; candidates = emptyList() }, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(place.name, color = Color.White, fontSize = 17.sp)
                    Text("${place.category} · ${place.distanceMeters}m${if (preview) " · 가상 장소" else " · Kakao"}", color = JourneyMuted, fontSize = 13.sp)
                }
            }
        }
    }
    if (mapQr && canEdit) StopMapDialog(stop, preview, mapApp, onMapAppChange) { mapQr = false }
}

@Composable
private fun JourneyCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(JourneyPanel, RoundedCornerShape(18.dp)).padding(20.dp), content = content)
}

@Composable
private fun JourneyStat(value: String, label: String) {
    Column { Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text(label, color = JourneyMuted, fontSize = 13.sp) }
}

/** Geographical projection of collected coordinates; intentionally no invented roads or inferred routes. */
@Composable
private fun RouteOverview(journey: Journey, selected: String?, onSelect: (String) -> Unit, modifier: Modifier) {
    val points = journey.route + journey.stops.mapNotNull { it.point }
    BoxWithConstraints(modifier) {
        val width = maxWidth.value
        val height = maxHeight.value
        val minLat = points.minOfOrNull { it.latitude } ?: 0.0
        val maxLat = points.maxOfOrNull { it.latitude } ?: 0.0
        val minLng = points.minOfOrNull { it.longitude } ?: 0.0
        val maxLng = points.maxOfOrNull { it.longitude } ?: 0.0
        val centerLat = (minLat + maxLat) / 2
        val centerLng = (minLng + maxLng) / 2
        val lngFactor = cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)
        val spanLat = (maxLat - minLat).coerceAtLeast(0.002)
        val spanLng = ((maxLng - minLng) * lngFactor).coerceAtLeast(0.002)
        val scale = minOf((width - 100).coerceAtLeast(1f) / spanLng, (height - 100).coerceAtLeast(1f) / spanLat)
        fun project(p: JourneyPoint) = Offset((width / 2 + (p.longitude - centerLng) * lngFactor * scale).toFloat(), (height / 2 - (p.latitude - centerLat) * scale).toFloat())
        Canvas(Modifier.fillMaxSize()) {
            val unit = 44.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(Color(0xFF1C3441), Offset(x, 0f), Offset(x, size.height), 1f); x += unit }
            var y = 0f
            while (y < size.height) { drawLine(Color(0xFF1C3441), Offset(0f, y), Offset(size.width, y), 1f); y += unit }
            val path = Path()
            journey.route.forEachIndexed { i, p ->
                val pos = project(p)
                if (i == 0 || p.breakBefore) path.moveTo(pos.x.dp.toPx(), pos.y.dp.toPx()) else path.lineTo(pos.x.dp.toPx(), pos.y.dp.toPx())
            }
            drawPath(path, JourneyAccent.copy(alpha = 0.12f), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, JourneyAccent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        val markers = journeyMarkers(journey.stops) { point ->
            val position = project(point)
            position.x.toDouble() to position.y.toDouble()
        }
        markers.forEach { marker ->
            key(marker.visits.first().value.id) {
                val visit = marker.representative(selected)
                val pos = project(visit.value.point!!)
                val highlighted = visit.value.id == selected
                val markerColor = if (highlighted) JourneyAccent else if (visit.value.favorite) JourneyGold else Color(0xFF2D4351)
                val left = (pos.x - 66).coerceIn(0f, (width - 132).coerceAtLeast(0f))
                var expanded by remember { mutableStateOf(false) }
                Column(Modifier.offset(left.dp, (pos.y - 22).coerceIn(0f, (height - 80).coerceAtLeast(0f)).dp).width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(if (visit.value.favorite) 72.dp else 44.dp).height(44.dp).background(markerColor, RoundedCornerShape(16.dp))
                        .clickable { onSelect(visit.value.id) }, contentAlignment = Alignment.Center) {
                        Text("${visit.index + 1}${if (visit.value.favorite) " ★" else ""}", color = if (highlighted || visit.value.favorite) Color(0xFF10212D) else Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                    if (marker.visits.size > 1) Box {
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) {
                            Text("같은 위치 ${marker.visits.size}곳", color = JourneyMuted, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            marker.visits.forEach { item ->
                                DropdownMenuItem(text = {
                                    Text("${item.index + 1}. ${item.value.name.ifBlank { item.value.address.ifBlank { "방문 기록" } }}${if (item.value.favorite) " ★" else ""}")
                                }, onClick = { expanded = false; onSelect(item.value.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun clock(at: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(at))
private fun day(at: Long): String = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(at))

private fun sampleJourney(): Journey {
    val start = java.time.LocalDate.now().atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val coordinates = listOf(37.49 to 127.10, 37.491 to 127.12, 37.50 to 127.15, 37.515 to 127.16, 37.53 to 127.175,
        37.535 to 127.19, 37.55 to 127.20, 37.56 to 127.215, 37.565 to 127.23, 37.58 to 127.235, 37.59 to 127.25)
    val route = coordinates.mapIndexed { i, (lat, lng) -> JourneyPoint(lat, lng, start + i * 600_000) }
    return Journey("sample", "주말, 우리 가족의 작은 발견", start, start + 21_600_000, route,
        listOf(
            JourneyStop("sample-park", start + 3_600_000, route[2], start + 6_600_000, "초록숲 산책길", "유모차로 걷기 좋아요", true),
            JourneyStop("sample-food", start + 9_000_000, route[5], start + 12_600_000, "숲길 가족식당", "아이도 잘 먹었어요. 다음에 또 오기!", true),
            JourneyStop("sample-cafe", start + 15_000_000, route[8], start + 17_400_000, "느린 오후 카페", "창가 자리가 좋아요"),
            JourneyStop("sample-last", start + 19_800_000, route[10], start + 21_600_000),
        ))
}
