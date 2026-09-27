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
    /** Where [rhost] is, when it's a public IP the server's GeoIP database knows (ISO code, e.g. `SK`). */
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val city: String? = null
) {
    val eventKind: AuthEventKind get() = AuthEventKind.fromWire(kind) ?: AuthEventKind.SESSION_CLOSE

    /** "Bratislava, Slovakia (SK)", or null if the server didn't locate [rhost]. */
    val location: String? get() = formatLocation(city, countryName, countryCode)
}

/** Mirrors `protocol::AuthEventKind`. */
enum class AuthEventKind(val wire: String) {
    SESSION_OPEN("session_open"),
    SESSION_CLOSE("session_close"),
    AUTH_FAILURE("auth_failure");

    companion object {
        fun fromWire(s: String): AuthEventKind? = entries.firstOrNull { it.wire == s }
    }
}

/** "City, Country (CC)" from whichever parts are known; null if none are. */
fun formatLocation(city: String?, countryName: String?, countryCode: String?): String? {
    val country = when {
        countryName != null && countryCode != null -> "$countryName ($countryCode)"
        else -> countryName ?: countryCode
    }
    return listOfNotNull(city, country).joinToString(", ").ifEmpty { null }
}

/** Mirrors `protocol::PamNotifications` — returned by `GET /agents/pam-notifications` and `GET/PUT /agents/{id}/pam-notifications`. */
@Serializable
data class PamNotifications(
    @SerialName("agent_id") val agentId: Long,
    val hostname: String,
    /** Which of the agent's PAM events are pushed; empty = none (the default). */
    val kinds: List<String> = emptyList()
) {
    val kindSet: Set<AuthEventKind> get() = kinds.mapNotNull { AuthEventKind.fromWire(it) }.toSet()
}

/** Mirrors `protocol::SetPamNotifications` — sent to `PUT /agents/{id}/pam-notifications`; replaces the agent's kinds. */
@Serializable
data class SetPamNotifications(val kinds: List<String>)

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
    val linux: LinuxInfo? = null
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

// ---------------------------------------------------------------- alerts

/** Mirrors `protocol::AlertMetric` — a value an [AlertRule] compares against a threshold. */
enum class AlertMetric(val wire: String, val label: String, val unit: String) {
    CPU_USAGE_PERCENT("cpu_usage_percent", "CPU usage", "%"),
    MEMORY_USED_PERCENT("memory_used_percent", "Memory used", "%"),
    SWAP_USED_PERCENT("swap_used_percent", "Swap used", "%"),
    DISK_USED_PERCENT("disk_used_percent", "Disk used (fullest)", "%"),
    LOAD_AVG_ONE("load_avg_one", "Load average, 1 min", ""),
    LOAD_AVG_FIVE("load_avg_five", "Load average, 5 min", ""),
    LOAD_AVG_FIFTEEN("load_avg_fifteen", "Load average, 15 min", "");

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
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null,
    /** Set for geo alerts (an SSH login from a country that isn't allowed); they resolve when acknowledged. */
    val geo: GeoAlertInfo? = null
) {
    val severityEnum: AlertSeverity get() = AlertSeverity.fromWire(severity)
    val isActive: Boolean get() = resolvedAt == null
    val isAcknowledged: Boolean get() = acknowledgedAt != null

    /**
     * What raised this alert. Geo alerts carry [geo]; offline alerts have no rule and no marker of
     * their own on the wire, so they're told apart by the title the server gives them
     * (`crates/serverd/src/offline.rs`).
     */
    val source: AlertSource
        get() = when {
            geo != null -> AlertSource.GEO
            ruleId != null -> AlertSource.RULE
            agentId != null && title.endsWith(" stopped sending metrics") -> AlertSource.OFFLINE
            else -> AlertSource.DELETED_RULE
        }
}

enum class AlertSource { RULE, DELETED_RULE, OFFLINE, GEO }

/** Mirrors `protocol::GeoAlertInfo` — what a geo alert is about. */
@Serializable
data class GeoAlertInfo(
    /** `session_open` (a successful login) or `auth_failure`. */
    val kind: String,
    val ip: String,
    val user: String,
    /** Null when the IP isn't in the server's GeoIP database. */
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val city: String? = null
) {
    val isFailure: Boolean get() = kind == AuthEventKind.AUTH_FAILURE.wire
    val location: String get() = formatLocation(city, countryName, countryCode) ?: "unknown location"
}

// ---------------------------------------------------------------- geo alert settings

/** Mirrors `protocol::GeoAlertSettings` — returned by `GET/PUT /geo-alerts/settings`. */
@Serializable
data class GeoAlertSettings(
    /** ISO codes, e.g. `["SK", "CZ"]`. Empty: geo alerts are off. */
    @SerialName("allowed_countries") val allowedCountries: List<String> = emptyList(),
    /** Also alert on failed SSH logins, not just successful ones. */
    @SerialName("include_failures") val includeFailures: Boolean = false,
    /** Push geo alerts to every registered device. */
    val notify: Boolean = true,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    /** The GeoIP database the server loaded; null means no lookups, so no geo alerts. */
    val database: GeoDatabaseInfo? = null
)

/** Mirrors `protocol::GeoDatabaseInfo`. */
@Serializable
data class GeoDatabaseInfo(
    val path: String,
    @SerialName("database_type") val databaseType: String,
    /** When MaxMind built it (UTC `YYYY-MM-DD HH:MM:SS`). */
    @SerialName("built_at") val builtAt: String
)

/** Mirrors `protocol::SetGeoAlertSettings` — sent to `PUT /geo-alerts/settings`; replaces all of them. */
@Serializable
data class SetGeoAlertSettings(
    @SerialName("allowed_countries") val allowedCountries: List<String>,
    @SerialName("include_failures") val includeFailures: Boolean,
    val notify: Boolean
)

// ---------------------------------------------------------------- offline alerts

/** Mirrors `protocol::MIN_OFFLINE_AFTER_SECS` / `MAX_OFFLINE_AFTER_SECS`. */
const val MIN_OFFLINE_AFTER_SECS = 60
const val MAX_OFFLINE_AFTER_SECS = 30 * 24 * 60 * 60

/** Mirrors `protocol::OfflineAlertSetting` — returned by `GET /agents/offline-alerts` and `PUT /agents/{id}/offline-alert`. */
@Serializable
data class OfflineAlertSetting(
    @SerialName("agent_id") val agentId: Long,
    val hostname: String,
    /** `pending`, `approved` or `revoked`; only approved agents are watched. */
    val status: String,
    /** Null: not watched (the default). */
    @SerialName("after_secs") val afterSecs: Int? = null,
    /** UTC `YYYY-MM-DD HH:MM:SS`; null if it never sent any. */
    @SerialName("last_metrics_at") val lastMetricsAt: String? = null,
    /** It has an active offline alert: quiet for longer than [afterSecs] and nothing since. */
    val offline: Boolean = false
)

/** Mirrors `protocol::SetOfflineAlert` — sent to `PUT /agents/{id}/offline-alert`; null turns it off. */
@Serializable
data class SetOfflineAlert(@SerialName("after_secs") val afterSecs: Int?)

// ---------------------------------------------------------------- retention

/** Mirrors `protocol::MAX_RETENTION_DAYS`; `0` keeps data forever. */
const val MAX_RETENTION_DAYS = 3650

/** Mirrors `protocol::RetentionData` — a kind of data the server deletes once it's old enough. */
enum class RetentionData(val wire: String, val label: String, val description: String) {
    METRICS("metrics", "METRICS", "Metrics snapshots, by when the server received them."),
    AUTH_EVENTS("auth_events", "AUTH EVENTS", "PAM login events, by when the server received them."),
    ALERTS("alerts", "ALERTS", "Resolved alerts, by when they resolved. Active alerts are always kept.");

    companion object {
        fun fromWire(s: String): RetentionData? = entries.firstOrNull { it.wire == s }
    }
}

/** Mirrors `protocol::RetentionSetting` — returned by `GET /retention` and `PUT /retention/{data}`. */
@Serializable
data class RetentionSetting(
    val data: String,
    /** In effect now; `0` = kept forever. */
    val days: Int,
    /** From the server config's `[retention]`, used when not overridden. */
    @SerialName("default_days") val defaultDays: Int,
    /** Set by a user (then [updatedBy]/[updatedAt] say who and when) rather than the config default. */
    val overridden: Boolean = false,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val dataEnum: RetentionData? get() = RetentionData.fromWire(data)
}

/** Mirrors `protocol::SetRetention` — sent to `PUT /retention/{data}`; null resets to the config default. */
@Serializable
data class SetRetention(val days: Int?)

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
