package sk.dilino.pulseclientmobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `protocol::AgentSummary` — returned by `GET /agents`. */
@Serializable
data class AgentSummary(
    val id: Long,
    val fingerprint: String,
    val hostname: String,
    val status: String,
    @SerialName("created_at") val createdAt: String
) {
    val lifecycle: AgentLifecycle
        get() = when (status) {
            "approved" -> AgentLifecycle.APPROVED
            "revoked" -> AgentLifecycle.REVOKED
            else -> AgentLifecycle.PENDING
        }
}

enum class AgentLifecycle { PENDING, APPROVED, REVOKED }

/** Mirrors `protocol::AuthEventRecord` — returned by `GET /agents/{id}/auth-events`. */
@Serializable
data class AuthEventRecord(
    val id: Long,
    val kind: String,
    val service: String,
    val user: String,
    val ruser: String? = null,
    val rhost: String? = null,
    val tty: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    /** Where `rhost` is, if it's a public IP the server's GeoIP database knows (ISO code, e.g. `SK`). */
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val city: String? = null
) {
    /** "Bratislava, Slovakia", falling back to whatever parts the server knows. */
    val location: String?
        get() = listOfNotNull(city, countryName ?: countryCode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifEmpty { null }

    val eventKind: AuthEventKind
        get() = when (kind) {
            "session_open" -> AuthEventKind.SESSION_OPEN
            "session_close" -> AuthEventKind.SESSION_CLOSE
            "auth_failure" -> AuthEventKind.AUTH_FAILURE
            else -> AuthEventKind.SESSION_CLOSE
        }
}

enum class AuthEventKind { SESSION_OPEN, SESSION_CLOSE, AUTH_FAILURE }

/** Mirrors `protocol::LoginRequest` — sent to `POST /auth/login`. */
@Serializable
data class LoginRequest(val username: String, val password: String)

/** Mirrors `protocol::LoginResponse` — `token` goes in `Authorization: Bearer` on user routes. */
@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String
)

/** Mirrors `protocol::MetricsRecord` — returned by `GET /agents/{id}/metrics` (newest first). */
@Serializable
data class MetricsRecord(
    val id: Long,
    @SerialName("created_at") val createdAt: String,
    val metrics: Metrics
)

@Serializable
data class Metrics(
    val cpu: CpuInfo? = null,
    val memory: MemoryInfo? = null,
    val disks: List<DiskInfo> = emptyList(),
    val linux: LinuxInfo? = null,
    /** Null for agents too old to report it. */
    val network: NetworkInfo? = null
)

@Serializable
data class CpuInfo(
    @SerialName("global_usage_percent") val globalUsagePercent: Float,
    @SerialName("per_core_usage_percent") val perCoreUsagePercent: List<Float> = emptyList(),
    @SerialName("core_count") val coreCount: Int = 0
)

@Serializable
data class MemoryInfo(
    @SerialName("total_bytes") val totalBytes: Long,
    @SerialName("used_bytes") val usedBytes: Long,
    @SerialName("free_bytes") val freeBytes: Long = 0,
    @SerialName("swap_total_bytes") val swapTotalBytes: Long = 0,
    @SerialName("swap_used_bytes") val swapUsedBytes: Long = 0
)

@Serializable
data class DiskInfo(
    val name: String,
    @SerialName("mount_point") val mountPoint: String,
    @SerialName("file_system") val fileSystem: String = "",
    @SerialName("total_bytes") val totalBytes: Long,
    @SerialName("available_bytes") val availableBytes: Long,
    val removable: Boolean = false
) {
    val usedBytes get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedPercent get() = if (totalBytes > 0) usedBytes * 100f / totalBytes else 0f
}

@Serializable
data class LinuxInfo(
    @SerialName("load_avg_one") val loadAvgOne: Double,
    @SerialName("load_avg_five") val loadAvgFive: Double,
    @SerialName("load_avg_fifteen") val loadAvgFifteen: Double,
    @SerialName("uptime_secs") val uptimeSecs: Long
)

/** Mirrors `protocol::NetworkInfo` — traffic averaged over the time since the previous snapshot. */
@Serializable
data class NetworkInfo(
    /** Received over all [interfaces], bytes per second. */
    @SerialName("rx_bytes_per_sec") val rxBytesPerSec: Double,
    /** Transmitted over all [interfaces], bytes per second. */
    @SerialName("tx_bytes_per_sec") val txBytesPerSec: Double,
    /** Real interfaces only: loopback and container/VM ones aren't reported. */
    val interfaces: List<NetworkInterfaceInfo> = emptyList()
)

@Serializable
data class NetworkInterfaceInfo(
    val name: String,
    @SerialName("rx_bytes_per_sec") val rxBytesPerSec: Double,
    @SerialName("tx_bytes_per_sec") val txBytesPerSec: Double,
    @SerialName("total_rx_bytes") val totalRxBytes: Long = 0,
    @SerialName("total_tx_bytes") val totalTxBytes: Long = 0
)

/** Mirrors `protocol::UserInfo` — returned by `GET /users/me`. */
@Serializable
data class UserInfo(
    val id: Long,
    val username: String,
    @SerialName("created_at") val createdAt: String
)

/** Mirrors `protocol::PairingStatus` — returned by `GET/PUT /agents/pairing`. */
@Serializable
data class PairingStatus(
    val open: Boolean,
    /** UTC `YYYY-MM-DD HH:MM:SS` when an open window closes by itself; null if open-ended or closed. */
    @SerialName("open_until") val openUntil: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String = ""
)

/** Mirrors `protocol::SetPairingRequest` — sent to `PUT /agents/pairing`. */
@Serializable
data class SetPairingRequest(val open: Boolean, val minutes: Int? = null)

/** Mirrors `protocol::OfflineAlertSetting` — returned by `GET /agents/offline-alerts` and `PUT /agents/{id}/offline-alert`. */
@Serializable
data class OfflineAlertSetting(
    @SerialName("agent_id") val agentId: Long,
    /** Seconds without metrics before the agent counts as offline; null when not watched (the default). */
    @SerialName("after_secs") val afterSecs: Long? = null,
    /** UTC `YYYY-MM-DD HH:MM:SS`; null if it never sent any. */
    @SerialName("last_metrics_at") val lastMetricsAt: String? = null,
    /** It has an active offline alert: quiet for longer than [afterSecs] and hasn't sent metrics since. */
    val offline: Boolean = false
)

/** Mirrors `protocol::SetOfflineAlert` — sent to `PUT /agents/{id}/offline-alert`; null turns it off. */
@Serializable
data class SetOfflineAlert(@SerialName("after_secs") val afterSecs: Long?)

// ---------------------------------------------------------------- alerts

/** Mirrors `protocol::AlertMetric` — a value an [AlertRule] compares against a threshold. */
enum class AlertMetric(val wire: String, val label: String, val unit: String) {
    CPU_USAGE_PERCENT("cpu_usage_percent", "CPU usage", "%"),
    MEMORY_USED_PERCENT("memory_used_percent", "Memory used", "%"),
    SWAP_USED_PERCENT("swap_used_percent", "Swap used", "%"),
    DISK_USED_PERCENT("disk_used_percent", "Disk used (fullest)", "%"),
    LOAD_AVG_ONE("load_avg_one", "Load average, 1 min", ""),
    LOAD_AVG_FIVE("load_avg_five", "Load average, 5 min", ""),
    LOAD_AVG_FIFTEEN("load_avg_fifteen", "Load average, 15 min", ""),
    NETWORK_RX_MBPS("network_rx_mbps", "Network in", "Mbit/s"),
    NETWORK_TX_MBPS("network_tx_mbps", "Network out", "Mbit/s");

    companion object {
        fun fromWire(s: String): AlertMetric = entries.firstOrNull { it.wire == s } ?: CPU_USAGE_PERCENT
    }
}

/** Mirrors `protocol::AlertOperator` — JSON is the symbol itself (">", ">=", "<", "<="). */
enum class AlertOperator(val wire: String, val symbol: String) {
    GT(">", ">"),
    GE(">=", "≥"),
    LT("<", "<"),
    LE("<=", "≤");

    companion object {
        fun fromWire(s: String): AlertOperator = entries.firstOrNull { it.wire == s } ?: GT
    }
}

/** Mirrors `protocol::AlertSeverity`. */
enum class AlertSeverity(val wire: String, val label: String) {
    INFO("info", "INFO"),
    WARNING("warning", "WARNING"),
    CRITICAL("critical", "CRITICAL");

    companion object {
        fun fromWire(s: String): AlertSeverity = entries.firstOrNull { it.wire == s } ?: WARNING
    }
}

/** Mirrors `protocol::AlertRule` — returned by `GET/POST /alert-rules` and `PATCH /alert-rules/{id}`. */
@Serializable
data class AlertRule(
    val id: Long,
    val name: String,
    /** The agent this rule watches; null = every agent. */
    @SerialName("agent_id") val agentId: Long? = null,
    val metric: String,
    val operator: String,
    val threshold: Double,
    @SerialName("duration_secs") val durationSecs: Int = 0,
    val severity: String = "warning",
    val notify: Boolean = false,
    val enabled: Boolean = true,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
) {
    val metricEnum: AlertMetric get() = AlertMetric.fromWire(metric)
    val operatorEnum: AlertOperator get() = AlertOperator.fromWire(operator)
    val severityEnum: AlertSeverity get() = AlertSeverity.fromWire(severity)
}

/** Mirrors `protocol::NewAlertRule` — sent to `POST /alert-rules`. */
@Serializable
data class NewAlertRule(
    val name: String,
    @SerialName("agent_id") val agentId: Long? = null,
    val metric: String,
    val operator: String,
    val threshold: Double,
    @SerialName("duration_secs") val durationSecs: Int = 0,
    val severity: String = "warning",
    val notify: Boolean = false
)

/** Mirrors `protocol::UpdateAlertRule` — sent to `PATCH /alert-rules/{id}`; unset fields stay as they are. */
@Serializable
data class UpdateAlertRule(val enabled: Boolean? = null, val notify: Boolean? = null)

/** Mirrors `protocol::AlertRecord` — returned by `GET /alerts`, newest first. */
@Serializable
data class AlertRecord(
    val id: Long,
    /** Null once the rule that raised it has been deleted. */
    @SerialName("rule_id") val ruleId: Long? = null,
    @SerialName("agent_id") val agentId: Long? = null,
    /** The agent's current hostname; null if this alert isn't about one agent. */
    val hostname: String? = null,
    val severity: String,
    val title: String,
    val message: String,
    @SerialName("triggered_at") val triggeredAt: String,
    /** Null while the condition still holds. */
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null
) {
    val severityEnum: AlertSeverity get() = AlertSeverity.fromWire(severity)
    val isActive: Boolean get() = resolvedAt == null
    val isAcknowledged: Boolean get() = acknowledgedAt != null
}

// ---------------------------------------------------------------- push devices

/** Mirrors `protocol::PushPlatform`. This app only ever registers `ANDROID`. */
enum class PushPlatform(val wire: String) { ANDROID("android"), IOS("ios") }

/** Mirrors `protocol::RegisterPushDevice` — sent to `POST /push-devices`. */
@Serializable
data class RegisterPushDevice(
    val token: String,
    val platform: String = PushPlatform.ANDROID.wire,
    val name: String? = null
)

/** Mirrors `protocol::PushDevice` — returned by `GET/POST /push-devices`. The token itself is never sent back. */
@Serializable
data class PushDevice(
    val id: Long,
    val username: String,
    val platform: String,
    val name: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String
)

/**
 * Mirrors `protocol::AppRelease` — a release of this app uploaded to the server with
 * `pulse-server-cli app upload`; returned by `GET /app-releases/latest`.
 */
@Serializable
data class AppRelease(
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String,
    val notes: String? = null,
    /** APK size in bytes. */
    val size: Long,
    /** SHA-256 of the APK, lowercase hex. */
    val sha256: String,
    @SerialName("uploaded_by") val uploadedBy: String? = null,
    @SerialName("created_at") val createdAt: String
)
