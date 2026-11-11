package io.github.autobridge.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.source.RelayDiagnostics.Outcome
import io.github.autobridge.source.SlackFixtures.source
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelayDiagnosticsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun describe(source: SourceNotification, outcome: Outcome = Outcome.RELAYED) =
        RelayDiagnostics.describe(source, SlackParser.parse(source), outcome, alerted = true)

    @Test
    fun `reports the shape of a relayed notification`() {
        val text = describe(source(SlackFixtures.dm(context, workspace = "Acme")))

        assertTrue(text, "result: relayed" in text)
        assertTrue(text, "style: MessagingStyle" in text)
        assertTrue(text, "workspace name: found" in text)
        assertTrue(text, "last message from: someone else" in text)
        assertTrue(text, "sender icon: bitmap" in text)
        assertTrue(text, "reply action: found" in text)
        assertTrue(text, "mark-read action: found" in text)
    }

    @Test
    fun `reports why a notification was not relayed`() {
        assertTrue("ignored" in describe(source(SlackFixtures.huddle(context)), Outcome.IGNORED))
        assertTrue("not connected" in describe(source(SlackFixtures.dm(context)), Outcome.INACTIVE))
        assertTrue("filters" in describe(source(SlackFixtures.dm(context)), Outcome.FILTERED))
    }

    @Test
    fun `a failure reports only the error type`() {
        RelayDiagnostics.recordFailure(source(SlackFixtures.dm(context)), IllegalStateException("secret message"))

        val text = RelayDiagnostics.latest.value!!
        assertTrue(text, "result: failed (IllegalStateException)" in text)
        assertFalse(text, "secret message" in text)
    }

    @Test
    fun `never contains names, workspaces or message text`() {
        val text = describe(
            source(SlackFixtures.dm(context, sender = "Alice", messages = listOf("Running late" to 1L), workspace = "Acme")),
        )

        assertFalse(text, "Alice" in text)
        assertFalse(text, "Running late" in text)
        assertFalse(text, "Acme" in text)
    }
}
