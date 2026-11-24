package com.shai.uvwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Does the actual refresh. Running it as a job with a network constraint matters:
 * Android only starts the job once the app is allowed onto the network, which is
 * exactly what a plain background tap handler doesn't get on Samsung phones.
 */
class UvWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val trigger = runCatching { Trigger.valueOf(inputData.getString(KEY_TRIGGER) ?: "") }
            .getOrDefault(Trigger.AUTO)
        UvRepository.update(applicationContext, trigger)
        runCatching { UpdateChecker.checkAndNotify(applicationContext) }   // GitHub at most once an hour
        UvWidget.pushToWidgets(applicationContext, refreshing = false)     // also shows "⬆ עדכון זמין"
        // Retries are handled by RefreshPolicy's backoff, not by WorkManager.
        return Result.success()
    }

    /** Only used on Android 11 and older, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("uv_refresh", "רענון UV", NotificationManager.IMPORTANCE_MIN)
        )
        val n = NotificationCompat.Builder(applicationContext, "uv_refresh")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("מרענן מדד UV…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return ForegroundInfo(4242, n)
    }

    companion object {
        private const val PERIODIC = "uv_periodic_v2"
        private const val NOW = "uv_now"
        private const val KEY_TRIGGER = "trigger"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Tick every 15 minutes (Android's minimum). Most ticks never touch the network:
         * they just recompute "UV now" from the cached forecast. No network constraint here,
         * so the widget keeps moving even offline.
         */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork("uv_periodic")   // old 30-min schedule from v1.x
            val req = PeriodicWorkRequestBuilder<UvWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
            ClockTick.schedule(context)   // keeps "now" right even when this tick is held back
        }

        /** Refresh right now – expedited so it starts within seconds. */
        fun refreshNow(context: Context, trigger: Trigger = Trigger.MANUAL) {
            val req = OneTimeWorkRequestBuilder<UvWorker>()
                .setInputData(androidx.work.workDataOf(KEY_TRIGGER to trigger.name))
                .setConstraints(network)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            ClockTick.cancel(context)
        }
    }
}
