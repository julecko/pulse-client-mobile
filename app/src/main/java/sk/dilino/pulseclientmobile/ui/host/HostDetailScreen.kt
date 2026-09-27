package sk.dilino.pulseclientmobile.ui.host

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentLifecycle
import sk.dilino.pulseclientmobile.data.model.AuthEventKind
import sk.dilino.pulseclientmobile.data.model.AuthEventRecord
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.ui.components.EmptyPlaceholder
import sk.dilino.pulseclientmobile.ui.components.LineChart
import sk.dilino.pulseclientmobile.ui.components.Meter
import sk.dilino.pulseclientmobile.ui.components.Series
import sk.dilino.pulseclientmobile.ui.components.Severity
import sk.dilino.pulseclientmobile.ui.components.SeverityMarker
import sk.dilino.pulseclientmobile.ui.components.Sparkline
import sk.dilino.pulseclientmobile.ui.components.StatusPill
import sk.dilino.pulseclientmobile.ui.components.color
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.util.cpuPercent
import sk.dilino.pulseclientmobile.util.diskPercent
import sk.dilino.pulseclientmobile.util.formatBytes
import sk.dilino.pulseclientmobile.util.formatClock
import sk.dilino.pulseclientmobile.util.formatPercent
import sk.dilino.pulseclientmobile.util.formatRelative
import sk.dilino.pulseclientmobile.util.formatServerDateTime
import sk.dilino.pulseclientmobile.util.formatServerTime
import sk.dilino.pulseclientmobile.util.formatSigned
import sk.dilino.pulseclientmobile.util.formatUptime
import sk.dilino.pulseclientmobile.util.memPercent
import sk.dilino.pulseclientmobile.util.parseServerMillis
import sk.dilino.pulseclientmobile.util.severity
import sk.dilino.pulseclientmobile.util.severityOf

private val Hair = PulseColors.BorderSubtle

@Composable
fun HostDetailScreen(
    viewModel: HostDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val agent = state.agent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Background)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = PulseColors.Accent, modifier = Modifier.size(18.dp))
            Text("FLEET", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.Accent)
        }

        if (agent == null) {
            if (state.isLoading) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PulseColors.Accent)
                }
            } else {
                EmptyPlaceholder(
                    icon = Icons.Outlined.Shield,
                    title = "Host not found",
                    message = state.error ?: "This agent may have been removed."
                )
            }
            return
        }

        val shown = state.shown
        val severity = when (agent.lifecycle) {
            AgentLifecycle.PENDING -> Severity.WARNING
            AgentLifecycle.REVOKED -> Severity.CRITICAL
            AgentLifecycle.APPROVED -> shown?.severity ?: Severity.HEALTHY
        }

        HostHeader(agent.hostname, severity, agent.id, agent.fingerprint, agent.lifecycle)

        if (state.snapshots.isNotEmpty()) {
            Timeline(state, viewModel)
        }

        TabBar(state.tab, viewModel::selectTab)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 26.dp)
        ) {
            when (state.tab) {
                HostTab.OVERVIEW -> OverviewTab(state, viewModel, onBack)
                HostTab.CPU -> CpuTab(state)
                HostTab.AUTH -> AuthTab(state)
                HostTab.SNAPSHOTS -> SnapshotsTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun HostHeader(name: String, severity: Severity, id: Long, fingerprint: String, lifecycle: AgentLifecycle) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeverityMarker(severity, size = 10)
            Spacer(Modifier.width(9.dp))
            Text(name, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
            StatusPill(
                when (lifecycle) {
                    AgentLifecycle.PENDING -> "PENDING"
                    AgentLifecycle.REVOKED -> "REVOKED"
                    AgentLifecycle.APPROVED -> when (severity) {
                        Severity.HEALTHY -> "OK"
                        Severity.WARNING -> "WARN"
                        Severity.CRITICAL -> "CRIT"
                    }
                },
                severity
            )
        }
        Spacer(Modifier.height(7.dp))
        Text("#$id · ${fingerprint.take(16)}…", fontSize = 11.5.sp, color = PulseColors.TextSecondary)
    }
}

/** The design's single clock: dragging it rewinds the whole screen to that snapshot. */
@Composable
private fun Timeline(state: HostDetailUiState, vm: HostDetailViewModel) {
    val snaps = state.snapshots
    val shownIdx = state.shownIndex
    val lastMs = parseServerMillis(snaps.last().createdAt) ?: 0L
    val shownMs = parseServerMillis(snaps[shownIdx].createdAt) ?: 0L
    val live = state.isLive
    val relColor = if (live) PulseColors.Accent else PulseColors.Warning

    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(PulseColors.Surface)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(formatClock(shownMs), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(formatRelative(lastMs - shownMs), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = relColor)
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = vm::jumpLive)
                    .border(1.dp, if (live) PulseColors.Accent else PulseColors.Border)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(if (live) PulseColors.Accent else PulseColors.TextSecondary))
                Spacer(Modifier.width(5.dp))
                Text("LIVE", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = if (live) PulseColors.Accent else PulseColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(9.dp))

        val n = snaps.size
        val onScrub by rememberUpdatedState { fraction: Float ->
            vm.scrubTo(Math.round(fraction.coerceIn(0f, 1f) * (n - 1)))
        }
        val cpu = snaps.map { it.cpuPercent ?: 0f }
        val ink = PulseColors.TextPrimary
        val accent = PulseColors.Accent
        val dim = ink.copy(alpha = 0.28f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .pointerInput(n) {
                    detectTapGestures { onScrub(it.x / size.width) }
                }
                .pointerInput(n) {
                    detectHorizontalDragGestures(
                        onDragStart = { onScrub(it.x / size.width) },
                        onHorizontalDrag = { change, _ -> onScrub(change.position.x / size.width) }
                    )
                }
        ) {
            val trackH = 26.dp.toPx()
            fun path(values: List<Float>): androidx.compose.ui.graphics.Path {
                val p = androidx.compose.ui.graphics.Path()
                val step = if (n > 1) size.width / (n - 1) else 0f
                values.forEachIndexed { i, v ->
                    val x = i * step
                    val y = trackH - 2f - (v / 100f).coerceIn(0f, 1f) * (trackH - 4f)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                return p
            }
            drawPath(path(cpu), dim, style = Stroke(1.5.dp.toPx()))
            drawPath(path(cpu.take(shownIdx + 1)), accent, style = Stroke(2.dp.toPx()))
            val x = if (n > 1) shownIdx * size.width / (n - 1) else 0f
            drawLine(ink, Offset(x, 0f), Offset(x, trackH), 2.dp.toPx())
            val sq = 11.dp.toPx()
            drawRect(ink, Offset(x - sq / 2, -3.dp.toPx().coerceAtLeast(0f)), Size(sq, sq))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val startMs = parseServerMillis(snaps.first().createdAt) ?: lastMs
            Text("${formatClock(startMs)}", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextTertiary)
            Text("${snaps.size} SNAPSHOTS", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextTertiary)
            Text("NOW", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextTertiary)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hair))
}

@Composable
private fun TabBar(selected: HostTab, onSelect: (HostTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(PulseColors.Background)) {
        HostTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = if (active) PulseColors.TextPrimary else PulseColors.TextTertiary,
                    modifier = Modifier.padding(top = 11.dp, bottom = 9.dp)
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (active) PulseColors.Accent else Color.Transparent))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun Cap(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = PulseColors.TextSecondary, modifier = modifier)
}

@Composable
private fun Rule(color: Color = Hair) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
private fun NoData(text: String) {
    Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = PulseColors.TextTertiary, modifier = Modifier.padding(18.dp))
}

// ---------------------------------------------------------------- overview

@Composable
private fun OverviewTab(state: HostDetailUiState, vm: HostDetailViewModel, onBack: () -> Unit) {
    val agent = state.agent ?: return
    val m = state.shown

    if (m == null) {
        NoData(
            when (agent.lifecycle) {
                AgentLifecycle.APPROVED -> "No metrics received yet — the agent reports on its own interval."
                AgentLifecycle.PENDING -> "Approve this agent to start receiving its metrics."
                AgentLifecycle.REVOKED -> "This agent is revoked. Unrevoke only if you're sure its secret never leaked — otherwise remove it and run pulse-agent-cli reset-identity on the host to pair it anew."
            }
        )
    } else {
        val mem = m.metrics.memory
        val cpu = m.metrics.cpu
        val worstDisk = m.metrics.disks.filter { it.totalBytes > 0 }.let { d -> d.filter { !it.removable }.ifEmpty { d } }.maxByOrNull { it.usedPercent }

        Row(Modifier.fillMaxWidth()) {
            Gauge("CPU", m.cpuPercent, cpu?.let { "${it.coreCount} cores" } ?: "", Modifier.weight(1f))
            Gauge("MEM", m.memPercent, mem?.let { "${formatBytes(it.usedBytes)} / ${formatBytes(it.totalBytes)}" } ?: "", Modifier.weight(1f))
            Gauge("DISK", m.diskPercent, worstDisk?.mountPoint ?: "", Modifier.weight(1f), last = true)
        }
        Rule()

        m.metrics.linux?.let { l ->
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Cap("LOAD AVERAGE")
                Spacer(Modifier.height(12.dp))
                Row {
                    Load("%.2f".format(l.loadAvgOne), "1 MIN", Modifier.weight(1f))
                    Load("%.2f".format(l.loadAvgFive), "5 MIN", Modifier.weight(1f))
                    Load("%.2f".format(l.loadAvgFifteen), "15 MIN", Modifier.weight(1f))
                }
            }
            Rule()
        }

        if (m.metrics.disks.isNotEmpty()) {
            Cap("FILESYSTEMS", Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp))
            m.metrics.disks.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(d.mountPoint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary, maxLines = 1, modifier = Modifier.width(76.dp))
                    Meter(d.usedPercent, severityOf(d.usedPercent).color(), Modifier.weight(1f))
                    Text(
                        "${formatBytes(d.usedBytes)} / ${formatBytes(d.totalBytes)}",
                        fontSize = 10.5.sp, color = PulseColors.TextSecondary, textAlign = TextAlign.End,
                        modifier = Modifier.width(112.dp)
                    )
                }
                Rule(PulseColors.Divider)
            }
        }

        Cap("HOST FACTS", Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 2.dp))
        Fact("UPTIME", m.metrics.linux?.let { formatUptime(it.uptimeSecs) } ?: "—")
        Fact("CORES", cpu?.coreCount?.toString() ?: "—")
        Fact("MEMORY", mem?.let { formatBytes(it.totalBytes) } ?: "—")
        Fact("SWAP", mem?.takeIf { it.swapTotalBytes > 0 }?.let { "${formatBytes(it.swapUsedBytes)} / ${formatBytes(it.swapTotalBytes)}" } ?: "none")
        Fact("SNAPSHOT", formatServerDateTime(m.createdAt) + " UTC")
        Fact("PAIRED", formatServerDateTime(agent.createdAt) + " UTC")
        Fact("FINGERPRINT", agent.fingerprint)
    }

    if (agent.lifecycle == AgentLifecycle.APPROVED) {
        OfflineAlertSection(state, vm)
    }

    Cap("AGENT", Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp))
    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (agent.lifecycle) {
            AgentLifecycle.PENDING -> {
                ActionButton("APPROVE", true, state.actionInFlight, vm::approve)
                ActionButton("REJECT", false, state.actionInFlight) { vm.remove(onBack) }
            }
            AgentLifecycle.APPROVED -> {
                ActionButton("REVOKE", false, state.actionInFlight, vm::revoke)
                ActionButton(if (state.confirmRemove) "CONFIRM REMOVE" else "REMOVE", state.confirmRemove, state.actionInFlight) { vm.requestRemove(onBack) }
            }
            AgentLifecycle.REVOKED -> {
                ActionButton("UNREVOKE", false, state.actionInFlight, vm::unrevoke)
                ActionButton("REMOVE", false, state.actionInFlight) { vm.remove(onBack) }
            }
        }
    }
}

/** Limits offered for the offline alert, in seconds; the server takes 60 s to 30 days. */
private val OFFLINE_PRESETS = listOf("2M" to 120L, "5M" to 300L, "15M" to 900L, "1H" to 3600L)

/** Alert (and push) when the agent sends no metrics for the chosen time. */
@Composable
private fun OfflineAlertSection(state: HostDetailUiState, vm: HostDetailViewModel) {
    val setting = state.offlineAlert
    val after = setting?.afterSecs
    Cap("OFFLINE ALERT", Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp))
    Text(
        text = when {
            setting == null -> "Loading…"
            after == null -> "Off — no alert if this host stops sending metrics."
            setting.offline -> "OFFLINE — no metrics for over ${formatUptime(after)}."
            else -> "Alerts if no metrics arrive for ${formatUptime(after)}."
        },
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = if (setting?.offline == true) Severity.CRITICAL.color() else PulseColors.TextSecondary,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
    )
    if (setting != null) {
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("OFF", after == null, state.actionInFlight) { vm.setOfflineAlert(null) }
            OFFLINE_PRESETS.forEach { (label, secs) ->
                ActionButton(label, after == secs, state.actionInFlight) { vm.setOfflineAlert(secs) }
            }
        }
    }
}

@Composable
private fun Gauge(label: String, percent: Float?, sub: String, modifier: Modifier = Modifier, last: Boolean = false) {
    val tint = severityOf(percent).color()
    Row(modifier) {
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 15.dp)) {
            Cap(label)
            Spacer(Modifier.height(8.dp))
            Text(formatPercent(percent), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text(sub, fontSize = 10.sp, color = PulseColors.TextTertiary, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Meter(percent ?: 0f, tint, height = 4.dp)
        }
        if (!last) Box(Modifier.width(1.dp).height(112.dp).background(Hair))
    }
}

@Composable
private fun Load(value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier) {
        Box(Modifier.width(1.dp).height(38.dp).background(Hair))
        Column(Modifier.padding(start = 12.dp)) {
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = PulseColors.TextTertiary)
        }
    }
}

@Composable
private fun Fact(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(key, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = PulseColors.TextTertiary, modifier = Modifier.width(104.dp))
        Text(value, fontSize = 12.sp, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
    }
    Rule(PulseColors.Divider)
}

@Composable
private fun ActionButton(text: String, primary: Boolean, busy: Boolean, onClick: () -> Unit) {
    Text(
        text = if (busy) "…" else text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = if (primary) PulseColors.AccentOn else PulseColors.TextPrimary,
        modifier = Modifier
            .clickable(enabled = !busy, onClick = onClick)
            .then(if (primary) Modifier.background(PulseColors.Accent) else Modifier.border(1.dp, PulseColors.Border))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------- cpu

@Composable
private fun CpuTab(state: HostDetailUiState) {
    val m = state.shown
    val cpu = m?.metrics?.cpu
    if (m == null || cpu == null) {
        NoData("No CPU data for this snapshot.")
        return
    }
    val tint = severityOf(cpu.globalUsagePercent).color()

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Cap("CPU · ${cpu.coreCount} CORES")
            Spacer(Modifier.height(9.dp))
            Text(formatPercent(cpu.globalUsagePercent), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = tint)
        }
        m.metrics.linux?.let {
            Text(
                "load ${"%.2f".format(it.loadAvgOne)} · ${"%.2f".format(it.loadAvgFive)} · ${"%.2f".format(it.loadAvgFifteen)}",
                fontSize = 11.sp, color = PulseColors.TextSecondary, textAlign = TextAlign.End
            )
        }
    }
    Rule()

    if (cpu.perCoreUsagePercent.isNotEmpty()) {
        Cap("PER CORE", Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 18.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            cpu.perCoreUsagePercent.forEach { v ->
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((v / 100f).coerceIn(0.02f, 1f))
                            .background(severityOf(v).color())
                    )
                }
            }
        }
        cpu.perCoreUsagePercent.forEachIndexed { i, v ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CPU$i", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = PulseColors.TextTertiary, modifier = Modifier.width(52.dp))
                Meter(v, severityOf(v).color(), Modifier.weight(1f), height = 5.dp)
                Text("${v.toInt()}%", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
            Rule(PulseColors.Divider)
        }
    }

    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Cap("HISTORY · CPU / MEM · ${state.snapshots.size} SNAPSHOTS")
        Spacer(Modifier.height(10.dp))
        LineChart(
            series = listOf(
                Series(state.snapshots.map { it.memPercent ?: 0f }, PulseColors.TextTertiary),
                Series(state.snapshots.map { it.cpuPercent ?: 0f }, PulseColors.TextPrimary)
            ),
            modifier = Modifier.fillMaxWidth().height(80.dp),
            baseline = true
        )
    }
}

// ---------------------------------------------------------------- auth

@Composable
private fun AuthTab(state: HostDetailUiState) {
    Cap("AUTH LOG · LAST 100", Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp))
    if (state.authEvents.isEmpty()) {
        NoData("PAM session and auth-failure events forwarded by this agent will appear here.")
        return
    }
    state.authEvents.forEach { e ->
        val sev = if (e.eventKind == AuthEventKind.AUTH_FAILURE) Severity.CRITICAL else Severity.HEALTHY
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatServerTime(e.occurredAt), fontSize = 10.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = PulseColors.TextTertiary, modifier = Modifier.width(42.dp))
            Text(
                eventTag(e.eventKind),
                fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = sev.color(), textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp).border(1.dp, sev.color()).padding(vertical = 3.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(eventDescription(e), fontSize = 11.5.sp, lineHeight = 15.sp, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
        }
        Rule(PulseColors.Divider)
    }
}

private fun eventTag(kind: AuthEventKind) = when (kind) {
    AuthEventKind.AUTH_FAILURE -> "FAIL"
    AuthEventKind.SESSION_OPEN -> "OPEN"
    AuthEventKind.SESSION_CLOSE -> "CLOSE"
}

private fun eventDescription(event: AuthEventRecord): String {
    val actor = if (event.ruser != null && event.ruser != event.user) "${event.user} (as ${event.ruser})" else event.user
    val from = event.rhost?.let { " from $it" } ?: ""
    return when (event.eventKind) {
        AuthEventKind.AUTH_FAILURE -> "${event.service}: auth failed for $actor$from"
        AuthEventKind.SESSION_OPEN -> "${event.service}: session opened for $actor$from"
        AuthEventKind.SESSION_CLOSE -> "${event.service}: session closed for $actor"
    }
}

// ---------------------------------------------------------------- snapshots

@Composable
private fun SnapshotsTab(state: HostDetailUiState, vm: HostDetailViewModel) {
    val snaps = state.snapshots
    if (snaps.size < 2) {
        NoData("Snapshots to compare appear once this agent has reported at least twice.")
        return
    }
    val lastMs = parseServerMillis(snaps.last().createdAt) ?: 0L
    fun clock(i: Int) = formatClock(parseServerMillis(snaps[i].createdAt) ?: 0L)
    fun rel(i: Int) = formatRelative(lastMs - (parseServerMillis(snaps[i].createdAt) ?: 0L))

    val a = state.shownIndex
    val b = state.compareShown

    Column(Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
        Cap("COMPARE TWO SNAPSHOTS")
        Spacer(Modifier.height(6.dp))
        Text("Drag the clock to set A · tap a row to set B", fontSize = 11.sp, color = PulseColors.TextTertiary)
    }
    Row(Modifier.fillMaxWidth().border(width = 1.dp, color = Hair)) {
        SnapCell("A · BASE", clock(a), rel(a), PulseColors.TextTertiary, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(72.dp).background(Hair))
        SnapCell("B · COMPARE", clock(b), rel(b), PulseColors.Accent, Modifier.weight(1f))
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))

    val ra = snaps[a]
    val rb = snaps[b]
    DiffRow("CPU", ra.cpuPercent, rb.cpuPercent, "%")
    DiffRow("MEMORY", ra.memPercent, rb.memPercent, "%")
    DiffRow("DISK", ra.diskPercent, rb.diskPercent, "%")
    DiffRow("LOAD 1M", ra.metrics.linux?.loadAvgOne?.toFloat(), rb.metrics.linux?.loadAvgOne?.toFloat(), "")
    DiffRow(
        "SWAP",
        ra.metrics.memory?.takeIf { it.swapTotalBytes > 0 }?.let { it.swapUsedBytes * 100f / it.swapTotalBytes },
        rb.metrics.memory?.takeIf { it.swapTotalBytes > 0 }?.let { it.swapUsedBytes * 100f / it.swapTotalBytes },
        "%"
    )

    Cap("SNAPSHOT HISTORY", Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp))
    for (i in snaps.indices.reversed()) {
        val s = snaps[i]
        val sev = s.severity
        val marks = when (i) { a -> PulseColors.TextPrimary; b -> PulseColors.Accent; else -> Color.Transparent }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.compareWith(i) }
                .background(if (i == a || i == b) PulseColors.Track else Color.Transparent)
                .padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(2.dp).height(38.dp).background(marks))
            Spacer(Modifier.width(16.dp))
            Text(clock(i), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary, modifier = Modifier.width(44.dp))
            Text(rel(i), fontSize = 10.sp, color = PulseColors.TextTertiary, modifier = Modifier.width(48.dp))
            Sparkline(
                values = snaps.subList((i - 12).coerceAtLeast(0), i + 1).map { it.cpuPercent ?: 0f },
                color = sev.color(),
                modifier = Modifier.size(width = 52.dp, height = 18.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "cpu ${formatPercent(s.cpuPercent)} · mem ${formatPercent(s.memPercent)}",
                fontSize = 10.5.sp, color = PulseColors.TextSecondary
            )
            Spacer(Modifier.width(10.dp))
            SeverityMarker(sev, size = 8)
        }
        Rule(PulseColors.Divider)
    }
}

@Composable
private fun SnapCell(title: String, clock: String, rel: String, titleColor: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = titleColor)
        Spacer(Modifier.height(7.dp))
        Text(clock, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary)
        Spacer(Modifier.height(5.dp))
        Text(rel, fontSize = 10.5.sp, color = PulseColors.TextSecondary)
    }
}

@Composable
private fun DiffRow(label: String, a: Float?, b: Float?, unit: String) {
    val delta = if (a != null && b != null) b - a else null
    val fmt = { v: Float? -> if (v == null) "—" else if (unit == "%") "${v.toInt()}%" else "%.2f".format(v) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = PulseColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(fmt(a), fontSize = 12.sp, color = PulseColors.TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(58.dp))
        Text(fmt(b), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(58.dp))
        Text(
            text = delta?.let { formatSigned(it, if (unit == "%") " pt" else "") } ?: "—",
            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End,
            color = when {
                delta == null || kotlin.math.abs(delta) < 0.05f -> PulseColors.TextTertiary
                delta > 0 -> PulseColors.Accent
                else -> PulseColors.TextPrimary
            },
            modifier = Modifier.width(72.dp)
        )
    }
    Rule(PulseColors.Divider)
}
