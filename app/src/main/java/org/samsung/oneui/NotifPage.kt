package org.samsung.oneui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date

/**
 * Terceira pagina do cartao da central de controle: as notificacoes dos outros apps.
 * Toque abre a notificacao, o x tira da lista e "Limpar tudo" limpa as que podem ser limpas.
 */
class NotifPage(ctx: Context, private val onOpen: () -> Unit) : LinearLayout(ctx) {
    private val d = resources.displayMetrics.density
    private val scroll = ScrollView(ctx)
    private val list = LinearLayout(ctx)
    private val empty = TextView(ctx)
    private val loc = IntArray(2)

    private fun dp(v: Int): Int = (v * d + 0.5f).toInt()

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(6))

        val head = FrameLayout(ctx)
        val title = TextView(ctx)
        title.text = "Notificações"
        title.textSize = 15f
        title.setTextColor(0xFF111111.toInt())
        title.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        head.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        val clear = TextView(ctx)
        clear.text = "Limpar tudo"
        clear.textSize = 13f
        clear.setTextColor(0xFF0A78D6.toInt())
        clear.setPadding(dp(8), dp(6), dp(4), dp(6))
        clear.setOnClickListener { NotifService.dismissAll() }
        head.addView(clear, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        addView(head, LayoutParams(-1, dp(32)))

        list.orientation = VERTICAL
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(list, ViewGroup.LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, 0, 1f))

        empty.text = "Nenhuma notificação"
        empty.textSize = 13f
        empty.setTextColor(0xFF777777.toInt())
        empty.gravity = Gravity.CENTER
    }

    /** True se o dedo esta sobre a lista e ela ainda rola para baixo (arrastar para cima rola a lista, nao fecha o painel). */
    fun wantsScroll(rawX: Float, rawY: Float): Boolean {
        scroll.getLocationOnScreen(loc)
        val inside = rawX >= loc[0] && rawX <= loc[0] + scroll.width && rawY >= loc[1] && rawY <= loc[1] + scroll.height
        return inside && scroll.canScrollVertically(1)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotifState.onItems = { render() }
        render()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        NotifState.onItems = null
    }

    private fun render() {
        list.removeAllViews()
        val items = NotifState.items
        if (items.isEmpty()) {
            val h = if (scroll.height > 0) scroll.height else dp(160)
            list.addView(empty, LayoutParams(-1, h))
            return
        }
        for (it in items) list.addView(row(it), LayoutParams(-1, -2))
    }

    private fun copyOf(dr: Drawable?): Drawable? = dr?.constantState?.newDrawable()?.mutate()

    private fun row(it: NotifItem): View {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(7), 0, dp(7))

        // logo do app com o tema e o vidro do launcher (ou, sem ele, silhueta sobre um circulo colorido)
        val iconBox = FrameLayout(context)
        val img = ImageView(context)
        val logo = copyOf(it.logo)
        val small = copyOf(it.smallIcon)
        if (logo != null) {
            img.setImageDrawable(logo)
        } else if (small != null) {
            small.setTint(Color.WHITE)
            img.setImageDrawable(small)
            val pad = dp(8)
            img.setPadding(pad, pad, pad, pad)
            iconBox.background = GradientDrawable().also { g ->
                g.shape = GradientDrawable.OVAL
                g.setColor(if (it.color != 0) it.color else 0xFF8A8F98.toInt())
            }
        }
        iconBox.addView(img, FrameLayout.LayoutParams(-1, -1))
        row.addView(iconBox, LayoutParams(dp(40), dp(40)))

        val col = LinearLayout(context)
        col.orientation = VERTICAL
        val meta = TextView(context)
        meta.text = it.app + " · " + DateFormat.getTimeFormat(context).format(Date(it.time))
        meta.textSize = 11f
        meta.setTextColor(0xFF777777.toInt())
        meta.maxLines = 1
        meta.ellipsize = TextUtils.TruncateAt.END
        col.addView(meta)
        if (it.title.isNotBlank()) {
            val t = TextView(context)
            t.text = it.title
            t.textSize = 14f
            t.setTextColor(0xFF111111.toInt())
            t.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            col.addView(t)
        }
        if (it.text.isNotBlank()) {
            val b = TextView(context)
            b.text = it.text
            b.textSize = 13f
            b.setTextColor(0xFF444444.toInt())
            b.maxLines = 2
            b.ellipsize = TextUtils.TruncateAt.END
            col.addView(b)
        }
        val cl = LayoutParams(0, -2, 1f)
        cl.leftMargin = dp(10)
        row.addView(col, cl)

        if (it.clearable) {
            val x = TextView(context)
            x.text = "×"
            x.textSize = 22f
            x.gravity = Gravity.CENTER
            x.setTextColor(0xFF888888.toInt())
            x.setOnClickListener { _ -> NotifService.dismiss(it.key) }
            row.addView(x, LayoutParams(dp(36), dp(36)))
        }

        row.isClickable = true
        row.setOnClickListener { _ -> open(it) }
        return row
    }

    private fun open(it: NotifItem) {
        try {
            it.intent?.send()
        } catch (e: Exception) {
        }
        if (it.autoCancel) NotifService.dismiss(it.key)
        onOpen()
    }
}
