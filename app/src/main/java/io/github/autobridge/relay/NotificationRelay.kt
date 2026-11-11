package io.github.autobridge.relay

import android.util.Log
import io.github.autobridge.filter.FilterEngine
import io.github.autobridge.source.RelayDiagnostics
import io.github.autobridge.source.RelayDiagnostics.Outcome
import io.github.autobridge.source.SlackParser
import io.github.autobridge.source.SourceNotification
import io.github.autobridge.store.ConversationStore

/** Decides what happens to each Slack notification. Kept out of the service so it can be tested. */
class NotificationRelay(
    private val store: ConversationStore,
    private val notifier: AutoNotifier,
    private val rememberWorkspace: (String) -> Unit,
) {
    /** Null until settings and car state are first known. */
    private var state: RelayState? = null

    /** Any change can alter what may be shown, so rebuild from what Slack is currently showing. */
    fun applyState(next: RelayState, activeNotifications: () -> List<SourceNotification>) {
        state = next
        notifier.config = next.notifier
        store.clear()
        // Not just the store's keys: notifications from before a process restart are still showing.
        notifier.cancelAllConversations()
        if (next.active) activeNotifications().sortedBy { it.postTime }.forEach { onPosted(it, alert = false) }
    }

    fun reset() {
        state = null
    }

    fun onPosted(source: SourceNotification, alert: Boolean = true) {
        if (source.packageName != SlackParser.SLACK_PACKAGE) return
        try {
            relay(source, alert)
        } catch (e: RuntimeException) {
            // Notifications are another app's data; one bad one must not crash-loop the listener.
            Log.w(TAG, "Couldn't relay a notification: ${e.javaClass.simpleName}")
            RelayDiagnostics.recordFailure(source, e)
        }
    }

    fun onRemoved(packageName: String, sourceKey: String) {
        if (packageName == SlackParser.SLACK_PACKAGE) notifier.cancelAll(store.removeBySourceKey(sourceKey))
    }

    private fun relay(source: SourceNotification, alert: Boolean) {
        val parsed = SlackParser.parse(source)
        if (parsed == null) {
            RelayDiagnostics.record(source, null, Outcome.IGNORED)
            return
        }
        // Remember every workspace, even filtered ones, so its switch stays in settings.
        parsed.workspace?.let(rememberWorkspace)

        val current = state
        if (current == null || !current.active) {
            RelayDiagnostics.record(source, parsed, Outcome.INACTIVE)
            return
        }
        val allowed = FilterEngine.apply(parsed, current.filters)
        if (allowed == null) {
            store.refreshActions(parsed)
            RelayDiagnostics.record(source, parsed, Outcome.FILTERED)
            return
        }
        val update = store.upsert(allowed)
        if (update == null) {
            RelayDiagnostics.record(source, parsed, Outcome.ALREADY_READ)
            return
        }
        val alertNow = alert && update.hasNewIncoming
        val posted = notifier.post(update.conversation, alert = alertNow)
        RelayDiagnostics.record(source, parsed, if (posted) Outcome.RELAYED else Outcome.NOT_POSTED, alerted = posted && alertNow)
    }

    private companion object {
        const val TAG = "NotificationRelay"
    }
}
