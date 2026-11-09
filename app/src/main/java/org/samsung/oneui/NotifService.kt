package org.samsung.oneui

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.File

/** Uma notificacao de outro app, pronta para mostrar no painel. [logo] = icone do app com o mesmo tema/vidro do launcher. */
class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val color: Int,
    val smallIcon: Drawable?,
    val logo: Drawable?,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean
)

/** Guarda os icones (barra de status) e o conteudo (painel) das notificacoes ativas. */
object NotifState {
    @Volatile
    var icons: List<Drawable> = emptyList()

    @Volatile
    var items: List<NotifItem> = emptyList()

    /** Barra de status. */
    var listener: (() -> Unit)? = null

    /** Lista de notificacoes do painel. */
    var onItems: (() -> Unit)? = null

    fun set(list: List<Drawable>, all: List<NotifItem> = items) {
        icons = list
        items = all
        Handler(Looper.getMainLooper()).post {
            listener?.invoke()
            onItems?.invoke()
        }
    }
}

class NotifService : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: NotifService? = null

        fun dismiss(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
            }
        }

        fun dismissAll() {
            try {
                instance?.cancelAllNotifications()
            } catch (e: Exception) {
            }
        }
    }

    // logos no mesmo tema dos icones do launcher (pacote, forma, vidro), refeitos quando o tema muda
    private var themer: IconThemer? = null
    private var themerSig = ""
    private val logoCache = HashMap<String, Drawable?>()

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        NotifState.set(emptyList(), emptyList())
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun smallIconOf(sbn: StatusBarNotification): Drawable? {
        val ic = sbn.notification?.smallIcon ?: return null
        try {
            ic.loadDrawable(this)?.let { return it }
        } catch (e: Exception) {
        }
        try {
            // icone de recurso de outro app: carrega com o contexto do proprio app
            val pc = createPackageContext(sbn.packageName, Context.CONTEXT_IGNORE_SECURITY)
            return ic.loadDrawable(pc)
        } catch (e: Exception) {
        }
        return null
    }

    /** Desenha o icone em 96x96 (nitido ao reduzir), opcionalmente todo branco. */
    private fun hd(d: Drawable, white: Boolean): Drawable {
        val s = 96
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val copy = d.constantState?.newDrawable()?.mutate() ?: d.mutate()
        if (white) copy.setTint(Color.WHITE)
        copy.setBounds(0, 0, s, s)
        copy.draw(c)
        return BitmapDrawable(resources, bmp)
    }

    private fun logoFor(pkg: String, appIcon: Drawable?): Drawable? {
        if (appIcon == null) return null
        val p = Prefs(this)
        val sig = listOf(
            p.iconPack(), p.iconShape().id, p.fitIcons(), p.maskBlur(), Glass.on(this),
            File(filesDir, "shape_mask.png").lastModified()
        ).joinToString("|")
        if (sig != themerSig) {
            themerSig = sig
            logoCache.clear()
            themer = IconThemer(this, p.iconPack(), p.iconShape(), p.fitIcons(), 44, p.maskBlur(), Glass.on(this))
        }
        if (logoCache.containsKey(pkg)) return logoCache[pkg]
        val t = themer
        val cn = packageManager.getLaunchIntentForPackage(pkg)?.component ?: ComponentName(pkg, pkg)
        val d = try {
            if (t != null) t.apply(cn, appIcon) else appIcon
        } catch (e: Exception) {
            appIcon
        }
        logoCache[pkg] = d
        return d
    }

    private fun refresh() {
        val list = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val seen = HashSet<String>()
        val icons = ArrayList<Drawable>()
        val items = ArrayList<NotifItem>()
        val pm = packageManager
        for (sbn in list.sortedByDescending { it.postTime }) {
            val n = sbn.notification ?: continue
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue

            val small = smallIconOf(sbn)
            val appIcon = try {
                pm.getApplicationIcon(sbn.packageName)
            } catch (e: Exception) {
                null
            }

            // icone da barra de status (um por app), em HD
            if (seen.add(sbn.packageName)) {
                if (small != null) icons.add(hd(small, true))
                else if (appIcon != null) icons.add(hd(appIcon, false))
            }

            // conteudo para o painel
            val ex = n.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (ex?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex?.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) continue
            val app = try {
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                sbn.packageName
            }
            items.add(
                NotifItem(
                    sbn.key, sbn.packageName, app, title, text, sbn.postTime, n.color,
                    small?.let { hd(it, false) }, logoFor(sbn.packageName, appIcon), n.contentIntent,
                    (n.flags and Notification.FLAG_AUTO_CANCEL) != 0, sbn.isClearable
                )
            )
        }
        NotifState.set(icons, items)
    }
}
