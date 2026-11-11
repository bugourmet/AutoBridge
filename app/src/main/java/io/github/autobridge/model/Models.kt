package io.github.autobridge.model

import android.app.PendingIntent
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat

/** One conversation in a source app. [id] is source-specific (Slack: shortcut id, else notification tag/id). */
data class ConversationKey(val sourcePackage: String, val id: String) {
    /** Stable string form, used as notification tag and intent extra. */
    fun asString(): String = "$sourcePackage$SEPARATOR$id"

    companion object {
        private const val SEPARATOR = '|'

        fun parse(value: String?): ConversationKey? {
            val i = value?.indexOf(SEPARATOR) ?: return null
            if (i <= 0 || i == value.lastIndex) return null
            return ConversationKey(value.substring(0, i), value.substring(i + 1))
        }
    }
}

data class Sender(val name: String, val key: String? = null, val icon: IconCompat? = null)

/** [sender] null means the message was sent by the user (MessagingStyle semantics). */
data class Message(val text: String, val timestamp: Long, val sender: Sender?) {
    val isFromUser: Boolean get() = sender == null

    /** Identity for de-duplication across notification updates; ignores avatar. */
    internal val dedupeId: String get() = "$timestamp|${sender?.key ?: sender?.name}|$text"
}

/** Slack's reply action: its PendingIntent plus the RemoteInputs whose result keys it reads. */
data class ReplyTarget(val intent: PendingIntent, val remoteInputs: List<RemoteInput>)

data class SourceActions(
    val reply: ReplyTarget? = null,
    val markRead: PendingIntent? = null,
    val content: PendingIntent? = null,
)

data class Conversation(
    val key: ConversationKey,
    val title: String,
    val isGroup: Boolean,
    val messages: List<Message>,
    val actions: SourceActions,
    /** StatusBarNotification keys of the source notifications feeding this conversation. */
    val sourceNotificationKeys: Set<String>,
    val workspace: String? = null,
    /** Newest timestamp among messages that came from Slack, unlike the local ones in [messages]. */
    val latestIncoming: Long = Long.MIN_VALUE,
)
