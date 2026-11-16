package app.crome1394.almanac.widget

import android.content.Context
import android.provider.CalendarContract
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Redraws the widgets when the calendar store changes while the app is closed (a DAVx5
 * sync, say). WorkManager's content-URI trigger runs this after the change has settled; each
 * run arms the next one. It is armed only while at least one widget is on the home screen.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { ScheduleWidget().updateAll(applicationContext) }
        runCatching { MonthWidget().updateAll(applicationContext) }
        WidgetRefresh.arm(applicationContext)
        return Result.success()
    }
}

object WidgetRefresh {
    private const val NAME = "widget-refresh"

    suspend fun arm(c: Context) {
        val m = GlanceAppWidgetManager(c)
        val any = runCatching {
            m.getGlanceIds(ScheduleWidget::class.java).isNotEmpty() || m.getGlanceIds(MonthWidget::class.java).isNotEmpty()
        }.getOrDefault(false)
        val wm = WorkManager.getInstance(c)
        if (!any) { wm.cancelUniqueWork(NAME); return }
        val req = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                    .setTriggerContentMaxDelay(60, TimeUnit.SECONDS)
                    .build(),
            ).build()
        wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
    }
}
