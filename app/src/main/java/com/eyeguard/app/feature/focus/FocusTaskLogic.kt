package com.eyeguard.app.feature.focus

import com.eyeguard.app.feature.focus.data.FocusTaskEntity

/** 任务状态与奖励规则；恢复已完成任务必须撤回奖励，防止反复刷取。 */
object FocusTaskLogic {
    fun canTransition(from: String, to: String): Boolean = when (from) {
        FocusTaskEntity.ACTIVE -> to in listOf(FocusTaskEntity.COMPLETED, FocusTaskEntity.CANCELLED)
        FocusTaskEntity.COMPLETED, FocusTaskEntity.CANCELLED -> to == FocusTaskEntity.ACTIVE
        else -> false
    }

    fun rewardDelta(from: String, to: String, rewardXp: Int): Int {
        if (!canTransition(from, to)) return 0
        val reward = rewardXp.coerceIn(1, 5)
        return when {
            from == FocusTaskEntity.ACTIVE && to == FocusTaskEntity.COMPLETED -> reward
            from == FocusTaskEntity.COMPLETED && to == FocusTaskEntity.ACTIVE -> -reward
            else -> 0
        }
    }
}
