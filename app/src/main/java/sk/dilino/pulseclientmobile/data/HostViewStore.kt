package sk.dilino.pulseclientmobile.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hostViewStore by preferencesDataStore(name = "host_view")

/** A graph on a host's GRAPHS tab. [key] is what's stored; don't rename it. */
enum class HostGraph(val key: String, val label: String) {
    CPU("cpu", "CPU"),
    MEMORY("memory", "MEMORY"),
    SWAP("swap", "SWAP"),
    DISK("disk", "DISK"),
    LOAD("load", "LOAD"),
    NETWORK("network", "NETWORK")
}

/** How one host's screen looks and refreshes. Kept on this phone only, per host. */
data class HostViewPrefs(
    /** Newest snapshots loaded for the timeline, compare view and history (the server sends at most 1000). */
    val snapshotCount: Int = DEFAULT_SNAPSHOT_COUNT,
    /** Seconds between refreshes while the screen is open; 0 refreshes only when asked. */
    val refreshSecs: Int = DEFAULT_REFRESH_SECS,
    /** How far back the GRAPHS tab goes. */
    val rangeSecs: Long = DEFAULT_RANGE_SECS,
    /** Points per graph: more is finer detail over the same range. */
    val graphPoints: Int = DEFAULT_GRAPH_POINTS,
    val graphs: Set<HostGraph> = DEFAULT_GRAPHS
) {
    companion object {
        const val DEFAULT_SNAPSHOT_COUNT = 120
        const val DEFAULT_REFRESH_SECS = 10
        const val DEFAULT_RANGE_SECS = 24 * 3600L
        const val DEFAULT_GRAPH_POINTS = 300
        val DEFAULT_GRAPHS = setOf(HostGraph.CPU, HostGraph.MEMORY, HostGraph.DISK, HostGraph.LOAD, HostGraph.NETWORK)

        val SNAPSHOT_COUNTS = listOf(60, 120, 240, 500, 1000)
        /** Label to seconds; 0 is manual. */
        val REFRESH_OPTIONS = listOf("5S" to 5, "10S" to 10, "30S" to 30, "1M" to 60, "5M" to 300, "MANUAL" to 0)
        val RANGE_OPTIONS = listOf(
            "1H" to 3600L, "6H" to 6 * 3600L, "24H" to 24 * 3600L,
            "3D" to 3 * 86400L, "7D" to 7 * 86400L, "30D" to 30 * 86400L, "90D" to 90 * 86400L
        )
        val GRAPH_POINT_OPTIONS = listOf("LOW" to 120, "MEDIUM" to 300, "HIGH" to 600, "MAX" to 1000)
    }
}

class HostViewStore(private val context: Context) {

    private fun snapshotsKey(agentId: Long) = intPreferencesKey("host_${agentId}_snapshots")
    private fun refreshKey(agentId: Long) = intPreferencesKey("host_${agentId}_refresh_secs")
    private fun rangeKey(agentId: Long) = longPreferencesKey("host_${agentId}_range_secs")
    private fun pointsKey(agentId: Long) = intPreferencesKey("host_${agentId}_graph_points")
    private fun graphsKey(agentId: Long) = stringSetPreferencesKey("host_${agentId}_graphs")

    fun prefs(agentId: Long): Flow<HostViewPrefs> = context.hostViewStore.data.map { p -> p.toPrefs(agentId) }

    private fun Preferences.toPrefs(agentId: Long) = HostViewPrefs(
        snapshotCount = this[snapshotsKey(agentId)]?.coerceIn(10, 1000) ?: HostViewPrefs.DEFAULT_SNAPSHOT_COUNT,
        refreshSecs = this[refreshKey(agentId)]?.coerceAtLeast(0) ?: HostViewPrefs.DEFAULT_REFRESH_SECS,
        rangeSecs = this[rangeKey(agentId)]?.coerceIn(3600L, 90 * 86400L) ?: HostViewPrefs.DEFAULT_RANGE_SECS,
        graphPoints = this[pointsKey(agentId)]?.coerceIn(2, 1000) ?: HostViewPrefs.DEFAULT_GRAPH_POINTS,
        graphs = this[graphsKey(agentId)]
            ?.let { keys -> HostGraph.entries.filter { it.key in keys }.toSet() }
            ?: HostViewPrefs.DEFAULT_GRAPHS
    )

    suspend fun save(agentId: Long, prefs: HostViewPrefs) {
        context.hostViewStore.edit {
            it[snapshotsKey(agentId)] = prefs.snapshotCount
            it[refreshKey(agentId)] = prefs.refreshSecs
            it[rangeKey(agentId)] = prefs.rangeSecs
            it[pointsKey(agentId)] = prefs.graphPoints
            it[graphsKey(agentId)] = prefs.graphs.map { g -> g.key }.toSet()
        }
    }

    /** Back to the defaults for this host. */
    suspend fun reset(agentId: Long) {
        context.hostViewStore.edit {
            it.remove(snapshotsKey(agentId))
            it.remove(refreshKey(agentId))
            it.remove(rangeKey(agentId))
            it.remove(pointsKey(agentId))
            it.remove(graphsKey(agentId))
        }
    }
}
