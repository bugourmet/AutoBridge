package io.github.autobridge.store

import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.Sender
import io.github.autobridge.model.SourceActions
import io.github.autobridge.source.ParsedNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStoreTest {
    private val dm = ConversationKey("com.Slack", "s:D1")
    private val alice = Sender("Alice", "U1")

    private fun parsed(
        vararg messages: Message,
        key: ConversationKey = dm,
        sbnKey: String = "sbn-1",
        actions: SourceActions = SourceActions(),
    ) = ParsedNotification(key, sbnKey, "Alice", false, messages.toList(), actions)

    private fun msg(text: String, ts: Long, sender: Sender? = alice) = Message(text, ts, sender)

    private fun ownReply(text: String, ts: Long) = Message(text, ts, sender = null)

    @Test
    fun `three messages in one DM thread into one conversation`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("a", 1)))
        store.upsert(parsed(msg("b", 2)))
        store.upsert(parsed(msg("c", 3)))

        assertEquals(listOf("a", "b", "c"), store.get(dm)!!.messages.map { it.text })
    }

    @Test
    fun `full-history updates are de-duplicated`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("a", 1)))
        val update = store.upsert(parsed(msg("a", 1), msg("b", 2)))!!

        assertEquals(listOf("a", "b"), update.conversation.messages.map { it.text })
        assertTrue(update.hasNewIncoming)
    }

    @Test
    fun `re-posting the same notification does not count as new`() {
        val store = ConversationStore()
        assertTrue(store.upsert(parsed(msg("a", 1)))!!.hasNewIncoming)
        assertFalse(store.upsert(parsed(msg("a", 1)))!!.hasNewIncoming)
    }

    @Test
    fun `history is capped at the last ten messages and trimmed ones do not re-alert`() {
        val store = ConversationStore()
        val all = (1..15).map { msg("m$it", it.toLong()) }
        store.upsert(parsed(*all.toTypedArray()))

        val conversation = store.get(dm)!!
        assertEquals(10, conversation.messages.size)
        assertEquals("m6", conversation.messages.first().text)
        assertFalse(store.upsert(parsed(*all.toTypedArray()))!!.hasNewIncoming)
    }

    @Test
    fun `own reply is appended and Slack's echo of it is dropped`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("hi", 1_000)))
        store.appendLocal(dm, ownReply("on my way", 2_000))

        val update = store.upsert(parsed(msg("hi", 1_000), ownReply("on my way", 2_500)))!!

        assertEquals(listOf("hi", "on my way"), update.conversation.messages.map { it.text })
        assertTrue(update.conversation.messages.last().isFromUser)
        assertFalse(update.hasNewIncoming)
    }

    @Test
    fun `a phone clock ahead of Slack doesn't silence the next incoming message`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("hi", 1_000)))
        store.appendLocal(dm, ownReply("on my way", 90_000))

        assertTrue(store.upsert(parsed(msg("where are you", 60_000)))!!.hasNewIncoming)
    }

    @Test
    fun `read messages stay gone when Slack repeats them`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("a", 1)))
        store.markRead(dm)

        assertNull(store.upsert(parsed(msg("a", 1))))
        assertNull(store.get(dm))
    }

    @Test
    fun `a new message after reading shows without the read history and alerts`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("a", 1)))
        store.markRead(dm)

        val update = store.upsert(parsed(msg("a", 1), msg("b", 2)))!!

        assertEquals(listOf("b"), update.conversation.messages.map { it.text })
        assertTrue(update.hasNewIncoming)
    }

    @Test
    fun `read state survives clear, so reconnecting doesn't restore read messages`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("a", 1)))
        store.markRead(dm)
        store.clear()

        assertNull(store.upsert(parsed(msg("a", 1))))
    }

    @Test
    fun `the user's own message alone doesn't start a conversation`() {
        assertNull(ConversationStore().upsert(parsed(ownReply("sent from desktop", 5))))
    }

    @Test
    fun `refreshActions tracks the source without adding messages or creating conversations`() {
        val store = ConversationStore()
        store.refreshActions(parsed(msg("muted", 1)))
        assertNull(store.get(dm))

        store.upsert(parsed(msg("a", 1), sbnKey = "sbn-1"))
        store.refreshActions(parsed(msg("muted", 2), sbnKey = "sbn-2"))

        assertEquals(listOf("a"), store.get(dm)!!.messages.map { it.text })
        assertEquals(setOf("sbn-1", "sbn-2"), store.get(dm)!!.sourceNotificationKeys)
    }

    @Test
    fun `dismiss sync removes only the conversation fed by the removed notification`() {
        val store = ConversationStore()
        val channel = ConversationKey("com.Slack", "s:C1")
        store.upsert(parsed(msg("a", 1), sbnKey = "sbn-1"))
        store.upsert(parsed(msg("b", 2), key = channel, sbnKey = "sbn-2"))

        assertEquals(listOf(dm), store.removeBySourceKey("sbn-1"))
        assertNull(store.get(dm))
        assertNotNull(store.get(channel))
        assertEquals(emptyList<ConversationKey>(), store.removeBySourceKey("unknown"))
    }

    @Test
    fun `a conversation with several source notifications ends with the last one`() {
        val store = ConversationStore()
        store.upsert(parsed(msg("a", 1), sbnKey = "sbn-1"))
        store.upsert(parsed(msg("b", 2), sbnKey = "sbn-2"))

        assertEquals(emptyList<ConversationKey>(), store.removeBySourceKey("sbn-1"))
        assertNotNull(store.get(dm))

        assertEquals(listOf(dm), store.removeBySourceKey("sbn-2"))
        assertNull(store.get(dm))
    }

    @Test
    fun `appendLocal on unknown conversation returns null`() {
        assertNull(ConversationStore().appendLocal(dm, ownReply("x", 1)))
    }

    @Test
    fun `conversation key round-trips through its string form`() {
        assertEquals(dm, ConversationKey.parse(dm.asString()))
        assertEquals(ConversationKey("com.Slack", "t:a|b:1"), ConversationKey.parse("com.Slack|t:a|b:1"))
        assertNull(ConversationKey.parse(null))
        assertNull(ConversationKey.parse("nokey"))
    }
}
