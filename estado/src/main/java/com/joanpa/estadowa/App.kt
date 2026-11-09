package com.joanpa.estadowa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Resultado de publicación", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Publicar a la hora", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Abre la pantalla para publicar el estado a la hora programada"
                setSound(null, null)
            }
        )
    }

    companion object {
        const val CH_INFO = "info"
        const val CH_ALARM = "alarm"

        fun notify(ctx: Context, id: Int, title: String, text: String) {
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, CH_INFO)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(id, n)
            } catch (_: SecurityException) {
                // sin permiso de notificaciones: no pasa nada, el estado queda en la lista
            }
        }
    }
}
