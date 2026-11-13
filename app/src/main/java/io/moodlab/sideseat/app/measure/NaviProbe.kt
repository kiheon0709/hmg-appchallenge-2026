package io.moodlab.sideseat.app.measure

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * 디버그 실측 전용: NaviHelper 주행 콜백이 에뮬레이터에서 실제로 오는지, 값과 주기를 로그로 남긴다(기능 아님).
 * `adb shell am broadcast -a io.moodlab.sideseat.DEBUG --es cmd navi --es arg "start|route <lat>,<lon>|trip <lat>,<lon>|get|reroute|cancel|stop"`
 */
class NaviProbe(private val context: Context) {
    private var navi: NaviHelper? = null
    private val counts = mutableMapOf<String, Int>()
    private val lastAt = mutableMapOf<String, Long>()

    private fun log(name: String, value: Any?) {
        val now = SystemClock.elapsedRealtime()
        val gap = lastAt[name]?.let { now - it }
        lastAt[name] = now
        counts[name] = (counts[name] ?: 0) + 1
        Log.i(MEASURE_TAG, "t=$now navi $name #${counts[name]} gap=${gap ?: "-"}ms ${value.toString().take(600)}")
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = log("onRouteStarted", info)
        override fun onRoutingCompleted() = log("onRoutingCompleted", "")
        override fun onReRouteEnded() = log("onReRouteEnded", "")
        override fun onRouteOptionChanged(option: RouteOption) = log("onRouteOptionChanged", option)
        override fun onRouteCancelled() = log("onRouteCancelled", "")
        override fun onWaypointChanged(info: WaypointChangedInfo) = log("onWaypointChanged", info)
        override fun onDestinationArrived(info: DestinationArrivedInfo) = log("onDestinationArrived", info)
        override fun onWayPointArrived(info: WaypointArrivedInfo) = log("onWayPointArrived", info)
        override fun onDestinationChanged() = log("onDestinationChanged", "")
        override fun onError(errorCode: NaviErrorCode) = log("onError", errorCode)
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = log("onCurrentLocationInfo", info)
        override fun onRouteStateInfo(info: RouteStateInfo) = log("onRouteStateInfo", info)
        override fun onDestinationInfo(info: DestinationInfo) = log("onDestinationInfo", info)
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = log("onCurrentRoadSpeedLimit", speedLimit)
        override fun onCameraAlert(alerts: List<ai.pleos.playground.navi.data.CameraAlert>) = log("onCameraAlert", alerts)
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("onDrivingInfo", drivingInfo)
        override fun onNavigationStatus(status: NavigationStatus) = log("onNavigationStatus", status)
        override fun onTBTInfo(tbtInfo: List<ai.pleos.playground.navi.data.TBTInfo>) = log("onTBTInfo", tbtInfo)
        override fun onCautionPointInfo(infos: List<ai.pleos.playground.navi.data.CautionPoint>) = log("onCautionPointInfo", infos)
        override fun onTripInfo(tripInfo: TripInfo) = log("onTripInfo", tripInfo)
    }

    fun command(arg: String) {
        val parts = arg.trim().split(" ", limit = 2)
        val latLon = parts.getOrNull(1)?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }
        runCatching {
            when (parts[0]) {
                "start" -> if (navi == null) {
                    navi = NaviHelper(context).also {
                        it.initialize({ Log.i(MEASURE_TAG, "navi probe connected") }, { e -> Log.w(MEASURE_TAG, "navi probe init failed $e") })
                        it.addListener(listener)
                    }
                }
                "route" -> navi?.requestRoute(RouteInfo(latLon!![0], latLon[1], null, null, "probe", "", "", "", RouteOption.RECOMMENDED, "", false))
                "trip" -> navi?.requestTrip(TripRequest(latLon!![0], latLon[1], "", "probe", ""))
                "get" -> navi?.run { getRouteStateInfo(); getCurrentLocationInfo(); getDestinationInfo(); getTBTInfo() }
                "reroute" -> navi?.requestReRoute()
                "cancel" -> navi?.cancelRoute()
                "stop" -> {
                    navi?.removeListener(listener); navi?.release(); navi = null
                    Log.i(MEASURE_TAG, "navi probe counts=$counts")
                }
            }
        }.onFailure { Log.w(MEASURE_TAG, "navi probe $arg failed: $it") }
        Log.i(MEASURE_TAG, "navi probe cmd '$arg' done")
    }
}
