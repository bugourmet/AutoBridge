package io.github.autobridge.relay

import io.github.autobridge.AutoBridgeApp
import io.github.autobridge.R
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.Sender
import io.github.autobridge.model.SourceActions
import io.github.autobridge.source.ParsedNotification

/**
 * Posts a conversation with no Slack data. If it reaches the car but Slack messages don't, Slack's
 * data is the problem; if it doesn't, Android Auto is rejecting the app itself.
 */
object TestMessage {
    private const val SOURCE_KEY = "autobridge:test"

    fun post(app: AutoBridgeApp) {
        val sender = app.getString(R.string.test_sender)
        val parsed = ParsedNotification(
            key = ConversationKey(app.packageName, "test"),
            sourceNotificationKey = SOURCE_KEY,
            title = sender,
            isGroup = false,
            messages = listOf(Message(app.getString(R.string.test_text), System.currentTimeMillis(), Sender(sender, SOURCE_KEY))),
            actions = SourceActions(),
        )
        // Always the alerting channel, so the test also works before Android Auto is detected.
        app.store.upsert(parsed)?.let {
            app.notifier.post(it.conversation, alert = true, config = app.notifier.config.copy(quiet = false))
        }
    }
}
