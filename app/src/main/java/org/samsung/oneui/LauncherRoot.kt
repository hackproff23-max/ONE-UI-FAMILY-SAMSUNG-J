package org.samsung.oneui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Raiz da tela. Tem a tela inicial e a gaveta de apps empilhadas; arrastar para cima na tela inicial
 * puxa a gaveta (segue o dedo), arrastar para baixo com a lista no topo fecha.
 */
class LauncherRoot @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var drawerView: View? = null
    var homeView: View? = null
    var drawerScroller: RecyclerView? = null

    /** Chamado quando o destino muda (true = abrindo, false = fechando). */
    var onStateChanged: ((Boolean) -> Unit)? = null

    /** Chamado quando a gaveta termina de fechar e some. */
    var onClosed: (() -> Unit)? = null

    var isDrawerOpen = false
        private set

    private var progress = 0f
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var originY = 0f
    private var originProgress = 0f
    private var openAtStart = false
    private var velocity: VelocityTracker? = null
    private var animator: ValueAnimator? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 8

    private val animating: Boolean get() = animator?.isRunning == true

    fun openDrawer() = animateTo(true)

    fun closeDrawer(animate: Boolean = true) = animateTo(false, animate)

    private fun setOpenFlag(open: Boolean) {
        if (isDrawerOpen != open) {
            isDrawerOpen = open
            onStateChanged?.invoke(open)
        }
    }

    private fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        val d = drawerView ?: return
        val home = homeView
        if (progress <= 0f) {
            if (d.visibility != View.GONE) {
                d.visibility = View.GONE
                onClosed?.invoke()
            }
            d.translationY = 0f
            home?.alpha = 1f
            home?.scaleX = 1f
            home?.scaleY = 1f
            return
        }
        if (d.visibility != View.VISIBLE) d.visibility = View.VISIBLE
        d.translationY = (1f - progress) * height
        d.alpha = (progress * 1.6f).coerceAtMost(1f)
        if (home != null) {
            val s = 1f - 0.06f * progress
            home.alpha = 1f - 0.8f * progress
            home.scaleX = s
            home.scaleY = s
        }
    }

    private fun animateTo(open: Boolean, animate: Boolean = true) {
        animator?.cancel()
        animator = null
        setOpenFlag(open)
        val end = if (open) 1f else 0f
        if (!animate || abs(end - progress) < 0.001f) {
            setProgress(end)
            return
        }
        if (open && progress <= 0f) setProgress(0.001f)
        animator = ValueAnimator.ofFloat(progress, end).apply {
            duration = (120 + 200 * abs(end - progress)).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { setProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun beginTracking(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        dragging = false
        velocity?.recycle()
        velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    private fun endTracking() {
        velocity?.recycle()
        velocity = null
        dragging = false
    }

    /** Decide se o movimento atual e um arrasto vertical valido para abrir/fechar a gaveta. */
    private fun maybeStartDrag(ev: MotionEvent): Boolean {
        if (dragging) return true
        val dy = ev.y - downY
        val dx = ev.x - downX
        if (abs(dy) <= slop || abs(dy) <= abs(dx) * 1.5f) return false
        val start = when {
            !isDrawerOpen && dy < 0 -> true
            isDrawerOpen && dy > 0 && drawerScroller?.canScrollVertically(-1) != true -> true
            else -> false
        }
        if (!start) return false
        dragging = true
        openAtStart = isDrawerOpen
        originY = ev.y
        originProgress = progress
        if (progress <= 0f) setProgress(0.001f)
        return true
    }

    private fun updateDrag(y: Float) {
        if (height <= 0) return
        setProgress(originProgress - (y - originY) / height.toFloat())
    }

    private fun finishDrag() {
        val vt = velocity
        vt?.computeCurrentVelocity(1000)
        val vy = vt?.yVelocity ?: 0f
        dragging = false
        val target = when {
            vy < -flingVelocity -> true
            vy > flingVelocity -> false
            else -> if (openAtStart) progress > 0.65f else progress > 0.35f
        }
        animateTo(target)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (animating) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTracking(ev)
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                if (maybeStartDrag(ev)) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endTracking()
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (animating) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTracking(ev)
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                if (maybeStartDrag(ev)) updateDrag(ev.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(ev)
                if (dragging) finishDrag()
                endTracking()
            }
        }
        return true
    }
}
