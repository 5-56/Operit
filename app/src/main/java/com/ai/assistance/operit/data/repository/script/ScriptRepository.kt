package com.ai.assistance.operit.data.repository.script

import android.content.Context
import com.ai.assistance.operit.data.dao.ScriptDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.script.ScriptEntity
import com.ai.assistance.operit.data.model.script.ScriptValidationResult
import kotlinx.coroutines.flow.Flow

class ScriptRepository(context: Context) {
    private val scriptDao: ScriptDao by lazy {
        AppDatabase.getDatabase(context).scriptDao()
    }

    fun observeAllScripts(): Flow<List<ScriptEntity>> {
        return scriptDao.observeScripts()
    }

    fun observeScript(id: String): Flow<ScriptEntity?> {
        return scriptDao.observeScript(id)
    }

    suspend fun getScript(id: String): ScriptEntity? {
        return scriptDao.getScript(id)
    }

    suspend fun createScript(script: ScriptEntity) {
        scriptDao.insert(script.copy(
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun updateScript(script: ScriptEntity) {
        scriptDao.update(script.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteScript(id: String) {
        scriptDao.deleteById(id)
    }

    fun validateScript(script: ScriptEntity): ScriptValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (script.name.isBlank()) {
            errors.add("Script name cannot be empty")
        }

        if (script.code.isBlank()) {
            errors.add("Script code cannot be empty")
        }

        if (script.code.length > 100000) {
            warnings.add("Script is very large, consider breaking it into smaller parts")
        }

        return ScriptValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
