package com.joanpa.smsaviso

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Envío de SMS",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso del envío controlado de SMS"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "sms_sender"
    }
}
