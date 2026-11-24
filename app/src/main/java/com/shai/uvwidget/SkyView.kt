package com.shai.uvwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The sky scene for the home-screen widget: one still frame, rounded corners baked in. */
fun skyBitmap(dm: DisplayMetrics, scene: SkyScene, widthDp: Float, heightDp: Float, seed: Long): Bitmap {
    val pw = max(1f, widthDp * dm.density)
    val ph = max(1f, heightDp * dm.density)
    // Keep the bitmap small: widget updates travel between processes and have a size budget.
    val s = min(1f, 640f / max(pw, ph))
    val w = max(1, (pw * s).roundToInt())
    val h = max(1, (ph * s).roundToInt())
    val sky = SkyRenderer(scene.style, w, h, seed).still()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val rad = 28f * dm.density * s
    c.clipPath(Path().apply { addRoundRect(rect, rad, rad, Path.Direction.CW) })
    sky.drawTo(c, rect)
    // Widget text can't have a shadow, so darken the background a little where text sits
    // (top row and bottom line) to keep white text readable over white clouds and snow.
    val scrim = Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                android.graphics.Color.argb(72, 8, 14, 30), android.graphics.Color.argb(26, 8, 14, 30),
                android.graphics.Color.argb(26, 8, 14, 30), android.graphics.Color.argb(64, 8, 14, 30),
            ),
            floatArrayOf(0f, .45f, .7f, 1f), android.graphics.Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(rect, scrim)
    sky.buffer.recycle()
    return out
}

/** Full-screen live background for the app: the same scenes, animated. */
class SkyView(context: Context) : View(context) {

    var scene: SkyScene = SkyScene.NOON
        set(value) {
            if (field == value) return
            field = value
            rebuild(fade = true)
        }

    private var renderer: SkyRenderer? = null
    private var previous: Bitmap? = null
    private var fade = 1f
    private var last = 0L
    private val dst = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Draw at reduced resolution: the sky is soft anyway and this keeps it light on the battery.
    private val scale = 0.4f

    private val speed: Float =
        if (Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) .35f else 1f

    private fun rebuild(fade: Boolean) {
        if (width == 0 || height == 0) return
        val old = renderer
        if (fade && old != null) {
            previous?.recycle()
            previous = old.buffer.copy(Bitmap.Config.ARGB_8888, false)
            this.fade = 0f
        }
        old?.buffer?.recycle()
        renderer = SkyRenderer(
            scene.style, max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()), System.nanoTime(),
        )
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild(fade = false)
    }

    override fun onDraw(canvas: Canvas) {
        val r = renderer ?: return
        val now = System.nanoTime()
        val real = if (last == 0L) 0f else min(.05f, (now - last) / 1e9f)
        last = now
        r.render(real * speed)
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        r.drawTo(canvas, dst)
        val prev = previous
        if (prev != null) {
            fade = min(1f, fade + real / .9f)
            val e = fade * fade * (3 - 2 * fade)
            paint.alpha = ((1 - e) * 255).roundToInt()
            canvas.drawBitmap(prev, null, dst, paint)
            if (fade >= 1f) { prev.recycle(); previous = null }
        }
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Back from the background: restart the loop without a time jump.
        if (visibility == VISIBLE) { last = 0L; invalidate() }
    }
}
