package org.samsung.oneui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

class IconPackInfo(val packageName: String, val label: String)

/** Pacote de icones carregado (formato ADW/Nova/Apex: appfilter.xml). */
class IconPack(
    val packageName: String,
    private val res: Resources,
    private val byComponent: Map<String, String>,
    private val byPackage: Map<String, String>,
    val backs: List<String>,
    val masks: List<String>,
    val upons: List<String>,
    val scale: Float
) {
    /** Icone do pacote para esse app (por atividade; se nao houver, por pacote). */
    fun drawableFor(cn: ComponentName): Drawable? {
        val name = byComponent[cn.flattenToString()] ?: byPackage[cn.packageName] ?: return null
        return load(name)
    }

    fun load(name: String): Drawable? {
        var id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = res.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return try {
            res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    /** Alguns nomes de icones espalhados pelo pacote, para descobrir o formato dos icones. */
    fun sampleNames(count: Int): List<String> {
        val all = byComponent.values.distinct()
        if (all.isEmpty()) return emptyList()
        val step = maxOf(1, all.size / count)
        return all.filterIndexed { i, _ -> i % step == 0 }.take(count)
    }
}

object IconPackLoader {
    private val ACTIONS = arrayOf(
        "org.adw.launcher.THEMES",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )

    /** Pacotes de icones instalados. */
    fun list(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (action in ACTIONS) {
            @Suppress("DEPRECATION")
            val found = pm.queryIntentActivities(Intent(action), 0)
            for (ri in found) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName in seen) continue
                val label = ai.applicationInfo?.loadLabel(pm)?.toString() ?: ai.packageName
                seen[ai.packageName] = IconPackInfo(ai.packageName, label)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun load(context: Context, pkg: String): IconPack? {
        val res = try {
            context.packageManager.getResourcesForApplication(pkg)
        } catch (e: Exception) {
            return null
        }
        val parser = openAppFilter(res, pkg) ?: return null
        val byComponent = LinkedHashMap<String, String>()
        val byPackage = HashMap<String, String>()
        val backs = ArrayList<String>()
        val masks = ArrayList<String>()
        val upons = ArrayList<String>()
        var scale = 0.75f
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val comp = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (comp != null && drawable != null) {
                                val cn = parseComponent(comp)
                                if (cn != null) {
                                    byComponent.putIfAbsent(cn.flattenToString(), drawable)
                                    byPackage.putIfAbsent(cn.packageName, drawable)
                                }
                            }
                        }
                        "iconback" -> collectImages(parser, backs)
                        "iconmask" -> collectImages(parser, masks)
                        "iconupon" -> collectImages(parser, upons)
                        "scale" -> parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { scale = it }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // usa o que deu para ler
        }
        if (byComponent.isEmpty() && backs.isEmpty() && masks.isEmpty()) return null
        return IconPack(pkg, res, byComponent, byPackage, backs, masks, upons, scale)
    }

    private fun openAppFilter(res: Resources, pkg: String): XmlPullParser? {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        if (id != 0) {
            try {
                return res.getXml(id)
            } catch (e: Exception) {
                // tenta pelos assets
            }
        }
        return try {
            Xml.newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), null) }
        } catch (e: Exception) {
            null
        }
    }

    private fun collectImages(parser: XmlPullParser, out: MutableList<String>) {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).startsWith("img")) out.add(parser.getAttributeValue(i))
        }
    }

    /** "ComponentInfo{pacote/atividade}" -> ComponentName. */
    private fun parseComponent(s: String): ComponentName? {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return ComponentName.unflattenFromString(s.substring(start + 1, end).trim())
    }
}
