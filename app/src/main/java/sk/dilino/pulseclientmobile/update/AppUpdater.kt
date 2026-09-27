package sk.dilino.pulseclientmobile.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.dilino.pulseclientmobile.MainActivity
import sk.dilino.pulseclientmobile.data.model.AppRelease
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import java.io.File
import java.security.MessageDigest

/** Where the app is in updating itself. */
sealed interface UpdateStatus {
    /** Not checked yet this run. */
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    /** Nothing newer on the server; [latest] is its newest release, if it has any. */
    data class UpToDate(val latest: AppRelease?) : UpdateStatus
    /** [release] is newer than this app; [message] says why a previous attempt stopped, if one did. */
    data class Available(val release: AppRelease, val message: String? = null) : UpdateStatus
    /** Android won't let this app install others until the user allows it in system settings. */
    data class NeedsPermission(val release: AppRelease) : UpdateStatus
    /** [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateStatus
    data class Installing(val release: AppRelease) : UpdateStatus
    /** Android asks the user to confirm the install; [confirm] shows that prompt again. */
    data class AwaitingConfirmation(val release: AppRelease, val confirm: Intent) : UpdateStatus
    /**
     * A newer version is installed, but this process still runs the old code; [AppUpdater.restart]
     * switches to it. [release] is null when the install finished while this process wasn't watching.
     */
    data class Installed(val release: AppRelease?) : UpdateStatus
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateStatus
}

data class UpdateState(
    val installedVersionCode: Long = 0,
    val installedVersionName: String = "",
    val autoUpdate: Boolean = true,
    val status: UpdateStatus = UpdateStatus.Idle
) {
    /** Something is in progress, so checks and new installs wait. */
    val busy: Boolean
        get() = status is UpdateStatus.Checking || status is UpdateStatus.Downloading ||
            status is UpdateStatus.Installing || status is UpdateStatus.AwaitingConfirmation ||
            status is UpdateStatus.Installed
}

/**
 * Keeps the app up to date with the releases uploaded to the server (`pulse-server-cli app upload`):
 * asks `GET /app-releases/latest` whether there's a newer version code than this app's, downloads its
 * APK, checks it (SHA-256 from the server, package name, version code) and installs it with
 * [PackageInstaller]. Android shows its own confirmation the first time; from Android 12 on, later
 * updates install without one, since this app then installed itself.
 *
 * With auto-update on (the default), a check that finds a newer release installs it right away.
 *
 * Android replaces the APK but doesn't always kill the running process, which would keep running the
 * old code (and offer the same update again). So once the installed version is newer than the one
 * this process started with, the app restarts itself: right away if it's on screen, else the next
 * time it comes to the foreground.
 */
object AppUpdater {

    /** Checks not forced by the user or a push happen at most this often. */
    private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L
    private const val PREFS = "app_updates"
    private const val PREF_AUTO_UPDATE = "auto_update"
    private const val UPDATES_DIR = "updates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Bumped when a push says a new release is out, so the UI checks right away. */
    private val _checkRequests = MutableStateFlow(0)
    val checkRequests: StateFlow<Int> = _checkRequests.asStateFlow()

    private lateinit var appContext: Context
    @Volatile private var lastCheckMs = 0L
    /** Whether an activity is started (visible), so a restart won't yank the app up from the background. */
    @Volatile private var foreground = false

    /** Safe to call repeatedly; every entry point (activity, receivers) calls it first. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val info = installedPackageInfo(appContext)
        _state.update {
            it.copy(
                installedVersionCode = PackageInfoCompat.getLongVersionCode(info),
                installedVersionName = info.versionName.orEmpty(),
                autoUpdate = prefs().getBoolean(PREF_AUTO_UPDATE, true)
            )
        }
    }

    fun setAutoUpdate(enabled: Boolean) {
        prefs().edit().putBoolean(PREF_AUTO_UPDATE, enabled).apply()
        _state.update { it.copy(autoUpdate = enabled) }
    }

    /** Asks for a check at the next opportunity, e.g. after an "update available" push. */
    fun requestCheck() = _checkRequests.update { it + 1 }

    /**
     * Checks the server for a newer release. Unless [force]d, skipped if one ran in the last few
     * minutes. With [autoInstall] (and auto-update on) it goes on to install what it finds.
     */
    fun check(api: PulseApiClient, force: Boolean = false, autoInstall: Boolean = true) {
        if (markIfUpdated()) return
        val now = System.currentTimeMillis()
        if (_state.value.busy || (!force && now - lastCheckMs < CHECK_INTERVAL_MS)) return
        lastCheckMs = now
        setStatus(UpdateStatus.Checking)
        scope.launch {
            api.latestAppRelease()
                .onSuccess { latest ->
                    if (latest != null && latest.versionCode > _state.value.installedVersionCode) {
                        setStatus(UpdateStatus.Available(latest))
                        if (autoInstall && _state.value.autoUpdate) install(api, latest)
                    } else {
                        setStatus(UpdateStatus.UpToDate(latest))
                    }
                }
                .onFailure { e ->
                    lastCheckMs = 0
                    setStatus(UpdateStatus.Failed(e.message ?: "Couldn't check for updates"))
                }
        }
    }

    /** Downloads, verifies and installs [release]. */
    fun install(api: PulseApiClient, release: AppRelease) {
        val current = _state.value.status
        if (current is UpdateStatus.Downloading || current is UpdateStatus.Installing) return
        // Already installed (e.g. a second tap after the first install went through): don't reinstall it.
        if (markIfUpdated(release)) {
            if (foreground) restart()
            return
        }
        if (!canInstallPackages()) {
            setStatus(UpdateStatus.NeedsPermission(release))
            return
        }
        setStatus(UpdateStatus.Downloading(release, null))
        scope.launch {
            val dir = File(appContext.cacheDir, UPDATES_DIR)
            dir.deleteRecursively()
            dir.mkdirs()
            val apk = File(dir, "pulse-${release.versionCode}.apk")

            var lastPercent = -1
            val downloaded = api.downloadAppRelease(release.versionCode, apk) { done, total ->
                val size = if (total > 0) total else release.size
                val percent = if (size > 0) (done * 100 / size).toInt().coerceIn(0, 100) else -1
                if (percent != lastPercent) {
                    lastPercent = percent
                    setStatus(UpdateStatus.Downloading(release, if (percent >= 0) percent / 100f else null))
                }
            }
            downloaded.exceptionOrNull()?.let { e ->
                apk.delete()
                setStatus(UpdateStatus.Available(release, "Download failed: ${e.message ?: "network error"}"))
                return@launch
            }

            verify(apk, release)?.let { problem ->
                apk.delete()
                setStatus(UpdateStatus.Failed(problem, release))
                return@launch
            }

            setStatus(UpdateStatus.Installing(release))
            runCatching { commitInstall(apk, release) }.onFailure { e ->
                setStatus(UpdateStatus.Failed("Couldn't start the install: ${e.message}", release))
            }
        }
    }

    /** Carries on after the user comes back from allowing installs in system settings. */
    fun resume(api: PulseApiClient) {
        val status = _state.value.status
        if (status is UpdateStatus.NeedsPermission && canInstallPackages()) install(api, status.release)
    }

    /** Called from `MainActivity.onStart`/`onStop`. Coming to the foreground on outdated code restarts the app. */
    fun setForeground(visible: Boolean) {
        foreground = visible
        if (visible && markIfUpdated()) restart()
    }

    /**
     * Relaunches the app in a fresh process, which loads the newly installed code. Only while the app
     * is on screen: Android doesn't let a background app start an activity.
     */
    fun restart() {
        val intent = Intent.makeRestartActivityTask(ComponentName(appContext, MainActivity::class.java))
        appContext.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** Opens the system setting that lets this app install updates. */
    fun permissionSettingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ---------------------------------------------------------------- install results (InstallResultReceiver)

    internal fun onAwaitingConfirmation(confirm: Intent) {
        val release = currentRelease() ?: return
        setStatus(UpdateStatus.AwaitingConfirmation(release, confirm))
    }

    internal fun onInstallFinished(status: Int, message: String?) {
        val release = currentRelease()
        when (status) {
            // Android may or may not kill this process; if it didn't, get onto the new code. If it
            // did, this is already a fresh process running it.
            PackageInstaller.STATUS_SUCCESS ->
                if (markIfUpdated(release)) {
                    if (foreground) restart()
                } else {
                    setStatus(UpdateStatus.Idle)
                }
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                setStatus(release?.let { UpdateStatus.Available(it, "Install cancelled") } ?: UpdateStatus.Idle)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                setStatus(UpdateStatus.Failed(
                    "Android refused the update (${message ?: "conflict"}). It must be signed with the same key as the installed app.",
                    release
                ))
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                setStatus(UpdateStatus.Failed("Not enough storage to install the update", release))
            else -> setStatus(UpdateStatus.Failed("Install failed: ${message ?: "status $status"}", release))
        }
        clearDownloads(appContext)
    }

    /** Deletes downloaded APKs; called once they're installed or given up on. */
    fun clearDownloads(context: Context) {
        File(context.cacheDir, UPDATES_DIR).deleteRecursively()
    }

    // ---------------------------------------------------------------- internals

    private fun setStatus(status: UpdateStatus) = _state.update { it.copy(status = status) }

    private fun currentRelease(): AppRelease? = when (val s = _state.value.status) {
        is UpdateStatus.Available -> s.release
        is UpdateStatus.NeedsPermission -> s.release
        is UpdateStatus.Downloading -> s.release
        is UpdateStatus.Installing -> s.release
        is UpdateStatus.AwaitingConfirmation -> s.release
        is UpdateStatus.Failed -> s.release
        is UpdateStatus.Installed -> s.release
        else -> null
    }

    /** The version code installed now, which is newer than [UpdateState.installedVersionCode] after an update. */
    private fun installedPackageVersionCode(): Long =
        PackageInfoCompat.getLongVersionCode(installedPackageInfo(appContext))

    /**
     * True (and the status set to [UpdateStatus.Installed]) if a newer version than this process's is
     * installed.
     */
    private fun markIfUpdated(release: AppRelease? = currentRelease()): Boolean {
        if (installedPackageVersionCode() <= _state.value.installedVersionCode) return false
        if (_state.value.status !is UpdateStatus.Installed) setStatus(UpdateStatus.Installed(release))
        return true
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    /** Null if [apk] is what the server said [release] is, else what's wrong with it. */
    private suspend fun verify(apk: File, release: AppRelease): String? = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha256.equals(release.sha256, ignoreCase = true)) {
            return@withContext "The download is corrupt (checksum mismatch)"
        }

        @Suppress("DEPRECATION")
        val info = appContext.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: return@withContext "The server's file isn't a valid APK"
        if (info.packageName != appContext.packageName) {
            return@withContext "The release is a different app (${info.packageName})"
        }
        val code = PackageInfoCompat.getLongVersionCode(info)
        if (code != release.versionCode) {
            return@withContext "The APK is version code $code, but was uploaded as ${release.versionCode}"
        }
        null
    }

    private fun commitInstall(apk: File, release: AppRelease) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("pulse-${release.versionCode}.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(appContext, InstallResultReceiver::class.java)
                // Mutable: the installer fills in the result extras.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}

@Suppress("DEPRECATION")
private fun installedPackageInfo(context: Context) =
    context.packageManager.getPackageInfo(context.packageName, 0)
