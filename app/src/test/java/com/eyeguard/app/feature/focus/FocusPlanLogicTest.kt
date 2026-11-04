package com.eyeguard.app.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPlanLogicTest {
    @Test
    fun `three hour plan uses six standard sessions and a thirty minute tail`() {
        assertEquals(listOf(25, 25, 25, 25, 25, 25, 30),
            FocusPlanLogic.segmentsFor(180, 25))
    }

    @Test
    fun `short tail is merged into previous session`() {
        assertEquals(listOf(25, 45), FocusPlanLogic.segmentsFor(70, 25))
        assertEquals(listOf(30), FocusPlanLogic.segmentsFor(30, 25))
    }

    @Test
    fun `target below minimum is rejected`() {
        assertTrue(FocusPlanLogic.segmentsFor(24, 25).isEmpty())
    }

    @Test
    fun `current session can grow while daily target remains unchanged`() {
        val updated = FocusPlanLogic.rescheduleCurrent(
            targetMinutes = 180,
            completedMinutes = 50,
            currentIndex = 2,
            existingSegments = listOf(25, 25, 25, 25, 25, 25, 30),
            preferredSegmentMinutes = 25,
            newCurrentMinutes = 35
        )
        assertEquals(listOf(25, 25, 35, 25, 25, 45), updated)
        assertEquals(180, updated?.sum())
    }

    @Test
    fun `current session cannot be under minimum or leave a tiny tail`() {
        val existing = listOf(25, 25, 25)
        assertNull(FocusPlanLogic.rescheduleCurrent(75, 0, 0, existing, 25, 24))
        assertNull(FocusPlanLogic.rescheduleCurrent(75, 0, 0, existing, 25, 60))
    }

    @Test
    fun `longer sessions receive a longer distance viewing suggestion`() {
        assertEquals(30, FocusPlanLogic.recommendedRestSeconds(1))
        assertEquals(50, FocusPlanLogic.recommendedRestSeconds(25))
        assertEquals(120, FocusPlanLogic.recommendedRestSeconds(90))
    }
}
