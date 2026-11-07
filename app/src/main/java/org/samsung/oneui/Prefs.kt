package org.samsung.oneui

import android.content.Context

/** Apps fixados na tela inicial e no dock, guardados como "pacote/atividade" em ordem. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("oneui_launcher", Context.MODE_PRIVATE)

    fun home(): MutableList<String> = read("home")
    fun dock(): MutableList<String> = read("dock")
    fun setHome(list: List<String>) = write("home", list)
    fun setDock(list: List<String>) = write("dock", list)

    fun iconPack(): String = sp.getString("icon_pack", "") ?: ""
    fun setIconPack(pkg: String) = sp.edit().putString("icon_pack", pkg).apply()
    fun iconShape(): IconShape = IconShape.fromId(sp.getString("icon_shape", null))
    fun setIconShape(s: IconShape) = sp.edit().putString("icon_shape", s.id).apply()

    /** Ajustar icones: tira o fundo branco e redesenha na forma. */
    fun fitIcons(): Boolean = sp.getBoolean("fit_icons", true)
    fun setFitIcons(v: Boolean) = sp.edit().putBoolean("fit_icons", v).apply()

    /** Tamanho dos icones em dp. */
    fun iconSize(): Int = sp.getInt("icon_size", ICON_DEF).coerceIn(ICON_MIN, ICON_MAX)
    fun setIconSize(v: Int) = sp.edit().putInt("icon_size", v).apply()

    /** Grade de uma lista de apps: target = "drawer" (gaveta) ou "home" (tela inicial). */
    fun grid(target: String): GridCfg = GridCfg(
        GridStyle.fromId(sp.getString("${target}_style", null)),
        sp.getInt("${target}_cols", 0).coerceIn(0, 15),
        sp.getInt("${target}_rows", 0).coerceIn(0, 15)
    )
    fun setGridStyle(target: String, s: GridStyle) = sp.edit().putString("${target}_style", s.id).apply()
    fun setGridCols(target: String, v: Int) = sp.edit().putInt("${target}_cols", v).apply()
    fun setGridRows(target: String, v: Int) = sp.edit().putInt("${target}_rows", v).apply()

    private fun read(key: String): MutableList<String> {
        val s = sp.getString(key, "") ?: ""
        return if (s.isEmpty()) mutableListOf() else s.split("\n").toMutableList()
    }

    private fun write(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString("\n")).apply()
    }

    companion object {
        const val DOCK_MAX = 5
        const val HOME_MAX = 16
        const val ICON_MIN = 32
        const val ICON_MAX = 96
        const val ICON_DEF = 56
    }
}
