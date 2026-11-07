package org.samsung.oneui

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

enum class GridStyle(val id: String, val label: String) {
    VERTICAL("vertical", "Vertical (rola para cima/baixo)"),
    HORIZONTAL("horizontal", "Horizontal (rola para o lado)"),
    LIST("list", "Lista");

    companion object {
        fun fromId(id: String?): GridStyle = values().firstOrNull { it.id == id } ?: VERTICAL
    }
}

/** cols/rows: 0 = automatico, 1..15 = valor escolhido. */
class GridCfg(val style: GridStyle, val cols: Int, val rows: Int)

/**
 * Liga uma lista de apps ao estilo escolhido: lista, grade vertical ou grade horizontal,
 * com N colunas e N linhas visiveis ao mesmo tempo.
 */
class GridController(
    private val rv: RecyclerView,
    private val adapter: AppAdapter,
    private val defCols: Int,
    private val homeFixed: Boolean
) {
    private var cfg = GridCfg(GridStyle.VERTICAL, 0, 0)
    private var iconDp = Prefs.ICON_DEF
    private var lastW = -1
    private var lastH = -1

    init {
        rv.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l
            val h = b - t
            if (w != lastW || h != lastH) {
                lastW = w
                lastH = h
                rv.post { configure() }
            }
        }
    }

    private fun effCols(): Int = if (cfg.cols > 0) cfg.cols else defCols
    private fun effRows(): Int = if (cfg.rows > 0) cfg.rows else 4

    fun apply(c: GridCfg, iconDp: Int) {
        cfg = c
        this.iconDp = iconDp
        val ctx = rv.context
        rv.layoutManager = when (c.style) {
            GridStyle.LIST -> LinearLayoutManager(ctx, RecyclerView.VERTICAL, false)
            GridStyle.VERTICAL -> object : GridLayoutManager(ctx, effCols()) {
                override fun canScrollVertically(): Boolean = if (homeFixed) false else super.canScrollVertically()
            }
            GridStyle.HORIZONTAL -> GridLayoutManager(ctx, effRows(), RecyclerView.HORIZONTAL, false)
        }
        configure()
    }

    private fun configure() {
        val d = rv.resources.displayMetrics.density
        val availW = rv.width - rv.paddingLeft - rv.paddingRight
        val availH = rv.height - rv.paddingTop - rv.paddingBottom
        var cw = 0
        var ch = 0
        if (cfg.style == GridStyle.HORIZONTAL && availW > 0) cw = availW / effCols()
        if (availH > 0) {
            if (cfg.rows > 0) ch = availH / cfg.rows
            else if (cfg.style == GridStyle.HORIZONTAL) ch = availH / effRows()
        }
        adapter.configure(
            cfg.style == GridStyle.LIST,
            cfg.style == GridStyle.HORIZONTAL,
            (iconDp * d + 0.5f).toInt(),
            cw,
            ch
        )
    }
}
