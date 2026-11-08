package org.samsung.oneui

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * Coloca a barra de status do launcher por cima da barra do sistema em todos os apps.
 * So uma janela de acessibilidade consegue ficar acima da barra do sistema e segurar o toque
 * dela, entao a barra do sistema deixa de abrir as notificacoes. Em troca, puxar esta barra
 * para baixo abre a central de controle (QuickPanel).
 * Para desfazer: desligue este servico em Configuracoes > Acessibilidade.
 */
class BarService : AccessibilityService() {
    companion object {
        @Volatile
        var running = false
    }

    private var bar: StatusBar.Bar? = null
    private var wm: WindowManager? = null
    private var panel: QuickPanel? = null

    /** Fundo da barra conforme o app da frente (transparente no launcher, preto nos outros). */
    private var baseSolid = true

    // gesto de puxar a barra
    private var downX = 0f
    private var downY = 0f
    private var pulling = false
    private var baseProgress = 0f
    private var tracker: VelocityTracker? = null
    private val slop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        addBar()
    }

    private fun addBar() {
        if (bar != null) return
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = StatusBar.Bar(this, null)
        b.blockTouches = true
        b.solid = true
        b.setOnTouchListener { _, e ->
            onBarTouch(e)
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            StatusBar.overlayHeightPx(this),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        try {
            w.addView(b, lp)
            bar = b
            wm = w
        } catch (e: Exception) {
        }
    }

    private fun removeBar() {
        panel?.let { removePanel(it) }
        val b = bar ?: return
        try {
            wm?.removeView(b)
        } catch (e: Exception) {
        }
        bar = null
    }

    // ---------- central de controle ----------

    /** Cria a janela do painel (logo abaixo da barra) se ainda nao existir. */
    private fun ensurePanel(): QuickPanel? {
        panel?.let { return it }
        val w = wm ?: return null
        val p = QuickPanel(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.y = StatusBar.overlayHeightPx(this)
        try {
            w.addView(p, lp)
        } catch (e: Exception) {
            return null
        }
        panel = p
        bar?.solid = true // combina com o fundo escuro do painel
        return p
    }

    fun removePanel(p: QuickPanel) {
        if (panel !== p) return
        try {
            wm?.removeView(p)
        } catch (e: Exception) {
        }
        panel = null
        bar?.solid = baseSolid
    }

    fun screenshot() {
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun onBarTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                pulling = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dy = e.rawY - downY
                val dx = e.rawX - downX
                if (!pulling && abs(dy) > slop && abs(dy) > abs(dx)) {
                    val existing = panel
                    if (existing != null) {
                        pulling = true
                        baseProgress = existing.progress
                        existing.stopAnimation()
                    } else if (dy > 0) {
                        if (ensurePanel() != null) {
                            pulling = true
                            baseProgress = 0f
                        }
                    }
                }
                if (pulling) panel?.let { it.dragTo(baseProgress + dy / it.sheetHeight) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pulling) {
                    tracker?.computeCurrentVelocity(1000)
                    panel?.settle(tracker?.yVelocity ?: 0f, if (baseProgress > 0.5f) 0.7f else 0.3f)
                }
                pulling = false
                tracker?.recycle()
                tracker = null
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "android" || pkg.contains("systemui")) return
        // no launcher o fundo fica transparente (papel de parede); nos outros apps, preto
        baseSolid = pkg != packageName
        val p = panel
        if (p != null) {
            // trocou de app com o painel aberto: fecha
            if (pkg != packageName) p.closeNow()
            return
        }
        bar?.solid = baseSolid
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        removeBar()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        removeBar()
        super.onDestroy()
    }
}
