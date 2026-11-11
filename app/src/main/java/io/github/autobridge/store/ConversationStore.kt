package io.github.autobridge.store

import io.github.autobridge.model.Conversation
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.source.ParsedNotification

/** In-memory conversation state, rebuilt from active notifications when the listener connects. */
class ConversationStore(private val historyLimit: Int = DEFAULT_HISTORY_LIMIT) {

    /** [hasNewIncoming]: a message from someone else newer than anything seen before (worth alerting). */
    data class Update(val conversation: Conversation, val hasNewIncoming: Boolean)

    private val conversations = LinkedHashMap<ConversationKey, Conversation>()

    /**
     * Newest incoming message the user has read or dismissed, per conversation. Slack's notifications
     * repeat their history, so anything up to here must not come back. Survives [clear]; one small
     * entry per conversation for the life of the process.
     */
    private val readUpTo = HashMap<ConversationKey, Long>()

    @Synchronized
    fun get(key: ConversationKey): Conversation? = conversations[key]

    /** @return null when the notification holds nothing the user hasn't already read. */
    @Synchronized
    fun upsert(parsed: ParsedNotification): Update? {
        val existing = conversations[parsed.key]
        val previous = existing?.messages.orEmpty()
        val read = readUpTo[parsed.key] ?: Long.MIN_VALUE

        val known = previous.mapTo(HashSet()) { it.dedupeId }
        val added = parsed.messages.filter {
            it.timestamp > read && it.dedupeId !in known && !isEchoOfOwnReply(it, previous)
        }
        val incoming = added.filterNot { it.isFromUser }
        if (existing == null && incoming.isEmpty()) return null

        // Only Slack's timestamps count here; local ones (own replies, notices) use the phone's clock.
        val latestSeen = existing?.latestIncoming ?: Long.MIN_VALUE
        val conversation = Conversation(
            key = parsed.key,
            title = parsed.title,
            isGroup = parsed.isGroup,
            messages = (previous + added).sortedBy { it.timestamp }.takeLast(historyLimit),
            // Always keep Slack's latest intents; older ones may be stale.
            actions = parsed.actions,
            sourceNotificationKeys = existing?.sourceNotificationKeys.orEmpty() + parsed.sourceNotificationKey,
            workspace = parsed.workspace,
            latestIncoming = maxOf(latestSeen, incoming.maxOfOrNull { it.timestamp } ?: Long.MIN_VALUE),
        )
        conversations[parsed.key] = conversation
        return Update(conversation, incoming.any { it.timestamp > latestSeen })
    }

    /** A filtered update adds no messages, but Slack's newest intents must still replace stale ones. */
    @Synchronized
    fun refreshActions(parsed: ParsedNotification) {
        val existing = conversations[parsed.key] ?: return
        conversations[parsed.key] = existing.copy(
            actions = parsed.actions,
            sourceNotificationKeys = existing.sourceNotificationKeys + parsed.sourceNotificationKey,
        )
    }

    /** Adds a message that didn't come from Slack: the user's reply, or a notice from AutoBridge. */
    @Synchronized
    fun appendLocal(key: ConversationKey, message: Message): Conversation? {
        val existing = conversations[key] ?: return null
        val updated = existing.copy(messages = (existing.messages + message).takeLast(historyLimit))
        conversations[key] = updated
        return updated
    }

    /** The user read or dismissed the conversation: drop it and keep its messages from coming back. */
    @Synchronized
    fun markRead(key: ConversationKey): Conversation? {
        val removed = conversations.remove(key) ?: return null
        readUpTo[key] = maxOf(readUpTo[key] ?: Long.MIN_VALUE, removed.latestIncoming)
        return removed
    }

    /**
     * Dismiss sync: a conversation can have several source notifications, and it ends with the last one.
     * @return the conversations that ended.
     */
    @Synchronized
    fun removeBySourceKey(sourceNotificationKey: String): List<ConversationKey> {
        val ended = mutableListOf<ConversationKey>()
        conversations.values.toList()
            .filter { sourceNotificationKey in it.sourceNotificationKeys }
            .forEach { conversation ->
                val remaining = conversation.sourceNotificationKeys - sourceNotificationKey
                if (remaining.isEmpty()) {
                    conversations.remove(conversation.key)
                    ended += conversation.key
                } else {
                    conversations[conversation.key] = conversation.copy(sourceNotificationKeys = remaining)
                }
            }
        return ended
    }

    @Synchronized
    fun clear() = conversations.clear()

    /** Slack echoes the user's reply back with its own timestamp; match by text near our copy. */
    private fun isEchoOfOwnReply(message: Message, previous: List<Message>): Boolean =
        message.isFromUser && previous.any {
            it.isFromUser && it.text == message.text && kotlin.math.abs(it.timestamp - message.timestamp) <= ECHO_WINDOW_MS
        }

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 10
        private const val ECHO_WINDOW_MS = 2 * 60_000L
    }
}
