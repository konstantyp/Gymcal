package com.konstantyp.gymcal

import android.app.Application
import com.konstantyp.gymcal.data.LocalePreferences
import com.konstantyp.gymcal.data.QuotePreferences
import com.konstantyp.gymcal.data.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GymcalApp : Application() {
    lateinit var workoutRepository: WorkoutRepository
        private set
    lateinit var localePreferences: LocalePreferences
        private set
    lateinit var quotePreferences: QuotePreferences
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        localePreferences = LocalePreferences(this)
        // Default English on first launch; apply stored tag thereafter.
        localePreferences.bootstrap()
        quotePreferences = QuotePreferences(this)
        workoutRepository = WorkoutRepository(this)
        appScope.launch {
            workoutRepository.ensureInitialized()
        }
    }
}
