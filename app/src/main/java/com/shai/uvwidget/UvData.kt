package com.shai.uvwidget

import org.json.JSONArray
import org.json.JSONObject

/** Everything the widget needs to draw itself. */
data class UvData(
    val uvNow: Double,
    val isDay: Boolean,
    val uvMaxToday: Double,
    val peakHour: Int,
    val hourly: List<Double>,      // 24 values, index = hour of day
    val currentHour: Int,
    val sunrise: String,           // "06:14"
    val sunset: String,            // "16:39"
    val temperature: Double,
    val place: String,
    val updatedAt: Long,           // when the data came from the server
    val checkedAt: Long = updatedAt, // when the widget last recomputed "now"
    // Weather right now, for the background scene (defaults = unknown).
    val weatherCode: Int = -1,       // WMO code
    val cloudCover: Int = -1,        // %
    val windKmh: Double = 0.0,
    val gustKmh: Double = 0.0,
    val visibilityM: Double = -1.0,
    val rainRecently: Boolean = false, // rain in the previous two hours
    val sunriseAt: Long = 0,         // epoch ms, today
    val sunsetAt: Long = 0,
) {
    fun toJson(): String = JSONObject().apply {
        put("uvNow", uvNow); put("isDay", isDay); put("uvMaxToday", uvMaxToday)
        put("peakHour", peakHour); put("hourly", JSONArray(hourly)); put("currentHour", currentHour)
        put("sunrise", sunrise); put("sunset", sunset); put("temperature", temperature)
        put("place", place); put("updatedAt", updatedAt); put("checkedAt", checkedAt)
        put("weatherCode", weatherCode); put("cloudCover", cloudCover); put("windKmh", windKmh)
        put("gustKmh", gustKmh); put("visibilityM", visibilityM); put("rainRecently", rainRecently)
        put("sunriseAt", sunriseAt); put("sunsetAt", sunsetAt)
    }.toString()

    companion object {
        fun fromJson(s: String): UvData {
            val o = JSONObject(s)
            val h = o.getJSONArray("hourly")
            return UvData(
                uvNow = o.getDouble("uvNow"),
                isDay = o.getBoolean("isDay"),
                uvMaxToday = o.getDouble("uvMaxToday"),
                peakHour = o.getInt("peakHour"),
                hourly = List(h.length()) { h.getDouble(it) },
                currentHour = o.getInt("currentHour"),
                sunrise = o.getString("sunrise"),
                sunset = o.getString("sunset"),
                temperature = o.getDouble("temperature"),
                place = o.getString("place"),
                updatedAt = o.getLong("updatedAt"),
                checkedAt = o.optLong("checkedAt", o.getLong("updatedAt")),
                weatherCode = o.optInt("weatherCode", -1),
                cloudCover = o.optInt("cloudCover", -1),
                windKmh = o.optDouble("windKmh", 0.0),
                gustKmh = o.optDouble("gustKmh", 0.0),
                visibilityM = o.optDouble("visibilityM", -1.0),
                rainRecently = o.optBoolean("rainRecently", false),
                sunriseAt = o.optLong("sunriseAt", 0),
                sunsetAt = o.optLong("sunsetAt", 0),
            )
        }
    }
}

/** WHO UV index categories. */
enum class UvLevel(val label: String, val advice: String) {
    LOW("נמוך", "אפשר ליהנות מהשמש בבטחה"),
    MODERATE("בינוני", "משקפי שמש וקרם הגנה"),
    HIGH("גבוה", "קרם הגנה, כובע וצל בצהריים"),
    VERY_HIGH("גבוה מאוד", "הימנע משמש ישירה בצהריים"),
    EXTREME("קיצוני", "הישאר בצל ככל האפשר"),
    NIGHT("לילה", "אין קרינת UV עכשיו");

    companion object {
        fun of(uv: Double, isDay: Boolean): UvLevel = when {
            !isDay && uv < 0.5 -> NIGHT
            uv < 3 -> LOW
            uv < 6 -> MODERATE
            uv < 8 -> HIGH
            uv < 11 -> VERY_HIGH
            else -> EXTREME
        }
    }
}
