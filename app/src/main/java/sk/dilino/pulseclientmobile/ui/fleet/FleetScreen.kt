package sk.dilino.pulseclientmobile.ui.fleet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.ui.components.EmptyPlaceholder
import sk.dilino.pulseclientmobile.ui.components.Meter
import sk.dilino.pulseclientmobile.ui.components.Severity
import sk.dilino.pulseclientmobile.ui.components.SeverityMarker
import sk.dilino.pulseclientmobile.ui.components.Sparkline
import sk.dilino.pulseclientmobile.ui.components.StatusPill
import sk.dilino.pulseclientmobile.ui.components.color
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.util.cpuPercent
import sk.dilino.pulseclientmobile.util.diskPercent
import sk.dilino.pulseclientmobile.util.formatAgo
import sk.dilino.pulseclientmobile.util.formatPercent
import sk.dilino.pulseclientmobile.util.formatServerDateTime
import sk.dilino.pulseclientmobile.util.formatUptime
import sk.dilino.pulseclientmobile.util.memPercent
import sk.dilino.pulseclientmobile.util.severityOf

private fun Severity.pillLabel() = when (this) {
    Severity.HEALTHY -> "OK"
    Severity.WARNING -> "WARN"
    Severity.CRITICAL -> "CRIT"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetScreen(
    viewModel: FleetViewModel,
    onOpenAgent: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Background)
    ) {
        FleetHeader(state)

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.error != null && state.agents.isEmpty() -> EmptyPlaceholder(
                    icon = Icons.Outlined.Dns,
                    title = "Can't reach the fleet",
                    message = state.error.orEmpty()
                )
                state.agents.isEmpty() && !state.isLoading -> EmptyPlaceholder(
                    icon = Icons.Outlined.Dns,
                    title = "No hosts yet",
                    message = "Open pairing in Settings, then start the agent on a machine — it will show up here."
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.agents, key = { it.id }) { agent ->
                        HostRow(
                            agent = agent,
                            severity = state.severityOf(agent),
                            offline = state.isOffline(agent),
                            snapshots = state.metrics[agent.id].orEmpty(),
                            busy = state.actionInFlightId == agent.id,
                            onClick = { onOpenAgent(agent.id) },
                            onApprove = { viewModel.approve(agent.id) },
                            onRemove = { viewModel.remove(agent.id) }
                        )
                    }
                    item {
                        Text(
                            text = "Pull to refresh · agent poll every 10s",
                            fontSize = 11.sp,
                            color = PulseColors.TextTertiary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FleetHeader(state: FleetUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "FLEET",
                style = MaterialTheme.typography.headlineLarge,
                color = PulseColors.TextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (state.isReachable) PulseColors.Accent else PulseColors.TextTertiary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.isReachable) "LIVE" else "OFFLINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                    color = PulseColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = PulseColors.BorderSubtle)
                .padding(vertical = 12.dp)
        ) {
            SummaryStat("HOSTS UP", state.upCount, false, Modifier.weight(1f).padding(start = 18.dp))
            SummaryStat("ATTENTION", state.attentionCount, state.attentionCount > 0, Modifier.weight(1f).padding(start = 14.dp))
            SummaryStat("PENDING", state.pendingCount, state.pendingCount > 0, Modifier.weight(1f).padding(start = 14.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
    }
}

@Composable
private fun SummaryStat(label: String, value: Int, hot: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (hot) PulseColors.Accent else PulseColors.TextPrimary
        )
        Spacer(Modifier.height(5.dp))
        Text(text = label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextSecondary)
    }
}

@Composable
private fun HostRow(
    agent: AgentSummary,
    severity: Severity,
    offline: Boolean,
    snapshots: List<MetricsRecord>,
    busy: Boolean,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onRemove: () -> Unit
) {
    val latest = snapshots.lastOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeverityMarker(severity)
            Spacer(Modifier.width(9.dp))
            Text(agent.hostname, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary)
            Spacer(Modifier.width(9.dp))
            StatusPill(
                when (agent.lifecycle) {
                    AgentLifecycle.PENDING -> "PENDING"
                    AgentLifecycle.REVOKED -> "REVOKED"
                    AgentLifecycle.APPROVED -> if (offline) "OFFLINE" else severity.pillLabel()
                },
                severity
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PulseColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildList {
                add("#${agent.id}")
                if (offline && latest != null) add("last seen ${formatAgo(latest.createdAt)}")
                latest?.metrics?.cpu?.coreCount?.takeIf { it > 0 }?.let { add("$it cores") }
                latest?.metrics?.linux?.let { add("up ${formatUptime(it.uptimeSecs)}") }
                if (latest == null) add("paired ${formatServerDateTime(agent.createdAt)}")
            }.joinToString("  /  "),
            fontSize = 11.sp,
            color = PulseColors.TextSecondary
        )

        when {
            latest != null -> {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UsageBar("CPU", latest.cpuPercent)
                        UsageBar("MEM", latest.memPercent)
                        UsageBar("DSK", latest.diskPercent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Sparkline(
                        values = snapshots.mapNotNull { it.cpuPercent },
                        color = severity.color(),
                        modifier = Modifier.size(width = 86.dp, height = 40.dp)
                    )
                }
            }
            agent.lifecycle == AgentLifecycle.APPROVED -> {
                Spacer(Modifier.height(10.dp))
                Text("Waiting for the first metrics report…", fontSize = 11.sp, color = PulseColors.TextTertiary)
            }
            agent.lifecycle == AgentLifecycle.PENDING -> {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RowAction(if (busy) "…" else "APPROVE", PulseColors.Accent, !busy, onApprove)
                    RowAction(if (busy) "…" else "REJECT", PulseColors.TextSecondary, !busy, onRemove)
                }
            }
            else -> {
                Spacer(Modifier.height(10.dp))
                RowAction(if (busy) "…" else "REMOVE", PulseColors.TextSecondary, !busy, onRemove)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.BorderSubtle))
}

@Composable
private fun UsageBar(label: String, percent: Float?) {
    val tint = severityOf(percent).color()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextTertiary, modifier = Modifier.width(30.dp))
        Meter(percent ?: 0f, tint, Modifier.weight(1f))
        Text(
            text = formatPercent(percent),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PulseColors.TextPrimary,
            modifier = Modifier.width(38.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun RowAction(text: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = tint,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .border(1.dp, tint.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}
