package org.samsung.oneui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.TextClock

/** TextClock com contorno (traco) ao redor das letras. */
class OutlineTextClock : TextClock {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    var strokeWidthPx = 0f
    var strokeColor = Color.BLACK
    private var drawing = false

    // setTextColor() chama invalidate(); durante o desenho isso causaria redesenho infinito
    override fun invalidate() {
        if (!drawing) super.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strokeWidthPx <= 0f) {
            super.onDraw(canvas)
            return
        }
        val fill = textColors
        drawing = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidthPx
        paint.strokeJoin = Paint.Join.ROUND
        setTextColor(strokeColor)
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        setTextColor(fill)
        drawing = false
        super.onDraw(canvas)
    }
}
