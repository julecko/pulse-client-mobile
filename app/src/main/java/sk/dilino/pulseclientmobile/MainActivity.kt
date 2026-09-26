package sk.dilino.pulseclientmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.ui.PulseApp
import sk.dilino.pulseclientmobile.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {

    private val connectionStore by lazy { ConnectionStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseTheme {
                PulseApp(connectionStore = connectionStore)
            }
        }
    }
}
