package com.joanpa.smsaviso.util

/**
 * Validación/normalización de celulares de Colombia.
 *
 * Un celular colombiano es: **10 dígitos que empiezan por 3** (ej. 3001234567).
 * La app siempre lo guarda y lo envía como **`+57` + esos 10 dígitos** → `+573XXXXXXXXX`.
 *
 * Acepta cualquier forma de entrada y la lleva a ese formato:
 *   3001234567          -> +573001234567
 *   300 123 4567        -> +573001234567
 *   +57 300 1234567     -> +573001234567
 *   +573001234567       -> +573001234567
 *   0057 300 1234567    -> +573001234567
 *   03001234567         -> +573001234567
 *
 * Rechaza (devuelve null): fijos nuevos de 10 dígitos que empiezan por 60x
 * (ej. 6012345678), números incompletos, y números de otros países.
 */
object ColombiaPhone {

    /** Devuelve `+573XXXXXXXXX` si es un celular colombiano válido; si no, null. */
    fun normalizeMobile(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var d = raw.filter { it.isDigit() }

        if (d.startsWith("0057")) d = d.drop(4)
        if (d.length == 12 && d.startsWith("57")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("0")) d = d.drop(1)

        // A estas alturas d debe ser exactamente el número nacional de 10 dígitos.
        return if (d.length == 10 && d.startsWith("3")) "+57$d" else null
    }

    fun isMobile(raw: String?): Boolean = normalizeMobile(raw) != null
}
