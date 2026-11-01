package ai.pleos.challenge.arrival

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

    fun start() {
        vehicle.initialize()
        navi.initialize({ navi.addListener(naviListener) }, { Log.w(TAG, "navi init", it) })
    }

    /** The Vehicle Service binds asynchronously, so listeners are registered once it is ready. */
    @Suppress("DEPRECATION")
    fun startListeners() {
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getOdometer().registerDistanceDriven(odometerListener)
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
        runCatching { navi.release() }
        runCatching { vehicle.release() }
    }

    private companion object {
        const val TAG = "CarSignals"
    }
}
