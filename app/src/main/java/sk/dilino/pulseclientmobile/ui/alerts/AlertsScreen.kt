package sk.dilino.pulseclientmobile.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.AlertMetric
import sk.dilino.pulseclientmobile.data.model.AlertOperator
import sk.dilino.pulseclientmobile.data.model.AlertRecord
import sk.dilino.pulseclientmobile.data.model.AlertRule
import sk.dilino.pulseclientmobile.data.model.AlertSeverity
import sk.dilino.pulseclientmobile.ui.components.SectionLabel
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.util.formatAgo
import sk.dilino.pulseclientmobile.util.formatServerDateTime

private val Hair = PulseColors.BorderSubtle

private fun AlertSeverity.color(): Color = when (this) {
    AlertSeverity.CRITICAL -> PulseColors.Accent
    AlertSeverity.WARNING -> PulseColors.Warning
    AlertSeverity.INFO -> PulseColors.TextSecondary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: AlertsViewModel, onOpenHost: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PulseColors.Background)) {
        AlertsHeader(state)
        TabBar(state.tab, viewModel::selectTab)

        when (state.tab) {
            AlertsTab.ALERTS -> PullToRefreshBox(
                isRefreshing = false,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                    FilterRow(state.filter, viewModel::setFilter)
                    AckAllBar(state, viewModel)
                    AlertsList(state, viewModel, onOpenHost)
                }
            }
            AlertsTab.RULES -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
                RulesTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun AlertsHeader(state: AlertsUiState) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("ALERTS", style = MaterialTheme.typography.headlineLarge, color = PulseColors.TextPrimary)
            Text(
                "${state.openCount} OPEN · ${state.ackCount} ACK",
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
    }
}

@Composable
private fun TabBar(selected: AlertsTab, onSelect: (AlertsTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(PulseColors.Background)) {
        AlertsTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                modifier = Modifier.weight(1f).clickable { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.name,
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
                    color = if (active) PulseColors.TextPrimary else PulseColors.TextTertiary,
                    modifier = Modifier.padding(top = 11.dp, bottom = 9.dp)
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) PulseColors.Accent else Color.Transparent))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
        color = if (selected) PulseColors.Background else PulseColors.TextSecondary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.background(PulseColors.TextPrimary) else Modifier.border(1.dp, PulseColors.Border))
            .padding(horizontal = 11.dp, vertical = 7.dp)
    )
}

@Composable
private fun FilterRow(filter: AlertFilter, onSelect: (AlertFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip("ALL", filter == AlertFilter.ALL) { onSelect(AlertFilter.ALL) }
        FilterChip("CRIT", filter == AlertFilter.CRITICAL) { onSelect(AlertFilter.CRITICAL) }
        FilterChip("WARN", filter == AlertFilter.WARNING) { onSelect(AlertFilter.WARNING) }
        FilterChip("ACK", filter == AlertFilter.ACKNOWLEDGED) { onSelect(AlertFilter.ACKNOWLEDGED) }
    }
}

@Composable
private fun AckAllBar(state: AlertsUiState, vm: AlertsViewModel) {
    val count = state.unacknowledgedInView.size
    if (count == 0) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$count unacknowledged",
            fontSize = 11.sp, color = PulseColors.TextTertiary, modifier = Modifier.weight(1f)
        )
        FormButton(
            if (state.acknowledgingAll) "…" else "ACKNOWLEDGE ALL",
            primary = false,
            enabled = !state.acknowledgingAll
        ) { vm.acknowledgeAll() }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AlertsList(state: AlertsUiState, vm: AlertsViewModel, onOpenHost: (Long) -> Unit) {
    val alerts = state.filteredAlerts
    when {
        state.isLoading -> Text("Loading…", fontSize = 12.sp, color = PulseColors.TextTertiary, modifier = Modifier.padding(18.dp))
        state.alertsError != null && state.alerts.isEmpty() ->
            Text(state.alertsError, fontSize = 12.sp, lineHeight = 17.sp, color = PulseColors.Accent, modifier = Modifier.padding(18.dp))
        alerts.isEmpty() -> Text(
            if (state.alerts.isEmpty()) "No alerts yet. Set up a rule in the RULES tab and it will fire here when its condition holds."
            else "No alerts match this filter.",
            fontSize = 12.sp, lineHeight = 17.sp, color = PulseColors.TextTertiary, modifier = Modifier.padding(18.dp)
        )
        else -> alerts.forEach { alert -> AlertCard(alert, state, vm, onOpenHost) }
    }
}

@Composable
private fun AlertCard(alert: AlertRecord, state: AlertsUiState, vm: AlertsViewModel, onOpenHost: (Long) -> Unit) {
    val tint = alert.severityEnum.color()
    val expanded = state.expandedId == alert.id
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.fillMaxHeight().width(3.dp).background(tint))
        Column(
            Modifier
                .weight(1f)
                .clickable { vm.toggleExpand(alert.id) }
                .padding(start = 15.dp, end = 18.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alert.severityEnum.label,
                    fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = tint,
                    modifier = Modifier.border(1.dp, tint).padding(horizontal = 5.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    alert.hostname ?: state.hostnameOf(alert.agentId) ?: "fleet-wide",
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(formatAgo(alert.triggeredAt), fontSize = 10.sp, color = PulseColors.TextTertiary)
                if (!alert.isActive) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(PulseColors.TextTertiary))
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(alert.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(alert.message, fontSize = 11.5.sp, lineHeight = 16.sp, color = PulseColors.TextSecondary)

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
                Spacer(Modifier.height(10.dp))
                DetailRow("TRIGGERED", formatServerDateTime(alert.triggeredAt) + " UTC")
                if (alert.resolvedAt != null) DetailRow("RESOLVED", formatServerDateTime(alert.resolvedAt) + " UTC")
                else DetailRow("STATUS", "still active")
                if (alert.isAcknowledged) {
                    DetailRow("ACKNOWLEDGED", "${alert.acknowledgedBy ?: "someone"} · ${formatServerDateTime(alert.acknowledgedAt!!)} UTC")
                }
                if (alert.ruleId == null) DetailRow("RULE", "deleted")

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (alert.agentId != null) {
                        FormButton("OPEN HOST", primary = true) { onOpenHost(alert.agentId) }
                    }
                    if (!alert.isAcknowledged) {
                        val busy = state.ackInFlightId == alert.id || state.acknowledgingAll
                        FormButton(if (busy) "…" else "ACKNOWLEDGE", primary = alert.agentId == null, enabled = !busy) { vm.acknowledge(alert.id) }
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Divider))
}

@Composable
private fun DetailRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = PulseColors.TextTertiary, modifier = Modifier.width(104.dp))
        Text(value, fontSize = 11.5.sp, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FormButton(text: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
        color = when {
            !enabled -> PulseColors.TextTertiary
            primary -> PulseColors.AccentOn
            else -> PulseColors.TextPrimary
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (primary && enabled) Modifier.background(PulseColors.Accent) else Modifier.border(1.dp, PulseColors.Border))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------- rules

@Composable
private fun RulesTab(state: AlertsUiState, vm: AlertsViewModel) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        SectionLabel("RULES · ${state.rules.size}")
        Spacer(Modifier.height(4.dp))
        Text(
            "Each enabled rule checks every metrics snapshot. Once its condition holds for the full duration, it raises an alert.",
            fontSize = 11.5.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
        )
    }

    if (state.showNewRule) {
        NewRuleForm(state, vm)
    } else {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            FormButton("+ NEW RULE", primary = true) { vm.showNewRuleForm() }
        }
    }

    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))

    when {
        !state.rulesLoaded -> Text("Loading…", fontSize = 12.sp, color = PulseColors.TextTertiary, modifier = Modifier.padding(18.dp))
        state.rules.isEmpty() -> Text(
            state.rulesError ?: "No rules yet. Create one above, e.g. “CPU over 90% for 5 minutes”.",
            fontSize = 12.sp, lineHeight = 17.sp,
            color = if (state.rulesError != null) PulseColors.Accent else PulseColors.TextTertiary,
            modifier = Modifier.padding(18.dp)
        )
        else -> state.rules.forEach { rule -> RuleRow(rule, state, vm) }
    }
}

@Composable
private fun RuleRow(rule: AlertRule, state: AlertsUiState, vm: AlertsViewModel) {
    val busy = state.ruleBusyId == rule.id
    val confirming = state.confirmDeleteRuleId == rule.id
    val tint = rule.severityEnum.color()
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rule.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                rule.severityEnum.label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = tint,
                modifier = Modifier.border(1.dp, tint).padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(ruleSummary(rule, state.agents), fontSize = 11.5.sp, color = PulseColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledSwitch("ENABLED", rule.enabled, !busy) { vm.toggleEnabled(rule) }
            LabeledSwitch("NOTIFY", rule.notify, !busy) { vm.toggleNotify(rule) }
            Spacer(Modifier.weight(1f))
            if (confirming) {
                FormButton("CONFIRM", primary = true, enabled = !busy) { vm.requestDeleteRule(rule.id) }
                Spacer(Modifier.width(6.dp))
                FormButton("CANCEL", primary = false) { vm.cancelDeleteRule() }
            } else {
                FormButton(if (busy) "…" else "DELETE", primary = false, enabled = !busy) { vm.requestDeleteRule(rule.id) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Divider))
}

private fun ruleSummary(rule: AlertRule, agents: List<AgentSummary>): String {
    val scope = rule.agentId?.let { id -> agents.firstOrNull { it.id == id }?.hostname ?: "agent #$id" } ?: "every host"
    val threshold = if (rule.threshold == rule.threshold.toLong().toDouble()) rule.threshold.toLong().toString() else rule.threshold.toString()
    val duration = if (rule.durationSecs <= 0) "at once" else "for ${formatDuration(rule.durationSecs)}"
    return "${rule.metricEnum.label} ${rule.operatorEnum.symbol} $threshold${rule.metricEnum.unit} $duration · $scope"
}

private fun formatDuration(secs: Int): String = when {
    secs % 3600 == 0 -> "${secs / 3600}h"
    secs % 60 == 0 -> "${secs / 60}m"
    else -> "${secs}s"
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            modifier = Modifier.height(20.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = PulseColors.AccentOn,
                checkedTrackColor = PulseColors.Accent,
                uncheckedThumbColor = PulseColors.TextTertiary,
                uncheckedTrackColor = PulseColors.Track,
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextTertiary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewRuleForm(state: AlertsUiState, vm: AlertsViewModel) {
    val draft = state.newRule
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = PulseColors.TextPrimary,
        unfocusedTextColor = PulseColors.TextPrimary,
        focusedBorderColor = PulseColors.Accent,
        unfocusedBorderColor = PulseColors.Border,
        cursorColor = PulseColors.Accent
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> vm.updateDraft { it.copy(name = name) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Rule name, e.g. “CPU high”") },
            colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))

        Text("METRIC", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        WrapChips(AlertMetric.entries) { metric ->
            PickChip(metric.label, draft.metric == metric) { vm.updateDraft { it.copy(metric = metric) } }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlertOperator.entries.forEach { op ->
                PickChip(op.symbol, draft.operator == op, Modifier.weight(1f)) { vm.updateDraft { it.copy(operator = op) } }
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.threshold,
            onValueChange = { v -> vm.updateDraft { it.copy(threshold = v) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Threshold${if (draft.metric.unit.isNotEmpty()) " (${draft.metric.unit})" else ""}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))

        Text("FOR AT LEAST", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "AT ONCE", 5 to "5 MIN", 15 to "15 MIN", 60 to "1 HOUR").forEach { (mins, label) ->
                PickChip(label, draft.durationMinutes == mins, Modifier.weight(1f)) { vm.updateDraft { it.copy(durationMinutes = mins) } }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("SEVERITY", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlertSeverity.entries.forEach { sev ->
                PickChip(sev.label, draft.severity == sev, Modifier.weight(1f), sev.color()) { vm.updateDraft { it.copy(severity = sev) } }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("APPLIES TO", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        WrapChips(listOf<Long?>(null) + state.agents.map { it.id }) { agentId ->
            val label = agentId?.let { id -> state.agents.firstOrNull { it.id == id }?.hostname ?: "#$id" } ?: "EVERY HOST"
            PickChip(label, draft.agentId == agentId) { vm.updateDraft { it.copy(agentId = agentId) } }
        }
        Spacer(Modifier.height(12.dp))

        LabeledSwitch("PUSH NOTIFICATION WHEN THIS FIRES", draft.notify, true) { vm.updateDraft { it.copy(notify = !it.notify) } }

        state.createRuleError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 11.5.sp, color = PulseColors.Accent)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormButton(if (state.creatingRule) "…" else "CREATE RULE", primary = true, enabled = !state.creatingRule) { vm.createRule() }
            FormButton("CANCEL", primary = false, enabled = !state.creatingRule) { vm.cancelNewRule() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> WrapChips(items: List<T>, chip: @Composable (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item -> chip(item) }
    }
}

@Composable
private fun PickChip(text: String, selected: Boolean, modifier: Modifier = Modifier, tint: Color = PulseColors.TextPrimary, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, textAlign = TextAlign.Center,
        color = if (selected) PulseColors.Background else tint,
        modifier = modifier
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.background(tint) else Modifier.border(1.dp, if (tint != PulseColors.TextPrimary) tint else PulseColors.Border))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}
