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

const val NOTIFICATION_CHANNEL_ID = "alerts"
/** Plain pushes that aren't alerts: PAM login events and `pulse-agent-cli notify` messages. */
const val HOST_NOTIFICATION_CHANNEL_ID = "host_notifications"
const val EXTRA_OPEN_ALERTS = "open_alerts"

/** Ensures the notification channels exist; safe to call repeatedly. */
fun ensureAlertChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.alert_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
    )
    manager.createNotificationChannel(
        NotificationChannel(
            HOST_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.host_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.host_notification_channel_description) }
    )
}

/**
 * Receives pushes from the pulse server (see `crates/serverd/src/push.rs`). There are two kinds:
 * alerts (rule, offline and geo alerts), whose `data` carries `alert_id`, `agent_id` and `severity`,
 * and plain notifications (PAM login events, `pulse-agent-cli notify`) that carry only a title and
 * body. When the app is backgrounded, FCM shows the `notification` payload itself using the
 * manifest's default channel; this only has to handle the foreground case and keeping this
 * device's token registered.
 */
class PulseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { registerToken(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val alertId = message.data["alert_id"]
        val isAlert = alertId != null
        val title = message.notification?.title ?: if (isAlert) "Pulse alert" else "Pulse"
        val body = message.notification?.body ?: message.data["severity"]?.let { "Severity: $it" } ?: ""
        ensureAlertChannel(this)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isAlert) putExtra(EXTRA_OPEN_ALERTS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, alertId?.hashCode() ?: 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (isAlert) NOTIFICATION_CHANNEL_ID else HOST_NOTIFICATION_CHANNEL_ID
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (isAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // An offline alert's "back online" push reuses its alert_id, so it replaces the offline one.
        val notificationId = alertId?.toIntOrNull() ?: System.currentTimeMillis().toInt()
        runCatching { NotificationManagerCompat.from(this).notify(notificationId, notification) }
    }
}

/** Registers [token] as this device's push target with the currently connected server, if any. */
suspend fun registerToken(context: Context, token: String, deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}") {
    val connection = ConnectionStore(context).connection.first() ?: return
    val api = PulseApiClient(connection.baseUrl, connection.username, connection.password, connection.pinnedCertSha256)
    api.registerPushDevice(token, deviceName)
}
