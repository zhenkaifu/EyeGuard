package com.eyeguard.app.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimingTest {
    private fun snapshot() = FocusSnapshot(
        phase = FocusPhase.ACTIVE,
        activeStartedAt = 1_000_000L,
        activeEndAt = 2_500_000L,
        activeDurationMinutes = 25,
        activeElapsedStartAt = 10_000L,
        activeElapsedEndAt = 1_510_000L
    )

    @Test
    fun `wall clock changes do not alter remaining time`() {
        val result = FocusTiming.resolve(snapshot(), wallNow = 9_000_000L, elapsedNow = 610_000L)
        assertEquals(900_000L, result.remainingMs)
        assertEquals(600_000L, result.elapsedMs)
        assertTrue(result.timeAdjusted)
    }

    @Test
    fun `monotonic clock completes at real deadline`() {
        val result = FocusTiming.resolve(snapshot(), wallNow = 2_500_000L, elapsedNow = 1_510_000L)
        assertEquals(0L, result.remainingMs)
        assertEquals(1_500_000L, result.elapsedMs)
        assertFalse(result.timeAdjusted)
    }

    @Test
    fun `device reboot falls back to wall clock`() {
        val result = FocusTiming.resolve(snapshot(), wallNow = 1_600_000L, elapsedNow = 5_000L)
        assertEquals(900_000L, result.remainingMs)
        assertEquals(600_000L, result.elapsedMs)
    }

    @Test
    fun `presence pause freezes remaining time indefinitely`() {
        val paused = snapshot().copy(
            phase = FocusPhase.PRESENCE_PAUSED,
            activeRemainingMs = 600_000L,
            activeEndAt = 0L,
            activeElapsedEndAt = 0L
        )
        val first = FocusTiming.resolve(paused, wallNow = 2_000_000L, elapsedNow = 700_000L)
        val later = FocusTiming.resolve(paused, wallNow = 8_000_000L, elapsedNow = 6_700_000L)
        assertEquals(600_000L, first.remainingMs)
        assertEquals(first.remainingMs, later.remainingMs)
        assertEquals(900_000L, later.elapsedMs)
    }

    @Test
    fun `resumed session excludes time spent paused`() {
        val resumed = snapshot().copy(
            activeElapsedStartAt = 10_000L,
            activeElapsedEndAt = 3_010_000L,
            activeEndAt = 4_000_000L
        )
        val result = FocusTiming.resolve(resumed, wallNow = 2_500_000L, elapsedNow = 1_510_000L)
        assertEquals(1_500_000L, result.remainingMs)
        assertEquals(0L, result.elapsedMs)
    }
}
