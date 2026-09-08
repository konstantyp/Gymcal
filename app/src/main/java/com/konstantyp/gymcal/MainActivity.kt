package com.konstantyp.gymcal

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.konstantyp.gymcal.ui.calendar.CalendarScreen
import com.konstantyp.gymcal.ui.detail.DayDetailScreen
import com.konstantyp.gymcal.ui.settings.SettingsScreen
import com.konstantyp.gymcal.ui.theme.GymcalTheme
import com.konstantyp.gymcal.ui.types.TypesScreen
import com.konstantyp.gymcal.widget.GymcalWidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute.value = routeFromIntent(intent)
        enableEdgeToEdge()
        setContent {
            GymcalRoot(
                pendingRoute = pendingRoute.value,
                onRouteConsumed = { pendingRoute.value = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Immediate home-widget refresh when returning from DayDetail / Types.
        GymcalWidgetUpdater.requestUpdateAsync(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = routeFromIntent(intent)
    }

    companion object {
        const val EXTRA_DEST = "widget_dest"
        const val EXTRA_DATE = "widget_date"
        const val EXTRA_EPOCH_DAY = "epochDay"
        const val DEST_CALENDAR = "calendar"
        const val DEST_DETAIL = "detail"

        fun routeFromIntent(intent: Intent?): String? {
            if (intent == null) return null
            val dest = intent.getStringExtra(EXTRA_DEST) ?: return null
            return when (dest) {
                DEST_DETAIL -> {
                    val iso = intent.getStringExtra(EXTRA_DATE)
                    val hasEpoch = intent.hasExtra(EXTRA_EPOCH_DAY)
                    val epoch = if (hasEpoch) {
                        intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
                    } else {
                        Long.MIN_VALUE
                    }
                    val date = when {
                        !iso.isNullOrBlank() ->
                            runCatching { LocalDate.parse(iso) }.getOrNull()
                        epoch != Long.MIN_VALUE -> LocalDate.ofEpochDay(epoch)
                        else -> null
                    } ?: return null
                    "detail/$date"
                }
                DEST_CALENDAR -> "calendar"
                else -> null
            }
        }
    }
}

@Composable
private fun GymcalRoot(
    pendingRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as GymcalApp }
    val repository = remember { app.workoutRepository }
    val localePreferences = remember { app.localePreferences }
    val types by repository.types.collectAsState(initial = emptyList())
    val workouts by repository.workouts.collectAsState(initial = emptyMap())
    val localeTag by localePreferences.localeTag.collectAsState(initial = "en")
    val scope = rememberCoroutineScope()

    GymcalTheme(types = types) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()

            LaunchedEffect(pendingRoute) {
                val route = pendingRoute ?: return@LaunchedEffect
                when (route) {
                    "calendar" -> {
                        navController.navigate("calendar") {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                    else -> {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                }
                onRouteConsumed()
            }

            NavHost(
                navController = navController,
                startDestination = "calendar",
            ) {
                composable("calendar") {
                    CalendarScreen(
                        workouts = workouts,
                        types = types,
                        onDayClick = { date ->
                            navController.navigate("detail/${date}")
                        },
                        onManageTypes = {
                            navController.navigate("types")
                        },
                        onOpenSettings = {
                            navController.navigate("settings")
                        },
                    )
                }
                composable(
                    route = "detail/{date}",
                    arguments = listOf(navArgument("date") { type = NavType.StringType }),
                ) { entry ->
                    val date = LocalDate.parse(entry.arguments!!.getString("date")!!)
                    DayDetailScreen(
                        date = date,
                        types = types,
                        initialTypeId = workouts[date],
                        onBack = { navController.popBackStack() },
                        onSave = { typeId ->
                            scope.launch {
                                repository.setWorkout(date, typeId)
                                navController.popBackStack()
                            }
                        },
                        onClear = {
                            scope.launch {
                                repository.clearWorkout(date)
                                navController.popBackStack()
                            }
                        },
                        onManageTypes = {
                            navController.navigate("types")
                        },
                    )
                }
                composable("types") {
                    TypesScreen(
                        types = types,
                        onBack = { navController.popBackStack() },
                        onAdd = { name, seedArgb ->
                            repository.addType(name, seedArgb).map { }
                        },
                        onUpdate = { id, name, seedArgb ->
                            repository.updateType(id, name, seedArgb)
                        },
                        onDelete = { id ->
                            repository.deleteType(id)
                        },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        selectedTag = localeTag,
                        onLocaleSelected = { tag ->
                            scope.launch {
                                localePreferences.setLocaleTag(tag)
                            }
                        },
                        repository = repository,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
