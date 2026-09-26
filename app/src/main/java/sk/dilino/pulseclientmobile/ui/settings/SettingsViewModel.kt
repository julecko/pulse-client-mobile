package sk.dilino.pulseclientmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.data.Connection
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.network.PulseApiClient

data class SettingsUiState(
    val currentUrl: String = "",
    val username: String = "",
    val input: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

class SettingsViewModel(
    private val connectionStore: ConnectionStore,
    private val connection: Connection
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(currentUrl = connection.baseUrl, username = connection.username, input = connection.baseUrl)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value, error = null, saved = false) }
    }

    fun save() {
        val url = _uiState.value.input.trim()
        if (url.isBlank() || url == _uiState.value.currentUrl) return

        _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
        viewModelScope.launch {
            val client = PulseApiClient(url, connection.username, connection.password)
            val failure = client.healthz().exceptionOrNull()?.let {
                "Couldn't reach the server: ${it.message ?: "unknown error"}"
            } ?: client.login().exceptionOrNull()?.let {
                "Login failed: ${it.message ?: "unknown error"}"
            }
            if (failure == null) {
                connectionStore.save(connection.copy(baseUrl = url))
                _uiState.update { it.copy(isSaving = false, currentUrl = url, saved = true) }
            } else {
                _uiState.update { it.copy(isSaving = false, error = failure) }
            }
        }
    }

    /** Forgets the server and credentials; the app falls back to the connect screen. */
    fun signOut() {
        viewModelScope.launch { connectionStore.clear() }
    }
}
