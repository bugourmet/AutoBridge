package io.github.autobridge.relay

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.AutoBridgeApp
import io.github.autobridge.filter.RelayFilters
import io.github.autobridge.source.RelayDiagnostics
import io.github.autobridge.source.SlackFixtures
import io.github.autobridge.source.SlackFixtures.source
import io.github.autobridge.source.SlackParser
import io.github.autobridge.source.SourceNotification
import io.github.autobridge.settings.RelaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationRelayTest {
    private val app: AutoBridgeApp = ApplicationProvider.getApplicationContext()
    private val shadowManager get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val workspaces = mutableListOf<String>()
    private val relay by lazy { NotificationRelay(app.store, app.notifier) { workspaces += it } }

    private val relayTag get() = SlackParser.parse(source(SlackFixtures.dm(app)))!!.key.asString()
    private val relayed get() = shadowManager.getNotification(relayTag, AutoNotifier.ID_CONVERSATION)

    private fun state(filters: RelayFilters = RelayFilters(), enabled: Boolean = true) =
        RelayState.from(RelaySettings(enabled = enabled, onlyInCar = false, filters = filters), projecting = true)

    private fun start(state: RelayState = state(), active: List<SourceNotification> = emptyList()) =
        relay.applyState(state) { active }

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `new Slack message is relayed with an alert`() {
        start()
        relay.onPosted(source(SlackFixtures.dm(app)))

        assertEquals(0, relayed!!.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertTrue(RelayDiagnostics.latest.value!!, "result: relayed" in RelayDiagnostics.latest.value!!)
        assertTrue(RelayDiagnostics.latest.value!!, "alerted: yes" in RelayDiagnostics.latest.value!!)
    }

    @Test
    fun `nothing is relayed before settings and car state are known`() {
        relay.onPosted(source(SlackFixtures.dm(app)))

        assertNull(relayed)
        assertTrue("not connected" in RelayDiagnostics.latest.value!!)
    }

    @Test
    fun `inactive relay posts nothing but still learns the workspace`() {
        start(state(enabled = false))
        relay.onPosted(source(SlackFixtures.dm(app, workspace = "Acme")))

        assertNull(relayed)
        assertEquals(listOf("Acme"), workspaces)
    }

    @Test
    fun `filtered message is not relayed`() {
        start(state(RelayFilters(mutedKeywords = setOf("late"))))
        relay.onPosted(source(SlackFixtures.dm(app)))

        assertNull(relayed)
        assertTrue("filters" in RelayDiagnostics.latest.value!!)
    }

    @Test
    fun `other apps' notifications are ignored`() {
        start()
        relay.onPosted(source(SlackFixtures.dm(app), packageName = "com.example.chat"))

        assertEquals(0, shadowManager.size())
    }

    @Test
    fun `resync restores what Slack is showing, without alerting`() {
        start(active = listOf(source(SlackFixtures.dm(app))))

        assertTrue(relayed!!.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun `turning the relay off also clears notifications from before a process restart`() {
        val orphan = app.notifier.build(
            app.store.upsert(SlackParser.parse(source(SlackFixtures.dm(app)))!!)!!.conversation,
            alert = true,
        )
        app.getSystemService(NotificationManager::class.java).notify(relayTag, AutoNotifier.ID_CONVERSATION, orphan)
        app.store.clear()
        assertNotNull(relayed)

        start(state(enabled = false))

        assertNull(relayed)
    }

    @Test
    fun `changing settings keeps delivery-problem notifications`() {
        val key = SlackParser.parse(source(SlackFixtures.dm(app)))!!.key
        app.notifier.postReplyFailed(key, null, "on my way")

        start(state(enabled = false))

        assertNotNull(shadowManager.getNotification(key.asString(), AutoNotifier.ID_REPLY_FAILED))
    }

    @Test
    fun `dismiss sync clears the relayed copy`() {
        start()
        val posted = source(SlackFixtures.dm(app))
        relay.onPosted(posted)

        relay.onRemoved(posted.packageName, posted.key)

        assertNull(relayed)
    }

    @Test
    fun `removals from other apps are ignored`() {
        start()
        val posted = source(SlackFixtures.dm(app))
        relay.onPosted(posted)

        relay.onRemoved("com.example.chat", posted.key)

        assertNotNull(relayed)
    }

    @Test
    fun `read conversation doesn't come back when Slack repeats it`() {
        start()
        val posted = source(SlackFixtures.dm(app))
        relay.onPosted(posted)
        app.store.markRead(posted.let { SlackParser.parse(it)!!.key })
        app.notifier.cancelAllConversations()

        relay.onPosted(posted)

        assertNull(relayed)
        assertTrue("already read" in RelayDiagnostics.latest.value!!)
    }

    @Test
    fun `a blocked notification permission is reported instead of relayed`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        start()

        relay.onPosted(source(SlackFixtures.dm(app)))

        assertTrue("not posted" in RelayDiagnostics.latest.value!!)
        assertFalse("alerted: yes" in RelayDiagnostics.latest.value!!)
    }

    @Test
    fun `a notification that can't be parsed doesn't crash and is reported`() {
        start()
        val broken = SlackFixtures.dm(app).apply { extras = null }

        relay.onPosted(source(broken))

        assertTrue("result: failed" in RelayDiagnostics.latest.value!!)
    }
}
