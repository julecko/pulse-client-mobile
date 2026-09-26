package sk.dilino.pulseclientmobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.dilino.pulseclientmobile.ui.components.SectionLabel
import sk.dilino.pulseclientmobile.ui.theme.PulseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            Text(
                text = "Signed in as ${state.username}",
                fontSize = 11.sp,
                color = PulseColors.TextTertiary
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            SectionLabel("SERVER")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RectangleShape,
                placeholder = { Text("https://host:8443") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PulseColors.TextPrimary,
                    unfocusedTextColor = PulseColors.TextPrimary,
                    focusedBorderColor = PulseColors.Accent,
                    unfocusedBorderColor = PulseColors.Border,
                    cursorColor = PulseColors.Accent
                )
            )
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = PulseColors.Accent)
            } else if (state.saved) {
                Spacer(Modifier.height(8.dp))
                Text("Saved — now connected to ${state.currentUrl}", style = MaterialTheme.typography.bodyMedium, color = PulseColors.TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            val canSave = state.input.isNotBlank() && state.input.trim() != state.currentUrl && !state.isSaving
            Box(
                modifier = Modifier
                    .background(if (canSave) PulseColors.Accent else PulseColors.Track)
                    .clickable(enabled = canSave, onClick = viewModel::save)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(14.dp),
                        color = PulseColors.AccentOn,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "SAVE",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = if (canSave) PulseColors.AccentOn else PulseColors.TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            SectionLabel("ACCOUNT")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your credentials are stored on this device only, so the app can sign back in when a session expires.",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = PulseColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "SIGN OUT",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = PulseColors.TextPrimary,
                modifier = Modifier
                    .clickable(onClick = viewModel::signOut)
                    .border(1.dp, PulseColors.Border)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Sentry · read-only fleet monitor",
                fontSize = 10.5.sp,
                color = PulseColors.TextTertiary
            )
        }
    }
}
