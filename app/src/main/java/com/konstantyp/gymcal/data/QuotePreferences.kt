package com.konstantyp.gymcal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quoteDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gymcal_quote",
)

/**
 * Motivation quote visibility on CalendarScreen.
 * Key [KEY_SHOW_MOTIVATION_QUOTE] defaults to true (existing behavior).
 */
class QuotePreferences(private val context: Context) {

    companion object {
        private val KEY_SHOW_MOTIVATION_QUOTE = booleanPreferencesKey("show_motivation_quote")
        const val DEFAULT_SHOW = true
    }

    val showMotivationQuote: Flow<Boolean> = context.quoteDataStore.data.map { prefs ->
        prefs[KEY_SHOW_MOTIVATION_QUOTE] ?: DEFAULT_SHOW
    }

    suspend fun setShowMotivationQuote(show: Boolean) {
        context.quoteDataStore.edit { it[KEY_SHOW_MOTIVATION_QUOTE] = show }
    }
}
