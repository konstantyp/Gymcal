package com.konstantyp.gymcal.data

/**
 * Editable local workout type (v1.1). Replaces the v1.0 fixed enum.
 * [seedArgb] is the user-chosen seed (not the runtime container color).
 */
data class WorkoutType(
    val id: String,
    val name: String,
    val seedArgb: Long,
    val sortOrder: Int,
) {
    companion object {
        const val MAX_TYPES = 20
        const val NAME_MAX_LEN = 24

        /** Default seed list matching v1.0 §1.2 — written on first launch. */
        fun defaults(): List<WorkoutType> = listOf(
            WorkoutType(id = "default-push", name = "Push", seedArgb = 0xFFC45C26L, sortOrder = 0),
            WorkoutType(id = "default-pull", name = "Pull", seedArgb = 0xFF2B6CB0L, sortOrder = 1),
            WorkoutType(id = "default-legs", name = "Legs", seedArgb = 0xFF2F855AL, sortOrder = 2),
            WorkoutType(id = "default-chest", name = "Chest", seedArgb = 0xFFB83280L, sortOrder = 3),
            WorkoutType(id = "default-biceps", name = "Biceps", seedArgb = 0xFF6B46C1L, sortOrder = 4),
        )

        /** Map old enum name → stable default id (migration). */
        fun migrateEnumName(enumName: String): String? = when (enumName) {
            "Push" -> "default-push"
            "Pull" -> "default-pull"
            "Legs" -> "default-legs"
            "Chest" -> "default-chest"
            "Biceps" -> "default-biceps"
            else -> null
        }
    }
}
