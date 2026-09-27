package sk.dilino.pulseclientmobile.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.PairingStatus
import sk.dilino.pulseclientmobile.data.model.PushDevice
import sk.dilino.pulseclientmobile.ui.components.SectionLabel
import sk.dilino.pulseclientmobile.ui.components.Severity
import sk.dilino.pulseclientmobile.ui.components.SeverityMarker
import sk.dilino.pulseclientmobile.ui.components.StatusPill
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.update.AppUpdater
import sk.dilino.pulseclientmobile.update.UpdateState
import sk.dilino.pulseclientmobile.update.UpdateStatus
import sk.dilino.pulseclientmobile.update.describe
import sk.dilino.pulseclientmobile.update.installedVersionLabel
import sk.dilino.pulseclientmobile.util.formatServerDateTime

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenHost: (Long) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by AppUpdater.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.Background)
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)) {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineLarge,
                color = PulseColors.TextPrimary
            )
            Spacer(Modifier.height(7.dp))
            Text("Signed in as ${state.username} · Pulse ${installedVersionLabel(update)}", fontSize = 11.sp, color = PulseColors.TextTertiary)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            ServerSection(state)
            Divider()
            PairingSection(state, viewModel)
            Divider()
            HostsSection(state, viewModel, onOpenHost)
            Divider()
            NotificationsSection(state, viewModel)
            Divider()
            AppUpdateSection(viewModel)
            Divider()
            AccountSection(viewModel)
            Text(
                text = "Sentry · read-only fleet monitor · ${installedVersionLabel(update, withCode = true)}",
                fontSize = 10.5.sp,
                color = PulseColors.TextTertiary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.BorderSubtle))

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        SectionLabel(title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    tint: Color = PulseColors.TextPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        textAlign = TextAlign.Center,
        color = when {
            !enabled -> PulseColors.TextTertiary
            primary -> PulseColors.AccentOn
            else -> tint
        },
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (primary && enabled) Modifier.background(PulseColors.Accent) else Modifier.border(1.dp, if (enabled && tint != PulseColors.TextPrimary) tint else PulseColors.Border))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    )
}

// ---------------------------------------------------------------- server

@Composable
private fun ServerSection(state: SettingsUiState) {
    Section("SERVER") {
        Text(state.serverUrl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        if (state.pinnedCert != null) {
            Text("TLS · pinned certificate (self-signed)", fontSize = 11.5.sp, color = PulseColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(state.pinnedCert, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = PulseColors.TextTertiary)
        } else {
            Text("TLS · certificate verified by a trusted authority", fontSize = 11.5.sp, color = PulseColors.TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text("To use another server, sign out below.", fontSize = 11.sp, color = PulseColors.TextTertiary)
    }
}

// ---------------------------------------------------------------- pairing

@Composable
private fun PairingSection(state: SettingsUiState, vm: SettingsViewModel) {
    val pairing = state.pairing
    Section("AGENT PAIRING") {
        if (pairing == null) {
            Text(state.pairingError ?: "Loading…", fontSize = 12.sp, color = if (state.pairingError != null) PulseColors.Accent else PulseColors.TextTertiary)
            return@Section
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeverityMarker(if (pairing.open) Severity.CRITICAL else Severity.HEALTHY)
            Spacer(Modifier.width(9.dp))
            Text(
                if (pairing.open) "OPEN" else "CLOSED",
                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                color = if (pairing.open) PulseColors.Accent else PulseColors.TextPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(pairingSummary(pairing), fontSize = 11.5.sp, lineHeight = 17.sp, color = PulseColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            "New agents can only send a pairing request while this is open. Hosts the server already knows are unaffected.",
            fontSize = 11.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
        )
        Spacer(Modifier.height(12.dp))
        Text("OPEN FOR", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = PulseColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val enabled = !state.pairingBusy
            Chip("15 MIN", Modifier.weight(1f), enabled = enabled) { vm.setPairing(true, 15) }
            Chip("1 HOUR", Modifier.weight(1f), enabled = enabled) { vm.setPairing(true, 60) }
            Chip("8 HOURS", Modifier.weight(1f), enabled = enabled) { vm.setPairing(true, 480) }
            Chip("NO LIMIT", Modifier.weight(1f), enabled = enabled) { vm.setPairing(true, null) }
        }
        if (pairing.open) {
            Spacer(Modifier.height(8.dp))
            Chip("CLOSE PAIRING", Modifier.fillMaxWidth(), primary = true, enabled = !state.pairingBusy) { vm.setPairing(false) }
        }
        state.pairingError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 11.5.sp, color = PulseColors.Accent)
        }
    }
}

private fun pairingSummary(p: PairingStatus): String {
    val until = when {
        !p.open -> null
        p.openUntil != null -> "closes automatically at ${formatServerDateTime(p.openUntil)} UTC"
        else -> "stays open until you close it"
    }
    val by = p.updatedBy?.let { "last changed by $it · ${formatServerDateTime(p.updatedAt)} UTC" }
    return listOfNotNull(until, by).joinToString("\n").ifEmpty { "Not changed yet" }
}

// ---------------------------------------------------------------- hosts

@Composable
private fun HostsSection(state: SettingsUiState, vm: SettingsViewModel, onOpenHost: (Long) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            SectionLabel("HOSTS · ${state.agents.size}")
            Spacer(Modifier.height(4.dp))
        }
        when {
            !state.agentsLoaded -> Text("Loading…", fontSize = 12.sp, color = PulseColors.TextTertiary, modifier = Modifier.padding(18.dp))
            state.agents.isEmpty() -> Text(
                state.agentsError ?: "No hosts have paired yet. Open pairing above, then run the agent on a machine.",
                fontSize = 12.sp, lineHeight = 17.sp,
                color = if (state.agentsError != null) PulseColors.Accent else PulseColors.TextTertiary,
                modifier = Modifier.padding(18.dp)
            )
            else -> state.agents.forEach { agent -> HostRow(agent, state, vm, onOpenHost) }
        }
        (state.agentActionError ?: state.agentsError.takeIf { state.agents.isNotEmpty() })?.let {
            Text(it, fontSize = 11.5.sp, color = PulseColors.Accent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HostRow(agent: AgentSummary, state: SettingsUiState, vm: SettingsViewModel, onOpenHost: (Long) -> Unit) {
    val busy = state.busyAgentId == agent.id
    val confirming = state.confirmRemoveId == agent.id
    val severity = when (agent.lifecycle) {
        AgentLifecycle.APPROVED -> Severity.HEALTHY
        AgentLifecycle.PENDING -> Severity.WARNING
        AgentLifecycle.REVOKED -> Severity.CRITICAL
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpenHost(agent.id) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeverityMarker(severity, size = 8)
            Spacer(Modifier.width(9.dp))
            Text(agent.hostname, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = PulseColors.TextPrimary, modifier = Modifier.weight(1f))
            StatusPill(agent.status.uppercase(), severity)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "#${agent.id} · ${agent.fingerprint} · paired ${formatServerDateTime(agent.createdAt)}",
            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = PulseColors.TextTertiary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (agent.lifecycle) {
                AgentLifecycle.PENDING -> Chip(if (busy) "…" else "APPROVE", primary = true, enabled = !busy) { vm.approve(agent.id) }
                AgentLifecycle.APPROVED -> Chip(if (busy) "…" else "REVOKE", tint = PulseColors.Warning, enabled = !busy) { vm.revoke(agent.id) }
                AgentLifecycle.REVOKED -> Chip(if (busy) "…" else "UNREVOKE", tint = PulseColors.Warning, enabled = !busy) { vm.unrevoke(agent.id) }
            }
            if (confirming) {
                Chip("CONFIRM REMOVE", primary = true, enabled = !busy) { vm.requestRemove(agent.id) }
                Chip("CANCEL") { vm.cancelRemove() }
            } else {
                Chip("REMOVE", tint = PulseColors.Accent, enabled = !busy) { vm.requestRemove(agent.id) }
            }
        }
        if (confirming) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Removes ${agent.hostname} and all of its stored metrics and auth events. It can pair again as a new request while pairing is open.",
                fontSize = 11.sp, lineHeight = 16.sp, color = PulseColors.Warning
            )
        } else if (agent.lifecycle == AgentLifecycle.REVOKED) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Unrevoke only if you're sure its secret never leaked — anyone with a copy gets access back too. Otherwise remove it and run pulse-agent-cli reset-identity on the host to pair it anew.",
                fontSize = 11.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Divider))
}

// ---------------------------------------------------------------- notifications

@Composable
private fun NotificationsSection(state: SettingsUiState, vm: SettingsViewModel) {
    Section("NOTIFICATIONS") {
        Text(
            "Devices registered to receive alert pushes. This device registers itself automatically once you're signed in and have notified rules with push on.",
            fontSize = 11.5.sp, lineHeight = 17.sp, color = PulseColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        when {
            !state.pushDevicesLoaded -> Text("Loading…", fontSize = 12.sp, color = PulseColors.TextTertiary)
            state.pushDevices.isEmpty() -> Text(
                state.pushDevicesError ?: "No devices registered yet.",
                fontSize = 12.sp, lineHeight = 17.sp,
                color = if (state.pushDevicesError != null) PulseColors.Accent else PulseColors.TextTertiary
            )
            else -> state.pushDevices.forEach { device -> PushDeviceRow(device, state, vm) }
        }
    }
}

@Composable
private fun PushDeviceRow(device: PushDevice, state: SettingsUiState, vm: SettingsViewModel) {
    val busy = state.busyDeviceId == device.id
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name ?: "Unnamed device", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${device.username} · last seen ${formatServerDateTime(device.lastSeenAt)}",
                fontSize = 10.sp, color = PulseColors.TextTertiary
            )
        }
        Chip(if (busy) "…" else "REMOVE", tint = PulseColors.Accent, enabled = !busy) { vm.removePushDevice(device.id) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Divider))
}

// ---------------------------------------------------------------- app updates

@Composable
private fun AppUpdateSection(vm: SettingsViewModel) {
    val update: UpdateState by AppUpdater.state.collectAsStateWithLifecycle()
    val status = update.status
    val context = LocalContext.current
    Section("APP") {
        Text(
            "Installed · Pulse ${installedVersionLabel(update, withCode = true)}",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PulseColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            describe(status), fontSize = 11.5.sp, lineHeight = 17.sp,
            color = if (status is UpdateStatus.Failed) PulseColors.Accent else PulseColors.TextSecondary
        )
        val release = when (status) {
            is UpdateStatus.Available -> status.release
            is UpdateStatus.NeedsPermission -> status.release
            is UpdateStatus.Downloading -> status.release
            is UpdateStatus.Installing -> status.release
            is UpdateStatus.AwaitingConfirmation -> status.release
            is UpdateStatus.Failed -> status.release
            is UpdateStatus.Installed -> status.release
            else -> null
        }
        release?.notes?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (status) {
                is UpdateStatus.Available -> Chip("INSTALL ${status.release.versionName}", primary = true) { vm.installUpdate(status.release) }
                is UpdateStatus.NeedsPermission -> Chip("ALLOW INSTALLS", primary = true) {
                    context.startActivity(AppUpdater.permissionSettingsIntent(context))
                }
                is UpdateStatus.AwaitingConfirmation -> Chip("CONFIRM INSTALL", primary = true) { context.startActivity(status.confirm) }
                is UpdateStatus.Failed -> status.release?.let { r -> Chip("RETRY", primary = true) { vm.installUpdate(r) } }
                is UpdateStatus.Installed -> Chip("RESTART", primary = true) { AppUpdater.restart() }
                else -> Unit
            }
            Chip("CHECK NOW", enabled = !update.busy) { vm.checkForUpdate() }
            Chip(
                if (update.autoUpdate) "AUTO-UPDATE ON" else "AUTO-UPDATE OFF",
                tint = if (update.autoUpdate) PulseColors.TextPrimary else PulseColors.Warning
            ) { vm.setAutoUpdate(!update.autoUpdate) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Updates come from your Pulse server (uploaded with pulse-server-cli app upload). With auto-update on, " +
                "the app installs a newer release as soon as it finds one. Android asks you to confirm the first update; " +
                "on Android 12 and later, the ones after that install on their own.",
            fontSize = 11.sp, lineHeight = 16.sp, color = PulseColors.TextTertiary
        )
    }
}

// ---------------------------------------------------------------- account

@Composable
private fun AccountSection(vm: SettingsViewModel) {
    Section("ACCOUNT") {
        Text(
            text = "Your credentials are stored on this device only, so the app can sign back in when a session expires. Signing out forgets them and the server.",
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = PulseColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Chip("SIGN OUT / CHANGE SERVER") { vm.signOut() }
    }
}
