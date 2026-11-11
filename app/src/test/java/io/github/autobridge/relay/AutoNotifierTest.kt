package io.github.autobridge.relay

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.AutoBridgeApp
import io.github.autobridge.model.Conversation
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.Sender
import io.github.autobridge.model.SourceActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AutoNotifierTest {
    private val app: AutoBridgeApp = ApplicationProvider.getApplicationContext()
    private val notifier = AutoNotifier(app)

    private fun conversation(isGroup: Boolean = false) = Conversation(
        key = ConversationKey("com.Slack", "s:D1"),
        title = if (isGroup) "#general" else "Alice",
        isGroup = isGroup,
        messages = listOf(
            Message("hi", 1_000, Sender("Alice", "U1")),
            Message("hey", 2_000, null),
        ),
        actions = SourceActions(),
        sourceNotificationKeys = setOf("sbn"),
    )

    @Test
    fun `relayed notification is MessagingStyle with sender and history`() {
        val n = notifier.build(conversation(), alert = true)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)!!

        assertEquals(listOf("hi", "hey"), style.messages.map { it.text.toString() })
        assertEquals("Alice", style.messages.first().person?.name)
        assertNull(style.messages.last().person) // sent by the user
        assertFalse(style.isGroupConversation)
        assertEquals(NotificationCompat.CATEGORY_MESSAGE, n.category)
        assertEquals(AutoNotifier.CHANNEL_RELAY, n.channelId)
    }

    @Test
    fun `group conversation carries its title`() {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
            notifier.build(conversation(isGroup = true), alert = true),
        )!!
        assertTrue(style.isGroupConversation)
        assertEquals("#general", style.conversationTitle)
    }

    @Test
    fun `has Auto-compatible reply action`() {
        val n = notifier.build(conversation(), alert = true)
        val reply = NotificationCompat.getAction(n, 0)!!

        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_REPLY, reply.semanticAction)
        assertFalse(reply.showsUserInterface)
        assertEquals(AutoNotifier.KEY_TEXT_REPLY, reply.remoteInputs!!.single().resultKey)
    }

    @Test
    fun `has invisible mark-as-read action`() {
        val n = notifier.build(conversation(), alert = true)
        val markRead = NotificationCompat.getInvisibleActions(n).single()

        assertEquals(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ, markRead.semanticAction)
        assertFalse(markRead.showsUserInterface)
    }

    @Test
    fun `quick replies are offered as reply choices with free-form input kept`() {
        val n = notifier.build(conversation(), alert = true, NotifierConfig(quickReplies = listOf("On my way", "OK")))
        val input = NotificationCompat.getAction(n, 0)!!.remoteInputs!!.single()

        assertEquals(listOf("On my way", "OK"), input.choices!!.map { it.toString() })
        assertTrue(input.allowFreeFormInput)
    }

    @Test
    fun `workspace is added to the title only when labels are on`() {
        fun title(isGroup: Boolean, labels: Boolean) = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
            notifier.build(conversation(isGroup).copy(workspace = "Acme"), alert = true, NotifierConfig(labelWorkspaces = labels)),
        )!!.conversationTitle?.toString()

        assertEquals("#general · Acme", title(isGroup = true, labels = true))
        assertEquals("Alice · Acme", title(isGroup = false, labels = true))
        assertEquals("#general", title(isGroup = true, labels = false))
        assertNull(title(isGroup = false, labels = false))
    }

    @Test
    fun `quiet config uses the silent channel`() {
        val n = notifier.build(conversation(), alert = true, NotifierConfig(quiet = true))
        assertEquals(AutoNotifier.CHANNEL_RELAY_QUIET, n.channelId)
    }

    @Test
    fun `post reports when Android wouldn't accept the notification`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.post(conversation(), alert = true))

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notifier.post(conversation(), alert = true))
    }

    @Test
    fun `reply failure keeps the reply's text`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val key = conversation().key

        notifier.postReplyFailed(key, conversation(), "on my way")

        val posted = shadowOf(app.getSystemService(NotificationManager::class.java))
            .getNotification(key.asString(), AutoNotifier.ID_REPLY_FAILED)
        assertTrue(posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("on my way"))
    }

    @Test
    fun `silent re-post only alerts once`() {
        val n = notifier.build(conversation(), alert = false)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }
}
