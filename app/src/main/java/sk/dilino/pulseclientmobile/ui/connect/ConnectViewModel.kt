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

data class ConnectUiState(
    val input: String = "",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = input.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !isConnecting
}

class ConnectViewModel(private val connectionStore: ConnectionStore) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun connect(onConnected: () -> Unit) {
        val state = _uiState.value
        val connection = Connection(state.input.trim(), state.username.trim(), state.password)
        _uiState.update { it.copy(isConnecting = true, error = null) }
        viewModelScope.launch {
            val client = PulseApiClient(connection.baseUrl, connection.username, connection.password)
            val failure = client.healthz().exceptionOrNull()?.let {
                "Couldn't reach the server: ${it.message ?: "unknown error"}"
            } ?: client.login().exceptionOrNull()?.let {
                "Login failed: ${it.message ?: "unknown error"}"
            }
            if (failure == null) {
                connectionStore.save(connection)
                _uiState.update { it.copy(isConnecting = false, password = "") }
                onConnected()
            } else {
                _uiState.update { it.copy(isConnecting = false, error = failure) }
            }
        }
    }
}
