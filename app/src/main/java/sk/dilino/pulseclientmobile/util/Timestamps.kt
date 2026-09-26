package sk.dilino.pulseclientmobile.util

/** Server timestamps are SQLite `datetime()` strings: "YYYY-MM-DD HH:MM:SS" (UTC). */
fun formatServerDateTime(raw: String): String {
    val parts = raw.split(" ")
    if (parts.size != 2) return raw
    return "${parts[0]} · ${parts[1].take(5)}"
}

fun formatServerTime(raw: String): String =
    raw.split(" ").getOrNull(1)?.take(5) ?: raw
