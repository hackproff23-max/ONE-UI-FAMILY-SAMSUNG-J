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
 * com iconback/iconmask/iconupon do pacote quando ele define, ou na forma importada (PNG), ou no
 * formato detectado nos icones do pacote (circulo, squircle ou quadrado arredondado).
 *
 * Com [fit] ligado, icones antigos com fundo branco (ou transparente) tem o fundo removido e o
 * conteudo e recortado e redesenhado preenchendo a forma.
 */
class IconThemer(
    private val context: Context,
    private val packName: String,
    private val shape: IconShape,
    private val fit: Boolean = true
) {
    private class Art(val bmp: Bitmap, val adaptive: Boolean)
    private class Info(val coverage: Float, val luminance: Float, val fill: Float, val bounds: Rect?)

    /** Conteudo do icone ja sem o fundo, recortado. [tile] = preenche o quadro (nao e so um simbolo). */
    private class Trimmed(val bmp: Bitmap, val tile: Boolean, val edge: Int, val lum: Float)

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

    /** Forma importada pelo usuario (PNG), se houver. */
    private val customMask: Bitmap? by lazy { ShapeMask.load(context, size) }

    /** Sem pacote, sem forma e sem ajuste, os icones ficam como o sistema entrega. */
    private val active: Boolean by lazy {
        pack != null || customMask != null || fit ||
            (shape != IconShape.AUTO && shape != IconShape.ORIGINAL)
    }

    private val resolvedShape: IconShape by lazy {
        val p = pack
        when {
            shape == IconShape.CIRCLE || shape == IconShape.SQUIRCLE || shape == IconShape.ROUNDED -> shape
            p != null -> inferShape(p)
            else -> IconShape.SQUIRCLE
        }
    }

    /** Mascara final (alfa 255 dentro, 0 fora): a forma importada ou a forma escolhida. */
    private val clipMask: Bitmap by lazy {
        customMask ?: run {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            Canvas(bmp).drawPath(shapePath(resolvedShape, size), p)
            bmp
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

    private fun clipToShape(c: Canvas) {
        c.drawBitmap(clipMask, 0f, 0f, dstIn)
    }

    private fun composeWithShape(art: Art): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        drawArt(c, art, null, true)
        clipToShape(c)
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
            clipToShape(lc)
        }
        canvas.drawBitmap(layer, 0f, 0f, paint)

        pick(p.upons, cn)?.let { n -> packBitmap(p, n)?.let { canvas.drawBitmap(it, 0f, 0f, paint) } }
        return out
    }

    /**
     * Desenha o icone original. Adaptativo: camadas sem recorte, preenchendo tudo. Legado com fundo
     * (branco/transparente) e [fit] ligado: tira o fundo, recorta e redesenha. Senao: preenche se
     * for cheio, ou vai sobre uma base com o desenho centralizado.
     */
    private fun drawArt(c: Canvas, art: Art, scaleFactor: Float?, plate: Boolean) {
        if (art.adaptive) {
            c.drawBitmap(art.bmp, 0f, 0f, paint)
            return
        }
        if (fit) {
            val t = trim(art.bmp)
            if (t != null) {
                drawTrimmed(c, t, scaleFactor, plate)
                return
            }
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

    private fun drawTrimmed(c: Canvas, t: Trimmed, scaleFactor: Float?, plate: Boolean) {
        val bw = t.bmp.width.toFloat()
        val bh = t.bmp.height.toFloat()
        if (scaleFactor != null) {
            // dentro de um pacote com base propria: cabe no tamanho do pacote
            val k = size * scaleFactor / max(bw, bh)
            val w = bw * k
            val h = bh * k
            c.drawBitmap(t.bmp, null, RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f), paint)
            return
        }
        if (t.tile) {
            // quadro cheio: aumenta ate cobrir a forma inteira (recorta o que sobrar)
            c.drawColor(t.edge)
            val k = size / minOf(bw, bh) * 1.06f
            val w = bw * k
            val h = bh * k
            c.drawBitmap(t.bmp, null, RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f), paint)
        } else {
            // so um simbolo: sobre uma base escura/clara (nunca branca) e centralizado
            if (plate) c.drawColor(if (t.lum > 0.55f) PLATE_DARK else PLATE_SOFT)
            val k = size * 0.62f / max(bw, bh)
            val w = bw * k
            val h = bh * k
            c.drawBitmap(t.bmp, null, RectF((size - w) / 2f, (size - h) / 2f, (size + w) / 2f, (size + h) / 2f), paint)
        }
    }

    // ---------- remocao do fundo ----------

    /**
     * Remove o fundo ligado as bordas (branco ou transparente) e recorta o conteudo.
     * Devolve null se o icone nao tem esse tipo de fundo (ai segue o desenho normal).
     */
    private fun trim(b: Bitmap): Trimmed? {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)

        val ring = ArrayList<Int>()
        for (x in 0 until w) {
            ring.add(x)
            ring.add((h - 1) * w + x)
        }
        for (y in 0 until h) {
            ring.add(y * w)
            ring.add(y * w + w - 1)
        }

        var transparent = 0
        val counts = HashMap<Int, Int>()
        val sample = HashMap<Int, Int>()
        for (i in ring) {
            val c = px[i]
            if ((c ushr 24) < 24) {
                transparent++
                continue
            }
            val key = ((Color.red(c) shr 3) shl 10) or ((Color.green(c) shr 3) shl 5) or (Color.blue(c) shr 3)
            counts[key] = (counts[key] ?: 0) + 1
            if (!sample.containsKey(key)) sample[key] = c
        }

        val bgTransparent = transparent > ring.size / 2
        var bgColor = 0
        if (!bgTransparent) {
            val top = counts.maxByOrNull { it.value } ?: return null
            if (top.value < ring.size * 0.6f) return null
            bgColor = sample[top.key] ?: return null
            // so remove fundo claro (branco / cinza bem claro)
            if (minOf(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)) < 215) return null
        }
        val br = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bb = Color.blue(bgColor)

        fun isBg(c: Int): Boolean {
            if ((c ushr 24) < 24) return true
            if (bgTransparent) return false
            return abs(Color.red(c) - br) + abs(Color.green(c) - bgG) + abs(Color.blue(c) - bb) <= 48
        }

        val gone = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        for (i in ring) {
            if (!gone[i] && isBg(px[i])) {
                gone[i] = true
                stack[sp++] = i
            }
        }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w
            val y = i / w
            if (x > 0) {
                val n = i - 1
                if (!gone[n] && isBg(px[n])) {
                    gone[n] = true
                    stack[sp++] = n
                }
            }
            if (x < w - 1) {
                val n = i + 1
                if (!gone[n] && isBg(px[n])) {
                    gone[n] = true
                    stack[sp++] = n
                }
            }
            if (y > 0) {
                val n = i - w
                if (!gone[n] && isBg(px[n])) {
                    gone[n] = true
                    stack[sp++] = n
                }
            }
            if (y < h - 1) {
                val n = i + w
                if (!gone[n] && isBg(px[n])) {
                    gone[n] = true
                    stack[sp++] = n
                }
            }
        }

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var count = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (gone[i] || (px[i] ushr 24) < 24) continue
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0 || count < w * h * 0.02f) return null

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val out = IntArray(bw * bh)
        var er = 0L
        var eg = 0L
        var eb = 0L
        var en = 0
        var lum = 0.0
        var ln = 0
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                val i = (y + minY) * w + (x + minX)
                val c = if (gone[i]) 0 else px[i]
                out[y * bw + x] = c
                val a = c ushr 24
                if (a > 128) {
                    lum += (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
                    ln++
                }
                if (a > 200 && (x < 2 || y < 2 || x >= bw - 2 || y >= bh - 2)) {
                    er += Color.red(c)
                    eg += Color.green(c)
                    eb += Color.blue(c)
                    en++
                }
            }
        }
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, bw, 0, 0, bw, bh)
        val edge = if (en > 0) Color.rgb((er / en).toInt(), (eg / en).toInt(), (eb / en).toInt()) else PLATE_DARK
        val tile = count.toFloat() / (bw * bh) >= 0.72f
        return Trimmed(bmp, tile, edge, if (ln > 0) (lum / ln).toFloat() else 0f)
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
        const val PLATE_SOFT = 0xFFE4E6EB.toInt()
        const val PLATE_DARK = 0xFF2B2D31.toInt()
    }
}
