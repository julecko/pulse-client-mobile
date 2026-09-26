package sk.dilino.pulseclientmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.dilino.pulseclientmobile.ui.theme.PulseColors

/** Legend from the design: healthy = no color, warning = outlined, critical = filled red. Ordered by badness. */
enum class Severity { HEALTHY, WARNING, CRITICAL }

fun Severity.color(): Color = when (this) {
    Severity.HEALTHY -> PulseColors.TextPrimary
    Severity.WARNING -> PulseColors.Warning
    Severity.CRITICAL -> PulseColors.Accent
}

/** Square outlined tag, tinted by severity. */
@Composable
fun StatusPill(label: String, severity: Severity, modifier: Modifier = Modifier) {
    val tint = severity.color()
    Text(
        text = label,
        color = tint,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier
            .border(1.dp, tint)
            .padding(horizontal = 5.dp, vertical = 3.dp)
    )
}

/** The small square marker next to host names: solid ink / outlined warning / solid red. */
@Composable
fun SeverityMarker(severity: Severity, modifier: Modifier = Modifier, size: Int = 9) {
    val fill = when (severity) {
        Severity.HEALTHY -> PulseColors.TextPrimary
        Severity.WARNING -> Color.Transparent
        Severity.CRITICAL -> PulseColors.Accent
    }
    val border = if (severity == Severity.WARNING) PulseColors.Warning else fill
    Box(
        modifier = modifier
            .size(size.dp)
            .background(fill)
            .border(2.dp, border)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = PulseColors.TextSecondary,
        modifier = modifier
    )
}
