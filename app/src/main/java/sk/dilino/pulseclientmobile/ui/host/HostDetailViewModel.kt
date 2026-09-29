package sk.dilino.pulseclientmobile.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import sk.dilino.pulseclientmobile.data.HostGraph
import sk.dilino.pulseclientmobile.data.HostViewPrefs
import sk.dilino.pulseclientmobile.data.HostViewStore
import sk.dilino.pulseclientmobile.data.model.AgentLifecycle
import sk.dilino.pulseclientmobile.data.model.AgentSummary
import sk.dilino.pulseclientmobile.data.model.AuthEventRecord
import sk.dilino.pulseclientmobile.data.model.MetricsRecord
import sk.dilino.pulseclientmobile.data.model.MetricsSeries
import sk.dilino.pulseclientmobile.data.model.OfflineAlertSetting
import sk.dilino.pulseclientmobile.data.network.PulseApiClient
import sk.dilino.pulseclientmobile.util.parseServerMillis

/** Auth events per page (the server allows up to 500). */
private const val AUTH_PAGE = 100
/** Older snapshots fetched per page when paging back. */
private const val SNAPSHOT_PAGE = 200

enum class HostTab(val label: String) {
    OVERVIEW("OVERVIEW"), CPU("CPU"), GRAPHS("GRAPHS"), AUTH("AUTH"), SNAPSHOTS("SNAPSHOTS"), SETUP("SETUP")
}

data class HostDetailUiState(
    val isLoading: Boolean = true,
    val agent: AgentSummary? = null,
    val prefs: HostViewPrefs = HostViewPrefs(),
    /** Oldest → newest: the newest [HostViewPrefs.snapshotCount], plus any older pages loaded. */
    val snapshots: List<MetricsRecord> = emptyList(),
    /** Snapshots loaded by paging back, kept on top of the newest window. */
    val olderSnapshotCount: Int = 0,
    val loadingOlderSnapshots: Boolean = false,
    /** The server has nothing older than [snapshots]. */
    val snapshotsExhausted: Boolean = false,
    /** Newest first. */
    val authEvents: List<AuthEventRecord> = emptyList(),
    val loadingOlderAuth: Boolean = false,
    val authExhausted: Boolean = false,
    val series: MetricsSeries? = null,
    val seriesError: String? = null,
    /** Null until loaded, or for an agent that isn't approved (only approved agents are watched). */
    val offlineAlert: OfflineAlertSetting? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
    /** Device clock, millis; 0 before the first refresh. */
    val lastRefreshMs: Long = 0,
    val actionInFlight: Boolean = false,
    /** Removal is permanent, so the button needs a second tap. */
    val confirmRemove: Boolean = false,
    val tab: HostTab = HostTab.OVERVIEW,
    /** Snapshot the whole screen is showing; null follows the newest one ("live"). */
    val pinnedId: Long? = null,
    /** Snapshot B in the compare view; null picks the one an hour before the shown snapshot. */
    val compareId: Long? = null
) {
    val lastIndex get() = snapshots.lastIndex
    val shownIndex get() = pinnedId?.let { indexOfId(it) } ?: lastIndex
    val isLive get() = pinnedId == null || shownIndex >= lastIndex
    val shown: MetricsRecord? get() = snapshots.getOrNull(shownIndex)
    val compareShown: Int
        get() = compareId?.let { indexOfId(it) } ?: run {
            val shownMs = shown?.let { parseServerMillis(it.createdAt) } ?: return@run 0
            lastIndexAtOrBefore(shownMs - 3_600_000L)
        }

    /** Index of snapshot [id], or of the next newer one if it's no longer loaded (ids grow with time). */
    private fun indexOfId(id: Long): Int {
        if (snapshots.isEmpty()) return -1
        var lo = 0
        var hi = snapshots.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (snapshots[mid].id < id) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The newest snapshot taken at or before [millis]; the oldest loaded one if none is. */
    private fun lastIndexAtOrBefore(millis: Long): Int {
        var lo = 0
        var hi = snapshots.lastIndex
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val at = parseServerMillis(snapshots[mid].createdAt) ?: 0L
            if (at <= millis) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

class HostDetailViewModel(
    private val api: PulseApiClient,
    private val viewStore: HostViewStore,
    private val agentId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostDetailUiState())
    val uiState: StateFlow<HostDetailUiState> = _uiState

    /** Wakes the refresh loop early: a manual refresh or changed settings. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    /** Refreshes and page loads change the same lists, so they take turns. */
    private val lock = Mutex()
    /** What the loaded [HostDetailUiState.series] was fetched for, and when (device millis). */
    private var seriesKey: Pair<Long, Int>? = null
    private var seriesFetchedMs = 0L

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(prefs = viewStore.prefs(agentId).first()) }
            launch {
                viewStore.prefs(agentId).collect { prefs ->
                    val old = _uiState.value.prefs
                    if (prefs == old) return@collect
                    _uiState.update { it.copy(prefs = prefs) }
                    // A new window or range needs data now; a new interval just restarts the wait.
                    wake.trySend(Unit)
                }
            }
            while (true) {
                fetch()
                val secs = _uiState.value.prefs.refreshSecs
                if (secs > 0) withTimeoutOrNull(secs * 1000L) { wake.receive() } else wake.receive()
            }
        }
    }

    /** Refreshes now (also the only way to refresh with MANUAL). */
    fun load() {
        wake.trySend(Unit)
    }

    private suspend fun fetch() = lock.withLock {
        _uiState.update { it.copy(refreshing = true) }
        val prefs = _uiState.value.prefs
        val agentsResult = api.listAgents()
        val agent = agentsResult.getOrNull()?.firstOrNull { it.id == agentId }
        val approved = agent?.lifecycle == AgentLifecycle.APPROVED
        val metricsResult = if (approved) api.metrics(agentId, prefs.snapshotCount) else null
        val eventsResult = if (agent != null) api.authEvents(agentId, AUTH_PAGE) else null
        val offlineResult = if (approved) api.offlineAlert(agentId) else null
        val seriesResult = if (approved && seriesDue(prefs)) fetchSeries(prefs) else null

        val error = agentsResult.exceptionOrNull()?.message
            ?: metricsResult?.exceptionOrNull()?.message
            ?: eventsResult?.exceptionOrNull()?.message
            ?: offlineResult?.exceptionOrNull()?.message

        _uiState.update { s ->
            val snaps = metricsResult?.getOrNull()?.let { mergeSnapshots(s, it, prefs.snapshotCount) }
            val auth = eventsResult?.getOrNull()?.let { mergeAuth(s, it) }
            s.copy(
                isLoading = false,
                agent = agent ?: s.agent,
                snapshots = snaps?.first ?: s.snapshots,
                olderSnapshotCount = snaps?.second ?: s.olderSnapshotCount,
                // Without older pages, there's more exactly when the window came back full.
                snapshotsExhausted = snaps?.takeIf { it.second == 0 }?.let { it.first.size < prefs.snapshotCount }
                    ?: s.snapshotsExhausted,
                authEvents = auth?.first ?: s.authEvents,
                authExhausted = auth?.takeIf { it.second }?.let { it.first.size < AUTH_PAGE } ?: s.authExhausted,
                series = seriesResult?.getOrNull() ?: s.series,
                seriesError = seriesResult?.exceptionOrNull()?.let(::seriesErrorText) ?: if (seriesResult != null) null else s.seriesError,
                // Kept while the agent list can't be loaded; dropped once the agent isn't approved.
                offlineAlert = if (agent == null || approved) offlineResult?.getOrNull() ?: s.offlineAlert else null,
                error = error,
                refreshing = false,
                lastRefreshMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * The newest [fresh] window merged with the older snapshots already loaded, keeping [window] plus the ones paged
     * in. Returns the list and how many of it came from paging. If [fresh] doesn't reach back to what was loaded
     * (the screen wasn't refreshed for a long time), the older pages are dropped rather than shown with a gap.
     */
    private fun mergeSnapshots(s: HostDetailUiState, fresh: List<MetricsRecord>, window: Int): Pair<List<MetricsRecord>, Int> {
        val firstFresh = fresh.firstOrNull() ?: return s.snapshots to s.olderSnapshotCount
        val older = s.snapshots.filter { it.id < firstFresh.id }
        val connected = s.snapshots.any { it.id >= firstFresh.id } || fresh.size < window
        if (!connected || s.olderSnapshotCount == 0) return fresh to 0
        val merged = (older + fresh).takeLast(fresh.size + s.olderSnapshotCount)
        return merged to (merged.size - fresh.size)
    }

    /**
     * The newest page [fresh] merged with older events already loaded. Returns the list and whether older pages
     * were dropped (no overlap with [fresh], so there could be a gap), which restarts paging.
     */
    private fun mergeAuth(s: HostDetailUiState, fresh: List<AuthEventRecord>): Pair<List<AuthEventRecord>, Boolean> {
        val last = fresh.lastOrNull() ?: return fresh to true
        val freshIds = fresh.mapTo(HashSet()) { it.id }
        if (s.authEvents.none { it.id in freshIds }) return fresh to true
        val older = s.authEvents.filter { it.id !in freshIds && olderThan(it, last) }
        return (fresh + older) to false
    }

    /** Whether [a] sorts after [b] in the server's newest-first order (by occurred_at, then id). */
    private fun olderThan(a: AuthEventRecord, b: AuthEventRecord): Boolean {
        val byTime = a.occurredAt.compareTo(b.occurredAt)
        return byTime < 0 || (byTime == 0 && a.id < b.id)
    }

    private fun seriesDue(prefs: HostViewPrefs): Boolean {
        if (HostGraph.entries.none { it in prefs.graphs }) return false
        if (seriesKey != prefs.rangeSecs to prefs.graphPoints) return true
        // A new point only appears once a bucket fills, so wider buckets need fewer refetches.
        val bucketMs = (_uiState.value.series?.bucketSecs ?: 0L) * 1000L
        val intervalMs = maxOf(bucketMs, prefs.refreshSecs * 1000L)
        return System.currentTimeMillis() - seriesFetchedMs >= intervalMs
    }

    private suspend fun fetchSeries(prefs: HostViewPrefs): Result<MetricsSeries> {
        val result = api.metricsSeries(agentId, prefs.rangeSecs, prefs.graphPoints)
        if (result.isSuccess) {
            seriesKey = prefs.rangeSecs to prefs.graphPoints
            seriesFetchedMs = System.currentTimeMillis()
        }
        return result
    }

    private fun seriesErrorText(e: Throwable): String =
        if ((e as? sk.dilino.pulseclientmobile.data.network.ApiException)?.code == 404) {
            "The server is too old for graphs — update pulse-server to 1.3.0 or later."
        } else {
            e.message ?: "Couldn't load graphs"
        }

    /** Pages back through older snapshots, adding them before the loaded ones. */
    fun loadOlderSnapshots() {
        val s = _uiState.value
        if (s.loadingOlderSnapshots || s.snapshotsExhausted) return
        val oldest = s.snapshots.firstOrNull()?.id ?: return
        viewModelScope.launch {
            lock.withLock {
                _uiState.update { it.copy(loadingOlderSnapshots = true) }
                api.metrics(agentId, SNAPSHOT_PAGE, beforeId = oldest)
                    .onSuccess { page ->
                        _uiState.update {
                            // A refresh in between may have dropped the pages this continues.
                            if (it.snapshots.firstOrNull()?.id != oldest) {
                                it.copy(loadingOlderSnapshots = false)
                            } else {
                                it.copy(
                                    snapshots = page + it.snapshots,
                                    olderSnapshotCount = it.olderSnapshotCount + page.size,
                                    snapshotsExhausted = page.size < SNAPSHOT_PAGE,
                                    loadingOlderSnapshots = false
                                )
                            }
                        }
                    }
                    .onFailure { e -> _uiState.update { it.copy(loadingOlderSnapshots = false, error = e.message) } }
            }
        }
    }

    /** Pages back through older auth events, adding them after the loaded ones. */
    fun loadOlderAuth() {
        val s = _uiState.value
        if (s.loadingOlderAuth || s.authExhausted) return
        val last = s.authEvents.lastOrNull()?.id ?: return
        viewModelScope.launch {
            lock.withLock {
                _uiState.update { it.copy(loadingOlderAuth = true) }
                api.authEvents(agentId, AUTH_PAGE, beforeId = last)
                    .onSuccess { page ->
                        _uiState.update {
                            if (it.authEvents.lastOrNull()?.id != last) {
                                it.copy(loadingOlderAuth = false)
                            } else {
                                it.copy(
                                    authEvents = it.authEvents + page,
                                    authExhausted = page.size < AUTH_PAGE,
                                    loadingOlderAuth = false
                                )
                            }
                        }
                    }
                    .onFailure { e -> _uiState.update { it.copy(loadingOlderAuth = false, error = e.message) } }
            }
        }
    }

    // ---------------------------------------------------------------- view settings

    fun updatePrefs(change: (HostViewPrefs) -> HostViewPrefs) {
        val next = change(_uiState.value.prefs)
        viewModelScope.launch { viewStore.save(agentId, next) }
    }

    fun toggleGraph(graph: HostGraph) = updatePrefs {
        it.copy(graphs = if (graph in it.graphs) it.graphs - graph else it.graphs + graph)
    }

    fun resetPrefs() {
        viewModelScope.launch { viewStore.reset(agentId) }
    }

    // ---------------------------------------------------------------- navigation

    fun selectTab(tab: HostTab) = _uiState.update { it.copy(tab = tab) }

    fun scrubTo(index: Int) = _uiState.update {
        val i = index.coerceIn(0, it.lastIndex.coerceAtLeast(0))
        it.copy(pinnedId = if (i >= it.lastIndex) null else it.snapshots.getOrNull(i)?.id)
    }

    fun jumpLive() = _uiState.update { it.copy(pinnedId = null) }

    fun compareWith(index: Int) = _uiState.update { it.copy(compareId = it.snapshots.getOrNull(index)?.id) }

    // ---------------------------------------------------------------- agent actions

    fun requestRemove(onRemoved: () -> Unit) {
        if (_uiState.value.confirmRemove) {
            _uiState.update { it.copy(confirmRemove = false) }
            remove(onRemoved)
        } else {
            _uiState.update { it.copy(confirmRemove = true) }
        }
    }

    fun approve() = runAction { api.approveAgent(agentId) }
    fun revoke() = runAction { api.revokeAgent(agentId) }
    fun unrevoke() = runAction { api.unrevokeAgent(agentId) }
    fun remove(onRemoved: () -> Unit) = runAction(onDone = onRemoved) { api.removeAgent(agentId) }

    /** Offline alert after [afterSecs] without metrics; null turns it off. */
    fun setOfflineAlert(afterSecs: Long?) = runAction { api.setOfflineAlert(agentId, afterSecs).map { } }

    private fun runAction(onDone: (() -> Unit)? = null, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInFlight = true) }
            val result = action()
            _uiState.update { it.copy(actionInFlight = false) }
            if (result.isSuccess && onDone != null) {
                onDone()
            } else {
                wake.trySend(Unit)
            }
        }
    }
}
