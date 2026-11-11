package io.github.autobridge.relay

import io.github.autobridge.settings.RelaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayStateTest {
    @Test
    fun `car-only mode relays only while projecting`() {
        val settings = RelaySettings(onlyInCar = true)
        assertTrue(RelayState.from(settings, projecting = true).active)
        assertFalse(RelayState.from(settings, projecting = false).active)
    }

    @Test
    fun `turning car-only off relays everywhere, silently when not projecting`() {
        val settings = RelaySettings(onlyInCar = false)

        val parked = RelayState.from(settings, projecting = false)
        assertTrue(parked.active)
        assertTrue(parked.notifier.quiet)

        val driving = RelayState.from(settings, projecting = true)
        assertTrue(driving.active)
        assertFalse(driving.notifier.quiet)
    }

    @Test
    fun `master switch wins over everything`() {
        assertFalse(RelayState.from(RelaySettings(enabled = false, onlyInCar = false), projecting = true).active)
    }

    @Test
    fun `workspace labels start at two workspaces`() {
        fun label(vararg names: String) =
            RelayState.from(RelaySettings(knownWorkspaces = names.toSet()), projecting = true).notifier.labelWorkspaces

        assertFalse(label())
        assertFalse(label("Acme"))
        assertTrue(label("Acme", "Other"))
    }

    @Test
    fun `quick replies reach the notifier`() {
        val state = RelayState.from(RelaySettings(quickReplies = listOf("OK")), projecting = true)
        assertEquals(listOf("OK"), state.notifier.quickReplies)
    }
}
