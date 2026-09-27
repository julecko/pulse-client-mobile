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
import sk.dilino.pulseclientmobile.data.model.AlertSource
import sk.dilino.pulseclientmobile.data.model.GeoAlertSettings
import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting
import sk.dilino.pulseclientmobile.ui.components.SectionLabel
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.util.formatAgo
import sk.dilino.pulseclientmobile.util.formatDuration
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
            if (state.alerts.isEmpty()) "No alerts yet. Set up rules, offline checks or geo alerts in the RULES tab and they will fire here."
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
                    alert.hostname ?: state.hostnameOf(alert.agentId) ?: "all hosts",
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                sourceTag(alert.source)?.let { tag ->
                    Text(
                        tag,
                        fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextSecondary,
                        modifier = Modifier.border(1.dp, PulseColors.Border).padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
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
                alert.geo?.let { geo ->
                    DetailRow("LOGIN", if (geo.isFailure) "failed SSH login" else "SSH login")
                    DetailRow("USER", geo.user)
                    DetailRow("FROM", geo.ip)
                    DetailRow("LOCATION", geo.location)
                }
                DetailRow("TRIGGERED", formatServerDateTime(alert.triggeredAt) + " UTC")
                if (alert.resolvedAt != null) DetailRow("RESOLVED", formatServerDateTime(alert.resolvedAt) + " UTC")
                else DetailRow(
                    "STATUS",
                    when (alert.source) {
                        AlertSource.GEO -> "open until acknowledged"
                        AlertSource.OFFLINE -> "still offline · resolves when metrics arrive"
                        else -> "still active"
                    }
                )
                if (alert.isAcknowledged) {
                    DetailRow("ACKNOWLEDGED", "${alert.acknowledgedBy ?: "someone"} · ${formatServerDateTime(alert.acknowledgedAt!!)} UTC")
                }
                when (alert.source) {
                    AlertSource.DELETED_RULE -> DetailRow("RULE", "deleted")
                    AlertSource.OFFLINE -> DetailRow("SOURCE", "offline check")
                    AlertSource.GEO -> DetailRow("SOURCE", "geo alert")
                    AlertSource.RULE -> Unit
                }

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

    OfflineSection(state, vm)
    GeoSection(state, vm)
}

// ---------------------------------------------------------------- offline alerts

/** Limits offered for the offline check; the agent's default metrics interval is 60 s, so a few of those. */
private val OFFLINE_CHOICES = listOf(null to "OFF", 120 to "2 MIN", 300 to "5 MIN", 900 to "15 MIN", 3600 to "1 HOUR", 86400 to "1 DAY")

@Composable
private fun OfflineSection(state: AlertsUiState, vm: AlertsViewModel) {
    val watchable = state.offline.filter { it.status == "approved" }
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        SectionLabel("OFFLINE ALERTS · ${watchable.count { it.afterSecs != null }} WATCHED")
        Spacer(Modifier.height(4.dp))
        Text(
            "Raise a critical alert when a host sends no metrics for longer than its limit, and push again when it's back. Off for every host until set.",
            fontSize = 11.5.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
    when {
        !state.offlineLoaded -> Text("Loading…", fontSize = 12.sp, color = PulseColors.TextTertiary, modifier = Modifier.padding(18.dp))
        watchable.isEmpty() -> Text(
            state.offlineError ?: "No approved hosts yet. Only approved hosts can be watched.",
            fontSize = 12.sp, lineHeight = 17.sp,
            color = if (state.offlineError != null) PulseColors.Accent else PulseColors.TextTertiary,
            modifier = Modifier.padding(18.dp)
        )
        else -> {
            watchable.forEach { setting -> OfflineRow(setting, state.offlineBusyId == setting.agentId, vm) }
            state.offlineError?.let {
                Text(it, fontSize = 11.5.sp, color = PulseColors.Accent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun OfflineRow(setting: OfflineAlertSetting, busy: Boolean, vm: AlertsViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(setting.hostname, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
            if (setting.offline) {
                Text(
                    "OFFLINE", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.Accent,
                    modifier = Modifier.border(1.dp, PulseColors.Accent).padding(horizontal = 5.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (busy) "saving…" else buildList {
                add(setting.afterSecs?.let { "alert after ${formatDuration(it)} quiet" } ?: "not watched")
                add(setting.lastMetricsAt?.let { "last metrics ${formatAgo(it)}" } ?: "no metrics yet")
            }.joinToString(" · "),
            fontSize = 11.5.sp, color = PulseColors.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        val choices = OFFLINE_CHOICES.let { list ->
            // Keep a limit set elsewhere (e.g. with pulse-server-cli) visible and selected.
            val current = setting.afterSecs
            if (current != null && list.none { it.first == current }) list + (current to formatDuration(current).uppercase()) else list
        }
        WrapChips(choices) { (secs, label) ->
            PickChip(label, setting.afterSecs == secs, Modifier.padding(bottom = 6.dp)) {
                if (!busy && setting.afterSecs != secs) vm.setOfflineAlert(setting.agentId, secs)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Divider))
}

// ---------------------------------------------------------------- geo alerts

@Composable
private fun GeoSection(state: AlertsUiState, vm: AlertsViewModel) {
    val geo = state.geo
    Spacer(Modifier.height(18.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        SectionLabel("GEO ALERTS")
        Spacer(Modifier.height(4.dp))
        Text(
            "Alert when someone logs in over SSH from a country you didn't allow. Needs the PAM hook on the host; logins from private and VPN addresses are never checked.",
            fontSize = 11.5.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
        )
        Spacer(Modifier.height(12.dp))

        if (geo == null) {
            Text(
                state.geoError ?: "Loading…", fontSize = 12.sp,
                color = if (state.geoError != null) PulseColors.Accent else PulseColors.TextTertiary
            )
        } else {
            GeoSettingsForm(geo, state, vm)
        }
    }
}

@Composable
private fun GeoSettingsForm(geo: GeoAlertSettings, state: AlertsUiState, vm: AlertsViewModel) {
    val draft = state.geoDraft
    Column {
        val db = geo.database
        if (db == null) {
            Text(
                "No GeoIP database is loaded on the server, so no logins are checked whatever you set here. Install GeoLite2-City.mmdb (see the server README) and restart pulse-serverd.",
                fontSize = 11.5.sp, lineHeight = 16.sp, color = PulseColors.Warning
            )
        } else {
            Text("${db.databaseType} · built ${formatServerDateTime(db.builtAt)} UTC", fontSize = 11.sp, color = PulseColors.TextSecondary)
        }
        Spacer(Modifier.height(12.dp))

        Text("ALLOWED COUNTRIES", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        if (draft.allowedCountries.isEmpty()) {
            Text("None — geo alerts are off. Add the countries logins normally come from.", fontSize = 11.5.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary)
        } else {
            WrapChips(draft.allowedCountries) { code ->
                PickChip("$code  ×", selected = false, modifier = Modifier.padding(bottom = 6.dp)) { vm.removeGeoCountry(code) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.input,
                onValueChange = { v -> vm.updateGeoDraft { it.copy(input = v.take(2).uppercase()) } },
                modifier = Modifier.width(96.dp),
                singleLine = true,
                placeholder = { Text("SK") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PulseColors.TextPrimary,
                    unfocusedTextColor = PulseColors.TextPrimary,
                    focusedBorderColor = PulseColors.Accent,
                    unfocusedBorderColor = PulseColors.Border,
                    cursorColor = PulseColors.Accent
                )
            )
            Spacer(Modifier.width(8.dp))
            FormButton("ADD", primary = false, enabled = draft.input.isNotBlank()) { vm.addGeoCountry() }
        }
        Spacer(Modifier.height(12.dp))

        LabeledSwitch("ALSO FAILED LOGINS", draft.includeFailures, !state.geoSaving) { vm.updateGeoDraft { it.copy(includeFailures = !it.includeFailures) } }
        Spacer(Modifier.height(8.dp))
        LabeledSwitch("PUSH NOTIFICATION", draft.notify, !state.geoSaving) { vm.updateGeoDraft { it.copy(notify = !it.notify) } }
        Spacer(Modifier.height(6.dp))
        Text(
            "Failed logins from abroad are constant on a server open to the internet, so they're off by default. A successful login is critical, a failed one a warning; either resolves when acknowledged.",
            fontSize = 11.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
        )

        state.geoSaveError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 11.5.sp, color = PulseColors.Accent)
        }
        if (state.geoDirty) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormButton(if (state.geoSaving) "…" else "SAVE", primary = true, enabled = !state.geoSaving) { vm.saveGeo() }
                FormButton("DISCARD", primary = false, enabled = !state.geoSaving) { vm.resetGeoDraft() }
            }
        }
        geo.updatedBy?.let {
            Spacer(Modifier.height(10.dp))
            Text("last changed by $it · ${formatServerDateTime(geo.updatedAt)} UTC", fontSize = 10.5.sp, color = PulseColors.TextTertiary)
        }
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

private fun sourceTag(source: AlertSource): String? = when (source) {
    AlertSource.GEO -> "GEO"
    AlertSource.OFFLINE -> "OFFLINE"
    AlertSource.RULE, AlertSource.DELETED_RULE -> null
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
