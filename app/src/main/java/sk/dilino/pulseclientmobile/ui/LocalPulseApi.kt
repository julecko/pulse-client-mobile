package sk.dilino.pulseclientmobile.ui

import androidx.compose.runtime.compositionLocalOf
import sk.dilino.pulseclientmobile.data.ConnectionStore
import sk.dilino.pulseclientmobile.data.network.PulseApiClient

val LocalPulseApi = compositionLocalOf<PulseApiClient> {
    error("No PulseApiClient provided — a server must be connected first")
}

val LocalConnectionStore = compositionLocalOf<ConnectionStore> {
    error("No ConnectionStore provided")
}
