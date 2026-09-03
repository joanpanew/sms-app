package com.joanpa.smsaviso.sms

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.joanpa.smsaviso.App
import com.joanpa.smsaviso.R
import com.joanpa.smsaviso.data.AppDatabase
import com.joanpa.smsaviso.data.Recipient
import com.joanpa.smsaviso.data.RecipientDao
import com.joanpa.smsaviso.data.SendLog
import com.joanpa.smsaviso.prefs.SettingsStore
import com.joanpa.smsaviso.ui.MainActivity
import com.joanpa.smsaviso.util.isSystemLimit
import com.joanpa.smsaviso.util.phoneKey
import com.joanpa.smsaviso.util.smsResultText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SmsSenderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile private var paused = false
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLoop()
            ACTION_PAUSE -> {
                paused = true
                SenderBus.update { it.copy(paused = true, waitingReason = "en pausa") }
                pushNotification("En pausa")
            }
            ACTION_RESUME -> {
                paused = false
                SenderBus.update { it.copy(paused = false, waitingReason = null) }
                pushNotification("Reanudando…")
            }
            ACTION_STOP -> {
                stopping = true
                loopJob?.cancel()
            }
        }
        return START_STICKY
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        stopping = false
        paused = false
        startForeground(NOTIF_ID, buildNotification("Preparando envío…"))
        loopJob = scope.launch {
            try {
                runLoop()
            } catch (e: CancellationException) {
                // detenido por el usuario
            } catch (e: Exception) {
                SenderBus.update { it.copy(running = false, lastEvent = "Error: ${e.message}") }
                pushNotification("Error: ${e.message}")
            } finally {
                SenderBus.update { it.copy(running = false, nextInSec = 0) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun runLoop() {
        val db = AppDatabase.get(this)
        val rDao = db.recipientDao()
        val lDao = db.sendLogDao()
        val oDao = db.optOutDao()
        val settings = SettingsStore(this)
        val limiter = RateLimiter(settings, lDao)

        rDao.requeueStuck()

        SenderBus.update {
            Progress(
                running = true,
                total = rDao.count(),
                done = rDao.countDone(),
                failed = rDao.countFailed(),
                skipped = rDao.countSkipped(),
                pending = rDao.countPending(),
                lastEvent = "Iniciado"
            )
        }

        var sentThisRun = 0

        while (stillActive()) {
            waitWhilePaused()
            if (!stillActive()) break

            val r = rDao.nextPending() ?: break

            // 1) exclusión (opt-out)
            if (oDao.isOptedOut(r.phone.phoneKey())) {
                rDao.update(
                    r.id, Recipient.STATUS_SKIPPED_OPTOUT,
                    "En lista de exclusión (pidió baja)", r.attempts, System.currentTimeMillis()
                )
                publish(rDao, "Omitido (baja): ${r.name}")
                continue
            }

            // 2) ventana horaria
            while (stillActive() && !limiter.withinWindow()) {
                val wait = limiter.millisUntilWindowOpens()
                SenderBus.update {
                    it.copy(waitingReason = "fuera de la ventana horaria", nextInSec = (wait / 1000).toInt())
                }
                pushNotification("Fuera de la ventana horaria. Reanuda a las ${settings.windowStartHour}:00")
                sleepInterruptible(minOf(wait, 60_000L))
            }
            if (!stillActive()) break
            waitWhilePaused()

            // 3) topes por minuto / hora / día
            quota@ while (stillActive()) {
                when (val q = limiter.checkQuota()) {
                    is RateLimiter.Quota.Ok -> break@quota
                    is RateLimiter.Quota.Wait -> {
                        SenderBus.update { it.copy(waitingReason = "esperando: ${q.reason}") }
                        pushNotification("Esperando (${q.reason})…")
                        sleepInterruptible(q.millis)
                    }
                    is RateLimiter.Quota.DayLimit -> {
                        paused = true
                        SenderBus.update { it.copy(paused = true, waitingReason = "tope diario alcanzado") }
                        pushNotification("Tope diario alcanzado. Pausado hasta que reanudes.")
                        waitWhilePaused()
                    }
                }
            }
            if (!stillActive()) break

            // 4) pausa de lote
            if (sentThisRun > 0 && settings.batchSize > 0 && sentThisRun % settings.batchSize == 0) {
                val pause = settings.batchPauseMin * 60_000L
                if (pause > 0) {
                    SenderBus.update {
                        it.copy(waitingReason = "pausa de lote", nextInSec = (pause / 1000).toInt())
                    }
                    pushNotification("Pausa de lote (${settings.batchPauseMin} min)…")
                    sleepInterruptible(pause)
                }
            }
            if (!stillActive()) break
            waitWhilePaused()

            // 5) ENVIAR
            rDao.update(r.id, Recipient.STATUS_SENDING, null, r.attempts, System.currentTimeMillis())
            val logId = lDao.insert(
                SendLog(
                    phone = r.phone, name = r.name,
                    timestamp = System.currentTimeMillis(),
                    status = SendLog.ATTEMPT, simSubId = settings.simSubId
                )
            )
            val text = renderMessage(settings.messageTemplate, r.name)
            val result = trySend(r, text, settings.simSubId, logId)
            sentThisRun++

            when (result) {
                SendResult.OK -> publish(rDao, "Enviado: ${r.name}")
                SendResult.FAILED -> publish(rDao, "Falló: ${r.name}")
                SendResult.SYSTEM_LIMIT -> {
                    if (settings.retryOnSystemLimit && r.attempts + 1 < MAX_ATTEMPTS) {
                        rDao.update(
                            r.id, Recipient.STATUS_PENDING,
                            "Reintento tras límite del sistema", r.attempts + 1, System.currentTimeMillis()
                        )
                        publish(rDao, "Límite del sistema; reintento programado: ${r.name}")
                    } else {
                        rDao.update(
                            r.id, Recipient.STATUS_FAILED,
                            "Límite del sistema Android (sin más reintentos)", r.attempts, System.currentTimeMillis()
                        )
                        publish(rDao, "Falló (límite del sistema): ${r.name}")
                    }
                    SenderBus.update { it.copy(waitingReason = "enfriando tras límite del sistema") }
                    pushNotification("Android limitó el envío. Enfriando 2 min…")
                    sleepInterruptible(120_000L)
                }
            }
            if (!stillActive()) break

            // 6) espera base con cuenta atrás
            var remaining = limiter.baseDelayMillis()
            while (remaining > 0 && stillActive()) {
                SenderBus.update { it.copy(nextInSec = (remaining / 1000).toInt(), waitingReason = null) }
                delay(1000)
                remaining -= 1000
                waitWhilePaused()
            }
        }

        val stoppedByUser = stopping || !currentCoroutineContext().isActive
        SenderBus.update {
            it.copy(
                running = false,
                finished = !stoppedByUser,
                nextInSec = 0,
                waitingReason = if (stoppedByUser) "detenido" else null,
                lastEvent = if (stoppedByUser) "Detenido por el usuario" else "Envío finalizado"
            )
        }
        pushNotification(if (stoppedByUser) "Envío detenido" else "Envío finalizado")
    }

    // ---------------------------------------------------------------------

    private enum class SendResult { OK, FAILED, SYSTEM_LIMIT }

    private suspend fun trySend(r: Recipient, text: String, subId: Int, logId: Long): SendResult =
        suspendCancellableCoroutine { cont ->
            val db = AppDatabase.get(this)
            val action = "$ACTION_SENT.${r.id}.${System.nanoTime()}"

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val rc = resultCode
                    try { unregisterReceiver(this) } catch (_: Exception) {}
                    val now = System.currentTimeMillis()
                    if (rc == Activity.RESULT_OK) {
                        db.recipientDao().update(r.id, Recipient.STATUS_SENT, null, r.attempts, now)
                        db.sendLogDao().update(logId, SendLog.SENT, null)
                        if (cont.isActive) cont.resume(SendResult.OK)
                    } else {
                        val reason = smsResultText(rc)
                        val systemLimit = isSystemLimit(rc)
                        db.recipientDao().update(
                            r.id,
                            if (systemLimit) Recipient.STATUS_SENDING else Recipient.STATUS_FAILED,
                            reason, r.attempts, now
                        )
                        db.sendLogDao().update(logId, SendLog.FAILED, reason)
                        if (cont.isActive) {
                            cont.resume(if (systemLimit) SendResult.SYSTEM_LIMIT else SendResult.FAILED)
                        }
                    }
                }
            }

            try {
                ContextCompat.registerReceiver(
                    this, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
                )
                val sms = SimHelper.smsManager(this, subId)
                val parts = sms.divideMessage(text)
                val sentIntents = ArrayList<PendingIntent>()
                for (idx in parts.indices) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            this, idx,
                            Intent(action).setPackage(packageName),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                if (parts.size <= 1) {
                    sms.sendTextMessage(r.phone, null, text, sentIntents.firstOrNull(), null)
                } else {
                    sms.sendMultipartTextMessage(r.phone, null, parts, sentIntents, null)
                }
            } catch (e: Exception) {
                try { unregisterReceiver(receiver) } catch (_: Exception) {}
                db.recipientDao().update(
                    r.id, Recipient.STATUS_FAILED,
                    e.message ?: "Excepción al enviar", r.attempts, System.currentTimeMillis()
                )
                db.sendLogDao().update(logId, SendLog.FAILED, e.message)
                if (cont.isActive) cont.resume(SendResult.FAILED)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try { unregisterReceiver(receiver) } catch (_: Exception) {}
            }

            // Si la confirmación no llega en 45 s, damos el SMS por enviado.
            scope.launch {
                delay(45_000)
                if (cont.isActive) {
                    try { unregisterReceiver(receiver) } catch (_: Exception) {}
                    db.sendLogDao().update(logId, SendLog.SENT, "Sin confirmación (timeout)")
                    db.recipientDao().update(
                        r.id, Recipient.STATUS_SENT,
                        "Enviado sin confirmación", r.attempts, System.currentTimeMillis()
                    )
                    cont.resume(SendResult.OK)
                }
            }
        }

    // ---------------------------------------------------------------------

    private suspend fun stillActive(): Boolean = currentCoroutineContext().isActive && !stopping

    private suspend fun waitWhilePaused() {
        while (paused && !stopping && currentCoroutineContext().isActive) {
            delay(500)
        }
    }

    private suspend fun sleepInterruptible(millis: Long) {
        var left = millis
        while (left > 0 && !stopping && currentCoroutineContext().isActive) {
            delay(minOf(left, 1000L))
            left -= 1000L
            while (paused && !stopping && currentCoroutineContext().isActive) delay(500)
        }
    }

    private fun renderMessage(template: String, fullName: String): String {
        val clean = fullName.trim()
        val first = clean.substringBefore(" ").ifBlank { clean }
        return template
            .replace("{nombre}", first)
            .replace("{nombre_completo}", clean)
    }

    private fun publish(rDao: RecipientDao, lastEvent: String? = null) {
        SenderBus.update {
            it.copy(
                done = rDao.countDone(),
                failed = rDao.countFailed(),
                skipped = rDao.countSkipped(),
                pending = rDao.countPending(),
                lastEvent = lastEvent ?: it.lastEvent
            )
        }
        val p = SenderBus.progress.value
        pushNotification("Enviados ${p.done} · Fallidos ${p.failed} · Pendientes ${p.pending}")
    }

    // ---------------------------------------------------------------------

    private fun servicePI(action: String, code: Int): PendingIntent {
        val i = Intent(this, SmsSenderService::class.java).setAction(action)
        return PendingIntent.getService(
            this, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)

        if (paused) {
            builder.addAction(0, "Reanudar", servicePI(ACTION_RESUME, 2))
        } else {
            builder.addAction(0, "Pausar", servicePI(ACTION_PAUSE, 1))
        }
        builder.addAction(0, "Detener", servicePI(ACTION_STOP, 3))
        return builder.build()
    }

    private fun pushNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopping = true
        scope.cancel()
    }

    companion object {
        const val ACTION_START = "com.joanpa.smsaviso.action.START"
        const val ACTION_PAUSE = "com.joanpa.smsaviso.action.PAUSE"
        const val ACTION_RESUME = "com.joanpa.smsaviso.action.RESUME"
        const val ACTION_STOP = "com.joanpa.smsaviso.action.STOP"
        private const val ACTION_SENT = "com.joanpa.smsaviso.action.SMS_SENT"
        private const val NOTIF_ID = 1001
        private const val MAX_ATTEMPTS = 3
    }
}
