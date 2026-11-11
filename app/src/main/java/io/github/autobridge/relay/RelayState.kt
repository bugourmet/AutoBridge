package io.github.autobridge.relay

import io.github.autobridge.filter.RelayFilters
import io.github.autobridge.settings.RelaySettings

/** How relayed notifications are presented. */
data class NotifierConfig(
    /** Not projecting: use the silent channel so the relayed copy doesn't double Slack's alert. */
    val quiet: Boolean = false,
    val quickReplies: List<String> = emptyList(),
    val labelWorkspaces: Boolean = false,
)

data class RelayState(val active: Boolean, val filters: RelayFilters, val notifier: NotifierConfig) {
    companion object {
        fun from(settings: RelaySettings, projecting: Boolean) = RelayState(
            active = settings.enabled && (projecting || !settings.onlyInCar),
            filters = settings.filters,
            notifier = NotifierConfig(
                quiet = !projecting,
                quickReplies = settings.quickReplies,
                // One workspace needs no label; it would only make Auto read more aloud.
                labelWorkspaces = settings.knownWorkspaces.size >= 2,
            ),
        )
    }
}
