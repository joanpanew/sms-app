package com.joanpa.smsaviso.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joanpa.smsaviso.databinding.ItemContactBinding

data class ContactRow(val name: String, val phone: String)

class ContactAdapter(
    private val onToggle: () -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    private val all = mutableListOf<ContactRow>()
    private val visible = mutableListOf<ContactRow>()
    val selectedKeys = linkedSetOf<String>()

    fun submit(rows: List<ContactRow>) {
        all.clear(); all.addAll(rows)
        filter("")
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        visible.clear()
        if (q.isEmpty()) {
            visible.addAll(all)
        } else {
            all.filterTo(visible) {
                it.name.lowercase().contains(q) || it.phone.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    fun selectAllVisible(select: Boolean) {
        for (row in visible) {
            if (select) selectedKeys.add(key(row)) else selectedKeys.remove(key(row))
        }
        notifyDataSetChanged()
        onToggle()
    }

    fun selectedRows(): List<ContactRow> = all.filter { selectedKeys.contains(key(it)) }

    private fun key(row: ContactRow) = row.name + "|" + row.phone

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = visible.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = visible[position]
        holder.b.tvName.text = row.name
        holder.b.tvPhone.text = row.phone
        holder.b.cb.isChecked = selectedKeys.contains(key(row))
        holder.itemView.setOnClickListener {
            val k = key(row)
            if (selectedKeys.contains(k)) selectedKeys.remove(k) else selectedKeys.add(k)
            notifyItemChanged(position)
            onToggle()
        }
    }

    inner class VH(val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root)
}
