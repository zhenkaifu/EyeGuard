package com.eyeguard.app.feature.focus.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_tasks", indices = [Index("planDay")])
data class FocusTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planDay: Long,
    val title: String,
    val rewardXp: Int,
    val status: String = ACTIVE,
    val createdAt: Long,
    val completedAt: Long = 0L
) {
    companion object {
        const val ACTIVE = "active"
        const val COMPLETED = "completed"
        const val CANCELLED = "cancelled"
    }
}
