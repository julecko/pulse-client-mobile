package sk.dilino.pulseclientmobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pulse_connection")
private val SERVER_URL_KEY = stringPreferencesKey("server_base_url")
private val USERNAME_KEY = stringPreferencesKey("username")
private val PASSWORD_KEY = stringPreferencesKey("password")
private val PINNED_CERT_KEY = stringPreferencesKey("pinned_cert_sha256")
private val REFRESH_INTERVAL_KEY = intPreferencesKey("refresh_interval_secs")

/** How often screens re-fetch from the server unless the user picks otherwise in Settings. */
const val DEFAULT_REFRESH_INTERVAL_SECS = 10

/** Everything needed to talk to the server as a logged-in user. */
data class Connection(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** SHA-256 of the self-signed server certificate the user chose to trust; null = normal CA verification. */
    val pinnedCertSha256: String? = null
)

/**
 * Persists the server address and the user's credentials in the app-private
 * DataStore. The credentials are kept (rather than just a session token) so the
 * client can transparently log in again when the server-side session expires.
 */
class ConnectionStore(private val context: Context) {

    /** Null until a server has been connected and a login has succeeded. */
    val connection: Flow<Connection?> = context.dataStore.data.map { prefs ->
        val url = prefs[SERVER_URL_KEY]
        val user = prefs[USERNAME_KEY]
        val pass = prefs[PASSWORD_KEY]
        if (url != null && user != null && pass != null) Connection(url, user, pass, prefs[PINNED_CERT_KEY]) else null
    }.distinctUntilChanged()

    /** How often the host list, host detail and alerts re-fetch, in seconds. Kept across sign-outs: it's a device preference. */
    val refreshIntervalSecs: Flow<Int> = context.dataStore.data
        .map { it[REFRESH_INTERVAL_KEY] ?: DEFAULT_REFRESH_INTERVAL_SECS }
        .distinctUntilChanged()

    suspend fun setRefreshInterval(secs: Int) {
        context.dataStore.edit { it[REFRESH_INTERVAL_KEY] = secs }
    }

    suspend fun save(connection: Connection) {
        context.dataStore.edit {
            it[SERVER_URL_KEY] = connection.baseUrl
            it[USERNAME_KEY] = connection.username
            it[PASSWORD_KEY] = connection.password
            if (connection.pinnedCertSha256 != null) it[PINNED_CERT_KEY] = connection.pinnedCertSha256 else it.remove(PINNED_CERT_KEY)
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(SERVER_URL_KEY)
            it.remove(USERNAME_KEY)
            it.remove(PASSWORD_KEY)
            it.remove(PINNED_CERT_KEY)
        }
    }
}
