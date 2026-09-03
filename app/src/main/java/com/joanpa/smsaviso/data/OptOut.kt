package com.joanpa.smsaviso.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Números que pidieron baja (respondieron STOP / BAJA / CANCELAR...).
 * [phoneKey] son los últimos 9 dígitos del número, para comparar sin depender
 * del prefijo internacional.
 */
@Entity(tableName = "opt_out")
data class OptOut(
    @PrimaryKey val phoneKey: String,
    val originalAddress: String,
    val timestamp: Long,
    val reason: String
)
