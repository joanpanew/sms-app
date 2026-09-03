package com.joanpa.smsaviso.prefs

import android.content.Context

/**
 * Ajustes persistentes. Los valores por defecto son deliberadamente
 * conservadores: ~3 SMS/minuto, 1 cada 15-25 s, pausas de lote y ventana horaria.
 */
class SettingsStore(context: Context) {

    private val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var messageTemplate: String
        get() = p.getString("msg", "") ?: ""
        set(v) = p.edit().putString("msg", v).apply()

    var minDelaySec: Int
        get() = p.getInt("minDelay", 15)
        set(v) = p.edit().putInt("minDelay", v.coerceIn(1, 3600)).apply()

    var jitterSec: Int
        get() = p.getInt("jitter", 10)
        set(v) = p.edit().putInt("jitter", v.coerceIn(0, 3600)).apply()

    var maxPerMinute: Int
        get() = p.getInt("maxMin", 3)
        set(v) = p.edit().putInt("maxMin", v.coerceIn(0, 60)).apply()

    var maxPerHour: Int
        get() = p.getInt("maxHour", 80)
        set(v) = p.edit().putInt("maxHour", v.coerceIn(0, 5000)).apply()

    var maxPerDay: Int
        get() = p.getInt("maxDay", 400)
        set(v) = p.edit().putInt("maxDay", v.coerceIn(0, 50000)).apply()

    var batchSize: Int
        get() = p.getInt("batchSize", 30)
        set(v) = p.edit().putInt("batchSize", v.coerceIn(0, 10000)).apply()

    var batchPauseMin: Int
        get() = p.getInt("batchPause", 8)
        set(v) = p.edit().putInt("batchPause", v.coerceIn(0, 1440)).apply()

    var windowStartHour: Int
        get() = p.getInt("winStart", 8)
        set(v) = p.edit().putInt("winStart", v.coerceIn(0, 23)).apply()

    var windowEndHour: Int
        get() = p.getInt("winEnd", 21)
        set(v) = p.edit().putInt("winEnd", v.coerceIn(0, 23)).apply()

    var simSubId: Int
        get() = p.getInt("sim", -1)
        set(v) = p.edit().putInt("sim", v).apply()

    var retryOnSystemLimit: Boolean
        get() = p.getBoolean("retry", true)
        set(v) = p.edit().putBoolean("retry", v).apply()

    var consentAccepted: Boolean
        get() = p.getBoolean("consent", false)
        set(v) = p.edit().putBoolean("consent", v).apply()
}
