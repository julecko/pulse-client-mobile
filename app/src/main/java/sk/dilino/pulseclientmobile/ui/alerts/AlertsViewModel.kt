package sk.dilino.pulseclientmobile.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
import sk.dilino.pulseclientmobile.data.model.GeoAlertSettings
import sk.dilino.pulseclientmobile.data.model.NewAlertRule
import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.util.pollEvery

private const val ALERTS_LIMIT = 200

enum class AlertsTab { ALERTS, RULES }

enum class AlertFilter { ALL, CRITICAL, WARNING, ACKNOWLEDGED }

/** Unsaved edits to the geo alert settings; they're only sent on SAVE, since the server replaces them all at once. */
data class GeoDraft(
    val allowedCountries: List<String> = emptyList(),
    val includeFailures: Boolean = false,
    val notify: Boolean = true,
    /** The country code being typed. */
    val input: String = ""
) {
    fun matches(s: GeoAlertSettings) =
        allowedCountries == s.allowedCountries && includeFailures == s.includeFailures && notify == s.notify

    companion object {
        fun from(s: GeoAlertSettings) = GeoDraft(s.allowedCountries, s.includeFailures, s.notify)
    }
}

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
    val createRuleError: String? = null,

    /** Every agent's offline-alert setting; only approved agents are watched. */
    val offline: List<OfflineAlertSetting> = emptyList(),
    val offlineLoaded: Boolean = false,
    val offlineError: String? = null,
    val offlineBusyId: Long? = null,

    val geo: GeoAlertSettings? = null,
    val geoError: String? = null,
    val geoDraft: GeoDraft = GeoDraft(),
    val geoSaving: Boolean = false,
    val geoSaveError: String? = null
) {
    val geoDirty: Boolean get() = geo != null && !geoDraft.matches(geo)

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

class AlertsViewModel(private val api: PulseApiClient, refreshIntervalSecs: Flow<Int>) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState

    init {
        viewModelScope.launch {
            loadAlerts(showSpinner = true)
            pollEvery(refreshIntervalSecs) { loadAlerts(showSpinner = false) }
        }
        viewModelScope.launch { loadRulesAndAgents() }
    }

    fun selectTab(tab: AlertsTab) {
        _uiState.update { it.copy(tab = tab) }
        // Offline state changes on its own (hosts go quiet and come back), so show it fresh.
        if (tab == AlertsTab.RULES) viewModelScope.launch { loadOffline() }
    }

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
        loadOffline()
        loadGeo()
    }

    // ------------------------------------------------------------ offline alerts

    private suspend fun loadOffline() {
        api.offlineAlerts()
            .onSuccess { list -> _uiState.update { it.copy(offline = list.sortedBy { o -> o.hostname }, offlineLoaded = true, offlineError = null) } }
            .onFailure { e -> _uiState.update { it.copy(offlineLoaded = true, offlineError = e.message ?: "Couldn't load offline alerts") } }
    }

    /** Alerts when [agentId] sends no metrics for [afterSecs]; null turns the check off (resolving an active offline alert). */
    fun setOfflineAlert(agentId: Long, afterSecs: Int?) {
        viewModelScope.launch {
            _uiState.update { it.copy(offlineBusyId = agentId, offlineError = null) }
            api.setOfflineAlert(agentId, afterSecs)
                .onSuccess { updated ->
                    _uiState.update { s -> s.copy(offlineBusyId = null, offline = s.offline.map { if (it.agentId == agentId) updated else it }) }
                }
                .onFailure { e -> _uiState.update { it.copy(offlineBusyId = null, offlineError = e.message ?: "Couldn't change offline alert") } }
            // Turning a check off resolves its alert.
            if (afterSecs == null) loadAlerts(showSpinner = false)
        }
    }

    // ------------------------------------------------------------ geo alerts

    private suspend fun loadGeo() {
        api.geoAlertSettings()
            .onSuccess { g ->
                _uiState.update { s ->
                    // Don't clobber edits in progress with a background reload.
                    val keepDraft = s.geo != null && !s.geoDraft.matches(s.geo)
                    s.copy(geo = g, geoError = null, geoDraft = if (keepDraft) s.geoDraft else GeoDraft.from(g))
                }
            }
            .onFailure { e -> _uiState.update { it.copy(geoError = e.message ?: "Couldn't load geo alert settings") } }
    }

    fun updateGeoDraft(transform: (GeoDraft) -> GeoDraft) =
        _uiState.update { it.copy(geoDraft = transform(it.geoDraft), geoSaveError = null) }

    /** Adds the typed code to the allowed countries, if it's a two-letter ISO code. */
    fun addGeoCountry() {
        val code = _uiState.value.geoDraft.input.trim().uppercase()
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) {
            _uiState.update { it.copy(geoSaveError = "Use a two-letter ISO country code, e.g. SK or DE") }
            return
        }
        updateGeoDraft { d ->
            d.copy(allowedCountries = if (code in d.allowedCountries) d.allowedCountries else d.allowedCountries + code, input = "")
        }
    }

    fun removeGeoCountry(code: String) = updateGeoDraft { d -> d.copy(allowedCountries = d.allowedCountries - code) }

    fun resetGeoDraft() = _uiState.update { s -> s.copy(geoDraft = s.geo?.let { GeoDraft.from(it) } ?: GeoDraft(), geoSaveError = null) }

    fun saveGeo() {
        val draft = _uiState.value.geoDraft
        viewModelScope.launch {
            _uiState.update { it.copy(geoSaving = true, geoSaveError = null) }
            api.setGeoAlertSettings(draft.allowedCountries, draft.includeFailures, draft.notify)
                .onSuccess { g -> _uiState.update { it.copy(geoSaving = false, geo = g, geoDraft = GeoDraft.from(g)) } }
                .onFailure { e -> _uiState.update { it.copy(geoSaving = false, geoSaveError = e.message ?: "Couldn't save geo alert settings") } }
        }
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
