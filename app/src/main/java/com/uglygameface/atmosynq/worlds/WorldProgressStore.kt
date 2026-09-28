package com.uglygameface.atmosynq.worlds

import android.content.Context
import java.time.LocalDate

data class WorldProgress(
    val completedToday: Boolean,
    val streakDays: Int
)

class WorldProgressStore(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "atmosynq_world_progress",
            Context.MODE_PRIVATE
        )

    fun progressFor(
        date: LocalDate,
        kind: WorldKind
    ): WorldProgress =
        WorldProgress(
            completedToday =
                completedKeys().contains(
                    completionKey(date, kind)
                ),
            streakDays =
                prefs.getInt(KEY_STREAK_DAYS, 0)
                    .coerceAtLeast(0)
        )

    fun markCompleted(
        date: LocalDate,
        kind: WorldKind
    ): WorldProgress {
        val key = completionKey(date, kind)
        if (completedKeys().contains(key)) {
            return progressFor(date, kind)
        }

        val previousDate =
            prefs.getString(
                KEY_LAST_COMPLETED_DATE,
                null
            )?.let { raw ->
                runCatching {
                    LocalDate.parse(raw)
                }.getOrNull()
            }

        val previousStreak =
            prefs.getInt(
                KEY_STREAK_DAYS,
                0
            ).coerceAtLeast(0)

        val streak =
            WorldStreakPolicy.nextStreak(
                previousDate = previousDate,
                previousStreak = previousStreak,
                currentDate = date
            )

        val todayPrefix = "$date:"
        val updatedKeys =
            completedKeys()
                .filterTo(mutableSetOf()) {
                    it.startsWith(todayPrefix)
                }
                .apply { add(key) }

        prefs.edit()
            .putStringSet(
                KEY_COMPLETED_KEYS,
                updatedKeys
            )
            .putString(
                KEY_LAST_COMPLETED_DATE,
                date.toString()
            )
            .putInt(
                KEY_STREAK_DAYS,
                streak
            )
            .apply()

        return WorldProgress(
            completedToday = true,
            streakDays = streak
        )
    }

    private fun completedKeys(): Set<String> =
        prefs.getStringSet(
            KEY_COMPLETED_KEYS,
            emptySet()
        )?.toSet().orEmpty()

    private fun completionKey(
        date: LocalDate,
        kind: WorldKind
    ): String =
        "$date:${kind.name}"

    companion object {
        private const val KEY_COMPLETED_KEYS =
            "completed_keys"
        private const val KEY_LAST_COMPLETED_DATE =
            "last_completed_date"
        private const val KEY_STREAK_DAYS =
            "streak_days"
    }
}

object WorldStreakPolicy {
    fun nextStreak(
        previousDate: LocalDate?,
        previousStreak: Int,
        currentDate: LocalDate
    ): Int =
        when {
            previousDate == currentDate ->
                previousStreak.coerceAtLeast(1)

            previousDate == currentDate.minusDays(1) ->
                previousStreak.coerceAtLeast(0) + 1

            else ->
                1
        }
}
