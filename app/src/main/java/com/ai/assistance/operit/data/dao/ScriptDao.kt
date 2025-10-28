package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.script.ScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun observeScripts(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun observeScript(id: String): Flow<ScriptEntity?>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getScript(id: String): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: ScriptEntity)

    @Update
    suspend fun update(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(script: ScriptEntity)
}
