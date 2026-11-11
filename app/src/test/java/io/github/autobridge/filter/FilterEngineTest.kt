package io.github.autobridge.filter

import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.Sender
import io.github.autobridge.model.SourceActions
import io.github.autobridge.source.ParsedNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FilterEngineTest {
    private val alice = Sender("Alice", "U1")

    private fun notification(
        vararg texts: String,
        title: String = "Alice",
        isGroup: Boolean = false,
        workspace: String? = "Acme",
    ) = ParsedNotification(
        key = ConversationKey("com.Slack", "s:1"),
        sourceNotificationKey = "sbn",
        title = title,
        isGroup = isGroup,
        messages = texts.mapIndexed { i, text -> Message(text, i.toLong(), alice) },
        actions = SourceActions(),
        workspace = workspace,
    )

    private fun channel(vararg texts: String, title: String = "#general") = notification(*texts, title = title, isGroup = true)

    @Test
    fun `no filters passes everything through unchanged`() {
        val parsed = channel("hello")
        assertEquals(parsed, FilterEngine.apply(parsed, RelayFilters()))
    }

    @Test
    fun `DMs only drops channels and keeps DMs`() {
        val filters = RelayFilters(dmsOnly = true)
        assertNull(FilterEngine.apply(channel("hello"), filters))
        assertNotNull(FilterEngine.apply(notification("hello"), filters))
    }

    @Test
    fun `mentions only keeps channel messages with an @name and all DMs`() {
        val filters = RelayFilters(mentionsOnly = true)

        val kept = FilterEngine.apply(channel("no ping", "@alice please look", "@here lunch"), filters)!!
        assertEquals(listOf("@alice please look", "@here lunch"), kept.messages.map { it.text })
        assertNull(FilterEngine.apply(channel("no ping"), filters))
        assertNotNull(FilterEngine.apply(notification("no ping"), filters))
    }

    @Test
    fun `an email address is not a mention`() {
        assertNull(FilterEngine.apply(channel("write to bob@example.com"), RelayFilters(mentionsOnly = true)))
    }

    @Test
    fun `allow list drops other channels, ignoring case and the hash`() {
        val filters = RelayFilters(allowedChannels = setOf("General"))
        assertNotNull(FilterEngine.apply(channel("hi", title = "#general"), filters))
        assertNull(FilterEngine.apply(channel("hi", title = "#random"), filters))
    }

    @Test
    fun `block list wins over allow list`() {
        val filters = RelayFilters(allowedChannels = setOf("general"), blockedChannels = setOf("#GENERAL"))
        assertNull(FilterEngine.apply(channel("hi"), filters))
    }

    @Test
    fun `channel lists do not affect DMs`() {
        val filters = RelayFilters(allowedChannels = setOf("general"), blockedChannels = setOf("Alice"))
        assertNotNull(FilterEngine.apply(notification("hi"), filters))
    }

    @Test
    fun `muted keywords remove only matching messages, in DMs too`() {
        val filters = RelayFilters(mutedKeywords = setOf("standup"))

        val kept = FilterEngine.apply(notification("STANDUP moved", "call me"), filters)!!
        assertEquals(listOf("call me"), kept.messages.map { it.text })
        assertNull(FilterEngine.apply(notification("standup moved"), filters))
    }

    @Test
    fun `muted keywords match whole words and treat regex characters literally`() {
        assertNotNull(FilterEngine.apply(notification("wholesale prices"), RelayFilters(mutedKeywords = setOf("sale"))))
        assertNull(FilterEngine.apply(notification("build a.b failed"), RelayFilters(mutedKeywords = setOf("a.b"))))
        assertNotNull(FilterEngine.apply(notification("build axb failed"), RelayFilters(mutedKeywords = setOf("a.b"))))
    }

    @Test
    fun `blocked workspace drops its messages only`() {
        val filters = RelayFilters(blockedWorkspaces = setOf("acme"))
        assertNull(FilterEngine.apply(notification("hi", workspace = "Acme"), filters))
        assertNotNull(FilterEngine.apply(notification("hi", workspace = "Other"), filters))
        assertNotNull(FilterEngine.apply(notification("hi", workspace = null), filters))
    }

    @Test
    fun `the user's own messages stay as context but never trigger a relay`() {
        val own = Message("on my way", 5, null)
        val incoming = Message("where are you", 4, alice)
        val base = notification("x")

        assertNull(FilterEngine.apply(base.copy(messages = listOf(own)), RelayFilters()))
        val kept = FilterEngine.apply(base.copy(messages = listOf(incoming, own)), RelayFilters())!!
        assertSame(own, kept.messages.last())
    }
}
