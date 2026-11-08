package org.samsung.oneui

import android.app.Notification
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Guarda os icones das notificacoes ativas para a barra de status. */
object NotifState {
    @Volatile
    var icons: List<Drawable> = emptyList()
    var listener: (() -> Unit)? = null

    fun set(list: List<Drawable>) {
        icons = list
        Handler(Looper.getMainLooper()).post { listener?.invoke() }
    }
}

class NotifService : NotificationListenerService() {
    override fun onListenerConnected() {
        refresh()
    }

    override fun onListenerDisconnected() {
        NotifState.set(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        val list = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val seen = HashSet<String>()
        val out = ArrayList<Drawable>()
        for (sbn in list) {
            val n = sbn.notification ?: continue
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            if (!seen.add(sbn.packageName)) continue
            val d = try {
                n.smallIcon?.loadDrawable(this)
            } catch (e: Exception) {
                null
            } ?: continue
            val m = d.mutate()
            m.setTint(Color.WHITE)
            out.add(m)
        }
        NotifState.set(out)
    }
}
