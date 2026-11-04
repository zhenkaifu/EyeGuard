package com.eyeguard.app.feature.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [FocusSessionEntity::class, FocusTaskEntity::class], version = 3, exportSchema = false)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun focusTaskDao(): FocusTaskDao
}

@Module
@InstallIn(SingletonComponent::class)
object FocusDatabaseModule {
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE focus_sessions ADD COLUMN goal TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE focus_sessions ADD COLUMN pausedMs INTEGER NOT NULL DEFAULT 0")
        }
    }
    internal val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS focus_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    planDay INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    rewardXp INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    completedAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_focus_tasks_planDay ON focus_tasks(planDay)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusDatabase =
        Room.databaseBuilder(context, FocusDatabase::class.java, "eyeguard_focus.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideFocusSessionDao(database: FocusDatabase): FocusSessionDao = database.focusSessionDao()

    @Provides
    fun provideFocusTaskDao(database: FocusDatabase): FocusTaskDao = database.focusTaskDao()
}
