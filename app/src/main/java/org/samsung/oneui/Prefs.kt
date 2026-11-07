package org.samsung.oneui

import android.content.Context

/** Apps fixados na tela inicial e no dock, guardados como "pacote/atividade" em ordem. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("oneui_launcher", Context.MODE_PRIVATE)

    fun home(): MutableList<String> = read("home")
    fun dock(): MutableList<String> = read("dock")
    fun setHome(list: List<String>) = write("home", list)
    fun setDock(list: List<String>) = write("dock", list)

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
    }
}
