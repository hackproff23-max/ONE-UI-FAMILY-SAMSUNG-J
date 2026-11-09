package org.samsung.oneui

import android.content.Context

/** Liga/desliga o efeito de vidro dos icones. */
object Glass {
    private fun sp(c: Context) = c.getSharedPreferences("oneui_launcher", Context.MODE_PRIVATE)

    fun on(c: Context): Boolean = sp(c).getBoolean("glass", true)

    fun set(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("glass", v).apply()
    }
}
