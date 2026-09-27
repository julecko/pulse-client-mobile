package sk.dilino.pulseclientmobile.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.dilino.pulseclientmobile.util.formatDuration

/** The app's models against JSON shaped like the server's (`crates/protocol`). */
class ServerModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun alert(ruleId: String, title: String, geo: String = "null") = """
        {"id":1,"rule_id":$ruleId,"agent_id":2,"hostname":"web01","severity":"critical","title":"$title",
         "message":"m","triggered_at":"2026-09-27 10:00:00","resolved_at":null,"acknowledged_at":null,
         "acknowledged_by":null,"geo":$geo}
    """

    @Test
    fun alertSources() {
        val geo = json.decodeFromString<AlertRecord>(
            alert(
                "null", "SSH login from Russia (RU) on web01",
                """{"kind":"session_open","ip":"203.0.113.9","user":"root","country_code":"RU","country_name":"Russia","city":"Moscow"}"""
            )
        )
        assertEquals(AlertSource.GEO, geo.source)
        assertEquals("Moscow, Russia (RU)", geo.geo!!.location)
        assertFalse(geo.geo!!.isFailure)

        assertEquals(AlertSource.OFFLINE, json.decodeFromString<AlertRecord>(alert("null", "web01 stopped sending metrics")).source)
        assertEquals(AlertSource.RULE, json.decodeFromString<AlertRecord>(alert("4", "CPU high")).source)
        assertEquals(AlertSource.DELETED_RULE, json.decodeFromString<AlertRecord>(alert("null", "CPU high")).source)
    }

    @Test
    fun alertWithoutGeoFieldFromOlderServer() {
        val record = json.decodeFromString<AlertRecord>(
            """{"id":1,"rule_id":4,"agent_id":2,"severity":"warning","title":"t","message":"m","triggered_at":"2026-09-27 10:00:00"}"""
        )
        assertNull(record.geo)
    }

    @Test
    fun authEventLocation() {
        val events = json.decodeFromString<List<AuthEventRecord>>(
            """[{"id":1,"kind":"auth_failure","service":"sshd","user":"root","rhost":"203.0.113.9","occurred_at":"2026-09-27 10:00:00","country_code":"DE","country_name":"Germany","city":null},
                {"id":2,"kind":"session_open","service":"sudo","user":"root","occurred_at":"2026-09-27 10:00:00"}]"""
        )
        assertEquals("Germany (DE)", events[0].location)
        assertEquals(AuthEventKind.AUTH_FAILURE, events[0].eventKind)
        assertNull(events[1].location)
    }

    @Test
    fun pamNotifications() {
        val pam = json.decodeFromString<PamNotifications>("""{"agent_id":3,"hostname":"web01","kinds":["session_open","auth_failure"]}""")
        assertEquals(setOf(AuthEventKind.SESSION_OPEN, AuthEventKind.AUTH_FAILURE), pam.kindSet)
        assertEquals("""{"kinds":["session_close"]}""", json.encodeToString(SetPamNotifications(listOf("session_close"))))
    }

    @Test
    fun offlineAlerts() {
        val list = json.decodeFromString<List<OfflineAlertSetting>>(
            """[{"agent_id":1,"hostname":"a","status":"approved","after_secs":300,"last_metrics_at":"2026-09-27 10:00:00","offline":true},
                {"agent_id":2,"hostname":"b","status":"pending","after_secs":null,"last_metrics_at":null,"offline":false}]"""
        )
        assertEquals(300, list[0].afterSecs)
        assertTrue(list[0].offline)
        assertNull(list[1].afterSecs)
        // The server reads a missing or null `after_secs` as "off", so null has to be sent explicitly.
        assertEquals("""{"after_secs":null}""", json.encodeToString(SetOfflineAlert(null)))
        assertEquals("""{"after_secs":300}""", json.encodeToString(SetOfflineAlert(300)))
    }

    @Test
    fun geoAlertSettings() {
        val settings = json.decodeFromString<GeoAlertSettings>(
            """{"allowed_countries":["SK","CZ"],"include_failures":false,"notify":true,"updated_by":null,"updated_at":"2026-09-27 10:00:00",
                "database":{"path":"/var/lib/GeoIP/GeoLite2-City.mmdb","database_type":"GeoLite2-City","built_at":"2026-09-20 00:00:00"}}"""
        )
        assertEquals(listOf("SK", "CZ"), settings.allowedCountries)
        assertEquals("GeoLite2-City", settings.database!!.databaseType)
        // Every field is required by the server, including ones equal to their usual defaults.
        assertEquals(
            """{"allowed_countries":["SK"],"include_failures":false,"notify":true}""",
            json.encodeToString(SetGeoAlertSettings(listOf("SK"), includeFailures = false, notify = true))
        )
    }

    @Test
    fun retention() {
        val list = json.decodeFromString<List<RetentionSetting>>(
            """[{"data":"metrics","days":14,"default_days":14,"overridden":false,"updated_by":null,"updated_at":null},
                {"data":"auth_events","days":0,"default_days":14,"overridden":true,"updated_by":"alice","updated_at":"2026-09-27 10:00:00"},
                {"data":"alerts","days":90,"default_days":90,"overridden":false,"updated_by":null,"updated_at":null}]"""
        )
        assertEquals(RetentionData.entries.toList(), list.map { it.dataEnum })
        assertTrue(list[1].overridden)
        assertEquals("""{"days":null}""", json.encodeToString(SetRetention(null)))
        assertEquals("""{"days":30}""", json.encodeToString(SetRetention(30)))
    }

    @Test
    fun durations() {
        assertEquals("90s", formatDuration(90))
        assertEquals("5m", formatDuration(300))
        assertEquals("2h", formatDuration(7200))
        assertEquals("1d", formatDuration(86400))
    }
}
