package io.github.autobridge.relay

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.autobridge.R
import io.github.autobridge.actions.MarkReadReceiver
import io.github.autobridge.actions.ReplyReceiver
import io.github.autobridge.model.Conversation
import io.github.autobridge.model.ConversationKey
import io.github.autobridge.model.Sender
import io.github.autobridge.source.SlackParser

/** Builds and posts the relayed MessagingStyle notifications Android Auto reads. */
class AutoNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    @Volatile
    var config = NotifierConfig()

    fun ensureChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_RELAY, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_relay))
                    .setDescription(context.getString(R.string.channel_relay_desc))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_RELAY_QUIET, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_relay_quiet))
                    .setDescription(context.getString(R.string.channel_relay_quiet_desc))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ERRORS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_errors))
                    .build(),
            ),
        )
    }

    fun build(conversation: Conversation, alert: Boolean, config: NotifierConfig = this.config): Notification {
        val key = conversation.key
        val me = Person.Builder().setName(context.getString(R.string.me)).setKey(ME_KEY).build()
        val workspace = conversation.workspace?.takeIf { config.labelWorkspaces }
        val style = NotificationCompat.MessagingStyle(me)
            .setGroupConversation(conversation.isGroup)
            .setConversationTitle(
                when {
                    workspace != null -> "${conversation.title} · $workspace"
                    conversation.isGroup -> conversation.title
                    else -> null
                },
            )
        conversation.messages.forEach { m ->
            style.addMessage(NotificationCompat.MessagingStyle.Message(m.text, m.timestamp, m.sender?.toPerson()))
        }

        val replyInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.action_reply))
            .apply { if (config.quickReplies.isNotEmpty()) setChoices(config.quickReplies.toTypedArray()) }
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_reply),
            context.getString(R.string.action_reply),
            broadcast(ReplyReceiver::class.java, ReplyReceiver.ACTION_REPLY, key, mutable = true),
        )
            .addRemoteInput(replyInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val markReadAction = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_mark_read),
            context.getString(R.string.action_mark_read),
            broadcast(MarkReadReceiver::class.java, MarkReadReceiver.ACTION_MARK_READ, key),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        return NotificationCompat.Builder(context, if (config.quiet) CHANNEL_RELAY_QUIET else CHANNEL_RELAY)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(!alert)
            // Re-posts after connecting are new notifications, so only-alert-once alone would still announce them.
            .setSilent(!alert)
            .setWhen(conversation.messages.lastOrNull()?.timestamp ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(conversation.actions.content)
            .setDeleteIntent(broadcast(MarkReadReceiver::class.java, MarkReadReceiver.ACTION_DISMISSED, key))
            .setAutoCancel(true)
            .addAction(replyAction)
            // Hidden on the phone; Android Auto still finds it by its semantic action.
            .addInvisibleAction(markReadAction)
            .build()
    }

    /** @return false if Android wouldn't accept the notification. */
    fun post(conversation: Conversation, alert: Boolean, config: NotifierConfig = this.config): Boolean =
        notify(conversation.key.asString(), ID_CONVERSATION, build(conversation, alert, config))

    fun cancel(key: ConversationKey) = manager.cancel(key.asString(), ID_CONVERSATION)

    fun cancelAll(keys: Collection<ConversationKey>) = keys.forEach(::cancel)

    /** Cancels every relayed conversation still showing, including ones from before a process restart. */
    fun cancelAllConversations() {
        manager.activeNotifications
            .filter { it.id == ID_CONVERSATION }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /** Phone notification keeping the reply's text, so it can still be sent from Slack when parked. */
    fun postReplyFailed(key: ConversationKey, conversation: Conversation?, reply: String) {
        val title = conversation?.title ?: context.getString(R.string.slack)
        val open = conversation?.actions?.content ?: slackLaunchIntent()
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reply_failed_title))
            .setContentText(context.getString(R.string.reply_failed_text, title))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.reply_failed_details, title, reply)))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notify(key.asString(), ID_REPLY_FAILED, notification)
    }

    private fun notify(tag: String, id: Int, notification: Notification): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(tag, id, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Android refused the notification", e)
            false
        }
    }

    private fun slackLaunchIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(SlackParser.SLACK_PACKAGE)?.let {
            PendingIntentCompat.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT, false)
        }

    private fun broadcast(cls: Class<*>, action: String, key: ConversationKey, mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, cls)
            .setAction(action)
            // Unique data per conversation so PendingIntents don't collapse into one.
            .setData(Uri.Builder().scheme("autobridge").authority("conversation").appendPath(key.asString()).build())
            .putExtra(EXTRA_CONVERSATION_KEY, key.asString())
        return PendingIntentCompat.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, mutable)!!
    }

    private fun Sender.toPerson(): Person =
        Person.Builder().setName(name).setKey(key ?: name).setIcon(icon).build()

    companion object {
        const val CHANNEL_RELAY = "relay"
        const val CHANNEL_RELAY_QUIET = "relay_quiet"
        const val CHANNEL_ERRORS = "errors"
        const val ID_CONVERSATION = 1
        const val ID_REPLY_FAILED = 2
        const val KEY_TEXT_REPLY = "autobridge_reply"
        const val EXTRA_CONVERSATION_KEY = "io.github.autobridge.CONVERSATION_KEY"
        private const val ME_KEY = "autobridge:me"
        private const val TAG = "AutoNotifier"
    }
}
