package com.shai.uvwidget

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny persistence layer: forecast cache, last snapshot, location, network bookkeeping. */
object UvStore {
    private const val PREFS = "uv_store"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- snapshot the widget draws ----
    fun save(c: Context, data: UvData) = prefs(c).edit().putString("data", data.toJson()).apply()

    fun load(c: Context): UvData? =
        prefs(c).getString("data", null)?.let { runCatching { UvData.fromJson(it) }.getOrNull() }

    // ---- raw forecast cache ----
    fun saveCache(c: Context, cache: ForecastCache) = prefs(c).edit().putString("cache", cache.toJson()).apply()

    fun loadCache(c: Context): ForecastCache? =
        prefs(c).getString("cache", null)?.let { runCatching { ForecastCache.fromJson(it) }.getOrNull() }

    // ---- location ----
    fun saveLocation(c: Context, lat: Double, lon: Double) =
        prefs(c).edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()

    fun loadLocation(c: Context): Pair<Double, Double>? {
        val p = prefs(c)
        if (!p.contains("lat")) return null
        return p.getFloat("lat", 0f).toDouble() to p.getFloat("lon", 0f).toDouble()
    }

    // ---- place-name cache (geocoding is slow; a ~1 km grid is plenty) ----
    private fun placeKey(lat: Double, lon: Double) = String.format(Locale.US, "place_%.2f_%.2f", lat, lon)
    fun cachedPlace(c: Context, lat: Double, lon: Double): String? = prefs(c).getString(placeKey(lat, lon), null)
    fun savePlace(c: Context, lat: Double, lon: Double, name: String) =
        prefs(c).edit().putString(placeKey(lat, lon), name).apply()

    // ---- network bookkeeping for the rate limiter ----
    fun today(now: Long = System.currentTimeMillis()) =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now)).toInt()

    fun netState(c: Context): NetState {
        val p = prefs(c)
        return NetState(
            lastAttemptAt = p.getLong("net_last_attempt", 0),
            failures = p.getInt("net_failures", 0),
            lastFailureAt = p.getLong("net_last_failure", 0),
            callsDay = p.getInt("net_calls_day", 0),
            callsToday = p.getInt("net_calls_today", 0),
        )
    }

    fun markAttempt(c: Context, now: Long) {
        val s = netState(c)
        val day = today(now)
        val calls = if (s.callsDay == day) s.callsToday + 1 else 1
        prefs(c).edit()
            .putLong("net_last_attempt", now)
            .putInt("net_calls_day", day)
            .putInt("net_calls_today", calls)
            .apply()
    }

    fun markSuccess(c: Context) = prefs(c).edit().putInt("net_failures", 0).putLong("net_last_failure", 0).apply()

    fun markFailure(c: Context, now: Long) {
        val f = netState(c).failures + 1
        prefs(c).edit().putInt("net_failures", f).putLong("net_last_failure", now).apply()
    }

    // ---- error shown on the widget ----
    fun setError(c: Context, msg: String?) = prefs(c).edit().putString("error", msg).apply()
    fun error(c: Context): String? = prefs(c).getString("error", null)
}
