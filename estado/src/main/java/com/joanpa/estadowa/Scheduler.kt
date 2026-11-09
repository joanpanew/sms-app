package com.joanpa.estadowa

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Programa alarmas exactas (tipo despertador) que se disparan aunque el teléfono esté en reposo. */
object Scheduler {
    const val EXTRA_JOB_ID = "job_id"

    /** Si el teléfono estuvo apagado y se pasó la hora por menos de esto, se publica igual al encender. */
    private const val MISSED_GRACE_MS = 30 * 60 * 1000L

    fun schedule(ctx: Context, job: StatusJob) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val op = alarmIntent(ctx, job.id)
        val show = PendingIntent.getActivity(
            ctx, job.id.toInt(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            // setAlarmClock: máxima prioridad, ignora el modo Doze, igual que un despertador.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(job.timeMillis, show), op)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.timeMillis, op)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, id))
    }

    /** Tras reiniciar el teléfono o actualizar la app, las alarmas se pierden: se vuelven a crear. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        var catchUp = now + 10_000
        JobStore.all(ctx).forEach { job ->
            when {
                job.state == StatusJob.RUNNING && job.timeMillis < now - 5 * 60_000 ->
                    JobStore.update(ctx, job.id, StatusJob.FAILED, "Se interrumpió (¿se reinició el teléfono?)")
                job.state != StatusJob.PENDING -> Unit
                job.timeMillis >= now -> schedule(ctx, job)
                now - job.timeMillis <= MISSED_GRACE_MS -> {
                    val moved = job.copy(timeMillis = catchUp)
                    catchUp += 2 * 60_000
                    JobStore.save(ctx, moved)
                    schedule(ctx, moved)
                }
                else -> JobStore.update(ctx, job.id, StatusJob.FAILED, "El teléfono estaba apagado a esa hora")
            }
        }
    }

    private fun alarmIntent(ctx: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id.toInt(),
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_JOB_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
