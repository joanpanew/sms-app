package com.joanpa.smsaviso.util

import android.telephony.SmsManager

/** Deja solo dígitos y toma los últimos 9 como clave de comparación. */
fun String.phoneKey(): String {
    val digits = filter { it.isDigit() }
    return if (digits.length <= 9) digits else digits.takeLast(9)
}

/** Normaliza para enviar: deja + inicial y dígitos. */
fun String.cleanPhone(): String {
    val trimmed = trim()
    val plus = if (trimmed.startsWith("+")) "+" else ""
    return plus + trimmed.filter { it.isDigit() }
}

fun smsResultText(resultCode: Int): String = when (resultCode) {
    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Fallo genérico de la operadora"
    SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio del teléfono apagada (modo avión)"
    SmsManager.RESULT_ERROR_NULL_PDU -> "Mensaje vacío o inválido"
    SmsManager.RESULT_ERROR_NO_SERVICE -> "Sin servicio de red"
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Límite del sistema Android alcanzado"
    else -> "Error de envío (código $resultCode)"
}

/** true si el código indica que Android cortó por exceso de mensajes. */
fun isSystemLimit(resultCode: Int): Boolean =
    resultCode == SmsManager.RESULT_ERROR_LIMIT_EXCEEDED
