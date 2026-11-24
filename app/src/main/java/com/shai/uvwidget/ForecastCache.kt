package com.shai.uvwidget

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

/**
 * The raw 2-day hourly forecast from Open-Meteo, kept on the phone.
 *
 * Because it holds UV/temperature for every hour, the widget can compute
 * "UV right now" locally at any moment – no network needed – just by
 * interpolating between the two surrounding hours.
 */
data class ForecastCache(
    val fetchedAt: Long,
    val lat: Double,
    val lon: Double,
    val place: String,
    val hourTimes: List<Long>,      // epoch ms, one per hour
    val uv: List<Double>,
    val temp: List<Double>,
    val isDay: List<Int>,
    val dayStarts: List<Long>,      // epoch ms of local midnight
    val sunrise: List<Long>,
    val sunset: List<Long>,
    // Weather conditions per hour, used to pick the widget's background scene.
    // Empty in caches saved by versions before backgrounds existed.
    val code: List<Int> = emptyList(),       // WMO weather code
    val cloud: List<Int> = emptyList(),      // cloud cover %
    val wind: List<Double> = emptyList(),    // km/h
    val gust: List<Double> = emptyList(),    // km/h
    val vis: List<Double> = emptyList(),     // meters
) {
    val hasWeather get() = code.isNotEmpty() && code.size == hourTimes.size

    /** True while the forecast still has data for [t]. */
    fun covers(t: Long) = hourTimes.isNotEmpty() && t >= hourTimes.first() && t < hourTimes.last() + HOUR

    /** UV is 0 now and stays 0 for the next [hours] hours – nothing will change soon. */
    fun quietFor(now: Long, hours: Int): Boolean {
        if (!covers(now + hours * HOUR)) return false
        val i = indexAt(now)
        return (i..minOf(i + hours, hourTimes.lastIndex)).all { isDay[it] == 0 && uv[it] < 0.05 }
    }

    private fun indexAt(t: Long) = hourTimes.indexOfLast { it <= t }.coerceAtLeast(0)

    /** Build the widget's view of the world for the moment [now], fully offline. */
    fun snapshot(now: Long): UvData {
        val i = indexAt(now)
        val j = minOf(i + 1, hourTimes.lastIndex)
        val f = if (j > i) ((now - hourTimes[i]).toDouble() / (hourTimes[j] - hourTimes[i])).coerceIn(0.0, 1.0) else 0.0
        fun lerp(v: List<Double>) = v[i] + (v[j] - v[i]) * f

        val d = dayStarts.indexOfLast { it <= now }.coerceAtLeast(0)
        val dayStart = dayStarts[d]
        val todayUv = hourTimes.indices
            .filter { hourTimes[it] >= dayStart && hourTimes[it] < dayStart + DAY }
            .map { uv[it] }
            .let { (it + List(24) { 0.0 }).take(24) }
        val peak = todayUv.indices.maxByOrNull { todayUv[it] } ?: 12
        val hhmm = SimpleDateFormat("HH:mm", Locale.US)

        return UvData(
            uvNow = lerp(uv).coerceAtLeast(0.0),
            isDay = isDay[i] == 1,
            uvMaxToday = todayUv.maxOrNull() ?: 0.0,
            peakHour = peak,
            hourly = todayUv,
            currentHour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY),
            sunrise = hhmm.format(Date(sunrise[d])),
            sunset = hhmm.format(Date(sunset[d])),
            temperature = lerp(temp),
            place = place,
            updatedAt = fetchedAt,
            checkedAt = now,
            weatherCode = code.getOrElse(i) { -1 },
            cloudCover = cloud.getOrElse(i) { -1 },
            windKmh = wind.getOrElse(i) { 0.0 },
            gustKmh = gust.getOrElse(i) { 0.0 },
            visibilityM = vis.getOrElse(i) { -1.0 },
            rainRecently = (i - 2 until i).any { k -> code.getOrElse(k) { -1 }.let(::isWet) },
            sunriseAt = sunrise[d],
            sunsetAt = sunset[d],
        )
    }

    private fun isWet(c: Int) = c in 51..67 || c in 80..82 || c in 95..99

    fun toJson(): String = JSONObject().apply {
        put("fetchedAt", fetchedAt); put("lat", lat); put("lon", lon); put("place", place)
        put("hourTimes", JSONArray(hourTimes)); put("uv", JSONArray(uv)); put("temp", JSONArray(temp))
        put("isDay", JSONArray(isDay)); put("dayStarts", JSONArray(dayStarts))
        put("sunrise", JSONArray(sunrise)); put("sunset", JSONArray(sunset))
        put("code", JSONArray(code)); put("cloud", JSONArray(cloud)); put("wind", JSONArray(wind))
        put("gust", JSONArray(gust)); put("vis", JSONArray(vis))
    }.toString()

    companion object {
        private fun JSONArray.longs() = List(length()) { getLong(it) }
        private fun JSONArray.doubles() = List(length()) { optDouble(it, 0.0) }
        private fun JSONArray.ints() = List(length()) { optInt(it, 0) }

        fun fromJson(s: String): ForecastCache {
            val o = JSONObject(s)
            return ForecastCache(
                fetchedAt = o.getLong("fetchedAt"), lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                place = o.getString("place"),
                hourTimes = o.getJSONArray("hourTimes").longs(), uv = o.getJSONArray("uv").doubles(),
                temp = o.getJSONArray("temp").doubles(), isDay = o.getJSONArray("isDay").ints(),
                dayStarts = o.getJSONArray("dayStarts").longs(),
                sunrise = o.getJSONArray("sunrise").longs(), sunset = o.getJSONArray("sunset").longs(),
                code = o.optJSONArray("code")?.ints() ?: emptyList(),
                cloud = o.optJSONArray("cloud")?.ints() ?: emptyList(),
                wind = o.optJSONArray("wind")?.doubles() ?: emptyList(),
                gust = o.optJSONArray("gust")?.doubles() ?: emptyList(),
                vis = o.optJSONArray("vis")?.doubles() ?: emptyList(),
            )
        }

        /** Parse an Open-Meteo response requested with timeformat=unixtime. */
        fun fromApi(json: JSONObject, lat: Double, lon: Double, place: String, now: Long): ForecastCache {
            val h = json.getJSONObject("hourly")
            val d = json.getJSONObject("daily")
            fun secs(a: JSONArray) = List(a.length()) { a.getLong(it) * 1000 }
            return ForecastCache(
                fetchedAt = now, lat = lat, lon = lon, place = place,
                hourTimes = secs(h.getJSONArray("time")),
                uv = h.getJSONArray("uv_index").doubles(),
                temp = h.getJSONArray("temperature_2m").doubles(),
                isDay = h.getJSONArray("is_day").ints(),
                dayStarts = secs(d.getJSONArray("time")),
                sunrise = secs(d.getJSONArray("sunrise")),
                sunset = secs(d.getJSONArray("sunset")),
                code = h.optJSONArray("weather_code")?.ints() ?: emptyList(),
                cloud = h.optJSONArray("cloud_cover")?.ints() ?: emptyList(),
                wind = h.optJSONArray("wind_speed_10m")?.doubles() ?: emptyList(),
                gust = h.optJSONArray("wind_gusts_10m")?.doubles() ?: emptyList(),
                vis = h.optJSONArray("visibility")?.doubles() ?: emptyList(),
            )
        }
    }
}
