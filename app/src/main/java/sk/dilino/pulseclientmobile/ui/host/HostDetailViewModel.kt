package sk.dilino.pulseclientmobile.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.AuthEventKind
import sk.dilino.pulseclientmobile.data.model.AuthEventRecord
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting
import sk.dilino.pulseclientmobile.data.model.PamNotifications
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.util.pollEvery

private const val SNAPSHOT_WINDOW = 96

enum class HostTab(val label: String) { OVERVIEW("OVERVIEW"), CPU("CPU"), AUTH("AUTH"), SNAPSHOTS("SNAPSHOTS") }

data class HostDetailUiState(
    val isLoading: Boolean = true,
    val agent: AgentSummary? = null,
    /** Oldest → newest. */
    val snapshots: List<MetricsRecord> = emptyList(),
    val authEvents: List<AuthEventRecord> = emptyList(),
    /** Which of this host's PAM events are pushed; null until loaded. */
    val pam: PamNotifications? = null,
    val pamBusy: Boolean = false,
    val pamError: String? = null,
    /** This host's offline check and whether it's offline now; null until loaded. */
    val offline: OfflineAlertSetting? = null,
    val error: String? = null,
    val actionInFlight: Boolean = false,
    /** Removal is permanent, so the button needs a second tap. */
    val confirmRemove: Boolean = false,
    val tab: HostTab = HostTab.OVERVIEW,
    /** Index into [snapshots] the whole screen is showing; null follows the newest one ("live"). */
    val pinnedIndex: Int? = null,
    /** Snapshot B in the compare view; null picks one an hour before the shown snapshot. */
    val compareIndex: Int? = null
) {
    val lastIndex get() = snapshots.lastIndex
    val shownIndex get() = pinnedIndex?.coerceIn(0, lastIndex.coerceAtLeast(0)) ?: lastIndex
    val isLive get() = pinnedIndex == null || pinnedIndex >= lastIndex
    val shown: MetricsRecord? get() = snapshots.getOrNull(shownIndex)
    val compareShown: Int get() = (compareIndex ?: (shownIndex - 4)).coerceIn(0, lastIndex.coerceAtLeast(0))
}

class HostDetailViewModel(
    private val api: PulseApiClient,
    private val agentId: Long,
    refreshIntervalSecs: Flow<Int>
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostDetailUiState())
    val uiState: StateFlow<HostDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            fetch()
            pollEvery(refreshIntervalSecs) { fetch() }
        }
    }

    fun load() {
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        val agentsResult = api.listAgents()
        val agent = agentsResult.getOrNull()?.firstOrNull { it.id == agentId }
        val approved = agent?.lifecycle == sk.dilino.pulseclientmobile.data.model.AgentLifecycle.APPROVED
        val metricsResult = if (approved) api.metrics(agentId, SNAPSHOT_WINDOW) else null
        val eventsResult = if (agent != null) api.authEvents(agentId) else null
        val offline = if (approved) api.offlineAlerts().getOrNull()?.firstOrNull { it.agentId == agentId } else null
        if (agent != null && _uiState.value.pam == null) loadPam()

        val error = agentsResult.exceptionOrNull()?.message
            ?: metricsResult?.exceptionOrNull()?.message
            ?: eventsResult?.exceptionOrNull()?.message

        _uiState.update { s ->
            val newSnapshots = metricsResult?.getOrNull() ?: s.snapshots
            // A pinned snapshot has to keep pointing at the same moment when the window slides.
            val shift = if (s.pinnedIndex != null && newSnapshots.size == SNAPSHOT_WINDOW && s.snapshots.size == SNAPSHOT_WINDOW) {
                s.snapshots.lastOrNull()?.id?.let { oldLast -> newSnapshots.indexOfLast { it.id == oldLast } }
                    ?.let { idx -> if (idx >= 0) newSnapshots.lastIndex - idx else 0 } ?: 0
            } else 0
            s.copy(
                isLoading = false,
                agent = agent ?: s.agent,
                snapshots = newSnapshots,
                authEvents = eventsResult?.getOrNull() ?: s.authEvents,
                offline = offline ?: s.offline.takeIf { approved },
                pinnedIndex = s.pinnedIndex?.minus(shift)?.coerceAtLeast(0),
                compareIndex = s.compareIndex?.minus(shift)?.coerceAtLeast(0),
                error = error
            )
        }
    }

    fun selectTab(tab: HostTab) = _uiState.update { it.copy(tab = tab) }

    private suspend fun loadPam() {
        api.pamNotifications(agentId)
            .onSuccess { p -> _uiState.update { it.copy(pam = p, pamError = null) } }
            .onFailure { e -> _uiState.update { it.copy(pamError = e.message ?: "Couldn't load login notifications") } }
    }

    /** Turns pushing [kind] of this host's PAM events on or off; the others stay as they are. */
    fun togglePamKind(kind: AuthEventKind) {
        val current = _uiState.value.pam?.kindSet ?: return
        val next = if (kind in current) current - kind else current + kind
        viewModelScope.launch {
            _uiState.update { it.copy(pamBusy = true, pamError = null) }
            api.setPamNotifications(agentId, next)
                .onSuccess { p -> _uiState.update { it.copy(pamBusy = false, pam = p) } }
                .onFailure { e -> _uiState.update { it.copy(pamBusy = false, pamError = e.message ?: "Couldn't change login notifications") } }
        }
    }

    fun scrubTo(index: Int) = _uiState.update {
        it.copy(pinnedIndex = if (index >= it.lastIndex) null else index.coerceAtLeast(0))
    }

    fun jumpLive() = _uiState.update { it.copy(pinnedIndex = null) }

    fun compareWith(index: Int) = _uiState.update { it.copy(compareIndex = index) }

    fun requestRemove(onRemoved: () -> Unit) {
        if (_uiState.value.confirmRemove) {
            _uiState.update { it.copy(confirmRemove = false) }
            remove(onRemoved)
        } else {
            _uiState.update { it.copy(confirmRemove = true) }
        }
    }

    fun approve() = runAction { api.approveAgent(agentId) }
    fun revoke() = runAction { api.revokeAgent(agentId) }
    fun unrevoke() = runAction { api.unrevokeAgent(agentId) }
    fun remove(onRemoved: () -> Unit) = runAction(onDone = onRemoved) { api.removeAgent(agentId) }

    private fun runAction(onDone: (() -> Unit)? = null, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInFlight = true) }
            val result = action()
            _uiState.update { it.copy(actionInFlight = false) }
            if (result.isSuccess && onDone != null) {
                onDone()
            } else {
                fetch()
            }
        }
    }
}
