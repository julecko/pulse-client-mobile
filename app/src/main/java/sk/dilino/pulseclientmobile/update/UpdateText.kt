package sk.dilino.pulseclientmobile.update

import sk.dilino.pulseclientmobile.data.model.AppRelease

/** Shown for an update [status] in the banner and in Settings. */
fun describe(status: UpdateStatus): String = when (status) {
    UpdateStatus.Idle -> "Not checked yet"
    UpdateStatus.Checking -> "Checking the server for updates…"
    is UpdateStatus.UpToDate ->
        if (status.latest == null) "Up to date · the server has no app releases" else "Up to date"
    is UpdateStatus.Available -> status.message?.let { "${label(status.release)} available · $it" }
        ?: "${label(status.release)} available"
    is UpdateStatus.NeedsPermission ->
        "${label(status.release)} available · allow Pulse to install apps to update"
    is UpdateStatus.Downloading -> "Downloading ${label(status.release)}" +
        (status.progress?.let { " · ${(it * 100).toInt()}%" } ?: "…")
    is UpdateStatus.Installing -> "Installing ${label(status.release)}…"
    is UpdateStatus.AwaitingConfirmation -> "Confirm the install of ${label(status.release)}"
    is UpdateStatus.Failed -> status.message
}

private fun label(release: AppRelease) = "Version ${release.versionName}"

/** This app's own version, e.g. `v1.1.0 (2)`, shown on the connect and settings screens. */
fun installedVersionLabel(state: UpdateState, withCode: Boolean = false): String =
    "v${state.installedVersionName}" + if (withCode) " (${state.installedVersionCode})" else ""
