package io.github.autobridge.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.autobridge.filter.RelayFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class RelaySettings(
    val enabled: Boolean = true,
    val onlyInCar: Boolean = true,
    val filters: RelayFilters = RelayFilters(),
    val quickReplies: List<String> = SettingsRepository.DEFAULT_QUICK_REPLIES,
    /** Workspace names seen in Slack's notifications, offered as per-workspace switches. */
    val knownWorkspaces: Set<String> = emptySet(),
)

/**
 * User settings. Holds preferences and workspace names only, never message content. Edits read the
 * stored value inside the transaction, so quick successive changes can't overwrite each other.
 */
class SettingsRepository(private val context: Context) {
    val relaySettings: Flow<RelaySettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toRelaySettings() }

    suspend fun setRelayEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    suspend fun setOnlyInCar(onlyInCar: Boolean) = edit { it[ONLY_IN_CAR] = onlyInCar }

    suspend fun updateFilters(change: RelayFilters.() -> RelayFilters) = edit {
        it.writeFilters(it.toRelaySettings().filters.change())
    }

    suspend fun updateQuickReplies(change: List<String>.() -> List<String>) = edit {
        it[QUICK_REPLIES] = it.toRelaySettings().quickReplies.change().joinToString(LIST_SEPARATOR)
    }

    /** Called for every notification; DataStore skips the disk write when nothing changed. */
    suspend fun addKnownWorkspace(name: String) = edit { it.rememberWorkspace(name) }

    suspend fun forgetKnownWorkspaces() = edit { it.remove(KNOWN_WORKSPACES) }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit(change)
        } catch (e: IOException) {
            // For example a full disk. The setting stays as it was, which the UI keeps showing.
            Log.w(TAG, "Couldn't save settings", e)
        }
    }

    companion object {
        val DEFAULT_QUICK_REPLIES = listOf("On my way", "I'm driving, will reply later", "OK")

        private const val TAG = "SettingsRepository"
        private const val LIST_SEPARATOR = "\n"
        internal const val MAX_KNOWN_WORKSPACES = 20
        private val ENABLED = booleanPreferencesKey("relay_enabled")
        private val ONLY_IN_CAR = booleanPreferencesKey("only_in_car")
        private val DMS_ONLY = booleanPreferencesKey("dms_only")
        private val MENTIONS_ONLY = booleanPreferencesKey("mentions_only")
        private val ALLOWED_CHANNELS = stringSetPreferencesKey("allowed_channels")
        private val BLOCKED_CHANNELS = stringSetPreferencesKey("blocked_channels")
        private val MUTED_KEYWORDS = stringSetPreferencesKey("muted_keywords")
        private val BLOCKED_WORKSPACES = stringSetPreferencesKey("blocked_workspaces")
        private val KNOWN_WORKSPACES = stringSetPreferencesKey("known_workspaces")
        private val QUICK_REPLIES = stringPreferencesKey("quick_replies")

        internal fun Preferences.toRelaySettings() = RelaySettings(
            enabled = this[ENABLED] ?: true,
            onlyInCar = this[ONLY_IN_CAR] ?: true,
            filters = RelayFilters(
                dmsOnly = this[DMS_ONLY] ?: false,
                mentionsOnly = this[MENTIONS_ONLY] ?: false,
                allowedChannels = this[ALLOWED_CHANNELS].orEmpty(),
                blockedChannels = this[BLOCKED_CHANNELS].orEmpty(),
                mutedKeywords = this[MUTED_KEYWORDS].orEmpty(),
                blockedWorkspaces = this[BLOCKED_WORKSPACES].orEmpty(),
            ),
            quickReplies = this[QUICK_REPLIES]?.split(LIST_SEPARATOR)?.filter { it.isNotBlank() } ?: DEFAULT_QUICK_REPLIES,
            knownWorkspaces = this[KNOWN_WORKSPACES].orEmpty(),
        )

        internal fun MutablePreferences.writeFilters(filters: RelayFilters) {
            this[DMS_ONLY] = filters.dmsOnly
            this[MENTIONS_ONLY] = filters.mentionsOnly
            this[ALLOWED_CHANNELS] = filters.allowedChannels
            this[BLOCKED_CHANNELS] = filters.blockedChannels
            this[MUTED_KEYWORDS] = filters.mutedKeywords
            this[BLOCKED_WORKSPACES] = filters.blockedWorkspaces
        }

        /** Capped because the name is Slack's sub text, which isn't guaranteed to be a short fixed list. */
        internal fun MutablePreferences.rememberWorkspace(name: String) {
            val known = this[KNOWN_WORKSPACES].orEmpty()
            if (name !in known && known.size < MAX_KNOWN_WORKSPACES) this[KNOWN_WORKSPACES] = known + name
        }
    }
}
