package io.github.autobridge.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.autobridge.filter.RelayFilters
import io.github.autobridge.settings.SettingsRepository.Companion.rememberWorkspace
import io.github.autobridge.settings.SettingsRepository.Companion.toRelaySettings
import io.github.autobridge.settings.SettingsRepository.Companion.writeFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelaySettingsTest {
    @Test
    fun `fresh install relays only in car with default quick replies and no filters`() {
        val settings = emptyPreferences().toRelaySettings()

        assertTrue(settings.enabled)
        assertTrue(settings.onlyInCar)
        assertEquals(RelayFilters(), settings.filters)
        assertEquals(SettingsRepository.DEFAULT_QUICK_REPLIES, settings.quickReplies)
    }

    @Test
    fun `reads stored values`() {
        val settings = preferencesOf(
            booleanPreferencesKey("only_in_car") to false,
            stringSetPreferencesKey("muted_keywords") to setOf("standup"),
            stringPreferencesKey("quick_replies") to "Yes\nNo",
        ).toRelaySettings()

        assertFalse(settings.onlyInCar)
        assertEquals(setOf("standup"), settings.filters.mutedKeywords)
        assertEquals(listOf("Yes", "No"), settings.quickReplies)
    }

    @Test
    fun `removing every quick reply stays empty instead of restoring defaults`() {
        val settings = preferencesOf(stringPreferencesKey("quick_replies") to "").toRelaySettings()
        assertEquals(emptyList<String>(), settings.quickReplies)
    }

    @Test
    fun `filters round-trip through storage`() {
        val filters = RelayFilters(
            dmsOnly = true,
            mentionsOnly = true,
            allowedChannels = setOf("general"),
            blockedChannels = setOf("random"),
            mutedKeywords = setOf("standup"),
            blockedWorkspaces = setOf("Acme"),
        )

        val stored = mutablePreferencesOf().apply { writeFilters(filters) }

        assertEquals(filters, stored.toRelaySettings().filters)
    }

    @Test
    fun `known workspaces are capped`() {
        val stored = mutablePreferencesOf()
        repeat(SettingsRepository.MAX_KNOWN_WORKSPACES + 5) { stored.rememberWorkspace("Workspace $it") }

        assertEquals(SettingsRepository.MAX_KNOWN_WORKSPACES, stored.toRelaySettings().knownWorkspaces.size)
    }

    @Test
    fun `remembering a known workspace changes nothing`() {
        val stored = mutablePreferencesOf().apply { rememberWorkspace("Acme") }
        val before = stored.toPreferences()

        stored.rememberWorkspace("Acme")

        assertEquals(before, stored.toPreferences())
    }
}
