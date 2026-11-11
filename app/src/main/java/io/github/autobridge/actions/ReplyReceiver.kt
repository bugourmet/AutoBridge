package io.github.autobridge.actions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.github.autobridge.R
import io.github.autobridge.app
import io.github.autobridge.model.Conversation
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.Sender
import io.github.autobridge.relay.AutoNotifier

/** Receives a reply typed or dictated on the relayed notification and forwards it to Slack. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val key = ConversationKey.parse(intent.getStringExtra(AutoNotifier.EXTRA_CONVERSATION_KEY)) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AutoNotifier.KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
        val store = context.app.store
        val notifier = context.app.notifier
        val conversation = store.get(key)

        // Every path re-posts, which clears the system's reply spinner. A conversation the store no
        // longer knows is left over from before a process restart, so it's removed instead.
        fun show(updated: Conversation?, alert: Boolean) {
            if (updated != null) notifier.post(updated, alert) else notifier.cancel(key)
        }

        val now = System.currentTimeMillis()
        when {
            text.isEmpty() -> show(conversation, alert = false)
            ReplyForwarder.forward(context, conversation?.actions?.reply, text) ->
                show(store.appendLocal(key, Message(text, now, sender = null)), alert = false)
            else -> {
                // The phone notification can't appear in Android Auto, so tell the driver in the conversation.
                val notice = Message(
                    context.getString(R.string.reply_failed_notice),
                    now,
                    Sender(context.getString(R.string.app_name), NOTICE_SENDER_KEY),
                )
                show(store.appendLocal(key, notice), alert = true)
                notifier.postReplyFailed(key, conversation, text)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "io.github.autobridge.action.REPLY"
        private const val NOTICE_SENDER_KEY = "autobridge:notice"
    }
}
