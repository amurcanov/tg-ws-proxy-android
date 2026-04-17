package com.amurcanov.tgwsproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED broadcast.
 * Currently just logs — future implementation can auto-start proxy
 * if it was running before reboot (read from DataStore).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i("BootReceiver", "Device booted — proxy can be started from app")
            // Future: read saved state from DataStore and auto-start if was running
        }
    }
}
