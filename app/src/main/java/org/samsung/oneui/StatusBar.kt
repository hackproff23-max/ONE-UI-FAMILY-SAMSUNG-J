package org.samsung.oneui

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Barra de status propria (estilo iPhone): a do sistema fica escondida e esta mostra a hora,
 * os icones das notificacoes e tudo que estiver ligado: modo silencioso, aviao, sinal, wifi,
 * bluetooth, localizacao, alarme, VPN, nao perturbe e bateria.
 */
object StatusBar {
    private const val TAG = "oneui_statusbar"

    fun heightPx(ctx: Context): Int = (28 * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** A janela global precisa cobrir a barra do sistema inteira. */
    fun overlayHeightPx(ctx: Context): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val sys = if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
        return maxOf(heightPx(ctx), sys)
    }

        fun install(a: Activity) {
        val decor = a.window.decorView as ViewGroup
        if (decor.findViewWithTag<View>(TAG) != null) return
        hideSystem(a)
        if (BarService.running) return // a barra global (servico) ja desenha por cima de tudo
        askAccessibility(a)
        val v = Bar(a, a)
        v.tag = TAG
        decor.addView(v, FrameLayout.LayoutParams(-1, heightPx(a), Gravity.TOP))
        askNotificationAccess(a)
    }

    @Suppress("DEPRECATION")
    fun hideSystem(a: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            a.window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.statusBars())
            }
        } else {
            val d = a.window.decorView
            d.systemUiVisibility = d.systemUiVisibility or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** Os icones das notificacoes so aparecem com o "acesso a notificacoes" ligado: pede uma vez. */
    private fun askNotificationAccess(a: Activity) {
        val enabled = Settings.Secure.getString(a.contentResolver, "enabled_notification_listeners") ?: ""
        if (enabled.contains(a.packageName)) return
        val sp = a.getSharedPreferences("oneui_launcher", Context.MODE_PRIVATE)
        if (sp.getBoolean("asked_notif", false)) return
        sp.edit().putBoolean("asked_notif", true).apply()
        Toast.makeText(a, "Ative o acesso a notificações deste app para ver os ícones delas na barra", Toast.LENGTH_LONG).show()
        try {
            a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
        }
    }

    /** Para a barra valer em todos os apps e bloquear a do sistema, liga-se o servico de acessibilidade. */
    private fun askAccessibility(a: Activity) {
        val enabled = Settings.Secure.getString(a.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (enabled.contains(a.packageName)) return
        val sp = a.getSharedPreferences("oneui_launcher", Context.MODE_PRIVATE)
        if (sp.getBoolean("asked_a11y", false)) return
        sp.edit().putBoolean("asked_a11y", true).apply()
        Toast.makeText(
            a,
            "Ative este app em Acessibilidade para a barra valer em todos os apps e bloquear a do sistema",
            Toast.LENGTH_LONG
        ).show()
        try {
            a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
        }
    }

    private class Item(val w: Float, val draw: (Canvas, Float, Float) -> Unit)

    class Bar(ctx: Context, private val a: Activity?) : View(ctx) {
        /** Fundo preto (nos outros apps) ou transparente com degrade (no launcher). */
        var solid = false
            set(v) {
                field = v
                invalidate()
            }

        /** Na janela global a barra segura o toque, para a do sistema nao abrir por baixo. */
        var blockTouches = false

        override fun onTouchEvent(event: android.view.MotionEvent?): Boolean =
            blockTouches || super.onTouchEvent(event)

        private val d = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = 15f * resources.displayMetrics.scaledDensity
        }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111111.toInt()
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 10.5f * resources.displayMetrics.scaledDensity
        }
        private val shade = Paint()

        private val WHITE = Color.WHITE
        private val DIM = 0x55FFFFFF

        // estado
        private var timeText = ""
        private var battery = -1
        private var charging = false
        private var powerSave = false
        private var airplane = false
        private var ringer = AudioManager.RINGER_MODE_NORMAL
        private var dnd = false
        private var wifi = false
        private var wifiLevel = 0
        private var cellLevel = 0
        private var simReady = true
        private var bluetooth = false
        private var location = false
        private var alarm = false
        private var vpn = false

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refresh()
            }
        }

        @Suppress("DEPRECATION")
        private val phoneListener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                cellLevel = signalStrength?.level ?: 0
                invalidate()
            }
        }

        private val poll = object : Runnable {
            override fun run() {
                refresh()
                postDelayed(this, 4000)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            val f = IntentFilter()
            for (act in listOf(
                Intent.ACTION_BATTERY_CHANGED, PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                Intent.ACTION_AIRPLANE_MODE_CHANGED, AudioManager.RINGER_MODE_CHANGED_ACTION,
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                WifiManager.RSSI_CHANGED_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION,
                BluetoothAdapter.ACTION_STATE_CHANGED, "android.location.MODE_CHANGED",
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
                Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED
            )) f.addAction(act)
            context.registerReceiver(receiver, f)
            try {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                    .listen(phoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            } catch (e: Exception) {
            }
            NotifState.listener = { invalidate() }
            refresh()
            postDelayed(poll, 4000)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            removeCallbacks(poll)
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
            }
            try {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                    .listen(phoneListener, PhoneStateListener.LISTEN_NONE)
            } catch (e: Exception) {
            }
            NotifState.listener = null
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus && a != null) hideSystem(a)
        }

        @Suppress("DEPRECATION")
        private fun refresh() {
            val c = context
            try {
                timeText = DateFormat.format(
                    if (DateFormat.is24HourFormat(c)) "H:mm" else "h:mm", System.currentTimeMillis()
                ).toString()

                val bi = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (bi != null) {
                    val lv = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    battery = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
                    val st = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                        (st == BatteryManager.BATTERY_STATUS_FULL && bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0)
                }
                powerSave = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
                airplane = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                ringer = (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode
                val f = (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter
                dnd = f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                alarm = (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock != null
                val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                simReady = tm.simState == TelephonyManager.SIM_STATE_READY
            } catch (e: Exception) {
            }
            try {
                val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork
                val caps = if (net != null) cm.getNetworkCapabilities(net) else null
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                if (wifi) {
                    val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val rssi = wm.connectionInfo?.rssi ?: -100
                    wifiLevel = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
                }
            } catch (e: Exception) {
            }
            try {
                bluetooth = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
            } catch (e: Exception) {
                bluetooth = false
            }
            try {
                val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                location = if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
                else Settings.Secure.getInt(c.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0
            } catch (e: Exception) {
                location = false
            }
            invalidate()
        }

        // ---------- desenho ----------

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cy = h / 2f
            val padL = 16 * d
            val padR = 16 * d
            val gap = 7 * d

            // leve degrade no topo para a barra ler bem em qualquer papel de parede
            if (solid) {
                canvas.drawColor(0xFF000000.toInt())
            } else {
                shade.shader = LinearGradient(0f, 0f, 0f, h, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, shade)
            }

            // direita: tudo que esta ligado
            val items = rightItems()
            var total = 0f
            for (it in items) total += it.w
            if (items.size > 1) total += gap * (items.size - 1)
            var x = w - padR - total
            val leftLimit = x - 8 * d
            for (it in items) {
                it.draw(canvas, x, cy)
                x += it.w + gap
            }

            // esquerda: hora
            val fm = text.fontMetrics
            val base = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(timeText, padL, base, text)

            // icones das notificacoes
            var nx = padL + text.measureText(timeText) + 10 * d
            val s = (15 * d).toInt()
            for (ic in NotifState.icons) {
                if (nx + s > leftLimit) break
                ic.setBounds(nx.toInt(), (cy - s / 2f).toInt(), nx.toInt() + s, (cy + s / 2f).toInt())
                ic.draw(canvas)
                nx += s + 5 * d
            }
        }

        private fun rightItems(): List<Item> {
            val l = ArrayList<Item>()
            if (alarm) l.add(Item(14 * d) { c, x, cy -> drawAlarm(c, x + 7 * d, cy) })
            if (vpn) l.add(Item(24 * d) { c, x, cy -> drawVpn(c, x, cy) })
            if (location) l.add(Item(12 * d) { c, x, cy -> drawLocation(c, x + 6 * d, cy) })
            if (bluetooth) l.add(Item(10 * d) { c, x, cy -> drawBluetooth(c, x + 5 * d, cy) })
            if (airplane) l.add(Item(16 * d) { c, x, cy -> drawPlane(c, x + 8 * d, cy) })
            if (dnd) l.add(Item(14 * d) { c, x, cy -> drawDnd(c, x + 7 * d, cy) })
            if (ringer == AudioManager.RINGER_MODE_SILENT) l.add(Item(15 * d) { c, x, cy -> drawBell(c, x + 7.5f * d, cy, true) })
            if (ringer == AudioManager.RINGER_MODE_VIBRATE) l.add(Item(15 * d) { c, x, cy -> drawVibrate(c, x + 7.5f * d, cy) })
            if (!airplane && simReady) l.add(Item(18 * d) { c, x, cy -> drawBars(c, x, cy) })
            if (wifi) l.add(Item(18 * d) { c, x, cy -> drawWifi(c, x + 9 * d, cy) })
            if (battery >= 0) l.add(Item(30 * d) { c, x, cy -> drawBattery(c, x, cy) })
            return l
        }

        private fun drawBattery(c: Canvas, x: Float, cy: Float) {
            val bw = 30 * d
            val bh = 15 * d
            val r = RectF(x, cy - bh / 2f, x + bw, cy + bh / 2f)
            val rad = bh * 0.34f
            fill.color = 0x66FFFFFF
            c.drawRoundRect(r, rad, rad, fill)
            val col = when {
                charging -> 0xFF30D158.toInt()
                powerSave -> 0xFFFFD60A.toInt()
                battery <= 20 -> 0xFFFF453A.toInt()
                else -> WHITE
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

        private fun drawWifi(c: Canvas, cx: Float, cy: Float) {
            val by = cy + 6 * d
            line.strokeWidth = 2 * d
            for (i in 1..3) {
                val r = i * 4.4f * d
                line.color = if (i <= wifiLevel) WHITE else DIM
                c.drawArc(RectF(cx - r, by - r, cx + r, by + r), 225f, 90f, false, line)
            }
            fill.color = WHITE
            c.drawCircle(cx, by, 1.7f * d, fill)
        }

        private fun drawBars(c: Canvas, x: Float, cy: Float) {
            val bw = 3.2f * d
            val g = 1.6f * d
            for (i in 0 until 4) {
                val bh = (4 + i * 3) * d
                val left = x + i * (bw + g)
                fill.color = if (i < cellLevel) WHITE else DIM
                c.drawRoundRect(RectF(left, cy + 6.5f * d - bh, left + bw, cy + 6.5f * d), d, d, fill)
            }
        }

        private fun drawBell(c: Canvas, cx: Float, cy: Float, slash: Boolean) {
            val p = Path()
            p.moveTo(cx - 5 * d, cy + 3 * d)
            p.lineTo(cx - 5 * d, cy - 1 * d)
            p.quadTo(cx - 5 * d, cy - 6 * d, cx, cy - 6 * d)
            p.quadTo(cx + 5 * d, cy - 6 * d, cx + 5 * d, cy - 1 * d)
            p.lineTo(cx + 5 * d, cy + 3 * d)
            p.close()
            fill.color = WHITE
            c.drawPath(p, fill)
            c.drawCircle(cx, cy + 5 * d, 1.7f * d, fill)
            if (slash) {
                line.color = WHITE
                line.strokeWidth = 1.9f * d
                c.drawLine(cx - 7 * d, cy - 7 * d, cx + 7 * d, cy + 7 * d, line)
            }
        }

        private fun drawVibrate(c: Canvas, cx: Float, cy: Float) {
            fill.color = WHITE
            c.drawRoundRect(RectF(cx - 3 * d, cy - 6 * d, cx + 3 * d, cy + 6 * d), 1.5f * d, 1.5f * d, fill)
            line.color = WHITE
            line.strokeWidth = 1.6f * d
            c.drawLine(cx - 5.5f * d, cy - 3.5f * d, cx - 5.5f * d, cy + 3.5f * d, line)
            c.drawLine(cx + 5.5f * d, cy - 3.5f * d, cx + 5.5f * d, cy + 3.5f * d, line)
            c.drawLine(cx - 7.5f * d, cy - 2 * d, cx - 7.5f * d, cy + 2 * d, line)
            c.drawLine(cx + 7.5f * d, cy - 2 * d, cx + 7.5f * d, cy + 2 * d, line)
        }

        private fun drawDnd(c: Canvas, cx: Float, cy: Float) {
            line.color = WHITE
            line.strokeWidth = 1.8f * d
            c.drawCircle(cx, cy, 6 * d, line)
            c.drawLine(cx - 3 * d, cy, cx + 3 * d, cy, line)
        }

        private fun drawPlane(c: Canvas, cx: Float, cy: Float) {
            c.save()
            c.translate(cx, cy)
            c.rotate(45f)
            fill.color = WHITE
            val p = Path()
            p.moveTo(0f, -7.5f * d)
            p.quadTo(1.8f * d, -6 * d, 1.8f * d, -3 * d)
            p.lineTo(1.8f * d, -1 * d)
            p.lineTo(7.5f * d, 3 * d)
            p.lineTo(7.5f * d, 5 * d)
            p.lineTo(1.8f * d, 3 * d)
            p.lineTo(1.4f * d, 6 * d)
            p.lineTo(3.5f * d, 7.5f * d)
            p.lineTo(3.5f * d, 8.5f * d)
            p.lineTo(0f, 7.6f * d)
            p.lineTo(-3.5f * d, 8.5f * d)
            p.lineTo(-3.5f * d, 7.5f * d)
            p.lineTo(-1.4f * d, 6 * d)
            p.lineTo(-1.8f * d, 3 * d)
            p.lineTo(-7.5f * d, 5 * d)
            p.lineTo(-7.5f * d, 3 * d)
            p.lineTo(-1.8f * d, -1 * d)
            p.lineTo(-1.8f * d, -3 * d)
            p.quadTo(-1.8f * d, -6 * d, 0f, -7.5f * d)
            p.close()
            c.drawPath(p, fill)
            c.restore()
        }

        private fun drawBluetooth(c: Canvas, cx: Float, cy: Float) {
            line.color = WHITE
            line.strokeWidth = 1.6f * d
            val p = Path()
            p.moveTo(cx - 3.2f * d, cy - 3.4f * d)
            p.lineTo(cx + 3.2f * d, cy + 3.2f * d)
            p.lineTo(cx, cy + 6.5f * d)
            p.lineTo(cx, cy - 6.5f * d)
            p.lineTo(cx + 3.2f * d, cy - 3.2f * d)
            p.lineTo(cx - 3.2f * d, cy + 3.4f * d)
            c.drawPath(p, line)
        }

        private fun drawLocation(c: Canvas, cx: Float, cy: Float) {
            c.save()
            c.translate(cx, cy)
            c.rotate(40f)
            fill.color = WHITE
            val p = Path()
            p.moveTo(0f, -6.5f * d)
            p.lineTo(4.6f * d, 5.5f * d)
            p.lineTo(0f, 3 * d)
            p.lineTo(-4.6f * d, 5.5f * d)
            p.close()
            c.drawPath(p, fill)
            c.restore()
        }

        private fun drawAlarm(c: Canvas, cx: Float, cy: Float) {
            line.color = WHITE
            line.strokeWidth = 1.6f * d
            c.drawCircle(cx, cy + 0.5f * d, 5 * d, line)
            c.drawLine(cx, cy + 0.5f * d, cx, cy - 2.5f * d, line)
            c.drawLine(cx, cy + 0.5f * d, cx + 2.5f * d, cy + 1.5f * d, line)
            fill.color = WHITE
            c.drawCircle(cx - 5 * d, cy - 4.5f * d, 1.8f * d, fill)
            c.drawCircle(cx + 5 * d, cy - 4.5f * d, 1.8f * d, fill)
        }

        private fun drawVpn(c: Canvas, x: Float, cy: Float) {
            line.color = WHITE
            line.strokeWidth = 1.3f * d
            c.drawRoundRect(RectF(x, cy - 5.5f * d, x + 24 * d, cy + 5.5f * d), 3 * d, 3 * d, line)
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WHITE
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = 8 * resources.displayMetrics.scaledDensity
            }
            val fm = t.fontMetrics
            c.drawText("VPN", x + 12 * d, cy - (fm.ascent + fm.descent) / 2f, t)
        }
    }
}
