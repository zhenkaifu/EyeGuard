package com.eyeguard.app.feature.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusProgressionTest {
    @Test
    fun `new cultivator starts at qi layer one`() {
        val level = FocusProgression.levelFor(0)
        assertEquals(0, level.realmIndex)
        assertEquals(1, level.layer)
        assertEquals(50, level.xpToNext)
        assertEquals(0f, level.progress, 0.001f)
    }

    @Test
    fun `each realm has ten layers before major breakthrough`() {
        val layerTwo = FocusProgression.levelFor(50)
        assertEquals(0, layerTwo.realmIndex)
        assertEquals(2, layerTwo.layer)

        val layerTen = FocusProgression.levelFor(450)
        assertEquals(0, layerTen.realmIndex)
        assertEquals(10, layerTen.layer)

        val foundation = FocusProgression.levelFor(500)
        assertEquals(1, foundation.realmIndex)
        assertEquals(1, foundation.layer)
        assertEquals(100, foundation.xpToNext)
    }

    @Test
    fun `every later realm doubles xp required per layer`() {
        assertEquals(0, FocusProgression.realmStartXp(0))
        assertEquals(500, FocusProgression.realmStartXp(1))
        assertEquals(1_500, FocusProgression.realmStartXp(2))
        assertEquals(3_500, FocusProgression.realmStartXp(3))
        assertEquals(71, FocusProgression.thresholds.size)
    }

    @Test
    fun `normal earns one point per minute and strict earns one point five rounded up`() {
        assertEquals(25, FocusProgression.xpForMinutes(25, false))
        assertEquals(38, FocusProgression.xpForMinutes(25, true))
        assertEquals(30, FocusProgression.xpForMinutes(30, false))
        assertEquals(45, FocusProgression.xpForMinutes(30, true))
    }

    @Test
    fun `negative xp is safely clamped`() {
        assertEquals(0, FocusProgression.levelFor(-10).currentXp)
    }

    @Test
    fun `seventieth layer is the highest stage`() {
        val level = FocusProgression.levelFor(FocusProgression.thresholds.last())
        assertEquals(6, level.realmIndex)
        assertEquals(10, level.layer)
        assertNull(level.nextThreshold)
        assertEquals(1f, level.progress, 0.001f)
    }
}
