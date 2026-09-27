package sk.dilino.pulseclientmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.data.Connection
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.PairingStatus
import sk.dilino.pulseclientmobile.data.network.PulseApiClient

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val pinnedCert: String? = null,

    val pairing: PairingStatus? = null,
    val pairingBusy: Boolean = false,
    val pairingError: String? = null,

    val agents: List<AgentSummary> = emptyList(),
    val agentsLoaded: Boolean = false,
    val agentsError: String? = null,
    /** Agent currently being changed on the server. */
    val busyAgentId: Long? = null,
    /** Agent whose removal awaits a second tap; removing is permanent, so it's a two-step action. */
    val confirmRemoveId: Long? = null,
    val agentActionError: String? = null
)

class SettingsViewModel(
    private val api: PulseApiClient,
    private val connectionStore: ConnectionStore,
    connection: Connection
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            serverUrl = connection.baseUrl,
            username = connection.username,
            pinnedCert = connection.pinnedCertSha256
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            api.pairingStatus()
                .onSuccess { p -> _uiState.update { it.copy(pairing = p, pairingError = null) } }
                .onFailure { e -> _uiState.update { it.copy(pairingError = e.message ?: "Couldn't load pairing status") } }
            loadAgents()
        }
    }

    private suspend fun loadAgents() {
        api.listAgents()
            .onSuccess { list -> _uiState.update { it.copy(agents = list, agentsLoaded = true, agentsError = null) } }
            .onFailure { e -> _uiState.update { it.copy(agentsLoaded = true, agentsError = e.message ?: "Couldn't load hosts") } }
    }

    /** Opens the pairing window (for [minutes], or until closed when null) or closes it. */
    fun setPairing(open: Boolean, minutes: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(pairingBusy = true, pairingError = null) }
            api.setPairing(open, minutes)
                .onSuccess { p -> _uiState.update { it.copy(pairing = p, pairingBusy = false) } }
                .onFailure { e -> _uiState.update { it.copy(pairingBusy = false, pairingError = e.message ?: "Couldn't change pairing") } }
        }
    }

    fun approve(agentId: Long) = agentAction(agentId) { api.approveAgent(agentId) }

    fun revoke(agentId: Long) = agentAction(agentId) { api.revokeAgent(agentId) }

    /** First tap arms the removal; the second confirms it. */
    fun requestRemove(agentId: Long) {
        if (_uiState.value.confirmRemoveId == agentId) {
            _uiState.update { it.copy(confirmRemoveId = null) }
            agentAction(agentId) { api.removeAgent(agentId) }
        } else {
            _uiState.update { it.copy(confirmRemoveId = agentId, agentActionError = null) }
        }
    }

    fun cancelRemove() = _uiState.update { it.copy(confirmRemoveId = null) }

    private fun agentAction(agentId: Long, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyAgentId = agentId, agentActionError = null) }
            val result = action()
            _uiState.update {
                it.copy(
                    busyAgentId = null,
                    agentActionError = result.exceptionOrNull()?.let { e -> e.message ?: "Action failed" }
                )
            }
            loadAgents()
        }
    }

    /** Ends the server session and forgets the server and credentials; the app returns to the connect screen. */
    fun signOut() {
        viewModelScope.launch {
            api.logout()
            connectionStore.clear()
        }
    }
}
