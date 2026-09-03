package com.joanpa.smsaviso.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.joanpa.smsaviso.data.SendLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private fun esc(s: String?): String {
        val v = s ?: ""
        return "\"" + v.replace("\"", "\"\"") + "\""
    }

    fun export(context: Context, rows: List<SendLog>): Uri {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder("fecha,nombre,telefono,estado,sim,error\n")
        for (r in rows) {
            sb.append(esc(df.format(Date(r.timestamp)))).append(',')
                .append(esc(r.name)).append(',')
                .append(esc(r.phone)).append(',')
                .append(esc(r.status)).append(',')
                .append(esc(if (r.simSubId >= 0) r.simSubId.toString() else "default")).append(',')
                .append(esc(r.error)).append('\n')
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "historial_$stamp.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
