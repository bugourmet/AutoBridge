package io.github.autobridge.source

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Message
import io.github.autobridge.model.SourceActions

/** The parts of a StatusBarNotification the parser needs; decoupled so tests can build them directly. */
data class SourceNotification(
    val packageName: String,
    val key: String,
    val tag: String?,
    val id: Int,
    val postTime: Long,
    val notification: Notification,
)

fun StatusBarNotification.toSource() = SourceNotification(packageName, key, tag, id, postTime, notification)

/** Parser output for one source notification. */
data class ParsedNotification(
    val key: ConversationKey,
    val sourceNotificationKey: String,
    val title: String,
    val isGroup: Boolean,
    val messages: List<Message>,
    val actions: SourceActions,
    val workspace: String? = null,
)
