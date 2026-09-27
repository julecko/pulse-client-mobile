package sk.dilino.pulseclientmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.data.Connection
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.DEFAULT_REFRESH_INTERVAL_SECS
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.PairingStatus
import sk.dilino.pulseclientmobile.data.model.PushDevice
import sk.dilino.pulseclientmobile.data.model.RetentionData
import sk.dilino.pulseclientmobile.data.model.RetentionSetting
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
    val agentActionError: String? = null,

    val pushDevices: List<PushDevice> = emptyList(),
    val pushDevicesLoaded: Boolean = false,
    val pushDevicesError: String? = null,
    val busyDeviceId: Long? = null,

    val retention: List<RetentionSetting> = emptyList(),
    val retentionLoaded: Boolean = false,
    val retentionError: String? = null,
    val retentionBusy: RetentionData? = null,
    /** A change that would delete data right away, awaiting a second tap. */
    val retentionConfirm: RetentionChange? = null
)

/** Keep [data] for [days] (0 = forever); null resets it to the server config's default. */
data class RetentionChange(val data: RetentionData, val days: Int?)

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

    /** How often screens re-fetch, in seconds; a device preference, not a server setting. */
    val refreshIntervalSecs: StateFlow<Int> = connectionStore.refreshIntervalSecs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_REFRESH_INTERVAL_SECS)

    fun setRefreshInterval(secs: Int) {
        viewModelScope.launch { connectionStore.setRefreshInterval(secs) }
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            api.pairingStatus()
                .onSuccess { p -> _uiState.update { it.copy(pairing = p, pairingError = null) } }
                .onFailure { e -> _uiState.update { it.copy(pairingError = e.message ?: "Couldn't load pairing status") } }
            loadAgents()
            loadPushDevices()
            loadRetention()
        }
    }

    private suspend fun loadRetention() {
        api.retention()
            .onSuccess { list -> _uiState.update { it.copy(retention = list, retentionLoaded = true, retentionError = null) } }
            .onFailure { e -> _uiState.update { it.copy(retentionLoaded = true, retentionError = e.message ?: "Couldn't load retention") } }
    }

    /**
     * Changes how long [change]'s data is kept. Shortening it deletes the older data on the server
     * right away with no undo, so that needs a second tap; lengthening it applies at once.
     */
    fun requestRetention(change: RetentionChange) {
        val state = _uiState.value
        val current = state.retention.firstOrNull { it.dataEnum == change.data } ?: return
        val newDays = change.days ?: current.defaultDays
        if (newDays == current.days && (change.days != null) == current.overridden) return
        if (state.retentionConfirm != change && deletesData(current.days, newDays)) {
            _uiState.update { it.copy(retentionConfirm = change, retentionError = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(retentionBusy = change.data, retentionConfirm = null, retentionError = null) }
            api.setRetention(change.data, change.days)
                .onSuccess { updated ->
                    _uiState.update { s ->
                        s.copy(retentionBusy = null, retention = s.retention.map { if (it.data == updated.data) updated else it })
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(retentionBusy = null, retentionError = e.message ?: "Couldn't change retention") } }
        }
    }

    fun cancelRetention() = _uiState.update { it.copy(retentionConfirm = null) }

    /** Whether going from [from] to [to] days (0 = forever) drops data that is kept now. */
    private fun deletesData(from: Int, to: Int): Boolean = to != 0 && (from == 0 || to < from)

    private suspend fun loadAgents() {
        api.listAgents()
            .onSuccess { list -> _uiState.update { it.copy(agents = list, agentsLoaded = true, agentsError = null) } }
            .onFailure { e -> _uiState.update { it.copy(agentsLoaded = true, agentsError = e.message ?: "Couldn't load hosts") } }
    }

    private suspend fun loadPushDevices() {
        api.pushDevices()
            .onSuccess { list -> _uiState.update { it.copy(pushDevices = list, pushDevicesLoaded = true, pushDevicesError = null) } }
            .onFailure { e -> _uiState.update { it.copy(pushDevicesLoaded = true, pushDevicesError = e.message ?: "Couldn't load devices") } }
    }

    fun removePushDevice(deviceId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyDeviceId = deviceId) }
            api.removePushDevice(deviceId)
            _uiState.update { it.copy(busyDeviceId = null) }
            loadPushDevices()
        }
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

    fun unrevoke(agentId: Long) = agentAction(agentId) { api.unrevokeAgent(agentId) }

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
