package com.joanpa.smsaviso.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.joanpa.smsaviso.data.AppDatabase
import com.joanpa.smsaviso.databinding.ActivityHistoryBinding
import com.joanpa.smsaviso.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistoryBinding
    private val adapter = HistoryAdapter()
    private val df = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private var showingOptOut = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter

        b.btnToggle.setOnClickListener {
            showingOptOut = !showingOptOut
            b.btnToggle.text = if (showingOptOut) "Ver historial" else "Ver exclusiones"
            b.tvHeader.text = if (showingOptOut) "Lista de exclusión (bajas)" else "Historial de envíos"
            b.btnExport.isEnabled = !showingOptOut
            render()
        }
        b.btnExport.setOnClickListener { exportCsv() }

        render()
    }

    private fun render() {
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(this@HistoryActivity)
                if (showingOptOut) {
                    val list = mutableListOf<HistoryRow>()
                    db.query(
                        "SELECT originalAddress, timestamp, reason FROM opt_out ORDER BY timestamp DESC",
                        null
                    ).use { c ->
                        while (c.moveToNext()) {
                            list.add(
                                HistoryRow(
                                    title = c.getString(0),
                                    status = "BAJA",
                                    meta = df.format(Date(c.getLong(1))),
                                    error = c.getString(2)
                                )
                            )
                        }
                    }
                    list
                } else {
                    db.sendLogDao().getAll().map { log ->
                        HistoryRow(
                            title = "${log.name}  ·  ${log.phone}",
                            status = log.status,
                            meta = df.format(Date(log.timestamp)) +
                                (if (log.simSubId >= 0) "  ·  SIM ${log.simSubId}" else ""),
                            error = log.error
                        )
                    }
                }
            }
            adapter.submit(rows)
            if (rows.isEmpty()) {
                Toast.makeText(this@HistoryActivity, "Sin registros todavía", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val logs = AppDatabase.get(this@HistoryActivity).sendLogDao().getAll()
                    if (logs.isEmpty()) null else CsvExporter.export(this@HistoryActivity, logs)
                }
                if (uri == null) {
                    Toast.makeText(this@HistoryActivity, "No hay historial para exportar", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, "Compartir historial CSV"))
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
