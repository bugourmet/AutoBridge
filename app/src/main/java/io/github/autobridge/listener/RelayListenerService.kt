package io.github.autobridge.listener

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.autobridge.BuildConfig
import io.github.autobridge.app
import io.github.autobridge.relay.NotificationRelay
import io.github.autobridge.relay.RelayState
import io.github.autobridge.source.NotificationDumper
import io.github.autobridge.source.SlackParser
import io.github.autobridge.source.SourceNotification
import io.github.autobridge.source.toSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Connects Android's notification callbacks, settings and car state to [NotificationRelay]. */
class RelayListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private val relay by lazy {
        NotificationRelay(app.store, app.notifier) { workspace ->
            scope.launch { app.settings.addKnownWorkspace(workspace) }
        }
    }

    override fun onListenerConnected() {
        stateJob?.cancel()
        stateJob = scope.launch {
            combine(app.settings.relaySettings, app.carState.projecting, RelayState::from)
                .distinctUntilChanged()
                .collect { relay.applyState(it, ::activeSources) }
        }
    }

    override fun onListenerDisconnected() {
        stateJob?.cancel()
        relay.reset()
        // Battery optimizers can unbind us; ask to be bound again.
        requestRebind(ComponentName(this, RelayListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (BuildConfig.DEBUG && sbn.packageName == SlackParser.SLACK_PACKAGE) NotificationDumper.dump(sbn)
        relay.onPosted(sbn.toSource())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = relay.onRemoved(sbn.packageName, sbn.key)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun activeSources(): List<SourceNotification> = try {
        activeNotifications.orEmpty().map { it.toSource() }
    } catch (e: SecurityException) {
        Log.w(TAG, "Listener not connected", e)
        emptyList()
    }

    private companion object {
        const val TAG = "RelayListener"
    }
}
