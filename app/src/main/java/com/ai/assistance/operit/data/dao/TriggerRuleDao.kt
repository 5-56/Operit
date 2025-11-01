package com.ai.assistance.operit.data.dao

import androidx.room.*
import com.ai.assistance.operit.data.model.TriggerRule
import com.ai.assistance.operit.data.model.TriggerType
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerRuleDao {
    @Query("SELECT * FROM trigger_rules ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<TriggerRule>>
    
    @Query("SELECT * FROM trigger_rules WHERE type = :type ORDER BY updatedAt DESC")
    fun getRulesByType(type: TriggerType): Flow<List<TriggerRule>>
    
    @Query("SELECT * FROM trigger_rules WHERE enabled = 1 ORDER BY updatedAt DESC")
    fun getEnabledRules(): Flow<List<TriggerRule>>
    
    @Query("SELECT * FROM trigger_rules WHERE type = :type AND enabled = 1 ORDER BY updatedAt DESC")
    fun getEnabledRulesByType(type: TriggerType): Flow<List<TriggerRule>>
    
    @Query("SELECT * FROM trigger_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): TriggerRule?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: TriggerRule): Long
    
    @Update
    suspend fun updateRule(rule: TriggerRule)
    
    @Delete
    suspend fun deleteRule(rule: TriggerRule)
    
    @Query("DELETE FROM trigger_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
    
    @Query("DELETE FROM trigger_rules")
    suspend fun deleteAllRules()
    
    @Query("UPDATE trigger_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean, updatedAt: Long)
}
