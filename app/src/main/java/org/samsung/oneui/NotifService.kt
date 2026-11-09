package org.samsung.oneui

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Uma notificacao de outro app, pronta para mostrar no painel. */
class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val color: Int,
    val smallIcon: Drawable?,
    val appIcon: Drawable?,
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

            // icone branco da barra de status (um por app)
            if (seen.add(sbn.packageName)) {
                val shown = small ?: appIcon
                if (shown != null) {
                    val m = shown.constantState?.newDrawable()?.mutate() ?: shown.mutate()
                    if (small != null) m.setTint(Color.WHITE)
                    icons.add(m)
                }
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
                    small, appIcon, n.contentIntent,
                    (n.flags and Notification.FLAG_AUTO_CANCEL) != 0, sbn.isClearable
                )
            )
        }
        NotifState.set(icons, items)
    }
}
