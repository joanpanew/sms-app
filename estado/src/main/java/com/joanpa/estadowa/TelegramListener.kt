package com.joanpa.estadowa

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lee las notificaciones de Telegram. Cuando llega un mensaje del canal configurado
 * (por defecto "joanpa noticias"), lo pone en la cola para publicarlo como estado de texto
 * en WhatsApp, respetando el intervalo mínimo entre publicaciones.
 * El resto de notificaciones se ignoran y no se guardan.
 */
class TelegramListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in TELEGRAM_PKGS) return
        if (!AppPrefs.forwardEnabled(this)) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val source = AppPrefs.source(this).trim()
        if (source.isEmpty()) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return
        if (!title.contains(source, ignoreCase = true)) return

        val text = extractText(n)?.trim().orEmpty()
        if (text.isEmpty() || isTelegramSummary(text)) return

        TextQueue.enqueue(this, text, title)
    }

    /** Telegram usa MessagingStyle: tomamos el último mensaje; si no, el texto largo o corto. */
    private fun extractText(n: Notification): String? {
        val extras = n.extras
        @Suppress("DEPRECATION")
        val msgs: Array<Parcelable>? = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!msgs.isNullOrEmpty()) {
            val last = msgs.last() as? Bundle
            last?.getCharSequence("text")?.toString()?.let { if (it.isNotBlank()) return it }
        }
        return extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    /** "3 mensajes nuevos", "5 new messages"… no son noticias. */
    private fun isTelegramSummary(text: String) =
        Regex("^\\d+\\s+(mensajes? nuevos?|new messages?|nuevos mensajes)", RegexOption.IGNORE_CASE).containsMatchIn(text)

    companion object {
        val TELEGRAM_PKGS = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            "org.thunderdog.challegram",
        )

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, TelegramListener::class.java)
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
