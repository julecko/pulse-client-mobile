package sk.dilino.pulseclientmobile.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.AlertRecord
import sk.dilino.pulseclientmobile.data.model.AlertRule
import sk.dilino.pulseclientmobile.data.model.AuthEventKind
import sk.dilino.pulseclientmobile.data.model.AuthEventRecord
import sk.dilino.pulseclientmobile.data.model.GeoAlertSettings
import sk.dilino.pulseclientmobile.data.model.LoginRequest
import sk.dilino.pulseclientmobile.data.model.LoginResponse
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.data.model.NewAlertRule
import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting
import sk.dilino.pulseclientmobile.data.model.PairingStatus
import sk.dilino.pulseclientmobile.data.model.PamNotifications
import sk.dilino.pulseclientmobile.data.model.PushDevice
import sk.dilino.pulseclientmobile.data.model.RegisterPushDevice
import sk.dilino.pulseclientmobile.data.model.RetentionData
import sk.dilino.pulseclientmobile.data.model.RetentionSetting
import sk.dilino.pulseclientmobile.data.model.SetGeoAlertSettings
import sk.dilino.pulseclientmobile.data.model.SetOfflineAlert
import sk.dilino.pulseclientmobile.data.model.SetPairingRequest
import sk.dilino.pulseclientmobile.data.model.SetPamNotifications
import sk.dilino.pulseclientmobile.data.model.SetRetention
import sk.dilino.pulseclientmobile.data.model.UpdateAlertRule
import sk.dilino.pulseclientmobile.data.model.UserInfo

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val JSON_MEDIA = "application/json".toMediaType()

/** A non-success HTTP response; [message] is the server's own explanation when it sent one. */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Talks to the `pulse` server. `/healthz` and `/auth/login` are public; everything else needs the
 * session token from login, which is fetched lazily and refreshed once on a 401.
 *
 * The server rate-limits failed logins per IP (429 + `Retry-After`) and successful ones don't
 * count, so re-logging in is cheap — but a saved password that has stopped working must not be
 * retried in a loop. After the server rejects the credentials this client stops trying.
 *
 * TLS is always verified: public CAs by default, or the single certificate in [pinnedCertSha256]
 * (see [buildHttpClient]).
 */
class PulseApiClient(
    rawBaseUrl: String,
    private val username: String,
    private val password: String,
    pinnedCertSha256: String? = null
) {

    private val baseUrl: String = rawBaseUrl.trimEnd('/')
    private val client: OkHttpClient = buildHttpClient(pinnedCertSha256)

    @Volatile private var token: String? = null
    @Volatile private var credentialsRejected = false
    @Volatile private var loginRetryAtMs = 0L

    suspend fun healthz(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/healthz").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toException()
            }
        }
    }

    /** Verifies the username/password against the server, caching the session token. */
    suspend fun login(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { loginBlocking(); Unit }
    }

    /** Ends the server-side session. Best effort: it expires on its own anyway. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        if (token == null) return@withContext
        runCatching { authed("/auth/logout", { post("".toRequestBody(JSON_MEDIA)) }) { } }
        token = null
    }

    @Synchronized
    private fun loginBlocking(): String {
        if (credentialsRejected) {
            throw ApiException(401, "Saved credentials were rejected — sign out in Settings and sign in again")
        }
        val waitMs = loginRetryAtMs - System.currentTimeMillis()
        if (waitMs > 0) throw ApiException(429, "Too many failed sign-in attempts; retry in ${(waitMs + 999) / 1000}s")

        val payload = json.encodeToString(LoginRequest(username, password))
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> {
                    credentialsRejected = true
                    throw ApiException(401, "Invalid username or password")
                }
                429 -> {
                    val secs = response.header("Retry-After")?.toLongOrNull() ?: 60L
                    loginRetryAtMs = System.currentTimeMillis() + secs * 1000
                    throw ApiException(429, "Too many failed sign-in attempts; retry in ${secs}s")
                }
            }
            if (!response.isSuccessful) throw response.toException()
            return json.decodeFromString<LoginResponse>(response.body?.string().orEmpty()).token.also { token = it }
        }
    }

    /** Sends an authenticated request, logging in first and once more if the session was rejected. */
    private fun <T> authed(path: String, configure: Request.Builder.() -> Unit = {}, handle: (Response) -> T): T {
        for (attempt in 0..1) {
            val bearer = token ?: loginBlocking()
            val request = Request.Builder()
                .url("$baseUrl$path")
                .header("Authorization", "Bearer $bearer")
                .apply(configure)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 && attempt == 0) {
                    token = null
                } else {
                    if (!response.isSuccessful) throw response.toException()
                    return handle(response)
                }
            }
        }
        throw ApiException(401, "Not signed in")
    }

    private fun Response.toException(): ApiException {
        val text = runCatching { body?.string() }.getOrNull()?.trim().orEmpty()
        return ApiException(code, if (text.isNotEmpty() && text.length <= 300) text else "HTTP $code")
    }

    suspend fun listAgents(): Result<List<AgentSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents") { response ->
                json.decodeFromString<List<AgentSummary>>(response.body?.string().orEmpty())
            }
        }
    }

    suspend fun authEvents(agentId: Long): Result<List<AuthEventRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId/auth-events") { response ->
                json.decodeFromString<List<AuthEventRecord>>(response.body?.string().orEmpty())
            }
        }
    }

    /** Most recent snapshots for one agent, returned oldest → newest. */
    suspend fun metrics(agentId: Long, limit: Int = 96): Result<List<MetricsRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId/metrics?limit=$limit") { response ->
                json.decodeFromString<List<MetricsRecord>>(response.body?.string().orEmpty()).asReversed()
            }
        }
    }

    /** 204 — the agent's own secret becomes its token; nothing is returned. Revoked agents get 409. */
    suspend fun approveAgent(agentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId/approve", { post("".toRequestBody(JSON_MEDIA)) }) { }
        }
    }

    suspend fun revokeAgent(agentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId/revoke", { post("".toRequestBody(JSON_MEDIA)) }) { }
        }
    }

    suspend fun removeAgent(agentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId", { delete() }) { }
        }
    }

    /** Undoes a revoke: the agent's existing secret is trusted again. Only agents that are actually revoked can be unrevoked. */
    suspend fun unrevokeAgent(agentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId/unrevoke", { post("".toRequestBody(JSON_MEDIA)) }) { }
        }
    }

    suspend fun me(): Result<UserInfo> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/users/me") { response ->
                json.decodeFromString<UserInfo>(response.body?.string().orEmpty())
            }
        }
    }

    suspend fun pairingStatus(): Result<PairingStatus> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/pairing") { response ->
                json.decodeFromString<PairingStatus>(response.body?.string().orEmpty())
            }
        }
    }

    /** Opens pairing (for [minutes], or until closed if null) or closes it. */
    suspend fun setPairing(open: Boolean, minutes: Int? = null): Result<PairingStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(SetPairingRequest(open, if (open) minutes else null))
            authed("/agents/pairing", { put(body.toRequestBody(JSON_MEDIA)) }) { response ->
                json.decodeFromString<PairingStatus>(response.body?.string().orEmpty())
            }
        }
    }

    // ------------------------------------------------------------ alert rules

    suspend fun alertRules(): Result<List<AlertRule>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/alert-rules") { response ->
                json.decodeFromString<List<AlertRule>>(response.body?.string().orEmpty())
            }
        }
    }

    suspend fun createAlertRule(rule: NewAlertRule): Result<AlertRule> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(rule)
            authed("/alert-rules", { post(body.toRequestBody(JSON_MEDIA)) }) { response ->
                json.decodeFromString<AlertRule>(response.body?.string().orEmpty())
            }
        }
    }

    /** Enables/disables a rule and/or toggles its push notifications; unset fields stay as they are. */
    suspend fun updateAlertRule(ruleId: Long, enabled: Boolean? = null, notify: Boolean? = null): Result<AlertRule> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(UpdateAlertRule(enabled, notify))
                authed("/alert-rules/$ruleId", { patch(body.toRequestBody(JSON_MEDIA)) }) { response ->
                    json.decodeFromString<AlertRule>(response.body?.string().orEmpty())
                }
            }
        }

    suspend fun deleteAlertRule(ruleId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/alert-rules/$ruleId", { delete() }) { }
        }
    }

    // ------------------------------------------------------------ alerts

    /** Most recent alerts, newest first. [agentId] and [activeOnly] filter server-side. */
    suspend fun alerts(agentId: Long? = null, activeOnly: Boolean = false, limit: Int = 50): Result<List<AlertRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = buildString {
                    append("?limit=$limit")
                    if (agentId != null) append("&agent_id=$agentId")
                    if (activeOnly) append("&active=true")
                }
                authed("/alerts$query") { response ->
                    json.decodeFromString<List<AlertRecord>>(response.body?.string().orEmpty())
                }
            }
        }

    suspend fun acknowledgeAlert(alertId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/alerts/$alertId/acknowledge", { post("".toRequestBody(JSON_MEDIA)) }) { }
        }
    }

    // ------------------------------------------------------------ push devices

    suspend fun pushDevices(): Result<List<PushDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/push-devices") { response ->
                json.decodeFromString<List<PushDevice>>(response.body?.string().orEmpty())
            }
        }
    }

    /** Registers this device's FCM token, or refreshes it if already known. */
    suspend fun registerPushDevice(token: String, name: String? = null): Result<PushDevice> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(RegisterPushDevice(token = token, name = name))
            authed("/push-devices", { post(body.toRequestBody(JSON_MEDIA)) }) { response ->
                json.decodeFromString<PushDevice>(response.body?.string().orEmpty())
            }
        }
    }

    suspend fun removePushDevice(deviceId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/push-devices/$deviceId", { delete() }) { }
        }
    }

    // ------------------------------------------------------------ offline alerts

    /** Every agent's offline-alert limit and whether it's offline right now. */
    suspend fun offlineAlerts(): Result<List<OfflineAlertSetting>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/offline-alerts") { response ->
                json.decodeFromString<List<OfflineAlertSetting>>(response.body?.string().orEmpty())
            }
        }
    }

    /** Raises an alert once the agent sends no metrics for [afterSecs]; null turns it off (and resolves an active one). */
    suspend fun setOfflineAlert(agentId: Long, afterSecs: Int?): Result<OfflineAlertSetting> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(SetOfflineAlert(afterSecs))
            authed("/agents/$agentId/offline-alert", { put(body.toRequestBody(JSON_MEDIA)) }) { response ->
                json.decodeFromString<OfflineAlertSetting>(response.body?.string().orEmpty())
            }
        }
    }

    // ------------------------------------------------------------ PAM push settings

    suspend fun pamNotifications(agentId: Long): Result<PamNotifications> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId/pam-notifications") { response ->
                json.decodeFromString<PamNotifications>(response.body?.string().orEmpty())
            }
        }
    }

    /** Replaces which of the agent's PAM events are pushed; an empty set turns them off. */
    suspend fun setPamNotifications(agentId: Long, kinds: Set<AuthEventKind>): Result<PamNotifications> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Server order, so the request reads the same whatever order they were tapped in.
                val wire = AuthEventKind.entries.filter { it in kinds }.map { it.wire }
                val body = json.encodeToString(SetPamNotifications(wire))
                authed("/agents/$agentId/pam-notifications", { put(body.toRequestBody(JSON_MEDIA)) }) { response ->
                    json.decodeFromString<PamNotifications>(response.body?.string().orEmpty())
                }
            }
        }

    // ------------------------------------------------------------ geo alerts

    suspend fun geoAlertSettings(): Result<GeoAlertSettings> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/geo-alerts/settings") { response ->
                json.decodeFromString<GeoAlertSettings>(response.body?.string().orEmpty())
            }
        }
    }

    /** Replaces all geo alert settings; an empty [allowedCountries] turns geo alerts off. */
    suspend fun setGeoAlertSettings(allowedCountries: List<String>, includeFailures: Boolean, notify: Boolean): Result<GeoAlertSettings> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(SetGeoAlertSettings(allowedCountries, includeFailures, notify))
                authed("/geo-alerts/settings", { put(body.toRequestBody(JSON_MEDIA)) }) { response ->
                    json.decodeFromString<GeoAlertSettings>(response.body?.string().orEmpty())
                }
            }
        }

    // ------------------------------------------------------------ retention

    suspend fun retention(): Result<List<RetentionSetting>> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/retention") { response ->
                json.decodeFromString<List<RetentionSetting>>(response.body?.string().orEmpty())
            }
        }
    }

    /** Keeps [data] for [days] (0 = forever), or with null resets it to the server config's default. Lowering it deletes older data right away. */
    suspend fun setRetention(data: RetentionData, days: Int?): Result<RetentionSetting> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(SetRetention(days))
            authed("/retention/${data.wire}", { put(body.toRequestBody(JSON_MEDIA)) }) { response ->
                json.decodeFromString<RetentionSetting>(response.body?.string().orEmpty())
            }
        }
    }
}
