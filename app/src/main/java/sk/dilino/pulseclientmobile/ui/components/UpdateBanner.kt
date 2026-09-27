package sk.dilino.pulseclientmobile.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.dilino.pulseclientmobile.data.model.AppRelease
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.update.UpdateState
import sk.dilino.pulseclientmobile.update.UpdateStatus
import sk.dilino.pulseclientmobile.update.describe

/**
 * A strip above every screen while an app update is available or on its way; shows nothing
 * otherwise. Its action installs it, opens the install permission setting, or re-shows Android's
 * confirmation prompt.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onInstall: (AppRelease) -> Unit,
    onAllowInstalls: () -> Unit,
    onConfirm: (Intent) -> Unit
) {
    val status = state.status
    val action: Pair<String, (() -> Unit)?> = when (status) {
        is UpdateStatus.Available -> "UPDATE" to { onInstall(status.release) }
        is UpdateStatus.NeedsPermission -> "ALLOW" to onAllowInstalls
        is UpdateStatus.AwaitingConfirmation -> "CONFIRM" to { onConfirm(status.confirm) }
        is UpdateStatus.Failed -> status.release?.let { release -> "RETRY" to { onInstall(release) } } ?: return
        is UpdateStatus.Downloading, is UpdateStatus.Installing -> "…" to null
        else -> return
    }

    Column(Modifier.fillMaxWidth().background(PulseColors.SurfaceElevated).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeverityMarker(if (status is UpdateStatus.Failed) Severity.CRITICAL else Severity.WARNING, size = 7)
            Spacer(Modifier.width(9.dp))
            Text(
                describe(status),
                fontSize = 11.5.sp, color = PulseColors.TextPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            val onClick = action.second
            Text(
                action.first,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
                color = if (onClick != null) PulseColors.AccentOn else PulseColors.TextTertiary,
                modifier = Modifier
                    .then(if (onClick != null) Modifier.background(PulseColors.Accent).clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Border))
    }
}
