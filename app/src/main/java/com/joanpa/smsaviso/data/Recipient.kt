package com.joanpa.smsaviso.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipient")
data class Recipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_SKIPPED_OPTOUT = "SKIPPED_OPTOUT"
    }
}
