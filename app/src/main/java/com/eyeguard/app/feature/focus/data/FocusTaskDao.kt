package com.eyeguard.app.feature.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusTaskDao {
    @Query("SELECT * FROM focus_tasks WHERE planDay = :day ORDER BY createdAt DESC")
    fun tasksForDay(day: Long): Flow<List<FocusTaskEntity>>

    @Query("SELECT * FROM focus_tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FocusTaskEntity?

    @Insert
    suspend fun insert(task: FocusTaskEntity): Long

    @Query("UPDATE focus_tasks SET status = :newStatus, completedAt = :completedAt WHERE id = :id AND status = :expectedStatus")
    suspend fun updateStatus(id: Long, expectedStatus: String, newStatus: String, completedAt: Long): Int
}
