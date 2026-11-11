package io.github.autobridge.actions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import io.github.autobridge.model.ReplyTarget

/** Fills Slack's own RemoteInput result keys with the reply text and fires Slack's reply intent. */
object ReplyForwarder {
    /** @return false when there is no target or Slack's PendingIntent is stale. */
    fun forward(context: Context, target: ReplyTarget?, text: String): Boolean {
        if (target == null || target.remoteInputs.isEmpty()) return false
        val inputs = target.remoteInputs.toTypedArray()
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(inputs, fillIn, results)
        RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return try {
            target.intent.send(context, 0, fillIn)
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }
}
