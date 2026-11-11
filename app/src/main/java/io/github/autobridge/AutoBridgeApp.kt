package io.github.autobridge

import android.app.Application
import android.content.Context
import io.github.autobridge.car.CarStateMonitor
import io.github.autobridge.relay.AutoNotifier
import io.github.autobridge.settings.SettingsRepository
import io.github.autobridge.store.ConversationStore

/** Process-wide singletons shared by the listener, receivers and UI. */
class AutoBridgeApp : Application() {
    val store = ConversationStore()
    val notifier by lazy { AutoNotifier(this) }
    val settings by lazy { SettingsRepository(this) }
    val carState by lazy { CarStateMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
    }
}

val Context.app: AutoBridgeApp get() = applicationContext as AutoBridgeApp
