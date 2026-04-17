package com.amurcanov.tgwsproxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val UPDATE_CHECK_NEVER = -1

fun updateIntervalHoursToMillis(hours: Int): Long? = when {
    hours <= 0 -> null
    else -> hours * 60L * 60L * 1000L
}

fun updateIntervalLabel(hours: Int): String = when (hours) {
    7 -> "7 ч"
    24 -> "24 ч"
    48 -> "48 ч"
    UPDATE_CHECK_NEVER -> "Никогда"
    else -> "$hours ч"
}

data class AppReleaseInfo(
    val versionTag: String,
    val releaseUrl: String,
    val changelogMarkdown: String,
)

const val UPDATE_DIALOG_ACTION_POSTPONED = "postponed"
const val UPDATE_DIALOG_ACTION_UPDATE = "update"

suspend fun fetchLatestReleaseInfo(): AppReleaseInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/amurcanov/tg-ws-proxy-android/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val versionTag = json.optString("tag_name")
            val releaseUrl = json.optString("html_url")
            val body = json.optString("body")
            if (versionTag.isBlank() || releaseUrl.isBlank()) return@withContext null

            AppReleaseInfo(
                versionTag = versionTag,
                releaseUrl = releaseUrl,
                changelogMarkdown = extractReleaseChangelog(body)
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun isNewerVersion(local: String, remote: String): Boolean {
    val localParts = local.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    val remoteParts = remote.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(localParts.size, remoteParts.size)

    for (i in 0 until maxLen) {
        val l = localParts.getOrElse(i) { 0 }
        val r = remoteParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }

    return false
}

private fun extractReleaseChangelog(body: String): String {
    if (body.isBlank()) return ""

    val lines = body.lines()
    val markerIndexes = lines.mapIndexedNotNull { index, line ->
        if (line.trim() == "---") index else null
    }

    val scopedLines = if (markerIndexes.size >= 2) {
        lines.subList(markerIndexes.first() + 1, markerIndexes[1])
    } else {
        lines
    }

    return scopedLines
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n")
        .trim()
}
