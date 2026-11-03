package ai.pleos.challenge.arrival

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mockup of the arrival assistant. Recording is automatic and silent; organizing happens on request.
 * - Leaving P / returning to P records a [Segment] (time, odometer, navigation address).
 * - Returning to P only shows a quiet banner.
 * - The mic button takes a command: "운행일지 정리해줘" merges the open segments into one trip, asks the
 *   purpose, saves it, then offers to record the parking spot (floor, then pillar). "주차 어디 했지?" answers
 *   with the last saved spot.
 *
 * Demo: `adb shell am start -n ai.pleos.challenge.arrival/.MainActivity --ez demo_audio true`
 * then drive with the Virtual Console (odometer + gear). Each STT request is fed the next phrase in [DEMO_SCRIPT]
 * (or the comma-separated assets in `--es demo_script ...`).
 */
class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, LOGBOOK, RECORDS }

    /** ASK_* speaks a question, HEAR_* listens for the answer, CHOOSE_* waits for a tap after voice failed. */
    private enum class Step {
        IDLE, HEAR_COMMAND,
        ASK_PURPOSE, HEAR_PURPOSE, CHOOSE_PURPOSE,
        ASK_PARK, HEAR_PARK, CHOOSE_PARK,
        ASK_FLOOR, HEAR_FLOOR, ASK_PILLAR, HEAR_PILLAR, ASK_CONFIRM, HEAR_CONFIRM, REVIEW_SPOT,
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: TripStore
    private lateinit var segmentStore: SegmentStore
    private lateinit var consentStore: ConsentStore
    private lateinit var car: CarSignals
    private lateinit var voice: Voice

    private var screen by mutableStateOf(Screen.HOME)
    private var parked by mutableStateOf<Boolean?>(null)
    private var odometer by mutableStateOf<Float?>(null)
    private var showBanner by mutableStateOf(false)
    private var step by mutableStateOf(Step.IDLE)
    private var question by mutableStateOf("")
    private var partial by mutableStateOf("")
    private var heard by mutableStateOf("")
    private var reply by mutableStateOf("")
    private var draft by mutableStateOf<Trip?>(null)
    private var floor by mutableStateOf<Int?>(null)
    /** Pillar code built from voice or the letter/number buttons, e.g. "AL02". */
    private var pillarCode by mutableStateOf("")
    /** Free text typed with "직접 입력"; when set it is saved as the parking spot as is. */
    private var manualSpot by mutableStateOf("")
    private var showManual by mutableStateOf(false)
    private var notice by mutableStateOf("")
    private var phone by mutableStateOf("")
    private var mapApp by mutableStateOf(MapApp.GOOGLE)
    private var consented by mutableStateOf(false)
    private var declined by mutableStateOf(false)
    private var confirmWithdraw by mutableStateOf(false)
    private var running = false
    private var listenToken = 0
    private val trips = mutableStateListOf<Trip>()
    private val segments = mutableStateListOf<Segment>()

    private val listening get() = step.name.startsWith("HEAR_")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TripStore(this)
        segmentStore = SegmentStore(this)
        trips.addAll(store.load())
        segments.addAll(segmentStore.load())
        phone = store.phone
        mapApp = store.mapApp
        consentStore = ConsentStore(this)
        consented = consentStore.granted
        if (consented) startCollecting()

        setContent { App() }
    }

    /** Nothing is read from the car, microphone or navigation until the user has agreed. */
    private fun startCollecting() {
        if (running) return
        running = true
        val missing = listOf(Manifest.permission.RECORD_AUDIO, FUSED_LOCATION)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)

        car = CarSignals(
            applicationContext,
            onGear = { isParked -> runOnUiThread { onGearChanged(isParked) } },
            onOdometer = { km -> runOnUiThread { onOdometer(km) } },
        )
        voice = Voice(
            applicationContext,
            demoScript = when {
                // e.g. --es demo_script cmd_logbook.pcm,purpose.pcm,yes.pcm,floor.pcm,pillar_al02.pcm
                intent.hasExtra("demo_script") -> intent.getStringExtra("demo_script").orEmpty().split(',').filter { it.isNotBlank() }
                intent.getBooleanExtra("demo_audio", false) -> DEMO_SCRIPT
                else -> emptyList()
            },
            onSpoken = { runOnUiThread { onSpoken() } },
            onListening = {},
            onHeard = { text, isFinal -> runOnUiThread { onHeard(text, isFinal) } },
            onError = { message -> runOnUiThread { onVoiceError(message) } },
        )
        car.start()
        voice.start()
        // The Vehicle Service binds asynchronously; register listeners once it is up.
        handler.postDelayed({ if (running) car.startListeners() }, 2000)
    }

    private fun stopCollecting() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        voice.release()
        car.stop()
    }

    private fun agree() {
        consentStore.granted = true
        consented = true
        declined = false
        startCollecting()
    }

    /** Withdrawal: stop collecting right away and delete everything stored. */
    private fun withdrawConsent() {
        stopCollecting()
        consentStore.granted = false
        store.clearAll()
        trips.clear()
        segments.clear()
        phone = ""
        draft = null
        step = Step.IDLE
        showBanner = false
        parked = null
        odometer = null
        screen = Screen.HOME
        consented = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val located = permissions.indexOf(FUSED_LOCATION).let { it >= 0 && grantResults[it] == PackageManager.PERMISSION_GRANTED }
        if (located && running) car.startLocation()
    }

    override fun onDestroy() {
        stopCollecting()
        super.onDestroy()
    }

    // ---- Silent recording ----

    private fun onGearChanged(isParked: Boolean) {
        val wasParked = parked
        parked = isParked
        val open = segments.indexOfLast { !it.isClosed }
        Log.i(TAG, "gear parked=$isParked odometer=$odometer open=$open")
        if (!isParked && open < 0) {
            segments.add(Segment(System.currentTimeMillis(), odometer ?: 0f))
            saveSegments()
            val index = segments.lastIndex
            car.requestAddress { address -> runOnUiThread { updateSegment(index) { it.copy(departAddress = address) } } }
        } else if (isParked && wasParked != true && open >= 0) {
            val fix = car.lastFix
            updateSegment(open) {
                it.copy(arriveAt = System.currentTimeMillis(), arriveKm = odometer ?: it.departKm, arriveLat = fix?.latitude, arriveLng = fix?.longitude)
            }
            car.requestAddress { address -> runOnUiThread { updateSegment(open) { it.copy(arriveAddress = address) } } }
            showBanner = step == Step.IDLE
        }
        if (!isParked && wasParked == true) stopLogbookWhileDriving()
    }

    /** The logbook flow (questions, pickers, keyboard) is for a parked car only; driving cancels it. */
    private fun stopLogbookWhileDriving() {
        if (step == Step.IDLE || step == Step.HEAR_COMMAND) return
        listenToken++
        voice.stopListening()
        draft = null
        screen = Screen.HOME
        showBanner = false
        reply = "주행을 시작해서 정리를 멈췄어요. 정차 후 다시 불러 주세요."
        say(Step.IDLE, reply)
    }

    /** The first gear event can arrive before the odometer; fill in a departure recorded without it. */
    private fun onOdometer(km: Float) {
        odometer = km
        val open = segments.indexOfLast { !it.isClosed }
        if (open >= 0 && segments[open].departKm <= 0f) updateSegment(open) { it.copy(departKm = km) }
    }

    private fun updateSegment(index: Int, change: (Segment) -> Segment) {
        if (index !in segments.indices) return
        segments[index] = change(segments[index])
        saveSegments()
    }

    private fun saveSegments() = segmentStore.save(segments)

    // ---- Voice dialog ----

    /** Speaks [prompt]; ASK_* steps then listen. Falls back to a timer because TTS onDone is sometimes not delivered. */
    private fun say(next: Step, prompt: String) {
        step = next
        question = prompt
        val token = ++listenToken
        voice.speak(prompt)
        if (next.name.startsWith("ASK_")) {
            // Fallback only: TTS onDone is sometimes not delivered. Listening early would record our own prompt.
            handler.postDelayed({ if (token == listenToken && step == next) onSpoken() }, 4_000L + prompt.length * 250L)
        }
    }

    private fun onSpoken() {
        // Let the speaker tail die out before opening the microphone.
        val token = listenToken
        handler.postDelayed({ if (token == listenToken) startAnswer() }, 400)
    }

    private fun startAnswer() {
        when (step) {
            Step.ASK_PURPOSE -> listenFor(Step.HEAR_PURPOSE)
            Step.ASK_PARK -> listenFor(Step.HEAR_PARK)
            Step.ASK_FLOOR -> listenFor(Step.HEAR_FLOOR)
            Step.ASK_PILLAR -> listenFor(Step.HEAR_PILLAR)
            Step.ASK_CONFIRM -> listenFor(Step.HEAR_CONFIRM)
            else -> Unit
        }
    }

    private fun listenFor(next: Step) {
        partial = ""
        step = next
        val token = ++listenToken
        voice.listen()
        // A silent microphone never produces a final result; move on so the on-screen buttons take over.
        handler.postDelayed({
            if (token == listenToken && step == next) {
                voice.stopListening()
                accept(partial)
            }
        }, LISTEN_TIMEOUT_MS)
    }

    /** Mic button on the home screen: listen for a command. */
    private fun callAssistant() {
        showBanner = false
        reply = ""
        heard = ""
        notice = ""
        listenFor(Step.HEAR_COMMAND)
    }

    private fun onHeard(text: String, isFinal: Boolean) {
        if (!listening) return
        partial = text
        if (isFinal) accept(text)
    }

    /** The on-device engine sometimes ends with an error after partial results; use what was heard. */
    private fun onVoiceError(message: String) {
        // The engine also reports an error after a successful final result; only surface it while listening.
        if (!listening) return
        notice = message
        accept(partial)
    }

    /** True when STT picked up the app's own question from the speaker instead of the driver. */
    private fun isEcho(text: String): Boolean {
        val said = text.replace(" ", "")
        val asked = question.replace(" ", "")
        return said.length >= 4 && (asked.contains(said) || listOf("할까요", "말씀해", "이에요?", "인가요").any(said::contains))
    }

    private var echoRetried = false

    private fun accept(text: String) {
        if (listening && step != Step.HEAR_COMMAND && isEcho(text) && !echoRetried) {
            Log.i(TAG, "$step ignored echo \"$text\"")
            echoRetried = true
            listenFor(step)
            return
        }
        echoRetried = false
        listenToken++
        heard = text
        Log.i(TAG, "$step heard \"$text\"")
        when (step) {
            Step.HEAR_COMMAND -> when (Commands.parseIntent(text)) {
                Commands.Intent.LOGBOOK -> if (parked == false) {
                    reply = "운행일지는 정차한 뒤에 정리할 수 있어요."
                    say(Step.IDLE, reply)
                } else {
                    startLogbook()
                }
                Commands.Intent.WHERE_PARKED -> answerWhereParked()
                Commands.Intent.UNKNOWN -> {
                    reply = "운행일지 정리, 또는 주차 위치를 물어보세요."
                    say(Step.IDLE, reply)
                }
            }
            Step.HEAR_PURPOSE -> {
                val purpose = Commands.parsePurpose(text)
                Log.i(TAG, "purpose -> $purpose")
                if (purpose != null) savePurpose(purpose) else say(Step.CHOOSE_PURPOSE, "화면에서 운행 구분을 골라 주세요.")
            }
            Step.HEAR_PARK -> when (Commands.parseYesNo(text)) {
                true -> askFloor()
                false -> finishLogbook("알겠어요. 운행일지만 저장했어요.")
                null -> say(Step.CHOOSE_PARK, "주차 위치를 기록할지 화면에서 골라 주세요.")
            }
            Step.HEAR_FLOOR -> {
                floor = ParkingText.parseFloor(text)
                if (floor != null) notice = ""
                // Some people answer with the whole spot at once ("지하 5층 A4").
                val pillar = ParkingText.parseSentence(text).second?.takeIf { floor != null }
                Log.i(TAG, "floor -> $floor, pillar=$pillar")
                if (pillar != null) {
                    setPillar(pillar)
                    reviewSpot()
                } else {
                    say(Step.ASK_PILLAR, "기둥 번호를 말씀해 주세요.")
                }
            }
            Step.HEAR_PILLAR -> {
                val pillar = ParkingText.parsePillar(text)
                Log.i(TAG, "pillar -> $pillar")
                pillar?.let(::setPillar)
                if (pillar != null) notice = ""
                reviewSpot()
            }
            Step.HEAR_CONFIRM -> when (Commands.parseYesNo(text)) {
                true -> saveSpot(parkingText())
                false -> say(Step.REVIEW_SPOT, "화면에서 고친 뒤 저장을 눌러 주세요.")
                null -> say(Step.REVIEW_SPOT, "맞으면 저장을 눌러 주세요.")
            }
            else -> Unit
        }
    }

    // ---- Logbook flow ----

    private fun startLogbook() {
        showBanner = false
        val trip = Logbook.merge(segments)
        if (trip == null) {
            // Only short moves (or nothing) since the last entry: drop them so they never become a trip.
            segments.removeAll { it.isClosed }
            saveSegments()
            reply = "정리할 운행이 없어요."
            screen = Screen.HOME
            say(Step.IDLE, reply)
            return
        }
        draft = trip
        clearSpot()
        screen = Screen.LOGBOOK
        val start = SimpleDateFormat("H시 m분", Locale.KOREA).format(Date(trip.startedAt))
        val place = trip.endAddress.ifBlank { "목적지" }
        say(
            Step.ASK_PURPOSE,
            "${start}에 출발해서 ${place}에 도착했어요. ${spokenKm(trip.startKm)}에서 ${spokenKm(trip.endKm)}까지, " +
                "총 ${spokenDistance(trip.distanceKm)}예요. 업무 운행인가요, 출퇴근인가요?",
        )
    }

    /** Saves the merged trip to the logbook and clears the segments it came from. */
    private fun savePurpose(purpose: Purpose) {
        val trip = draft?.copy(purpose = purpose) ?: return
        draft = trip
        trips.add(0, trip)
        store.save(trips)
        segments.removeAll { it.isClosed && it.departAt >= trip.startedAt && it.arriveAt!! <= trip.endedAt }
        saveSegments()
        Log.i(TAG, "logbook saved ${trip.distanceKm}km ${purpose.label}")
        say(Step.ASK_PARK, "${purpose.short} 운행으로 저장했어요. 주차 위치도 기록할까요?")
    }

    private fun askFloor() {
        clearSpot()
        notice = ""
        say(Step.ASK_FLOOR, "몇 층이에요?")
    }

    private fun clearSpot() {
        floor = null
        pillarCode = ""
        manualSpot = ""
        showManual = false
    }

    private fun setPillar(code: String) {
        pillarCode = code
    }

    /** Reads the spot back and asks to save it by voice; the save button stays available. */
    private fun reviewSpot() {
        val spot = parkingText()
        if (spot.isBlank()) say(Step.REVIEW_SPOT, "화면에서 주차 위치를 골라 주세요.")
        else say(Step.ASK_CONFIRM, "$spot, 저장할까요?")
    }

    /** A tap on the picker means the driver is fixing the spot by hand: stop the voice confirmation. */
    private fun onSpotEdited() {
        listenToken++
        voice.stopListening()
        step = Step.REVIEW_SPOT
        question = "맞으면 저장을 눌러 주세요."
    }

    private fun parkingText(): String =
        manualSpot.trim().ifBlank { ParkingText.format(floor, pillarCode) }

    private fun saveSpot(spot: String) {
        val trip = draft ?: return
        val index = trips.indexOfFirst { it.startedAt == trip.startedAt && it.endedAt == trip.endedAt }
        if (index >= 0) {
            trips[index] = trips[index].copy(parking = spot)
            store.save(trips)
        }
        if (phone.isNotBlank() && index >= 0) Log.i(TAG, "qr ${trips[index].smsQrText(phone, mapApp)}")
        finishLogbook("${spot}로 저장했어요.")
    }

    private fun finishLogbook(message: String) {
        listenToken++
        voice.stopListening()
        draft = null
        screen = Screen.RECORDS
        say(Step.IDLE, message)
    }

    private fun answerWhereParked() {
        val last = trips.firstOrNull { it.parking.isNotBlank() }
        reply = if (last == null) {
            "아직 기록된 주차 위치가 없어요."
        } else {
            val time = SimpleDateFormat("M월 d일 H시 m분", Locale.KOREA).format(Date(last.endedAt))
            val place = last.endAddress.takeIf { it.isNotBlank() }?.let { "$it, " } ?: ""
            "${last.parking}예요. ${place}${time}에 주차했어요."
        }
        screen = Screen.HOME
        say(Step.IDLE, reply)
    }

    // ---- UI ----

    @Composable
    private fun App() {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Panel)) {
            Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                if (!consented) {
                    ConsentScreen()
                    return@Surface
                }
                Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    TopBar()
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.weight(1f)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen()
                            Screen.LOGBOOK -> LogbookScreen()
                            Screen.RECORDS -> RecordsScreen()
                        }
                    }
                    if (confirmWithdraw) WithdrawBanner() else if (showBanner) ArrivalBanner()
                }
            }
        }
    }

    @Composable
    private fun ConsentScreen() {
        Column(
            Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("도착노트", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("시작하기 전에 개인정보 수집·이용에 동의해 주세요", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Card(Modifier.fillMaxWidth()) {
                Label("수집하는 정보와 쓰는 곳")
                Consent.items.forEach { item ->
                    Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    Body(item.detail)
                }
                Spacer(Modifier.height(16.dp))
                Label("보관")
                Body(Consent.STORAGE)
                Spacer(Modifier.height(16.dp))
                Label("동의 철회")
                Body(Consent.WITHDRAW)
            }
            if (declined) Body("동의하지 않으면 운행 기록과 음성 기능을 쓸 수 없어요. 언제든 다시 동의할 수 있어요.", color = Warn)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { agree() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                ) { Text("동의하고 시작", fontSize = 20.sp) }
                OutlinedButton(onClick = { declined = true }) { Text("동의하지 않음", fontSize = 20.sp) }
            }
        }
    }

    @Composable
    private fun WithdrawBanner() {
        Row(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("동의를 철회하면 수집을 멈추고 저장된 운행 기록·주차 위치·번호를 모두 지워요.", fontSize = 18.sp, color = Warn, modifier = Modifier.weight(1f))
            Button(
                onClick = { confirmWithdraw = false; withdrawConsent() },
                colors = ButtonDefaults.buttonColors(containerColor = Warn, contentColor = Ink),
            ) { Text("철회하고 지우기", fontSize = 18.sp) }
            TextButton(onClick = { confirmWithdraw = false }) { Text("취소", fontSize = 18.sp) }
        }
    }

    @Composable
    private fun TopBar() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("도착노트", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(24.dp))
            GearBadge()
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { screen = Screen.HOME }) { Text("홈", fontSize = 20.sp) }
            TextButton(onClick = { screen = Screen.RECORDS }) { Text("운행기록부", fontSize = 20.sp) }
            TextButton(onClick = { confirmWithdraw = true }) { Text("동의 철회", fontSize = 16.sp, color = Muted) }
        }
    }

    @Composable
    private fun GearBadge() {
        val label = when (parked) {
            null -> "기어 확인 중"
            true -> "P · 주차"
            false -> "주행 중 · 기록 중"
        }
        Text(
            label,
            fontSize = 18.sp,
            color = if (parked == false) Ink else Color.White,
            modifier = Modifier
                .background(if (parked == false) Accent else Panel, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }

    /** Quiet prompt after returning to P; ignoring it just leaves the segments for later. */
    @Composable
    private fun ArrivalBanner() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).background(Line, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("운행일지 정리할까요?", fontSize = 22.sp, color = Color.White, modifier = Modifier.weight(1f))
            Button(
                onClick = { startLogbook() },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
            ) { Text("🎤 정리하기", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { showBanner = false }) { Text("닫기", fontSize = 20.sp) }
        }
    }

    @Composable
    private fun HomeScreen() {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Card(Modifier.weight(1f)) {
                    Label("계기판 누적 거리")
                    Big(odometer?.let(::km) ?: "읽는 중")
                    Spacer(Modifier.height(16.dp))
                    val pending = Logbook.merge(segments)
                    val closed = segments.count { it.isClosed }
                    Body(
                        when {
                            pending != null -> "정리 안 한 운행 ${closed}구간 · ${km(pending.distanceKm)}"
                            segments.any { !it.isClosed } -> "운행을 기록하고 있어요."
                            else -> "정리할 운행이 없어요. 기어를 D로 바꾸면 자동으로 기록해요."
                        },
                    )
                }
                Card(Modifier.weight(1f)) {
                    Label("마지막 주차 위치")
                    val last = trips.firstOrNull { it.parking.isNotBlank() }
                    Big(last?.parking ?: "아직 없어요")
                    Spacer(Modifier.height(16.dp))
                    Body(last?.let { "${formatDate(it.endedAt)} · ${it.endAddress.ifBlank { "주소 없음" }}" } ?: "운행일지를 정리할 때 함께 기록할 수 있어요.")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { callAssistant() },
                        enabled = step == Step.IDLE,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                        modifier = Modifier.size(width = 200.dp, height = 96.dp),
                    ) { Text("🎤 말하기", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        when {
                            step == Step.HEAR_COMMAND -> Big(partial.ifBlank { "듣고 있어요…" })
                            reply.isNotBlank() -> Big(reply)
                            else -> Body("예: \"운행일지 정리해줘\", \"주차 어디 했지?\"", color = Color.White)
                        }
                        if (heard.isNotBlank() && step != Step.HEAR_COMMAND) Body("들은 말: \"$heard\"")
                        if (notice.isNotBlank() && step == Step.HEAR_COMMAND) Body(notice, color = Warn)
                    }
                }
            }
        }
    }

    @Composable
    private fun LogbookScreen() {
        val trip = draft ?: return HomeScreen()
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.weight(0.8f)) {
                Label("정리할 운행")
                Big("총 ${km(trip.distanceKm)}")
                Spacer(Modifier.height(12.dp))
                Body("${formatDate(trip.startedAt)} 출발 · ${trip.startAddress.ifBlank { "주소 없음" }}")
                Body("${formatDate(trip.endedAt)} 도착 · ${trip.endAddress.ifBlank { "주소 없음" }}")
                Body("${km(trip.startKm)}  →  ${km(trip.endKm)}")
                Spacer(Modifier.height(20.dp))
                Label("운행 구분")
                val purposeOpen = step in setOf(Step.ASK_PURPOSE, Step.HEAR_PURPOSE, Step.CHOOSE_PURPOSE)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Purpose.entries.forEach { purpose ->
                        FilterChip(
                            selected = !purposeOpen && trip.purpose == purpose,
                            enabled = purposeOpen,
                            onClick = { savePurpose(purpose) },
                            label = { Text(purpose.label, fontSize = 18.sp) },
                        )
                    }
                }
            }
            Card(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) {
                Label("도착노트")
                Text(question, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (listening) Body("듣고 있어요… ${partial}", color = Accent)
                else if (heard.isNotBlank()) Body("들은 말: \"$heard\"")
                if (notice.isNotBlank()) Body(notice, color = Warn)
                Spacer(Modifier.height(16.dp))

                when (step) {
                    Step.ASK_PARK, Step.HEAR_PARK, Step.CHOOSE_PARK -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { listenToken++; voice.stopListening(); askFloor() },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                        ) { Text("주차 위치 기록", fontSize = 20.sp) }
                        OutlinedButton(onClick = { finishLogbook("알겠어요. 운행일지만 저장했어요.") }) { Text("안 할게요", fontSize = 20.sp) }
                    }
                    Step.ASK_FLOOR, Step.HEAR_FLOOR, Step.ASK_PILLAR, Step.HEAR_PILLAR,
                    Step.ASK_CONFIRM, Step.HEAR_CONFIRM, Step.REVIEW_SPOT -> SpotPicker()
                    else -> Unit
                }
            }
        }
    }

    @Composable
    private fun SpotPicker() {
        val spot = parkingText()
        val reviewing = step in setOf(Step.ASK_CONFIRM, Step.HEAR_CONFIRM, Step.REVIEW_SPOT)
        Big(spot.ifBlank { "주차 위치" })
        Spacer(Modifier.height(12.dp))
        Label("층")
        Picker(FLOORS.map { if (it < 0) "B${-it}" else "${it}F" }, FLOORS.indexOf(floor)) { floor = FLOORS[it]; onSpotEdited() }
        Label("기둥 번호  ${pillarCode.ifBlank { "—" }}")
        // Each tap appends, so codes such as AL02 or B-15 can be built; leading zeros are kept.
        Picker(LETTERS.map(Char::toString), -1) { pillarCode = (pillarCode + LETTERS[it]).take(8); onSpotEdited() }
        Picker((1..9).map(Int::toString) + "0" + "-" + "⌫" + "전체 지우기", -1) { index ->
            Log.i(TAG, "pillar key $index (was $pillarCode)")
            pillarCode = when (index) {
                9 -> (pillarCode + "0").take(8)
                10 -> (pillarCode + "-").take(8)
                11 -> pillarCode.dropLast(1)
                12 -> ""
                else -> (pillarCode + (index + 1)).take(8)
            }
            onSpotEdited()
        }
        TextButton(onClick = { showManual = !showManual }) { Text(if (showManual) "직접 입력 닫기" else "직접 입력", fontSize = 20.sp) }
        if (showManual) {
            OutlinedTextField(
                value = manualSpot,
                onValueChange = { manualSpot = it.take(40); onSpotEdited() },
                label = { Text("주차 위치 직접 입력 (예: 지하2층 AL02 동편)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { saveSpot(spot) },
                enabled = reviewing && spot.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
            ) { Text("저장", fontSize = 20.sp) }
            OutlinedButton(onClick = { askFloor() }, enabled = reviewing) { Text("다시 묻기", fontSize = 20.sp) }
            TextButton(onClick = { finishLogbook("알겠어요. 운행일지만 저장했어요.") }) { Text("건너뛰기", fontSize = 20.sp) }
        }
    }

    /** A wrapping row of chips; tapping one reports its index. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Picker(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onPick(index) },
                    label = { Text(label, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) },
                )
            }
        }
    }

    @Composable
    private fun RecordsScreen() {
        var selected by remember { mutableStateOf(0) }
        var monthly by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.weight(1.6f)) {
                Label("업무용승용차 운행기록부")
                Row(Modifier.padding(vertical = 8.dp)) {
                    Cell("사용일자", 1.3f, header = true)
                    Cell("주행 전", 1f, header = true)
                    Cell("주행 후", 1f, header = true)
                    Cell("주행거리", 0.9f, header = true)
                    Cell("구분", 0.8f, header = true)
                    Cell("주차 위치", 1.2f, header = true)
                }
                HorizontalDivider(color = Line)
                if (trips.isEmpty()) Body("아직 기록이 없어요.")
                LazyColumn {
                    items(trips.size) { index ->
                        val trip = trips[index]
                        Row(
                            Modifier
                                .background(if (index == selected) Line else Color.Transparent)
                                .padding(vertical = 10.dp),
                        ) {
                            TextButton(onClick = { selected = index }, modifier = Modifier.weight(1.3f)) {
                                Text(formatDate(trip.endedAt), fontSize = 17.sp, color = Color.White, modifier = Modifier.fillMaxWidth())
                            }
                            Cell(km(trip.startKm), 1f)
                            Cell(km(trip.endKm), 1f)
                            Cell(km(trip.distanceKm), 0.9f)
                            Cell(trip.purpose.label, 0.8f)
                            Cell(trip.parking.ifBlank { "-" }, 1.2f)
                        }
                    }
                }
            }
            Card(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Label("휴대폰으로 받기")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    FilterChip(selected = !monthly, onClick = { monthly = false }, label = { Text("이번 운행 · 문자", fontSize = 18.sp) })
                    FilterChip(selected = monthly, onClick = { monthly = true }, label = { Text("월간 운행기록부", fontSize = 18.sp) })
                }
                if (monthly) MonthlyShare() else TripShare(trips.getOrNull(selected))
            }
        }
    }

    /** One month as a link QR: the phone opens the NTS table and can save it as a spreadsheet. */
    @Composable
    private fun MonthlyShare() {
        val months = LogbookLink.months(trips)
        if (months.isEmpty()) return Body("기록이 생기면 QR 코드가 나타나요.")
        var index by remember { mutableStateOf(0) }
        val month = months[index.coerceIn(months.indices)]
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { index++ }, enabled = index < months.lastIndex) { Text("◀", fontSize = 20.sp) }
            Text("${month.year}년 ${month.monthValue}월", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = { index-- }, enabled = index > 0) { Text("▶", fontSize = 20.sp) }
        }
        val inMonth = LogbookLink.tripsIn(trips, month)
        val sum = LogbookLink.summary(inMonth)
        Body("${inMonth.size}건 · 총 ${km(sum.totalKm)} · 업무용 ${km(sum.workKm)}", color = Color.White)
        Body(String.format(Locale.KOREA, "업무사용비율 %.1f%%", sum.ratio), color = Accent)
        Spacer(Modifier.height(12.dp))
        val url = remember(month, inMonth) {
            // e.g. --es viewer_url http://192.168.0.10:8765/ to try viewer/ from a laptop before it is published
            LogbookLink.url(trips, month, base = intent.getStringExtra("viewer_url") ?: LogbookLink.BASE_URL)
                .also { Log.i(TAG, "monthly ${it.length} chars $it") }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(qrBitmap(url, 640).asImageBitmap(), contentDescription = "월간 운행기록부 QR 코드", modifier = Modifier.size(300.dp))
        }
        Spacer(Modifier.height(12.dp))
        Body("휴대폰 카메라로 스캔하면 국세청 운행기록부 양식으로 열려요. 엑셀로 받거나 인쇄할 수 있어요.")
    }

    @Composable
    private fun TripShare(trip: Trip?) {
        Column {
            OutlinedTextField(
                value = phone,
                onValueChange = { value ->
                    phone = value.filter { it.isDigit() || it == '-' }.take(13)
                    store.phone = phone
                },
                label = { Text(if (parked == false) "내 휴대폰 번호 (정차 중에만 입력)" else "내 휴대폰 번호") },
                // Typing is for a parked car only (driver distraction guideline).
                enabled = parked != false,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            when {
                trip == null -> Body("기록이 생기면 QR 코드가 나타나요.")
                phone.count(Char::isDigit) < 10 -> Body("내 번호를 입력하면 QR로 문자를 보낼 수 있어요.")
                else -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(qrBitmap(trip.smsQrText(phone, mapApp)).asImageBitmap(), contentDescription = "문자 보내기 QR 코드", modifier = Modifier.size(240.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Body("휴대폰 카메라로 스캔하면 문자 앱이 열려요. 보내면 내 문자함에 저장돼요.")
                }
            }
            if (trip?.mapLink() != null) {
                Spacer(Modifier.height(12.dp))
                Label("지도 링크")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapApp.entries.forEach { app ->
                        FilterChip(
                            selected = mapApp == app,
                            onClick = { mapApp = app; store.mapApp = app },
                            label = { Text(app.label, fontSize = 18.sp) },
                        )
                    }
                }
            }
            if (trip != null) {
                Spacer(Modifier.height(12.dp))
                Label("보낼 문자")
                Text(
                    trip.smsBody(mapApp),
                    fontSize = 17.sp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().background(Line, RoundedCornerShape(8.dp)).padding(12.dp),
                )
            }
        }
    }

    @Composable
    private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
        Column(modifier.background(Panel, RoundedCornerShape(16.dp)).padding(28.dp)) { content() }
    }

    @Composable
    private fun Label(text: String) = Text(text, fontSize = 18.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))

    @Composable
    private fun Big(text: String) = Text(text, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)

    @Composable
    private fun Body(text: String, color: Color = Muted) = Text(text, fontSize = 20.sp, color = color, modifier = Modifier.padding(top = 4.dp))

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, header: Boolean = false) = Text(
        text,
        fontSize = 17.sp,
        color = if (header) Muted else Color.White,
        textAlign = TextAlign.Start,
        modifier = Modifier.weight(weight).padding(horizontal = 8.dp, vertical = 12.dp),
    )

    private companion object {
        const val TAG = "ArrivalNote"
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val LISTEN_TIMEOUT_MS = 10_000L
        val FLOORS = listOf(-6, -5, -4, -3, -2, -1, 1, 2, 3)
        val LETTERS = ('A'..'Z').toList()

        /** Phrases fed to STT in order when launched with `--ez demo_audio true` (the emulator mic is silent). */
        val DEMO_SCRIPT = listOf(
            "cmd_logbook.pcm", "purpose.pcm", "yes.pcm", "floor.pcm", "pillar.pcm", "yes.pcm",
            "cmd_where.pcm", "cmd_logbook.pcm",
        )

        val Ink = Color(0xFF0F1419)
        val Panel = Color(0xFF1A222C)
        val Line = Color(0xFF2A3542)
        val Muted = Color(0xFF9AA7B6)
        val Accent = Color(0xFF5EC8B8)
        val Warn = Color(0xFFF0B45C)

        /** "12,340킬로미터" reads better through TTS than "12,340.0km". */
        fun spokenKm(value: Float) = String.format(Locale.KOREA, "%,d킬로미터", value.toInt())

        fun spokenDistance(value: Float) = String.format(Locale.KOREA, "%.1f킬로미터", value)
    }
}
