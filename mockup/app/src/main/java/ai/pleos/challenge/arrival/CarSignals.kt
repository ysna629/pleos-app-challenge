package ai.pleos.challenge.arrival

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log

/**
 * Vehicle signals the app reacts to:
 * - gear (Pleos Gear API: deprecated, but the AAOS GEAR_SELECTION callback does not follow
 *   the Virtual Console gear on the emulator)
 * - odometer (Pleos Odometer.registerDistanceDriven)
 * - current address (NaviHelper), used as departure/arrival place
 * - GPS position (FusedLocation), turned into a map link for the parking spot
 */
class CarSignals(
    context: Context,
    private val onGear: (parked: Boolean) -> Unit,
    private val onOdometer: (km: Float) -> Unit,
) {
    private val vehicle = Vehicle(context)
    private val navi = NaviHelper(context)
    private var lastAddress = ""
    private var pendingAddress: ((String) -> Unit)? = null
    private val fused = FusedLocation(context)

    /** Latest GPS fix. Kept while driving so a car park without signal still gets the position at its entrance. */
    var lastFix: LocationData? = null
        private set

    @Suppress("DEPRECATION")
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (gear != null && gear != VehicleGear.GEAR_UNKNOWN) onGear(gear == VehicleGear.GEAR_PARK)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear", e)
        }
    }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            value?.let(onOdometer)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "odometer", e)
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            lastAddress = info.address
            pendingAddress?.invoke(info.address)
            pendingAddress = null
        }
    }

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location != null) lastFix = location
        }

        override fun onError(e: Exception) {
            Log.w(TAG, "location", e)
        }
    }

    fun start() {
        vehicle.initialize()
        runCatching { fused.initialize() }.onFailure { Log.w(TAG, "fused init", it) }
        navi.initialize({ navi.addListener(naviListener) }, { Log.w(TAG, "navi init", it) })
    }

    /** The Vehicle Service binds asynchronously, so listeners are registered once it is ready. */
    @Suppress("DEPRECATION")
    fun startListeners() {
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getOdometer().registerDistanceDriven(odometerListener)
        startLocation()
    }

    /** Needs the runtime FUSED_LOCATION permission; called again once it is granted. */
    fun startLocation() {
        runCatching {
            fused.getLastKnownLocation(fusedListener)
            fused.registerFusedLocationCallback(fusedListener)
        }.onFailure { Log.w(TAG, "location", it) }
    }

    /** Asks NaviHelper for the current address; falls back to the last known one. */
    fun requestAddress(callback: (String) -> Unit) {
        pendingAddress = callback
        runCatching { navi.getCurrentLocationInfo() }.onFailure { callback(lastAddress) }
    }

    fun stop() {
        @Suppress("DEPRECATION")
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getOdometer().unregisterDistanceDriven(odometerListener) }
        runCatching { fused.unregisterFusedLocationCallback(fusedListener) }
        runCatching { fused.release() }
        runCatching { navi.release() }
        runCatching { vehicle.release() }
    }

    private companion object {
        const val TAG = "CarSignals"
    }
}
