package sk.dilino.pulseclientmobile.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sk.dilino.pulseclientmobile.MainActivity
import sk.dilino.pulseclientmobile.R
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.update.AppUpdater

const val NOTIFICATION_CHANNEL_ID = "alerts"
const val EXTRA_OPEN_ALERTS = "open_alerts"
/** In an "update available" push's `data` (see the server's `web::app_releases`), and so in its tap intent. */
const val EXTRA_APP_VERSION_CODE = "app_version_code"

/** Ensures the alerts notification channel exists; safe to call repeatedly. */
fun ensureAlertChannel(context: Context) {
    val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.alert_notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Receives alert pushes from the pulse server (see `crates/serverd/src/push.rs`). When the app is
 * backgrounded, FCM shows the `notification` payload itself using the manifest's default channel;
 * this only has to handle the foreground case and keeping this device's token registered.
 */
class PulseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { registerToken(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val appVersionCode = message.data[EXTRA_APP_VERSION_CODE]
        if (appVersionCode != null) {
            // A new app release: the running app checks for it (and installs it with auto-update on).
            AppUpdater.init(applicationContext)
            AppUpdater.requestCheck()
        }
        val title = message.notification?.title ?: "Pulse alert"
        val body = message.notification?.body ?: message.data["severity"]?.let { "Severity: $it" } ?: ""
        ensureAlertChannel(this)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (appVersionCode != null) putExtra(EXTRA_APP_VERSION_CODE, appVersionCode) else putExtra(EXTRA_OPEN_ALERTS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, message.data["alert_id"]?.hashCode() ?: 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = message.data["alert_id"]?.toIntOrNull() ?: System.currentTimeMillis().toInt()
        runCatching { NotificationManagerCompat.from(this).notify(notificationId, notification) }
    }
}

/** Registers [token] as this device's push target with the currently connected server, if any. */
suspend fun registerToken(context: Context, token: String, deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}") {
    val connection = ConnectionStore(context).connection.first() ?: return
    val api = PulseApiClient(connection.baseUrl, connection.username, connection.password, connection.pinnedCertSha256)
    api.registerPushDevice(token, deviceName)
}
