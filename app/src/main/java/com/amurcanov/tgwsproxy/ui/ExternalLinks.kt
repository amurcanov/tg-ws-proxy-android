package com.amurcanov.tgwsproxy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openUrlInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
