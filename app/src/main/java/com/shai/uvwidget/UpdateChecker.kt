package com.shai.uvwidget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

/**
 * Self-update through GitHub Releases:
 * the newest release's tag (e.g. "v1.8") is compared with this build's version,
 * and its .apk asset is downloaded and handed to Android's installer.
 */
object UpdateChecker {
    private const val PREFS = "uv_update"
    private const val CHECK_EVERY = 60 * 60 * 1000L         // once an hour – GitHub allows 60 calls/hour
    private const val CHANNEL = "uv_update"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** "1.10" > "1.9" – compares dot-separated numbers. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Last update found by a previous check (no network). */
    fun cached(c: Context): UpdateInfo? {
        val p = prefs(c)
        val v = p.getString("version", null) ?: return null
        if (!isNewer(v, BuildConfig.VERSION_NAME)) return null
        return UpdateInfo(v, p.getString("url", "")!!, p.getString("notes", "")!!)
    }

    /** Ask GitHub for the latest release. Rate-limited unless [force]. */
    suspend fun check(c: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("checkedAt", 0) < CHECK_EVERY) return@withContext cached(c)
        p.edit().putLong("checkedAt", now).apply()

        runCatching {
            val conn = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "UvIndexApp")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = try {
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }

            val json = JSONObject(body)
            val version = json.getString("tag_name").trimStart('v', 'V')
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
            p.edit()
                .putString("version", version)
                .putString("url", apk.getString("browser_download_url"))
                .putString("notes", json.optString("body", ""))
                .apply()
            cached(c)
        }.getOrElse { cached(c) }
    }

    /** Download the APK into the app's cache; reports progress 0..100. */
    suspend fun download(c: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(c.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "UvIndex-${info.version}.apk")
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "UvIndexApp")
            try {
                if (conn.responseCode != 200) error("הורדה נכשלה (${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) withContext(Dispatchers.Main) { onProgress((done * 100 / total).toInt()) }
                        }
                    }
                }
            } finally { conn.disconnect() }
            out
        }

    /** True if Android already lets this app install updates. */
    fun canInstall(c: Context) = c.packageManager.canRequestPackageInstalls()

    /** One-time screen where the user allows "install unknown apps" for this app. */
    fun openInstallPermissionScreen(c: Context) {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Hand the downloaded APK to Android's installer (the user confirms). */
    fun install(c: Context, apk: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", apk)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Background check (from the worker): notify once per new version. */
    suspend fun checkAndNotify(c: Context) {
        val info = check(c) ?: return
        val p = prefs(c)
        if (p.getString("notified", null) == info.version) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "עדכוני אפליקציה", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("עדכון חדש ל-UV Index")
            .setContentText("גרסה ${info.version} מוכנה – הקש כדי להתקין")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(77, n)
        p.edit().putString("notified", info.version).apply()
    }
}
