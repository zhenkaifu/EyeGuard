package com.eyeguard.app.feature.focus

/** 今日修行的纯排程逻辑；休息与暂停不占用目标分钟。 */
object FocusPlanLogic {
    const val MIN_SEGMENT_MINUTES = 25

    fun segmentsFor(targetMinutes: Int, preferredSegmentMinutes: Int): List<Int> {
        if (targetMinutes < MIN_SEGMENT_MINUTES) return emptyList()
        val block = preferredSegmentMinutes.coerceAtLeast(MIN_SEGMENT_MINUTES)
        val result = mutableListOf<Int>()
        var remaining = targetMinutes
        while (remaining >= block) {
            result += block
            remaining -= block
        }
        when {
            remaining >= MIN_SEGMENT_MINUTES -> result += remaining
            remaining > 0 && result.isNotEmpty() -> result[result.lastIndex] += remaining
            remaining > 0 -> result += remaining
        }
        return result
    }

    /** 当前段即时改时后重排未来段，保持今日总目标不变。 */
    fun rescheduleCurrent(
        targetMinutes: Int,
        completedMinutes: Int,
        currentIndex: Int,
        existingSegments: List<Int>,
        preferredSegmentMinutes: Int,
        newCurrentMinutes: Int
    ): List<Int>? {
        if (currentIndex !in existingSegments.indices) return null
        val remainingTotal = targetMinutes - completedMinutes.coerceAtLeast(0)
        val futureTotal = remainingTotal - newCurrentMinutes
        if (newCurrentMinutes < MIN_SEGMENT_MINUTES || futureTotal < 0) return null
        if (futureTotal in 1 until MIN_SEGMENT_MINUTES) return null
        val future = if (futureTotal == 0) emptyList()
        else segmentsFor(futureTotal, preferredSegmentMinutes)
        return existingSegments.take(currentIndex) + newCurrentMinutes + future
    }

    fun recommendedRestSeconds(segmentMinutes: Int): Int =
        (segmentMinutes * 2).coerceIn(30, 120)
}
