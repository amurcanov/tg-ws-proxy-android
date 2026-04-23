package com.amurcanov.tgwsproxy

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ProxyTilePreferencesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openApp()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openApp()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
