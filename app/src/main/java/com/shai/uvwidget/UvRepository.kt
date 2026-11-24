package com.shai.uvwidget

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/** What an update did – handy for the UI and for debugging. */
data class Outcome(val fetched: Boolean, val reason: String, val data: UvData?, val error: String?)

object UvRepository {

    /**
     * The single entry point. Decides (via [RefreshPolicy]) whether to call the server
     * or just recompute "now" from the cached forecast, then stores the new snapshot.
     */
    suspend fun update(context: Context, trigger: Trigger): Outcome = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val location = currentLocation(context, fresh = trigger == Trigger.APP_OPEN)
            ?: UvStore.loadLocation(context)
        location?.let { UvStore.saveLocation(context, it.first, it.second) }

        var cache = UvStore.loadCache(context)
        val decision = RefreshPolicy.decide(
            now, trigger, cache, UvStore.netState(context), UvStore.today(now), location,
        )

        var error: String? = null
        if (decision.fetch && location != null) {
            UvStore.markAttempt(context, now)
            try {
                cache = fetch(context, location.first, location.second, now)
                UvStore.saveCache(context, cache)
                UvStore.markSuccess(context)
            } catch (t: Throwable) {
                UvStore.markFailure(context, now)
                error = friendly(t)
            }
        }

        if (cache == null) {
            val msg = error ?: if (location == null) "אין מיקום – פתח את האפליקציה ואשר הרשאת מיקום" else decision.reason
            UvStore.setError(context, msg)
            return@withContext Outcome(false, decision.reason, null, msg)
        }

        val snap = cache.snapshot(now)
        UvStore.save(context, snap)
        // A failed call only matters if what we're showing is getting old.
        val shownError = error?.takeIf { now - cache.fetchedAt > RefreshPolicy.STALE_AFTER }
        UvStore.setError(context, shownError)
        Outcome(decision.fetch && error == null, decision.reason, snap, error)
    }

    /** Quick, offline check used by the widget tap to decide whether a job is worth starting. */
    fun wouldFetch(context: Context, trigger: Trigger): Decision {
        val now = System.currentTimeMillis()
        return RefreshPolicy.decide(
            now, trigger, UvStore.loadCache(context), UvStore.netState(context),
            UvStore.today(now), UvStore.loadLocation(context),
        )
    }

    // ---------- network ----------

    private suspend fun fetch(context: Context, lat: Double, lon: Double, now: Long): ForecastCache {
        val json = JSONObject(downloadWithRetry(buildUrl(lat, lon)))
        return ForecastCache.fromApi(json, lat, lon, placeName(context, lat, lon), now)
    }

    private fun buildUrl(lat: Double, lon: Double) =
        String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f", lat, lon) +
            "&hourly=uv_index,temperature_2m,is_day,weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m,visibility" +
            "&daily=sunrise,sunset" +
            "&timezone=auto&timeformat=unixtime&forecast_days=2"

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            if (conn.responseCode == 429) error("השרת עמוס – ננסה מאוחר יותר")
            if (conn.responseCode != 200) error("שגיאת שרת ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Network can take a moment to come up for a background app – try a few times. */
    private suspend fun downloadWithRetry(url: String): String {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return download(url)
            } catch (e: java.io.IOException) {
                last = e
                if (attempt < 2) delay(2_000L * (attempt + 1))
            }
        }
        throw last!!
    }

    private fun friendly(t: Throwable): String = when (t) {
        is java.net.UnknownHostException, is java.net.ConnectException,
        is java.net.SocketTimeoutException -> "אין חיבור לאינטרנט – נסה שוב"
        is IllegalStateException -> t.message ?: "שגיאה בטעינה"
        else -> "שגיאה בטעינה – נסה שוב"
    }

    // ---------- location ----------

    fun hasLocationPermission(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * [fresh] asks the GPS/network for a new fix – only possible while the app is open;
     * in the background we use the newest cached fix so we never hang.
     */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context, fresh: Boolean): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)

        if (fresh && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
                else -> providers.firstOrNull()
            }
            if (provider != null) {
                val fix = withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            lm.getCurrentLocation(provider, signal, context.mainExecutor) {
                                if (cont.isActive) cont.resume(it)
                            }
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
                if (fix != null) return fix.latitude to fix.longitude
            }
        }
        val last = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        return last?.let { it.latitude to it.longitude }
    }

    @Suppress("DEPRECATION")
    private fun placeName(context: Context, lat: Double, lon: Double): String {
        UvStore.cachedPlace(context, lat, lon)?.let { return it }
        val name = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val a = Geocoder(context, Locale("he")).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull()
        if (name != null) UvStore.savePlace(context, lat, lon, name)
        return name ?: UvStore.loadCache(context)?.place ?: "המיקום שלי"
    }
}
