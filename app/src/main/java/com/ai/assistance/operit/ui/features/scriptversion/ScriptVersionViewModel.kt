package com.ai.assistance.operit.ui.features.scriptversion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.ScriptHistory
import com.ai.assistance.operit.data.model.ScriptVersionRecord
import com.ai.assistance.operit.data.repository.ScriptExportData
import com.ai.assistance.operit.data.repository.ScriptVersionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

class ScriptVersionViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScriptVersionState())
    val state: StateFlow<ScriptVersionState> = _state.asStateFlow()

    private var repository: ScriptVersionRepository? = null
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun loadScript(context: Context, filePath: String) {
        ensureRepository(context)
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                val history = repository?.getScriptByPath(filePath)
                if (history == null) {
                    _state.update { it.copy(isLoading = false, errorMessage = "Script not found") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        script = history,
                        versions = history.versions.sortedByDescending { v -> v.versionNumber }
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun rollbackToVersion(context: Context, filePath: String, versionId: String) {
        ensureRepository(context)
        viewModelScope.launch {
            try {
                val result = repository?.rollbackToVersion(filePath, versionId)
                result?.onSuccess { newVersion ->
                    val updatedHistory = repository?.getScriptByPath(filePath)
                    _state.update {
                        it.copy(
                            versions = updatedHistory?.versions?.sortedByDescending { v -> v.versionNumber } ?: emptyList(),
                            script = updatedHistory,
                            message = "Rolled back to version ${newVersion.versionNumber}"
                        )
                    }
                }?.onFailure { error ->
                    _state.update { it.copy(message = "Rollback failed: ${error.message}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Rollback failed: ${e.message}") }
            }
        }
    }

    fun deleteVersion(context: Context, filePath: String, versionId: String) {
        ensureRepository(context)
        viewModelScope.launch {
            try {
                val result = repository?.deleteVersion(filePath, versionId)
                result?.onSuccess {
                    val updatedHistory = repository?.getScriptByPath(filePath)
                    _state.update {
                        it.copy(
                            versions = updatedHistory?.versions?.sortedByDescending { v -> v.versionNumber } ?: emptyList(),
                            script = updatedHistory,
                            message = "Version deleted"
                        )
                    }
                }?.onFailure { error ->
                    _state.update { it.copy(message = "Delete failed: ${error.message}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Delete failed: ${e.message}") }
            }
        }
    }

    fun exportScript(context: Context, filePath: String, exportPath: String) {
        ensureRepository(context)
        viewModelScope.launch {
            try {
                val exportResult = repository?.exportScriptWithHistory(filePath)
                exportResult?.onSuccess { data ->
                    saveExportToFile(exportPath, data)
                    _state.update { it.copy(message = "Exported to $exportPath") }
                }?.onFailure { error ->
                    _state.update { it.copy(message = "Export failed: ${error.message}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Export failed: ${e.message}") }
            }
        }
    }

    fun importScript(context: Context, importPath: String) {
        ensureRepository(context)
        viewModelScope.launch {
            try {
                val exportedData = loadExportFromFile(importPath)
                val result = repository?.importScriptWithHistory(exportedData)
                result?.onSuccess { history ->
                    _state.update { it.copy(message = "Imported ${history.name}") }
                }?.onFailure { error ->
                    _state.update { it.copy(message = "Import failed: ${error.message}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Import failed: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun ensureRepository(context: Context) {
        if (repository == null) {
            repository = ScriptVersionRepository(context)
        }
    }

    private suspend fun saveExportToFile(filePath: String, data: ScriptExportData) {
        withContext(Dispatchers.IO) {
            val exportJson = json.encodeToString(data)
            File(filePath).writeText(exportJson)
        }
    }

    private suspend fun loadExportFromFile(filePath: String): ScriptExportData {
        return withContext(Dispatchers.IO) {
            val fileContent = File(filePath).readText()
            json.decodeFromString<ScriptExportData>(fileContent)
        }
    }
}

data class ScriptVersionState(
    val isLoading: Boolean = false,
    val script: ScriptHistory? = null,
    val versions: List<ScriptVersionRecord> = emptyList(),
    val errorMessage: String? = null,
    val message: String? = null
)
