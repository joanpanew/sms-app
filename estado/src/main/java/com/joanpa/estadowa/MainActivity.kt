package com.joanpa.estadowa

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.joanpa.estadowa.databinding.ActivityMainBinding
import com.joanpa.estadowa.databinding.ItemCheckBinding
import com.joanpa.estadowa.databinding.ItemJobBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var pickedUri: Uri? = null
    private var pickedName = ""
    private var pickedMime = "video/*"
    private val cal: Calendar = defaultTime()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        pickedUri = uri
        pickedMime = contentResolver.getType(uri) ?: "video/*"
        pickedName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "video"
        b.tvVideo.text = pickedName
    }

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.swLock.isChecked = AppPrefs.lockAfter(this)
        b.swLock.setOnCheckedChangeListener { _, v -> AppPrefs.setLockAfter(this, v) }

        setupAppChoice()

        b.btnVideo.setOnClickListener { pickFile.launch(arrayOf("video/*", "image/*")) }
        b.btnDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d); updateWhen()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        b.btnTime.setOnClickListener {
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); cal.set(Calendar.SECOND, 0)
                updateWhen()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        setupForward()

        b.btnSchedule.setOnClickListener { createJob(cal.timeInMillis) }
        b.btnTest.setOnClickListener { createJob(System.currentTimeMillis() + 20_000) }
        updateWhen()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------- Programar ----------

    private fun createJob(time: Long) {
        val uri = pickedUri ?: return toast("Primero elige el video.")
        if (time < System.currentTimeMillis() + 5_000) return toast("Esa hora ya pasó. Elige una hora futura.")
        if (!StatusAccessibilityService.isEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Falta activar la accesibilidad")
                .setMessage("Sin el servicio de accesibilidad la app no puede pulsar \"Mi estado\" y \"Enviar\" en WhatsApp.")
                .setPositiveButton("Activar") { _, _ -> openAccessibility() }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val pkg = if (b.rbWaBiz.isChecked) PKG_BIZ else PKG_WA
        val caption = b.etCaption.text?.toString().orEmpty().trim()
        b.btnSchedule.isEnabled = false
        b.btnTest.isEnabled = false
        Thread {
            val result = runCatching {
                val id = System.currentTimeMillis()
                val ext = pickedName.substringAfterLast('.', "mp4").take(5)
                val dir = File(filesDir, "videos").apply { mkdirs() }
                val dest = File(dir, "$id.$ext")
                // Se copia el video dentro de la app: así sigue disponible aunque lo borres de la galería.
                contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
                StatusJob(id, time, dest.absolutePath, pickedName, pickedMime, caption, pkg)
                    .also { JobStore.save(this, it); Scheduler.schedule(this, it) }
            }
            runOnUiThread {
                b.btnSchedule.isEnabled = true
                b.btnTest.isEnabled = true
                result.onSuccess {
                    toast("Programado para ${fmt(it.timeMillis)}")
                    pickedUri = null
                    b.tvVideo.text = "Ningún archivo elegido"
                    b.etCaption.setText("")
                    refresh()
                }.onFailure { toast("No se pudo copiar el video: ${it.message}") }
            }
        }.start()
    }

    private fun updateWhen() {
        b.btnDate.text = SimpleDateFormat("EEE d MMM yyyy", Locale("es")).format(cal.time)
        b.btnTime.text = SimpleDateFormat("HH:mm", Locale("es")).format(cal.time)
    }

    // ---------- Telegram ----------

    private fun setupForward() {
        b.swForward.isChecked = AppPrefs.forwardEnabled(this)
        b.etSource.setText(AppPrefs.source(this))
        buildIntervalChips()
        showInterval()
        b.swForward.setOnCheckedChangeListener { _, on ->
            if (on && !TelegramListener.isEnabled(this)) {
                toast("Primero da acceso a las notificaciones (botón Arreglar).")
            }
            AppPrefs.setForwardEnabled(this, on)
        }
        b.etSource.doAfterTextChanged { AppPrefs.setSource(this, it?.toString().orEmpty().trim()) }
        b.etInterval.setOnEditorActionListener { v, _, _ ->
            v.text?.toString()?.toIntOrNull()?.let { setInterval(it) }
            false
        }
        b.etInterval.setOnFocusChangeListener { v, has ->
            if (!has) (v as android.widget.TextView).text?.toString()?.toIntOrNull()?.let { setInterval(it) }
        }
    }

    private fun buildIntervalChips() {
        val current = AppPrefs.intervalMin(this)
        b.cgInterval.removeAllViews()
        INTERVALS.forEach { min ->
            val chip = Chip(this).apply {
                text = if (min < 60) "$min min" else "${min / 60} h"
                isCheckable = true
                isChecked = min == current
                setOnClickListener { setInterval(min) }
            }
            b.cgInterval.addView(chip)
        }
    }

    /** Cambia el intervalo en cualquier momento; también reordena lo que ya está en cola. */
    private fun setInterval(min: Int) {
        val v = min.coerceIn(1, 24 * 60)
        if (v == AppPrefs.intervalMin(this)) return showInterval()
        AppPrefs.setIntervalMin(this, v)
        TextQueue.respace(this)
        buildIntervalChips()
        showInterval()
        renderJobs()
        toast("Ahora: 1 noticia cada ${fmtMin(v)}. Las que estaban en cola se reprogramaron.")
    }

    private fun showInterval() {
        val v = AppPrefs.intervalMin(this)
        b.tvInterval.text = "Tiempo entre publicaciones: ${fmtMin(v)} (cámbialo cuando quieras)"
        if (b.etInterval.text?.toString() != v.toString()) b.etInterval.setText(v.toString())
    }

    private fun fmtMin(v: Int) = if (v < 60 || v % 60 != 0) "$v min" else "${v / 60} h"

    private fun renderForwardChecks() {
        b.fwdChecks.removeAllViews()
        addCheck(
            b.fwdChecks,
            TelegramListener.isEnabled(this),
            "Acceso a notificaciones permitido",
            "Falta el acceso a las notificaciones (para leer los mensajes de Telegram)"
        ) {
            restrictedHelp("acceso a notificaciones") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        val tgOk = TelegramListener.TELEGRAM_PKGS.any { isInstalled(it) }
        addCheck(b.fwdChecks, tgOk, "Telegram instalado", "No encuentro Telegram instalado", null)
    }

    /**
     * Android 13+ bloquea accesibilidad y acceso a notificaciones en apps instaladas desde un APK
     * ("A la app se le negó el acceso"). Explica cómo desbloquearlo.
     */
    private fun restrictedHelp(what: String, open: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Activar $what")
            .setMessage(
                "Si Android dice \"A la app se le negó el acceso\" o \"Configuración restringida\":\n\n" +
                    "1. Intenta activarlo una vez y pulsa Cerrar en el aviso.\n" +
                    "2. Pulsa \"Info de la app\" aquí abajo.\n" +
                    "3. Arriba a la derecha toca ⋮ → \"Permitir configuración restringida\" y confirma.\n" +
                    "4. Vuelve aquí y pulsa \"Abrir ajustes\" para activarlo."
            )
            .setPositiveButton("Abrir ajustes") { _, _ -> open() }
            .setNeutralButton("Info de la app") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // ---------- Lista ----------

    private fun renderJobs() {
        val jobs = JobStore.all(this).sortedByDescending { it.timeMillis }
        b.jobs.removeAllViews()
        b.tvEmpty.visibility = if (jobs.isEmpty()) View.VISIBLE else View.GONE
        jobs.forEach { job ->
            val row = ItemJobBinding.inflate(LayoutInflater.from(this), b.jobs, false)
            row.tvWhen.text = fmt(job.timeMillis)
            row.tvName.text = job.fileName + if (job.caption.isNotBlank()) " · \"${job.caption.replace('\n', ' ')}\"" else ""
            row.tvState.text = job.state + if (job.detail.isNotBlank()) " — ${job.detail}" else ""
            row.tvState.setTextColor(ContextCompat.getColor(this, when (job.state) {
                StatusJob.DONE -> R.color.ok_green
                StatusJob.FAILED -> R.color.fail_red
                else -> R.color.warn_amber
            }))
            row.btnDelete.text = if (job.state == StatusJob.PENDING) "Cancelar" else "Borrar"
            row.btnDelete.setOnClickListener {
                Scheduler.cancel(this, job.id)
                JobStore.delete(this, job.id)
                renderJobs()
            }
            b.jobs.addView(row.root)
        }
    }

    // ---------- Comprobaciones ----------

    private fun refresh() {
        renderChecks()
        renderForwardChecks()
        renderJobs()
    }

    @SuppressLint("BatteryLife")
    private fun renderChecks() {
        b.checks.removeAllViews()

        addCheck(
            StatusAccessibilityService.isEnabled(this),
            "Servicio de accesibilidad activado",
            "Accesibilidad DESACTIVADA (obligatorio). En Ajustes busca \"Estado Programado\" y actívalo."
        ) { restrictedHelp("la accesibilidad") { openAccessibility() } }

        if (Build.VERSION.SDK_INT >= 33) {
            addCheck(
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                "Notificaciones permitidas",
                "Notificaciones bloqueadas (recomendado, para avisarte del resultado)"
            ) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        val pm = getSystemService(PowerManager::class.java)
        addCheck(
            pm.isIgnoringBatteryOptimizations(packageName),
            "Sin restricciones de batería",
            "La optimización de batería puede retrasar la publicación (recomendado quitarla)"
        ) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addCheck(
                getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
                "Alarmas exactas permitidas",
                "Alarmas exactas no permitidas: la publicación podría retrasarse"
            ) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        val secure = getSystemService(KeyguardManager::class.java).isDeviceSecure
        addCheck(
            !secure,
            "Pantalla sin PIN/patrón: se puede desbloquear sola",
            "Tu teléfono tiene PIN, patrón o huella. Ninguna app puede desbloquearlo sola: " +
                "la noche que programes, pon el bloqueo en \"Ninguno\" o \"Deslizar\"."
        ) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }

        val waOk = isInstalled(PKG_WA) || isInstalled(PKG_BIZ)
        addCheck(waOk, "WhatsApp instalado", "No encuentro WhatsApp instalado", null)
    }

    private fun addCheck(ok: Boolean, okText: String, badText: String, fix: (() -> Unit)?): Unit =
        addCheck(b.checks, ok, okText, badText, fix)

    private fun addCheck(container: LinearLayout, ok: Boolean, okText: String, badText: String, fix: (() -> Unit)?) {
        val row = ItemCheckBinding.inflate(LayoutInflater.from(this), container, false)
        row.tvCheck.text = (if (ok) "✅ " else "⚠️ ") + (if (ok) okText else badText)
        row.btnFix.visibility = if (!ok && fix != null) View.VISIBLE else View.GONE
        row.btnFix.setOnClickListener {
            try { fix?.invoke() } catch (_: Exception) { toast("Ábrelo a mano desde Ajustes.") }
        }
        container.addView(row.root)
    }

    private fun setupAppChoice() {
        val wa = isInstalled(PKG_WA)
        val biz = isInstalled(PKG_BIZ)
        b.rbWa.isEnabled = wa
        b.rbWaBiz.isEnabled = biz
        val saved = AppPrefs.pkg(this)
        when {
            saved == PKG_BIZ && biz -> b.rbWaBiz.isChecked = true
            wa -> b.rbWa.isChecked = true
            biz -> b.rbWaBiz.isChecked = true
        }
        b.rgApp.setOnCheckedChangeListener { _, id ->
            AppPrefs.setPkg(this, if (id == R.id.rbWaBiz) PKG_BIZ else PKG_WA)
        }
    }

    private fun openAccessibility() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun isInstalled(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun fmt(t: Long) = SimpleDateFormat("EEE d MMM, HH:mm", Locale("es")).format(t)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        const val PKG_WA = "com.whatsapp"
        const val PKG_BIZ = "com.whatsapp.w4b"
        private val INTERVALS = listOf(1, 5, 10, 15, 30, 60, 120)

        /** Por defecto: próximas 5:00 a. m. */
        private fun defaultTime(): Calendar = Calendar.getInstance().apply {
            val now = timeInMillis
            set(Calendar.HOUR_OF_DAY, 5); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}
