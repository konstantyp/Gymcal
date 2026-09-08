package com.konstantyp.gymcal.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.konstantyp.gymcal.widget.GymcalWidgetUpdater
import kotlinx.coroutines.runBlocking

private val Context.localeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gymcal_locale",
)

/**
 * Per-app locale (EN/PL/DE). Default English on first launch / unset preference.
 * Applies via [AppCompatDelegate.setApplicationLocales].
 */
class LocalePreferences(private val context: Context) {

    companion object {
        const val TAG_EN = "en"
        const val TAG_PL = "pl"
        const val TAG_DE = "de"
        const val DEFAULT_TAG = TAG_EN

        private val KEY_LOCALE = stringPreferencesKey("app_locale_tag")

        val SUPPORTED = listOf(TAG_EN, TAG_PL, TAG_DE)

        fun applyLocales(tag: String) {
            val normalized = when (tag) {
                TAG_PL -> TAG_PL
                TAG_DE -> TAG_DE
                else -> TAG_EN
            }
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(normalized),
            )
        }
    }

    val localeTag: Flow<String> = context.localeDataStore.data.map { prefs ->
        prefs[KEY_LOCALE] ?: DEFAULT_TAG
    }

    /** Sync read + apply default EN if unset. Call from Application.onCreate. */
    fun bootstrap() {
        val tag = runBlocking {
            val prefs = context.localeDataStore.data.first()
            val stored = prefs[KEY_LOCALE]
            if (stored == null) {
                context.localeDataStore.edit { it[KEY_LOCALE] = DEFAULT_TAG }
                DEFAULT_TAG
            } else {
                stored
            }
        }
        applyLocales(tag)
    }

    suspend fun setLocaleTag(tag: String) {
        val normalized = when (tag) {
            TAG_PL -> TAG_PL
            TAG_DE -> TAG_DE
            else -> TAG_EN
        }
        context.localeDataStore.edit { it[KEY_LOCALE] = normalized }
        applyLocales(normalized)
        GymcalWidgetUpdater.requestUpdate(context)
    }
}
