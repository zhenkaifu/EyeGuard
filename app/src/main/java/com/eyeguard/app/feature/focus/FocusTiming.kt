package com.eyeguard.app.feature.focus

import kotlin.math.abs

/** 单调时钟计算有效时间；暂停状态直接读取冻结的剩余时间。 */
object FocusTiming {
    data class Result(
        val remainingMs: Long,
        val elapsedMs: Long,
        val timeAdjusted: Boolean
    )

    fun resolve(
        snapshot: FocusSnapshot,
        wallNow: Long = System.currentTimeMillis(),
        elapsedNow: Long = android.os.SystemClock.elapsedRealtime()
    ): Result {
        if (snapshot.phase != FocusPhase.ACTIVE) {
            val remaining = snapshot.activeRemainingMs.coerceAtLeast(0L)
            val duration = snapshot.activeDurationMinutes * 60_000L
            return Result(remaining, (duration - remaining).coerceAtLeast(0L), false)
        }
        val monotonicValid = snapshot.activeElapsedEndAt > snapshot.activeElapsedStartAt &&
            snapshot.activeElapsedStartAt > 0L && snapshot.activeElapsedStartAt <= elapsedNow
        val remaining = if (monotonicValid) snapshot.activeElapsedEndAt - elapsedNow
        else snapshot.activeEndAt - wallNow
        // 有效学习时长由“本段总时长 - 剩余时长”推导，恢复后不会把暂停时间算进去。
        val elapsed = snapshot.activeDurationMinutes * 60_000L - remaining
        val wallRemaining = snapshot.activeEndAt - wallNow
        return Result(
            remainingMs = remaining.coerceAtLeast(0L),
            elapsedMs = elapsed.coerceAtLeast(0L),
            timeAdjusted = monotonicValid && abs(wallRemaining - remaining) > 120_000L
        )
    }

    fun restRemainingMs(
        snapshot: FocusSnapshot,
        wallNow: Long = System.currentTimeMillis(),
        elapsedNow: Long = android.os.SystemClock.elapsedRealtime()
    ): Long {
        if (snapshot.phase == FocusPhase.PLAN_PAUSED) return snapshot.restRemainingMs.coerceAtLeast(0L)
        if (snapshot.phase != FocusPhase.RESTING) return 0L
        val monotonicValid = snapshot.restElapsedEndAt > 0L && snapshot.restElapsedEndAt >= elapsedNow
        return (if (monotonicValid) snapshot.restElapsedEndAt - elapsedNow
        else snapshot.restEndAt - wallNow).coerceAtLeast(0L)
    }
}
