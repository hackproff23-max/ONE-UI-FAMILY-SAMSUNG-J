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
    private var listMode = false
    private var horizontal = false
    private var iconPx = 0
    private var cellW = 0
    private var cellH = 0

    fun submit(list: List<AppInfo>) {
        items = list
        notifyDataSetChanged()
    }

    /** Estilo e tamanhos: lista ou grade, tamanho do icone e da celula (0 = automatico). */
    fun configure(list: Boolean, horizontal: Boolean, iconPx: Int, cellW: Int, cellH: Int) {
        if (list == listMode && horizontal == this.horizontal && iconPx == this.iconPx &&
            cellW == this.cellW && cellH == this.cellH
        ) return
        listMode = list
        this.horizontal = horizontal
        this.iconPx = iconPx
        this.cellW = cellW
        this.cellH = cellH
        notifyDataSetChanged()
    }

    fun first(): AppInfo? = items.firstOrNull()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun getItemViewType(position: Int): Int = if (listMode) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 1) R.layout.item_app_list else R.layout.item_app
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        if (iconPx > 0) {
            val ip = holder.icon.layoutParams
            ip.width = iconPx
            ip.height = iconPx
            holder.icon.layoutParams = ip
        }
        val lp = holder.itemView.layoutParams
        lp.width = if (horizontal && cellW > 0) cellW else ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = if (cellH > 0) cellH else ViewGroup.LayoutParams.WRAP_CONTENT
        holder.itemView.layoutParams = lp
        holder.icon.setImageDrawable(app.newIcon())
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app, it)
            true
        }
    }
}
