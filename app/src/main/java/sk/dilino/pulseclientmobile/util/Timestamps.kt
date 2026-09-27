package sk.dilino.pulseclientmobile.util

/** Server timestamps are SQLite `datetime()` strings: "YYYY-MM-DD HH:MM:SS" (UTC). */
fun formatServerDateTime(raw: String): String {
    val parts = raw.split(" ")
    if (parts.size != 2) return raw
    return "${parts[0]} · ${parts[1].take(5)}"
}

fun formatServerTime(raw: String): String =
    raw.split(" ").getOrNull(1)?.take(5) ?: raw

/** "just now" / "22m ago" / "3h ago" / "5d ago", for a server UTC timestamp relative to now. */
fun formatAgo(raw: String): String {
    val then = parseServerMillis(raw) ?: return raw
    val mins = (System.currentTimeMillis() - then) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

/** `300` → "5m", `7200` → "2h", `86400` → "1d", `90` → "90s" — the server's own duration style. */
fun formatDuration(secs: Int): String = when {
    secs >= 86400 && secs % 86400 == 0 -> "${secs / 86400}d"
    secs >= 3600 && secs % 3600 == 0 -> "${secs / 3600}h"
    secs >= 60 && secs % 60 == 0 -> "${secs / 60}m"
    else -> "${secs}s"
}
