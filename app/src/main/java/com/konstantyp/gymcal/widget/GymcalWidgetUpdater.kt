package com.konstantyp.gymcal.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
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
 * Glance 1.1: when a session is already running, [GlanceAppWidget.update] only
 * applies UpdateGlanceState and does **not** re-enter [GlanceAppWidget.provideGlance].
 * Type/color data captured outside `provideContent` would stay stale. We write
 * [ForceRefreshAtKey] so [GymcalBaseWidget] reloads DataStore inside content, then
 * updateAll + per-id update + [AppWidgetManager.ACTION_APPWIDGET_UPDATE].
 */
object GymcalWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Glance Preferences nonce — [GymcalBaseWidget] reloads types when this changes. */
    internal val ForceRefreshAtKey = longPreferencesKey("force_refresh_at")

    /**
     * Updates all week + month Glance widgets. Uses [NonCancellable] so a
     * cancelled DayDetail / Types coroutine cannot skip the home-screen refresh.
     */
    suspend fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        withContext(NonCancellable + Dispatchers.Default) {
            val glanceManager = GlanceAppWidgetManager(appContext)
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val targets = listOf(
                WidgetTarget(GymcalWeekWidget(), GymcalWeekWidgetReceiver::class.java),
                WidgetTarget(GymcalMonthWidget(), GymcalMonthWidgetReceiver::class.java),
            )
            val now = System.currentTimeMillis()
            for (target in targets) {
                val ids = resolveGlanceIds(appContext, glanceManager, appWidgetManager, target)
                for (id in ids) {
                    runCatching {
                        updateAppWidgetState(appContext, id) { prefs ->
                            prefs[ForceRefreshAtKey] = now
                        }
                    }
                }
                // updateAll covers Glance-tracked IDs; per-id covers AppWidgetManager fallback.
                runCatching { target.widget.updateAll(appContext) }
                for (id in ids) {
                    runCatching { target.widget.update(appContext, id) }
                }
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

    private data class WidgetTarget(
        val widget: GlanceAppWidget,
        val receiver: Class<*>,
    )

    private suspend fun resolveGlanceIds(
        appContext: Context,
        glanceManager: GlanceAppWidgetManager,
        appWidgetManager: AppWidgetManager,
        target: WidgetTarget,
    ): List<GlanceId> {
        val fromGlance = runCatching { glanceManager.getGlanceIds(target.widget.javaClass) }
            .getOrDefault(emptyList())
        if (fromGlance.isNotEmpty()) return fromGlance
        // Glance DataStore can lag AppWidgetManager (fresh pin / OEM). Fall back.
        val cn = ComponentName(appContext, target.receiver)
        return appWidgetManager.getAppWidgetIds(cn).map { AppWidgetId(it) }
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
                runCatching {
                    manager.notifyAppWidgetViewDataChanged(id, android.R.id.content)
                }
            }
        }
    }
}
