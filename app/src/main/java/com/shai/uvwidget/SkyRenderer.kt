package com.shai.uvwidget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (PI * 2).toFloat()
private const val DEG = (180 / PI).toFloat()

private fun ca(c: Int, a: Float): Int =
    Color.argb((a.coerceIn(0f, 1f) * 255f).roundToInt(), Color.red(c), Color.green(c), Color.blue(c))

private val RAINBOW = intArrayOf(
    Color.parseColor("#ff4d4d"), Color.parseColor("#ff9a3d"), Color.parseColor("#ffe14d"), Color.parseColor("#5ee06a"),
    Color.parseColor("#4db8ff"), Color.parseColor("#5b6cff"), Color.parseColor("#a35bff"),
)
private val LEAF = intArrayOf(
    Color.parseColor("#c9a24a"), Color.parseColor("#a7b84e"), Color.parseColor("#d9843a"), Color.parseColor("#8fae3c"),
)

/**
 * Draws one [SkyStyle] into a bitmap. [render] advances the animation by dt seconds,
 * so the same code drives the live background in the app and the still frame in the widget
 * ([still]). Everything is drawn with a software canvas into [buffer].
 *
 * Units: sizes scale with the "reference height" (the widget's height, or the height a
 * widget of this width would have), so a full-screen portrait view looks like a taller widget.
 */
class SkyRenderer(private val st: SkyStyle, val w: Int, val h: Int, seed: Long) {

    private val rnd = Random(seed)
    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    private fun chance(p: Float) = rnd.nextFloat() < p

    private val W = w.toFloat()
    private val H = h.toFloat()
    private val ref = min(W / 1.57f, H)
    private val u = ref / 330f
    private val dens = ((W * H) / (ref * 1.57f * ref)).coerceIn(1f, 3f)
    private fun n(count: Int) = (count * dens).roundToInt()

    val buffer: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val cv = Canvas(buffer)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val path = Path()
    private val oval = RectF()
    private var t = 0f

    private val skyShader = LinearGradient(0f, 0f, W * .25f, H, st.sky, null, Shader.TileMode.CLAMP)
    private val vigShader = RadialGradient(
        W * .5f, H * .45f, W * .75f,
        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, ca(Color.BLACK, st.vig)), floatArrayOf(0f, .4f, 1f),
        Shader.TileMode.CLAMP,
    )

    // ---------- particles ----------
    private class Blob(val col: Int, val a: Float, val x: Float, val y: Float, val r: Float, val ph: Float, val sp: Float)
    private class Star(val x: Float, val y: Float, val r: Float, val tw: Float, val ph: Float)
    private class Shoot(var x: Float, var y: Float, val vx: Float, val vy: Float, var l: Float, val d: Float)
    private class Cloud(val spr: Bitmap, val w: Float, val h: Float, var x: Float, val y: Float, val v: Float, val a: Float, val ph: Float)
    private class Drop(var x: Float = 0f, var y: Float = 0f, var len: Float = 0f, var v: Float = 0f, var z: Float = 0f)
    private class Splash(var x: Float, var y: Float, val vx: Float, var vy: Float, var l: Float, val m: Float)
    private class Glass(var x: Float = 0f, var y: Float = 0f, var r: Float = 0f, var vy: Float = 0f, var wait: Float = 0f)
    private class Bokeh(val x: Float, val y: Float, val r: Float, val col: Int, val ph: Float, val a: Float)
    private class Flake(var x: Float = 0f, var y: Float = 0f, var r: Float = 0f, var v: Float = 0f, var sw: Float = 0f,
                        var ph: Float = 0f, var a: Float = 0f, var big: Boolean = false)
    private class Stone(var x: Float = 0f, var y: Float = 0f, var vy: Float = 0f, var vx: Float = 0f, var r: Float = 0f, var b: Boolean = false)
    private class FogBand(val spr: Bitmap, val bw: Float, val y: Float, val v: Float, val a: Float, var off: Float)
    private class Streak(var x: Float = 0f, var y: Float = 0f, var len: Float = 0f, var v: Float = 0f, var amp: Float = 0f,
                         var ph: Float = 0f, var a: Float = 0f)
    private class Leaf(var x: Float = 0f, var y: Float = 0f, var v: Float = 0f, var s: Float = 0f, var rot: Float = 0f,
                       var vr: Float = 0f, var ph: Float = 0f, var col: Int = 0)
    private class Mote(var x: Float, var y: Float, val r: Float, val v: Float, val ph: Float, val a: Float)
    private class Bird(var x: Float, var y: Float, val v: Float, val s: Float, val ph: Float)
    private class Bolt(val pts: FloatArray, val wd: Float)

    private val blobs = st.glows.map { Blob(it.color, it.a, it.x, it.y, it.r, r(0f, TAU), r(.08f, .16f)) }
    private val stars = st.stars?.let { s ->
        List(n(s.n)) { Star(r(0f, W), rnd.nextFloat().pow(1.4f) * H * s.max, (if (chance(.1f)) r(1.1f, 1.7f) else r(.35f, 1f)) * u, r(.5f, 2.4f), r(0f, TAU)) }
    } ?: emptyList()
    private var shoot: Shoot? = null
    private var nextShoot = r(1.5f, 4f)
    private val moonSpr = st.moon?.let { moonSprite(it.r * ref, it.phase) }
    private val clouds = st.clouds?.let { o ->
        List(o.n) {
            val cw = W * r(o.s0, o.s1); val ch = cw * .56f
            Cloud(cloudSprite(cw, ch, o), cw, ch, r(-cw * .6f, W - cw * .2f), H * r(o.y0, o.y1) - ch * .3f,
                W * r(o.v0, o.v1) * (cw / W + .4f) * o.dir, r(o.a0, o.a1), r(0f, TAU))
        }.sortedBy { it.w }
    } ?: emptyList()
    private val rain = st.rain?.let { List(n(it.n)) { Drop().also { d -> resetDrop(d, true) } } } ?: emptyList()
    private val rainSegs = FloatArray(rain.size * 4)
    private var splashes = ArrayList<Splash>()
    private val glass = List(st.glass) { Glass().also { g -> resetGlass(g, true) } }
    private val bokeh = st.bokeh?.let { b ->
        List(b.n) { i -> Bokeh(r(0f, W), H * r(.5f, 1f), ref * r(.04f, .11f), b.cols[i % b.cols.size], r(0f, TAU), r(.15f, .35f) * b.a) }
    } ?: emptyList()
    private val snow = st.snow?.let { List(n(it.n)) { Flake().also { f -> resetFlake(f, true) } } } ?: emptyList()
    private val hail = List(n(st.hail)) { Stone().also { s -> resetStone(s, true) } }
    private var bolts: List<Bolt>? = null
    private var flashT = 99f
    private var nextBolt = r(.8f, 2.2f)
    private val fog = st.fog?.let { f ->
        List(f.n) { i ->
            val bw = W * 2; val bh = ref * r(.45f, .7f)
            val dir = if (f.dir != 0) f.dir else if (i % 2 == 1) 1 else -1
            FogBand(fogSprite(bw, bh, f.col), bw, H * r(-.1f, .8f) - bh * .4f, W * r(.015f, .04f) * f.speed * dir,
                r(f.a * .6f, f.a), r(0f, bw))
        }
    } ?: emptyList()
    private val streaks = st.wind?.let { List(it.streaks) { Streak().also { s -> resetStreak(s, true) } } } ?: emptyList()
    private val leaves = st.wind?.let { List(it.leaves) { Leaf().also { l -> resetLeaf(l, true) } } } ?: emptyList()
    private val dust = st.dust?.let { List(n(it.n)) { Mote(r(0f, W), r(0f, H), r(.5f, 1.9f) * u, W * r(.18f, .45f), r(0f, TAU), r(.25f, .7f)) } } ?: emptyList()
    private val birds = List(st.birds) { Bird(r(0f, W), H * r(.15f, .5f), W * r(.02f, .045f), r(4f, 7f) * u, r(0f, TAU)) }

    private fun resetDrop(d: Drop, init: Boolean) {
        val c = st.rain!!
        d.x = r(-.1f, 1.25f) * W; d.y = if (init) r(-.1f, 1f) * H else r(-.25f, -.02f) * H
        d.len = r(c.len0, c.len1) * u; d.v = r(c.v0, c.v1) * u; d.z = rnd.nextFloat()
    }
    private fun resetGlass(g: Glass, init: Boolean) {
        g.x = r(0f, W); g.y = if (init) r(0f, H) else r(0f, H * .6f); g.r = r(1.8f, 5.5f) * u; g.vy = 0f
        g.wait = r(1f, if (init) 12f else 8f)
    }
    private fun resetFlake(f: Flake, init: Boolean) {
        val big = chance(.08f)
        f.big = big; f.r = (if (big) r(3f, 5.5f) else r(.7f, 2.4f)) * u
        f.x = r(0f, W); f.y = if (init) r(0f, H) else r(-20f, -5f) * u
        f.v = (14 + f.r / u * 11) * u * st.snow!!.speed; f.sw = r(8f, 26f) * u; f.ph = r(0f, TAU)
        f.a = if (big) r(.25f, .5f) else r(.55f, .95f)
    }
    private fun resetStone(s: Stone, init: Boolean) {
        s.x = r(0f, W * 1.1f); s.y = if (init) r(-H, H) else r(-.3f, -.02f) * H
        s.vy = r(420f, 640f) * u; s.vx = -50f * u; s.r = r(1.3f, 2.8f) * u; s.b = false
    }
    private fun resetStreak(s: Streak, init: Boolean) {
        s.x = if (init) r(0f, W) else W + r(0f, W * .6f); s.y = r(.08f, .92f) * H; s.len = r(.18f, .38f) * W
        s.v = r(.55f, .95f) * W; s.amp = r(3f, 10f) * u; s.ph = r(0f, TAU); s.a = r(.18f, .42f)
    }
    private fun resetLeaf(l: Leaf, init: Boolean) {
        l.x = if (init) r(0f, W) else W + r(10f, W * .5f); l.y = r(.1f, .85f) * H; l.v = r(.4f, .7f) * W
        l.s = r(3.5f, 6.5f) * u; l.rot = r(0f, TAU); l.vr = r(-7f, 7f); l.ph = r(0f, TAU); l.col = LEAF[rnd.nextInt(LEAF.size)]
    }

    // ---------- sprites ----------
    private fun cloudSprite(cw: Float, ch: Float, o: CloudCfg): Bitmap {
        val bw = max(1, cw.roundToInt()); val bh = max(1, ch.roundToInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val q = Paint(Paint.ANTI_ALIAS_FLAG)
        val W2 = bw.toFloat(); val H2 = bh.toFloat()
        val count = 6 + rnd.nextInt(4)
        val puffs = ArrayList<FloatArray>()
        for (i in 0 until count) {
            val f = i / (count - 1f)
            val pr = H2 * (.2f + .2f * sin(PI.toFloat() * f)) * r(.85f, 1.15f)
            puffs += floatArrayOf(W2 * (.14f + .72f * f) + r(-1f, 1f) * W2 * .03f, H2 * .7f - pr * .45f + r(-1f, 1f) * H2 * .03f, pr)
        }
        repeat(4) { puffs += floatArrayOf(W2 * r(.2f, .8f), H2 * r(.64f, .7f), H2 * r(.14f, .2f)) }
        for ((px, py, pr) in puffs) {
            q.shader = RadialGradient(px, py, pr, intArrayOf(ca(o.col, 1f), ca(o.col, .85f), ca(o.col, 0f)),
                floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(px, py, pr, q)
        }
        q.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        q.shader = LinearGradient(0f, H2 * .3f, 0f, H2 * .92f, ca(o.shade, 0f), ca(o.shade, .85f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W2, H2, q)
        q.shader = LinearGradient(0f, 0f, 0f, H2 * .5f, ca(o.hi, .7f), ca(o.hi, 0f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W2, H2, q)
        return bmp
    }

    private fun fogSprite(bw: Float, bh: Float, col: Int): Bitmap {
        val iw = max(1, bw.roundToInt()); val ih = max(1, bh.roundToInt())
        val bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val q = Paint(Paint.ANTI_ALIAS_FLAG)
        val fw = iw.toFloat(); val fh = ih.toFloat()
        repeat(16) {
            val bx = r(0f, fw); val by = fh * r(.35f, .65f); val rx = fw * r(.06f, .14f); val ry = fh * r(.22f, .4f)
            q.shader = RadialGradient(0f, 0f, rx, ca(col, .55f), ca(col, 0f), Shader.TileMode.CLAMP)
            for (ox in floatArrayOf(-fw, 0f, fw)) {
                c.save(); c.translate(bx + ox, by); c.scale(1f, ry / rx); c.drawCircle(0f, 0f, rx, q); c.restore()
            }
        }
        return bmp
    }

    private fun moonSprite(rad: Float, phase: Float): Bitmap {
        val s = max(4, (rad * 2 + 4).roundToInt())
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val q = Paint(Paint.ANTI_ALIAS_FLAG); val cx = s / 2f
        q.shader = RadialGradient(cx - rad * .3f, cx - rad * .3f, rad * 1.3f,
            Color.parseColor("#fffdf2"), Color.parseColor("#e2dcc2"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cx, rad, q)
        q.shader = null; q.color = Color.argb(33, 140, 130, 100)
        for ((a, b, k) in listOf(
            floatArrayOf(-.3f, -.2f, .22f), floatArrayOf(.25f, .15f, .16f), floatArrayOf(.05f, -.45f, .1f),
            floatArrayOf(-.1f, .4f, .14f), floatArrayOf(.45f, -.25f, .09f), floatArrayOf(-.5f, .15f, .08f),
        )) c.drawCircle(cx + a * rad, cx + b * rad, k * rad, q)
        if (phase > 0f) {
            q.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            c.drawCircle(cx - rad * 2 * (1 - phase), cx - rad * .12f, rad * 1.02f, q)
        }
        return bmp
    }

    private fun makeBolt(): List<Bolt> {
        val lines = ArrayList<Bolt>()
        val main = ArrayList<Float>()
        var px = W * r(.2f, .8f); var py = -5f
        val endY = H * r(.65f, 1.05f)
        main += px; main += py
        while (py < endY) {
            py += H * r(.04f, .09f); px += r(-1f, 1f) * W * .035f
            main += px; main += py
            if (chance(.28f)) {
                val br = arrayListOf(px, py); var bx = px; var by = py
                val dir = if (chance(.5f)) -1 else 1
                repeat(2 + rnd.nextInt(4)) {
                    by += H * r(.03f, .07f); bx += dir * W * r(.01f, .04f) + r(-1f, 1f) * W * .015f
                    br += bx; br += by
                }
                lines += Bolt(br.toFloatArray(), 1f)
            }
        }
        lines.add(0, Bolt(main.toFloatArray(), 2.2f))
        return lines
    }

    private fun flashCurve(x: Float): Float = when {
        x < .06f -> 1 - x / .06f * .5f
        x < .12f -> .15f
        x < .18f -> .85f
        x < .7f -> .85f * (1 - (x - .18f) / .52f).pow(2)
        else -> 0f
    }

    // ---------- drawing helpers ----------
    private fun radialFill(x: Float, y: Float, rad: Float, colors: IntArray, stops: FloatArray? = null, additive: Boolean = false) {
        p.reset(); p.isAntiAlias = true
        if (additive) p.xfermode = add
        p.shader = RadialGradient(x, y, max(rad, .5f), colors, stops, Shader.TileMode.CLAMP)
        cv.drawCircle(x, y, rad, p)
        p.shader = null; p.xfermode = null
    }

    private fun drawSun(s: SunCfg) {
        val cx = s.x * W; val cy = s.y * H; val rad = s.r * ref; val i = s.int
        val pulse = 1 + .035f * sin(t * 1.3f)
        radialFill(cx, cy, rad * 7 * pulse,
            intArrayOf(ca(s.glow, .5f * i), ca(s.glow, .22f * i), ca(s.glow, .07f * i), ca(s.glow, 0f)),
            floatArrayOf(0f, .18f, .5f, 1f), additive = true)
        if (s.rays) {
            p.reset(); p.isAntiAlias = true; p.xfermode = add
            cv.save(); cv.translate(cx, cy); cv.rotate(t * .04f * DEG)
            val count = 16
            for (k in 0 until count) {
                cv.rotate(360f / count)
                val len = rad * (if (k % 2 == 1) 5f else 7.5f) * (1 + .07f * sin(t * 1.7f + k))
                p.shader = LinearGradient(0f, 0f, 0f, -len, ca(s.glow, .18f * i), ca(s.glow, 0f), Shader.TileMode.CLAMP)
                path.reset(); path.moveTo(-rad * .18f, 0f); path.lineTo(0f, -len); path.lineTo(rad * .18f, 0f); path.close()
                cv.drawPath(path, p)
            }
            cv.restore(); p.shader = null; p.xfermode = null
        }
        radialFill(cx, cy, rad,
            intArrayOf(ca(Color.WHITE, min(1f, .95f * i + .05f)), ca(s.core, min(1f, .95f * i)), ca(s.core, .55f * i), ca(s.core, 0f)),
            floatArrayOf(0f, .5f, .72f, 1f))
        if (s.flare) {
            val tx = W * .55f; val ty = H * .6f
            for ((k, rr, a) in listOf(floatArrayOf(.45f, .05f, .08f), floatArrayOf(.7f, .025f, .12f),
                floatArrayOf(.95f, .08f, .05f), floatArrayOf(1.25f, .04f, .08f))) {
                val px = cx + (tx - cx) * k; val py = cy + (ty - cy) * k
                radialFill(px, py, rr * ref * pulse, intArrayOf(ca(s.glow, a), ca(s.glow, a * .5f), ca(s.glow, 0f)),
                    floatArrayOf(0f, .7f, 1f), additive = true)
            }
        }
    }

    private fun drawMoon(m: MoonCfg) {
        val cx = m.x * W; val cy = m.y * H; val rad = m.r * ref; val pl = 1 + .04f * sin(t * .8f)
        radialFill(cx, cy, rad * 6 * pl, intArrayOf(ca(m.glow, .28f), ca(m.glow, .28f), ca(m.glow, .08f), ca(m.glow, 0f)),
            floatArrayOf(0f, .13f, .4f, 1f), additive = true)
        val spr = moonSpr ?: return
        p.reset(); p.isFilterBitmap = true
        cv.drawBitmap(spr, cx - spr.width / 2f, cy - spr.height / 2f, p)
    }

    // ---------- one frame ----------
    fun render(dtIn: Float) {
        val dt = dtIn
        t += dt
        p.reset(); p.isAntiAlias = true

        // sky + drifting light
        p.shader = skyShader; cv.drawRect(0f, 0f, W, H, p); p.shader = null
        for (b in blobs) {
            val bx = (b.x + sin(t * b.sp + b.ph) * .08f) * W
            val by = (b.y + cos(t * b.sp * .8f + b.ph) * .06f) * H
            radialFill(bx, by, b.r * W, intArrayOf(ca(b.col, b.a), ca(b.col, 0f)))
        }

        // stars
        st.stars?.let { cfg ->
            p.reset(); p.isAntiAlias = true
            for (s in stars) {
                val a = cfg.a * (.3f + .7f * (.5f + .5f * sin(t * s.tw + s.ph)))
                p.color = ca(Color.WHITE, a); cv.drawCircle(s.x, s.y, s.r, p)
                if (s.r > 1.1f * u) {
                    p.color = ca(Color.WHITE, a * .35f)
                    cv.drawRect(s.x - s.r * 3.5f, s.y - .35f * u, s.x + s.r * 3.5f, s.y + .35f * u, p)
                    cv.drawRect(s.x - .35f * u, s.y - s.r * 3.5f, s.x + .35f * u, s.y + s.r * 3.5f, p)
                }
            }
            if (cfg.shoot) {
                nextShoot -= dt
                if (shoot == null && nextShoot <= 0) {
                    shoot = Shoot(r(.35f, 1f) * W, r(.02f, .3f) * H, -W * r(.7f, 1f), H * r(.25f, .45f), 0f, r(.6f, .9f))
                    nextShoot = r(4f, 9f)
                }
                shoot?.let { s ->
                    s.l += dt; s.x += s.vx * dt; s.y += s.vy * dt
                    val a = sin(PI.toFloat() * min(1f, s.l / s.d))
                    val tx = s.x - s.vx * .14f; val ty = s.y - s.vy * .14f
                    p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f * u; p.strokeCap = Paint.Cap.ROUND
                    p.shader = LinearGradient(s.x, s.y, tx, ty, ca(Color.WHITE, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    cv.drawLine(s.x, s.y, tx, ty, p); p.shader = null
                    if (s.l >= s.d) shoot = null
                }
            }
        }

        // rainbow
        st.rainbow?.let { rb ->
            val cx = rb.x * W; val cy = H + (rb.y - 1f) * ref; val rad = rb.r * ref; val bw = ref * .024f
            val a = rb.a * (.75f + .25f * sin(t * .4f))
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = bw + .6f * u
            p.maskFilter = BlurMaskFilter(2f * u, BlurMaskFilter.Blur.NORMAL)
            RAINBOW.forEachIndexed { k, col ->
                val rr = rad - k * bw
                p.color = ca(col, a); oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
                cv.drawArc(oval, 180f, 180f, false, p)
            }
            p.maskFilter = null
        }

        st.sun?.let { drawSun(it) }
        st.moon?.let { drawMoon(it) }

        // lightning
        var fl = 0f
        if (st.lightning) {
            nextBolt -= dt; flashT += dt
            if (nextBolt <= 0) { flashT = 0f; bolts = makeBolt(); nextBolt = r(2.2f, 6.5f) }
            fl = flashCurve(flashT)
            val bs = bolts
            if (bs != null && flashT < .6f) {
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
                p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
                p.color = ca(Color.rgb(235, 242, 255), min(1f, fl * 1.4f))
                p.setShadowLayer(18f * u, 0f, 0f, Color.rgb(170, 200, 255))
                for (b in bs) {
                    p.strokeWidth = b.wd * u
                    path.reset(); path.moveTo(b.pts[0], b.pts[1])
                    var k = 2; while (k < b.pts.size) { path.lineTo(b.pts[k], b.pts[k + 1]); k += 2 }
                    cv.drawPath(path, p)
                }
                p.clearShadowLayer()
            }
        }

        // clouds
        p.reset(); p.isFilterBitmap = true
        for (k in clouds) {
            k.x += k.v * dt
            if (k.v > 0 && k.x > W + 10) k.x = -k.w - r(0f, W * .2f)
            if (k.v < 0 && k.x < -k.w - 10) k.x = W + r(0f, W * .2f)
            p.alpha = (k.a * 255).roundToInt()
            cv.drawBitmap(k.spr, k.x, k.y + sin(t * .25f + k.ph) * 2 * u, p)
        }
        p.alpha = 255

        // city lights behind wet glass
        for (b in bokeh) {
            val a = b.a * (.75f + .25f * sin(t * .7f + b.ph))
            radialFill(b.x, b.y, b.r, intArrayOf(ca(b.col, a), ca(b.col, a * .6f), ca(b.col, 0f)), floatArrayOf(0f, .7f, 1f), additive = true)
        }

        // birds
        if (birds.isNotEmpty()) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * u
            p.strokeCap = Paint.Cap.ROUND; p.color = ca(st.birdCol, .6f)
            path.reset()
            for (b in birds) {
                b.x -= b.v * dt
                if (b.x < -20 * u) { b.x = W + 20 * u; b.y = H * r(.15f, .5f) }
                val f = sin(t * 7 + b.ph) * b.s * .55f; val by = b.y + sin(t * .6f + b.ph) * 4 * u
                path.moveTo(b.x - b.s, by - f)
                path.quadTo(b.x - b.s * .45f, by - b.s * .15f, b.x, by)
                path.quadTo(b.x + b.s * .45f, by - b.s * .15f, b.x + b.s, by - f)
            }
            cv.drawPath(path, p)
        }

        // fog / dust bands
        p.reset(); p.isFilterBitmap = true
        for (f in fog) {
            f.off = ((f.off + f.v * dt) % f.bw + f.bw) % f.bw
            p.alpha = (f.a * 255).roundToInt()
            cv.drawBitmap(f.spr, -f.off, f.y, p); cv.drawBitmap(f.spr, -f.off + f.bw, f.y, p)
        }
        p.alpha = 255

        // dust motes
        st.dust?.let { cfg ->
            p.reset(); p.isAntiAlias = true
            for (d in dust) {
                d.x -= d.v * dt; d.y += sin(t * 2.6f + d.ph) * 18 * u * dt
                if (d.x < -5) { d.x = W + 5; d.y = r(0f, H) }
                p.color = ca(cfg.col, d.a); cv.drawCircle(d.x, d.y, d.r, p)
            }
        }

        if (fl > 0) { p.reset(); p.color = ca(Color.rgb(205, 218, 255), fl * .45f); cv.drawRect(0f, 0f, W, H, p) }

        // rain
        st.rain?.let { rc ->
            val ang = rc.angle
            for (d in rain) {
                d.y += d.v * dt; d.x -= d.v * ang * dt
                if (d.y - d.len > H) {
                    if (rc.splash && chance(.6f)) repeat(2) {
                        val life = r(.25f, .4f)
                        splashes.add(Splash(d.x, H - r(1f, 6f) * u, r(-50f, 50f) * u, r(-110f, -50f) * u, life, life))
                    }
                    resetDrop(d, false)
                }
            }
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            for (near in booleanArrayOf(false, true)) {
                var k = 0
                for (d in rain) if ((d.z > .72f) == near) {
                    rainSegs[k++] = d.x; rainSegs[k++] = d.y; rainSegs[k++] = d.x + d.len * ang; rainSegs[k++] = d.y - d.len
                }
                p.color = ca(rc.col, rc.a * (if (near) 1.25f else .65f)); p.strokeWidth = (if (near) 1.35f else .8f) * u
                if (k > 0) cv.drawLines(rainSegs, 0, k, p)
            }
            if (splashes.isNotEmpty()) {
                p.reset(); p.isAntiAlias = true
                val it = splashes.iterator()
                while (it.hasNext()) {
                    val s = it.next()
                    s.l -= dt; s.vy += 500 * u * dt; s.x += s.vx * dt; s.y += s.vy * dt
                    if (s.l <= 0) { it.remove(); continue }
                    p.color = ca(rc.col, .7f * s.l / s.m); cv.drawCircle(s.x, s.y, .9f * u, p)
                }
            }
        }

        // hail
        if (hail.isNotEmpty()) {
            p.reset(); p.isAntiAlias = true
            for (s in hail) {
                s.y += s.vy * dt; s.x += s.vx * dt
                if (!s.b && s.y > H - s.r) { s.y = H - s.r; s.vy = -s.vy * r(.18f, .32f); s.vx = r(-80f, 80f) * u; s.b = true }
                else if (s.b) { s.vy += 1400 * u * dt; if (s.y > H + 8 * u) resetStone(s, false) }
                if (!s.b) {
                    p.style = Paint.Style.STROKE; p.strokeWidth = s.r * .9f; p.color = ca(Color.rgb(235, 243, 255), .3f)
                    cv.drawLine(s.x, s.y, s.x - s.vx * .02f, s.y - s.vy * .03f, p)
                }
                p.style = Paint.Style.FILL
                p.color = ca(Color.rgb(215, 229, 244), .92f); cv.drawCircle(s.x, s.y, s.r, p)
                p.color = ca(Color.WHITE, .9f); cv.drawCircle(s.x - s.r * .3f, s.y - s.r * .3f, s.r * .45f, p)
            }
        }

        // snow
        st.snow?.let { cfg ->
            p.reset(); p.isAntiAlias = true
            for (f in snow) {
                f.y += f.v * dt; f.x += (sin(t * .9f + f.ph) * f.sw + cfg.wind * u) * dt
                if (f.y > H + 6 * u) resetFlake(f, false)
                if (f.x < -10 * u) f.x = W + 10 * u
                if (f.x > W + 10 * u) f.x = -10 * u
                if (f.big) radialFill(f.x, f.y, f.r, intArrayOf(ca(Color.WHITE, f.a), ca(Color.WHITE, 0f)))
                else { p.color = ca(Color.WHITE, f.a); cv.drawCircle(f.x, f.y, f.r, p) }
            }
        }

        // wind
        if (streaks.isNotEmpty() || leaves.isNotEmpty()) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * u; p.strokeCap = Paint.Cap.ROUND
            for (s in streaks) {
                s.x -= s.v * dt
                if (s.x + s.len < 0) resetStreak(s, false)
                p.shader = LinearGradient(s.x, 0f, s.x + s.len, 0f,
                    intArrayOf(Color.TRANSPARENT, ca(Color.WHITE, s.a), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                path.reset()
                for (k in 0..14) {
                    val q = k / 14f
                    val px = s.x + q * s.len; val py = s.y + sin(q * 4 + t * 2.5f + s.ph) * s.amp * q
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                cv.drawPath(path, p)
            }
            p.shader = null
            for (l in leaves) {
                l.x -= l.v * dt; l.y += (sin(t * 2.2f + l.ph) * 45 + 12) * u * dt; l.rot += l.vr * dt
                if (l.x < -20 * u || l.y > H + 20 * u) resetLeaf(l, false)
                cv.save(); cv.translate(l.x, l.y); cv.rotate(l.rot * DEG); cv.scale(1f, .35f + .65f * abs(cos(t * 3 + l.ph)))
                p.style = Paint.Style.FILL; p.color = l.col
                oval.set(-l.s, -l.s * .45f, l.s, l.s * .45f); cv.drawOval(oval, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = .6f * u; p.color = Color.argb(102, 60, 40, 10)
                cv.drawLine(-l.s, 0f, l.s * 1.3f, 0f, p)
                cv.restore()
            }
        }

        // drops on the glass
        for (g in glass) {
            g.wait -= dt
            var sliding = false
            if (g.wait < 0) {
                g.vy = min(g.vy + 60 * u * dt, 70 * u); g.y += g.vy * dt; g.x += sin(t * 3 + g.r) * 3 * u * dt; sliding = true
                if (g.y > H + g.r) resetGlass(g, false)
            }
            val gx = g.x; val gy = g.y; val gr = g.r
            if (sliding) {
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = gr * .45f; p.color = ca(Color.WHITE, .1f)
                cv.drawLine(gx, gy - min(40 * u, g.vy * .6f), gx, gy, p)
            }
            radialFill(gx - gr * .15f, gy - gr * .15f, gr * 1.1f,
                intArrayOf(ca(Color.WHITE, .35f), ca(Color.WHITE, .06f), ca(Color.WHITE, .2f), ca(Color.WHITE, 0f)),
                floatArrayOf(0f, .45f, .8f, 1f))
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = max(.6f * u, gr * .18f)
            p.color = ca(Color.BLACK, .14f)
            oval.set(gx - gr * .82f, gy + gr * .08f - gr * .82f, gx + gr * .82f, gy + gr * .08f + gr * .82f)
            cv.drawArc(oval, 36f, 108f, false, p)
            p.style = Paint.Style.FILL; p.color = ca(Color.WHITE, .75f)
            cv.drawCircle(gx - gr * .35f, gy - gr * .38f, gr * .18f, p)
        }

        // vignette
        p.reset(); p.shader = vigShader; cv.drawRect(0f, 0f, W, H, p); p.shader = null
    }

    /** Copy the current frame onto [c] at [dst], adding the heat shimmer where the scene has one. */
    fun drawTo(c: Canvas, dst: RectF) {
        val q = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(buffer, null, dst, q)
        if (!st.heat) return
        val sx = dst.width() / W; val sy = dst.height() / H
        val y0 = (H * .42f).toInt(); val step = max(2, (2 * u).roundToInt())
        val src = Rect(); val out = RectF()
        var y = y0
        while (y < h) {
            val f = (y - y0).toFloat() / (H - y0)
            val off = (sin(y / u * .09f + t * 5) * 1.8f + sin(y / u * .031f - t * 2.3f) * 1.2f) * f * u
            src.set(0, y, w, min(h, y + step))
            out.set(dst.left + off * sx, dst.top + y * sy, dst.right + off * sx, dst.top + src.bottom * sy)
            c.drawBitmap(buffer, src, out, q)
            y += step
        }
    }

    /** A single still frame, as the home-screen widget shows it: settled particles, random moment. */
    fun still(): SkyRenderer {
        t = r(0f, 60f)
        repeat(45) { render(1 / 30f) }
        if (st.lightning) {
            nextBolt = 99f
            if (chance(.7f)) { bolts = makeBolt(); flashT = r(.19f, .3f) } else { bolts = null; flashT = 99f }
        }
        render(0f)
        return this
    }
}
