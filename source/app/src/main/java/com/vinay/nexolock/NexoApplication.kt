package com.vinay.nexolock

import android.app.Application
import kotlinx.coroutines.launch

/**
 * Process-wide composition root. NEXO LOCK deliberately uses manual dependency
 * injection: the application is small, completely offline, and this keeps the
 * build light (no annotation processors, no reflection).
 */
class NexoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // DataStore has no room-level migration DSL for Preferences. Schema
        // changes are additive and handled in NexoPreferences.toConfig(); this
        // stamp records that the current process applied the idempotent upgrade.
        container.appScope.launch {
            runCatching { container.prefs.stampSchemaVersion() }
        }
    }
}
