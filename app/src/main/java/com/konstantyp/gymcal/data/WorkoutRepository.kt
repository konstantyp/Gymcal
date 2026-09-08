package com.konstantyp.gymcal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import com.konstantyp.gymcal.R
import com.konstantyp.gymcal.widget.GymcalWidgetUpdater
import org.json.JSONObject

private val Context.workoutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "workout_calendar",
)

class WorkoutRepository(private val context: Context) {

    companion object {
        private val KEY_TYPES = stringPreferencesKey("__types_v1")
        private val KEY_SCHEMA = stringPreferencesKey("__schema_version")
        private const val SCHEMA_V1_1 = "1.1"

        /** Public plan JSON schema version (DesignBot export/import contract). */
        const val PLAN_JSON_VERSION = 1

        private val ENUM_NAMES = setOf("Push", "Pull", "Legs", "Chest", "Biceps")
    }

    /** Sorted workout types (by sortOrder). Defaults until persisted seed written. */
    val types: Flow<List<WorkoutType>> = context.workoutDataStore.data.map { prefs ->
        val raw = prefs[KEY_TYPES]
        val list = if (raw == null) WorkoutType.defaults() else parseTypes(raw)
        list.sortedBy { it.sortOrder }
    }

    /** ISO date (yyyy-MM-dd) → typeId */
    val workouts: Flow<Map<LocalDate, String>> = context.workoutDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith("__")) return@mapNotNull null
            val date = runCatching { LocalDate.parse(key.name) }.getOrNull() ?: return@mapNotNull null
            val typeId = value as? String ?: return@mapNotNull null
            // Map legacy enum names on the fly if migration not yet flushed
            val mapped = if (typeId in ENUM_NAMES) {
                WorkoutType.migrateEnumName(typeId) ?: return@mapNotNull null
            } else {
                typeId
            }
            date to mapped
        }.toMap()
    }

    /** Persist default types + migrate old enum day values. Call from App.onCreate. */
    suspend fun ensureInitialized() {
        context.workoutDataStore.edit { prefs ->
            migrateLocked(prefs)
        }
        GymcalWidgetUpdater.requestUpdate(context)
    }

    suspend fun setWorkout(date: LocalDate, typeId: String) {
        context.workoutDataStore.edit { prefs ->
            prefs[stringPreferencesKey(date.toString())] = typeId
        }
        // Ensure Flow snapshot is committed before Glance re-reads .first()
        workouts.first()
        GymcalWidgetUpdater.requestUpdate(context)
    }

    suspend fun clearWorkout(date: LocalDate) {
        context.workoutDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(date.toString()))
        }
        workouts.first()
        GymcalWidgetUpdater.requestUpdate(context)
    }

    suspend fun addType(name: String, seedArgb: Long): Result<WorkoutType> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > WorkoutType.NAME_MAX_LEN) {
            return Result.failure(
                IllegalArgumentException(context.getString(R.string.error_name_length, WorkoutType.NAME_MAX_LEN)),
            )
        }
        return try {
            var created: WorkoutType? = null
            context.workoutDataStore.edit { prefs ->
                migrateLocked(prefs)
                val current = parseTypes(prefs[KEY_TYPES]).toMutableList()
                if (current.size >= WorkoutType.MAX_TYPES) {
                    throw IllegalStateException(context.getString(R.string.error_max_types, WorkoutType.MAX_TYPES))
                }
                if (current.any { it.name.equals(trimmed, ignoreCase = true) }) {
                    throw IllegalArgumentException(context.getString(R.string.error_name_exists))
                }
                val nextOrder = (current.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val type = WorkoutType(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    seedArgb = seedArgb,
                    sortOrder = nextOrder,
                )
                current += type
                prefs[KEY_TYPES] = serializeTypes(current)
                created = type
            }
            types.first()
            GymcalWidgetUpdater.requestUpdate(context)
            Result.success(created!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateType(id: String, name: String, seedArgb: Long): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > WorkoutType.NAME_MAX_LEN) {
            return Result.failure(
                IllegalArgumentException(context.getString(R.string.error_name_length, WorkoutType.NAME_MAX_LEN)),
            )
        }
        return try {
            context.workoutDataStore.edit { prefs ->
                migrateLocked(prefs)
                val current = parseTypes(prefs[KEY_TYPES]).toMutableList()
                val idx = current.indexOfFirst { it.id == id }
                if (idx < 0) throw IllegalArgumentException(context.getString(R.string.error_type_missing))
                if (current.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
                    throw IllegalArgumentException(context.getString(R.string.error_name_exists))
                }
                current[idx] = current[idx].copy(name = trimmed, seedArgb = seedArgb)
                prefs[KEY_TYPES] = serializeTypes(current)
            }
            types.first()
            GymcalWidgetUpdater.requestUpdate(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete type and clear all days that referenced it. */
    suspend fun deleteType(id: String) {
        context.workoutDataStore.edit { prefs ->
            migrateLocked(prefs)
            val current = parseTypes(prefs[KEY_TYPES]).filterNot { it.id == id }
            prefs[KEY_TYPES] = serializeTypes(current)
            val toRemove = prefs.asMap().keys.filter { key ->
                !key.name.startsWith("__") && prefs[key] == id
            }
            toRemove.forEach { prefs.remove(it) }
        }
        types.first()
        workouts.first()
        GymcalWidgetUpdater.requestUpdate(context)
    }

    /**
     * Export plan JSON (DesignBot contract):
     * `{ "version": 1, "types": [...], "days": [{ "date", "typeId" }] }`
     */
    suspend fun exportPlanJson(): String {
        ensureInitialized()
        val typeList = types.first()
        val dayMap = workouts.first()
        val root = JSONObject()
        root.put("version", PLAN_JSON_VERSION)
        val typesArr = JSONArray()
        typeList.forEach { t ->
            typesArr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("seedArgb", t.seedArgb)
                    .put("sortOrder", t.sortOrder),
            )
        }
        root.put("types", typesArr)
        val daysArr = JSONArray()
        dayMap.toSortedMap().forEach { (date, typeId) ->
            daysArr.put(
                JSONObject()
                    .put("date", date.toString())
                    .put("typeId", typeId),
            )
        }
        root.put("days", daysArr)
        return root.toString(2)
    }

    /**
     * Import plan JSON and **replace** types + day assignments.
     * Validates version and type count; no partial write on failure.
     */
    suspend fun importPlanJson(json: String): Result<Unit> {
        val parsed = runCatching { parsePlanJson(json) }.getOrElse { e ->
            return Result.failure(e)
        }
        return try {
            context.workoutDataStore.edit { prefs ->
                // Clear existing day keys
                val dayKeys = prefs.asMap().keys.filter { !it.name.startsWith("__") }
                dayKeys.forEach { prefs.remove(it) }
                prefs[KEY_TYPES] = serializeTypes(parsed.types)
                prefs[KEY_SCHEMA] = SCHEMA_V1_1
                parsed.days.forEach { (date, typeId) ->
                    prefs[stringPreferencesKey(date.toString())] = typeId
                }
            }
            types.first()
            workouts.first()
            GymcalWidgetUpdater.requestUpdate(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class ParsedPlan(
        val types: List<WorkoutType>,
        val days: List<Pair<LocalDate, String>>,
    )

    private fun parsePlanJson(json: String): ParsedPlan {
        if (json.isBlank()) {
            throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
        }
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
        }
        if (!root.has("version") || root.optInt("version", -1) != PLAN_JSON_VERSION) {
            throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
        }
        if (!root.has("types") || !root.has("days")) {
            throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
        }
        val typesArr = root.getJSONArray("types")
        if (typesArr.length() > WorkoutType.MAX_TYPES) {
            throw IllegalArgumentException(
                context.getString(R.string.import_too_many_types, WorkoutType.MAX_TYPES),
            )
        }
        val typeList = buildList {
            for (i in 0 until typesArr.length()) {
                val o = typesArr.getJSONObject(i)
                val id = o.getString("id").trim()
                val name = o.getString("name").trim()
                if (id.isEmpty() || name.isEmpty() || name.length > WorkoutType.NAME_MAX_LEN) {
                    throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
                }
                val seedArgb = o.getLong("seedArgb")
                val sortOrder = o.getInt("sortOrder")
                add(WorkoutType(id = id, name = name, seedArgb = seedArgb, sortOrder = sortOrder))
            }
        }
        val typeIds = typeList.map { it.id }.toSet()
        if (typeIds.size != typeList.size) {
            throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
        }
        val daysArr = root.getJSONArray("days")
        val days = buildList {
            for (i in 0 until daysArr.length()) {
                val o = daysArr.getJSONObject(i)
                val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrElse {
                    throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
                }
                val typeId = o.getString("typeId").trim()
                if (typeId.isEmpty() || typeId !in typeIds) {
                    throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
                }
                add(date to typeId)
            }
        }
        return ParsedPlan(types = typeList, days = days)
    }

    private fun migrateLocked(prefs: MutablePreferences) {
        if (prefs[KEY_SCHEMA] == SCHEMA_V1_1 && prefs[KEY_TYPES] != null) return

        if (prefs[KEY_TYPES] == null) {
            prefs[KEY_TYPES] = serializeTypes(WorkoutType.defaults())
        }

        val keys = prefs.asMap().keys.toList()
        for (key in keys) {
            if (key.name.startsWith("__")) continue
            val value = prefs[key] as? String ?: continue
            if (value in ENUM_NAMES) {
                val mapped = WorkoutType.migrateEnumName(value)
                val stringKey = stringPreferencesKey(key.name)
                if (mapped != null) {
                    prefs[stringKey] = mapped
                } else {
                    prefs.remove(stringKey)
                }
            }
        }

        prefs[KEY_SCHEMA] = SCHEMA_V1_1
    }

    private fun parseTypes(json: String?): List<WorkoutType> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        WorkoutType(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            seedArgb = o.getLong("seedArgb"),
                            sortOrder = o.getInt("sortOrder"),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun serializeTypes(types: List<WorkoutType>): String {
        val arr = JSONArray()
        types.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("seedArgb", t.seedArgb)
                    .put("sortOrder", t.sortOrder),
            )
        }
        return arr.toString()
    }
}
