package sk.dilino.pulseclientmobile.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.dilino.pulseclientmobile.ui.theme.PulseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onConnected: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 28.dp, vertical = 24.dp)),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SENTRY / PULSE",
            style = MaterialTheme.typography.labelSmall,
            color = PulseColors.Accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect to your fleet.",
            style = MaterialTheme.typography.headlineLarge,
            color = PulseColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter the pulse server address (scheme and port included, e.g. https://10.0.0.5:8443) and your pulse user credentials.",
            style = MaterialTheme.typography.bodyMedium,
            color = PulseColors.TextSecondary
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://host:8443") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PulseColors.TextPrimary,
                unfocusedTextColor = PulseColors.TextPrimary,
                focusedBorderColor = PulseColors.Accent,
                unfocusedBorderColor = PulseColors.Border,
                cursorColor = PulseColors.Accent
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Username") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PulseColors.TextPrimary,
                unfocusedTextColor = PulseColors.TextPrimary,
                focusedBorderColor = PulseColors.Accent,
                unfocusedBorderColor = PulseColors.Border,
                cursorColor = PulseColors.Accent
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PulseColors.TextPrimary,
                unfocusedTextColor = PulseColors.TextPrimary,
                focusedBorderColor = PulseColors.Accent,
                unfocusedBorderColor = PulseColors.Border,
                cursorColor = PulseColors.Accent
            )
        )
        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = PulseColors.Accent
            )
        }
        val cert = state.untrustedCert
        if (cert != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, PulseColors.Warning)
                    .padding(14.dp)
            ) {
                Text("UNTRUSTED CERTIFICATE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = PulseColors.Warning)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This server's certificate isn't signed by a trusted authority (normal for a self-signed pulse server). " +
                        "Compare this fingerprint with the one on the server — " +
                        "openssl x509 -noout -fingerprint -sha256 -in /etc/pulse-server/certs/cert.pem — and only continue if they match.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = PulseColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(cert, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = PulseColors.TextPrimary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TRUST & CONNECT",
                        fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = PulseColors.AccentOn,
                        modifier = Modifier
                            .background(PulseColors.Accent)
                            .clickable { viewModel.trustCertificate(onConnected) }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                    Text(
                        "CANCEL",
                        fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = PulseColors.TextPrimary,
                        modifier = Modifier
                            .border(1.dp, PulseColors.Border)
                            .clickable(onClick = viewModel::refuseCertificate)
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.connect(onConnected) },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PulseColors.Accent,
                contentColor = PulseColors.AccentOn
            )
        ) {
            if (state.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    color = PulseColors.AccentOn,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Credentials are stored on this device. Read-only fleet + auth log, with alert rules and push notifications.",
            style = MaterialTheme.typography.bodyMedium,
            color = PulseColors.TextTertiary,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
