package com.joanpa.smsaviso.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joanpa.smsaviso.databinding.ItemHistoryBinding

data class HistoryRow(
    val title: String,
    val status: String,
    val meta: String,
    val error: String?
)

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val rows = mutableListOf<HistoryRow>()

    fun submit(list: List<HistoryRow>) {
        rows.clear(); rows.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.b.tvName.text = r.title
        holder.b.tvStatus.text = r.status
        holder.b.tvMeta.text = r.meta
        holder.b.tvStatus.setTextColor(colorFor(r.status))
        if (r.error.isNullOrBlank()) {
            holder.b.tvError.visibility = View.GONE
        } else {
            holder.b.tvError.visibility = View.VISIBLE
            holder.b.tvError.text = r.error
        }
    }

    private fun colorFor(status: String): Int = when (status.uppercase()) {
        "SENT", "DELIVERED" -> Color.parseColor("#2E7D32")
        "FAILED" -> Color.parseColor("#C62828")
        "ATTEMPT" -> Color.parseColor("#EF6C00")
        else -> Color.parseColor("#EF6C00")
    }

    inner class VH(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)
}
