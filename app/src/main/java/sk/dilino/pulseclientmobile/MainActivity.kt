package sk.dilino.pulseclientmobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.push.EXTRA_OPEN_ALERTS
import sk.dilino.pulseclientmobile.push.ensureAlertChannel
import sk.dilino.pulseclientmobile.ui.PulseApp
import sk.dilino.pulseclientmobile.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private val connectionStore by lazy { ConnectionStore(applicationContext) }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Bumped every time an "open alerts" intent arrives, so PulseApp can react even to a repeat tap. */
    private var openAlertsRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureAlertChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true) openAlertsRequest++

        setContent {
            PulseTheme {
                PulseApp(connectionStore = connectionStore, openAlertsRequest = openAlertsRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)) openAlertsRequest++
    }
}
