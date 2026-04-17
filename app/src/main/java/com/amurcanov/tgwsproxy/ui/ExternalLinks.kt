package com.amurcanov.tgwsproxy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

private val browserPackages = listOf(
    "com.android.chrome",
    "com.google.android.googlequicksearchbox",
    "org.mozilla.firefox",
    "com.yandex.browser",
    "ru.yandex.searchplugin",
    "com.yandex.browser.lite",
    "com.opera.browser",
    "com.opera.mini.native",
    "com.microsoft.emmx",
    "com.brave.browser",
    "com.duckduckgo.mobile.android",
    "com.sec.android.app.sbrowser",
    "com.vivaldi.browser",
    "com.kiwibrowser.browser",
)

private val browserProbeUri: Uri = Uri.parse("https://www.example.com")

private fun createBrowserIntent(uri: Uri): Intent {
    return Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
}

private fun resolveBrowserPackage(context: Context): String? {
    val pm = context.packageManager

    for (pkg in browserPackages) {
        val intent = createBrowserIntent(browserProbeUri).apply {
            setPackage(pkg)
        }
        if (intent.resolveActivity(pm) != null) {
            return pkg
        }
    }

    return pm.queryIntentActivities(createBrowserIntent(browserProbeUri), 0)
        .firstOrNull()
        ?.activityInfo
        ?.packageName
}

fun openUrlInBrowser(context: Context, url: String) {
    try {
        val pm = context.packageManager
        val uri = Uri.parse(url)
        val browserPackage = resolveBrowserPackage(context) ?: return
        val intent = createBrowserIntent(uri).apply {
            setPackage(browserPackage)
        }
        if (intent.resolveActivity(pm) != null) {
            context.startActivity(intent)
        }
    } catch (_: Exception) {
    }
}
