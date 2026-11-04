package com.eyeguard.app.feature.focus

import com.eyeguard.app.feature.focus.data.FocusTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTaskLogicTest {
    @Test
    fun `completion grants selected one to five points`() {
        for (reward in 1..5) {
            assertEquals(reward, FocusTaskLogic.rewardDelta(FocusTaskEntity.ACTIVE, FocusTaskEntity.COMPLETED, reward))
        }
    }

    @Test
    fun `restoring completed task withdraws exactly the original reward`() {
        assertEquals(-4, FocusTaskLogic.rewardDelta(FocusTaskEntity.COMPLETED, FocusTaskEntity.ACTIVE, 4))
    }

    @Test
    fun `cancelling and restoring unfinished task grants nothing`() {
        assertEquals(0, FocusTaskLogic.rewardDelta(FocusTaskEntity.ACTIVE, FocusTaskEntity.CANCELLED, 5))
        assertEquals(0, FocusTaskLogic.rewardDelta(FocusTaskEntity.CANCELLED, FocusTaskEntity.ACTIVE, 5))
    }

    @Test
    fun `invalid transitions cannot grant or remove points`() {
        assertFalse(FocusTaskLogic.canTransition(FocusTaskEntity.COMPLETED, FocusTaskEntity.CANCELLED))
        assertEquals(0, FocusTaskLogic.rewardDelta(FocusTaskEntity.COMPLETED, FocusTaskEntity.CANCELLED, 5))
        assertTrue(FocusTaskLogic.canTransition(FocusTaskEntity.CANCELLED, FocusTaskEntity.ACTIVE))
    }
}
