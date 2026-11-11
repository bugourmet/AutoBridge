package io.github.autobridge.source

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat

/**
 * Slack-shaped notifications built in code. Shapes follow Slack's public behaviour (MessagingStyle,
 * RemoteInput reply, mark-read action); replace with real dumps from NotificationDumper as captured.
 */
object SlackFixtures {
    const val SLACK_REPLY_KEY = "slack_reply_text"
    const val ACTION_SLACK_REPLY = "com.Slack.test.REPLY"
    const val ACTION_SLACK_MARK_READ = "com.Slack.test.MARK_READ"

    fun slackReplyIntent(context: Context, requestCode: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, Intent(ACTION_SLACK_REPLY), PendingIntent.FLAG_MUTABLE)

    fun slackMarkReadIntent(context: Context, requestCode: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, Intent(ACTION_SLACK_MARK_READ), PendingIntent.FLAG_IMMUTABLE)

    fun replyAction(context: Context, semantic: Boolean = true): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, "Reply", slackReplyIntent(context))
            .addRemoteInput(RemoteInput.Builder(SLACK_REPLY_KEY).setLabel("Reply").build())
            .apply { if (semantic) setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY) }
            .build()

    fun markReadAction(context: Context, semantic: Boolean = true, showsUi: Boolean = false): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, "Mark as read", slackMarkReadIntent(context))
            .setShowsUserInterface(showsUi)
            .apply { if (semantic) setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ) }
            .build()

    fun person(name: String, key: String = "U_$name") =
        Person.Builder().setName(name).setKey(key).setIcon(IconCompat.createWithBitmap(
            android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888),
        )).build()

    /** DM: one-on-one MessagingStyle, shortcut per conversation. */
    fun dm(
        context: Context,
        sender: String = "Alice",
        messages: List<Pair<String, Long>> = listOf("Running late" to 1_000L),
        shortcutId: String? = "T1:D123",
        actions: List<NotificationCompat.Action> = listOf(replyAction(context), markReadAction(context)),
        workspace: String? = null,
    ): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
        messages.forEach { (text, ts) -> style.addMessage(text, ts, person(sender)) }
        return builder(context, shortcutId, actions).setStyle(style).setSubText(workspace).build()
    }

    /** Channel mention: group MessagingStyle with conversation title. */
    fun channel(
        context: Context,
        channel: String = "#general",
        messages: List<Triple<String, String, Long>> = listOf(Triple("Bob", "@me deploy is done", 2_000L)),
        shortcutId: String? = "T1:C456",
    ): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .setConversationTitle(channel)
            .setGroupConversation(true)
        messages.forEach { (who, text, ts) -> style.addMessage(text, ts, person(who)) }
        return builder(context, shortcutId, listOf(replyAction(context), markReadAction(context))).setStyle(style).build()
    }

    /** Older/plain shape: title + text only. */
    fun plain(context: Context, title: String, text: String, `when`: Long = 3_000L): Notification =
        builder(context, null, listOf(replyAction(context, semantic = false), markReadAction(context, semantic = false)))
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(`when`)
            .build()

    fun groupSummary(context: Context): Notification =
        NotificationCompat.Builder(context, "slack")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("2 new messages")
            .setGroup("T1")
            .setGroupSummary(true)
            .build()

    fun huddle(context: Context): Notification =
        NotificationCompat.Builder(context, "slack")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("Huddle in #general")
            .setContentText("You're in a huddle")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .build()

    fun source(
        notification: Notification,
        packageName: String = SlackParser.SLACK_PACKAGE,
        tag: String? = "tag",
        id: Int = 42,
        postTime: Long = 10_000L,
    ) = SourceNotification(packageName, "0|$packageName|$id|$tag|10001", tag, id, postTime, notification)

    private fun builder(context: Context, shortcutId: String?, actions: List<NotificationCompat.Action>) =
        NotificationCompat.Builder(context, "slack")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent("com.Slack.test.OPEN"), PendingIntent.FLAG_IMMUTABLE))
            .apply {
                shortcutId?.let { setShortcutId(it) }
                actions.forEach { addAction(it) }
            }
}
