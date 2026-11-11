package io.github.autobridge.source

import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Debug builds only: logs a Slack notification's extras and actions so real shapes can be
 * captured as test fixtures (`adb logcat -s AutoBridgeDump`). Contains message content.
 */
object NotificationDumper {
    private const val TAG = "AutoBridgeDump"

    fun dump(sbn: StatusBarNotification) {
        try {
            logDump(sbn)
        } catch (e: RuntimeException) {
            // Bundles can fail to unparcel another app's values; a debug aid must not break the relay.
            Log.w(TAG, "Couldn't dump notification", e)
        }
    }

    private fun logDump(sbn: StatusBarNotification) {
        val n = sbn.notification
        val out = StringBuilder()
        out.appendLine("key=${sbn.key} tag=${sbn.tag} id=${sbn.id} postTime=${sbn.postTime}")
        out.appendLine("flags=0x${Integer.toHexString(n.flags)} category=${n.category} shortcutId=${n.shortcutId} group=${n.group}")
        out.appendLine("extras:")
        appendBundle(out, n.extras, "  ")
        for (i in 0 until NotificationCompat.getActionCount(n)) {
            val a = NotificationCompat.getAction(n, i) ?: continue
            out.appendLine(
                "action[$i] title=${a.title} semantic=${a.semanticAction} showsUi=${a.showsUserInterface} " +
                    "inputs=${a.remoteInputs?.joinToString { "${it.resultKey}(freeForm=${it.allowFreeFormInput})" }}",
            )
        }
        out.lineSequence().forEach { Log.d(TAG, it) }
    }

    @Suppress("DEPRECATION")
    private fun appendBundle(out: StringBuilder, bundle: Bundle, indent: String) {
        for (key in bundle.keySet().sorted()) {
            when (val value = bundle.get(key)) {
                is Bundle -> { out.appendLine("$indent$key:"); appendBundle(out, value, "$indent  ") }
                is Array<*> -> {
                    out.appendLine("$indent$key: [${value.size}]")
                    value.forEachIndexed { i, item ->
                        if (item is Bundle) { out.appendLine("$indent  [$i]:"); appendBundle(out, item, "$indent    ") }
                        else out.appendLine("$indent  [$i]: $item")
                    }
                }
                else -> out.appendLine("$indent$key: ${value?.javaClass?.simpleName}=$value")
            }
        }
    }
}
