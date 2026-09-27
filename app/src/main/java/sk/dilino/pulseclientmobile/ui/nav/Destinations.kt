package sk.dilino.pulseclientmobile.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.ui.graphics.vector.ImageVector

const val ROUTE_FLEET = "fleet"
const val ROUTE_ALERTS = "alerts"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_HOST_DETAIL = "host/{agentId}"

fun hostDetailRoute(agentId: Long) = "host/$agentId"

data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val topLevelDestinations = listOf(
    TopLevelDestination(ROUTE_FLEET, "PULSE", Icons.Outlined.Dns),
    TopLevelDestination(ROUTE_ALERTS, "ALERTS", Icons.Outlined.NotificationsNone),
    TopLevelDestination(ROUTE_SETTINGS, "SETTINGS", Icons.Filled.Tune)
)
