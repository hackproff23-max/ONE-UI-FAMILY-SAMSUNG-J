package org.samsung.oneui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

enum class IconShape(val id: String, val label: String) {
    AUTO("auto", "Automático (igual ao pacote)"),
    CIRCLE("circle", "Círculo"),
    SQUIRCLE("squircle", "Squircle"),
    ROUNDED("rounded", "Quadrado arredondado"),
    ORIGINAL("original", "Manter original");

    companion object {
        fun fromId(id: String?): IconShape = values().firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Aplica o pacote de icones. Apps que o pacote nao cobre sao redesenhados no formato dos outros:
 * com iconback/iconmask/iconupon do pacote quando ele define, ou no formato detectado nos icones
 * do pacote (circulo, squircle ou quadrado arredondado).
 */
class IconThemer(
    private val context: Context,
    private val packName: String,
    private val shape: IconShape
) {
    private class Art(val bmp: Bitmap, val adaptive: Boolean)
    private class Info(val coverage: Float, val luminance: Float, val fill: Float, val bounds: Rect?)

    private val res = context.resources
    private val size = (56 * res.displayMetrics.density).toInt().coerceIn(96, 256)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dstIn = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val dstOut = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val packBitmaps = HashMap<String, Bitmap?>()
    private val maskKeepCache = HashMap<String, Boolean>()

    private val pack: IconPack? by lazy {
        if (packName.isEmpty()) null else IconPackLoader.load(context, packName)
    }

    /** Sem pacote e sem formato escolhido, os icones ficam como o sistema entrega. */
    private val active: Boolean by lazy {
        pack != null || (shape != IconShape.AUTO && shape != IconShape.ORIGINAL)
    }

    private val resolvedShape: IconShape by lazy {
        val p = pack
        when {
            shape == IconShape.CIRCLE || shape == IconShape.SQUIRCLE || shape == IconShape.ROUNDED -> shape
            p != null -> inferShape(p)
            else -> IconShape.SQUIRCLE
        }
    }

    fun apply(cn: ComponentName, original: Drawable): Drawable {
        if (!active) return original
        val p = pack
        if (p != null) {
            val themed = p.drawableFor(cn)
            if (themed != null) return BitmapDrawable(res, render(themed, size))
        }
        if (shape == IconShape.ORIGINAL) return original
        return try {
            val art = artOf(original)
            val out = if (p != null && (p.backs.isNotEmpty() || p.masks.isNotEmpty() || p.upons.isNotEmpty())) {
                composeWithPack(p, cn, art)
            } else {
                composeWithShape(art)
            }
            BitmapDrawable(res, out)
        } catch (e: Exception) {
            original
        }
    }

    // ---------- composicao ----------

    private fun composeWithShape(art: Art): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        drawArt(c, art, null, true)
        c.drawPath(shapePath(resolvedShape, size), dstIn)
        return out
    }

    private fun composeWithPack(p: IconPack, cn: ComponentName, art: Art): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        pick(p.backs, cn)?.let { n -> packBitmap(p, n)?.let { canvas.drawBitmap(it, 0f, 0f, paint) } }

        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        drawArt(lc, art, p.scale.coerceIn(0.3f, 1.2f), p.backs.isEmpty())
        val maskName = pick(p.masks, cn)
        val mask = if (maskName != null) packBitmap(p, maskName) else null
        if (mask != null && maskName != null) {
            lc.drawBitmap(mask, 0f, 0f, if (maskKeeps(maskName, mask)) dstIn else dstOut)
        } else if (art.adaptive) {
            lc.drawPath(shapePath(resolvedShape, size), dstIn)
        }
        canvas.drawBitmap(layer, 0f, 0f, paint)

        pick(p.upons, cn)?.let { n -> packBitmap(p, n)?.let { canvas.drawBitmap(it, 0f, 0f, paint) } }
        return out
    }

    /**
     * Desenha o icone original. Adaptativo: camadas sem recorte, preenchendo tudo. Legado cheio
     * (sem cantos transparentes): preenche. Legado com transparencia: vai sobre uma base clara/escura,
     * com o desenho centralizado e do mesmo tamanho nos outros icones.
     */
    private fun drawArt(c: Canvas, art: Art, scaleFactor: Float?, plate: Boolean) {
        if (art.adaptive) {
            c.drawBitmap(art.bmp, 0f, 0f, paint)
            return
        }
        val info = analyze(art.bmp)
        if (scaleFactor == null && info.coverage >= 0.80f) {
            val m = size * 0.06f
            c.drawBitmap(art.bmp, null, RectF(-m, -m, size + m, size + m), paint)
            return
        }
        if (plate) c.drawColor(if (info.luminance > 0.75f) PLATE_DARK else PLATE_LIGHT)
        if (scaleFactor != null) {
            val s = size * scaleFactor
            val o = (size - s) / 2f
            c.drawBitmap(art.bmp, null, RectF(o, o, o + s, o + s), paint)
        } else {
            val box = info.bounds ?: Rect(0, 0, size, size)
            val longest = max(box.width(), box.height()).coerceAtLeast(1)
            val k = size * 0.62f / longest
            val w = box.width() * k
            val h = box.height() * k
            c.drawBitmap(art.bmp, box, RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f), paint)
        }
    }

    // ---------- utilitarios ----------

    private fun artOf(original: Drawable): Art {
        if (Build.VERSION.SDK_INT >= 26 && original is AdaptiveIconDrawable) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val off = (size * 0.25f).toInt()
            original.background?.let {
                it.setBounds(-off, -off, size + off, size + off)
                it.draw(c)
            }
            original.foreground?.let {
                it.setBounds(-off, -off, size + off, size + off)
                it.draw(c)
            }
            return Art(bmp, true)
        }
        return Art(render(original, size), false)
    }

    private fun render(d: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is BitmapDrawable && d.bitmap != null) {
            c.drawBitmap(d.bitmap, null, Rect(0, 0, px, px), paint)
        } else {
            d.setBounds(0, 0, px, px)
            d.draw(c)
        }
        return bmp
    }

    private fun packBitmap(p: IconPack, name: String): Bitmap? =
        packBitmaps.getOrPut(name) { p.load(name)?.let { render(it, size) } }

    /** Mascara com centro opaco = "mantem o que esta dentro"; centro transparente = "corta o que esta dentro". */
    private fun maskKeeps(name: String, mask: Bitmap): Boolean =
        maskKeepCache.getOrPut(name) { Color.alpha(mask.getPixel(mask.width / 2, mask.height / 2)) > 128 }

    private fun pick(list: List<String>, cn: ComponentName): String? =
        if (list.isEmpty()) null else list[abs(cn.packageName.hashCode() % list.size)]

    private fun analyze(b: Bitmap): Info {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var opaque = 0
        var visible = 0
        var lum = 0.0
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                val a = c ushr 24
                if (a > 16) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                if (a > 200) opaque++
                if (a > 128) {
                    visible++
                    lum += (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
                }
            }
        }
        val bounds = if (maxX >= minX && maxY >= minY) Rect(minX, minY, maxX + 1, maxY + 1) else null
        val boxArea = if (bounds != null) bounds.width() * bounds.height() else 0
        return Info(
            opaque.toFloat() / (w * h),
            if (visible > 0) (lum / visible).toFloat() else 0f,
            if (boxArea > 0) visible.toFloat() / boxArea else 0f,
            bounds
        )
    }

    /** Descobre o formato dos icones do pacote pela area preenchida (circulo ~0.79, squircle ~0.93, quadrado ~0.96). */
    private fun inferShape(p: IconPack): IconShape {
        val fills = ArrayList<Float>()
        for (name in p.sampleNames(15)) {
            val d = p.load(name) ?: continue
            val info = analyze(render(d, 96))
            val box = info.bounds ?: continue
            if (box.width() < 96 * 0.7f || box.height() < 96 * 0.7f) continue // so icones que enchem a area
            fills.add(info.fill)
        }
        if (fills.isEmpty()) return IconShape.SQUIRCLE
        fills.sort()
        val median = fills[fills.size / 2]
        return when {
            median < 0.70f -> IconShape.SQUIRCLE
            median < 0.85f -> IconShape.CIRCLE
            median < 0.94f -> IconShape.SQUIRCLE
            else -> IconShape.ROUNDED
        }
    }

    private fun shapePath(shape: IconShape, s: Int): Path {
        val path = Path()
        val r = s / 2f
        when (shape) {
            IconShape.CIRCLE -> path.addCircle(r, r, r, Path.Direction.CW)
            IconShape.ROUNDED -> path.addRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), s * 0.22f, s * 0.22f, Path.Direction.CW)
            else -> {
                // superelipse (expoente 4): o "squircle"
                val steps = 128
                for (i in 0 until steps) {
                    val t = 2.0 * PI * i / steps
                    val c = cos(t)
                    val sn = sin(t)
                    val x = r + r * Math.signum(c).toFloat() * abs(c).pow(0.5).toFloat()
                    val y = r + r * Math.signum(sn).toFloat() * abs(sn).pow(0.5).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
        }
        return path
    }

    private companion object {
        const val PLATE_LIGHT = 0xFFF5F5F7.toInt()
        const val PLATE_DARK = 0xFF2B2D31.toInt()
    }
}
