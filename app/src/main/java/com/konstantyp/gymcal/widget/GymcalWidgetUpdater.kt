package com.konstantyp.gymcal.widget

import android.appwidget.AppWidgetManager
import android.os.Build
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immediate Glance widget refresh after DataStore / locale writes.
 *
 * Glance 1.1 + API 36: a live session often skips re-entering [GlanceAppWidget.provideGlance]
 * on [update]/[updateAll] (UpdateGlanceState only). We bump [ForceRefreshAtKey] as a kick while content
 * observes repository Flows, broadcast [AppWidgetManager.ACTION_APPWIDGET_UPDATE] (not Glance-only),
 * and on API 31+ run a second delayed pass — OEM launchers on Android 16 sometimes drop the first rebind.
 */
object GymcalWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Glance Preferences nonce — kick so [GymcalBaseWidget] restarts Flow collection. */
    internal val ForceRefreshAtKey = longPreferencesKey("force_refresh_at")

    private const val SecondPassDelayMs = 280L

    /**
     * Updates all week + month Glance widgets. Uses [NonCancellable] so a
     * cancelled DayDetail / Types coroutine cannot skip the home-screen refresh.
     */
    suspend fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        withContext(NonCancellable + Dispatchers.Default) {
            refreshPass(appContext, System.currentTimeMillis())
            // API 31+: second pass for sticky OEM launchers (Android 16 often drops first rebind).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                delay(SecondPassDelayMs)
                refreshPass(appContext, System.currentTimeMillis())
            }
        }
    }

    /** Fire-and-forget on an app-scoped Default job — receivers / Activity / belt-and-suspenders. */
    fun requestUpdateAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            requestUpdate(appContext)
        }
    }

    private suspend fun refreshPass(appContext: Context, nonce: Long) {
        val glanceManager = GlanceAppWidgetManager(appContext)
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val targets = listOf(
            WidgetTarget(GymcalWeekWidget(), GymcalWeekWidgetReceiver::class.java),
            WidgetTarget(GymcalMonthWidget(), GymcalMonthWidgetReceiver::class.java),
        )
        for (target in targets) {
            val ids = resolveGlanceIds(appContext, glanceManager, appWidgetManager, target)
            for (id in ids) {
                runCatching {
                    updateAppWidgetState(appContext, id) { prefs ->
                        prefs[ForceRefreshAtKey] = nonce
                    }
                }
            }
            runCatching { target.widget.updateAll(appContext) }
            for (id in ids) {
                runCatching { target.widget.update(appContext, id) }
            }
        }
        runCatching { notifyProviders(appContext) }
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
            // Explicit package + component — API 36 is stricter about implicit broadcasts.
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = cn
                setPackage(appContext.packageName)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            appContext.sendBroadcast(intent)
            // Also poke the manager directly (Glance session may ignore broadcast-only).
            runCatching {
                manager.notifyAppWidgetViewDataChanged(ids, android.R.id.background)
            }
            ids.forEach { id ->
                runCatching {
                    manager.notifyAppWidgetViewDataChanged(id, android.R.id.content)
                }
            }
        }
    }
}
