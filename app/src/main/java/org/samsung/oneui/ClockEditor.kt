package org.samsung.oneui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Editor livre do relogio, como num editor de fotos: arrastar move, os cantos aumentam,
 * a alca da direita gira, e no modo Perspectiva cada canto se move sozinho (deformacao).
 */
object ClockEditor {
    private const val TAG = "clock_editor"

    fun start(a: Activity) {
        val decor = a.window.decorView as ViewGroup
        if (decor.findViewWithTag<View>(TAG) != null) return
        a.findViewById<LauncherRoot>(R.id.root)?.closeDrawer(animate = false)
        val frame = a.findViewById<PerspectiveFrame>(R.id.clockFrame)
        val box = frame?.getChildAt(0)
        if (frame == null || box == null || box.width <= 0 || box.height <= 0) {
            Toast.makeText(a, "O relógio está escondido. Ative hora ou data no Estilo.", Toast.LENGTH_SHORT).show()
            return
        }
        Session(a, decor, frame).open()
    }

    private class Session(val a: Activity, val decor: ViewGroup, val frame: PerspectiveFrame) {
        val d = a.resources.displayMetrics.density
        val cp = ClockPrefs(a)
        var perspective = false

        private lateinit var holder: FrameLayout
        private lateinit var edit: EditView
        private lateinit var btnTransform: TextView
        private lateinit var btnPersp: TextView

        fun dp(v: Float): Float = v * d
        fun dpi(v: Int): Int = (v * d + 0.5f).toInt()

        private fun sysDimen(name: String): Int {
            val id = a.resources.getIdentifier(name, "dimen", "android")
            return if (id > 0) a.resources.getDimensionPixelSize(id) else 0
        }

        fun open() {
            ClockStyle.liveQuad = frame.offsets()
            holder = FrameLayout(a)
            holder.tag = TAG
            edit = EditView(a)
            holder.addView(edit, FrameLayout.LayoutParams(-1, -1))
            holder.addView(topBar(), FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            holder.addView(bottomBar(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            decor.addView(holder, ViewGroup.LayoutParams(-1, -1))
            setMode(false)
        }

        private fun close() {
            decor.removeView(holder)
        }

        private fun cancel() {
            ClockStyle.liveQuad = null
            ClockStyle.apply(a)
            close()
        }

        private fun confirm() {
            cp.setQuad(frame.offsets())
            ClockStyle.liveQuad = null
            ClockStyle.apply(a)
            close()
        }

        private fun reset() {
            val z = FloatArray(8)
            ClockStyle.liveQuad = z
            frame.setOffsets(z)
            edit.invalidate()
        }

        private fun setMode(p: Boolean) {
            perspective = p
            btnTransform.background = pillBg(!p)
            btnPersp.background = pillBg(p)
            edit.invalidate()
        }

        // ---------- barras (estilo escuro do editor de fotos) ----------

        private fun pillBg(selected: Boolean): GradientDrawable = GradientDrawable().apply {
            setColor(0xFF2A2A2A.toInt())
            cornerRadius = dp(6f)
            setStroke(dpi(1), if (selected) 0xFF4F8DFF.toInt() else 0xFF707070.toInt())
        }

        private fun pill(text: String, onClick: () -> Unit): TextView {
            val t = TextView(a)
            t.text = text
            t.setTextColor(Color.WHITE)
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding(dpi(4), dpi(14), dpi(4), dpi(14))
            t.background = pillBg(false)
            t.setOnClickListener { onClick() }
            return t
        }

        private fun topBar(): View {
            val bar = LinearLayout(a)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.gravity = Gravity.CENTER_VERTICAL
            bar.setBackgroundColor(0xFF1A1A1A.toInt())
            bar.setPadding(dpi(4), sysDimen("status_bar_height"), dpi(4), 0)
            val size = dpi(52)
            bar.addView(BarIcon(a, BarIcon.BACK, Color.WHITE).also { it.setOnClickListener { cancel() } }, LinearLayout.LayoutParams(size, size))
            bar.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            bar.addView(BarIcon(a, BarIcon.CHECK, 0xFF6FA8FF.toInt()).also { it.setOnClickListener { confirm() } }, LinearLayout.LayoutParams(size, size))
            bar.addView(BarIcon(a, BarIcon.DOTS, Color.WHITE).also { it.setOnClickListener { ClockSettings.show(a) } }, LinearLayout.LayoutParams(size, size))
            return bar
        }

        private fun bottomBar(): View {
            val bar = LinearLayout(a)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.setBackgroundColor(0xFF101010.toInt())
            bar.setPadding(dpi(4), dpi(4), dpi(4), dpi(4) + sysDimen("navigation_bar_height"))
            btnTransform = pill("Transformar") { setMode(false) }
            btnPersp = pill("Perspectiva") { setMode(true) }
            val style = pill("Estilo") { ClockSettings.show(a) }
            val reset = pill("Resetar") { reset() }
            for (b in listOf(btnTransform, btnPersp, style, reset)) {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dpi(2), 0, dpi(2), 0)
                bar.addView(b, lp)
            }
            return bar
        }

        // ---------- area de edicao ----------

        private inner class EditView(ctx: Context) : View(ctx) {
            private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = dp(1.5f)
                pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
            }
            private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0x88000000.toInt()
                strokeWidth = dp(3.5f)
            }
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0xFF3A3A3A.toInt()
                strokeWidth = dp(2f)
                strokeCap = Paint.Cap.ROUND
            }

            private val NONE = 0
            private val MOVE = 1
            private val CORNER = 2
            private val ROT = 3
            private var mode = NONE
            private var corner = -1
            private var sx = 0f
            private var sy = 0f
            private var startQ = FloatArray(8)

            private fun base(): FloatArray {
                val loc = IntArray(2)
                frame.getLocationOnScreen(loc)
                val me = IntArray(2)
                getLocationOnScreen(me)
                val c = frame.getChildAt(0)
                val l = (loc[0] - me[0] + c.left).toFloat()
                val t = (loc[1] - me[1] + c.top).toFloat()
                val r = l + c.width
                val b = t + c.height
                return floatArrayOf(l, t, r, t, r, b, l, b)
            }

            private fun quad(): FloatArray {
                val b = base()
                val o = frame.offsets()
                return FloatArray(8) { b[it] + o[it] * d }
            }

            private fun commit(q: FloatArray) {
                val b = base()
                val o = FloatArray(8) { (q[it] - b[it]) / d }
                ClockStyle.liveQuad = o
                frame.setOffsets(o)
                invalidate()
            }

            private fun center(q: FloatArray) = floatArrayOf(
                (q[0] + q[2] + q[4] + q[6]) / 4f,
                (q[1] + q[3] + q[5] + q[7]) / 4f
            )

            /** [x, y] da alca de giro e [x, y] do meio da borda direita. */
            private fun rotHandle(q: FloatArray): FloatArray {
                val c = center(q)
                val mx = (q[2] + q[4]) / 2f
                val my = (q[3] + q[5]) / 2f
                val dx = mx - c[0]
                val dy = my - c[1]
                val len = hypot(dx, dy).coerceAtLeast(1f)
                return floatArrayOf(mx + dx / len * dp(56f), my + dy / len * dp(56f), mx, my)
            }

            private fun inside(q: FloatArray, x: Float, y: Float): Boolean {
                var odd = false
                var j = 3
                for (i in 0 until 4) {
                    val xi = q[2 * i]
                    val yi = q[2 * i + 1]
                    val xj = q[2 * j]
                    val yj = q[2 * j + 1]
                    if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) odd = !odd
                    j = i
                }
                return odd
            }

            override fun onDraw(canvas: Canvas) {
                val q = quad()
                val path = Path()
                path.moveTo(q[0], q[1])
                for (i in 1 until 4) path.lineTo(q[2 * i], q[2 * i + 1])
                path.close()
                canvas.drawPath(path, shade)
                canvas.drawPath(path, dash)

                val rh = rotHandle(q)
                canvas.drawLine(rh[2], rh[3], rh[0], rh[1], shade)
                canvas.drawLine(rh[2], rh[3], rh[0], rh[1], dash)

                // cantos
                fill.color = if (perspective) 0xCC8FB8FF.toInt() else 0xCCD6DBE0.toInt()
                for (i in 0 until 4) canvas.drawCircle(q[2 * i], q[2 * i + 1], dp(15f), fill)

                // alca de giro (seta circular)
                fill.color = 0xDDE0E4E8.toInt()
                canvas.drawCircle(rh[0], rh[1], dp(19f), fill)
                val r = dp(8f)
                canvas.drawArc(RectF(rh[0] - r, rh[1] - r, rh[0] + r, rh[1] + r), -40f, 270f, false, ring)
                val ang = Math.toRadians(230.0)
                val ex = rh[0] + r * cos(ang).toFloat()
                val ey = rh[1] + r * sin(ang).toFloat()
                val tx = -sin(ang).toFloat()
                val ty = cos(ang).toFloat()
                val nx = cos(ang).toFloat()
                val ny = sin(ang).toFloat()
                val head = Path()
                head.moveTo(ex + tx * dp(6f), ey + ty * dp(6f))
                head.lineTo(ex - tx * dp(2f) + nx * dp(5f), ey - ty * dp(2f) + ny * dp(5f))
                head.lineTo(ex - tx * dp(2f) - nx * dp(5f), ey - ty * dp(2f) - ny * dp(5f))
                head.close()
                fill.color = 0xFF3A3A3A.toInt()
                canvas.drawPath(head, fill)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startQ = quad()
                        sx = x
                        sy = y
                        val rh = rotHandle(startQ)
                        mode = NONE
                        if (hypot(x - rh[0], y - rh[1]) < dp(34f)) {
                            mode = ROT
                        } else {
                            var best = -1
                            var bd = dp(34f)
                            for (i in 0 until 4) {
                                val dd = hypot(x - startQ[2 * i], y - startQ[2 * i + 1])
                                if (dd < bd) {
                                    bd = dd
                                    best = i
                                }
                            }
                            if (best >= 0) {
                                mode = CORNER
                                corner = best
                            } else if (inside(startQ, x, y)) {
                                mode = MOVE
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = x - sx
                        val dy = y - sy
                        val c0 = center(startQ)
                        when (mode) {
                            MOVE -> commit(FloatArray(8) { startQ[it] + if (it % 2 == 0) dx else dy })
                            CORNER -> if (perspective) {
                                val q = startQ.copyOf()
                                q[2 * corner] += dx
                                q[2 * corner + 1] += dy
                                commit(q)
                            } else {
                                val s = (hypot(x - c0[0], y - c0[1]) / hypot(sx - c0[0], sy - c0[1]).coerceAtLeast(1f))
                                    .coerceIn(0.2f, 6f)
                                commit(FloatArray(8) { c0[it % 2] + (startQ[it] - c0[it % 2]) * s })
                            }
                            ROT -> {
                                val da = atan2(y - c0[1], x - c0[0]) - atan2(sy - c0[1], sx - c0[0])
                                val cs = cos(da)
                                val sn = sin(da)
                                val q = FloatArray(8)
                                for (i in 0 until 4) {
                                    val px = startQ[2 * i] - c0[0]
                                    val py = startQ[2 * i + 1] - c0[1]
                                    q[2 * i] = c0[0] + px * cs - py * sn
                                    q[2 * i + 1] = c0[1] + px * sn + py * cs
                                }
                                commit(q)
                            }
                            else -> {}
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = NONE
                }
                return true // consome tudo: nada embaixo reage durante a edicao
            }
        }
    }

    /** Icones simples da barra de cima: voltar, confirmar e tres pontinhos. */
    private class BarIcon(ctx: Context, val kind: Int, val color: Int) : View(ctx) {
        companion object {
            const val BACK = 0
            const val CHECK = 1
            const val DOTS = 2
        }

        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * ctx.resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        init {
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        override fun onDraw(canvas: Canvas) {
            p.color = color
            val w = width.toFloat()
            val h = height.toFloat()
            val u = w / 52f
            when (kind) {
                BACK -> {
                    p.style = Paint.Style.STROKE
                    canvas.drawLine(14 * u, h / 2, 38 * u, h / 2, p)
                    canvas.drawLine(14 * u, h / 2, 24 * u, h / 2 - 10 * u, p)
                    canvas.drawLine(14 * u, h / 2, 24 * u, h / 2 + 10 * u, p)
                }
                CHECK -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 3.2f * u * 2f
                    val path = Path()
                    path.moveTo(12 * u, h / 2)
                    path.lineTo(22 * u, h / 2 + 10 * u)
                    path.lineTo(40 * u, h / 2 - 10 * u)
                    canvas.drawPath(path, p)
                }
                else -> {
                    p.style = Paint.Style.FILL
                    for (i in -1..1) canvas.drawCircle(w / 2, h / 2 + i * 9 * u, 2.6f * u * 1.2f, p)
                }
            }
        }
    }
}
