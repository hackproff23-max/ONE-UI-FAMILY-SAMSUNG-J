package org.samsung.oneui

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Coloca a barra de status do launcher por cima da barra do sistema em todos os apps.
 * So uma janela de acessibilidade consegue ficar acima da barra do sistema e segurar o toque
 * dela, entao a barra do sistema deixa de abrir as notificacoes.
 * Para desfazer: desligue este servico em Configuracoes > Acessibilidade.
 */
class BarService : AccessibilityService() {
    companion object {
        @Volatile
        var running = false
    }

    private var bar: StatusBar.Bar? = null
    private var wm: WindowManager? = null

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
        val b = bar ?: return
        try {
            wm?.removeView(b)
        } catch (e: Exception) {
        }
        bar = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "android" || pkg.contains("systemui")) return
        // no launcher o fundo fica transparente (papel de parede); nos outros apps, preto
        bar?.solid = pkg != packageName
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
