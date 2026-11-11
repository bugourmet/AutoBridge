package io.github.autobridge.actions

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.AutoBridgeApp
import io.github.autobridge.R
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.relay.AutoNotifier
import io.github.autobridge.source.SlackFixtures
import io.github.autobridge.source.SlackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ActionReceiversTest {
    private val app: AutoBridgeApp = ApplicationProvider.getApplicationContext()
    private val nm = app.getSystemService(NotificationManager::class.java)
    private lateinit var key: ConversationKey

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val parsed = SlackParser.parse(SlackFixtures.source(SlackFixtures.dm(app)))!!
        key = parsed.key
        app.notifier.post(app.store.upsert(parsed)!!.conversation, alert = true)
    }

    private fun replyIntent(text: String) = Intent(ReplyReceiver.ACTION_REPLY)
        .putExtra(AutoNotifier.EXTRA_CONVERSATION_KEY, key.asString())
        .also {
            RemoteInput.addResultsToIntent(
                arrayOf(RemoteInput.Builder(AutoNotifier.KEY_TEXT_REPLY).build()),
                it,
                Bundle().apply { putCharSequence(AutoNotifier.KEY_TEXT_REPLY, text) },
            )
        }

    private fun broadcastsWith(action: String) = shadowOf(app).broadcastIntents.filter { it.action == action }

    private fun relayed() = shadowOf(nm).getNotification(key.asString(), AutoNotifier.ID_CONVERSATION)

    @Test
    fun `reply is forwarded into Slack's own RemoteInput key`() {
        ReplyReceiver().onReceive(app, replyIntent("on my way"))

        val sent = broadcastsWith(SlackFixtures.ACTION_SLACK_REPLY).single()
        assertEquals("on my way", RemoteInput.getResultsFromIntent(sent)!!.getCharSequence(SlackFixtures.SLACK_REPLY_KEY))
    }

    @Test
    fun `sent reply shows in the conversation history`() {
        ReplyReceiver().onReceive(app, replyIntent("on my way"))

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(relayed()!!)!!
        assertEquals("on my way", style.messages.last().text.toString())
        assertNull(style.messages.last().person)
        assertTrue(app.store.get(key)!!.messages.last().isFromUser)
    }

    @Test
    fun `stale Slack intent posts a reply-failed notification`() {
        app.store.get(key)!!.actions.reply!!.intent.cancel()

        ReplyReceiver().onReceive(app, replyIntent("on my way"))

        assertNotNull(shadowOf(nm).getNotification(key.asString(), AutoNotifier.ID_REPLY_FAILED))
        assertFalse(app.store.get(key)!!.messages.any { it.isFromUser })
    }

    @Test
    fun `failed reply is announced in the conversation so the car shows it`() {
        app.store.get(key)!!.actions.reply!!.intent.cancel()

        ReplyReceiver().onReceive(app, replyIntent("on my way"))

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(relayed()!!)!!
        assertEquals(app.getString(R.string.reply_failed_notice), style.messages.last().text.toString())
        assertEquals(0, relayed()!!.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test
    fun `reply on a conversation lost to a process restart removes the leftover notification`() {
        app.store.clear()

        ReplyReceiver().onReceive(app, replyIntent("on my way"))

        assertNull(relayed())
        assertNotNull(shadowOf(nm).getNotification(key.asString(), AutoNotifier.ID_REPLY_FAILED))
    }

    @Test
    fun `empty reply is not forwarded`() {
        ReplyReceiver().onReceive(app, replyIntent("  "))
        assertTrue(broadcastsWith(SlackFixtures.ACTION_SLACK_REPLY).isEmpty())
    }

    @Test
    fun `mark as read forwards to Slack and clears the relayed copy`() {
        MarkReadReceiver().onReceive(app, markRead(MarkReadReceiver.ACTION_MARK_READ))

        assertEquals(1, broadcastsWith(SlackFixtures.ACTION_SLACK_MARK_READ).size)
        assertNull(relayed())
        assertNull(app.store.get(key))
    }

    @Test
    fun `mark as read without Slack action still clears the relayed copy`() {
        app.store.clear()
        app.store.upsert(SlackParser.parse(SlackFixtures.source(SlackFixtures.dm(app, actions = emptyList())))!!)

        MarkReadReceiver().onReceive(app, markRead(MarkReadReceiver.ACTION_MARK_READ))

        assertTrue(broadcastsWith(SlackFixtures.ACTION_SLACK_MARK_READ).isEmpty())
        assertNull(relayed())
        assertNull(app.store.get(key))
    }

    @Test
    fun `swiping the relayed notification keeps its messages from coming back`() {
        MarkReadReceiver().onReceive(app, markRead(MarkReadReceiver.ACTION_DISMISSED))

        assertNull(app.store.get(key))
        assertTrue(broadcastsWith(SlackFixtures.ACTION_SLACK_MARK_READ).isEmpty())
        assertNull(app.store.upsert(SlackParser.parse(SlackFixtures.source(SlackFixtures.dm(app)))!!))
    }

    private fun markRead(action: String) =
        Intent(action).putExtra(AutoNotifier.EXTRA_CONVERSATION_KEY, key.asString())
}
