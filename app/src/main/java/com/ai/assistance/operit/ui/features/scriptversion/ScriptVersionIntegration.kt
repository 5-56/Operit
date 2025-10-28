package com.ai.assistance.operit.ui.features.scriptversion

import android.content.Context
import com.ai.assistance.operit.data.repository.ScriptVersionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ScriptVersionIntegration {
    private var repository: ScriptVersionRepository? = null

    fun getRepository(context: Context): ScriptVersionRepository {
        if (repository == null) {
            repository = ScriptVersionRepository(context.applicationContext)
        }
        return repository!!
    }

    fun saveScriptVersion(
        context: Context,
        filePath: String,
        fileName: String,
        content: String,
        scriptType: String = "javascript",
        commitMessage: String = "",
        author: String = "user",
        isAutoSave: Boolean = true,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = getRepository(context)
                val result = repository.createOrUpdateScript(
                    filePath = filePath,
                    name = fileName,
                    content = content,
                    scriptType = scriptType,
                    description = "",
                    commitMessage = commitMessage,
                    author = author,
                    isAutoSave = isAutoSave
                )
                result.onSuccess {
                    onComplete?.invoke(true, null)
                }.onFailure { error ->
                    onComplete?.invoke(false, error.message)
                }
            } catch (e: Exception) {
                onComplete?.invoke(false, e.message)
            }
        }
    }

    fun getScriptVersions(
        context: Context,
        filePath: String
    ): List<com.ai.assistance.operit.data.model.ScriptVersionRecord> {
        val repository = getRepository(context)
        val script = repository.getScriptByPath(filePath)
        return script?.versions ?: emptyList()
    }

    fun rollbackToVersion(
        context: Context,
        filePath: String,
        versionId: String,
        onComplete: ((Boolean, String?, String?) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = getRepository(context)
                val result = repository.rollbackToVersion(filePath, versionId)
                result.onSuccess { version ->
                    val content = repository.getVersionContent(version)
                    onComplete?.invoke(true, content, null)
                }.onFailure { error ->
                    onComplete?.invoke(false, null, error.message)
                }
            } catch (e: Exception) {
                onComplete?.invoke(false, null, e.message)
            }
        }
    }

    fun getLatestVersion(
        context: Context,
        filePath: String
    ): String? {
        val repository = getRepository(context)
        val script = repository.getScriptByPath(filePath)
        return if (script != null) {
            val currentVersion = script.versions.maxByOrNull { it.versionNumber }
            currentVersion?.let { repository.getVersionContent(it) }
        } else {
            null
        }
    }
}
