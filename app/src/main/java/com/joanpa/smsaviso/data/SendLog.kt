package com.joanpa.smsaviso.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro histórico. Cada intento genera una fila con status = "ATTEMPT",
 * que luego se actualiza a SENT / DELIVERED / FAILED.
 * Las filas ATTEMPT son la base para contar el ritmo (por minuto/hora/día).
 */
@Entity(tableName = "send_log")
data class SendLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phone: String,
    val name: String,
    val timestamp: Long,
    val status: String,
    val simSubId: Int,
    val error: String? = null
) {
    companion object {
        const val ATTEMPT = "ATTEMPT"
        const val SENT = "SENT"
        const val DELIVERED = "DELIVERED"
        const val FAILED = "FAILED"
    }
}
