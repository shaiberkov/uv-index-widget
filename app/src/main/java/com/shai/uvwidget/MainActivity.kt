package com.shai.uvwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Small setup screen: asks for location permission (a widget can't ask by itself),
 * does the first fetch, offers to pin the widget, and installs app updates.
 */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var updateButton: Button
    private lateinit var sky: SkyView
    private var update: UpdateInfo? = null
    private var downloadedApk: File? = null

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh()
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        UvWorker.schedule(this)

        if (UvRepository.hasLocationPermission(this)) refresh()
        else askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        )
        lifecycleScope.launch { showUpdate(UpdateChecker.check(this@MainActivity, force = true)) }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "allow installs" settings screen with an APK ready → install it.
        val apk = downloadedApk
        if (apk != null && UpdateChecker.canInstall(this)) UpdateChecker.install(this, apk)
    }

    private fun refresh() {
        status.text = "טוען נתונים…"
        lifecycleScope.launch {
            val out = UvRepository.update(this@MainActivity, Trigger.APP_OPEN)
            val it = out.data
            if (it == null) {
                status.text = "⚠️ ${out.error ?: out.reason}"
            } else {
                sky.scene = SkyScene.pick(it)
                val level = UvLevel.of(it.uvNow, it.isDay)
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(it.updatedAt))
                val source = if (out.fetched) "נטען עכשיו מהשרת" else "מהמטמון · ${out.reason}"
                status.text = "📍 ${it.place}\n\nמדד UV עכשיו: ${it.uvNow.roundToInt()} (${level.label})\n" +
                    "שיא היום: ${it.uvMaxToday.roundToInt()}\n\n${level.advice}\n\n" +
                    "נתונים מ-$time · $source" + (out.error?.let { e -> "\n⚠️ $e" } ?: "")
            }
            runCatching { UvWidget.pushToWidgets(this@MainActivity) }
        }
    }

    private fun showUpdate(info: UpdateInfo?) {
        update = info
        if (info == null) {
            updateButton.visibility = View.GONE
            return
        }
        updateButton.text = "⬆  עדכון לגרסה ${info.version}"
        updateButton.visibility = View.VISIBLE
    }

    private fun installUpdate() {
        val info = update ?: return
        updateButton.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                UpdateChecker.download(this@MainActivity, info) { pct -> updateButton.text = "מוריד… $pct%" }
            }.onSuccess { apk ->
                downloadedApk = apk
                updateButton.isEnabled = true
                updateButton.text = "⬆  התקן גרסה ${info.version}"
                if (UpdateChecker.canInstall(this@MainActivity)) {
                    UpdateChecker.install(this@MainActivity, apk)
                } else {
                    status.text = "אשר ל-UV Index להתקין עדכונים ואז חזור לכאן"
                    UpdateChecker.openInstallPermissionScreen(this@MainActivity)
                }
            }.onFailure {
                updateButton.isEnabled = true
                updateButton.text = "⬆  נסה שוב – עדכון ${info.version}"
                status.text = "⚠️ ${it.message ?: "ההורדה נכשלה"}"
            }
        }
    }

    private fun pinWidget() {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, UvWidgetReceiver::class.java), null, null)
        } else {
            status.text = "לחיצה ארוכה על מסך הבית ← ווידג'טים ← UV Index"
        }
    }

    private fun buildUi(): View {
        val dp = resources.displayMetrics.density
        // The background always follows the current weather; it can't be changed by hand.
        sky = SkyView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            UvStore.load(this@MainActivity)?.let { d -> scene = SkyScene.pick(d, System.currentTimeMillis()) }
        }
        fun button(text: String, solid: Boolean = false, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            setTextColor(if (solid) Color.parseColor("#E8430C") else Color.WHITE)
            textSize = 16f
            isAllCaps = false
            typeface = if (solid) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = GradientDrawable().apply {
                cornerRadius = 28 * dp
                setColor(if (solid) Color.WHITE else Color.argb(60, 255, 255, 255))
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (56 * dp).toInt()).apply {
                topMargin = (12 * dp).toInt()
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * dp).toInt(), (28 * dp).toInt(), (28 * dp).toInt(), (28 * dp).toInt())
            layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            addView(TextView(context).apply {
                text = "☀️ UV Index"
                textSize = 34f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setShadowLayer(12 * dp, 0f, 1 * dp, Color.argb(90, 0, 0, 0))
            })
            status = TextView(context).apply {
                textSize = 17f
                setTextColor(Color.WHITE)
                setShadowLayer(10 * dp, 0f, 1 * dp, Color.argb(110, 0, 0, 0))
                gravity = Gravity.CENTER
                setPadding(0, (24 * dp).toInt(), 0, (24 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
            addView(status)
            updateButton = button("", solid = true) { installUpdate() }.apply { visibility = View.GONE }
            addView(updateButton)
            addView(button("➕  הוסף ווידג'ט למסך הבית") { pinWidget() })
            addView(button("↻  רענן עכשיו") {
                if (UvRepository.hasLocationPermission(this@MainActivity)) refresh()
                else askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            })
            addView(TextView(context).apply {
                text = "גרסה ${BuildConfig.VERSION_NAME}"
                textSize = 12f
                setTextColor(Color.argb(200, 255, 255, 255))
                setShadowLayer(8 * dp, 0f, 1 * dp, Color.argb(90, 0, 0, 0))
                gravity = Gravity.CENTER
                setPadding(0, (16 * dp).toInt(), 0, 0)
            })
        }
        return android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(sky)
            addView(content)
        }
    }
}
