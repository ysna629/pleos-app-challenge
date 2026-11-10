package ai.pleos.challenge.arrival

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mockup of the arrival assistant. Recording is automatic and silent; organizing happens on request.
 * - Leaving P / returning to P records a [Segment] (time, odometer, navigation address).
 * - Returning to P only shows a quiet banner.
 * - The mic button takes a command: "운행일지 정리해줘" merges the open segments into one trip, saves it as
 *   business use (changeable on screen), then asks where the car is parked. "주차 어디 했지?" answers with the
 *   last saved spot.
 * - Parked behind another app, it asks aloud instead: "도착하셨나요?", "운행을 기록할까요?", then the parking spot.
 *
 * Demo: `adb shell am start -n ai.pleos.challenge.arrival/.MainActivity --ez demo_audio true`
 * then drive with the Virtual Console (odometer + gear). Each STT request is fed the next phrase in [DEMO_SCRIPT]
 * (or the comma-separated assets in `--es demo_script ...`).
 */
class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, LOGBOOK, RECORDS, JOURNEY, SETTINGS }

    /** ASK_* speaks a question, HEAR_* listens for the answer, CHOOSE_* waits for a tap after voice failed. */
    private enum class Step {
        IDLE, HEAR_COMMAND, ASK_JOURNEY_NOTE, HEAR_JOURNEY_NOTE,
        ASK_PARKED, HEAR_PARKED, ASK_ARRIVAL, HEAR_ARRIVAL,
        ASK_FLOOR, HEAR_FLOOR, ASK_PILLAR, HEAR_PILLAR, ASK_SPOT_NOTE, HEAR_SPOT_NOTE, ASK_CONFIRM, HEAR_CONFIRM, REVIEW_SPOT,
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: TripStore
    private lateinit var segmentStore: SegmentStore
    private lateinit var consentStore: ConsentStore
    private lateinit var journeyStore: JourneyStore
    private lateinit var car: CarSignals
    private lateinit var voice: Voice

    private var screen by mutableStateOf(Screen.HOME)
    /** Where the settings screen was opened from, so tapping the gear again goes back there. */
    private var beforeSettings = Screen.HOME
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
    private var profile by mutableStateOf(LogbookProfile())
    private var consented by mutableStateOf(false)
    private var declined by mutableStateOf(false)
    private var confirmWithdraw by mutableStateOf(false)
    private var running = false
    /** False while another app covers this one; recording goes on, prompts go to a notification. */
    private var visible = false
    /** Unorganized distance when the arrival question was last asked; 0 once those segments are saved or dropped. */
    private var askedAtKm = 0f
    private var collectionGeneration = 0
    private var journeyShareRequest by mutableStateOf<JourneyShareMode?>(null)
    private var journeyPreview by mutableStateOf(false)
    private var stopMapApp by mutableStateOf(StopMapApp.KAKAO)
    private var drivingRestricted by mutableStateOf(false)
    private var drivingStatus by mutableStateOf("")
    private lateinit var drivingRestriction: DrivingRestriction
    private var journeyNoteTarget: Pair<String, String>? = null
    private var journeyFeedback by mutableStateOf("")
    private val journeys = mutableStateListOf<Journey>()
    private var listenToken = 0
    private val trips = mutableStateListOf<Trip>()
    private val segments = mutableStateListOf<Segment>()
    private val parkingNotes = mutableStateListOf<ParkingNote>()
    /** The spot flow is running on its own ("주차 위치 기록해줘"), not as the last step of organizing a trip. */
    private var parkOnly by mutableStateOf(false)
    private var parkAddress by mutableStateOf("")
    private var exportMessage by mutableStateOf("")
    private var pendingJourneyExport: Pair<JourneyExport.Snapshot, Int>? = null
    private var journeyExportJob: Job? = null
    private val saveJourneyDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val pending = pendingJourneyExport
        pendingJourneyExport = null
        if (pending == null) return@registerForActivityResult
        if (uri == null) {
            if (running && consented && pending.second == collectionGeneration) exportMessage = "파일 저장을 취소했어요. 차량에서 저장창을 사용할 수 없다면 휴대폰 QR을 이용해 주세요."
            return@registerForActivityResult
        }
        if (!running || !consented || parked != true || pending.second != collectionGeneration) {
            exportMessage = "P 상태에서 다시 내보내 주세요."
            return@registerForActivityResult
        }
        journeyExportJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = pending.first.csv().toByteArray(Charsets.UTF_8)
                    currentCoroutineContext().ensureActive()
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: error("저장 위치를 열 수 없어요.")
                    stream.use {
                        var offset = 0
                        while (offset < bytes.size) {
                            currentCoroutineContext().ensureActive()
                            val count = minOf(4096, bytes.size - offset)
                            it.write(bytes, offset, count)
                            offset += count
                        }
                    }
                }
                if (running && consented && pending.second == collectionGeneration) exportMessage = "CSV 파일을 저장했어요."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                if (running && consented && pending.second == collectionGeneration) exportMessage = "파일을 저장하지 못했어요. 다른 위치를 선택하거나 휴대폰 QR을 이용해 주세요."
            }
        }
    }

    private val listening get() = step.name.startsWith("HEAR_")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TripStore(this)
        segmentStore = SegmentStore(this)
        trips.addAll(store.load())
        segments.addAll(segmentStore.load())
        parkingNotes.addAll(store.loadParking())
        phone = store.phone
        profile = store.profile
        mapApp = store.mapApp
        consentStore = ConsentStore(this)
        journeyStore = JourneyStore(this)
        journeys.addAll(JourneyDays.keepStarred(journeyStore.load().map { it.resume() }))
        stopMapApp = journeyStore.stopMapApp
        journeyPreview = intent.getBooleanExtra("journey_demo", false)
        if (journeyPreview) screen = Screen.JOURNEY
        consented = consentStore.granted
        if (consented) startCollecting()

        drivingRestriction = DrivingRestriction(applicationContext) { value ->
            Log.i(TAG, "driving restricted=$value")
            runOnUiThread { drivingRestricted = value }
        }
        drivingRestriction.start()

        setContent { App() }
    }

    /** Nothing is read from the car, microphone or navigation until the user has agreed. */
    private fun startCollecting() {
        if (running) return
        running = true
        val generation = ++collectionGeneration
        val missing = listOfNotNull(
            Manifest.permission.RECORD_AUDIO, FUSED_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= 33 },
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)

        car = CarSignals(
            applicationContext,
            onGear = { isParked -> runOnUiThread { if (running && generation == collectionGeneration) onGearChanged(isParked) } },
            onOdometer = { km -> runOnUiThread { if (running && generation == collectionGeneration) onOdometer(km) } },
            onLocation = { point -> runOnUiThread {
                if (running && generation == collectionGeneration && point.isFresh(System.currentTimeMillis())) {
                    parked?.let { captureJourneyGear(it, System.currentTimeMillis()) }
                    updateActiveJourney { it.recordLocation(point) }
                }
            } },
        )
        voice = Voice(
            applicationContext,
            demoScript = when {
                // e.g. --es demo_script cmd_logbook.pcm,purpose.pcm,yes.pcm,floor.pcm,pillar_al02.pcm
                intent.hasExtra("demo_script") -> intent.getStringExtra("demo_script").orEmpty().split(',').filter { it.isNotBlank() }
                intent.getBooleanExtra("demo_audio", false) -> DEMO_SCRIPT
                else -> emptyList()
            },
            onSpoken = { runOnUiThread { if (running && generation == collectionGeneration) onSpoken() } },
            onListening = {},
            onHeard = { text, isFinal -> runOnUiThread { if (running && generation == collectionGeneration) onHeard(text, isFinal) } },
            onError = { message -> runOnUiThread { if (running && generation == collectionGeneration) onVoiceError(message) } },
        )
        car.start()
        voice.start()
        // Navigation is usually in front while driving; a foreground service keeps the callbacks coming.
        runCatching { startForegroundService(Intent(this, RecordingService::class.java)) }
            .onFailure { Log.w(TAG, "recording service not started", it) }
        // The Vehicle Service binds asynchronously; register listeners once it is up.
        handler.postDelayed({ if (running && generation == collectionGeneration) car.startListeners() }, 2000)
    }

    private fun stopCollecting() {
        if (!running) return
        running = false
        collectionGeneration++
        listenToken++
        pendingJourneyExport = null
        journeyExportJob?.cancel()
        // Persist a break rather than inventing movement or visit duration while the activity is absent.
        val interrupted = journeys.map { it.resume() }
        journeys.clear()
        journeys.addAll(interrupted)
        journeyStore.save(journeys)
        handler.removeCallbacksAndMessages(null)
        voice.release()
        car.stop()
        stopService(Intent(this, RecordingService::class.java))
        Notices.cancelArrival(this)
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
        parkingNotes.clear()
        parkOnly = false
        journeys.clear()
        journeyNoteTarget = null
        journeyFeedback = ""
        journeyShareRequest = null
        exportMessage = ""
        phone = ""
        profile = LogbookProfile()
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

    override fun onStart() {
        super.onStart()
        visible = true
        Notices.cancelArrival(this)
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    /** A tap on the arrival notification: open straight into what was chosen. The launch extras stay on [getIntent]. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.getStringExtra(Notices.EXTRA_ACTION) ?: return
        Log.i(TAG, "notice action $action step=$step parked=$parked")
        if (!running || !consented || step != Step.IDLE) return
        when (action) {
            Notices.ACTION_LOGBOOK -> if (parked == true) startLogbook()
            Notices.ACTION_PARK -> startParkOnly()
            Notices.ACTION_SPEAK -> callAssistant()
        }
    }

    override fun onDestroy() {
        drivingRestriction.stop()
        stopCollecting()
        super.onDestroy()
    }

    // ---- Silent recording ----

    private fun onGearChanged(isParked: Boolean?) {
        val wasParked = parked
        parked = isParked
        if (wasParked != isParked) drivingStatus = ""
        if (isParked != true) {
            pendingJourneyExport = null
            journeyShareRequest = null
            journeyExportJob?.cancel()
        }
        if (isParked == null) {
            showBanner = false
            stopLogbookWhileDriving()
            return
        }
        captureJourneyGear(isParked, System.currentTimeMillis())
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
            // Another app is in front: say so there instead of waiting for the driver to come back.
            val pending = Logbook.merge(segments)
            // Coming to the front by itself is not an option: the system blocks it (BAL_BLOCK) even with the service running.
            // Ask only after a real drive, and again only after another one, so shuffling in a car park stays quiet.
            if (!visible && pending != null && pending.distanceKm - askedAtKm >= ASK_MIN_KM) {
                askedAtKm = pending.distanceKm
                Notices.showArrival(this, km(pending.distanceKm))
                // The driver cannot see the banner behind the map, so ask aloud. P can also be a short stop,
                // so check that first; silence just ends it.
                if (step == Step.IDLE) say(Step.ASK_PARKED, "도착하셨나요?")
            }
        }
        if (!isParked) {
            Notices.cancelArrival(this)
            showBanner = false
                stopLogbookWhileDriving()
        }
        if (isParked && wasParked != true) requestJourneyAddress()
    }

    /** The logbook flow (questions, pickers, keyboard) is for a parked car only; driving cancels it. */
    private fun stopLogbookWhileDriving() {
        if (step == Step.IDLE || step == Step.HEAR_COMMAND) return
        listenToken++
        voice.stopListening()
        if (step in setOf(Step.ASK_PARKED, Step.HEAR_PARKED, Step.ASK_ARRIVAL, Step.HEAR_ARRIVAL)) {
            // Nothing was started yet, so there is nothing to announce.
            step = Step.IDLE
            return
        }
        journeyNoteTarget = null
        draft = null
        parkOnly = false
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
        if (!running || index !in segments.indices) return
        segments[index] = change(segments[index])
        saveSegments()
    }

    private fun saveSegments() = segmentStore.save(segments)

    // ---- Family journeys, kept separately from business logbook rows ----

    private fun updateActiveJourney(change: (Journey) -> Journey) {
        val index = journeys.indexOfFirst { it.endedAt == null }
        if (index < 0) return
        val updated = change(journeys[index])
        if (updated == journeys[index]) return
        journeys[index] = updated
        journeyStore.save(journeys)
    }

    private fun captureJourneyGear(isParked: Boolean, at: Long) {
        if (!running || !consented) return
        val updated = JourneyDays.keepStarred(JourneyDays.onGear(journeys.toList(), isParked, at, car.journeyFix))
        if (updated != journeys.toList()) {
            journeys.clear()
            journeys.addAll(updated)
            journeyStore.save(journeys)
        }
    }

    /** The driver noticed a place on the road: keep where the car is now and confirm by voice, without stopping. */
    private fun savePassingPlace() {
        if (!running || !consented) return
        if (listening) voice.stopListening()
        val index = journeys.indexOfFirst { it.endedAt == null }
        if (index < 0) {
            drivingStatus = "아직 기록이 시작되지 않았어요."
            say(Step.IDLE, drivingStatus)
            return
        }
        val updated = journeys[index].savePassingPlace(System.currentTimeMillis(), car.journeyFix)
        if (updated != journeys[index]) {
            journeys[index] = updated
            journeyStore.save(journeys)
            requestJourneyAddress()
        }
        drivingStatus = if (updated.stops.last().point != null) "저장했어요." else "위치는 못 잡았지만 시각을 저장했어요."
        say(Step.IDLE, drivingStatus)
    }

    private fun requestJourneyAddress() {
        val journey = journeys.firstOrNull { it.endedAt == null } ?: return
        val stop = journey.stops.lastOrNull() ?: return
        val generation = collectionGeneration
        car.requestAddress { address -> runOnUiThread {
            if (running && generation == collectionGeneration) {
                val index = journeys.indexOfFirst { it.id == journey.id }
                if (index >= 0) {
                    journeys[index] = journeys[index].updateStop(stop.id) { it.copy(address = address) }
                    journeyStore.save(journeys)
                }
            }
        } }
    }

    private fun saveJourneyCsv(snapshot: JourneyExport.Snapshot) {
        if (!running || !consented || parked != true || step != Step.IDLE || pendingJourneyExport != null) return
        if (journeyExportJob?.isActive == true) { exportMessage = "CSV 파일을 저장하고 있어요."; return }
        val destination = ActivityResultContracts.CreateDocument("text/csv").createIntent(this, snapshot.filename()).resolveActivity(packageManager)
        // AAOS may resolve this action to its placeholder rather than an actual document picker.
        if (destination == null || destination.packageName == "com.android.car.frameworkpackagestubs") {
            exportMessage = "이 차량은 파일 저장창을 지원하지 않아요. 위 QR을 휴대폰으로 스캔해 CSV를 받아 주세요."
            return
        }
        pendingJourneyExport = snapshot to collectionGeneration
        exportMessage = "CSV를 저장할 위치를 선택해 주세요."
        try { saveJourneyDocument.launch(snapshot.filename()) }
        catch (_: android.content.ActivityNotFoundException) {
            pendingJourneyExport = null
            exportMessage = "이 차량에는 파일 저장창이 없어요. 휴대폰 QR로 CSV를 받아 주세요."
        } catch (_: Exception) {
            pendingJourneyExport = null
            exportMessage = "저장창을 열지 못했어요. 휴대폰 QR을 이용해 주세요."
        }
    }

    private fun updateJourneyStop(journeyId: String, stopId: String, change: (JourneyStop) -> JourneyStop) {
        if (!running || parked != true) return
        val index = journeys.indexOfFirst { it.id == journeyId }
        if (index < 0) return
        journeys[index] = journeys[index].updateStop(stopId, change)
        // Unstarring the last place of a past day leaves nothing worth keeping of that day.
        val kept = JourneyDays.keepStarred(journeys.toList())
        if (kept != journeys.toList()) { journeys.clear(); journeys.addAll(kept) }
        journeyStore.save(journeys)
    }

    private fun recordJourneyNote(journeyId: String, stopId: String) {
        if (!running || parked != true || step != Step.IDLE) return
        journeyNoteTarget = journeyId to stopId
        journeyFeedback = ""
        say(Step.ASK_JOURNEY_NOTE, "이곳에서 기억하고 싶은 것을 말씀해 주세요.")
    }

    // ---- Voice dialog ----

    /** Speaks [prompt]; ASK_* steps then listen. Falls back to a timer because TTS onDone is sometimes not delivered. */
    private fun say(next: Step, prompt: String) {
        step = next
        question = prompt
        val token = ++listenToken
        voice.speak(ParkingText.spoken(prompt))
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
            Step.ASK_PARKED -> listenFor(Step.HEAR_PARKED)
            Step.ASK_ARRIVAL -> listenFor(Step.HEAR_ARRIVAL)
            Step.ASK_FLOOR -> listenFor(Step.HEAR_FLOOR)
            Step.ASK_SPOT_NOTE -> listenFor(Step.HEAR_SPOT_NOTE)
            Step.ASK_PILLAR -> listenFor(Step.HEAR_PILLAR)
            Step.ASK_CONFIRM -> listenFor(Step.HEAR_CONFIRM)
            Step.ASK_JOURNEY_NOTE -> listenFor(Step.HEAR_JOURNEY_NOTE)
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
        if (!running || !consented || step != Step.IDLE) return
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
                Commands.Intent.LOGBOOK -> if (parked != true) {
                    reply = "운행일지는 정차한 뒤에 정리할 수 있어요."
                    say(Step.IDLE, reply)
                } else {
                    startLogbook()
                }
                Commands.Intent.PARK_ONLY -> startParkOnly()
                Commands.Intent.WHERE_PARKED -> answerWhereParked()
                Commands.Intent.JOURNEY -> { screen = Screen.JOURNEY; step = Step.IDLE; journeyPreview = false }
                Commands.Intent.SHOW_JOURNEY_QR, Commands.Intent.EXPORT_JOURNEY_CSV -> {
                    if (parked != true) say(Step.IDLE, "정차한 뒤에 기록을 공유할 수 있어요.")
                    else if (JourneyDays.today(journeys, System.currentTimeMillis()) == null && !journeyPreview) say(Step.IDLE, "오늘은 아직 경유지 기록이 없어요. 이동과 주차를 확인하면 자동으로 기록해요.")
                    else {
                        voice.stopListening()
                        step = Step.IDLE
                        screen = Screen.JOURNEY
                        reply = ""
                        journeyShareRequest = if (Commands.parseIntent(text) == Commands.Intent.EXPORT_JOURNEY_CSV) JourneyShareMode.CSV else JourneyShareMode.VIEW
                    }
                }
                Commands.Intent.SAVE_PLACE -> {
                    val journey = journeys.firstOrNull { it.endedAt == null }
                    val stop = journey?.stops?.lastOrNull()
                    if (showsDrivingScreen(drivingRestricted, parked)) savePassingPlace()
                    else if (journeyPreview) say(Step.IDLE, "예시 화면이에요. 실제 경유지를 저장하려면 예시 보기를 꺼 주세요.")
                    else if (parked == true && journey != null && stop != null) {
                        updateJourneyStop(journey.id, stop.id) { it.copy(favorite = true) }
                        step = Step.IDLE
                        screen = Screen.JOURNEY
                        recordJourneyNote(journey.id, stop.id)
                    } else say(Step.IDLE, "정차한 뒤 현재 경유지를 저장해 주세요.")
                }
                Commands.Intent.UNKNOWN -> {
                    reply = if (showsDrivingScreen(drivingRestricted, parked)) "여기 기억해 줘라고 말씀해 주세요." else "운행일지 정리해줘, 주차 위치 기록해줘, 여기 기억해 줘처럼 말씀해 주세요."
                    say(Step.IDLE, reply)
                }
            }
            Step.HEAR_PARKED -> {
                voice.stopListening()
                step = Step.IDLE
                when (Commands.parseParked(text).takeIf { parked == true }) {
                    true -> say(Step.ASK_ARRIVAL, "운행을 기록할까요?")
                    false -> say(Step.IDLE, "알겠어요. 이어서 기록할게요.")
                    null -> Unit
                }
            }
            Step.HEAR_ARRIVAL -> {
                voice.stopListening()
                step = Step.IDLE
                // "응" records it as business use; "출퇴근이야" or "개인 용도야" records it as that instead.
                // "아니" leaves the segments for later and still offers the parking spot.
                val purpose = Commands.parsePurpose(text)
                val yes = Commands.parseYesNo(text)
                when {
                    parked != true -> Unit
                    purpose != null -> startLogbook(purpose, brief = true)
                    yes == true -> startLogbook(brief = true)
                    yes == false -> startParkOnly()
                }
            }
            Step.HEAR_JOURNEY_NOTE -> {
                voice.stopListening()
                val target = journeyNoteTarget
                if (parked == true && target != null && text.isNotBlank()) {
                    updateJourneyStop(target.first, target.second) { it.copy(note = text.trim().take(240)) }
                    journeyFeedback = "메모를 저장했어요."
                } else journeyFeedback = "메모를 듣지 못했어요. 다시 말하거나 직접 입력해 주세요."
                journeyNoteTarget = null
                step = Step.IDLE
            }
            // The answer to "어디에 주차하셨나요?": a floor (with or without a pillar), a parking tower,
            // an open-air spot, or any short description. Not every car park has floors and pillars.
            Step.HEAR_FLOOR -> {
                val tower = ParkingText.towerLabel(text)
                floor = if (tower == null) ParkingText.parseFloor(text) else null
                if (floor != null) notice = ""
                // Some people answer with the whole spot at once ("지하 5층 A4").
                val pillar = ParkingText.parseSentence(text).second?.takeIf { floor != null }
                Log.i(TAG, "floor -> $floor, pillar=$pillar, tower=$tower")
                when {
                    tower != null -> { setFreeSpot(tower); reviewSpot() }
                    floor != null && pillar != null -> { setPillar(pillar); reviewSpot() }
                    // A floor followed by a description rather than a pillar code: keep it as said ("지하 1층 B동 앞").
                    floor != null && ParkingText.placeAfterFloor(text) != null -> {
                        setFreeSpot("${ParkingText.floorLabel(floor!!)} ${ParkingText.placeAfterFloor(text)}".take(40))
                        reviewSpot()
                    }
                    floor != null -> say(Step.ASK_PILLAR, "기둥 번호를 말씀해 주세요. 없으면 없다고 하셔도 돼요.")
                    ParkingText.isOutdoor(text) -> {
                        setFreeSpot(ParkingText.OUTDOOR)
                        say(Step.ASK_SPOT_NOTE, "근처에 보이는 걸 한마디로 말씀해 주세요.")
                    }
                    // The spot is asked right after the trip is recorded, so "아니" here means no spot.
                    Commands.parseYesNo(text) == false -> skipSpot()
                    text.isNotBlank() -> { setFreeSpot(text.trim().trimEnd('.').take(40)); reviewSpot() }
                    else -> reviewSpot()
                }
            }
            Step.HEAR_SPOT_NOTE -> {
                val note = text.trim().trimEnd('.')
                if (note.isNotBlank()) setFreeSpot("${ParkingText.OUTDOOR}, $note".take(40))
                reviewSpot()
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

    /**
     * Saves the unorganized segments as one trip and goes straight on to the parking spot. The purpose defaults
     * to business use and can be changed on screen. [brief] is the short wording used after the arrival question.
     */
    private fun startLogbook(purpose: Purpose = Purpose.BUSINESS, brief: Boolean = false) {
        if (!running || parked != true) return
        showBanner = false
        val merged = Logbook.merge(segments)
        if (merged == null) {
            // Only short moves (or nothing) since the last entry: drop them so they never become a trip.
            segments.removeAll { it.isClosed }
            saveSegments()
            askedAtKm = 0f
            reply = "정리할 운행이 없어요."
            screen = Screen.HOME
            say(Step.IDLE, reply)
            return
        }
        val trip = merged.copy(purpose = purpose)
        draft = trip
        parkOnly = false
        clearSpot()
        notice = ""
        screen = Screen.LOGBOOK
        trips.add(0, trip)
        store.save(trips)
        segments.removeAll { it.isClosed && it.departAt >= trip.startedAt && it.arriveAt!! <= trip.endedAt }
        saveSegments()
        askedAtKm = 0f
        Log.i(TAG, "logbook saved ${trip.distanceKm}km ${purpose.label}")
        val recorded = if (brief) {
            "${spokenDistance(trip.distanceKm)} 운행을 기록합니다."
        } else {
            val start = SimpleDateFormat("H시 m분", Locale.KOREA).format(Date(trip.startedAt))
            val place = trip.endAddress.ifBlank { "목적지" }
            "${start}에 출발해서 ${place}에 도착했어요. ${spokenKm(trip.startKm)}에서 ${spokenKm(trip.endKm)}까지, " +
                "총 ${spokenDistance(trip.distanceKm)}예요. 운행을 기록합니다."
        }
        say(Step.ASK_FLOOR, "$recorded 어디에 주차하셨나요?")
    }

    /** The purpose buttons on the organizing screen: the trip is already saved, so update it in place. */
    private fun changePurpose(purpose: Purpose) {
        val trip = draft ?: return
        val index = trips.indexOfFirst { it.startedAt == trip.startedAt && it.endedAt == trip.endedAt }
        if (index < 0) return
        trips[index] = trips[index].copy(purpose = purpose)
        store.save(trips)
        draft = trips[index]
    }

    /** Records only where the car is parked. Open segments stay as they are, so the logbook can still be organized later. */
    private fun startParkOnly() {
        if (!running || parked != true) {
            reply = "주차 위치는 정차한 뒤에 기록할 수 있어요."
            say(Step.IDLE, reply)
            return
        }
        showBanner = false
        draft = null
        parkOnly = true
        reply = ""
        parkAddress = segments.lastOrNull { it.isClosed }?.arriveAddress.orEmpty()
        car.requestAddress { address -> runOnUiThread { if (parkOnly && address.isNotBlank()) parkAddress = address } }
        screen = Screen.LOGBOOK
        askFloor()
    }

    private fun askFloor() {
        clearSpot()
        notice = ""
        say(Step.ASK_FLOOR, "어디에 주차하셨나요?")
    }

    private fun clearSpot() {
        floor = null
        pillarCode = ""
        manualSpot = ""
        showManual = false
    }

    /** A spot that is not floor + pillar. It goes in the free-text field so it can be fixed by typing. */
    private fun setFreeSpot(text: String) {
        floor = null
        pillarCode = ""
        manualSpot = text
        showManual = true
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
        if (parkOnly) {
            if (spot.isBlank()) return
            val fix = car.lastFix
            parkingNotes.add(0, ParkingNote(System.currentTimeMillis(), spot, parkAddress, fix?.latitude, fix?.longitude))
            while (parkingNotes.size > MAX_PARKING_NOTES) parkingNotes.removeAt(parkingNotes.lastIndex)
            store.saveParking(parkingNotes)
            Log.i(TAG, "parking saved \"$spot\" fix=${fix != null}")
            finishLogbook("${spot}로 저장했어요.")
            return
        }
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
        // A spot saved on its own shows up on the home card; a finished trip is best seen in the logbook table.
        screen = if (parkOnly) Screen.HOME else Screen.RECORDS
        if (parkOnly) reply = message
        parkOnly = false
        say(Step.IDLE, message)
    }

    /**
     * Tapping a tab while the organizing screen is up ends what it was doing, so the driver is never stuck with a
     * flow they cannot see. A trip, if there was one, is already saved; only the parking spot is dropped.
     */
    private fun leaveFlow() {
        if (screen != Screen.LOGBOOK) return
        listenToken++
        voice.stopListening()
        reply = if (parkOnly) "주차 위치 기록을 취소했어요." else "운행은 저장했어요. 주차 위치는 나중에 \"주차 위치 기록해줘\"로 남길 수 있어요."
        draft = null
        parkOnly = false
        question = ""
        step = Step.IDLE
    }

    private fun skipSpot() = finishLogbook(if (parkOnly) "주차 위치 기록을 취소했어요." else "알겠어요. 운행일지만 저장했어요.")

    private fun answerWhereParked() {
        val last = ParkingNote.latest(trips, parkingNotes)
        reply = if (last == null) {
            "아직 기록된 주차 위치가 없어요."
        } else {
            val time = SimpleDateFormat("M월 d일 H시 m분", Locale.KOREA).format(Date(last.savedAt))
            val place = last.address.takeIf { it.isNotBlank() }?.let { "$it, " } ?: ""
            "${last.spot}예요. ${place}${time}에 주차했어요."
        }
        screen = Screen.HOME
        say(Step.IDLE, reply)
    }

    // ---- UI ----

    @Composable
    private fun App() {
        val colors = darkColorScheme(
            primary = Ui.Accent, onPrimary = Ui.OnAccent, background = Ui.Bg, onBackground = Ui.Ink,
            surface = Ui.Panel, onSurface = Ui.Ink, surfaceVariant = Ui.Raised, onSurfaceVariant = Ui.Muted, outline = Ui.Line,
        )
        MaterialTheme(colorScheme = colors) {
            // Leaving a screen, or closing the free-text field, takes the keyboard down with it.
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            LaunchedEffect(screen, showManual, step) {
                if (!showManual || step == Step.IDLE) { focus.clearFocus(); keyboard?.hide() }
            }
            // A tap on empty space also takes the keyboard down; buttons and fields consume their own taps first.
            val dismiss = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus(); keyboard?.hide() }) }
            Surface(modifier = Modifier.fillMaxSize().then(dismiss), color = Ui.Bg) {
                if (showsDrivingScreen(drivingRestricted, parked)) {
                    DrivingScreen(consented, listening, partial, drivingStatus, onSave = ::savePassingPlace, onSpeak = ::callAssistant)
                    return@Surface
                }
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
                            Screen.SETTINGS -> SettingsScreen()
                            Screen.JOURNEY -> JourneyScreen(
                                today = JourneyDays.today(journeys, System.currentTimeMillis()), places = savedPlaces(journeys), parked = parked, preview = journeyPreview, onPreviewChange = { journeyPreview = it },
                                shareRequest = journeyShareRequest, onShareRequestHandled = { journeyShareRequest = null },
                                onUpdate = ::updateJourneyStop,
                                onVoiceNote = ::recordJourneyNote, recording = step == Step.ASK_JOURNEY_NOTE || step == Step.HEAR_JOURNEY_NOTE,
                                partial = partial, feedback = journeyFeedback,
                                viewerBase = viewerBase(),
                                exportMessage = exportMessage, onSaveCsv = ::saveJourneyCsv,
                                onOpenExport = { exportMessage = "" }, busy = step != Step.IDLE,
                                stopMapApp = stopMapApp,
                            )
                        }
                    }
                    if (confirmWithdraw) WithdrawBanner() else if (showBanner) ArrivalBanner()
                    // The organizing flow asks and listens by itself; everywhere else the mic is in the same place.
                    if (screen != Screen.LOGBOOK && screen != Screen.SETTINGS) VoiceBar()
                }
            }
        }
    }

    @Composable
    private fun ConsentScreen() {
        Column(
            Modifier.fillMaxSize().padding(48.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Route94", fontSize = Ui.Title, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Heading("시작하기 전에 개인정보 수집·이용에 동의해 주세요")
            Panel(Modifier.fillMaxWidth()) {
                Note("수집하는 정보와 쓰는 곳")
                Consent.items.forEach { item ->
                    Text(item.title, fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Note(item.detail)
                }
                Rule(Modifier.padding(vertical = 20.dp))
                Text("보관", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink, modifier = Modifier.padding(bottom = 4.dp))
                Note(Consent.STORAGE)
                Text("동의 철회", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Note(Consent.WITHDRAW)
            }
            if (declined) Note("동의하지 않으면 운행 기록과 음성 기능을 쓸 수 없어요. 언제든 다시 동의할 수 있어요.", color = Ui.Warn)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("동의하고 시작") { agree() }
                QuietButton("동의하지 않음") { declined = true }
            }
        }
    }

    @Composable
    private fun WithdrawBanner() {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).background(Ui.Panel, Ui.Shape).border(1.dp, Ui.Warn, Ui.Shape).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("동의를 철회하면 수집을 멈추고 저장된 운행 기록, 주차 위치, 번호를 모두 지워요.", fontSize = Ui.Body, color = Ui.Warn, modifier = Modifier.weight(1f))
            Button(
                onClick = { confirmWithdraw = false; withdrawConsent() }, shape = Ui.ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = Ui.Warn, contentColor = Ui.Bg),
            ) { Text("철회하고 지우기", fontSize = Ui.Body, fontWeight = FontWeight.Bold) }
            QuietButton("취소") { confirmWithdraw = false }
        }
    }

    @Composable
    private fun TopBar() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 오늘 is the main screen; every other screen is something opened from it, so it gets a way back.
            if (screen != Screen.HOME) {
                IconButton(onClick = { goBack() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Ui.Ink, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("Route94", fontSize = Ui.Title, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Spacer(Modifier.width(20.dp))
            GearState()
            Spacer(Modifier.weight(1f))
            // Organizing a trip (LOGBOOK) starts from the today tab, so it keeps that tab lit.
            Tab("오늘", screen == Screen.HOME || screen == Screen.LOGBOOK) { leaveFlow(); screen = Screen.HOME }
            Tab("운행일지", screen == Screen.RECORDS) { leaveFlow(); screen = Screen.RECORDS }
            Tab("발자취", screen == Screen.JOURNEY) { leaveFlow(); screen = Screen.JOURNEY }
            Spacer(Modifier.width(8.dp))
            SettingsMenu()
        }
    }

    @Composable
    private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
        Column(
            Modifier.clip(Ui.ControlShape).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 8.dp).width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontSize = Ui.Heading, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Ui.Ink else Ui.Muted)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(if (selected) Ui.Accent else Color.Transparent, RoundedCornerShape(2.dp)))
        }
    }

    /** Rarely used things live on their own screen; consent withdrawal is still two taps from anywhere. */
    @Composable
    private fun SettingsMenu() {
        val on = screen == Screen.SETTINGS
        IconButton(
            onClick = {
                if (on) screen = beforeSettings
                else { leaveFlow(); beforeSettings = if (screen == Screen.LOGBOOK) Screen.HOME else screen; screen = Screen.SETTINGS }
            },
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "설정", tint = if (on) Ui.Ink else Ui.Muted, modifier = Modifier.size(34.dp))
        }
    }

    @Composable
    private fun SettingsScreen() {
        var showConsent by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                ScreenTitle("설정")
                Text("내 설정", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink)
                Spacer(Modifier.height(12.dp))
                PhoneField()
                Note("기록 페이지에서 문자를 보낼 때 받는 사람으로 써요.", modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(20.dp))
                Note("주차 위치 문자의 지도 링크")
                Spacer(Modifier.height(8.dp))
                Segmented(MapApp.entries.map { it.label }, MapApp.entries.indexOf(mapApp)) { mapApp = MapApp.entries[it]; store.mapApp = mapApp }
                Spacer(Modifier.height(20.dp))
                Note("발자취 장소를 여는 지도 앱")
                Spacer(Modifier.height(8.dp))
                Segmented(StopMapApp.entries.map { it.label }, StopMapApp.entries.indexOf(stopMapApp)) { stopMapApp = StopMapApp.entries[it]; journeyStore.stopMapApp = stopMapApp }
                Rule(Modifier.padding(vertical = 28.dp))
                Text("개인정보", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuietButton(if (showConsent) "동의한 내용 닫기" else "동의한 내용 보기") { showConsent = !showConsent }
                    QuietButton("동의 철회 및 기록 삭제") { confirmWithdraw = true }
                }
                if (showConsent) {
                    Spacer(Modifier.height(16.dp))
                    Consent.items.forEach { item ->
                        Text(item.title, fontSize = Ui.Small, fontWeight = FontWeight.Bold, color = Ui.Ink, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                        Note(item.detail)
                    }
                    Note(Consent.STORAGE, modifier = Modifier.padding(top = 12.dp))
                }
                Rule(Modifier.padding(vertical = 28.dp))
                Text("앱 정보", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink)
                Spacer(Modifier.height(12.dp))
                Fact("버전", BuildConfig.VERSION_NAME)
                Fact("보관", "모든 기록은 이 차량 안에만 저장돼요")
            }
            // The form header lives beside the personal settings; every field may stay blank.
            Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(50.dp))
                Text("운행기록부 기본 정보", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink)
                Spacer(Modifier.height(12.dp))
                LogbookProfile.LABELS.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (key, label) -> ProfileField(key, label, Modifier.weight(1f)) }
                    }
                }
                Note("월간 운행기록부 QR을 휴대폰으로 열면 양식 위쪽 칸이 이 값으로 채워져요. 비워 두면 빈칸으로 나와요. 모두 이 차량 안에만 저장돼요.")
            }
        }
    }

    @Composable
    private fun ProfileField(key: String, label: String, modifier: Modifier) {
        OutlinedTextField(
            value = profile.get(key),
            onValueChange = { value -> profile = profile.with(key, value.take(40)); store.profile = profile },
            label = { Text(label) },
            enabled = parked == true,
            singleLine = true,
            textStyle = TextStyle(fontSize = Ui.Body),
            modifier = modifier,
        )
    }

    /** The top-left arrow: settings returns to where it was opened, the organizing flow ends, everything else goes to 오늘. */
    private fun goBack() {
        when (screen) {
            Screen.SETTINGS -> screen = beforeSettings
            Screen.LOGBOOK -> { leaveFlow(); screen = Screen.HOME }
            else -> screen = Screen.HOME
        }
    }

    /** Title row for screens that are not tabs. */
    @Composable
    private fun ScreenTitle(title: String) = Heading(title, Modifier.padding(bottom = 16.dp))

    @Composable
    private fun GearState() {
        val label = when (parked) {
            null -> "기어 확인 중"
            true -> "주차 중"
            false -> "주행 중"
        }
        Text(label, fontSize = Ui.Small, color = Ui.Muted, modifier = Modifier.background(Ui.Raised, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
    }

    /** Quiet prompt after returning to P; ignoring it just leaves the segments for later. */
    @Composable
    private fun ArrivalBanner() {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).background(Ui.Raised, Ui.Shape).padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text("운행을 기록할까요?", fontSize = Ui.Heading, fontWeight = FontWeight.Bold, color = Ui.Ink, modifier = Modifier.weight(1f))
            PrimaryButton("기록하기") { startLogbook() }
            QuietButton("닫기") { showBanner = false }
        }
    }

    /** What the car has kept, one row per kind, and the latest parking spot ready to send to the phone. */
    @Composable
    private fun HomeScreen() {
        val last = ParkingNote.latest(trips, parkingNotes)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                val pending = Logbook.merge(segments)
                val recording = segments.any { !it.isClosed }
                val month = LogbookLink.tripsIn(trips, java.time.YearMonth.now())
                val sum = LogbookLink.summary(month)
                SummaryRow(
                    title = "운행일지",
                    value = when {
                        pending != null -> km(pending.distanceKm)
                        recording -> "기록 중"
                        else -> "${month.size}건"
                    },
                    caption = when {
                        pending != null -> "정리하지 않은 운행"
                        recording -> "지금 운행"
                        else -> "이번 달 운행"
                    },
                    details = listOfNotNull(
                        if (month.isEmpty()) "이번 달 기록이 아직 없어요" else "이번 달 ${month.size}건, ${km(sum.totalKm)}",
                        String.format(Locale.KOREA, "업무사용비율 %.0f%%", sum.ratio).takeIf { month.isNotEmpty() },
                        "계기판 ${odometer?.let(::km) ?: "읽는 중"}",
                    ),
                ) {
                    if (pending != null) PrimaryButton("기록하기", Modifier.fillMaxWidth(), enabled = step == Step.IDLE && parked == true) { startLogbook() }
                    else QuietButton("기록 보기", Modifier.fillMaxWidth()) { screen = Screen.RECORDS }
                }
                Rule()
                SummaryRow(
                    title = "주차 위치",
                    value = last?.spot ?: "기록 없음",
                    caption = if (last == null) "" else "마지막 주차",
                    details = if (last == null) listOf("운행일지와 따로 주차 위치만 남길 수 있어요")
                    else listOfNotNull(formatDate(last.savedAt), last.address.takeIf { it.isNotBlank() }),
                ) {
                    QuietButton("기록하기", Modifier.fillMaxWidth(), enabled = step == Step.IDLE && parked == true) { startParkOnly() }
                }
                Rule()
                val journey = JourneyDays.today(journeys, System.currentTimeMillis())
                SummaryRow(
                    title = "발자취",
                    value = "${journey?.stops?.size ?: 0}곳",
                    caption = "오늘 들른 곳",
                    details = listOf(
                        String.format(Locale.KOREA, "오늘 이동 %.1f km", journey?.distanceKm ?: 0.0),
                        "발자국 ${savedPlaces(journeys).size}곳",
                    ),
                ) {
                    QuietButton("발자취 보기", Modifier.fillMaxWidth()) { journeyPreview = false; screen = Screen.JOURNEY }
                }
            }
            SharePanel(listOf("마지막 주차 위치"), 0, {}) {
                RecordShare(last?.let { RecordLink.url(it, phone, mapApp, viewerBase()) }, last?.smsBody(mapApp), empty = "주차 위치를 기록하면 QR이 나타나요.")
            }
        }
    }

    /** One kind of record: its name, the current figure, a few facts, and the one thing to do next. */
    @Composable
    private fun ColumnScope.SummaryRow(
        title: String, value: String, caption: String, details: List<String>, action: @Composable () -> Unit,
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Heading(title, Modifier.width(170.dp))
            Column(Modifier.weight(1.1f)) {
                Text(value, fontSize = Ui.Value, fontWeight = FontWeight.Bold, color = Ui.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 56.sp)
                if (caption.isNotBlank()) Note(caption)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                details.forEach { Text(it, fontSize = Ui.Body, color = Ui.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            // Same width on every row so the buttons line up as a column.
            Box(Modifier.width(180.dp), contentAlignment = Alignment.CenterEnd) { action() }
        }
    }

    /** The mic is how the app is used, so it sits under every tab with the phrases that work there. */
    @Composable
    private fun VoiceBar() {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).background(Ui.Panel, Ui.Shape).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicButton("말하기", enabled = step == Step.IDLE) { callAssistant() }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                when {
                    step == Step.HEAR_COMMAND -> Heading(partial.ifBlank { "듣고 있어요" })
                    reply.isNotBlank() -> Heading(reply)
                    screen == Screen.JOURNEY -> Text("\"여기 기억해 줘\"   \"휴대폰으로 보여 줘\"   \"엑셀로 내보내 줘\"", fontSize = Ui.Body, color = Ui.Muted)
                    else -> Text("\"운행일지 정리해줘\"   \"주차 위치 기록해줘\"   \"주차 어디 했지?\"", fontSize = Ui.Body, color = Ui.Muted)
                }
                if (heard.isNotBlank() && step != Step.HEAR_COMMAND) Note("들은 말: \"$heard\"")
                if (notice.isNotBlank() && step == Step.HEAR_COMMAND) Note(notice, color = Ui.Warn)
            }
        }
    }

    /** An SMS to the driver's own number as a QR, with the text it will send. [qr] and [body] are null with nothing to send. */
    /**
     * [body] builds the SMS for a map app; the default app comes from settings and can be changed here for this
     * QR only. [canPick] false hides that choice.
     */
    /**
     * One record as a phone page QR. The page offers the map apps and a text message, so the driver picks there
     * instead of the QR dropping straight into the SMS composer. [text] previews what the text message will say.
     */
    @Composable
    private fun RecordShare(url: String?, text: String?, empty: String) {
        QrPlate(url, "기록 페이지 QR 코드", empty = empty)
        if (url != null) {
            Note("카메라로 스캔하면 기록 페이지가 열려요. 거기서 지도 앱으로 열거나 문자로 보낼 수 있어요.")
            if (phone.count(Char::isDigit) < 10) {
                Note("설정에 내 번호를 넣어 두면 문자 받는 사람이 자동으로 채워져요.", color = Ui.Warn)
                QuietButton("설정 열기") { beforeSettings = screen; screen = Screen.SETTINGS }
            }
            // The map link is a button on the page, so the preview shows only the words of the message.
            if (text != null) Text(text.lines().filterNot { it.startsWith("http") }.joinToString("\n"), fontSize = Ui.Small, color = Ui.Ink, lineHeight = 28.sp, modifier = Modifier.fillMaxWidth().background(Ui.Raised, Ui.ControlShape).padding(16.dp))
        }
    }

    /** e.g. --es viewer_url http://192.168.0.10:8765/ to try viewer/ from a laptop before it is published. */
    private fun viewerBase(): String = intent.getStringExtra("viewer_url") ?: LogbookLink.BASE_URL

    @Composable
    private fun PhoneField() {
        OutlinedTextField(
            value = phone,
            onValueChange = { value ->
                phone = value.filter { it.isDigit() || it == '-' }.take(13)
                store.phone = phone
            },
            label = { Text(if (parked == false) "내 휴대폰 번호 (정차 중에만 입력)" else "내 휴대폰 번호") },
            // Typing is for a parked car only (driver distraction guideline).
            enabled = parked == true,
            singleLine = true,
            textStyle = TextStyle(fontSize = Ui.Body),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun LogbookScreen() {
        if (parkOnly) return ParkOnlyScreen()
        val trip = draft ?: return HomeScreen()
        Column {
        ScreenTitle("운행 기록")
        Row(horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            Panel(Modifier.weight(0.8f)) {
                Heading("기록한 운행")
                Text("총 ${km(trip.distanceKm)}", fontSize = Ui.Value, fontWeight = FontWeight.Bold, color = Ui.Ink)
                Spacer(Modifier.height(12.dp))
                Fact("출발", "${formatDate(trip.startedAt)}  ${trip.startAddress.ifBlank { "주소 없음" }}")
                Fact("도착", "${formatDate(trip.endedAt)}  ${trip.endAddress.ifBlank { "주소 없음" }}")
                Fact("계기판", "${km(trip.startKm)} → ${km(trip.endKm)}")
                Spacer(Modifier.height(24.dp))
                Note("운행 구분")
                Spacer(Modifier.height(8.dp))
                Segmented(Purpose.entries.map { it.label }, Purpose.entries.indexOf(trip.purpose)) { changePurpose(Purpose.entries[it]) }
            }
            AssistantPanel {
                when (step) {
                    Step.ASK_FLOOR, Step.HEAR_FLOOR, Step.ASK_PILLAR, Step.HEAR_PILLAR, Step.ASK_SPOT_NOTE, Step.HEAR_SPOT_NOTE,
                    Step.ASK_CONFIRM, Step.HEAR_CONFIRM, Step.REVIEW_SPOT -> SpotPicker()
                    else -> Unit
                }
            }
        }
        }
    }

    @Composable
    private fun ParkOnlyScreen() {
        Column {
        ScreenTitle("주차 위치 기록")
        Row(horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            Panel(Modifier.weight(0.8f)) {
                Heading("주차 위치만 기록")
                Spacer(Modifier.height(12.dp))
                Fact("위치", parkAddress.ifBlank { "지금 주차한 곳" })
                Fact("시각", formatDate(System.currentTimeMillis()))
                Fact("지도", if (car.lastFix != null) "문자에 지도 링크를 넣어요" else "GPS 좌표가 없어 지도 링크는 빠져요")
                Spacer(Modifier.height(24.dp))
                Note("운행일지는 그대로 둬요. 나중에 \"운행일지 정리해줘\"로 따로 정리할 수 있어요.")
            }
            AssistantPanel { SpotPicker() }
        }
        }
    }

    @Composable
    private fun Fact(name: String, value: String) {
        Row(Modifier.padding(top = 8.dp)) {
            Text(name, fontSize = Ui.Body, color = Ui.Muted, modifier = Modifier.width(90.dp))
            Text(value, fontSize = Ui.Body, color = Ui.Ink)
        }
    }

    /** The current question, what was heard, and the controls that answer it by touch. */
    @Composable
    private fun RowScope.AssistantPanel(content: @Composable () -> Unit) {
        Panel(Modifier.weight(1.4f).verticalScroll(rememberScrollState())) {
            Text(question, fontSize = Ui.Title, fontWeight = FontWeight.Bold, color = Ui.Ink, lineHeight = 44.sp)
            // Always one line here, so the keys and buttons below do not jump when listening ends.
            if (listening) Note("듣고 있어요  $partial", color = Ui.Accent, modifier = Modifier.padding(top = 8.dp))
            else Note(if (heard.isNotBlank()) "들은 말: \"$heard\"" else " ", modifier = Modifier.padding(top = 8.dp))
            if (notice.isNotBlank()) Note(notice, color = Ui.Warn)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }

    @Composable
    private fun SpotPicker() {
        val spot = parkingText()
        val reviewing = step in setOf(Step.ASK_CONFIRM, Step.HEAR_CONFIRM, Step.REVIEW_SPOT)
        // While the field has focus the keyboard is up: the key grids are useless then and would push the buttons
        // under it (the IME's touch area reaches above its visible keys), so only the field and the buttons stay.
        var typing by remember { mutableStateOf(false) }
        Text(spot.ifBlank { "주차 위치" }, fontSize = Ui.Value, fontWeight = FontWeight.Bold, color = if (spot.isBlank()) Ui.Muted else Ui.Ink)
        Spacer(Modifier.height(16.dp))
        if (!typing) {
        Note("층이 없는 곳")
        val presets = listOf(ParkingText.TOWER, ParkingText.OUTDOOR)
        Keys(presets, presets.indexOfFirst { manualSpot.startsWith(it) }) { setFreeSpot(presets[it]); onSpotEdited() }
        Note("층")
        // Picking a floor or pillar goes back to the structured spot, so drop any free text.
        Keys(FLOORS.map { if (it < 0) "B${-it}" else "${it}F" }, FLOORS.indexOf(floor)) { manualSpot = ""; floor = FLOORS[it]; onSpotEdited() }
        Note(if (pillarCode.isBlank()) "기둥 번호" else "기둥 번호 $pillarCode")
        // Each tap appends, so codes such as AL02 or B-15 can be built; leading zeros are kept.
        Keys(LETTERS.map(Char::toString), -1) { manualSpot = ""; pillarCode = (pillarCode + LETTERS[it]).take(8); onSpotEdited() }
        Keys((1..9).map(Int::toString) + "0" + "-" + "지우기" + "전체 지우기", -1) { index ->
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
        }
        if (showManual) {
            OutlinedTextField(
                value = manualSpot,
                onValueChange = { manualSpot = it.take(40); onSpotEdited() },
                label = { Text("주차 위치 직접 입력 (예: 주차타워 3번, 정문 옆 편의점 앞)") },
                singleLine = true,
                textStyle = TextStyle(fontSize = Ui.Body),
                modifier = Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused },
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("저장", enabled = reviewing && spot.isNotBlank()) { saveSpot(spot) }
            QuietButton("다시 묻기", enabled = reviewing) { askFloor() }
            QuietButton(if (showManual) "직접 입력 닫기" else "직접 입력") { showManual = !showManual }
            QuietButton(if (parkOnly) "취소" else "건너뛰기") { skipSpot() }
        }
    }

    /** A wrapping row of keys; tapping one reports its index. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Keys(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Ui.Accent else Ui.Raised).clickable { onPick(index) }
                        .defaultMinSize(minWidth = 56.dp, minHeight = 52.dp).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = Ui.Body, color = if (on) Ui.OnAccent else Ui.Ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
            }
        }
    }

    /** The logbook one month at a time, the unit it is submitted in; the monthly QR follows the same month. */
    @Composable
    private fun RecordsScreen() {
        // This month is always offered, even before its first trip, so the table opens on it.
        val months = remember(trips.size) { (LogbookLink.months(trips) + java.time.YearMonth.now()).distinct().sortedDescending() }
        var monthIndex by remember { mutableStateOf(0) }
        val month = months[monthIndex.coerceIn(months.indices)]
        val inMonth = trips.filter { LogbookLink.monthOf(it) == month }
        var selected by remember(month) { mutableStateOf(0) }
        var share by remember { mutableStateOf(0) }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Ui.Gap)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Heading("운행기록부", Modifier.weight(1f))
                    IconButton(onClick = { monthIndex++ }, enabled = monthIndex < months.lastIndex, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", modifier = Modifier.size(36.dp))
                    }
                    Text("${month.year}년 ${month.monthValue}월", fontSize = Ui.Heading, fontWeight = FontWeight.Bold, color = Ui.Ink, textAlign = TextAlign.Center, modifier = Modifier.width(190.dp))
                    IconButton(onClick = { monthIndex-- }, enabled = monthIndex > 0, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", modifier = Modifier.size(36.dp))
                    }
                }
                Row(Modifier.padding(top = 12.dp)) {
                    Cell("사용일자", 1.3f, header = true)
                    Cell("주행 전", 1f, header = true)
                    Cell("주행 후", 1f, header = true)
                    Cell("주행거리", 0.8f, header = true)
                    Cell("구분", 0.8f, header = true)
                    Cell("주차 위치", 1.3f, header = true)
                }
                Rule()
                if (inMonth.isEmpty()) Note(
                    if (trips.isEmpty()) "아직 기록이 없어요. 주행을 마치고 \"운행일지 정리해줘\"라고 말해 보세요." else "이 달에는 기록이 없어요.",
                    modifier = Modifier.padding(top = 20.dp),
                )
                LazyColumn {
                    items(inMonth.size) { index ->
                        val trip = inMonth[index]
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(if (index == selected) Ui.Raised else Color.Transparent)
                                .clickable { selected = index; share = 0 },
                        ) {
                            Cell(formatDate(trip.endedAt), 1.3f)
                            Cell(km(trip.startKm), 1f)
                            Cell(km(trip.endKm), 1f)
                            Cell(km(trip.distanceKm), 0.8f)
                            Cell(trip.purpose.label, 0.8f)
                            Cell(trip.parking.ifBlank { "-" }, 1.3f)
                        }
                    }
                }
            }
            SharePanel(listOf("운행 기록", "월간 운행기록부"), share, { share = it }) {
                if (share == 1) MonthlyShare(month, inMonth)
                else {
                    val trip = inMonth.getOrNull(selected)
                    RecordShare(trip?.let { RecordLink.url(it, phone, mapApp, viewerBase()) }, trip?.smsBody(mapApp), empty = "기록이 생기면 QR이 나타나요.")
                }
            }
        }
    }

    /** The month shown in the table as a link QR: the phone opens the NTS table and can save it as a spreadsheet. */
    @Composable
    private fun MonthlyShare(month: java.time.YearMonth, inMonth: List<Trip>) {
        Text("${month.year}년 ${month.monthValue}월", fontSize = Ui.Body, fontWeight = FontWeight.Bold, color = Ui.Ink)
        if (inMonth.isEmpty()) return QrPlate(null, "월간 운행기록부 QR 코드", empty = "이 달에 기록이 생기면 QR이 나타나요.")
        val sum = LogbookLink.summary(inMonth)
        val url = remember(month, inMonth, profile) {
            LogbookLink.url(trips, month, base = viewerBase(), profile = profile)
                .also { Log.i(TAG, "monthly ${it.length} chars $it") }
        }
        QrPlate(url, "월간 운행기록부 QR 코드", empty = "QR을 만들지 못했어요.")
        Note("카메라로 스캔하면 이 달의 운행기록부가 열려요. 엑셀로 받을 수 있어요.")
        Column(Modifier.fillMaxWidth().background(Ui.Raised, Ui.ControlShape).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${inMonth.size}건, 총 ${km(sum.totalKm)}", fontSize = Ui.Small, color = Ui.Ink)
            Text("업무용 ${km(sum.workKm)}", fontSize = Ui.Small, color = Ui.Ink)
            Text(String.format(Locale.KOREA, "업무사용비율 %.1f%%", sum.ratio), fontSize = Ui.Small, fontWeight = FontWeight.Bold, color = Ui.Ink)
        }
    }

    @Composable
    private fun RowScope.Cell(text: String, weight: Float, header: Boolean = false) = Text(
        text,
        fontSize = if (header) Ui.Small else 22.sp,
        color = if (header) Ui.Muted else Ui.Ink,
        textAlign = TextAlign.Start,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight).padding(horizontal = 10.dp, vertical = if (header) 12.dp else 18.dp),
    )

    private companion object {
        const val TAG = "ArrivalNote"
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val LISTEN_TIMEOUT_MS = 10_000L
        const val MAX_PARKING_NOTES = 20
        const val ASK_MIN_KM = 1.0f
        val FLOORS = listOf(-6, -5, -4, -3, -2, -1, 1, 2, 3)
        val LETTERS = ('A'..'Z').toList()

        /** Phrases fed to STT in order when launched with `--ez demo_audio true` (the emulator mic is silent). */
        val DEMO_SCRIPT = listOf(
            "cmd_logbook.pcm", "floor.pcm", "pillar.pcm", "yes.pcm",
            "cmd_where.pcm", "cmd_logbook.pcm",
        )

        /** "12,340킬로미터" reads better through TTS than "12,340.0km". */
        fun spokenKm(value: Float) = String.format(Locale.KOREA, "%,d킬로미터", value.toInt())

        fun shortDate(millis: Long): String = SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(millis))

        fun spokenDistance(value: Float) = String.format(Locale.KOREA, "%.1f킬로미터", value)
    }
}
