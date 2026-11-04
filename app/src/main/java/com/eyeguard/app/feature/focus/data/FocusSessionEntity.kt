package com.eyeguard.app.feature.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val xpEarned: Int,
    val status: String,
    val strictMode: Boolean,
    val restCount: Int,
    val goal: String = "",
    val pausedMs: Long = 0L
) {
    companion object {
        const val COMPLETED = "completed"
        const val ABANDONED = "abandoned"
        const val DISTRACTED = "distracted"
        const val SHELVED = "shelved"
    }
}
