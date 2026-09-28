package com.uglygameface.atmosynq.worlds

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldStreakPolicyTest {
    @Test
    fun consecutiveDayExtendsStreak() {
        assertEquals(
            5,
            WorldStreakPolicy.nextStreak(
                previousDate = LocalDate.parse("2026-09-27"),
                previousStreak = 4,
                currentDate = LocalDate.parse("2026-09-28")
            )
        )
    }

    @Test
    fun skippedDayRestartsStreak() {
        assertEquals(
            1,
            WorldStreakPolicy.nextStreak(
                previousDate = LocalDate.parse("2026-09-25"),
                previousStreak = 9,
                currentDate = LocalDate.parse("2026-09-28")
            )
        )
    }

    @Test
    fun duplicateCompletionDoesNotIncrement() {
        assertEquals(
            3,
            WorldStreakPolicy.nextStreak(
                previousDate = LocalDate.parse("2026-09-28"),
                previousStreak = 3,
                currentDate = LocalDate.parse("2026-09-28")
            )
        )
    }
}
