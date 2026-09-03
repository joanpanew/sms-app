package com.joanpa.smsaviso.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.joanpa.smsaviso.data.AppDatabase
import com.joanpa.smsaviso.databinding.ActivityMainBinding
import com.joanpa.smsaviso.prefs.SettingsStore
import com.joanpa.smsaviso.sms.SenderBus
import com.joanpa.smsaviso.sms.SimHelper
import com.joanpa.smsaviso.sms.SmsSenderService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var settings: SettingsStore
    private var sims: List<SimHelper.Sim> = emptyList()

    private val permissions = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val askPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_PHONE_STATE] == true) loadSims()
        val core = result[Manifest.permission.SEND_SMS] == true &&
            result[Manifest.permission.READ_CONTACTS] == true
        if (!core) Toast.makeText(this, getString(com.joanpa.smsaviso.R.string.perm_needed), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = SettingsStore(this)

        if (!settings.consentAccepted) showConsent() else ensurePermissions()

        bindFields()
        wireButtons()
        observeQueue()
        observeProgress()
    }

    override fun onResume() {
        super.onResume()
        refreshQueueLabel()
    }

    // ---------------- Consentimiento y permisos ----------------

    private fun showConsent() {
        AlertDialog.Builder(this)
            .setTitle(com.joanpa.smsaviso.R.string.consent_title)
            .setMessage(com.joanpa.smsaviso.R.string.consent_body)
            .setCancelable(false)
            .setPositiveButton(com.joanpa.smsaviso.R.string.consent_accept) { _, _ ->
                settings.consentAccepted = true
                ensurePermissions()
            }
            .setNegativeButton(com.joanpa.smsaviso.R.string.consent_decline) { _, _ -> finish() }
            .show()
    }

    private fun missingPermissions(): List<String> = permissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) loadSims() else askPermissions.launch(missing.toTypedArray())
    }

    // ---------------- Campos ----------------

    private fun bindFields() {
        b.etMessage.setText(settings.messageTemplate)
        b.etMinDelay.setText(settings.minDelaySec.toString())
        b.etJitter.setText(settings.jitterSec.toString())
        b.etMaxMin.setText(settings.maxPerMinute.toString())
        b.etMaxHour.setText(settings.maxPerHour.toString())
        b.etMaxDay.setText(settings.maxPerDay.toString())
        b.etBatchSize.setText(settings.batchSize.toString())
        b.etBatchPause.setText(settings.batchPauseMin.toString())
        b.etWinStart.setText(settings.windowStartHour.toString())
        b.etWinEnd.setText(settings.windowEndHour.toString())
        b.swRetry.isChecked = settings.retryOnSystemLimit

        b.etMessage.doAfterTextChanged { updateSmsCount(it?.toString().orEmpty()) }
        updateSmsCount(settings.messageTemplate)
    }

    private fun updateSmsCount(text: String) {
        if (text.isEmpty()) {
            b.tvSmsCount.text = "0 caracteres · 1 SMS por destinatario"
            return
        }
        val info = SmsMessage.calculateLength(text, false) // [0]=partes, [1]=usados, [2]=restantes
        val parts = info[0]
        b.tvSmsCount.text = "${text.length} caracteres · $parts SMS por destinatario" +
            if (parts > 1) " (mensaje largo, cuesta $parts)" else ""
    }

    private fun loadSims() {
        sims = SimHelper.activeSims(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sims.map { it.label })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spSim.adapter = adapter
        val idx = sims.indexOfFirst { it.subId == settings.simSubId }
        if (idx >= 0) b.spSim.setSelection(idx)
    }

    // ---------------- Botones ----------------

    private fun wireButtons() {
        b.btnPickContacts.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) startActivity(Intent(this, ContactsActivity::class.java))
            else askPermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }

        b.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        b.btnClearQueue.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Vaciar la cola")
                .setMessage("Se quitarán todos los destinatarios pendientes. El historial no se borra.")
                .setPositiveButton("Vaciar") { _, _ ->
                    lifecycleScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            AppDatabase.get(this@MainActivity).recipientDao().clear()
                        }
                        refreshQueueLabel()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        b.btnStart.setOnClickListener { onStart() }
        b.btnPause.setOnClickListener {
            val p = SenderBus.progress.value
            val action = if (p.paused) SmsSenderService.ACTION_RESUME else SmsSenderService.ACTION_PAUSE
            sendServiceAction(action)
        }
        b.btnStop.setOnClickListener { sendServiceAction(SmsSenderService.ACTION_STOP) }
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, SmsSenderService::class.java).setAction(action))
    }

    private fun saveFields() {
        settings.messageTemplate = b.etMessage.text?.toString()?.trim().orEmpty()
        settings.minDelaySec = b.etMinDelay.intOr(15)
        settings.jitterSec = b.etJitter.intOr(10)
        settings.maxPerMinute = b.etMaxMin.intOr(3)
        settings.maxPerHour = b.etMaxHour.intOr(80)
        settings.maxPerDay = b.etMaxDay.intOr(400)
        settings.batchSize = b.etBatchSize.intOr(30)
        settings.batchPauseMin = b.etBatchPause.intOr(8)
        settings.windowStartHour = b.etWinStart.intOr(8)
        settings.windowEndHour = b.etWinEnd.intOr(21)
        settings.retryOnSystemLimit = b.swRetry.isChecked
        val pos = b.spSim.selectedItemPosition
        if (pos in sims.indices) settings.simSubId = sims[pos].subId
    }

    private fun onStart() {
        val missing = missingPermissions().filter {
            it == Manifest.permission.SEND_SMS || it == Manifest.permission.READ_CONTACTS ||
                it == Manifest.permission.RECEIVE_SMS
        }
        if (missing.isNotEmpty()) {
            askPermissions.launch(missing.toTypedArray())
            return
        }
        saveFields()
        if (settings.messageTemplate.isBlank()) {
            Toast.makeText(this, "Escribe el mensaje", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppDatabase.get(this@MainActivity).recipientDao().countPending()
            }
            if (count == 0) {
                Toast.makeText(this@MainActivity, "No hay destinatarios pendientes en la cola", Toast.LENGTH_LONG).show()
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Confirmar envío")
                .setMessage(
                    "Se enviarán hasta $count SMS, uno cada ${settings.minDelaySec}-" +
                        "${settings.minDelaySec + settings.jitterSec} s, máx ${settings.maxPerMinute}/min, " +
                        "solo entre las ${settings.windowStartHour}:00 y las ${settings.windowEndHour}:00.\n\n" +
                        "Tu operadora cobrará cada mensaje. ¿Continuar?"
                )
                .setPositiveButton("Iniciar") { _, _ ->
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, SmsSenderService::class.java)
                            .setAction(SmsSenderService.ACTION_START)
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ---------------- Observadores ----------------

    private fun observeQueue() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.get(this@MainActivity).recipientDao().observeCount().collectLatest {
                    b.tvRecipients.text = "$it destinatarios en la cola"
                }
            }
        }
    }

    private fun refreshQueueLabel() {
        lifecycleScope.launch {
            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppDatabase.get(this@MainActivity).recipientDao().count()
            }
            b.tvRecipients.text = "$n destinatarios en la cola"
        }
    }

    private fun observeProgress() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SenderBus.progress.collectLatest { render(it) }
            }
        }
    }

    private fun render(p: com.joanpa.smsaviso.sms.Progress) {
        val processed = p.done + p.failed + p.skipped
        b.progress.max = if (p.total > 0) p.total else 100
        b.progress.setProgressCompat(processed, true)

        val lines = buildString {
            append("Enviados ${p.done} · Fallidos ${p.failed}")
            if (p.skipped > 0) append(" · Omitidos ${p.skipped}")
            append(" · Pendientes ${p.pending}")
            append("\n")
            when {
                p.finished -> append("✅ Envío finalizado")
                !p.running -> append(p.lastEvent ?: "Sin actividad")
                p.paused -> append("⏸ En pausa" + (p.waitingReason?.let { " — $it" } ?: ""))
                p.waitingReason != null -> append("⏳ ${p.waitingReason}" +
                    if (p.nextInSec > 0) " (${p.nextInSec}s)" else "")
                p.nextInSec > 0 -> append("Próximo SMS en ${p.nextInSec}s")
                else -> append(p.lastEvent ?: "Enviando…")
            }
        }
        b.tvStatus.text = lines

        b.btnStart.isEnabled = !p.running
        b.btnPause.isEnabled = p.running
        b.btnPause.text = if (p.paused) "Reanudar" else "Pausar"
        b.btnStop.isEnabled = p.running
    }
}

private fun android.widget.EditText.intOr(default: Int): Int =
    text?.toString()?.trim()?.toIntOrNull() ?: default
