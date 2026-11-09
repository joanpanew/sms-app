package com.joanpa.estadowa

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Se abre sola a la hora programada:
 * 1. enciende la pantalla y quita el bloqueo (si no tiene PIN/patrón),
 * 2. comparte el video con WhatsApp,
 * 3. arma el servicio de accesibilidad para que pulse "Mi estado" y "Enviar".
 */
class PublishActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var jobId = -1L
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {
            text = "Publicando tu estado en WhatsApp…\nNo toques la pantalla."
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        })
        start(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        start(intent)
    }

    private fun start(intent: Intent) {
        if (launched) return
        jobId = intent.getLongExtra(Scheduler.EXTRA_JOB_ID, -1)
        val job = JobStore.get(this, jobId)
        if (job == null || job.state != StatusJob.PENDING) {
            finish(); return
        }
        launched = true
        NotificationManagerCompat.from(this).cancel(AlarmReceiver.NOTIF_ALARM + jobId.toInt())
        JobStore.update(this, jobId, StatusJob.RUNNING, "")

        if (!StatusAccessibilityService.isEnabled(this)) {
            fail(job, "El servicio de accesibilidad está desactivado. Actívalo en la app.")
            return
        }
        if (!job.isText && !File(job.filePath).exists()) {
            fail(job, "No se encontró el archivo del video.")
            return
        }

        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchWhatsApp(job)
                override fun onDismissCancelled() = fail(job, BLOCKED_MSG)
                override fun onDismissError() = fail(job, BLOCKED_MSG)
            })
            // Si hay PIN/patrón, el sistema muestra el teclado y nadie lo escribe: abandonamos a los 60 s.
            handler.postDelayed({
                if (JobStore.get(this, jobId)?.state == StatusJob.RUNNING && km.isKeyguardLocked) fail(job, BLOCKED_MSG)
            }, 60_000)
        } else {
            launchWhatsApp(job)
        }
    }

    private fun launchWhatsApp(job: StatusJob) {
        // Pequeña espera para que la pantalla termine de encenderse.
        handler.postDelayed({
            val share = Intent(Intent.ACTION_SEND).apply {
                type = job.mimeType
                setPackage(job.pkg)
                if (job.caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, job.caption)
                if (!job.isText) {
                    val uri = FileProvider.getUriForFile(this@PublishActivity, "$packageName.fileprovider", File(job.filePath))
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                StatusAccessibilityService.arm(job.id, job.pkg)
                startActivity(share)
            } catch (e: Exception) {
                StatusAccessibilityService.disarm()
                fail(job, "No se pudo abrir WhatsApp: ${e.message}")
                return@postDelayed
            }
            finish()
        }, 1200)
    }

    private fun fail(job: StatusJob, msg: String) {
        if (JobStore.get(this, job.id)?.state != StatusJob.RUNNING) return
        JobStore.update(this, job.id, StatusJob.FAILED, msg)
        App.notify(this, job.id.toInt(), "No se publicó el estado", msg)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val BLOCKED_MSG =
            "La pantalla tiene PIN/patrón/huella y no se puede desbloquear sola. Quita el bloqueo esa noche."

        fun intent(ctx: Context, jobId: Long) =
            Intent(ctx, PublishActivity::class.java)
                .putExtra(Scheduler.EXTRA_JOB_ID, jobId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
