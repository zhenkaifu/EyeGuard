package com.eyeguard.app.feature.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusDatabaseMigrationTest {
    @Test
    fun migration2To3_preservesSessionsAndCreatesExactTaskSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "focus-migration-${System.nanoTime()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE focus_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    endedAt INTEGER NOT NULL,
                    plannedMinutes INTEGER NOT NULL,
                    actualMinutes INTEGER NOT NULL,
                    xpEarned INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    strictMode INTEGER NOT NULL,
                    restCount INTEGER NOT NULL,
                    goal TEXT NOT NULL DEFAULT '',
                    pausedMs INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO focus_sessions (
                    startedAt, endedAt, plannedMinutes, actualMinutes,
                    xpEarned, status, strictMode, restCount, goal, pausedMs
                ) VALUES (100, 200, 25, 25, 38, 'completed', 1, 0, '保留历史', 0)
                """.trimIndent()
            )
            sqlite.version = 2
        }

        val database = Room.databaseBuilder(context, FocusDatabase::class.java, name)
            .addMigrations(FocusDatabaseModule.MIGRATION_1_2, FocusDatabaseModule.MIGRATION_2_3)
            .build()
        try {
            // 打开数据库即执行迁移和 Room 的完整表结构校验。
            val sessions = database.focusSessionDao().recentSessions().first()
            assertEquals(1, sessions.size)
            assertEquals("保留历史", sessions.single().goal)
            assertEquals(38, sessions.single().xpEarned)

            val taskId = database.focusTaskDao().insert(
                FocusTaskEntity(
                    planDay = 20_000L,
                    title = "迁移后新增任务",
                    rewardXp = 3,
                    createdAt = 300L
                )
            )
            val task = database.focusTaskDao().findById(taskId)
            assertNotNull(task)
            assertEquals(0L, task?.completedAt)

            val readable = database.openHelper.readableDatabase
            readable.query("PRAGMA table_info(focus_tasks)").use { cursor ->
                var checked = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "completedAt") {
                        checked = true
                        assertEquals(1, cursor.getInt(3))
                        assertNull(cursor.getString(4))
                    }
                }
                assertTrue("completedAt column missing", checked)
            }
            readable.query("PRAGMA index_list(focus_tasks)").use { cursor ->
                val names = mutableListOf<String>()
                while (cursor.moveToNext()) names += cursor.getString(1)
                assertTrue(names.contains("index_focus_tasks_planDay"))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
