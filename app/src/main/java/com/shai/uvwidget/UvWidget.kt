package com.shai.uvwidget

import android.content.Context
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class UvWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    // Data lives in Glance's own state so every update() redraws with fresh values.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // First draw: seed the widget state from the app's store.
        val seed = UvStore.load(context)
        val seedError = UvStore.error(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val data = prefs[DATA_KEY]?.let { runCatching { UvData.fromJson(it) }.getOrNull() } ?: seed
            val error = if (prefs[DATA_KEY] != null || prefs[ERROR_KEY] != null) prefs[ERROR_KEY] else seedError
            val refreshing = prefs[REFRESHING_KEY] == true
            val status = prefs[STATUS_KEY]
            val update = prefs[UPDATE_KEY]
            if (data == null) EmptyState(error) else UvCard(data, error, refreshing, status, update)
        }
    }

    companion object {
        private val DATA_KEY = stringPreferencesKey("uv_data")
        private val ERROR_KEY = stringPreferencesKey("uv_error")
        private val REFRESHING_KEY = booleanPreferencesKey("uv_refreshing")
        private val STATUS_KEY = stringPreferencesKey("uv_status")
        private val UPDATE_KEY = stringPreferencesKey("uv_update")

        /** Copy the latest stored reading into every placed widget and redraw it. */
        suspend fun pushToWidgets(context: Context, refreshing: Boolean = false, status: String? = null) {
            val data = UvStore.load(context)
            val error = UvStore.error(context)
            val update = UpdateChecker.cached(context)?.version   // offline; null once installed
            val widget = UvWidget()
            GlanceAppWidgetManager(context).getGlanceIds(UvWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    if (data != null) prefs[DATA_KEY] = data.toJson() else prefs.remove(DATA_KEY)
                    if (error != null) prefs[ERROR_KEY] = error else prefs.remove(ERROR_KEY)
                    prefs[REFRESHING_KEY] = refreshing
                    if (status != null) prefs[STATUS_KEY] = status else prefs.remove(STATUS_KEY)
                    if (update != null) prefs[UPDATE_KEY] = update else prefs.remove(UPDATE_KEY)
                }
                widget.update(context, id)
            }
        }
    }
}

class UvWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UvWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        UvWorker.schedule(context)
        UvWorker.refreshNow(context, Trigger.AUTO)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        UvWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        UvWorker.cancel(context)
    }
}

/** Tapping the widget refreshes right away and shows "מרענן…" meanwhile. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val decision = UvRepository.wouldFetch(context, Trigger.MANUAL)
        if (decision.fetch) {
            UvWidget.pushToWidgets(context, refreshing = true)
            UvWorker.refreshNow(context, Trigger.MANUAL)   // the worker clears "מרענן…" when done
        } else {
            // Data is fresh enough: recompute "now" from the cache, no network,
            // and flash a short confirmation inside the widget itself.
            val out = UvRepository.update(context, Trigger.MANUAL)
            val loaded = out.data?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.updatedAt)) }
            UvWidget.pushToWidgets(context, status = if (loaded != null) "✓ עדכני · נטען $loaded" else "✓ עדכני")
            delay(4_000)
            UvWidget.pushToWidgets(context)
        }
    }
}

// ---------- UI ----------

private val White = ColorProvider(Color.White)
private val WhiteSoft = ColorProvider(Color.White.copy(alpha = 0.78f))
private val WhiteFaint = ColorProvider(Color.White.copy(alpha = 0.22f))
private val BarDim = ColorProvider(Color.White.copy(alpha = 0.38f))
private val ErrorTint = ColorProvider(Color(0xFFFFC2B8))
private val UpdateInk = ColorProvider(Color(0xFF1B1F3B))

private fun backgroundFor(level: UvLevel) = when (level) {
    UvLevel.LOW -> R.drawable.bg_uv_low
    UvLevel.MODERATE -> R.drawable.bg_uv_moderate
    UvLevel.HIGH -> R.drawable.bg_uv_high
    UvLevel.VERY_HIGH -> R.drawable.bg_uv_very_high
    UvLevel.EXTREME -> R.drawable.bg_uv_extreme
    UvLevel.NIGHT -> R.drawable.bg_uv_night
}

private fun hh(hour: Int) = String.format(Locale.US, "%02d:00", hour)

@Composable
private fun UvCard(d: UvData, error: String?, refreshing: Boolean, status: String?, update: String?) {
    val level = UvLevel.of(d.uvNow, d.isDay)
    val size = LocalSize.current
    val showChart = size.height >= 130.dp
    val showAdvice = size.height >= 165.dp || !showChart
    val roomy = size.width >= 280.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(backgroundFor(level)))
            .cornerRadius(28.dp)
            .clickable(actionRunCallback<RefreshAction>())
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ---- Top: big number + level  |  place & stats ----
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = if (level == UvLevel.NIGHT) "🌙  מדד UV" else "☀️  מדד UV",
                        style = TextStyle(color = WhiteSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = d.uvNow.roundToInt().toString(),
                            style = TextStyle(color = White, fontSize = 54.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        Pill(level.label)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "📍 ${d.place}",
                        style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = "${d.temperature.roundToInt()}°",
                        style = TextStyle(color = White, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "שיא ${d.uvMaxToday.roundToInt()} · ${hh(d.peakHour)}",
                        style = TextStyle(color = WhiteSoft, fontSize = 12.sp),
                    )
                    if (roomy) {
                        Text(
                            text = "🌅 ${d.sunrise}  🌇 ${d.sunset}",
                            style = TextStyle(color = WhiteSoft, fontSize = 11.sp),
                        )
                    }
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            // ---- Middle: hourly UV bars for daylight hours ----
            if (showChart) {
                HourlyBars(d, maxBar = if (size.height >= 190.dp) 46.dp else 26.dp)
                Spacer(GlanceModifier.height(6.dp))
            }

            // ---- Bottom: advice + last update ----
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (error != null) {
                    Text(
                        text = "⚠ $error",
                        style = TextStyle(color = ErrorTint, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                } else if (showAdvice) {
                    Text(
                        text = level.advice,
                        style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                if (update != null && status == null && !refreshing) {
                    // New version available: a pill that opens the app straight on the update button.
                    Box(
                        modifier = GlanceModifier
                            .background(White)
                            .cornerRadius(50.dp)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .clickable(actionStartActivity<MainActivity>()),
                    ) {
                        Text(
                            text = "⬆ עדכון זמין · $update",
                            style = TextStyle(color = UpdateInk, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(
                        text = when {
                            status != null -> status
                            refreshing -> "מרענן…"
                            else -> "↻ " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(d.checkedAt))
                        },
                        style = TextStyle(
                            color = if (status != null) White else WhiteSoft,
                            fontSize = 11.sp,
                            fontWeight = if (status != null) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = GlanceModifier
            .background(WhiteFaint)
            .cornerRadius(50.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun HourlyBars(d: UvData, maxBar: Dp) {
    // Daylight window: first..last hour with measurable UV, padded by one hour.
    val lit = d.hourly.indices.filter { d.hourly[it] >= 0.05 }
    val from = ((lit.minOrNull() ?: 6) - 1).coerceAtLeast(0)
    val to = ((lit.maxOrNull() ?: 18) + 1).coerceAtMost(d.hourly.lastIndex)
    val scale = maxOf(d.uvMaxToday, 3.0)

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(maxBar + 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (h in from..to) {
            val isNow = h == d.currentHour
            val barH = (maxBar.value * (d.hourly[h] / scale)).coerceAtLeast(3.0).dp
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(if (isNow) 9.dp else 7.dp)
                        .height(barH)
                        .background(if (isNow) White else BarDim)
                        .cornerRadius(4.dp),
                ) {}
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = if (h % 3 == 0 || isNow) h.toString() else " ",
                    style = TextStyle(
                        color = if (isNow) White else WhiteSoft,
                        fontSize = 9.sp,
                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(error: String?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_uv_night))
            .cornerRadius(28.dp)
            .clickable(actionRunCallback<RefreshAction>())
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("☀️ מדד UV", style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = error ?: "טוען… הקש לרענון",
                style = TextStyle(color = WhiteSoft, fontSize = 12.sp),
                maxLines = 2,
            )
        }
    }
}
