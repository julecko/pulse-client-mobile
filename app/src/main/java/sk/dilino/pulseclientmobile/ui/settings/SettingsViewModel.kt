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
import sk.dilino.pulseclientmobile.data.model.AppRelease
import sk.dilino.pulseclientmobile.data.model.GeoAlertSettings
import sk.dilino.pulseclientmobile.data.model.PairingStatus
import sk.dilino.pulseclientmobile.data.model.PushDevice
import sk.dilino.pulseclientmobile.data.model.SetGeoAlertSettings
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.update.AppUpdater

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

    val geo: GeoAlertSettings? = null,
    val geoBusy: Boolean = false,
    val geoError: String? = null
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
            loadPushDevices()
            loadGeoAlerts()
        }
    }

    private suspend fun loadGeoAlerts() {
        api.geoAlertSettings()
            .onSuccess { g -> _uiState.update { it.copy(geo = g, geoError = null) } }
            .onFailure { e -> _uiState.update { it.copy(geoError = e.message ?: "Couldn't load geo alert settings") } }
    }

    fun allowCountry(code: String) = changeGeo { it.copy(allowedCountries = (it.allowedCountries + code).distinct()) }

    fun disallowCountry(code: String) = changeGeo { it.copy(allowedCountries = it.allowedCountries - code) }

    fun setGeoIncludeFailures(on: Boolean) = changeGeo { it.copy(includeFailures = on) }

    fun setGeoNotify(on: Boolean) = changeGeo { it.copy(notify = on) }

    /** Applies [change] to the current settings and saves them; the server replaces all of them at once. */
    private fun changeGeo(change: (SetGeoAlertSettings) -> SetGeoAlertSettings) {
        val current = _uiState.value.geo ?: return
        if (_uiState.value.geoBusy) return
        val next = change(SetGeoAlertSettings(current.allowedCountries, current.includeFailures, current.notify))
        viewModelScope.launch {
            _uiState.update { it.copy(geoBusy = true, geoError = null) }
            api.setGeoAlertSettings(next)
                .onSuccess { g -> _uiState.update { it.copy(geo = g, geoBusy = false) } }
                .onFailure { e -> _uiState.update { it.copy(geoBusy = false, geoError = e.message ?: "Couldn't save geo alert settings") } }
        }
    }

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

    /** Asks the server for a newer app release now; installing it is left to the user here. */
    fun checkForUpdate() = AppUpdater.check(api, force = true, autoInstall = false)

    fun installUpdate(release: AppRelease) = AppUpdater.install(api, release)

    fun setAutoUpdate(enabled: Boolean) = AppUpdater.setAutoUpdate(enabled)

    /** Ends the server session and forgets the server and credentials; the app returns to the connect screen. */
    fun signOut() {
        viewModelScope.launch {
            api.logout()
            connectionStore.clear()
        }
    }
}
