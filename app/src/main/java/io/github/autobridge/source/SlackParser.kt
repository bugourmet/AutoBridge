package io.github.autobridge.source

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.ReplyTarget
import io.github.autobridge.model.Sender
import io.github.autobridge.model.SourceActions

/** Slack notification → [ParsedNotification]. All Slack-specific knowledge lives here. */
object SlackParser {
    const val SLACK_PACKAGE = "com.Slack"

    private val MARK_READ_TITLE = Regex("""mark\b.*\bread""", RegexOption.IGNORE_CASE)
    private val CHANNEL_LINE = Regex("""^([^:\n]{1,80}):\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)

    fun parse(source: SourceNotification): ParsedNotification? {
        if (source.packageName != SLACK_PACKAGE) return null
        val n = source.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        // Huddles, calls and sync progress are ongoing; they are not messages.
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return null
        if (n.category in IGNORED_CATEGORIES) return null

        val content = parseMessagingStyle(n, source.postTime) ?: parsePlain(n, source.postTime) ?: return null
        return ParsedNotification(
            key = ConversationKey(SLACK_PACKAGE, conversationId(source)),
            sourceNotificationKey = source.key,
            title = content.title,
            isGroup = content.isGroup,
            messages = content.messages,
            actions = parseActions(n),
            // Slack shows the workspace name as the notification's sub text.
            workspace = n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private class Content(val title: String, val isGroup: Boolean, val messages: List<Message>)

    private fun conversationId(source: SourceNotification): String =
        source.notification.shortcutId?.takeIf { it.isNotBlank() }?.let { "s:$it" }
            ?: "t:${source.tag.orEmpty()}:${source.id}"

    private fun parseMessagingStyle(n: Notification, postTime: Long): Content? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) ?: return null
        val messages = style.messages.mapNotNull { m ->
            val text = m.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sender = m.person?.takeUnless { it.isSamePerson(style.user) }?.let { p ->
                Sender(name = p.name?.toString().orEmpty(), key = p.key, icon = p.icon?.takeUnless { it.isUriBacked() })
            }
            Message(text, if (m.timestamp > 0) m.timestamp else postTime, sender)
        }
        if (messages.isEmpty()) return null
        val title = style.conversationTitle?.toString()
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: messages.lastOrNull { !it.isFromUser }?.sender?.name
            ?: return null
        return Content(title, style.isGroupConversation || title.startsWith("#"), messages)
    }

    /** Slack may mark the user's own messages with their Person instead of leaving the sender null. */
    private fun Person.isSamePerson(other: Person): Boolean =
        if (key != null && other.key != null) {
            key == other.key
        } else {
            name != null && name.toString() == other.name?.toString()
        }

    /** Only Slack can read its content URIs, and Android refuses notifications from apps that can't. */
    private fun IconCompat.isUriBacked() = type == IconCompat.TYPE_URI || type == IconCompat.TYPE_URI_ADAPTIVE_BITMAP

    /** Fallback when Slack posts a plain notification: title = DM sender or "#channel", text = message. */
    private fun parsePlain(n: Notification, postTime: Long): Content? {
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val timestamp = if (n.`when` > 0) n.`when` else postTime

        if (title.startsWith("#")) {
            val match = CHANNEL_LINE.matchEntire(text)
            val sender = Sender(match?.groupValues?.get(1)?.trim() ?: title)
            val body = match?.groupValues?.get(2)?.trim() ?: text
            return Content(title, isGroup = true, messages = listOf(Message(body, timestamp, sender)))
        }
        return Content(title, isGroup = false, messages = listOf(Message(text, timestamp, Sender(title))))
    }

    private fun parseActions(n: Notification): SourceActions {
        val actions = (0 until NotificationCompat.getActionCount(n)).mapNotNull { NotificationCompat.getAction(n, it) }

        fun NotificationCompat.Action.freeFormInputs() =
            remoteInputs.orEmpty().filter { it.allowFreeFormInput }

        val reply = actions.firstOrNull {
            it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY && it.freeFormInputs().isNotEmpty()
        } ?: actions.firstOrNull { it.freeFormInputs().isNotEmpty() }

        val markRead = actions.firstOrNull { it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ }
            ?: actions.firstOrNull { it.title?.let { t -> MARK_READ_TITLE.containsMatchIn(t) } == true }

        return SourceActions(
            reply = reply?.actionIntent?.let { ReplyTarget(it, reply.freeFormInputs()) },
            // An action that opens UI can't run from the car; only forward background ones.
            markRead = markRead?.takeUnless { it.showsUserInterface }?.actionIntent,
            content = n.contentIntent,
        )
    }

    private val IGNORED_CATEGORIES = setOf(
        Notification.CATEGORY_CALL,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_TRANSPORT,
    )
}
