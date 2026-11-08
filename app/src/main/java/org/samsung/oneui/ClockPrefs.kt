package org.samsung.oneui

import android.content.Context
import android.graphics.Color

/** Tudo que o usuario personaliza no relogio da tela inicial. */
class ClockPrefs(context: Context) {
    private val sp = context.getSharedPreferences("oneui_clock", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun put(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    var showClock: Boolean
        get() = sp.getBoolean("show_clock", true)
        set(v) = sp.edit().putBoolean("show_clock", v).apply()
    var showDate: Boolean
        get() = sp.getBoolean("show_date", true)
        set(v) = sp.edit().putBoolean("show_date", v).apply()
    var shadow: Boolean
        get() = sp.getBoolean("shadow", true)
        set(v) = sp.edit().putBoolean("shadow", v).apply()
    var seconds: Boolean
        get() = sp.getBoolean("seconds", false)
        set(v) = sp.edit().putBoolean("seconds", v).apply()

    var font: String
        get() = sp.getString("font", "sans-serif-thin") ?: "sans-serif-thin"
        set(v) = sp.edit().putString("font", v).apply()

    /** 0 normal, 1 negrito, 2 italico, 3 negrito italico (valores de Typeface). */
    var fontStyle: Int
        get() = int("font_style", 0)
        set(v) = put("font_style", v)

    var hourSize: Int
        get() = int("hour_size", 68)
        set(v) = put("hour_size", v)
    var dateSize: Int
        get() = int("date_size", 18)
        set(v) = put("date_size", v)
    var hourColor: Int
        get() = int("hour_color", Color.WHITE)
        set(v) = put("hour_color", v)
    var dateColor: Int
        get() = int("date_color", 0xFFEDEDED.toInt())
        set(v) = put("date_color", v)

    /** Espacamento entre letras, -10..50 (centesimos de em). */
    var letter: Int
        get() = int("letter", 0)
        set(v) = put("letter", v)

    /** Largura do texto, 50..200 (%). */
    var width: Int
        get() = int("width", 100)
        set(v) = put("width", v)

    /** Inclinacao das letras, -50..50 (centesimos). */
    var skew: Int
        get() = int("skew", 0)
        set(v) = put("skew", v)

    /** Contorno: espessura 0..20 (0 = sem contorno) e cor. */
    var stroke: Int
        get() = int("stroke", 0)
        set(v) = put("stroke", v)
    var strokeColor: Int
        get() = int("stroke_color", Color.BLACK)
        set(v) = put("stroke_color", v)

    // perspectiva e transformacao do bloco inteiro
    var rotX: Int
        get() = int("rot_x", 0)
        set(v) = put("rot_x", v)
    var rotY: Int
        get() = int("rot_y", 0)
        set(v) = put("rot_y", v)
    var rotZ: Int
        get() = int("rot_z", 0)
        set(v) = put("rot_z", v)

    /** Profundidade da camera, 1..100: menor = perspectiva mais forte. */
    var depth: Int
        get() = int("depth", 40)
        set(v) = put("depth", v)
    var scale: Int
        get() = int("scale", 100)
        set(v) = put("scale", v)
    var posX: Int
        get() = int("pos_x", 0)
        set(v) = put("pos_x", v)
    var posY: Int
        get() = int("pos_y", 0)
        set(v) = put("pos_y", v)
    var opacity: Int
        get() = int("opacity", 100)
        set(v) = put("opacity", v)

    /** 0 esquerda, 1 centro, 2 direita. */
    var align: Int
        get() = int("align", 0)
        set(v) = put("align", v)

    /** 0 sistema, 1 24h, 2 12h. */
    var hourFormat: Int
        get() = int("hour_format", 0)
        set(v) = put("hour_format", v)

    /** 0 completa, 1 curta, 2 so o dia da semana, 3 com ano. */
    var dateFormat: Int
        get() = int("date_format", 0)
        set(v) = put("date_format", v)

    fun reset() {
        sp.edit().clear().apply()
    }
}
