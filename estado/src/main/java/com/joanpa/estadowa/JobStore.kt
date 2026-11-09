package com.joanpa.estadowa

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un estado programado. */
data class StatusJob(
    val id: Long,
    val timeMillis: Long,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val caption: String,
    val pkg: String,
    val state: String = PENDING,
    val detail: String = "",
) {
    companion object {
        const val PENDING = "PENDIENTE"
        const val RUNNING = "PUBLICANDO"
        const val DONE = "PUBLICADO"
        const val FAILED = "FALLÓ"
        const val TEXT_MIME = "text/plain"
    }

    val isText get() = mimeType == TEXT_MIME
}

/** Guarda los estados programados en SharedPreferences (JSON). */
object JobStore {
    private const val PREFS = "jobs"
    private const val KEY = "list"

    @Synchronized
    fun all(ctx: Context): List<StatusJob> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StatusJob(
                id = o.getLong("id"),
                timeMillis = o.getLong("time"),
                filePath = o.getString("path"),
                fileName = o.optString("name"),
                mimeType = o.optString("mime", "video/*"),
                caption = o.optString("caption"),
                pkg = o.optString("pkg", "com.whatsapp"),
                state = o.optString("state", StatusJob.PENDING),
                detail = o.optString("detail"),
            )
        }.sortedBy { it.timeMillis }
    }

    fun get(ctx: Context, id: Long): StatusJob? = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun save(ctx: Context, job: StatusJob) {
        // Limpieza: borra del historial lo terminado hace más de 7 días.
        val old = System.currentTimeMillis() - 7 * 24 * 3600_000L
        val (expired, keep) = all(ctx).filter { it.id != job.id }
            .partition { (it.state == StatusJob.DONE || it.state == StatusJob.FAILED) && it.timeMillis < old }
        expired.forEach { if (it.filePath.isNotEmpty()) File(it.filePath).delete() }
        write(ctx, keep + job)
    }

    @Synchronized
    fun update(ctx: Context, id: Long, state: String, detail: String) {
        val job = get(ctx, id) ?: return
        save(ctx, job.copy(state = state, detail = detail))
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val job = get(ctx, id) ?: return
        if (job.filePath.isNotEmpty()) File(job.filePath).delete()
        write(ctx, all(ctx).filter { it.id != id })
    }

    private fun write(ctx: Context, list: List<StatusJob>) {
        val arr = JSONArray()
        list.forEach { j ->
            arr.put(JSONObject().apply {
                put("id", j.id); put("time", j.timeMillis); put("path", j.filePath)
                put("name", j.fileName); put("mime", j.mimeType); put("caption", j.caption)
                put("pkg", j.pkg); put("state", j.state); put("detail", j.detail)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

object AppPrefs {
    private const val PREFS = "settings"
    fun lockAfter(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean("lock", true)
    fun setLockAfter(ctx: Context, v: Boolean) = ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("lock", v).apply()
    fun pkg(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getString("pkg", "com.whatsapp")!!
    fun setPkg(ctx: Context, v: String) = ctx.getSharedPreferences(PREFS, 0).edit().putString("pkg", v).apply()

    // --- Reenvío desde Telegram ---
    fun forwardEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean("fwd", false)
    fun setForwardEnabled(ctx: Context, v: Boolean) = ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("fwd", v).apply()
    fun source(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getString("source", "joanpa noticias")!!
    fun setSource(ctx: Context, v: String) = ctx.getSharedPreferences(PREFS, 0).edit().putString("source", v).apply()
    fun intervalMin(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getInt("interval", 10)
    fun setIntervalMin(ctx: Context, v: Int) = ctx.getSharedPreferences(PREFS, 0).edit().putInt("interval", v).apply()
    fun lastSlot(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getLong("lastSlot", 0L)
    fun setLastSlot(ctx: Context, v: Long) = ctx.getSharedPreferences(PREFS, 0).edit().putLong("lastSlot", v).apply()
    fun recentHashes(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, 0).getString("recent", "")!!.split(',').filter { it.isNotEmpty() }
    fun setRecentHashes(ctx: Context, v: List<String>) =
        ctx.getSharedPreferences(PREFS, 0).edit().putString("recent", v.joinToString(",")).apply()
}
