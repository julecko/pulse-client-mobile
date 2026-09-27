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
    @SerialName("occurred_at") val occurredAt: String
) {
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
