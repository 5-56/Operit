package com.ai.assistance.operit.data.repository

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.dao.TriggerRuleDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.TriggerRule
import com.ai.assistance.operit.data.model.TriggerType
import kotlinx.coroutines.flow.Flow

class TriggerRuleRepository private constructor(context: Context) {
    
    private val triggerRuleDao: TriggerRuleDao
    
    init {
        val database = AppDatabase.getDatabase(context)
        triggerRuleDao = database.triggerRuleDao()
        Log.d(TAG, "TriggerRuleRepository initialized")
    }
    
    companion object {
        private const val TAG = "TriggerRuleRepository"
        
        @Volatile
        private var INSTANCE: TriggerRuleRepository? = null
        
        fun getInstance(context: Context): TriggerRuleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TriggerRuleRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    fun getAllRules(): Flow<List<TriggerRule>> {
        return triggerRuleDao.getAllRules()
    }
    
    fun getRulesByType(type: TriggerType): Flow<List<TriggerRule>> {
        return triggerRuleDao.getRulesByType(type)
    }
    
    fun getEnabledRules(): Flow<List<TriggerRule>> {
        return triggerRuleDao.getEnabledRules()
    }
    
    fun getEnabledRulesByType(type: TriggerType): Flow<List<TriggerRule>> {
        return triggerRuleDao.getEnabledRulesByType(type)
    }
    
    suspend fun getRuleById(id: Long): TriggerRule? {
        return triggerRuleDao.getRuleById(id)
    }
    
    suspend fun insertRule(rule: TriggerRule): Long {
        Log.d(TAG, "Inserting trigger rule: ${rule.name}")
        val timestamp = System.currentTimeMillis()
        return triggerRuleDao.insertRule(
            rule.copy(
                createdAt = if (rule.createdAt == 0L) timestamp else rule.createdAt,
                updatedAt = timestamp
            )
        )
    }
    
    suspend fun updateRule(rule: TriggerRule) {
        Log.d(TAG, "Updating trigger rule: ${rule.name}")
        triggerRuleDao.updateRule(rule.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deleteRule(rule: TriggerRule) {
        Log.d(TAG, "Deleting trigger rule: ${rule.name}")
        triggerRuleDao.deleteRule(rule)
    }
    
    suspend fun deleteRuleById(id: Long) {
        Log.d(TAG, "Deleting trigger rule by ID: $id")
        triggerRuleDao.deleteRuleById(id)
    }
    
    suspend fun deleteAllRules() {
        Log.d(TAG, "Deleting all trigger rules")
        triggerRuleDao.deleteAllRules()
    }
    
    suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        Log.d(TAG, "Setting trigger rule $id enabled status to: $enabled")
        triggerRuleDao.setRuleEnabled(id, enabled, System.currentTimeMillis())
    }
}
