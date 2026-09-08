package com.konstantyp.gymcal.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immediate Glance widget refresh.
 *
 * [android:updatePeriodMillis] stays at 1_800_000 (Android's ~30 min floor for
 * periodic updates). Real-time refresh is driven by DataStore writes (awaited
 * [requestUpdate]) and [com.konstantyp.gymcal.MainActivity.onResume] via
 * [requestUpdateAsync] — not by TIME_TICK.
 */
object GymcalWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Updates all week + month Glance widgets on the main dispatcher
     * (Glance compose expects Main). Call from a coroutine after DataStore
     * [androidx.datastore.preferences.core.edit] so the write is committed
     * before widgets re-read preferences.
     */
    suspend fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.Main) {
            runCatching { GymcalWeekWidget().updateAll(appContext) }
            runCatching { GymcalMonthWidget().updateAll(appContext) }
            // Optional classic invalidation for hosts that track provider components.
            // Glance has no collection RemoteViews adapter, so viewId is unused
            // but notify still prompts some launchers to rebind.
            runCatching {
                val manager = AppWidgetManager.getInstance(appContext)
                val weekCn = ComponentName(appContext, GymcalWeekWidgetReceiver::class.java)
                val monthCn = ComponentName(appContext, GymcalMonthWidgetReceiver::class.java)
                manager.getAppWidgetIds(weekCn).forEach { id ->
                    manager.notifyAppWidgetViewDataChanged(id, android.R.id.content)
                }
                manager.getAppWidgetIds(monthCn).forEach { id ->
                    manager.notifyAppWidgetViewDataChanged(id, android.R.id.content)
                }
            }
        }
    }

    /** Fire-and-forget on Main — for receivers / Activity lifecycle. */
    fun requestUpdateAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            requestUpdate(appContext)
        }
    }
}
