package com.joanpa.estadowa

import android.content.Context

/** Cola de estados de texto: cada uno se programa al menos N minutos después del anterior. */
object TextQueue {
    /** Límite de caracteres de un estado de texto de WhatsApp (aprox.). */
    private const val MAX_CHARS = 700
    private const val RECENT_KEEP = 50

    @Synchronized
    fun enqueue(ctx: Context, rawText: String, sourceTitle: String) {
        val text = if (rawText.length > MAX_CHARS) rawText.take(MAX_CHARS - 1).trimEnd() + "…" else rawText

        // Telegram a veces vuelve a publicar la misma notificación (al editar o agrupar): no repetir.
        val key = text.hashCode().toString()
        val recent = AppPrefs.recentHashes(ctx)
        if (key in recent) return
        AppPrefs.setRecentHashes(ctx, (recent + key).takeLast(RECENT_KEEP))

        val now = System.currentTimeMillis()
        val gap = AppPrefs.intervalMin(ctx) * 60_000L
        val time = maxOf(now + 5_000, AppPrefs.lastSlot(ctx) + gap)
        AppPrefs.setLastSlot(ctx, time)

        val job = StatusJob(
            id = now,
            timeMillis = time,
            filePath = "",
            fileName = "Telegram · $sourceTitle",
            mimeType = StatusJob.TEXT_MIME,
            caption = text,
            pkg = AppPrefs.pkg(ctx),
        )
        JobStore.save(ctx, job)
        Scheduler.schedule(ctx, job)
    }

    /**
     * Al cambiar el intervalo, reprograma las noticias que siguen en cola con la nueva pausa,
     * contando desde la última que ya se publicó.
     */
    @Synchronized
    fun respace(ctx: Context) {
        val now = System.currentTimeMillis()
        val gap = AppPrefs.intervalMin(ctx) * 60_000L
        val texts = JobStore.all(ctx).filter { it.isText }
        val lastPublished = texts.filter { it.state == StatusJob.DONE || it.state == StatusJob.RUNNING }
            .maxOfOrNull { it.timeMillis } ?: 0L
        var t = maxOf(now + 5_000, lastPublished + gap)
        val pending = texts.filter { it.state == StatusJob.PENDING }.sortedBy { it.timeMillis }
        pending.forEach { job ->
            val moved = job.copy(timeMillis = t)
            JobStore.save(ctx, moved)
            Scheduler.schedule(ctx, moved)
            t += gap
        }
        AppPrefs.setLastSlot(ctx, if (pending.isEmpty()) lastPublished else t - gap)
    }
}
