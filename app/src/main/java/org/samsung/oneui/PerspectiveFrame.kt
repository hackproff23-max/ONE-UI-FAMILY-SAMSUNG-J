package org.samsung.oneui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewParent
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Desenha o filho (o relogio) deformado: cada canto pode sair do lugar (perspectiva livre).
 * Os deslocamentos ficam em dp, na ordem: topo-esq, topo-dir, baixo-dir, baixo-esq (x, y).
 * Mover, aumentar e girar tambem sao so deslocamentos dos quatro cantos.
 */
class PerspectiveFrame : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private val off = FloatArray(8)
    private val matrix = Matrix()
    private val density = resources.displayMetrics.density

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun offsets(): FloatArray = off.copyOf()

    fun setOffsets(v: FloatArray) {
        for (i in 0 until 8) off[i] = v[i]
        invalidate()
        rootView?.invalidate() // a deformacao sai dos limites desta view
    }

    fun hasWarp(): Boolean = off.any { abs(it) > 0.01f }

    private fun src(c: android.view.View): FloatArray {
        val l = c.left.toFloat()
        val t = c.top.toFloat()
        val r = c.right.toFloat()
        val b = c.bottom.toFloat()
        return floatArrayOf(l, t, r, t, r, b, l, b)
    }

    private fun dst(s: FloatArray): FloatArray = FloatArray(8) { s[it] + off[it] * density }

    private fun warpBounds(): Rect {
        val c = getChildAt(0) ?: return Rect(0, 0, width, height)
        val d = dst(src(c))
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until 4) {
            minX = minOf(minX, d[2 * i])
            maxX = maxOf(maxX, d[2 * i])
            minY = minOf(minY, d[2 * i + 1])
            maxY = maxOf(maxY, d[2 * i + 1])
        }
        return Rect(floor(minX).toInt() - 8, floor(minY).toInt() - 8, ceil(maxX).toInt() + 8, ceil(maxY).toInt() + 8)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun invalidateChildInParent(location: IntArray?, dirty: Rect?): ViewParent? {
        if (hasWarp() && dirty != null) dirty.union(warpBounds())
        return super.invalidateChildInParent(location, dirty)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val c = getChildAt(0)
        if (c == null || c.width <= 0 || c.height <= 0 || !hasWarp()) {
            super.dispatchDraw(canvas)
            return
        }
        val s = src(c)
        val ok = matrix.setPolyToPoly(s, 0, dst(s), 0, 4)
        if (!ok) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.concat(matrix)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
