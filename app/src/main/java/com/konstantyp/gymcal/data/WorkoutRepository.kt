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
        private const val SCHEMA_V1_2 = "1.2"

        /** Public plan JSON schema version (DesignBot export/import contract). */
        const val PLAN_JSON_VERSION = 2
        /** Oldest plan version still accepted on import. */
        private const val PLAN_JSON_VERSION_MIN = 1

        const val MAX_WORKOUTS_PER_DAY = 2
        /** Alias used by day-detail UI. */
        const val MAX_TYPES_PER_DAY = MAX_WORKOUTS_PER_DAY

        private val ENUM_NAMES = setOf("Push", "Pull", "Legs", "Chest", "Biceps")
    }

    /** Sorted workout types (by sortOrder). Defaults until persisted seed written. */
    val types: Flow<List<WorkoutType>> = context.workoutDataStore.data.map { prefs ->
        val raw = prefs[KEY_TYPES]
        val list = if (raw == null) WorkoutType.defaults() else parseTypes(raw)
        list.sortedBy { it.sortOrder }
    }

    /** ISO date (yyyy-MM-dd) → ordered typeIds (1–2). Empty days omitted. */
    val workouts: Flow<Map<LocalDate, List<String>>> = context.workoutDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith("__")) return@mapNotNull null
            val date = runCatching { LocalDate.parse(key.name) }.getOrNull() ?: return@mapNotNull null
            val raw = value as? String ?: return@mapNotNull null
            val ids = decodeDayTypeIds(raw).mapNotNull { id ->
                if (id in ENUM_NAMES) WorkoutType.migrateEnumName(id) else id
            }.take(MAX_WORKOUTS_PER_DAY)
            if (ids.isEmpty()) return@mapNotNull null
            date to ids
        }.toMap()
    }

    /** Persist default types + migrate old enum / single-slot day values. Call from App.onCreate. */
    suspend fun ensureInitialized() {
        context.workoutDataStore.edit { prefs ->
            migrateLocked(prefs)
        }
        GymcalWidgetUpdater.requestUpdate(context)
    }

    /** Replace day slots with up to [MAX_WORKOUTS_PER_DAY] ordered type ids. Empty clears. */
    suspend fun setWorkouts(date: LocalDate, typeIds: List<String>) {
        val cleaned = typeIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_WORKOUTS_PER_DAY)
        context.workoutDataStore.edit { prefs ->
            migrateLocked(prefs)
            val key = stringPreferencesKey(date.toString())
            if (cleaned.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = encodeDayTypeIds(cleaned)
            }
        }
        workouts.first()
        GymcalWidgetUpdater.requestUpdate(context)
    }

    /** Assign 0–2 ordered type ids (BINDING API). Empty clears. */
    suspend fun setWorkout(date: LocalDate, typeIds: List<String>) {
        setWorkouts(date, typeIds)
    }

    /** Convenience: single-slot assignment (replaces any existing slots). */
    suspend fun setWorkout(date: LocalDate, typeId: String) {
        setWorkouts(date, listOf(typeId))
    }

    suspend fun clearWorkout(date: LocalDate) {
        setWorkouts(date, emptyList())
    }

    /** Append [typeId] as next slot if under max; no-op if already present or full. */
    suspend fun addWorkoutSlot(date: LocalDate, typeId: String) {
        val id = typeId.trim()
        if (id.isEmpty()) return
        val current = workouts.first()[date].orEmpty()
        if (id in current || current.size >= MAX_WORKOUTS_PER_DAY) return
        setWorkouts(date, current + id)
    }

    /** Remove [typeId] from the day's ordered slots (if present). */
    suspend fun removeWorkoutSlot(date: LocalDate, typeId: String) {
        val current = workouts.first()[date].orEmpty()
        if (typeId !in current) return
        setWorkouts(date, current.filterNot { it == typeId })
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

    /** Delete type and remove it from any day slots (drop empty days). */
    suspend fun deleteType(id: String) {
        context.workoutDataStore.edit { prefs ->
            migrateLocked(prefs)
            val current = parseTypes(prefs[KEY_TYPES]).filterNot { it.id == id }
            prefs[KEY_TYPES] = serializeTypes(current)
            val dayKeys = prefs.asMap().keys.filter { !it.name.startsWith("__") }
            for (key in dayKeys) {
                val raw = prefs[key] as? String ?: continue
                val remaining = decodeDayTypeIds(raw).filter { it != id }
                val stringKey = stringPreferencesKey(key.name)
                if (remaining.isEmpty()) {
                    prefs.remove(stringKey)
                } else {
                    prefs[stringKey] = encodeDayTypeIds(remaining)
                }
            }
        }
        types.first()
        workouts.first()
        GymcalWidgetUpdater.requestUpdate(context)
    }

    /**
     * Export plan JSON (DesignBot contract v2):
     * `{ "version": 2, "types": [...], "days": [{ "date", "typeIds", "typeId"? }] }`
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
        dayMap.toSortedMap(compareBy { it }).forEach { (date, typeIds) ->
            val idsArr = JSONArray()
            typeIds.forEach { idsArr.put(it) }
            val dayObj = JSONObject()
                .put("date", date.toString())
                .put("typeIds", idsArr)
            // Soft-read mirror for older tools
            if (typeIds.isNotEmpty()) {
                dayObj.put("typeId", typeIds.first())
            }
            daysArr.put(dayObj)
        }
        root.put("days", daysArr)
        return root.toString(2)
    }

    /**
     * Import plan JSON and **replace** types + day assignments.
     * Accepts version 1 (single typeId) and version 2 (typeIds).
     * Validates type count; no partial write on failure.
     */
    suspend fun importPlanJson(json: String): Result<Unit> {
        val parsed = runCatching { parsePlanJson(json) }.getOrElse { e ->
            return Result.failure(e)
        }
        return try {
            context.workoutDataStore.edit { prefs ->
                val dayKeys = prefs.asMap().keys.filter { !it.name.startsWith("__") }
                dayKeys.forEach { prefs.remove(it) }
                prefs[KEY_TYPES] = serializeTypes(parsed.types)
                prefs[KEY_SCHEMA] = SCHEMA_V1_2
                parsed.days.forEach { (date, typeIds) ->
                    prefs[stringPreferencesKey(date.toString())] = encodeDayTypeIds(typeIds)
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
        val days: List<Pair<LocalDate, List<String>>>,
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
        val version = root.optInt("version", -1)
        if (version < PLAN_JSON_VERSION_MIN || version > PLAN_JSON_VERSION) {
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
        val knownTypeIds = typeList.map { it.id }.toSet()
        if (knownTypeIds.size != typeList.size) {
            throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
        }
        val daysArr = root.getJSONArray("days")
        val days = buildList {
            for (i in 0 until daysArr.length()) {
                val o = daysArr.getJSONObject(i)
                val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrElse {
                    throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
                }
                val ids = parseDayTypeIdsFromJson(o)
                if (ids.isEmpty()) continue
                if (ids.any { it !in knownTypeIds }) {
                    throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
                }
                add(date to ids)
            }
        }
        return ParsedPlan(types = typeList, days = days)
    }

    /** Prefer typeIds; else typeId + optional typeId2. Cap at 2. */
    private fun parseDayTypeIdsFromJson(o: JSONObject): List<String> {
        if (o.has("typeIds")) {
            val arr = o.optJSONArray("typeIds")
                ?: throw IllegalArgumentException(context.getString(R.string.import_invalid_json))
            return buildList {
                for (i in 0 until minOf(arr.length(), MAX_WORKOUTS_PER_DAY)) {
                    val id = arr.optString(i, "").trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
        }
        val first = o.optString("typeId", "").trim()
        val second = o.optString("typeId2", "").trim()
        return buildList {
            if (first.isNotEmpty()) add(first)
            if (second.isNotEmpty()) add(second)
        }.take(MAX_WORKOUTS_PER_DAY)
    }

    private fun migrateLocked(prefs: MutablePreferences) {
        if (prefs[KEY_SCHEMA] == SCHEMA_V1_2 && prefs[KEY_TYPES] != null) return

        if (prefs[KEY_TYPES] == null) {
            prefs[KEY_TYPES] = serializeTypes(WorkoutType.defaults())
        }

        val keys = prefs.asMap().keys.toList()
        for (key in keys) {
            if (key.name.startsWith("__")) continue
            val value = prefs[key] as? String ?: continue
            val stringKey = stringPreferencesKey(key.name)
            val decoded = decodeDayTypeIds(value).mapNotNull { id ->
                if (id in ENUM_NAMES) WorkoutType.migrateEnumName(id) else id
            }.take(MAX_WORKOUTS_PER_DAY)
            if (decoded.isEmpty()) {
                prefs.remove(stringKey)
            } else {
                prefs[stringKey] = encodeDayTypeIds(decoded)
            }
        }

        prefs[KEY_SCHEMA] = SCHEMA_V1_2
    }

    /** Persist day slots as a JSON string array. */
    private fun encodeDayTypeIds(ids: List<String>): String {
        val arr = JSONArray()
        ids.take(MAX_WORKOUTS_PER_DAY).forEach { arr.put(it) }
        return arr.toString()
    }

    /**
     * Read day value: JSON array `["id"]` / `["a","b"]`, or legacy plain typeId / enum name.
     */
    private fun decodeDayTypeIds(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) {
            return runCatching {
                val arr = JSONArray(trimmed)
                buildList {
                    for (i in 0 until minOf(arr.length(), MAX_WORKOUTS_PER_DAY)) {
                        val id = arr.optString(i, "").trim()
                        if (id.isNotEmpty()) add(id)
                    }
                }
            }.getOrElse { emptyList() }
        }
        return listOf(trimmed)
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
