package sk.dilino.pulseclientmobile.util

import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting

/**
 * For a host without an offline alert limit on the server, how long without metrics before the app
 * shows it as offline anyway (it isn't alerted on, just shown).
 */
const val DEFAULT_OFFLINE_AFTER_SECS = 5 * 60L

/**
 * Whether to show the host as offline: the server's offline alert when a limit is set, else no
 * metrics for [DEFAULT_OFFLINE_AFTER_SECS] (by this device's clock). A host that never sent metrics
 * isn't offline, just waiting for its first report.
 */
fun OfflineAlertSetting.isOffline(nowMs: Long = System.currentTimeMillis()): Boolean {
    if (afterSecs != null) return offline
    val last = lastMetricsAt?.let(::parseServerMillis) ?: return false
    return nowMs - last > DEFAULT_OFFLINE_AFTER_SECS * 1000
}
