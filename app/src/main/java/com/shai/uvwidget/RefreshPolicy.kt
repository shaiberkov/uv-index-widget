package com.shai.uvwidget

import android.location.Location

/** Who asked for the refresh – each gets a different budget. */
enum class Trigger { AUTO, MANUAL, APP_OPEN }

/** Network bookkeeping kept between runs. */
data class NetState(
    val lastAttemptAt: Long = 0,
    val failures: Int = 0,
    val lastFailureAt: Long = 0,
    val callsDay: Int = 0,          // yyyyMMdd the counter belongs to
    val callsToday: Int = 0,
)

data class Decision(val fetch: Boolean, val reason: String)

/**
 * Decides whether a refresh may hit the network or should be served from the cache.
 *
 *  - Automatic refresh pulls new data from the server every ~30 min, day and night.
 *  - A tap or opening the app refetches only if the data is older than 5 min.
 *  - Moving more than 10 km always refetches (new place = new forecast).
 *  - Never two calls within 30 s, and at most [DAILY_CAP] calls per day.
 *  - After failures, automatic refreshes back off: 1, 2, 4 … up to 30 min.
 */
object RefreshPolicy {
    private const val MIN = 60_000L
    const val MIN_GAP = 30_000L
    const val MANUAL_MAX_AGE = 5 * MIN
    // Automatic refresh every ~30 min, day and night. The 15-min tick fetches once the
    // data is older than 25 min, so in practice that's one server call every 30 min.
    const val DAY_MAX_AGE = 25 * MIN
    const val NIGHT_MAX_AGE = 25 * MIN
    const val MOVE_METERS = 10_000f
    const val DAILY_CAP = 150
    const val STALE_AFTER = 120 * MIN

    fun backoff(failures: Int): Long =
        if (failures <= 0) 0 else (MIN shl (failures - 1).coerceAtMost(5)).coerceAtMost(30 * MIN)

    fun decide(
        now: Long,
        trigger: Trigger,
        cache: ForecastCache?,
        state: NetState,
        today: Int,
        location: Pair<Double, Double>?,
    ): Decision {
        val callsToday = if (state.callsDay == today) state.callsToday else 0
        if (callsToday >= DAILY_CAP) return Decision(false, "הגעת למכסה היומית")
        if (now - state.lastAttemptAt < MIN_GAP) return Decision(false, "רוענן הרגע")
        if (location == null) return Decision(false, "אין מיקום")

        if (trigger == Trigger.AUTO && now < state.lastFailureAt + backoff(state.failures)) {
            return Decision(false, "ממתין אחרי שגיאה")
        }
        if (cache == null || !cache.covers(now + 3 * 3_600_000L)) return Decision(true, "אין נתונים להמשך היום")
        // Caches saved by older versions have no weather conditions for the background.
        if (!cache.hasWeather) return Decision(true, "חסרים נתוני מזג אוויר")

        val moved = FloatArray(1).also {
            Location.distanceBetween(cache.lat, cache.lon, location.first, location.second, it)
        }[0]
        if (moved > MOVE_METERS) return Decision(true, "המיקום השתנה")

        val age = now - cache.fetchedAt
        val maxAge = when {
            trigger != Trigger.AUTO -> MANUAL_MAX_AGE
            cache.quietFor(now, 2) -> NIGHT_MAX_AGE
            else -> DAY_MAX_AGE
        }
        return if (age > maxAge) Decision(true, "הנתונים ישנים")
        else Decision(false, "הנתונים עדכניים · לפני ${age / MIN} דק׳")
    }
}
