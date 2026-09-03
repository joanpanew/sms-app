package com.joanpa.smsaviso.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.joanpa.smsaviso.data.AppDatabase
import com.joanpa.smsaviso.data.OptOut
import com.joanpa.smsaviso.util.phoneKey

/**
 * Detecta respuestas de baja y agrega el número a la lista de exclusión.
 * No lee ni reenvía el contenido a ningún sitio: solo compara con palabras clave.
 */
class IncomingSmsReceiver : BroadcastReceiver() {

    private val keywords = listOf(
        "STOP", "BAJA", "CANCELAR", "CANCELA", "NO", "NO ENVIAR",
        "BASTA", "ELIMINAR", "REMOVER", "UNSUBSCRIBE", "SALIR"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        try {
            val dao = AppDatabase.get(context).optOutDao()
            for ((sender, parts) in bySender) {
                if (sender.isBlank()) continue
                val body = parts.joinToString(separator = "") { it.messageBody ?: "" }
                    .trim().uppercase()
                val matched = keywords.any { kw ->
                    body == kw || body.startsWith("$kw ") || body.startsWith("$kw.") ||
                        body.startsWith("$kw,") || body == "$kw."
                }
                if (matched) {
                    dao.insert(
                        OptOut(
                            phoneKey = sender.phoneKey(),
                            originalAddress = sender,
                            timestamp = System.currentTimeMillis(),
                            reason = "Respondió: ${body.take(40)}"
                        )
                    )
                }
            }
        } finally {
            pending.finish()
        }
    }
}
