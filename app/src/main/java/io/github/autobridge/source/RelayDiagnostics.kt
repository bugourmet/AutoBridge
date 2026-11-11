package io.github.autobridge.source

import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shape of the latest Slack notification, for debugging without adb. Kept in memory and shown in
 * settings. Never includes names, titles or message text, so it is safe to paste into an issue.
 */
object RelayDiagnostics {
    private val _latest = MutableStateFlow<String?>(null)
    val latest: StateFlow<String?> = _latest.asStateFlow()

    enum class Outcome(val label: String) {
        RELAYED("relayed"),
        IGNORED("ignored (not a message)"),
        INACTIVE("not relayed (relay off, or not connected to Android Auto)"),
        FILTERED("not relayed (blocked by your filters)"),
        ALREADY_READ("not relayed (already read or dismissed)"),
        NOT_POSTED("not posted (AutoBridge can't post notifications)"),
    }

    fun record(source: SourceNotification, parsed: ParsedNotification?, outcome: Outcome, alerted: Boolean = false) {
        _latest.value = describe(source, parsed, outcome, alerted)
    }

    fun recordFailure(source: SourceNotification, error: Throwable) {
        _latest.value = "${timeLine(source)}\nresult: failed (${error.javaClass.simpleName})"
    }

    fun describe(source: SourceNotification, parsed: ParsedNotification?, outcome: Outcome, alerted: Boolean = false): String {
        val n = source.notification
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) != null
        val lines = mutableListOf(
            timeLine(source),
            "result: ${outcome.label}",
            "style: ${if (style) "MessagingStyle" else "plain"}",
            "flags: 0x${Integer.toHexString(n.flags)}, category: ${n.category}",
            "shortcut id: ${if (n.shortcutId.isNullOrBlank()) "no" else "yes"}",
        )
        if (parsed != null) {
            val last = parsed.messages.last()
            lines += "group: ${if (parsed.isGroup) "yes" else "no"}"
            lines += "workspace name: ${if (parsed.workspace != null) "found" else "missing"}"
            lines += "messages: ${parsed.messages.size}"
            lines += "last message from: ${if (last.isFromUser) "you (no sender)" else "someone else"}"
            lines += "sender name: ${if (last.sender?.name.isNullOrBlank()) "blank" else "present"}"
            lines += "sender icon: ${iconType(last.sender?.icon)}"
            lines += "reply action: ${if (parsed.actions.reply != null) "found" else "missing"}"
            lines += "mark-read action: ${if (parsed.actions.markRead != null) "found" else "missing"}"
            lines += "alerted: ${if (alerted) "yes" else "no"}"
        }
        return lines.joinToString("\n")
    }

    private fun timeLine(source: SourceNotification) =
        "time: ${SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(source.postTime))}"

    private fun iconType(icon: IconCompat?): String {
        if (icon == null) return "none"
        return when (icon.type) {
            IconCompat.TYPE_BITMAP -> "bitmap"
            IconCompat.TYPE_ADAPTIVE_BITMAP -> "adaptive bitmap"
            IconCompat.TYPE_RESOURCE -> "resource"
            IconCompat.TYPE_DATA -> "data"
            IconCompat.TYPE_URI -> "uri"
            IconCompat.TYPE_URI_ADAPTIVE_BITMAP -> "adaptive uri"
            IconCompat.TYPE_UNKNOWN -> "unknown"
            else -> "unknown"
        }
    }
}
