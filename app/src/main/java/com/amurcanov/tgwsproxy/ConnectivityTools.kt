package com.amurcanov.tgwsproxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Small utilities that don't need the native library:
 *  - a direct reachability/latency probe against Telegram data centers, and
 *  - a helper to ask the system to exempt the app from battery optimizations
 *    so the foreground proxy service survives longer in the background.
 */
object ConnectivityTools {

    /** A couple of the main Telegram DC IPs (matches the Rust defaults). */
    private val TELEGRAM_DCS = listOf(
        "DC2" to "149.154.167.51",
        "DC4" to "149.154.167.91",
    )

    private const val PROBE_PORT = 443
    private const val PROBE_TIMEOUT_MS = 4000

    data class DcResult(val name: String, val reachable: Boolean, val latencyMs: Long)

    /**
     * TCP-connects to each DC on 443 and measures the handshake latency.
     * Runs on the IO dispatcher; safe to call from a coroutine.
     */
    suspend fun probeTelegram(): List<DcResult> = withContext(Dispatchers.IO) {
        TELEGRAM_DCS.map { (name, ip) ->
            val start = System.nanoTime()
            val ok = withTimeoutOrNull(PROBE_TIMEOUT_MS.toLong()) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, PROBE_PORT), PROBE_TIMEOUT_MS)
                        true
                    }
                }.getOrDefault(false)
            } ?: false
            val latency = (System.nanoTime() - start) / 1_000_000
            DcResult(name, ok, latency)
        }
    }

    /** True if the app is currently exempt from battery optimizations. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        // Battery optimizations only exist on API 23+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system dialog/screen asking the user to exempt the app from
     * battery optimizations. Falls back to the generic settings list if the
     * direct request is unavailable on this device.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(fallback) }
    }
}
