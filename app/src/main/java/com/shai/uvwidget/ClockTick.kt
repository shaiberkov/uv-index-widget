package com.shai.uvwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Keeps the widget's clock honest.
 *
 * WorkManager's 15-min tick can be held back for an hour or more (Doze, Samsung's
 * battery manager), which left the "now" bar stuck on an old hour. This exact alarm fires
 * right on every full hour and redraws "now" from the cached forecast – offline, a few
 * milliseconds of work – so the bar moves 11 → 12 at 12:00. Network refreshes stay with [UvWorker].
 */
class ClockTick : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // No widget on the home screen → nothing to keep fresh, let the chain stop.
                if (GlanceAppWidgetManager(app).getGlanceIds(UvWidget::class.java).isEmpty()) return@launch
                schedule(app)   // chain the next tick before the work, so a failure can't break it
                recompute(app)
                // If the data is also due for a server refresh, nudge the worker.
                if (UvRepository.wouldFetch(app, Trigger.AUTO).fetch) UvWorker.refreshNow(app, Trigger.AUTO)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val HOUR = 60 * 60_000L

        /** Recompute "now" from the cache and redraw – no network. */
        suspend fun recompute(context: Context) {
            val cache = UvStore.loadCache(context) ?: return
            val now = System.currentTimeMillis()
            if (!cache.covers(now)) return
            UvStore.save(context, cache.snapshot(now))
            UvWidget.pushToWidgets(context)
        }

        /**
         * Arm an exact alarm for the next full hour, one second past (so 12:00:01, 13:00:01…).
         * RTC_WAKEUP + exact-while-idle fires on time even with the screen off / in Doze.
         * Works across time zones with whole-hour offsets (Israel is UTC+2/+3).
         */
        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val at = nextHour(System.currentTimeMillis())
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context))   // fallback, may drift
        }

        /** The next local full hour + 1 s, computed with the calendar so odd offsets work too. */
        private fun nextHour(now: Long): Long = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 1)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(java.util.Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pi(context))
        }

        private fun pi(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, ClockTick::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
