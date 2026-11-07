package org.samsung.oneui

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object AppRepository {
    /** Lista os apps com icone de launcher. Reaproveita itens do [cache] para nao recarregar icones. */
    fun load(context: Context, cache: Map<String, AppInfo>): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(intent, 0)
        val result = ArrayList<AppInfo>(found.size)
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == context.packageName) continue
            val cn = ComponentName(ai.packageName, ai.name)
            val cached = cache[cn.flattenToString()]
            result.add(cached ?: AppInfo(ri.loadLabel(pm).toString(), cn, ri.loadIcon(pm)))
        }
        result.sortBy { it.label.lowercase() }
        return result
    }
}
