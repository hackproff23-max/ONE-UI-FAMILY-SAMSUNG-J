package org.samsung.oneui

import android.content.ComponentName
import android.graphics.drawable.Drawable

class AppInfo(val label: String, val component: ComponentName, val icon: Drawable) {
    val key: String = component.flattenToString()
    val packageName: String get() = component.packageName

    /** Cada ImageView precisa da sua propria copia do icone (bounds do Drawable nao sao compartilhaveis). */
    fun newIcon(): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon
}
