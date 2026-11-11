package io.github.autobridge.relay

import android.Manifest
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.AutoBridgeApp
import io.github.autobridge.model.ConversationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TestMessageTest {
    private val app: AutoBridgeApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `posts an Auto-shaped conversation from someone else`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        TestMessage.post(app)

        val key = ConversationKey(app.packageName, "test").asString()
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java))
            .getNotification(key, AutoNotifier.ID_CONVERSATION)
        assertNotNull(posted)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(posted)!!
        assertNotNull(style.messages.single().person)
        assertEquals(
            NotificationCompat.Action.SEMANTIC_ACTION_REPLY,
            NotificationCompat.getAction(posted, 0)!!.semanticAction,
        )
        assertFalse(NotificationCompat.getInvisibleActions(posted).isEmpty())
    }
}
