package org.samsung.oneui

import android.app.Activity
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.util.Locale

/** Aplica as preferencias do relogio nas views da tela inicial. */
object ClockStyle {

    /** Deformacao em edicao (ainda nao confirmada); null = usa a salva. */
    var liveQuad: FloatArray? = null

    fun apply(a: Activity) {
        val frame = a.findViewById<PerspectiveFrame>(R.id.clockFrame) ?: return
        val box = a.findViewById<LinearLayout>(R.id.clockBox) ?: return
        val hour = a.findViewById<OutlineTextClock>(R.id.clock) ?: return
        val date = a.findViewById<OutlineTextClock>(R.id.date) ?: return
        val cp = ClockPrefs(a)
        val d = a.resources.displayMetrics.density

        hour.visibility = if (cp.showClock) View.VISIBLE else View.GONE
        date.visibility = if (cp.showDate) View.VISIBLE else View.GONE
        frame.visibility = if (cp.showClock || cp.showDate) View.VISIBLE else View.GONE

        // texto
        val face = Typeface.create(cp.font, cp.fontStyle)
        val strokePx = cp.stroke * d * 0.5f
        val pad = if (strokePx > 0f) strokePx.toInt() + 1 else 0
        style(hour, cp, face, cp.hourSize, cp.hourColor, strokePx, pad, 8f, 2f)
        style(date, cp, face, cp.dateSize, cp.dateColor, strokePx, pad, 6f, 1f)

        // formatos
        val f24 = if (cp.seconds) "HH:mm:ss" else "HH:mm"
        val f12 = if (cp.seconds) "h:mm:ss" else "h:mm"
        when (cp.hourFormat) {
            1 -> {
                hour.format24Hour = f24
                hour.format12Hour = f24
            }
            2 -> {
                hour.format24Hour = f12
                hour.format12Hour = f12
            }
            else -> {
                hour.format24Hour = f24
                hour.format12Hour = f12
            }
        }
        val skeleton = when (cp.dateFormat) {
            1 -> "ddMM"
            2 -> "EEEE"
            3 -> "EEEdMMMyyyy"
            else -> "EEEEdMMMM"
        }
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        date.format12Hour = pattern
        date.format24Hour = pattern

        // alinhamento: do texto dentro do bloco e do bloco na tela
        val g = when (cp.align) {
            1 -> Gravity.CENTER_HORIZONTAL
            2 -> Gravity.END
            else -> Gravity.START
        }
        box.gravity = g
        val lp = frame.layoutParams
        if (lp is LinearLayout.LayoutParams && lp.gravity != g) {
            lp.gravity = g
            frame.layoutParams = lp
        }
        box.alpha = cp.opacity / 100f

        // perspectiva livre (editor): deslocamento dos 4 cantos
        frame.setOffsets(liveQuad ?: cp.quad())

        // deixa o relogio sair dos limites sem ser cortado pelos pais
        var p = frame.parent
        var n = 0
        while (p is ViewGroup && n < 4) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
            n++
        }
    }

    private fun style(
        tv: OutlineTextClock, cp: ClockPrefs, face: Typeface, sizeSp: Int, color: Int,
        strokePx: Float, pad: Int, shadowR: Float, shadowDy: Float
    ) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        tv.setTextColor(color)
        tv.typeface = face
        tv.letterSpacing = cp.letter / 100f
        tv.textScaleX = cp.width / 100f
        tv.paint.textSkewX = cp.skew / 100f
        tv.strokeWidthPx = strokePx
        tv.strokeColor = cp.strokeColor
        tv.setPadding(pad, pad, pad, pad)
        if (cp.shadow) tv.setShadowLayer(shadowR, 0f, shadowDy, 0x66000000) else tv.setShadowLayer(0f, 0f, 0f, 0)
        tv.invalidate()
    }
}
