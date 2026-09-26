package sk.dilino.pulseclientmobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pulse_connection")
private val SERVER_URL_KEY = stringPreferencesKey("server_base_url")
private val USERNAME_KEY = stringPreferencesKey("username")
private val PASSWORD_KEY = stringPreferencesKey("password")

/** Everything needed to talk to the server as a logged-in user. */
data class Connection(val baseUrl: String, val username: String, val password: String)

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
        if (url != null && user != null && pass != null) Connection(url, user, pass) else null
    }

    suspend fun save(connection: Connection) {
        context.dataStore.edit {
            it[SERVER_URL_KEY] = connection.baseUrl
            it[USERNAME_KEY] = connection.username
            it[PASSWORD_KEY] = connection.password
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(SERVER_URL_KEY)
            it.remove(USERNAME_KEY)
            it.remove(PASSWORD_KEY)
        }
    }
}
