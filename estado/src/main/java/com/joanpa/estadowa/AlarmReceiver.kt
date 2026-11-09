package com.joanpa.estadowa

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Se dispara a la hora programada. Abre [PublishActivity], que enciende la pantalla
 * y lanza WhatsApp. Como respaldo publica una notificación de pantalla completa,
 * que Android usa para abrir la actividad aunque el teléfono esté bloqueado.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_JOB_ID, -1)
        if (id < 0) return
        val job = JobStore.get(ctx, id) ?: return
        if (job.state != StatusJob.PENDING) return

        // Si otra publicación está en curso (p. ej. varios estados a la misma hora), esperar 2 min.
        if (!AppPrefs.tryLock(ctx, id)) {
            val later = job.copy(timeMillis = System.currentTimeMillis() + 2 * 60_000)
            JobStore.save(ctx, later)
            Scheduler.schedule(ctx, later)
            return
        }

        val activity = PublishActivity.intent(ctx, id)
        val fullScreen = PendingIntent.getActivity(
            ctx, id.toInt(), activity,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, App.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Publicando tu estado…")
            .setContentText(job.fileName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .setTimeoutAfter(3 * 60_000)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ALARM + id.toInt(), n)
        } catch (_: SecurityException) { }

        // Con el servicio de accesibilidad activo, Android permite abrir la actividad desde segundo plano.
        try {
            ctx.startActivity(activity)
        } catch (_: Exception) { }
    }

    companion object {
        const val NOTIF_ALARM = 5000
    }
}
