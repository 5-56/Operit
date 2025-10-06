package com.xihe.assistant.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xihe.assistant.data.db.Problem

/**
 * 问题数据访问对象
 */
@Dao
interface ProblemDao {
    @Query("SELECT COUNT(*) FROM Problem")
    suspend fun getProblemCount(): Int

    @Insert
    suspend fun insertProblem(problem: Problem)

    @Query("SELECT * FROM Problem")
    suspend fun getAllProblems(): List<Problem>
}