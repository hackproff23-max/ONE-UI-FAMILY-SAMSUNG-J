package org.samsung.oneui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppInfo> = emptyList()

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    fun first(): AppInfo? = items.firstOrNull()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.newIcon())
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app, it)
            true
        }
    }
}
