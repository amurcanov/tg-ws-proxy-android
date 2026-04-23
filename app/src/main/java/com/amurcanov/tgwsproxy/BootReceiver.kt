package com.amurcanov.tgwsproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives boot broadcasts and starts the proxy when autostart is enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settingsStore = SettingsStore(appContext)
                if (!settingsStore.autoStartOnBoot.first()) {
                    Log.i(TAG, "Boot completed, autostart disabled")
                    return@launch
                }

                val started = ProxyController.startFromSavedSettings(
                    context = appContext,
                    showInvalidPortToast = false
                )
                Log.i(TAG, "Boot completed, proxy autostart requested: $started")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to autostart proxy after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
