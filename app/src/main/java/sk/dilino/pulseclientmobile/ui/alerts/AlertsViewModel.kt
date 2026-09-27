package sk.dilino.pulseclientmobile.ui.alerts

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
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.AlertMetric
import sk.dilino.pulseclientmobile.data.model.AlertOperator
import sk.dilino.pulseclientmobile.data.model.AlertRecord
import sk.dilino.pulseclientmobile.data.model.AlertRule
import sk.dilino.pulseclientmobile.data.model.AlertSeverity
import sk.dilino.pulseclientmobile.data.model.NewAlertRule
import sk.dilino.pulseclientmobile.data.network.PulseApiClient

private const val ALERTS_POLL_INTERVAL_MS = 15_000L
private const val ALERTS_LIMIT = 200

enum class AlertsTab { ALERTS, RULES }

enum class AlertFilter { ALL, CRITICAL, WARNING, ACKNOWLEDGED }

/** In-progress state for the "new rule" form. */
data class NewRuleDraft(
    val name: String = "",
    val agentId: Long? = null,
    val metric: AlertMetric = AlertMetric.CPU_USAGE_PERCENT,
    val operator: AlertOperator = AlertOperator.GT,
    val threshold: String = "90",
    val durationMinutes: Int = 5,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val notify: Boolean = true
)

data class AlertsUiState(
    val tab: AlertsTab = AlertsTab.ALERTS,

    val isLoading: Boolean = true,
    val alerts: List<AlertRecord> = emptyList(),
    val filter: AlertFilter = AlertFilter.ALL,
    val expandedId: Long? = null,
    val alertsError: String? = null,
    val ackInFlightId: Long? = null,
    val acknowledgingAll: Boolean = false,

    val rules: List<AlertRule> = emptyList(),
    val rulesLoaded: Boolean = false,
    val rulesError: String? = null,
    val ruleBusyId: Long? = null,
    val confirmDeleteRuleId: Long? = null,

    val agents: List<AgentSummary> = emptyList(),
    val showNewRule: Boolean = false,
    val newRule: NewRuleDraft = NewRuleDraft(),
    val creatingRule: Boolean = false,
    val createRuleError: String? = null
) {
    val openCount get() = alerts.count { it.isActive }
    val ackCount get() = alerts.count { it.isAcknowledged }

    val filteredAlerts: List<AlertRecord>
        get() = when (filter) {
            AlertFilter.ALL -> alerts
            AlertFilter.CRITICAL -> alerts.filter { it.severityEnum == AlertSeverity.CRITICAL }
            AlertFilter.WARNING -> alerts.filter { it.severityEnum == AlertSeverity.WARNING }
            AlertFilter.ACKNOWLEDGED -> alerts.filter { it.isAcknowledged }
        }

    /** Alerts in the current filter that still need a human to see them. */
    val unacknowledgedInView: List<AlertRecord> get() = filteredAlerts.filter { !it.isAcknowledged }

    fun hostnameOf(agentId: Long?) = agentId?.let { id -> agents.firstOrNull { it.id == id }?.hostname }
}

class AlertsViewModel(private val api: PulseApiClient) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState

    init {
        viewModelScope.launch {
            loadAlerts(showSpinner = true)
            while (true) {
                delay(ALERTS_POLL_INTERVAL_MS)
                loadAlerts(showSpinner = false)
            }
        }
        viewModelScope.launch { loadRulesAndAgents() }
    }

    fun selectTab(tab: AlertsTab) = _uiState.update { it.copy(tab = tab) }

    fun setFilter(filter: AlertFilter) = _uiState.update { it.copy(filter = filter) }

    fun toggleExpand(id: Long) = _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }

    fun refresh() {
        viewModelScope.launch { loadAlerts(showSpinner = false) }
    }

    private suspend fun loadAlerts(showSpinner: Boolean) {
        if (showSpinner) _uiState.update { it.copy(isLoading = true) }
        api.alerts(limit = ALERTS_LIMIT)
            .onSuccess { list -> _uiState.update { it.copy(isLoading = false, alerts = list, alertsError = null) } }
            .onFailure { e -> _uiState.update { it.copy(isLoading = false, alertsError = e.message ?: "Couldn't load alerts") } }
    }

    fun acknowledge(alertId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(ackInFlightId = alertId) }
            api.acknowledgeAlert(alertId)
            _uiState.update { it.copy(ackInFlightId = null) }
            loadAlerts(showSpinner = false)
        }
    }

    /** Acknowledges every unacknowledged alert currently in view (respects the active filter). */
    fun acknowledgeAll() {
        val ids = _uiState.value.unacknowledgedInView.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(acknowledgingAll = true) }
            coroutineScope {
                ids.map { id -> async { api.acknowledgeAlert(id) } }.awaitAll()
            }
            _uiState.update { it.copy(acknowledgingAll = false) }
            loadAlerts(showSpinner = false)
        }
    }

    // ------------------------------------------------------------ rules

    fun refreshRules() {
        viewModelScope.launch { loadRulesAndAgents() }
    }

    private suspend fun loadRulesAndAgents() {
        api.alertRules()
            .onSuccess { rules -> _uiState.update { it.copy(rules = rules.sortedBy { r -> r.name.lowercase() }, rulesLoaded = true, rulesError = null) } }
            .onFailure { e -> _uiState.update { it.copy(rulesLoaded = true, rulesError = e.message ?: "Couldn't load rules") } }
        api.listAgents().onSuccess { agents -> _uiState.update { it.copy(agents = agents.sortedBy { a -> a.hostname }) } }
    }

    fun toggleEnabled(rule: AlertRule) = ruleAction(rule.id) { api.updateAlertRule(rule.id, enabled = !rule.enabled) }

    fun toggleNotify(rule: AlertRule) = ruleAction(rule.id) { api.updateAlertRule(rule.id, notify = !rule.notify) }

    /** First tap arms the deletion; the second confirms it. */
    fun requestDeleteRule(ruleId: Long) {
        if (_uiState.value.confirmDeleteRuleId == ruleId) {
            _uiState.update { it.copy(confirmDeleteRuleId = null) }
            ruleAction(ruleId) { api.deleteAlertRule(ruleId) }
        } else {
            _uiState.update { it.copy(confirmDeleteRuleId = ruleId) }
        }
    }

    fun cancelDeleteRule() = _uiState.update { it.copy(confirmDeleteRuleId = null) }

    private fun ruleAction(ruleId: Long, action: suspend () -> Result<*>) {
        viewModelScope.launch {
            _uiState.update { it.copy(ruleBusyId = ruleId) }
            action()
            _uiState.update { it.copy(ruleBusyId = null) }
            loadRulesAndAgents()
        }
    }

    fun showNewRuleForm() = _uiState.update { it.copy(showNewRule = true, newRule = NewRuleDraft(), createRuleError = null) }

    fun cancelNewRule() = _uiState.update { it.copy(showNewRule = false) }

    fun updateDraft(transform: (NewRuleDraft) -> NewRuleDraft) = _uiState.update { it.copy(newRule = transform(it.newRule), createRuleError = null) }

    fun createRule() {
        val draft = _uiState.value.newRule
        val threshold = draft.threshold.trim().toDoubleOrNull()
        val name = draft.name.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(createRuleError = "Name a rule first") }
            return
        }
        if (threshold == null) {
            _uiState.update { it.copy(createRuleError = "Threshold must be a number") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(creatingRule = true, createRuleError = null) }
            api.createAlertRule(
                NewAlertRule(
                    name = name,
                    agentId = draft.agentId,
                    metric = draft.metric.wire,
                    operator = draft.operator.wire,
                    threshold = threshold,
                    durationSecs = draft.durationMinutes * 60,
                    severity = draft.severity.wire,
                    notify = draft.notify
                )
            ).onSuccess {
                _uiState.update { it.copy(creatingRule = false, showNewRule = false) }
                loadRulesAndAgents()
            }.onFailure { e ->
                _uiState.update { it.copy(creatingRule = false, createRuleError = e.message ?: "Couldn't create rule") }
            }
        }
    }
}
