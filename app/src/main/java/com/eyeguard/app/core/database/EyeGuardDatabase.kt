package com.eyeguard.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * EyeGuard Room 数据库，当前版本 2，包含每日使用统计表。
 * v2：新增最长连续用眼 / 距离过近时长 / 休息完成次数字段。
 */
@Database(entities = [DailyUsageEntity::class], version = 2, exportSchema = false)
abstract class EyeGuardDatabase : RoomDatabase() {

    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        const val DATABASE_NAME = "eyeguard.db"
    }
}

/** Room 依赖注入模块。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EyeGuardDatabase =
        Room.databaseBuilder(context, EyeGuardDatabase::class.java, EyeGuardDatabase.DATABASE_NAME)
            // 开发阶段结构变更直接重建表；发布前需替换为正式 Migration
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDailyUsageDao(db: EyeGuardDatabase): DailyUsageDao = db.dailyUsageDao()
}
