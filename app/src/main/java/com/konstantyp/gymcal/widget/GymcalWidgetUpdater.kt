package com.konstantyp.gymcal.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immediate Glance widget refresh after DataStore writes.
 *
 * Periodic [android:updatePeriodMillis] is only a floor (~30 min). Real-time
 * refresh is [requestUpdate] (awaited from [com.konstantyp.gymcal.data.WorkoutRepository])
 * plus [requestUpdateAsync] from Activity / receivers.
 */
object GymcalWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Updates all week + month Glance widgets. Uses [NonCancellable] so a
     * cancelled DayDetail coroutine cannot skip the home-screen refresh.
     * Also broadcasts [AppWidgetManager.ACTION_APPWIDGET_UPDATE] so launchers
     * that ignore Glance-only updates still rebind.
     */
    suspend fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            runCatching { GymcalWeekWidget().updateAll(appContext) }
            runCatching { GymcalMonthWidget().updateAll(appContext) }
            runCatching { notifyProviders(appContext) }
        }
    }

    /** Fire-and-forget on an app-scoped Main job — receivers / Activity lifecycle. */
    fun requestUpdateAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            requestUpdate(appContext)
        }
    }

    private fun notifyProviders(appContext: Context) {
        val manager = AppWidgetManager.getInstance(appContext)
        listOf(
            GymcalWeekWidgetReceiver::class.java,
            GymcalMonthWidgetReceiver::class.java,
        ).forEach { clazz ->
            val cn = ComponentName(appContext, clazz)
            val ids = manager.getAppWidgetIds(cn)
            if (ids.isEmpty()) return@forEach
            appContext.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = cn
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
            ids.forEach { id ->
                manager.notifyAppWidgetViewDataChanged(id, android.R.id.content)
            }
        }
    }
}
