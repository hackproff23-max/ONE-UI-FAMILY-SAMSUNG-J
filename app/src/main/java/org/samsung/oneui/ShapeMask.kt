package org.samsung.oneui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Forma importada pelo usuario (PNG). O fundo (transparente ou a cor do canto, ex.: vermelho)
 * e "fora"; o resto e a forma. Os icones sao redesenhados dentro dela.
 */
object ShapeMask {
    private fun file(ctx: Context) = File(ctx.filesDir, "shape_mask.png")

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun delete(ctx: Context) {
        file(ctx).delete()
    }

    fun save(ctx: Context, uri: Uri): Boolean {
        return try {
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return false
            val scaled = Bitmap.createScaledBitmap(bmp, 512, 512, true)
            FileOutputStream(file(ctx)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Devolve uma mascara size x size: alfa 255 dentro da forma, 0 fora. */
    fun load(ctx: Context, size: Int): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val src = BitmapFactory.decodeFile(f.path) ?: return null
        val corner = src.getPixel(0, 0)
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)
        val cornerOpaque = Color.alpha(corner) > 200
        val cr = Color.red(corner)
        val cg = Color.green(corner)
        val cb = Color.blue(corner)
        for (i in px.indices) {
            val c = px[i]
            val a = if (cornerOpaque) {
                val dist = abs(Color.red(c) - cr) + abs(Color.green(c) - cg) + abs(Color.blue(c) - cb)
                ((dist - 40).coerceAtLeast(0) * 255 / 200).coerceAtMost(255)
            } else {
                Color.alpha(c)
            }
            px[i] = (a shl 24) or 0x00FFFFFF
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, size, 0, 0, size, size)
        return out
    }
}
