package org.samsung.oneui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.app.usage.NetworkStatsManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.PathParser
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Cores e icones (viewport 24x24, estilo Material) da central de controle. */
private object Qp {
    val BLUE = 0xFF0A78D6.toInt()
    val OFF = 0xFFE4E4E4.toInt()

    const val DATA = "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3V14h2V6.99h3L9,3z"
    const val WIFI = "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z"
    const val BT = "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z"
    const val PLANE = "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z"
    const val BELL = "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
    const val FLASH = "M8,2h8v4l-2,3v13h-4V9L8,6V2z"
    const val SHOT = "M3,5v4h2V5h4V3H5C3.9,3 3,3.9 3,5zM5,15H3v4c0,1.1 0.9,2 2,2h4v-2H5v-4zM19,19h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4zM19,3h-4v2h4v4h2V5c0,-1.1 -0.9,-2 -2,-2z"
    const val SAVER = "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4zM13,16h-2v-2H9v-2h2v-2h2v2h2v2h-2v2z"
    const val MOON = "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z"
    const val ROTATE = "M16.48,2.52c3.27,1.55 5.61,4.72 5.97,8.48h1.5C23.44,4.84 18.29,0 12,0l-0.66,0.03 3.81,3.81 1.33,-1.32zM10.23,1.75c-0.59,-0.59 -1.54,-0.59 -2.12,0L1.75,8.11c-0.59,0.59 -0.59,1.54 0,2.12l12.02,12.02c0.59,0.59 1.54,0.59 2.12,0l6.36,-6.36c0.59,-0.59 0.59,-1.54 0,-2.12L10.23,1.75zM14.94,21.12L2.88,9.06l6.36,-6.36 12.06,12.06 -6.36,6.36zM7.52,21.48C4.25,19.94 1.91,16.76 1.55,13H0.05C0.56,19.16 5.71,24 12,24l0.66,-0.03 -3.81,-3.81 -1.33,1.32z"
    const val HOME = "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"
    const val LINK = "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"
    const val LOC = "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z"
    const val DND = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM17,13H7v-2h10v2z"
    const val CAST = "M21,3H3c-1.1,0 -2,0.9 -2,2v3h2V5h18v14h-7v2h7c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM1,18v3h3c0,-1.66 -1.34,-3 -3,-3zM1,14v2c2.76,0 5,2.24 5,5h2c0,-3.87 -3.13,-7 -7,-7zM1,10v2c4.97,0 9,4.03 9,9h2c0,-6.08 -4.93,-11 -11,-11z"
    const val ALARM = "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"
    const val GEAR = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
    const val SUN = "M20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69L12,23.31 15.31,20H20v-4.69L23.31,12 20,8.69zM12,18c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6 6,2.69 6,6 -2.69,6 -6,6zM12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z"

    fun parse(s: String): Path? = try {
        PathParser.createPathFromPathData(s)?.also { it.fillType = Path.FillType.EVEN_ODD }
    } catch (e: Exception) {
        null
    }
}

private class Tile(
    val label: () -> String,
    val icon: String,
    val on: () -> Boolean,
    val tap: () -> Unit,
    val hold: (() -> Unit)? = null
)

/** Botao redondo do painel: fundo azul quando ligado, cinza claro quando desligado. */
private class Circle(c: Context, iconPath: String) : View(c) {
    var on = false
        set(v) {
            field = v
            invalidate()
        }
    private val path: Path? = Qp.parse(iconPath)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = if (on) Qp.BLUE else Qp.OFF
        canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) / 2f, paint)
        val p = path ?: return
        val size = minOf(w, h) * 0.52f
        val s = size / 24f
        canvas.save()
        canvas.translate((w - size) / 2f, (h - size) / 2f)
        canvas.scale(s, s)
        paint.color = if (on) Color.WHITE else 0xFF4A4A4A.toInt()
        canvas.drawPath(p, paint)
        canvas.restore()
    }
}

/** Engrenagem (abre as configuracoes). */
private class GearView(c: Context) : View(c) {
    private val path: Path? = Qp.parse(Qp.GEAR)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF222222.toInt()
    }

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        val p = path ?: return
        val s = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        canvas.drawPath(p, paint)
        canvas.restore()
    }
}

/** Barra de brilho: trilho cinza claro com preenchimento escuro e sol na ponta. */
private class Slider(c: Context, private val onValue: (Float) -> Unit) : View(c) {
    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDEDEDE.toInt() }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4D4D4D.toInt() }
    private val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sun: Path? = Qp.parse(Qp.SUN)
    private val clip = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(r, h / 2f, h / 2f, track)
        clip.reset()
        clip.addRoundRect(r, h / 2f, h / 2f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(0f, 0f, w * value, h, fillP)
        canvas.restore()
        val p = sun ?: return
        val size = 22f * d
        val s = size / 24f
        canvas.save()
        canvas.translate(16f * d, (h - size) / 2f)
        canvas.scale(s, s)
        iconP.color = if (w * value > 40f * d) Color.WHITE else 0xFF666666.toInt()
        canvas.drawPath(p, iconP)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
        }
        return true
    }

    private fun update(x: Float) {
        if (width <= 0) return
        value = x / width
        onValue(value)
    }
}

/** Duas paginas de botoes: arrasta de lado e encaixa na pagina mais proxima. */
private class Pager(c: Context) : HorizontalScrollView(c) {
    var pageW = 0
    var onPage: ((Int) -> Unit)? = null
    private var page = 0

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (pageW <= 0) return
        val p = (l + pageW / 2) / pageW
        if (p != page) {
            page = p
            onPage?.invoke(p)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val r = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (pageW > 0) {
                val p = (scrollX + pageW / 2) / pageW
                post { smoothScrollTo(p * pageW, 0) }
            }
            return true
        }
        return r
    }
}

/** Icones de estado (bluetooth, sinal, wifi, bateria) da linha da operadora. */
private class IconRow(c: Context) : View(c) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val wifiP: Path? = Qp.parse(Qp.WIFI)
    private val btP: Path? = Qp.parse(Qp.BT)
    private var battery = -1
    private var charging = false
    private var saver = false
    private var wifi = false
    private var bt = false
    private var cell = 0
    private var sim = true
    private var airplane = false

    @Suppress("DEPRECATION")
    private val phone = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            cell = signalStrength?.level ?: 0
            invalidate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .listen(phone, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .listen(phone, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    fun refreshState() {
        val c = context
        try {
            val bi = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (bi != null) {
                val lv = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
                val st = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (st == BatteryManager.BATTERY_STATUS_FULL && bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0)
            }
            saver = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
            airplane = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            sim = (c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simState ==
                TelephonyManager.SIM_STATE_READY
        } catch (e: Exception) {
        }
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
        }
        try {
            bt = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) {
            bt = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        var x = width.toFloat()
        if (battery >= 0) {
            x -= 30f * d
            drawBattery(canvas, x, cy)
            x -= 8f * d
        }
        if (wifi) {
            x -= 18f * d
            glyph(canvas, wifiP, x, cy, 18f * d)
            x -= 8f * d
        }
        if (!airplane && sim) {
            x -= 18f * d
            drawBars(canvas, x, cy)
            x -= 8f * d
        }
        if (bt) {
            x -= 14f * d
            glyph(canvas, btP, x, cy, 14f * d)
        }
    }

    private fun glyph(c: Canvas, p: Path?, x: Float, cy: Float, size: Float) {
        if (p == null) return
        val s = size / 24f
        c.save()
        c.translate(x, cy - size / 2f)
        c.scale(s, s)
        fill.color = 0xFF222222.toInt()
        c.drawPath(p, fill)
        c.restore()
    }

    private fun drawBars(c: Canvas, x: Float, cy: Float) {
        val bw = 3.2f * d
        val g = 1.6f * d
        for (i in 0 until 4) {
            val bh = (4 + i * 3) * d
            val left = x + i * (bw + g)
            fill.color = if (i < cell) 0xFF222222.toInt() else 0x33000000
            c.drawRoundRect(RectF(left, cy + 6.5f * d - bh, left + bw, cy + 6.5f * d), d, d, fill)
        }
    }

    private fun drawBattery(c: Canvas, x: Float, cy: Float) {
        val bw = 30f * d
        val bh = 15f * d
        val r = RectF(x, cy - bh / 2f, x + bw, cy + bh / 2f)
        val rad = bh * 0.34f
        fill.color = 0xFFDADADA.toInt()
        c.drawRoundRect(r, rad, rad, fill)
        val col = when {
            charging -> 0xFF30D158.toInt()
            saver -> 0xFFFFD60A.toInt()
            battery <= 20 -> 0xFFFF453A.toInt()
            else -> 0xFFB5B5B5.toInt()
        }
        c.save()
        c.clipRect(x, r.top, x + bw * battery / 100f, r.bottom)
        fill.color = col
        c.drawRoundRect(r, rad, rad, fill)
        c.restore()
        num.textSize = (if (battery >= 100) 8.5f else 10.5f) * resources.displayMetrics.scaledDensity
        val fm = num.fontMetrics
        c.drawText(battery.toString(), r.centerX(), cy - (fm.ascent + fm.descent) / 2f, num)
    }
}

/**
 * Central de controle (configuracoes rapidas). Desce ao puxar a barra de status e tem os botoes
 * de rede, som, lanterna, captura de tela, brilho etc. Wifi, dados, bluetooth, aviao, economia de
 * bateria, modo escuro e localizacao ligam/desligam direto pelo Shizuku; sem ele, abrem a tela
 * certa das configuracoes (segurar o botao sempre abre as configuracoes).
 */
class QuickPanel(private val svc: BarService) : FrameLayout(svc) {
    private val d = resources.displayMetrics.density
    private val ui = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(svc).scaledTouchSlop
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private val sheet = LinearLayout(svc)
    private val clockText = TextView(svc)
    private val dateText = TextView(svc)
    private val carrierText = TextView(svc)
    private val iconRow = IconRow(svc)
    private val pager = Pager(svc)
    private val usageToday = TextView(svc)
    private val usageMonth = TextView(svc)
    private val usageRow = LinearLayout(svc)
    private val dots = ArrayList<View>()
    private val cells = ArrayList<Cell>()
    private val slider: Slider

    private var sheetH = 0
    private var anim: ValueAnimator? = null
    private var askedWrite = false
    private var usageOk = true
    private var notifPage: NotifPage? = null

    /** 0 = fechado, 1 = totalmente aberto. */
    var progress = 0f
        private set

    val sheetHeight: Int get() = if (sheet.height > 0) sheet.height else sheetH

    // lanterna
    private val cam: CameraManager? = try {
        svc.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    } catch (e: Exception) {
        null
    }
    private var torchId: String? = null
    private var torchOn = false
    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                refreshTiles()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 3000)
        }
    }

    // gesto de fechar arrastando para cima dentro do painel
    private var dX = 0f
    private var dY = 0f
    private var dragging = false
    private var dragBase = 1f
    private var dragStartY = 0f
    private var tracker: VelocityTracker? = null

    private fun dp(v: Int): Int = (v * d + 0.5f).toInt()

    init {
        val sw = resources.displayMetrics.widthPixels
        val margin = dp(16)
        val cardW = sw - 2 * margin

        sheet.orientation = LinearLayout.VERTICAL
        sheet.setPadding(0, dp(4), 0, dp(18))

        // cabecalho: relogio grande, data, engrenagem, operadora e icones
        clockText.textSize = 54f
        clockText.setTextColor(0xFF111111.toInt())
        clockText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        clockText.includeFontPadding = false
        dateText.textSize = 14f
        dateText.setTextColor(0xFF444444.toInt())
        carrierText.textSize = 14f
        carrierText.setTextColor(0xFF444444.toInt())
        carrierText.maxLines = 1
        carrierText.ellipsize = TextUtils.TruncateAt.END

        val clockRow = FrameLayout(svc)
        clockRow.addView(clockText, FrameLayout.LayoutParams(wrap, wrap, Gravity.START or Gravity.CENTER_VERTICAL))
        val gear = GearView(svc)
        gear.setOnClickListener { launch(Intent(Settings.ACTION_SETTINGS)) }
        clockRow.addView(
            gear,
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.END or Gravity.BOTTOM).also { it.bottomMargin = dp(4) }
        )

        val carrierRow = FrameLayout(svc)
        carrierRow.addView(
            carrierText,
            FrameLayout.LayoutParams(dp(170), wrap, Gravity.START or Gravity.CENTER_VERTICAL)
        )
        carrierRow.addView(iconRow, FrameLayout.LayoutParams(dp(150), dp(28), Gravity.END or Gravity.CENTER_VERTICAL))

        val header = LinearLayout(svc)
        header.orientation = LinearLayout.VERTICAL
        header.addView(clockRow, LinearLayout.LayoutParams(match, wrap))
        header.addView(dateText, LinearLayout.LayoutParams(match, wrap).also { it.topMargin = dp(2) })
        header.addView(carrierRow, LinearLayout.LayoutParams(match, dp(30)).also { it.topMargin = dp(4) })
        sheet.addView(
            header,
            LinearLayout.LayoutParams(match, wrap).also {
                it.leftMargin = margin + dp(2)
                it.rightMargin = margin + dp(2)
            }
        )

        // cartao dos botoes
        val card = LinearLayout(svc)
        card.orientation = LinearLayout.VERTICAL
        card.background = GradientDrawable().also {
            it.setColor(Color.WHITE)
            it.cornerRadius = dp(28).toFloat()
        }
        pager.pageW = cardW
        val strip = LinearLayout(svc)
        strip.orientation = LinearLayout.HORIZONTAL
        for (tiles in buildTiles()) strip.addView(buildPage(tiles), LinearLayout.LayoutParams(cardW, wrap))
        notifPage = NotifPage(svc) { closeNow() }
        strip.addView(notifPage, LinearLayout.LayoutParams(cardW, dp(262)))
        pager.addView(strip, FrameLayout.LayoutParams(wrap, wrap))
        card.addView(pager, LinearLayout.LayoutParams(match, wrap))

        val dotRow = LinearLayout(svc)
        dotRow.orientation = LinearLayout.HORIZONTAL
        dotRow.gravity = Gravity.CENTER
        for (i in 0 until 2) {
            val dot = View(svc)
            dot.background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(if (i == 0) 0xFF222222.toInt() else 0x33000000)
            }
            dots.add(dot)
            dotRow.addView(
                dot,
                LinearLayout.LayoutParams(dp(5), dp(5)).also {
                    it.leftMargin = dp(3)
                    it.rightMargin = dp(3)
                }
            )
        }
        pager.onPage = { p ->
            dots.forEachIndexed { i, v ->
                (v.background as GradientDrawable).setColor(if (i == p) 0xFF222222.toInt() else 0x33000000)
            }
        }
        card.addView(dotRow, LinearLayout.LayoutParams(match, dp(14)).also { it.topMargin = dp(6) })

        // uso de dados
        usageRow.orientation = LinearLayout.HORIZONTAL
        usageRow.gravity = Gravity.CENTER_VERTICAL
        val q = TextView(svc)
        q.text = "?"
        q.textSize = 10f
        q.gravity = Gravity.CENTER
        q.setTextColor(0xFF666666.toInt())
        q.background = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.setColor(0xFFE0E0E0.toInt())
        }
        usageRow.addView(q, LinearLayout.LayoutParams(dp(18), dp(18)))
        for (t in listOf(usageToday, usageMonth)) {
            t.textSize = 11f
            t.setTextColor(0xFF666666.toInt())
        }
        usageToday.text = "Today: --"
        usageMonth.text = "This month: --"
        usageRow.addView(usageToday, LinearLayout.LayoutParams(wrap, wrap).also { it.leftMargin = dp(8) })
        usageRow.addView(usageMonth, LinearLayout.LayoutParams(wrap, wrap).also { it.leftMargin = dp(16) })
        usageRow.setOnClickListener {
            if (!usageOk) launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        usageRow.setPadding(dp(16), 0, dp(16), 0)
        card.addView(usageRow, LinearLayout.LayoutParams(match, dp(36)))

        sheet.addView(
            card,
            LinearLayout.LayoutParams(match, wrap).also {
                it.leftMargin = margin
                it.rightMargin = margin
                it.topMargin = dp(6)
            }
        )

        // brilho
        slider = Slider(svc) { v -> setBrightness(v) }
        slider.value = currentBrightness()
        sheet.addView(
            slider,
            LinearLayout.LayoutParams(match, dp(42)).also {
                it.leftMargin = margin
                it.rightMargin = margin
                it.topMargin = dp(12)
            }
        )

        // barrinha embaixo
        val handle = View(svc)
        handle.background = GradientDrawable().also {
            it.setColor(0x44000000)
            it.cornerRadius = dp(2).toFloat()
        }
        sheet.addView(
            handle,
            LinearLayout.LayoutParams(dp(36), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(14)
            }
        )

        addView(sheet, FrameLayout.LayoutParams(match, wrap, Gravity.TOP))
        sheet.measure(
            MeasureSpec.makeMeasureSpec(sw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        sheetH = sheet.measuredHeight
        applyProgress(0f)
    }

    // ---------- botoes ----------

    private val audio: AudioManager get() = svc.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notif: NotificationManager get() = svc.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun t(
        label: String,
        icon: String,
        on: () -> Boolean = { false },
        hold: (() -> Unit)? = null,
        tap: () -> Unit
    ): Tile = Tile({ label }, icon, on, tap, hold)

    private fun buildTiles(): List<List<Tile>> {
        val internet = { launch(*internetIntents()) }
        val page1 = listOf(
            t("Mobile data", Qp.DATA, on = { mobileData() }, hold = internet) {
                sh("svc data " + (if (mobileData()) "disable" else "enable")) { internet() }
            },
            Tile({ wifiLabel() }, Qp.WIFI, { wifiOn() }, { wifiTap() }, internet),
            t(
                "Bluetooth", Qp.BT, on = { btOn() },
                hold = { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            ) {
                val a = if (btOn()) "disable" else "enable"
                sh("cmd bluetooth_manager $a || svc bluetooth $a") { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            },
            t(
                "Airplane mode", Qp.PLANE, on = { airplane() },
                hold = { launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
            ) {
                val a = if (airplane()) "disable" else "enable"
                val n = if (airplane()) 0 else 1
                sh(
                    "cmd connectivity airplane-mode $a || (settings put global airplane_mode_on $n; " +
                        "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${n == 1})"
                ) { launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
            },
            t(
                "Mute", Qp.BELL, on = { audio.ringerMode != AudioManager.RINGER_MODE_NORMAL },
                hold = { launch(Intent(Settings.ACTION_SOUND_SETTINGS)) }
            ) { toggleMute() },
            t("Flashlight", Qp.FLASH, on = { torchOn }) { toggleTorch() },
            t("Screenshot", Qp.SHOT) {
                closeNow()
                ui.postDelayed({ svc.screenshot() }, 450)
            },
            t(
                "Battery saver", Qp.SAVER, on = { saverOn() },
                hold = { launch(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            ) {
                sh("settings put global low_power " + (if (saverOn()) 0 else 1)) {
                    launch(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                }
            },
            t(
                "Dark mode", Qp.MOON, on = { darkOn() },
                hold = { launch(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            ) {
                sh("cmd uimode night " + (if (darkOn()) "no" else "yes")) {
                    launch(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
            },
            t(
                "Lock orientation", Qp.ROTATE, on = { rotationLocked() },
                hold = { launch(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            ) { toggleRotation() },
            t("Device controls", Qp.HOME) {
                launch(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage("com.google.android.apps.chromecast.app")
                )
            },
            t("Hotspot", Qp.LINK) {
                launch(
                    Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            }
        )
        val page2 = listOf(
            t(
                "Location", Qp.LOC, on = { locationOn() },
                hold = { launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            ) {
                val on = locationOn()
                sh(
                    "cmd location set-location-enabled " + (if (on) "false" else "true") +
                        " || settings put secure location_mode " + (if (on) 0 else 3)
                ) { launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            },
            t("Do not disturb", Qp.DND, on = { dndOn() }) { toggleDnd() },
            t("Cast", Qp.CAST) { launch(Intent(Settings.ACTION_CAST_SETTINGS)) },
            t("Alarm", Qp.ALARM) { launch(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        )
        val gone = setOf("Screenshot", "Dark mode", "Device controls", "Cast")
        return listOf((page1 + page2).filter { it.label() !in gone })
    }

    private fun buildPage(tiles: List<Tile>): View {
        val page = LinearLayout(svc)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(4), dp(18), dp(4), 0)
        for (r in 0 until 3) {
            val row = LinearLayout(svc)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 4) {
                val i = r * 4 + c
                if (i < tiles.size) {
                    val cell = Cell(tiles[i])
                    cells.add(cell)
                    row.addView(cell, LinearLayout.LayoutParams(0, wrap, 1f))
                } else {
                    row.addView(View(svc), LinearLayout.LayoutParams(0, dp(66), 1f))
                }
            }
            page.addView(row, LinearLayout.LayoutParams(match, wrap).also { it.topMargin = if (r == 0) 0 else dp(22) })
        }
        return page
    }

    private inner class Cell(private val tile: Tile) : LinearLayout(svc) {
        private val circle = Circle(svc, tile.icon)
        private val label = TextView(svc)

        init {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(circle, LinearLayout.LayoutParams(dp(46), dp(46)))
            label.textSize = 11f
            label.setTextColor(0xFF2B2B2B.toInt())
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            label.gravity = Gravity.CENTER
            label.setPadding(dp(2), 0, dp(2), 0)
            addView(label, LinearLayout.LayoutParams(match, wrap).also { it.topMargin = dp(6) })
            isClickable = true
            setOnClickListener { tile.tap() }
            val h = tile.hold
            if (h != null) {
                setOnLongClickListener {
                    h()
                    true
                }
            }
            refresh()
        }

        fun refresh() {
            circle.on = tile.on()
            label.text = tile.label()
        }
    }

    // ---------- estado e acoes ----------

    /**
     * Roda um comando de shell pelo Shizuku (liga/desliga sem abrir configuracoes).
     * Sem o Shizuku ligado, cai no jeito antigo (fallback).
     */
    private fun sh(cmd: String, fallback: () -> Unit) {
        when {
            SysToggle.ready() -> Thread {
                SysToggle.run(cmd)
                ui.postDelayed({ refresh() }, 600)
                ui.postDelayed({ refresh() }, 1800)
            }.start()
            SysToggle.running() -> {
                SysToggle.ask()
                toast("Autorize o Shizuku para o One UI e toque de novo")
            }
            else -> fallback()
        }
    }

    private fun internetIntents(): Array<Intent> =
        if (Build.VERSION.SDK_INT >= 29)
            arrayOf(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        else arrayOf(Intent(Settings.ACTION_WIRELESS_SETTINGS))

    private fun mobileData(): Boolean = try {
        Settings.Global.getInt(svc.contentResolver, "mobile_data", 1) == 1
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun wifiOn(): Boolean = try {
        (svc.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun wifiLabel(): String {
        try {
            val wm = svc.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wm.isWifiEnabled) {
                val s = wm.connectionInfo?.ssid?.trim('"')
                if (!s.isNullOrEmpty() && s != "<unknown ssid>" && s != "0x") return s
            }
        } catch (e: Exception) {
        }
        return "Wi-Fi"
    }

    private fun wifiTap() = sh("svc wifi " + (if (wifiOn()) "disable" else "enable")) { wifiTapOld() }

    @Suppress("DEPRECATION")
    private fun wifiTapOld() {
        if (Build.VERSION.SDK_INT < 29) {
            try {
                val wm = svc.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.isWifiEnabled = !wm.isWifiEnabled
                refreshTiles()
                return
            } catch (e: Exception) {
            }
        }
        launch(*internetIntents())
    }

    private fun btOn(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    private fun airplane(): Boolean = try {
        Settings.Global.getInt(svc.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) {
        false
    }

    private fun saverOn(): Boolean = try {
        (svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (e: Exception) {
        false
    }

    private fun darkOn(): Boolean =
        (svc.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun rotationLocked(): Boolean = try {
        Settings.System.getInt(svc.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
    } catch (e: Exception) {
        false
    }

    private fun locationOn(): Boolean = try {
        val lm = svc.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else Settings.Secure.getInt(svc.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
    } catch (e: Exception) {
        false
    }

    private fun dndOn(): Boolean = try {
        val f = notif.currentInterruptionFilter
        f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    } catch (e: Exception) {
        false
    }

    private fun toggleMute() {
        try {
            val am = audio
            if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                am.ringerMode = if (notif.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT
                else AudioManager.RINGER_MODE_VIBRATE
            } else {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: SecurityException) {
            launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        } catch (e: Exception) {
        }
        refreshTiles()
    }

    private fun toggleDnd() {
        try {
            if (!notif.isNotificationPolicyAccessGranted) {
                launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return
            }
            notif.setInterruptionFilter(
                if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        } catch (e: Exception) {
        }
        refreshTiles()
    }

    private fun toggleRotation() {
        if (!Settings.System.canWrite(svc)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + svc.packageName)))
            return
        }
        try {
            val cur = Settings.System.getInt(svc.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            Settings.System.putInt(svc.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (cur == 1) 0 else 1)
        } catch (e: Exception) {
        }
        refreshTiles()
    }

    private fun toggleTorch() {
        val id = torchId
        if (id == null) {
            toast("Lanterna indisponível neste aparelho")
            return
        }
        try {
            cam?.setTorchMode(id, !torchOn)
        } catch (e: Exception) {
            toast("Não foi possível usar a lanterna")
        }
    }

    private fun findTorch(): String? {
        val m = cam ?: return null
        return try {
            m.cameraIdList.firstOrNull { id ->
                val ch = m.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun currentBrightness(): Float = try {
        Settings.System.getInt(svc.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    } catch (e: Exception) {
        0.5f
    }

    private fun setBrightness(v: Float) {
        if (!Settings.System.canWrite(svc)) {
            if (!askedWrite) {
                askedWrite = true
                launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + svc.packageName)))
            }
            return
        }
        try {
            val cr = svc.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 254f).toInt() + 1)
        } catch (e: Exception) {
        }
    }

    private fun carrier(): String = try {
        val n = (svc.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkOperatorName
        if (n.isNullOrBlank()) "No service" else n
    } catch (e: Exception) {
        "No service"
    }

    private fun toast(msg: String) = Toast.makeText(svc, msg, Toast.LENGTH_SHORT).show()

    /** Abre a primeira tela que existir; fecha o painel antes. */
    private fun launch(vararg intents: Intent) {
        var ok = false
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                svc.startActivity(i)
                ok = true
                break
            } catch (e: Exception) {
            }
        }
        if (ok) closeNow() else toast("Indisponível neste aparelho")
    }

    // ---------- atualizacao ----------

    private fun refreshTiles() {
        for (c in cells) c.refresh()
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        clockText.text = DateFormat.format(if (DateFormat.is24HourFormat(svc)) "HH:mm" else "h:mm", now)
        dateText.text = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"), now)
        carrierText.text = carrier()
        iconRow.refreshState()
        refreshTiles()
    }

    private fun loadUsage() {
        Thread {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val today = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val month = cal.timeInMillis
            val now = System.currentTimeMillis()
            val a = mobileBytes(today, now)
            val b = mobileBytes(month, now)
            ui.post {
                usageOk = a != null && b != null
                usageToday.text = "Today: " + (if (a != null) Formatter.formatShortFileSize(svc, a) else "--")
                usageMonth.text = "This month: " + (if (b != null) Formatter.formatShortFileSize(svc, b) else "--")
            }
        }.start()
    }

    private fun mobileBytes(from: Long, to: Long): Long? = try {
        val nsm = svc.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        @Suppress("DEPRECATION")
        val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, from, to)
        b.rxBytes + b.txBytes
    } catch (e: Exception) {
        null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val f = IntentFilter()
        for (act in listOf(
            Intent.ACTION_BATTERY_CHANGED, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
            Intent.ACTION_AIRPLANE_MODE_CHANGED, AudioManager.RINGER_MODE_CHANGED_ACTION,
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
            WifiManager.WIFI_STATE_CHANGED_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION,
            ConnectivityManager.CONNECTIVITY_ACTION, BluetoothAdapter.ACTION_STATE_CHANGED,
            "android.location.MODE_CHANGED", Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )) f.addAction(act)
        try {
            svc.registerReceiver(receiver, f)
        } catch (e: Exception) {
        }
        torchId = findTorch()
        try {
            cam?.registerTorchCallback(torchCb, ui)
        } catch (e: Exception) {
        }
        refresh()
        ui.postDelayed(poll, 3000)
        loadUsage()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ui.removeCallbacks(poll)
        anim?.cancel()
        try {
            svc.unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        try {
            cam?.unregisterTorchCallback(torchCb)
        } catch (e: Exception) {
        }
    }

    // ---------- abrir, fechar e arrastar ----------

    private fun applyProgress(p: Float) {
        progress = p
        sheet.translationY = -(1f - p) * sheetHeight
        setBackgroundColor(Color.argb((p * 245f).toInt(), 242, 242, 242))
    }

    fun stopAnimation() {
        anim?.cancel()
        anim = null
    }

    fun dragTo(p: Float) {
        stopAnimation()
        applyProgress(p.coerceIn(0f, 1f))
    }

    /** Solta o dedo: vy > 0 = para baixo. Abre ou fecha conforme a velocidade e o quanto desceu. */
    fun settle(vy: Float, threshold: Float) {
        val target = when {
            vy > 1000f -> 1f
            vy < -1000f -> 0f
            progress >= threshold -> 1f
            else -> 0f
        }
        animateTo(target)
    }

    fun animateTo(target: Float) {
        stopAnimation()
        val from = progress
        if (from == target) {
            if (target == 0f) svc.removePanel(this)
            return
        }
        val a = ValueAnimator.ofFloat(from, target)
        a.duration = (260f * abs(target - from)).toLong().coerceAtLeast(120L)
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { applyProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled && target == 0f) svc.removePanel(this@QuickPanel)
            }
        })
        anim = a
        a.start()
    }

    fun close() = animateTo(0f)

    fun closeNow() {
        stopAnimation()
        svc.removePanel(this)
    }

    private fun beginClose(e: MotionEvent) {
        dragging = true
        stopAnimation()
        tracker?.recycle()
        tracker = VelocityTracker.obtain()
        tracker?.addMovement(e)
        dragBase = progress
        dragStartY = e.rawY
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dX = ev.rawX
                dY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> if (!dragging) {
                val dy = ev.rawY - dY
                val dx = ev.rawX - dX
                if (dy < -touchSlop && abs(dy) > abs(dx) * 1.5f && notifPage?.wantsScroll(ev.rawX, ev.rawY) != true) {
                    beginClose(ev)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dX = e.rawX
                dY = e.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && e.rawY - dY < -touchSlop) beginClose(e)
                if (dragging) {
                    tracker?.addMovement(e)
                    dragTo(dragBase + (e.rawY - dragStartY) / sheetHeight)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    settle(tracker?.yVelocity ?: 0f, 0.7f)
                    dragging = false
                } else if (e.actionMasked == MotionEvent.ACTION_UP && abs(e.rawY - dY) < touchSlop &&
                    e.y > sheet.bottom
                ) {
                    close()
                }
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }
}
