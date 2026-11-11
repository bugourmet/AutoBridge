package io.github.autobridge.filter

import io.github.autobridge.model.Message
import io.github.autobridge.source.ParsedNotification

data class RelayFilters(
    val dmsOnly: Boolean = false,
    /** Applies to channels and group DMs; one-on-one DMs always pass. */
    val mentionsOnly: Boolean = false,
    val allowedChannels: Set<String> = emptySet(),
    val blockedChannels: Set<String> = emptySet(),
    val mutedKeywords: Set<String> = emptySet(),
    val blockedWorkspaces: Set<String> = emptySet(),
)

/** Decides what reaches Android Auto. Works per message, since Slack's notifications carry history. */
object FilterEngine {
    // Slack doesn't flag mentions, so any @name counts. The lookbehind skips email addresses.
    private val MENTION = Regex("""(?<![\p{L}\p{N}_.])@[\p{L}\p{N}_]""")

    /** @return the notification with only its permitted messages, or null if nothing new may be relayed. */
    fun apply(parsed: ParsedNotification, filters: RelayFilters): ParsedNotification? {
        val workspace = parsed.workspace?.normalized()
        if (workspace != null && workspace in filters.blockedWorkspaces.mapTo(HashSet()) { it.normalized() }) return null

        if (parsed.isGroup) {
            if (filters.dmsOnly) return null
            val channel = parsed.title.channelName()
            if (channel in filters.blockedChannels.mapTo(HashSet()) { it.channelName() }) return null
            val allowed = filters.allowedChannels.mapTo(HashSet()) { it.channelName() }
            if (allowed.isNotEmpty() && channel !in allowed) return null
        }

        val mutedPatterns = filters.mutedKeywords.map(::keywordPattern)
        val passes = { m: Message ->
            mutedPatterns.none { it.containsMatchIn(m.text) } &&
                (!filters.mentionsOnly || !parsed.isGroup || MENTION.containsMatchIn(m.text))
        }
        if (parsed.messages.none { !it.isFromUser && passes(it) }) return null
        return parsed.copy(messages = parsed.messages.filter { it.isFromUser || passes(it) })
    }

    /** Whole-word and case-insensitive, so "sale" doesn't mute "wholesale". */
    private fun keywordPattern(keyword: String) =
        Regex("""(?<![\p{L}\p{N}])${Regex.escape(keyword.trim())}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    private fun String.normalized() = trim().lowercase()

    private fun String.channelName() = trim().removePrefix("#").lowercase()
}
