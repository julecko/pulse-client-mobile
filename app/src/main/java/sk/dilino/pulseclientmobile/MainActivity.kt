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
import sk.dilino.pulseclientmobile.push.EXTRA_APP_VERSION_CODE
import sk.dilino.pulseclientmobile.push.EXTRA_OPEN_ALERTS
import sk.dilino.pulseclientmobile.push.ensureAlertChannel
import sk.dilino.pulseclientmobile.ui.PulseApp
import sk.dilino.pulseclientmobile.ui.theme.PulseTheme
import sk.dilino.pulseclientmobile.update.AppUpdater

class MainActivity : ComponentActivity() {

    private val connectionStore by lazy { ConnectionStore(applicationContext) }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Bumped every time an "open alerts" intent arrives, so PulseApp can react even to a repeat tap. */
    private var openAlertsRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppUpdater.init(applicationContext)
        ensureAlertChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true) openAlertsRequest++
        // An "update available" push; FCM puts its data in the extras when it shows it itself.
        if (intent?.hasExtra(EXTRA_APP_VERSION_CODE) == true) AppUpdater.requestCheck()

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
        if (intent.hasExtra(EXTRA_APP_VERSION_CODE)) AppUpdater.requestCheck()
    }
}
