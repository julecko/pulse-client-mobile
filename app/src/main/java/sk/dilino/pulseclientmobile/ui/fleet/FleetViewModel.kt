package sk.dilino.pulseclientmobile.ui.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.data.model.AgentLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.ui.components.Severity
import sk.dilino.pulseclientmobile.util.isOffline
import sk.dilino.pulseclientmobile.util.severity

private const val POLL_INTERVAL_MS = 10_000L
private const val SNAPSHOTS_PER_HOST = 48

data class FleetUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isReachable: Boolean = false,
    val agents: List<AgentSummary> = emptyList(),
    /** Recent snapshots per agent id, oldest → newest. Pending/revoked agents have none. */
    val metrics: Map<Long, List<MetricsRecord>> = emptyMap(),
    /** Offline alert setting and state per agent id. */
    val offlineAlerts: Map<Long, OfflineAlertSetting> = emptyMap(),
    val error: String? = null,
    val actionInFlightId: Long? = null
) {
    /** Approved hosts that are reporting (not offline). */
    val upCount get() = agents.count { it.lifecycle == AgentLifecycle.APPROVED && !isOffline(it) }
    val pendingCount get() = agents.count { it.lifecycle == AgentLifecycle.PENDING }

    /** An approved agent that stopped sending metrics (see [OfflineAlertSetting.isOffline]). */
    fun isOffline(agent: AgentSummary): Boolean =
        agent.lifecycle == AgentLifecycle.APPROVED && offlineAlerts[agent.id]?.isOffline() == true

    fun severityOf(agent: AgentSummary): Severity = when {
        agent.lifecycle == AgentLifecycle.PENDING -> Severity.WARNING
        agent.lifecycle == AgentLifecycle.REVOKED -> Severity.CRITICAL
        isOffline(agent) -> Severity.CRITICAL
        else -> metrics[agent.id]?.lastOrNull()?.severity ?: Severity.HEALTHY
    }

    /** Hosts that need a human: anything not healthy. */
    val attentionCount get() = agents.count { severityOf(it) != Severity.HEALTHY }
}

class FleetViewModel(private val api: PulseApiClient) : ViewModel() {

    private val _uiState = MutableStateFlow(FleetUiState())
    val uiState: StateFlow<FleetUiState> = _uiState

    init {
        // The agents report on an interval; keep the fleet view in step with them.
        viewModelScope.launch {
            fetch(showSpinner = true, isRefresh = false)
            while (true) {
                delay(POLL_INTERVAL_MS)
                fetch(showSpinner = false, isRefresh = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { fetch(showSpinner = false, isRefresh = true) }
    }

    private suspend fun fetch(showSpinner: Boolean, isRefresh: Boolean) {
        _uiState.update { it.copy(isLoading = showSpinner, isRefreshing = isRefresh) }
        val reachable = api.healthz().isSuccess
        val agentsResult = api.listAgents()
        val agents = agentsResult.getOrNull()
        if (agents == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isReachable = reachable,
                    error = agentsResult.exceptionOrNull()?.message ?: "Failed to load fleet"
                )
            }
            return
        }

        val (metrics, offlineAlerts) = coroutineScope {
            val offline = async { api.offlineAlerts().getOrNull() }
            val metrics = agents.filter { it.lifecycle == AgentLifecycle.APPROVED }
                .map { agent -> async { agent.id to api.metrics(agent.id, SNAPSHOTS_PER_HOST).getOrNull() } }
                .awaitAll()
                .mapNotNull { (id, list) -> list?.let { id to it } }
                .toMap()
            metrics to offline.await()?.associateBy { it.agentId }
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                isReachable = reachable,
                agents = agents.sortedBy { a -> a.hostname },
                // Keep the last known series for a host whose metrics call blipped.
                metrics = it.metrics.filterKeys { id -> agents.any { a -> a.id == id } } + metrics,
                // Same for the offline states.
                offlineAlerts = offlineAlerts ?: it.offlineAlerts,
                error = null
            )
        }
    }

    fun approve(agentId: Long) = runAction(agentId) { api.approveAgent(agentId) }

    fun remove(agentId: Long) = runAction(agentId) { api.removeAgent(agentId) }

    private fun runAction(agentId: Long, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInFlightId = agentId) }
            action()
            _uiState.update { it.copy(actionInFlightId = null) }
            fetch(showSpinner = false, isRefresh = false)
        }
    }
}
