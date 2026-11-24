package com.shai.uvwidget

import android.graphics.Color

private fun c(s: String) = Color.parseColor(s)

// ---------- what a scene is made of ----------
// Positions are fractions of the width/height; sizes of the sun, moon and fog are
// fractions of the "reference height" (the widget's height). See SkyRenderer.

class Glow(val color: Int, val a: Float, val x: Float, val y: Float, val r: Float)
class SunCfg(val x: Float, val y: Float, val r: Float, val core: Int, val glow: Int,
             val rays: Boolean = false, val flare: Boolean = false, val int: Float = 1f)
class MoonCfg(val x: Float, val y: Float, val r: Float, val phase: Float, val glow: Int)
class StarsCfg(val n: Int, val a: Float, val max: Float, val shoot: Boolean = false)
class CloudCfg(val n: Int, val col: Int, val shade: Int, val hi: Int,
               val a0: Float, val a1: Float, val y0: Float, val y1: Float,
               val s0: Float, val s1: Float, val v0: Float, val v1: Float, val dir: Int = 1)
class RainCfg(val n: Int, val v0: Float, val v1: Float, val angle: Float,
              val len0: Float, val len1: Float, val a: Float, val col: Int, val splash: Boolean = false)
class BokehCfg(val n: Int, val cols: IntArray, val a: Float)
class SnowCfg(val n: Int, val speed: Float, val wind: Float)
class FogCfg(val n: Int, val col: Int, val a: Float, val speed: Float, val dir: Int = 0)
class WindCfg(val streaks: Int, val leaves: Int)
class DustCfg(val n: Int, val col: Int)
class RainbowCfg(val x: Float, val y: Float, val r: Float, val a: Float)

class SkyStyle(
    val sky: IntArray,
    val glows: List<Glow> = emptyList(),
    val sun: SunCfg? = null,
    val moon: MoonCfg? = null,
    val stars: StarsCfg? = null,
    val clouds: CloudCfg? = null,
    val rain: RainCfg? = null,
    val glass: Int = 0,
    val bokeh: BokehCfg? = null,
    val snow: SnowCfg? = null,
    val hail: Int = 0,
    val lightning: Boolean = false,
    val fog: FogCfg? = null,
    val wind: WindCfg? = null,
    val dust: DustCfg? = null,
    val heat: Boolean = false,
    val rainbow: RainbowCfg? = null,
    val birds: Int = 0,
    val birdCol: Int = c("#2a2a3a"),
    val vig: Float = .16f,
)

private val NIGHT = intArrayOf(c("#040713"), c("#0a1533"), c("#18285a"))

/**
 * The 22 approved backgrounds. [advice] replaces the UV advice line when the weather
 * matters more than the UV level (rain, storms, dust…); null keeps the UV advice.
 */
enum class SkyScene(val label: String, val icon: String, val advice: String?, val style: SkyStyle) {
    DAWN("שחר", "🌄", null, SkyStyle(
        sky = intArrayOf(c("#262b57"), c("#6b5690"), c("#e8988a")),
        glows = listOf(Glow(c("#ffb08a"), .55f, .2f, 1.05f, .75f), Glow(c("#9b7fd1"), .3f, .85f, .15f, .55f)),
        stars = StarsCfg(45, .55f, .55f),
        clouds = CloudCfg(3, c("#f2b8b0"), c("#6d5487"), c("#ffd6c4"), .35f, .55f, .45f, .75f, .3f, .5f, .004f, .008f),
    )),
    MORNING("בוקר", "🌅", null, SkyStyle(
        sky = intArrayOf(c("#5a9fe3"), c("#9fc9ee"), c("#ffd3a1")),
        glows = listOf(Glow(c("#ffc78a"), .5f, .1f, .9f, .6f), Glow(Color.WHITE, .15f, .7f, .2f, .5f)),
        sun = SunCfg(.13f, .8f, .15f, c("#fff3d2"), c("#ffc47a"), rays = true, flare = true),
        clouds = CloudCfg(3, Color.WHITE, c("#c4d2e8"), c("#fff1de"), .75f, .95f, .08f, .4f, .22f, .38f, .006f, .012f),
        birds = 4,
    )),
    NOON("צהריים שמשי", "☀️", null, SkyStyle(
        sky = intArrayOf(c("#1667cf"), c("#3f93e8"), c("#9fd0f7")),
        glows = listOf(Glow(Color.WHITE, .18f, .85f, .05f, .6f), Glow(c("#7fc4ff"), .25f, .2f, .9f, .6f)),
        sun = SunCfg(.86f, .07f, .16f, c("#fffcef"), c("#fff0b8"), rays = true, flare = true, int = 1.15f),
        clouds = CloudCfg(2, Color.WHITE, c("#cfdcf0"), Color.WHITE, .85f, 1f, .35f, .6f, .2f, .3f, .008f, .012f),
    )),
    AFTERNOON("אחר הצהריים", "🌤️", null, SkyStyle(
        sky = intArrayOf(c("#3878c0"), c("#86b0d8"), c("#f1c487")),
        glows = listOf(Glow(c("#ffcf8a"), .4f, .8f, .5f, .6f)),
        sun = SunCfg(.78f, .4f, .14f, c("#fff0c4"), c("#ffbf6a"), rays = true, flare = true),
        clouds = CloudCfg(3, c("#fff6e6"), c("#b6b9cc"), c("#ffe2b0"), .75f, .9f, .05f, .4f, .22f, .4f, .006f, .01f),
        birds = 3,
    )),
    SUNSET("שקיעה", "🌇", null, SkyStyle(
        sky = intArrayOf(c("#33306e"), c("#b8507c"), c("#ff9548")),
        glows = listOf(Glow(c("#ff7a3d"), .6f, .82f, 1f, .7f), Glow(c("#8a4fa0"), .35f, .1f, .1f, .6f)),
        sun = SunCfg(.8f, .94f, .19f, c("#ffe0a3"), c("#ff8a4a"), rays = true, int = .9f),
        clouds = CloudCfg(4, c("#ff9a86"), c("#4e3466"), c("#ffcf96"), .65f, .85f, .1f, .5f, .3f, .5f, .004f, .008f),
        birds = 5, birdCol = c("#2a1a30"),
    )),
    EVENING("ערב", "🌆", null, SkyStyle(
        sky = intArrayOf(c("#111a3d"), c("#2c3d78"), c("#b06c87")),
        glows = listOf(Glow(c("#ff9b7a"), .35f, .5f, 1.05f, .7f)),
        stars = StarsCfg(40, .5f, .6f),
        moon = MoonCfg(.18f, .24f, .065f, .72f, c("#ffe9c4")),
        clouds = CloudCfg(3, c("#3c3f70"), c("#1a1d45"), c("#c98aa6"), .55f, .75f, .45f, .75f, .35f, .55f, .003f, .006f),
    )),
    NIGHT_CLEAR("לילה בהיר", "🌕", null, SkyStyle(
        sky = NIGHT,
        glows = listOf(Glow(c("#3b5bb5"), .25f, .8f, .9f, .7f), Glow(c("#7a8cff"), .12f, .2f, .2f, .5f)),
        stars = StarsCfg(150, 1f, .95f, shoot = true),
        moon = MoonCfg(.17f, .23f, .09f, 0f, c("#d6e0ff")),
    )),
    PARTLY_CLOUDY("מעונן חלקית", "⛅", null, SkyStyle(
        sky = intArrayOf(c("#3a84d6"), c("#6aa7e6"), c("#b8d9f5")),
        sun = SunCfg(.22f, .16f, .13f, c("#fffbea"), c("#fff0bf"), rays = true, flare = true),
        clouds = CloudCfg(6, Color.WHITE, c("#b7c7dc"), Color.WHITE, .85f, 1f, -.05f, .55f, .3f, .55f, .01f, .018f),
    )),
    PARTLY_CLOUDY_NIGHT("מעונן חלקית בלילה", "☁️", null, SkyStyle(
        sky = NIGHT,
        glows = listOf(Glow(c("#3b5bb5"), .2f, .7f, .9f, .7f)),
        stars = StarsCfg(90, .8f, .9f),
        moon = MoonCfg(.26f, .28f, .085f, .25f, c("#d6e0ff")),
        clouds = CloudCfg(5, c("#53618a"), c("#161d33"), c("#b7c6ea"), .7f, .9f, 0f, .55f, .3f, .55f, .008f, .014f),
    )),
    OVERCAST("מעונן", "☁️", null, SkyStyle(
        sky = intArrayOf(c("#6a7686"), c("#8b97a5"), c("#b2bbc5")),
        glows = listOf(Glow(Color.WHITE, .18f, .5f, .2f, .6f)),
        clouds = CloudCfg(11, c("#d3d9e0"), c("#77818e"), c("#eef1f4"), .8f, .95f, -.2f, .6f, .4f, .75f, .004f, .008f),
    )),
    DRIZZLE("גשם קל", "🌦️", "טפטוף קל, כדאי מטרייה", SkyStyle(
        sky = intArrayOf(c("#53637a"), c("#6f7e8e"), c("#97a3ae")),
        clouds = CloudCfg(7, c("#a9b3bf"), c("#56606d"), c("#c9d0d8"), .85f, .95f, -.2f, .4f, .4f, .7f, .005f, .009f),
        rain = RainCfg(90, 380f, 520f, .06f, 8f, 15f, .32f, c("#e2eaf3")),
        glass = 14,
    )),
    HEAVY_RAIN("גשם כבד", "🌧️", "גשם חזק, היזהרו מהצפות", SkyStyle(
        sky = intArrayOf(c("#2c3644"), c("#424e5c"), c("#5c6876")), vig = .25f,
        clouds = CloudCfg(9, c("#6c7684"), c("#262d38"), c("#8f99a6"), .85f, 1f, -.25f, .35f, .45f, .8f, .008f, .014f),
        rain = RainCfg(260, 650f, 850f, .16f, 16f, 28f, .42f, c("#cfdbe8"), splash = true),
        glass = 22,
    )),
    STORM("סופת רעמים", "⛈️", "סופת ברקים, הישארו בבית", SkyStyle(
        sky = intArrayOf(c("#151a26"), c("#252c3c"), c("#363f52")), vig = .3f, lightning = true,
        clouds = CloudCfg(9, c("#3b4354"), c("#10141c"), c("#5d6880"), .9f, 1f, -.25f, .35f, .45f, .8f, .012f, .02f),
        rain = RainCfg(220, 700f, 900f, .22f, 16f, 28f, .38f, c("#c4d0e0"), splash = true),
    )),
    NIGHT_RAIN("גשם בלילה", "🌧️", "גשם לילי, כבישים רטובים", SkyStyle(
        sky = intArrayOf(c("#070c1a"), c("#111a2e"), c("#1e2a44")),
        clouds = CloudCfg(6, c("#2a3550"), c("#0b0f1a"), c("#3f4d70"), .8f, .95f, -.25f, .3f, .45f, .75f, .006f, .01f),
        bokeh = BokehCfg(12, intArrayOf(c("#ffb257"), c("#ff6f5e"), c("#ffd98c"), c("#7fc8ff")), 1f),
        rain = RainCfg(150, 550f, 750f, .1f, 14f, 24f, .28f, c("#b9cbe6"), splash = true),
        glass = 24,
    )),
    RAINBOW("קשת אחרי גשם", "🌈", null, SkyStyle(
        sky = intArrayOf(c("#4a78b2"), c("#86a9cd"), c("#dbe3e8")),
        glows = listOf(Glow(c("#fff4d0"), .35f, .1f, .2f, .6f)),
        sun = SunCfg(.1f, .18f, .12f, c("#fff8e0"), c("#ffe7a6"), rays = true),
        rainbow = RainbowCfg(.62f, 1.25f, 1.05f, .4f),
        clouds = CloudCfg(4, c("#c9d2dd"), c("#6a7686"), Color.WHITE, .75f, .9f, -.15f, .25f, .35f, .55f, .012f, .018f),
        rain = RainCfg(35, 420f, 540f, .05f, 8f, 14f, .22f, Color.WHITE),
        glass = 10,
    )),
    HAIL("ברד", "🧊", "ברד! מצאו מחסה לרכב", SkyStyle(
        sky = intArrayOf(c("#38414f"), c("#535d6b"), c("#78828f")), vig = .22f,
        clouds = CloudCfg(8, c("#78828f"), c("#2a3039"), c("#a2abb6"), .85f, 1f, -.25f, .35f, .45f, .8f, .01f, .016f),
        hail = 70,
        rain = RainCfg(50, 600f, 750f, .1f, 12f, 20f, .25f, c("#dfe7f0")),
    )),
    SNOW("שלג", "🌨️", "שלג מחזיר קרינה, גם משקפי שמש", SkyStyle(
        sky = intArrayOf(c("#7a90ab"), c("#a3b4c8"), c("#d5dde7")),
        glows = listOf(Glow(Color.WHITE, .25f, .5f, .1f, .6f)),
        clouds = CloudCfg(6, c("#e4e9f0"), c("#8f9db0"), Color.WHITE, .75f, .9f, -.2f, .35f, .4f, .7f, .004f, .007f),
        snow = SnowCfg(150, 1f, -10f),
    )),
    SNOW_NIGHT("שלג בלילה", "❄️", "שלג בלילה, היזהרו מקרח בכבישים", SkyStyle(
        sky = intArrayOf(c("#0d1530"), c("#1b2850"), c("#36466e")),
        glows = listOf(Glow(c("#9fb4ff"), .2f, .3f, .1f, .6f)),
        clouds = CloudCfg(5, c("#3a4872"), c("#121a33"), c("#7f90c0"), .7f, .85f, -.25f, .25f, .4f, .7f, .004f, .007f),
        bokeh = BokehCfg(7, intArrayOf(c("#ffcf7a"), c("#ffb05c")), .6f),
        snow = SnowCfg(170, .9f, -6f),
    )),
    FOG("ערפל", "🌫️", "ערפל כבד, ראות נמוכה בכבישים", SkyStyle(
        sky = intArrayOf(c("#7d8994"), c("#9aa4ac"), c("#b9c0c5")), vig = .2f,
        sun = SunCfg(.72f, .28f, .11f, c("#fffaf0"), Color.WHITE, int = .4f),
        fog = FogCfg(5, c("#eef1f3"), .45f, 1f),
    )),
    WINDY("רוחות חזקות", "💨", "רוחות חזקות, היזהרו בחוץ", SkyStyle(
        sky = intArrayOf(c("#3a7cc4"), c("#74a5d9"), c("#c4dbef")),
        sun = SunCfg(.15f, .12f, .11f, c("#fffbea"), c("#fff0bf"), rays = true),
        clouds = CloudCfg(5, Color.WHITE, c("#bccbdd"), Color.WHITE, .75f, .9f, 0f, .45f, .25f, .45f, .05f, .08f, dir = -1),
        wind = WindCfg(16, 9),
    )),
    DUST("אובך וסופת אבק", "🏜️", "אובך כבד, הימנעו ממאמץ בחוץ", SkyStyle(
        sky = intArrayOf(c("#9a6331"), c("#bd8546"), c("#d8ab6e")), vig = .28f,
        glows = listOf(Glow(c("#ffd9a0"), .35f, .72f, .3f, .5f)),
        sun = SunCfg(.72f, .3f, .09f, c("#fff4df"), c("#ffe2b5"), int = .5f),
        fog = FogCfg(5, c("#dcb07a"), .5f, 4f, dir = 1),
        dust = DustCfg(240, c("#f6dcb0")),
    )),
    HEAT("שרב וגל חום", "🔥", "שרב! שתו הרבה והישארו בצל", SkyStyle(
        sky = intArrayOf(c("#e8752a"), c("#f3a443"), c("#f9cf76")),
        glows = listOf(Glow(c("#fff1b8"), .45f, .85f, .1f, .7f), Glow(c("#ff5a1f"), .3f, .1f, 1f, .6f)),
        sun = SunCfg(.84f, .1f, .19f, c("#fffbe6"), c("#fff0a8"), rays = true, flare = true, int = 1.3f),
        heat = true,
    ));

    companion object {
        private const val MIN = 60_000L

        /** Choose the background for the weather and time of day in [d]. */
        fun pick(d: UvData, now: Long = d.checkedAt): SkyScene {
            val code = d.weatherCode
            val day = d.isDay
            val hail = code == 96 || code == 99
            val storm = code == 95
            val snow = code in 71..77 || code == 85 || code == 86
            val heavy = code == 63 || code == 65 || code == 67 || code == 81 || code == 82
            val rain = code in 51..67 || code == 80 || heavy
            val fog = code == 45 || code == 48
            val haze = code in 0..3 && d.visibilityM in 0.0..3000.0
            val windy = d.gustKmh >= 50 || d.windKmh >= 35

            when {
                hail -> return HAIL
                storm -> return STORM
                snow -> return if (day) SNOW else SNOW_NIGHT
                rain && !day -> return NIGHT_RAIN
                heavy -> return HEAVY_RAIN
                rain -> return DRIZZLE
                fog -> return FOG
                haze && day -> return DUST
                day && d.temperature >= 35 -> return HEAT
                windy && day -> return WINDY
                day && d.rainRecently && code in 0..3 -> return RAINBOW
                code == 3 || d.cloudCover >= 85 -> return if (day) OVERCAST else PARTLY_CLOUDY_NIGHT
                code == 1 || code == 2 || d.cloudCover in 35..84 ->
                    if (!nearTwilight(d, now)) return if (day) PARTLY_CLOUDY else PARTLY_CLOUDY_NIGHT
            }
            return byTimeOfDay(d, now)
        }

        private fun nearTwilight(d: UvData, now: Long): Boolean {
            if (d.sunriseAt == 0L) return false
            return now in (d.sunriseAt - 60 * MIN)..(d.sunriseAt + 60 * MIN) ||
                now in (d.sunsetAt - 75 * MIN)..(d.sunsetAt + 75 * MIN)
        }

        /** Clear sky: the scene follows the sun. */
        private fun byTimeOfDay(d: UvData, now: Long): SkyScene {
            if (d.sunriseAt == 0L) {   // old data without sun times: fall back to the clock
                val h = d.currentHour
                return when {
                    !d.isDay -> NIGHT_CLEAR
                    h < 9 -> MORNING
                    h < 14 -> NOON
                    else -> AFTERNOON
                }
            }
            val rise = d.sunriseAt
            val set = d.sunsetAt
            val noon = (rise + set) / 2
            return when {
                now < rise - 60 * MIN -> NIGHT_CLEAR
                now < rise -> DAWN
                now < rise + 150 * MIN -> MORNING
                now < set - 75 * MIN -> if (now < noon + 90 * MIN) NOON else AFTERNOON
                now < set + 15 * MIN -> SUNSET
                now < set + 75 * MIN -> EVENING
                else -> NIGHT_CLEAR
            }
        }
    }
}
