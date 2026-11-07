package org.samsung.oneui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Menu ao segurar a tela inicial: botoes redondos (preto + borda roxa) com logo e nome. */
object HomeMenu {

    const val ICONS = 0
    const val CONFIGS = 1
    const val PAGINA = 2
    const val WIDGET = 3
    const val RELOGIO = 4
    const val DOCK = 5

    class Item(val kind: Int, val label: String, val action: () -> Unit)

    private const val PURPLE = 0xFF8A00FF.toInt()

    fun show(activity: Activity, items: List<Item>) {
        val d = activity.resources.displayMetrics.density
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val rootView = FrameLayout(activity)
        rootView.setBackgroundColor(0x99000000.toInt())
        rootView.setOnClickListener { dialog.dismiss() }

        val screenW = activity.resources.displayMetrics.widthPixels
        val size = min((screenW * 0.215f).toInt(), (96 * d).toInt())
        val gap = (14 * d).toInt()

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
        }

        items.chunked(3).forEach { rowItems ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (item in rowItems) {
                val btn = MenuButton(activity, item.kind, item.label)
                btn.setOnClickListener {
                    dialog.dismiss()
                    item.action()
                }
                val lp = LinearLayout.LayoutParams(size, size)
                lp.setMargins(gap, gap / 2, gap, gap / 2)
                row.addView(btn, lp)
            }
            panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val plp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        plp.bottomMargin = (40 * d).toInt()
        rootView.addView(panel, plp)

        dialog.setContentView(rootView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        panel.alpha = 0f
        panel.translationY = 60 * d
        dialog.show()
        panel.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private class MenuButton(context: Context, val kind: Int, val label: String) : View(context) {
        private val d = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PURPLE; style = Paint.Style.STROKE; strokeWidth = 3f * d
        }
        private val ic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val icFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PURPLE; style = Paint.Style.FILL }
        private val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PURPLE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeJoin = Paint.Join.ROUND
        }
        private val rect = RectF()

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = w / 2f - ring.strokeWidth / 2f
            c.drawCircle(w / 2f, h / 2f, r, fill)
            c.drawCircle(w / 2f, h / 2f, r, ring)

            drawLogo(c, w / 2f, h * 0.38f, w * 0.15f)

            var ts = w * 0.15f
            textFill.textSize = ts
            val maxW = w * 0.78f
            val tw = textFill.measureText(label)
            if (tw > maxW) ts *= maxW / tw
            textFill.textSize = ts
            textStroke.textSize = ts
            val y = h * 0.80f
            c.drawText(label, w / 2f, y, textStroke)
            c.drawText(label, w / 2f, y, textFill)
        }

        private fun drawLogo(c: Canvas, cx: Float, cy: Float, s: Float) {
            ic.strokeWidth = s * 0.28f
            when (kind) {
                ICONS -> { // 4 quadrados arredondados, um roxo
                    val g = s * 0.12f
                    val q = s - g
                    val rr = s * 0.3f
                    for (i in 0..1) for (j in 0..1) {
                        val l = cx - s + i * (q + g * 2)
                        val t = cy - s + j * (q + g * 2)
                        rect.set(l, t, l + q, t + q)
                        if (i == 1 && j == 0) c.drawRoundRect(rect, rr, rr, accent)
                        else c.drawRoundRect(rect, rr, rr, ic)
                    }
                }
                CONFIGS -> { // engrenagem
                    val rad = s * 0.62f
                    c.drawCircle(cx, cy, rad, ic)
                    for (k in 0 until 8) {
                        val a = Math.toRadians(k * 45.0)
                        val x1 = cx + cos(a).toFloat() * rad
                        val y1 = cy + sin(a).toFloat() * rad
                        val x2 = cx + cos(a).toFloat() * s * 1.08f
                        val y2 = cy + sin(a).toFloat() * s * 1.08f
                        c.drawLine(x1, y1, x2, y2, ic)
                    }
                    c.drawCircle(cx, cy, s * 0.2f, accent)
                }
                PAGINA -> { // duas paginas sobrepostas
                    rect.set(cx - s * 0.55f, cy - s * 0.85f, cx + s * 0.95f, cy + s * 0.65f)
                    c.drawRoundRect(rect, s * 0.25f, s * 0.25f, accent)
                    rect.set(cx - s * 0.95f, cy - s * 0.65f, cx + s * 0.55f, cy + s * 0.85f)
                    c.drawRoundRect(rect, s * 0.25f, s * 0.25f, fill)
                    c.drawRoundRect(rect, s * 0.25f, s * 0.25f, ic)
                    c.drawLine(cx - s * 0.6f, cy - s * 0.15f, cx + s * 0.2f, cy - s * 0.15f, ic)
                    c.drawLine(cx - s * 0.6f, cy + s * 0.3f, cx + s * 0.05f, cy + s * 0.3f, ic)
                }
                WIDGET -> { // widget grande + dois pequenos
                    rect.set(cx - s, cy - s, cx + s, cy + s * 0.1f)
                    c.drawRoundRect(rect, s * 0.3f, s * 0.3f, ic)
                    rect.set(cx - s * 0.7f, cy - s * 0.65f, cx + s * 0.1f, cy - s * 0.3f)
                    c.drawRoundRect(rect, s * 0.1f, s * 0.1f, accent)
                    rect.set(cx - s, cy + s * 0.4f, cx - s * 0.1f, cy + s)
                    c.drawRoundRect(rect, s * 0.25f, s * 0.25f, ic)
                    rect.set(cx + s * 0.1f, cy + s * 0.4f, cx + s, cy + s)
                    c.drawRoundRect(rect, s * 0.25f, s * 0.25f, ic)
                }
                RELOGIO -> {
                    c.drawCircle(cx, cy, s, ic)
                    c.drawLine(cx, cy, cx, cy - s * 0.62f, ic)
                    c.drawLine(cx, cy, cx + s * 0.45f, cy + s * 0.25f, ic)
                    c.drawCircle(cx, cy, s * 0.14f, accent)
                }
                DOCK -> { // app em cima + barra do dock com 4 apps
                    rect.set(cx - s * 0.5f, cy - s * 0.95f, cx + s * 0.5f, cy - s * 0.15f)
                    c.drawRoundRect(rect, s * 0.25f, s * 0.25f, ic)
                    rect.set(cx - s * 1.05f, cy + s * 0.2f, cx + s * 1.05f, cy + s * 0.95f)
                    c.drawRoundRect(rect, s * 0.35f, s * 0.35f, ic)
                    for (k in 0 until 4) {
                        val x = cx - s * 0.66f + k * s * 0.44f
                        c.drawCircle(x, cy + s * 0.575f, s * 0.13f, if (k == 1) accent else icFill)
                    }
                }
            }
        }
    }
}
