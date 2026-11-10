package ai.pleos.challenge.arrival

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

/**
 * The journey tab: where the car stopped today, the route it took, and the same phone column as every other tab.
 * Sample changes remain in Compose memory only.
 */
@Composable
fun JourneyScreen(
    journeys: List<Journey>, parked: Boolean?, preview: Boolean, onPreviewChange: (Boolean) -> Unit,
    shareRequest: JourneyShareMode?, onShareRequestHandled: () -> Unit,
    onUpdate: (String, String, (JourneyStop) -> JourneyStop) -> Unit,
    onVoiceNote: (String, String) -> Unit,
    recording: Boolean, partial: String, feedback: String,
    viewerBase: String, exportMessage: String,
    onSaveCsv: (JourneyExport.Snapshot) -> Unit,
    onOpenExport: () -> Unit, busy: Boolean,
    stopMapApp: StopMapApp,
) {
    var sample by remember { mutableStateOf(sampleJourney()) }
    var selectedJourney by remember { mutableStateOf<String?>(null) }
    val journey = if (preview) sample else journeys.firstOrNull { it.id == selectedJourney } ?: journeys.firstOrNull()
    var selectedStopId by remember(journey?.id, preview) { mutableStateOf<String?>(null) }
    val stop = journey?.stops?.firstOrNull { it.id == selectedStopId } ?: journey?.stops?.lastOrNull()
    val canEdit = parked == true && !busy
    var share by remember { mutableIntStateOf(SHARE_VIEW) }
    // The map app from settings is the default; the phone column can switch it for one QR.
    var mapPick by remember(stopMapApp) { mutableStateOf(stopMapApp) }

    // "휴대폰으로 보여 줘" and "엑셀로 내보내 줘" pick what the phone column shows.
    LaunchedEffect(shareRequest) {
        if (shareRequest != null) {
            onOpenExport()
            share = if (shareRequest == JourneyShareMode.CSV) SHARE_CSV else SHARE_VIEW
            onShareRequestHandled()
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            if (journey == null) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Heading("아직 들른 곳이 없어요")
                        Spacer(Modifier.height(12.dp))
                        Note("주행하고 주차하면 들른 곳과 이동 경로가 여기에 모여요.")
                        Spacer(Modifier.height(24.dp))
                        QuietButton("예시 여행 보기") { onPreviewChange(true) }
                    }
                }
            } else {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Heading(journey.title)
                    when {
                        preview -> Note("예시 여행이에요. 실제 기록에는 저장되지 않아요.", color = Ui.Warn)
                        parked == null -> Note("기어를 확인하고 있어요. 주차(P)가 확인되면 고치거나 보낼 수 있어요.", color = Ui.Warn)
                        else -> Note(if (journey.endedAt == null) "기록하고 있어요" else "기록을 마쳤어요")
                    }
                    if (!preview && journeys.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        Segmented(journeys.map { day(it.startedAt) }, journeys.indexOfFirst { it.id == journey.id }) { selectedJourney = journeys[it].id }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(journey.stops, key = { _, s -> s.id }) { index, s ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (s.id == stop?.id) Ui.Raised else Color.Transparent)
                                    .clickable { selectedStopId = s.id }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${index + 1}", color = Ui.Muted, fontSize = Ui.Body, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name.ifBlank { s.address.ifBlank { "이름 없는 곳" } }, color = Ui.Ink, fontSize = Ui.Body, maxLines = 1)
                                    Note("${clock(s.arrivedAt)}, ${stay(s, journey)}")
                                }
                                // The star is on every row: grey until tapped, gold when it is a place to come back to.
                                IconButton(
                                    onClick = {
                                        if (canEdit) {
                                            if (preview) sample = sample.updateStop(s.id) { it.copy(favorite = !it.favorite) }
                                            else onUpdate(journey.id, s.id) { it.copy(favorite = !it.favorite) }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = if (s.favorite) "다시 갈 곳" else "다시 갈 곳으로 표시", tint = if (s.favorite) Ui.Star else Ui.Line, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        if (journey.stops.isEmpty()) item { Note("주차(P)하면 첫 방문이 여기에 생겨요.", modifier = Modifier.padding(vertical = 20.dp)) }
                    }
                    if (stop != null) {
                        Rule(Modifier.padding(vertical = 14.dp))
                        StopDetails(
                            stop, preview, canEdit, recording, partial, feedback,
                            onUpdate = { change -> if (canEdit) { if (preview) sample = sample.updateStop(stop.id, change) else onUpdate(journey.id, stop.id, change) } },
                            onVoice = { if (canEdit) { if (preview) sample = sample.updateStop(stop.id) { it.copy(note = "아이도 좋아했어요. 다음 나들이에 또 오기!") } else onVoiceNote(journey.id, stop.id) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Panel(Modifier.weight(1.15f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Figure("%.1f km".format(Locale.KOREA, journey.distanceKm), "이동")
                        Figure("${journey.stops.size}곳", "방문")
                        Figure("${journey.stops.count { it.favorite }}곳", "다시 갈 곳")
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.weight(1f).fillMaxWidth().clip(Ui.ControlShape).background(Ui.Bg)) {
                        RouteOverview(journey, stop?.id, { selectedStopId = it }, Modifier.fillMaxSize())
                        if (journey.route.isEmpty() && journey.stops.none { it.point != null }) {
                            Note("GPS가 잡히면 경로가 나타나요.", modifier = Modifier.align(Alignment.Center).padding(24.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Note("GPS로 받은 경로만 그려요. 위쪽이 북쪽이고, 번호를 누르면 그 방문을 골라요.")
                            if (journey.route.any { it.breakBefore }) Note("앱이 닫혀 있던 구간은 잇지 않아요.", color = Ui.Warn)
                        }
                        QuietButton(if (preview) "예시 닫기" else "예시 여행 보기") { onPreviewChange(!preview) }
                    }
                }
            }
        }

        SharePanel(listOf("여행 기록", "엑셀", "이 장소 지도"), share, { onOpenExport(); share = it }) {
            when {
                journey == null -> QrPlate(null, "여행 기록 QR", empty = "기록이 생기면 QR이 나타나요.")
                !canEdit -> QrPlate(null, "여행 기록 QR", empty = if (parked == true) "지금 하던 일을 마치면 QR이 나타나요." else "주차 중일 때만 QR을 보여 줘요.")
                share == SHARE_MAP -> StopMapShare(stop, mapPick) { mapPick = it }
                else -> {
                    val snapshot = remember(journey.id, journey.title, journey.stops, preview) { JourneyExport.snapshot(journey, preview) }
                    JourneyQrShare(snapshot, viewerBase, csv = share == SHARE_CSV, message = exportMessage, onSave = onSaveCsv)
                }
            }
        }
    }
}

private const val SHARE_VIEW = 0
private const val SHARE_CSV = 1
private const val SHARE_MAP = 2

private fun stay(stop: JourneyStop, journey: Journey): String = when {
    stop.passedBy -> "지나가며 저장"
    stop.interrupted -> "기록 중단"
    stop.departedAt == null && journey.endedAt == null -> "머무는 중"
    else -> "${stop.durationMs(stop.departedAt ?: stop.arrivedAt) / 60_000}분 머묾"
}

@Composable
private fun StopDetails(
    stop: JourneyStop, preview: Boolean, canEdit: Boolean, recording: Boolean, partial: String, feedback: String,
    onUpdate: ((JourneyStop) -> JourneyStop) -> Unit, onVoice: () -> Unit, modifier: Modifier,
) {
    var category by remember(stop.id) { mutableStateOf("FD6") }
    var request by remember(stop.id) { mutableIntStateOf(0) }
    var candidates by remember(stop.id) { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var searching by remember(stop.id) { mutableStateOf(false) }
    var message by remember(stop.id) { mutableStateOf("") }
    var manual by remember(stop.id) { mutableStateOf(false) }
    var note by remember(stop.id, stop.note) { mutableStateOf(stop.note) }

    LaunchedEffect(stop.id, request) {
        if (request == 0) return@LaunchedEffect
        candidates = emptyList()
        message = ""
        if (preview) {
            candidates = listOf(NearbyPlace("sample-1", "숲길 가족식당", "가상 음식점", "미리보기용 장소", 35),
                NearbyPlace("sample-2", "느린 오후 카페", "가상 카페", "미리보기용 장소", 80))
        } else if (stop.point == null) message = "이 방문에는 GPS 좌표가 없어요. 메모로 남겨 주세요."
        else if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) message = "장소 검색이 아직 연결되지 않았어요. 위치는 기록되어 있고 메모를 남길 수 있어요."
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

    val categories = listOf("FD6" to "음식점", "CE7" to "카페", "AT4" to "명소")
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stop.name.ifBlank { stop.address.ifBlank { "이름 없는 곳" } }, color = Ui.Ink, fontSize = Ui.Heading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(Ui.ControlShape).clickable(enabled = canEdit) { onUpdate { it.copy(favorite = !it.favorite) } }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = if (stop.favorite) Ui.Star else Ui.Line, modifier = Modifier.size(30.dp))
                Text(if (stop.favorite) "다시 갈 곳" else "다시 갈 곳으로 표시", color = if (stop.favorite) Ui.Star else Ui.Muted, fontSize = Ui.Small)
            }
        }
        if (stop.name.isNotBlank() && stop.address.isNotBlank()) Note(stop.address)
        if (stop.point == null) Note("위치 정보가 없어요. 메모는 남길 수 있어요.", color = Ui.Warn)
        if (stop.note.isNotBlank() && !manual) Text(stop.note, color = Ui.Ink, fontSize = Ui.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("음성 메모", enabled = canEdit, onClick = onVoice)
            QuietButton(if (manual) "메모 닫기" else "메모 고치기", enabled = canEdit) { manual = !manual }
        }
        if (recording) Note("듣고 있어요  $partial", color = Ui.Accent)
        if (!preview && feedback.isNotBlank()) Note(feedback)
        if (manual) {
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(240) }, enabled = canEdit, label = { Text("이곳에서의 한마디") },
                textStyle = TextStyle(fontSize = Ui.Body), modifier = Modifier.fillMaxWidth(),
            )
            QuietButton("메모 저장", enabled = canEdit) { onUpdate { it.copy(note = note.trim()) }; manual = false }
        }
        Note("주변에서 이름 찾기")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(categories.map { it.second }, categories.indexOfFirst { it.first == category }, enabled = canEdit && !searching) { category = categories[it].first }
            QuietButton(if (searching) "찾는 중" else "찾기", enabled = canEdit && !searching && (preview || stop.point != null)) { request++ }
        }
        if (!preview) Note("찾을 때 이 주차 위치가 카카오에 전달돼요. 카카오계정 로그인은 필요 없어요.")
        if (message.isNotBlank()) Note(message, color = Ui.Warn)
        candidates.forEach { place ->
            Column(
                Modifier.fillMaxWidth().clip(Ui.ControlShape).background(Ui.Raised).clickable(enabled = canEdit) { onUpdate { it.copy(name = place.name) }; candidates = emptyList() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(place.name, color = Ui.Ink, fontSize = Ui.Body)
                Note("${place.category}, ${place.distanceMeters}m${if (preview) ", 가상 장소" else ", Kakao"}")
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Ui.Muted, fontSize = Ui.Small, modifier = Modifier.padding(bottom = 4.dp))
        Text(value, color = Ui.Ink, fontSize = Ui.Heading, fontWeight = FontWeight.Bold)
    }
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
            while (x < size.width) { drawLine(Ui.Line.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), 1f); x += unit }
            var y = 0f
            while (y < size.height) { drawLine(Ui.Line.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), 1f); y += unit }
            val path = Path()
            journey.route.forEachIndexed { i, p ->
                val pos = project(p)
                if (i == 0 || p.breakBefore) path.moveTo(pos.x.dp.toPx(), pos.y.dp.toPx()) else path.lineTo(pos.x.dp.toPx(), pos.y.dp.toPx())
            }
            drawPath(path, Ui.Accent.copy(alpha = 0.12f), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, Ui.Accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
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
                val markerColor = if (highlighted) Ui.Accent else if (visit.value.favorite) Ui.Star else Ui.Raised
                val left = (pos.x - 66).coerceIn(0f, (width - 132).coerceAtLeast(0f))
                var expanded by remember { mutableStateOf(false) }
                Column(Modifier.offset(left.dp, (pos.y - 22).coerceIn(0f, (height - 80).coerceAtLeast(0f)).dp).width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).background(markerColor, RoundedCornerShape(24.dp))
                        .clickable { onSelect(visit.value.id) }, contentAlignment = Alignment.Center) {
                        Text("${visit.index + 1}", color = if (highlighted || visit.value.favorite) Ui.OnAccent else Ui.Ink, fontWeight = FontWeight.Bold, fontSize = Ui.Body)
                    }
                    if (marker.visits.size > 1) Box {
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) {
                            Text("같은 위치 ${marker.visits.size}곳", color = Ui.Muted, fontSize = 18.sp)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            marker.visits.forEach { item ->
                                DropdownMenuItem(text = {
                                    Text("${item.index + 1}. ${item.value.name.ifBlank { item.value.address.ifBlank { "방문 기록" } }}${if (item.value.favorite) " (다시 갈 곳)" else ""}", fontSize = Ui.Small)
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
    return Journey("sample", "가족 나들이 예시", start, start + 21_600_000, route,
        listOf(
            JourneyStop("sample-park", start + 3_600_000, route[2], start + 6_600_000, "초록숲 산책길", "유모차로 걷기 좋아요", true),
            JourneyStop("sample-food", start + 9_000_000, route[5], start + 12_600_000, "숲길 가족식당", "아이도 잘 먹었어요. 다음에 또 오기!", true),
            JourneyStop("sample-cafe", start + 15_000_000, route[8], start + 17_400_000, "느린 오후 카페", "창가 자리가 좋아요"),
            JourneyStop("sample-last", start + 19_800_000, route[10], start + 21_600_000),
        ))
}
