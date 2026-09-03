package com.joanpa.smsaviso.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.joanpa.smsaviso.data.GroupWithCount
import com.joanpa.smsaviso.databinding.ItemGroupBinding

class GroupAdapter(
    private val onClick: (GroupWithCount) -> Unit
) : RecyclerView.Adapter<GroupAdapter.VH>() {

    private val items = mutableListOf<GroupWithCount>()

    fun submit(list: List<GroupWithCount>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = items[position]
        holder.b.tvName.text = g.group.name
        holder.b.tvCount.text = "${g.memberCount} contacto(s)"
        holder.itemView.setOnClickListener { onClick(g) }
    }

    inner class VH(val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root)
}
