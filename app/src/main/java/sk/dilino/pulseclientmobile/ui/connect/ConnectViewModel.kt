package sk.dilino.pulseclientmobile.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.data.Connection
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.data.network.isTlsFailure
import sk.dilino.pulseclientmobile.data.network.probeServerCertificate

data class ConnectUiState(
    val input: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val error: String? = null,
    /** SHA-256 of a server certificate that isn't CA-signed, waiting for the user to trust or refuse it. */
    val untrustedCert: String? = null
) {
    val canSubmit: Boolean
        get() = input.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !isConnecting
}

class ConnectViewModel(private val connectionStore: ConnectionStore) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value, error = null, untrustedCert = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun connect(onConnected: () -> Unit) = attempt(pinnedCert = null, onConnected)

    /** The user compared the fingerprint and accepts this exact certificate from now on. */
    fun trustCertificate(onConnected: () -> Unit) {
        val fingerprint = _uiState.value.untrustedCert ?: return
        attempt(pinnedCert = fingerprint, onConnected)
    }

    fun refuseCertificate() {
        _uiState.update { it.copy(untrustedCert = null, error = "Connection cancelled — the server's certificate wasn't trusted.") }
    }

    private fun attempt(pinnedCert: String?, onConnected: () -> Unit) {
        val state = _uiState.value
        val connection = Connection(state.input.trim(), state.username.trim(), state.password, pinnedCert)
        _uiState.update { it.copy(isConnecting = true, error = null, untrustedCert = null) }
        viewModelScope.launch {
            val client = PulseApiClient(connection.baseUrl, connection.username, connection.password, pinnedCert)

            val health = client.healthz().exceptionOrNull()
            if (health != null) {
                if (pinnedCert == null && health.isTlsFailure()) {
                    // Most likely a self-signed dev/home server: let the user decide about it explicitly.
                    probeServerCertificate(connection.baseUrl)
                        .onSuccess { fp -> _uiState.update { it.copy(isConnecting = false, untrustedCert = fp) } }
                        .onFailure { fail("Couldn't verify the server's certificate: ${it.message ?: "unknown error"}") }
                } else {
                    fail("Couldn't reach the server: ${health.message ?: "unknown error"}")
                }
                return@launch
            }

            val login = client.login().exceptionOrNull()
            if (login != null) {
                fail("Login failed: ${login.message ?: "unknown error"}")
                return@launch
            }

            connectionStore.save(connection)
            _uiState.update { it.copy(isConnecting = false, password = "") }
            onConnected()
        }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isConnecting = false, error = message) }
    }
}
