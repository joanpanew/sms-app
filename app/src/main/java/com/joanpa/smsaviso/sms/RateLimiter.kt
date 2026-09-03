package com.joanpa.smsaviso.sms

import com.joanpa.smsaviso.data.SendLogDao
import com.joanpa.smsaviso.prefs.SettingsStore
import java.util.Calendar
import kotlin.random.Random

/**
 * Lógica pura de control de ritmo. No hace pausas; solo calcula cuánto esperar.
 */
class RateLimiter(
    private val s: SettingsStore,
    private val logDao: SendLogDao
) {

    /** Espera base entre un SMS y el siguiente, con variación aleatoria. */
    fun baseDelayMillis(): Long {
        val jitter = if (s.jitterSec > 0) Random.nextInt(s.jitterSec + 1) else 0
        return (s.minDelaySec + jitter) * 1000L
    }

    fun withinWindow(now: Calendar = Calendar.getInstance()): Boolean {
        val h = now.get(Calendar.HOUR_OF_DAY)
        val start = s.windowStartHour
        val end = s.windowEndHour
        if (start == end) return true // ventana de 24 h
        return if (start < end) h in start until end else (h >= start || h < end)
    }

    fun millisUntilWindowOpens(now: Calendar = Calendar.getInstance()): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, s.windowStartHour)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
    }

    sealed class Quota {
        object Ok : Quota()
        data class Wait(val millis: Long, val reason: String) : Quota()
        object DayLimit : Quota()
    }

    /** Comprueba los topes por minuto / hora / día contando intentos recientes. */
    fun checkQuota(): Quota {
        val now = System.currentTimeMillis()
        if (s.maxPerMinute > 0 && logDao.countAttemptsSince(now - 60_000L) >= s.maxPerMinute) {
            return Quota.Wait(5_000L, "tope por minuto")
        }
        if (s.maxPerHour > 0 && logDao.countAttemptsSince(now - 3_600_000L) >= s.maxPerHour) {
            return Quota.Wait(60_000L, "tope por hora")
        }
        if (s.maxPerDay > 0 && logDao.countAttemptsSince(now - 86_400_000L) >= s.maxPerDay) {
            return Quota.DayLimit
        }
        return Quota.Ok
    }
}
