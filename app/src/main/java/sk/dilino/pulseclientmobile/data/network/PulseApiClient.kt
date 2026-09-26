package sk.dilino.pulseclientmobile.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.ApproveResponse
import sk.dilino.pulseclientmobile.data.model.AuthEventRecord
import sk.dilino.pulseclientmobile.data.model.LoginRequest
import sk.dilino.pulseclientmobile.data.model.LoginResponse
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Talks to the user-facing `pulse` server endpoints: `/healthz` and `/auth/login`
 * (public), then `/agents`, `/agents/{id}/approve`, `/agents/{id}/revoke`,
 * `/agents/{id}` (delete), `/agents/{id}/auth-events` behind the session token
 * from login. The token is fetched lazily and refreshed once on a 401.
 *
 * The dev server (see crates/server README) serves HTTPS with a self-signed
 * certificate, so this client trusts any cert — there's no settings/pairing
 * UI yet to manage a pinned CA, and this is an admin tool for one's own host.
 */
class PulseApiClient(
    rawBaseUrl: String,
    private val username: String,
    private val password: String
) {

    private val baseUrl: String = rawBaseUrl.trimEnd('/')

    @Volatile
    private var token: String? = null

    private val client: OkHttpClient = run {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun healthz(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/healthz").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
            }
        }
    }

    /** Verifies the username/password against the server, caching the session token. */
    suspend fun login(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { loginBlocking(); Unit }
    }

    @Synchronized
    private fun loginBlocking(): String {
        val payload = json.encodeToString(LoginRequest(username, password))
        val request = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) error("Invalid username or password")
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return json.decodeFromString<LoginResponse>(bodyText).token.also { token = it }
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
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    return handle(response)
                }
            }
        }
        error("HTTP 401")
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

    suspend fun approveAgent(agentId: Long): Result<ApproveResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            authed("/agents/$agentId/approve", { post(emptyBody) }) { response ->
                json.decodeFromString<ApproveResponse>(response.body?.string().orEmpty())
            }
        }
    }

    suspend fun revokeAgent(agentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val emptyBody = "".toRequestBody("application/json".toMediaType())
            authed("/agents/$agentId/revoke", { post(emptyBody) }) { }
        }
    }

    suspend fun removeAgent(agentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authed("/agents/$agentId", { delete() }) { }
        }
    }
}
