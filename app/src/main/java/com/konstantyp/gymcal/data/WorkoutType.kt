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

        /** Factory defaults for first launch / clear-data — Push / Pull / Legs only. */
        fun defaults(): List<WorkoutType> = listOf(
            WorkoutType(id = "default-push", name = "Push", seedArgb = 0xFFC62828L, sortOrder = 0),
            WorkoutType(id = "default-pull", name = "Pull", seedArgb = 0xFF1565C0L, sortOrder = 1),
            WorkoutType(id = "default-legs", name = "Legs", seedArgb = 0xFF2E7D32L, sortOrder = 2),
        )

        /** Map old enum name → stable default id (migration; Chest/Biceps kept for old data). */
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
