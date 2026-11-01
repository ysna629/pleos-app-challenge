package ai.pleos.challenge.smoketest

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.app.Activity
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView

/**
 * Calls each Pleos Vehicle read/control API once and logs the result with tag SMOKE.
 * Run: adb logcat -s SMOKE
 * Suites: default (control), or `adb shell am start -n <pkg>/.MainActivity --es suite logbook|speech`
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var vehicle: Vehicle
    private lateinit var output: TextView
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)

    // Areas discovered through check...Capabilities; fall back to the driver side.
    private var lightArea: SeatArea = driver
    private var seatMoveArea: SeatArea = driver
    private var doorArea = DoorArea(DoorValue.ROW_1_LEFT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(output) })

        vehicle = Vehicle(applicationContext)
        step("init") { vehicle.initialize(); log("init", "OK") }
        // Give the Vehicle Service binding a moment before the first call.
        val suite = when (intent.getStringExtra("suite")) {
            "logbook" -> logbookSteps()
            "speech" -> speechSteps()
            else -> steps()
        }
        handler.postDelayed({ runSteps(suite) }, 2000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { vehicle.release() }
        runCatching { navi?.release() }
        runCatching { fused?.release() }
        runCatching { tts?.release() }
        runCatching { stt?.release() }
        super.onDestroy()
    }

    private fun steps(): List<Pair<String, () -> Unit>> = listOf(
        "cap.light.ambientColor" to {
            vehicle.getLight().checkAmbientLightBrightnessColorCapabilities(
                { config, areas -> areas.firstOrNull()?.let { lightArea = it }; ok("cap.light.ambientColor", "${config.accessType} $areas") },
                { fail("cap.light.ambientColor", it) })
        },
        "cap.seat.moveBackrest" to {
            vehicle.getSeat().checkMoveSeatBackrestAngleCapabilities(
                { config, areas -> areas.firstOrNull()?.let { seatMoveArea = it }; ok("cap.seat.moveBackrest", "${config.accessType} $areas") },
                { fail("cap.seat.moveBackrest", it) })
        },
        "cap.door.lock" to {
            vehicle.getDoor().checkDoorLockCapabilities(
                { config, areas -> areas.firstOrNull()?.let { doorArea = it }; ok("cap.door.lock", "${config.accessType} $areas") },
                { fail("cap.door.lock", it) })
        },
        "gear.get" to {
            vehicle.getGear().getCurrentGearSelectionState({ ok("gear.get", it) }, { fail("gear.get", it) })
        },
        "ev.soc.get" to {
            vehicle.getEvBattery().getEvBatteryStateOfCharge({ ok("ev.soc.get", it) }, { fail("ev.soc.get", it) })
        },
        "ev.chargeSwitch.get" to {
            vehicle.getEvBattery().getEvChargeSwitchState({ ok("ev.chargeSwitch.get", it) }, { fail("ev.chargeSwitch.get", it) })
        },
        "ev.chargeLimit.set(80)" to {
            vehicle.getEvBattery().setEvChargePercentLimit(80f, { ok("ev.chargeLimit.set(80)", it) }, { fail("ev.chargeLimit.set(80)", it) })
        },
        "odo.tripTime.get" to {
            vehicle.getOdometer().getTripCurrentDrivingTime({ ok("odo.tripTime.get", it) }, { fail("odo.tripTime.get", it) })
        },
        "hvac.temp.get" to {
            vehicle.getHVAC().getHvacTemperature(driver, TemperatureUnit.CELSIUS, { ok("hvac.temp.get", it) }, { fail("hvac.temp.get", it) })
        },
        "hvac.temp.set(22.0)" to {
            vehicle.getHVAC().setHvacTemperature(driver, 22.0f, TemperatureUnit.CELSIUS,
                { _, v -> ok("hvac.temp.set(22.0)", v) }, { fail("hvac.temp.set(22.0)", it) })
        },
        "hvac.temp.get(after)" to {
            vehicle.getHVAC().getHvacTemperature(driver, TemperatureUnit.CELSIUS, { ok("hvac.temp.get(after)", it) }, { fail("hvac.temp.get(after)", it) })
        },
        "seat.backrest.get" to {
            vehicle.getSeat().getCurrentSeatBackrestAnglePosition(seatMoveArea, { ok("seat.backrest.get", it) }, { fail("seat.backrest.get", it) })
        },
        "seat.backrest.move(BACKWARD)" to {
            vehicle.getSeat().moveSeatBackrestAngle(seatMoveArea, MoveControl.BACKWARD,
                { _, v -> ok("seat.backrest.move(BACKWARD)", v) }, { fail("seat.backrest.move(BACKWARD)", it) })
        },
        "seat.backrest.move(STOP)" to {
            vehicle.getSeat().moveSeatBackrestAngle(seatMoveArea, MoveControl.STOP,
                { _, v -> ok("seat.backrest.move(STOP)", v) }, { fail("seat.backrest.move(STOP)", it) })
        },
        "seat.backrest.get(after)" to {
            vehicle.getSeat().getCurrentSeatBackrestAnglePosition(seatMoveArea, { ok("seat.backrest.get(after)", it) }, { fail("seat.backrest.get(after)", it) })
        },
        "seat.heat.set(2)" to {
            vehicle.getSeat().setHvacSeatTemperature(driver, 2, { _, v -> ok("seat.heat.set(2)", v) }, { fail("seat.heat.set(2)", it) })
        },
        "light.ambient.get" to {
            vehicle.getLight().getAmbientLightBrightnessColor(lightArea, { ok("light.ambient.get", "0x" + Integer.toHexString(it ?: -1)) }, { fail("light.ambient.get", it) })
        },
        "light.ambient.switch(ON)" to {
            vehicle.getLight().setAmbientLightSwitch(lightArea, LightSwitch.ON, { _, v -> ok("light.ambient.switch(ON)", v) }, { fail("light.ambient.switch(ON)", it) })
        },
        "light.ambient.set(0x7F00FF)" to {
            vehicle.getLight().setAmbientLightBrightnessColor(lightArea, 0x7F00FF,
                { _, v -> ok("light.ambient.set(0x7F00FF)", "0x" + Integer.toHexString(v ?: -1)) }, { fail("light.ambient.set(0x7F00FF)", it) })
        },
        "door.lock.get" to {
            vehicle.getDoor().getDoorLockState(doorArea, { ok("door.lock.get", it) }, { fail("door.lock.get", it) })
        },
        "door.lock.set(true)" to {
            vehicle.getDoor().setDoorLock(doorArea, true, { _, v -> ok("door.lock.set(true)", v) }, { fail("door.lock.set(true)", it) })
        },
        "window.pos.get" to {
            vehicle.getWindow().getCurrentWindowPosition(WindowArea(WindowValue.ROW_1_LEFT), { ok("window.pos.get", it) }, { fail("window.pos.get", it) })
        },
        "window.pos.set(5)" to {
            vehicle.getWindow().setWindowPosition(WindowArea(WindowValue.ROW_1_LEFT), 5, { _, v -> ok("window.pos.set(5)", v) }, { fail("window.pos.set(5)", it) })
        },
        "aaos.properties" to { readCarProperties() },
    )

    /** Checks what a trip logbook needs: odometer, plate number, destination and location. */
    private fun logbookSteps(): List<Pair<String, () -> Unit>> = listOf(
        "navi.init" to {
            navi = NaviHelper(applicationContext).also {
                it.initialize({ log("navi.init", "OK") }, { e -> log("navi.init", "FAIL $e") })
                it.addListener(naviListener)
            }
        },
        "fused.init" to {
            fused = FusedLocation(applicationContext).also {
                it.initialize()
                log("fused.init", "OK supported=${it.isSupported()}")
            }
        },
        "odo.cap.distance" to {
            vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities({ ok("odo.cap.distance", "${it.accessType}") }, { fail("odo.cap.distance", it) })
        },
        "odo.cap.tripTime" to {
            vehicle.getOdometer().checkTripCurrentDrivingTimeCapabilities({ ok("odo.cap.tripTime", "${it.accessType}") }, { fail("odo.cap.tripTime", it) })
        },
        "odo.distance.register" to {
            vehicle.getOdometer().registerDistanceDriven(object : ai.pleos.playground.vehicle.listener.DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(value: Float?) = log("odo.event.distance", "$value")
                override fun onFailed(e: Exception) = fail("odo.event.distance", e)
            })
            vehicle.getOdometer().registerTripCurrentDrivingTime(object : ai.pleos.playground.vehicle.listener.DrivingTimeListener {
                override fun onDrivingTimeUpdated(value: Int?) = log("odo.event.tripTime", "$value")
                override fun onFailed(e: Exception) = fail("odo.event.tripTime", e)
            })
            log("odo.distance.register", "OK")
        },
        "odo.distance.get" to {
            vehicle.getOdometer().getCurrentDistanceDriven({ ok("odo.distance.get", it) }, { fail("odo.distance.get", it) })
        },
        "odo.tripDistance.get" to {
            vehicle.getOdometer().getTripCurrentDrivingDistance({ ok("odo.tripDistance.get", it) }, { fail("odo.tripDistance.get", it) })
        },
        "odo.tripCumulativeDistance.get" to {
            vehicle.getOdometer().getTripCumulativeDrivingDistance({ ok("odo.tripCumulativeDistance.get", it) }, { fail("odo.tripCumulativeDistance.get", it) })
        },
        "odo.tripCumulativeTime.get" to {
            vehicle.getOdometer().getTripCumulativeDrivingTime({ ok("odo.tripCumulativeTime.get", it) }, { fail("odo.tripCumulativeTime.get", it) })
        },
        "odo.range.get" to {
            vehicle.getOdometer().getRangeRemainingDistance({ ok("odo.range.get", it) }, { fail("odo.range.get", it) })
        },
        "car.crn.get" to {
            vehicle.getCarInfo().getCRNInfo({ ok("car.crn.get", it) }, { fail("car.crn.get", it) })
        },
        "car.vin.get" to {
            vehicle.getCarInfo().getVINInfo({ ok("car.vin.get", it) }, { fail("car.vin.get", it) })
        },
        "aaos.PERF_ODOMETER" to { readProperty("PERF_ODOMETER", VehiclePropertyIds.PERF_ODOMETER) },
        "fused.last" to {
            fused?.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) = ok("fused.last", location)
                override fun onError(e: Exception) = fail("fused.last", e)
            })
        },
        "navi.currentLocation" to { navi?.getCurrentLocationInfo() },
        "navi.recentDestinations" to { navi?.getRecentDestinationInfo() },
        "navi.destination" to { navi?.getDestinationInfo() },
        "navi.requestRoute(Seoul Station)" to {
            navi?.requestRoute(RouteInfo(37.5547, 126.9707, null, null, "서울역", "", "서울 용산구 한강대로 405", "", RouteOption.RECOMMENDED, "", false))
            log("navi.requestRoute(Seoul Station)", "SENT")
        },
        "navi.destination(after)" to { navi?.getDestinationInfo() },
        "navi.routeState" to { navi?.getRouteStateInfo() },
        "done" to { log("done", "LOGBOOK STEPS FINISHED (listener events may still arrive)") },
    )

    /** On-device TTS and STT need no Playground app registration. STT listens for ~15 s after "stt.request". */
    private fun speechSteps(): List<Pair<String, () -> Unit>> = listOf(
        "tts.init" to {
            tts = TextToSpeech(applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE).also {
                it.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                    override fun onSuccess() {
                        it.addEventListener(ttsListener)
                        log("tts.init", "OK")
                    }
                    override fun onFailure(type: ai.pleos.playground.tts.constant.ErrorType) = log("tts.init", "FAIL $type")
                })
            }
        },
        "tts.speak" to {
            tts?.speak("오늘 12,340킬로미터에서 12,375킬로미터까지, 총 35킬로미터 주행했어요.", true)
            log("tts.speak", "SENT")
        },
        "tts.wait" to {},
        "tts.wait2" to {},
        "tts.wait3" to {},
        "stt.init" to {
            stt = SpeechToText(applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE).also {
                it.addListener(sttListener)
                it.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                    override fun onSuccess() = log("stt.init", "OK")
                    override fun onFailure(type: ai.pleos.playground.stt.constant.ErrorType) = log("stt.init", "FAIL $type")
                })
            }
        },
        "stt.request" to {
            stt?.request()
            log("stt.request", "LISTENING")
        },
        "stt.wait" to {},
        "stt.wait2" to {},
        "stt.wait3" to {},
        "stt.wait4" to {},
        "stt.stop" to { stt?.stop(); log("stt.stop", "OK") },
        // Feeds a recorded 16 kHz mono PCM phrase instead of the microphone.
        "stt.file" to {
            stt?.request()
            val pcm = assets.open("park.pcm").use { it.readBytes() }
            pcm.toList().chunked(3200).forEach { stt?.sendAudio(it.toByteArray()) }
            stt?.completeAudioSend()
            log("stt.file", "SENT ${pcm.size} bytes")
        },
    )

    private val ttsListener = object : EventListener {
        override fun onStart() = log("tts.event", "start")
        override fun onReady() = log("tts.event", "ready")
        override fun onDone() = log("tts.event", "done")
        override fun onError(type: ai.pleos.playground.tts.constant.ErrorType) = log("tts.event", "error $type")
        override fun onStop() = log("tts.event", "stop")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) = log("stt.event", "text final=$isFinal \"$text\"")
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = log("stt.event", "started")
        override fun onEndedRecognition() = log("stt.event", "ended")
        override fun onError(type: ai.pleos.playground.stt.constant.ErrorType) = log("stt.event", "error $type")
        override fun onReady() = log("stt.event", "ready")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = log("navi.event.currentLocation", "$info")
        override fun onDestinationInfo(info: DestinationInfo) = log("navi.event.destination", "$info")
        override fun onRecentDestinationInfo(list: List<RecentDestinationInfo>) = log("navi.event.recent", "${list.size} ${list.take(3)}")
        override fun onRouteStarted(info: RouteStartInfo) = log("navi.event.routeStarted", "$info")
        override fun onDestinationArrived(info: DestinationArrivedInfo) = log("navi.event.arrived", "$info")
        override fun onRouteEnded() = log("navi.event.routeEnded", "")
        override fun onError(code: NaviErrorCode) = log("navi.event.error", "$code")
    }

    private fun readProperty(name: String, id: Int) {
        val cpm = Car.createCar(this)?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        try {
            log("aaos.$name", "OK ${cpm?.getProperty<Any>(id, 0)?.value}")
        } catch (e: Exception) {
            log("aaos.$name", "FAIL ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Reads standard VHAL properties that the Pleos SDK does not expose directly. */
    private fun readCarProperties() {
        val car = Car.createCar(this)
        val cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (cpm == null) {
            log("aaos", "FAIL CarPropertyManager unavailable")
            return
        }
        listOf(
            "GEAR_SELECTION" to VehiclePropertyIds.GEAR_SELECTION,
            "PERF_VEHICLE_SPEED" to VehiclePropertyIds.PERF_VEHICLE_SPEED,
            "PARKING_BRAKE_ON" to VehiclePropertyIds.PARKING_BRAKE_ON,
            "EV_CHARGE_STATE" to VehiclePropertyIds.EV_CHARGE_STATE,
            "EV_CHARGE_TIME_REMAINING" to VehiclePropertyIds.EV_CHARGE_TIME_REMAINING,
            "EV_CHARGE_PORT_CONNECTED" to VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
            "RANGE_REMAINING" to VehiclePropertyIds.RANGE_REMAINING,
        ).forEach { (name, id) ->
            try {
                val value = cpm.getProperty<Any>(id, 0)?.value
                log("aaos.$name", "OK $value")
            } catch (e: Exception) {
                log("aaos.$name", "FAIL ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        car.disconnect()
        log("done", "ALL STEPS FINISHED")
    }

    private fun runSteps(remaining: List<Pair<String, () -> Unit>>) {
        val (name, action) = remaining.firstOrNull() ?: return
        step(name, action)
        handler.postDelayed({ runSteps(remaining.drop(1)) }, 1500)
    }

    private fun step(name: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            log(name, "THROW ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun ok(name: String, value: Any?) = log(name, "OK $value")

    private fun fail(name: String, e: Exception) = log(name, "FAIL ${e.javaClass.simpleName}: ${e.message}")

    private fun log(name: String, message: String) {
        Log.i("SMOKE", "$name -> $message")
        runOnUiThread { output.append("$name -> $message\n") }
    }
}
