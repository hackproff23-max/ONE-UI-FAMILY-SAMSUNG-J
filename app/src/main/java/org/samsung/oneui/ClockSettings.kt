package org.samsung.oneui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Janela do relogio: lista de ajustes que fica aberta embaixo da tela, e cada ajuste aparece
 * ao vivo no relogio (em cima). Cancelar volta ao valor anterior.
 */
class ClockSettings private constructor(private val a: Activity) {

    private val cp = ClockPrefs(a)
    private val density = a.resources.displayMetrics.density
    private val labels = ArrayList<String>()
    private var actions: List<() -> Unit> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    companion object {
        fun show(a: Activity) {
            ClockSettings(a).open()
        }

        private val FONTS = listOf(
            "Fina" to "sans-serif-thin",
            "Leve" to "sans-serif-light",
            "Normal" to "sans-serif",
            "Média" to "sans-serif-medium",
            "Preta" to "sans-serif-black",
            "Condensada" to "sans-serif-condensed",
            "Serifada" to "serif",
            "Monoespaçada" to "monospace",
            "Cursiva" to "cursive",
            "Casual" to "casual"
        )
        private val FONT_STYLES = listOf("Normal", "Negrito", "Itálico", "Negrito itálico")
        private val ALIGNS = listOf("Esquerda", "Centro", "Direita")
        private val HOUR_FORMATS = listOf("Sistema", "24 horas", "12 horas")
        private val DATE_FORMATS = listOf("Completa (domingo, 8 de novembro)", "Curta (08/11)", "Só o dia da semana", "Com ano")

        private val PRESETS = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFD700.toInt(), 0xFFFFA726.toInt(),
            0xFFFF5252.toInt(), 0xFFFF4081.toInt(), 0xFFB388FF.toInt(), 0xFF448AFF.toInt(),
            0xFF18FFFF.toInt(), 0xFF69F0AE.toInt(), 0xFFB2FF59.toInt(), 0xFF9E9E9E.toInt()
        )
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    private fun onOff(b: Boolean): String = if (b) "ligado" else "desligado"
    private fun fontLabel(): String = FONTS.firstOrNull { it.second == cp.font }?.first ?: cp.font
    private fun hex(c: Int): String = String.format("#%06X", c and 0xFFFFFF)

    // ---------- lista principal ----------

    private fun entries(): List<Pair<String, () -> Unit>> = listOf(
        "Mostrar hora: ${onOff(cp.showClock)}" to { cp.showClock = !cp.showClock; refresh() },
        "Mostrar data: ${onOff(cp.showDate)}" to { cp.showDate = !cp.showDate; refresh() },

        "Hora: tamanho (${cp.hourSize} sp)" to { slider("Tamanho da hora", 20, 200, cp.hourSize, "sp") { cp.hourSize = it } },
        "Hora: cor (${hex(cp.hourColor)})" to { colorDialog("Cor da hora", cp.hourColor) { cp.hourColor = it } },
        "Data: tamanho (${cp.dateSize} sp)" to { slider("Tamanho da data", 8, 60, cp.dateSize, "sp") { cp.dateSize = it } },
        "Data: cor (${hex(cp.dateColor)})" to { colorDialog("Cor da data", cp.dateColor) { cp.dateColor = it } },

        "Fonte: ${fontLabel()}" to { chooseFont() },
        "Estilo da fonte: ${FONT_STYLES[cp.fontStyle.coerceIn(0, 3)]}" to {
            choose("Estilo da fonte", FONT_STYLES, cp.fontStyle.coerceIn(0, 3)) { cp.fontStyle = it }
        },
        "Espaço entre letras (${cp.letter})" to { slider("Espaço entre letras", -10, 50, cp.letter, "") { cp.letter = it } },
        "Largura do texto (${cp.width}%)" to { slider("Largura do texto", 50, 200, cp.width, "%") { cp.width = it } },
        "Inclinação das letras (${cp.skew})" to { slider("Inclinação das letras", -50, 50, cp.skew, "") { cp.skew = it } },

        "Contorno: espessura (${cp.stroke})" to { slider("Espessura do contorno (0 = sem contorno)", 0, 20, cp.stroke, "") { cp.stroke = it } },
        "Contorno: cor (${hex(cp.strokeColor)})" to { colorDialog("Cor do contorno", cp.strokeColor) { cp.strokeColor = it } },
        "Sombra: ${onOff(cp.shadow)}" to { cp.shadow = !cp.shadow; refresh() },

        "Opacidade (${cp.opacity}%)" to { slider("Opacidade", 10, 100, cp.opacity, "%") { cp.opacity = it } },
        "Alinhamento: ${ALIGNS[cp.align.coerceIn(0, 2)]}" to {
            choose("Alinhamento", ALIGNS, cp.align.coerceIn(0, 2)) { cp.align = it }
        },

        "Formato da hora: ${HOUR_FORMATS[cp.hourFormat.coerceIn(0, 2)]}" to {
            choose("Formato da hora", HOUR_FORMATS, cp.hourFormat.coerceIn(0, 2)) { cp.hourFormat = it }
        },
        "Mostrar segundos: ${onOff(cp.seconds)}" to { cp.seconds = !cp.seconds; refresh() },
        "Formato da data: ${DATE_FORMATS[cp.dateFormat.coerceIn(0, 3)]}" to {
            choose("Formato da data", DATE_FORMATS, cp.dateFormat.coerceIn(0, 3)) { cp.dateFormat = it }
        },

        "Restaurar padrão" to { cp.reset(); refresh() }
    )

    private fun refresh() {
        ClockStyle.apply(a)
        val e = entries()
        labels.clear()
        labels.addAll(e.map { it.first })
        actions = e.map { it.second }
        adapter.notifyDataSetChanged()
    }

    private fun open() {
        adapter = ArrayAdapter(a, android.R.layout.simple_list_item_1, labels)
        val e = entries()
        labels.addAll(e.map { it.first })
        actions = e.map { it.second }
        val dlg = Dark.builder(a)
            .setTitle("Estilo do relógio")
            .setAdapter(adapter, null)
            .setPositiveButton("Fechar", null)
            .create()
        dlg.show()
        // a lista nao fecha ao tocar num item: o relogio muda ao vivo la em cima
        dlg.listView.setOnItemClickListener { _, _, pos, _ -> actions[pos]() }
        dlg.window?.apply {
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.1f)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (a.resources.displayMetrics.heightPixels * 0.55f).toInt())
        }
    }

    private fun lowShow(d: AlertDialog) {
        d.show()
        d.window?.apply {
            setGravity(Gravity.BOTTOM)
            setDimAmount(0f)
        }
    }

    // ---------- controles ----------

    private fun slider(title: String, lo: Int, hi: Int, cur: Int, unit: String, set: (Int) -> Unit) {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        val value = TextView(a).apply {
            text = "$cur $unit"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val bar = SeekBar(a)
        bar.max = hi - lo
        bar.progress = (cur - lo).coerceIn(0, hi - lo)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                value.text = "${p + lo} $unit"
                set(p + lo)
                ClockStyle.apply(a)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(value)
        box.addView(bar)
        val dlg = Dark.builder(a)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> refresh() }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                set(cur)
                refresh()
            }
            .setOnCancelListener {
                set(cur)
                refresh()
            }
            .create()
        lowShow(dlg)
    }

    private fun choose(title: String, names: List<String>, selected: Int, set: (Int) -> Unit) {
        Dark.builder(a)
            .setTitle(title)
            .setSingleChoiceItems(names.toTypedArray(), selected) { dialog, which ->
                set(which)
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseFont() {
        val cur = FONTS.indexOfFirst { it.second == cp.font }.coerceAtLeast(0)
        choose("Fonte", FONTS.map { it.first }, cur) { cp.font = FONTS[it].second }
    }

    private fun colorDialog(title: String, cur: Int, set: (Int) -> Unit) {
        val start = cur or 0xFF000000.toInt()
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        val swatch = View(a)
        swatch.setBackgroundColor(start)
        box.addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))
        val hexView = TextView(a).apply {
            text = hex(start)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        box.addView(hexView)

        fun bar(initial: Int): SeekBar {
            val s = SeekBar(a)
            s.max = 255
            s.progress = initial
            box.addView(s)
            return s
        }

        val rb = bar(Color.red(start))
        val gb = bar(Color.green(start))
        val bb = bar(Color.blue(start))

        fun push() {
            val c = Color.rgb(rb.progress, gb.progress, bb.progress)
            swatch.setBackgroundColor(c)
            hexView.text = hex(c)
            set(c)
            ClockStyle.apply(a)
        }

        val l = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) push()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        rb.setOnSeekBarChangeListener(l)
        gb.setOnSeekBarChangeListener(l)
        bb.setOnSeekBarChangeListener(l)

        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in PRESETS) {
            val chip = View(a)
            chip.setBackgroundColor(c)
            chip.setOnClickListener {
                rb.progress = Color.red(c)
                gb.progress = Color.green(c)
                bb.progress = Color.blue(c)
                push()
            }
            val lp = LinearLayout.LayoutParams(dp(36), dp(36))
            lp.setMargins(0, dp(8), dp(8), dp(4))
            row.addView(chip, lp)
        }
        val scroll = HorizontalScrollView(a)
        scroll.addView(row)
        box.addView(scroll)

        val dlg = Dark.builder(a)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> refresh() }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                set(cur)
                refresh()
            }
            .setOnCancelListener {
                set(cur)
                refresh()
            }
            .create()
        lowShow(dlg)
    }
}
