package io.github.autobridge.actions

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.autobridge.app
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.relay.AutoNotifier

/** Mark-as-read: forwards to Slack's mark-read action if it has one, then clears the relayed copy. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = ConversationKey.parse(intent.getStringExtra(AutoNotifier.EXTRA_CONVERSATION_KEY)) ?: return
        when (intent.action) {
            ACTION_MARK_READ -> {
                val conversation = context.app.store.markRead(key)
                try {
                    conversation?.actions?.markRead?.send()
                } catch (_: PendingIntent.CanceledException) {
                    // Slack already dropped it; clearing ours is all that's left.
                }
                context.app.notifier.cancel(key)
            }
            // User swiped the relayed notification away: these messages shouldn't come back either.
            ACTION_DISMISSED -> context.app.store.markRead(key)
        }
    }

    companion object {
        const val ACTION_MARK_READ = "io.github.autobridge.action.MARK_READ"
        const val ACTION_DISMISSED = "io.github.autobridge.action.DISMISSED"
    }
}
