package com.xihe.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xihe.assistant.data.dao.ProblemDao

/**
 * 应用数据库
 */
@Database(
    entities = [Problem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun problemDao(): ProblemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xihe_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * 问题实体
 */
data class Problem(
    val id: String,
    val title: String,
    val description: String,
    val solution: String,
    val timestamp: Long
)