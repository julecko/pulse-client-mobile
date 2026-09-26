package sk.dilino.pulseclientmobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PulseDarkScheme = darkColorScheme(
    primary = PulseColors.Accent,
    onPrimary = PulseColors.AccentOn,
    background = PulseColors.Background,
    onBackground = PulseColors.TextPrimary,
    surface = PulseColors.Surface,
    onSurface = PulseColors.TextPrimary,
    surfaceVariant = PulseColors.SurfaceElevated,
    onSurfaceVariant = PulseColors.TextSecondary,
    outline = PulseColors.Border,
    error = PulseColors.Accent,
    onError = PulseColors.AccentOn
)

/** The reference design is dark-only ("dark Modernist iOS app") — no light variant. */
@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulseDarkScheme,
        typography = PulseTypography,
        content = content
    )
}
