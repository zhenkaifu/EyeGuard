package com.eyeguard.app.feature.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY endedAt DESC LIMIT 20")
    fun recentSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM focus_sessions WHERE status = 'completed' AND endedAt >= :startMs")
    fun completedMinutesSince(startMs: Long): Flow<Int>
}
