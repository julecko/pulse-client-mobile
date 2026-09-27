package sk.dilino.pulseclientmobile.util

import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.ui.components.Severity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Same thresholds as the reference design: >88 critical, >74 warning. */
fun severityOf(percent: Float?): Severity = when {
    percent == null -> Severity.HEALTHY
    percent > 88f -> Severity.CRITICAL
    percent > 74f -> Severity.WARNING
    else -> Severity.HEALTHY
}

fun worst(vararg s: Severity): Severity = s.maxByOrNull { it.ordinal } ?: Severity.HEALTHY

val MetricsRecord.cpuPercent: Float? get() = metrics.cpu?.globalUsagePercent

val MetricsRecord.memPercent: Float?
    get() = metrics.memory?.takeIf { it.totalBytes > 0 }?.let { it.usedBytes * 100f / it.totalBytes }

/** The fullest non-removable filesystem, since that's the one that pages someone. */
val MetricsRecord.diskPercent: Float?
    get() {
        val disks = metrics.disks.filter { it.totalBytes > 0 }
        val fixed = disks.filter { !it.removable }.ifEmpty { disks }
        return fixed.maxOfOrNull { it.usedPercent }
    }

/** Network traffic in megabits per second, the unit alert rules use; null if the agent didn't report it. */
val MetricsRecord.netInMbps: Float? get() = metrics.network?.let { (it.rxBytesPerSec * 8 / 1_000_000).toFloat() }
val MetricsRecord.netOutMbps: Float? get() = metrics.network?.let { (it.txBytesPerSec * 8 / 1_000_000).toFloat() }

val MetricsRecord.severity: Severity
    get() = worst(severityOf(cpuPercent), severityOf(memPercent), severityOf(diskPercent))

fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "${bytes} B" else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

/** Bytes per second as "1.2 MB/s". */
fun formatRate(bytesPerSec: Double): String = formatBytes(bytesPerSec.toLong().coerceAtLeast(0)) + "/s"

fun formatUptime(secs: Long): String {
    val d = secs / 86400
    val h = (secs % 86400) / 3600
    val m = (secs % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h.toString().padStart(2, '0')}h"
        h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        else -> "${m}m"
    }
}

fun formatPercent(p: Float?): String = if (p == null) "—" else "${p.toInt()}%"

private fun serverFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/** Server timestamps are UTC `YYYY-MM-DD HH:MM:SS`; returns epoch millis or null if malformed. */
fun parseServerMillis(raw: String): Long? = runCatching { serverFormat().parse(raw)?.time }.getOrNull()

/** HH:mm in the device's time zone. */
fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

/** "LIVE" for the newest snapshot, otherwise "−1h 15m" style offset behind it. */
fun formatRelative(deltaMs: Long): String {
    val mins = abs(deltaMs) / 60_000
    if (mins == 0L) return "LIVE"
    val h = mins / 60
    return "−" + (if (h > 0) "${h}h " else "") + "${mins % 60}m"
}

fun formatSigned(v: Float, suffix: String = ""): String {
    val r = Math.round(v * 10) / 10f
    return when {
        r > 0 -> "+" + String.format(Locale.US, "%.1f", r) + suffix
        r < 0 -> "−" + String.format(Locale.US, "%.1f", abs(r)) + suffix
        else -> "0.0$suffix"
    }
}
