package sk.dilino.pulseclientmobile.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import sk.dilino.pulseclientmobile.data.Connection
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.ui.alerts.AlertsScreen
import sk.dilino.pulseclientmobile.ui.components.UpdateBanner
import sk.dilino.pulseclientmobile.ui.alerts.AlertsViewModel
import sk.dilino.pulseclientmobile.ui.connect.ConnectScreen
import sk.dilino.pulseclientmobile.ui.connect.ConnectViewModel
import sk.dilino.pulseclientmobile.ui.fleet.FleetScreen
import sk.dilino.pulseclientmobile.ui.fleet.FleetViewModel
import sk.dilino.pulseclientmobile.ui.host.HostDetailScreen
import sk.dilino.pulseclientmobile.ui.host.HostDetailViewModel
import sk.dilino.pulseclientmobile.ui.nav.ROUTE_ALERTS
import sk.dilino.pulseclientmobile.ui.nav.ROUTE_FLEET
import sk.dilino.pulseclientmobile.ui.nav.ROUTE_HOST_DETAIL
import sk.dilino.pulseclientmobile.ui.nav.ROUTE_SETTINGS
import sk.dilino.pulseclientmobile.ui.nav.hostDetailRoute
import sk.dilino.pulseclientmobile.ui.nav.topLevelDestinations
import sk.dilino.pulseclientmobile.ui.settings.SettingsScreen
import sk.dilino.pulseclientmobile.ui.settings.SettingsViewModel
import sk.dilino.pulseclientmobile.ui.theme.PulseColors
import sk.dilino.pulseclientmobile.update.AppUpdater

@Composable
fun PulseApp(connectionStore: ConnectionStore, openAlertsRequest: Int = 0) {
    val connection by connectionStore.connection.collectAsStateWithLifecycle(initialValue = null)

    if (connection == null) {
        val connectViewModel: ConnectViewModel = viewModel(
            factory = viewModelFactory { initializer { ConnectViewModel(connectionStore) } }
        )
        ConnectScreen(viewModel = connectViewModel, onConnected = { /* recomposes once the store flow emits */ })
    } else {
        val current = connection as Connection
        // Keyed on the connection: switching servers in Settings should reset the
        // whole nav graph (fresh NavController, fresh ViewModels) rather than leave
        // screens holding a stale PulseApiClient for the old server.
        key(current) {
            val api = remember(current) { PulseApiClient(current.baseUrl, current.username, current.password, current.pinnedCertSha256) }
            LaunchedEffect(current) { registerFcmToken(api) }
            // Look for a newer app release whenever the app comes to the foreground (throttled by
            // AppUpdater), and right away when a push announces one.
            LifecycleStartEffect(api) {
                AppUpdater.resume(api)
                AppUpdater.check(api)
                onStopOrDispose { }
            }
            val checkRequests by AppUpdater.checkRequests.collectAsStateWithLifecycle()
            LaunchedEffect(api, checkRequests) {
                if (checkRequests > 0) AppUpdater.check(api, force = true)
            }
            CompositionLocalProvider(
                LocalPulseApi provides api,
                LocalConnectionStore provides connectionStore
            ) {
                MainScaffold(current = current, openAlertsRequest = openAlertsRequest)
            }
        }
    }
}

@Composable
private fun MainScaffold(current: Connection, openAlertsRequest: Int) {
    val navController = rememberNavController()

    LaunchedEffect(openAlertsRequest) {
        if (openAlertsRequest > 0) {
            navController.navigate(ROUTE_ALERTS) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    val update by AppUpdater.state.collectAsStateWithLifecycle()
    val api = LocalPulseApi.current
    val context = LocalContext.current

    Scaffold(
        containerColor = PulseColors.Background,
        topBar = {
            UpdateBanner(
                state = update,
                onInstall = { AppUpdater.install(api, it) },
                onAllowInstalls = { context.startActivity(AppUpdater.permissionSettingsIntent(context)) },
                onConfirm = { context.startActivity(it) }
            )
        },
        bottomBar = { PulseBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_FLEET,
            modifier = Modifier
                .padding(padding)
                .background(PulseColors.Background)
        ) {
            composable(ROUTE_FLEET) {
                val api = LocalPulseApi.current
                val vm: FleetViewModel = viewModel(factory = viewModelFactory { initializer { FleetViewModel(api) } })
                FleetScreen(
                    viewModel = vm,
                    onOpenAgent = { id -> navController.navigate(hostDetailRoute(id)) }
                )
            }
            composable(ROUTE_ALERTS) {
                val api = LocalPulseApi.current
                val vm: AlertsViewModel = viewModel(factory = viewModelFactory { initializer { AlertsViewModel(api) } })
                AlertsScreen(viewModel = vm, onOpenHost = { id -> navController.navigate(hostDetailRoute(id)) })
            }
            composable(ROUTE_SETTINGS) {
                val connectionStore = LocalConnectionStore.current
                val api = LocalPulseApi.current
                val vm: SettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SettingsViewModel(api, connectionStore, current) } }
                )
                SettingsScreen(viewModel = vm, onOpenHost = { id -> navController.navigate(hostDetailRoute(id)) })
            }
            composable(ROUTE_HOST_DETAIL) { backStackEntry ->
                val agentId = backStackEntry.arguments?.getString("agentId")?.toLongOrNull()
                val api = LocalPulseApi.current
                if (agentId != null) {
                    val vm: HostDetailViewModel = viewModel(
                        key = "host-$agentId",
                        factory = viewModelFactory { initializer { HostDetailViewModel(api, agentId) } }
                    )
                    HostDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/** Registers this device's FCM token with the just-connected server, so it can receive alert pushes. */
private suspend fun registerFcmToken(api: PulseApiClient) {
    val token = suspendCancellableCoroutine<String?> { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
            .addOnFailureListener { cont.resumeWith(Result.success(null)) }
    } ?: return
    api.registerPushDevice(token, "${Build.MANUFACTURER} ${Build.MODEL}")
}

@Composable
private fun PulseBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column(Modifier.background(PulseColors.Surface)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(PulseColors.Border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            topLevelDestinations.forEach { destination ->
                // Host detail counts as part of the Fleet tab.
                val selected = currentRoute == destination.route ||
                    (destination.route == ROUTE_FLEET && currentRoute == ROUTE_HOST_DETAIL)
                val tint = if (selected) PulseColors.Accent else PulseColors.TextTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            // Deliberately no saveState/restoreState: this app has no
                            // per-tab nested state worth preserving, and that combo
                            // can restore a stale backstack (e.g. a previously
                            // visited host-detail screen) instead of the tab root.
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        }
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(destination.icon, contentDescription = destination.label, tint = tint, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(destination.label, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = tint)
                }
            }
        }
    }
}
