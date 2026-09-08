package com.konstantyp.gymcal.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immediate Glance widget refresh after DataStore / locale writes.
 *
 * Periodic [android:updatePeriodMillis] is only a floor (~30 min). Real-time
 * refresh is [requestUpdate] (awaited from repository) plus [requestUpdateAsync]
 * from Activity / receivers.
 *
 * Glance updates run on [Dispatchers.Default] — not Main.immediate — so type-color
 * saves on Main cannot stall/skip rebind. Each GlanceId also gets a Preferences
 * nonce so color-only changes force a redraw.
 */
object GymcalWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val ForceRefreshAtKey = longPreferencesKey("force_refresh_at")

    /**
     * Updates all week + month Glance widgets. Uses [NonCancellable] so a
     * cancelled DayDetail / Types coroutine cannot skip the home-screen refresh.
     * Also broadcasts [AppWidgetManager.ACTION_APPWIDGET_UPDATE] so launchers
     * that ignore Glance-only updates still rebind.
     */
    suspend fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        withContext(NonCancellable + Dispatchers.Default) {
            val manager = GlanceAppWidgetManager(appContext)
            val widgets: List<GlanceAppWidget> = listOf(
                GymcalWeekWidget(),
                GymcalMonthWidget(),
            )
            for (widget in widgets) {
                val ids = runCatching { manager.getGlanceIds(widget.javaClass) }
                    .getOrDefault(emptyList())
                val now = System.currentTimeMillis()
                for (id in ids) {
                    runCatching {
                        updateAppWidgetState(appContext, id) { prefs ->
                            prefs[ForceRefreshAtKey] = now
                        }
                        widget.update(appContext, id)
                    }
                }
                runCatching { widget.updateAll(appContext) }
            }
            runCatching { notifyProviders(appContext) }
        }
    }

    /** Fire-and-forget on an app-scoped Default job — receivers / Activity / belt-and-suspenders. */
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
        }
    }
}
