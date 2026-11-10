package ai.pleos.challenge.arrival

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The journey tab's other view: every stop the driver starred, from any day, newest first. Today's view only
 * keeps today, so this is where a starred place is found again. Editing needs the car parked, like today's view.
 * [switcher] is the today/places control the journey tab puts at the top of the left panel.
 */
@Composable
fun PlacesView(
    places: List<SavedPlace>, parked: Boolean?, busy: Boolean,
    onUpdate: (String, String, (JourneyStop) -> JourneyStop) -> Unit,
    stopMapApp: StopMapApp, switcher: @Composable () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val place = places.firstOrNull { it.stop.id == selectedId } ?: places.firstOrNull()
    val canEdit = parked == true && !busy
    var mapPick by remember(stopMapApp) { mutableStateOf(stopMapApp) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            switcher()
            Note(if (places.isEmpty()) "오늘의 발자취에서 별표한 곳이 여기에 모여요." else "최근에 들른 곳이 위에 있어요.")
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(places, key = { it.stop.id }) { item ->
                    val s = item.stop
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (s.id == place?.stop?.id) Ui.Raised else Color.Transparent)
                            .clickable { selectedId = s.id }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Ui.Star, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label(s), color = Ui.Ink, fontSize = Ui.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Note(listOf(dayAndClock(s.arrivedAt), s.note).filter { it.isNotBlank() }.joinToString(", "), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        Panel(Modifier.weight(1f).fillMaxHeight()) {
            if (place == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Heading("아직 저장한 곳이 없어요")
                    Spacer(Modifier.height(12.dp))
                    Note("오늘의 발자취에서 들른 곳 옆의 별을 누르거나, 달리면서 \"여기 기억해 줘\"라고 말하면 여기에 남아요.")
                }
            } else {
                PlaceDetails(place, canEdit, parked) { change -> onUpdate(place.journeyId, place.stop.id, change) }
            }
        }

        SharePanel(listOf("이 장소 지도"), 0, {}) {
            StopMapShare(place?.stop, mapPick) { mapPick = it }
        }
    }
}

@Composable
private fun PlaceDetails(place: SavedPlace, canEdit: Boolean, parked: Boolean?, onUpdate: ((JourneyStop) -> JourneyStop) -> Unit) {
    val stop = place.stop
    var editing by remember(stop.id) { mutableStateOf(false) }
    var note by remember(stop.id, stop.note) { mutableStateOf(stop.note) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label(stop), color = Ui.Ink, fontSize = Ui.Heading, fontWeight = FontWeight.Bold)
        Note(dayAndClock(stop.arrivedAt) + if (stop.passedBy) ", 지나가며 저장" else "")
        if (stop.name.isNotBlank() && stop.address.isNotBlank()) Note(stop.address)
        if (stop.point == null) Note("위치 정보가 없어 지도는 열 수 없어요.", color = Ui.Warn)
        if (stop.note.isNotBlank() && !editing) Text(stop.note, color = Ui.Ink, fontSize = Ui.Body)
        if (editing) {
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(240) }, enabled = canEdit, label = { Text("이곳에서의 한마디") },
                textStyle = TextStyle(fontSize = Ui.Body), modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) QuietButton("메모 저장", enabled = canEdit) { onUpdate { it.copy(note = note.trim()) }; editing = false }
            QuietButton(if (editing) "메모 닫기" else "메모 고치기", enabled = canEdit) { editing = !editing }
        }
        Spacer(Modifier.weight(1f))
        // Unstarring is the only way off this list, and past days keep nothing else, so it is one deliberate button.
        QuietButton("다시 갈 곳에서 빼기", enabled = canEdit) { onUpdate { it.copy(favorite = false) } }
        Note(if (parked == true) "지난 날의 장소는 별을 빼면 다시 찾을 수 없어요." else "주차(P)하면 고치거나 뺄 수 있어요.", color = if (parked == true) Ui.Muted else Ui.Warn)
    }
}

private fun label(stop: JourneyStop): String = stop.name.ifBlank { stop.address.ifBlank { "이름 없는 곳" } }
private fun dayAndClock(at: Long): String = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(at))
