package io.github.autobridge.source

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.source.SlackFixtures.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SlackParserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `DM messaging style yields sender, title and conversation key`() {
        val parsed = SlackParser.parse(source(SlackFixtures.dm(context, messages = listOf("hi" to 1L, "there" to 2L))))!!

        assertEquals(ConversationKey("com.Slack", "s:T1:D123"), parsed.key)
        assertEquals("Alice", parsed.title)
        assertFalse(parsed.isGroup)
        assertEquals(listOf("hi", "there"), parsed.messages.map { it.text })
        assertEquals("Alice", parsed.messages.last().sender?.name)
        assertNotNull(parsed.messages.last().sender?.icon)
        assertEquals(2L, parsed.messages.last().timestamp)
    }

    @Test
    fun `channel mention is a group conversation titled by channel`() {
        val parsed = SlackParser.parse(source(SlackFixtures.channel(context)))!!

        assertEquals("#general", parsed.title)
        assertTrue(parsed.isGroup)
        assertEquals("Bob", parsed.messages.single().sender?.name)
        assertEquals("@me deploy is done", parsed.messages.single().text)
    }

    @Test
    fun `finds Slack reply intent and its RemoteInput result key`() {
        val parsed = SlackParser.parse(source(SlackFixtures.dm(context)))!!

        val reply = parsed.actions.reply!!
        assertEquals(listOf(SlackFixtures.SLACK_REPLY_KEY), reply.remoteInputs.map { it.resultKey })
        assertNotNull(parsed.actions.markRead)
        assertNotNull(parsed.actions.content)
    }

    @Test
    fun `finds actions by RemoteInput and title when semantic actions are missing`() {
        val parsed = SlackParser.parse(source(SlackFixtures.plain(context, "Alice", "hi")))!!

        assertNotNull(parsed.actions.reply)
        assertNotNull(parsed.actions.markRead)
    }

    @Test
    fun `mark-read action that opens UI is not forwarded`() {
        val n = SlackFixtures.dm(
            context,
            actions = listOf(SlackFixtures.replyAction(context), SlackFixtures.markReadAction(context, showsUi = true)),
        )
        assertNull(SlackParser.parse(source(n))!!.actions.markRead)
    }

    @Test
    fun `no actions means no reply target`() {
        val parsed = SlackParser.parse(source(SlackFixtures.dm(context, actions = emptyList())))!!
        assertNull(parsed.actions.reply)
        assertNull(parsed.actions.markRead)
    }

    @Test
    fun `plain DM falls back to title and text`() {
        val parsed = SlackParser.parse(source(SlackFixtures.plain(context, "Carol", "lunch?", `when` = 5L)))!!

        assertEquals("Carol", parsed.title)
        assertFalse(parsed.isGroup)
        assertEquals("lunch?", parsed.messages.single().text)
        assertEquals("Carol", parsed.messages.single().sender?.name)
        assertEquals(5L, parsed.messages.single().timestamp)
    }

    @Test
    fun `plain channel notification splits sender from text`() {
        val parsed = SlackParser.parse(source(SlackFixtures.plain(context, "#random", "Dave: anyone up for coffee?")))!!

        assertTrue(parsed.isGroup)
        assertEquals("Dave", parsed.messages.single().sender?.name)
        assertEquals("anyone up for coffee?", parsed.messages.single().text)
    }

    @Test
    fun `without shortcut id the key falls back to tag and id`() {
        val parsed = SlackParser.parse(source(SlackFixtures.dm(context, shortcutId = null), tag = "T1:D9", id = 7))!!
        assertEquals("t:T1:D9:7", parsed.key.id)
    }

    private fun styled(style: NotificationCompat.MessagingStyle) =
        NotificationCompat.Builder(context, "slack")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setStyle(style)
            .build()

    @Test
    fun `a message from the style's own user counts as the user's`() {
        val me = Person.Builder().setName("Me").setKey("U_ME").build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage("hi", 1, SlackFixtures.person("Alice"))
            .addMessage("on my way", 2, me)

        val messages = SlackParser.parse(source(styled(style)))!!.messages

        assertFalse(messages.first().isFromUser)
        assertTrue(messages.last().isFromUser)
    }

    @Test
    fun `avatars Slack serves from a content URI are dropped`() {
        val alice = Person.Builder().setName("Alice").setKey("U1")
            .setIcon(IconCompat.createWithContentUri("content://com.Slack.provider/avatar/1"))
            .build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .addMessage("hi", 1, alice)

        val sender = SlackParser.parse(source(styled(style)))!!.messages.single().sender!!

        assertEquals("Alice", sender.name)
        assertNull(sender.icon)
    }

    @Test
    fun `a hash title counts as a channel even without the group flag`() {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .setConversationTitle("#general")
            .setGroupConversation(false)
            .addMessage("hi", 1, SlackFixtures.person("Bob"))

        assertTrue(SlackParser.parse(source(styled(style)))!!.isGroup)
    }

    @Test
    fun `workspace comes from the notification's sub text`() {
        assertEquals("Acme", SlackParser.parse(source(SlackFixtures.dm(context, workspace = " Acme ")))!!.workspace)
        assertNull(SlackParser.parse(source(SlackFixtures.dm(context)))!!.workspace)
    }

    @Test
    fun `ignores group summaries, huddles, other apps and empty notifications`() {
        assertNull(SlackParser.parse(source(SlackFixtures.groupSummary(context))))
        assertNull(SlackParser.parse(source(SlackFixtures.huddle(context))))
        assertNull(SlackParser.parse(source(SlackFixtures.dm(context), packageName = "com.example.chat")))
        assertNull(SlackParser.parse(source(SlackFixtures.plain(context, "Alice", " "))))
    }
}
