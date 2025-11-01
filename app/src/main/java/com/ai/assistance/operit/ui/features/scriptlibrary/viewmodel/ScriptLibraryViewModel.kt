package com.ai.assistance.operit.ui.features.scriptlibrary.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Script
import com.ai.assistance.operit.data.model.ScriptTag
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.ScriptLibraryRepository
import com.ai.assistance.operit.services.ScriptLibraryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for Script Library feature
 */
class ScriptLibraryViewModel(
    private val context: Context,
    private val service: ScriptLibraryService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScriptLibraryUiState>(ScriptLibraryUiState.Loading)
    val uiState: StateFlow<ScriptLibraryUiState> = _uiState.asStateFlow()

    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts.asStateFlow()

    private val _filteredScripts = MutableStateFlow<List<Script>>(emptyList())
    val filteredScripts: StateFlow<List<Script>> = _filteredScripts.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _tags = MutableStateFlow<List<ScriptTag>>(emptyList())
    val tags: StateFlow<List<ScriptTag>> = _tags.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private val _importDialogState = MutableStateFlow<ImportDialogState>(ImportDialogState.Hidden)
    val importDialogState: StateFlow<ImportDialogState> = _importDialogState.asStateFlow()

    private val _safetyWarningDialog = MutableStateFlow<SafetyWarningDialogState?>(null)
    val safetyWarningDialog: StateFlow<SafetyWarningDialogState?> = _safetyWarningDialog.asStateFlow()

    private lateinit var repository: ScriptLibraryRepository

    init {
        viewModelScope.launch {
            val profileId = preferencesManager.activeProfileIdFlow.first()
            repository = ScriptLibraryRepository(context, profileId)
            loadScripts()
        }
    }

    private suspend fun loadScripts() {
        _uiState.value = ScriptLibraryUiState.Loading
        try {
            val allScripts = repository.getAllScripts()
            _scripts.value = allScripts
            _categories.value = repository.getAllCategories()
            _tags.value = repository.getAllTags()
            applyFilters()
            _uiState.value = ScriptLibraryUiState.Success
        } catch (e: Exception) {
            _uiState.value = ScriptLibraryUiState.Error(e.message ?: "Unknown error")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadScripts()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                applyFilters()
            } else {
                val searchResults = repository.searchScripts(query)
                _filteredScripts.value = searchResults
            }
        }
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
        applyFilters()
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
        applyFilters()
    }

    fun toggleFavoritesOnly() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
        applyFilters()
    }

    private fun applyFilters() {
        viewModelScope.launch {
            var filtered = _scripts.value

            // Apply category filter
            _selectedCategory.value?.let { category ->
                filtered = filtered.filter { it.category == category }
            }

            // Apply tag filter
            _selectedTag.value?.let { tag ->
                filtered = filtered.filter { script ->
                    script.tags.any { it.name == tag }
                }
            }

            // Apply favorites filter
            if (_showFavoritesOnly.value) {
                filtered = filtered.filter { it.isFavorite }
            }

            _filteredScripts.value = filtered.sortedByDescending { it.updatedAt }
        }
    }

    fun toggleFavorite(scriptId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(scriptId)
            loadScripts()
        }
    }

    fun deleteScript(scriptId: Long) {
        viewModelScope.launch {
            repository.deleteScript(scriptId)
            loadScripts()
        }
    }

    fun saveScript(script: Script) {
        viewModelScope.launch {
            // Validate script for safety
            val validation = service.validateScript(script)
            if (validation.requiresUserConfirmation) {
                _safetyWarningDialog.value = SafetyWarningDialogState(
                    script = script,
                    validation = validation
                )
            } else {
                repository.saveScript(script)
                loadScripts()
            }
        }
    }

    fun confirmSaveScript(script: Script) {
        viewModelScope.launch {
            script.isTrusted = true // User confirmed, mark as trusted
            repository.saveScript(script)
            _safetyWarningDialog.value = null
            loadScripts()
        }
    }

    fun dismissSafetyWarning() {
        _safetyWarningDialog.value = null
    }

    // Import operations
    fun showImportDialog() {
        _importDialogState.value = ImportDialogState.SelectSource
    }

    fun hideImportDialog() {
        _importDialogState.value = ImportDialogState.Hidden
    }

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            _importDialogState.value = ImportDialogState.Importing
            val result = service.importFromFile(uri)
            _importDialogState.value = if (result.success) {
                loadScripts()
                ImportDialogState.Success(result.message)
            } else {
                ImportDialogState.Error(result.message)
            }
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _importDialogState.value = ImportDialogState.Importing
            val result = service.importFromUrl(url)
            _importDialogState.value = if (result.success) {
                loadScripts()
                ImportDialogState.Success(result.message)
            } else {
                ImportDialogState.Error(result.message)
            }
        }
    }

    fun importBundle(uri: Uri) {
        viewModelScope.launch {
            _importDialogState.value = ImportDialogState.Importing
            val result = service.importBundle(uri)
            _importDialogState.value = if (result.success) {
                loadScripts()
                ImportDialogState.Success(result.message)
            } else {
                ImportDialogState.Error(result.message)
            }
        }
    }

    // Export operations
    fun exportScript(scriptId: Long) {
        viewModelScope.launch {
            val result = service.exportScript(scriptId)
            if (result.success) {
                _uiState.value = ScriptLibraryUiState.ExportSuccess(result.message)
            } else {
                _uiState.value = ScriptLibraryUiState.Error(result.message)
            }
        }
    }

    fun exportScripts(scriptIds: List<Long>) {
        viewModelScope.launch {
            val result = service.exportScripts(scriptIds)
            if (result.success) {
                _uiState.value = ScriptLibraryUiState.ExportSuccess(result.message)
            } else {
                _uiState.value = ScriptLibraryUiState.Error(result.message)
            }
        }
    }

    fun shareScript(scriptId: Long) {
        viewModelScope.launch {
            service.shareScript(scriptId)
        }
    }

    // Backup/Restore operations
    fun createBackup() {
        viewModelScope.launch {
            _uiState.value = ScriptLibraryUiState.Loading
            val result = service.createBackup()
            _uiState.value = if (result.success) {
                ScriptLibraryUiState.BackupSuccess(result.message)
            } else {
                ScriptLibraryUiState.Error(result.message)
            }
        }
    }

    fun restoreBackup(uri: Uri, replaceExisting: Boolean) {
        viewModelScope.launch {
            _uiState.value = ScriptLibraryUiState.Loading
            val result = service.restoreBackup(uri, replaceExisting)
            _uiState.value = if (result.success) {
                loadScripts()
                ScriptLibraryUiState.RestoreSuccess(result.message)
            } else {
                ScriptLibraryUiState.Error(result.message)
            }
        }
    }

    fun addTagToScript(script: Script, tagName: String) {
        viewModelScope.launch {
            repository.addTagToScript(script, tagName)
            loadScripts()
        }
    }

    fun removeTagFromScript(script: Script, tagName: String) {
        viewModelScope.launch {
            repository.removeTagFromScript(script, tagName)
            loadScripts()
        }
    }

    fun recordScriptUsage(scriptId: Long) {
        viewModelScope.launch {
            repository.recordScriptUsage(scriptId)
        }
    }

    class Factory(
        private val context: Context,
        private val service: ScriptLibraryService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScriptLibraryViewModel::class.java)) {
                return ScriptLibraryViewModel(context, service) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// UI States
sealed class ScriptLibraryUiState {
    object Loading : ScriptLibraryUiState()
    object Success : ScriptLibraryUiState()
    data class Error(val message: String) : ScriptLibraryUiState()
    data class ExportSuccess(val message: String) : ScriptLibraryUiState()
    data class BackupSuccess(val message: String) : ScriptLibraryUiState()
    data class RestoreSuccess(val message: String) : ScriptLibraryUiState()
}

sealed class ImportDialogState {
    object Hidden : ImportDialogState()
    object SelectSource : ImportDialogState()
    object Importing : ImportDialogState()
    data class Success(val message: String) : ImportDialogState()
    data class Error(val message: String) : ImportDialogState()
}

data class SafetyWarningDialogState(
    val script: Script,
    val validation: com.ai.assistance.operit.services.ValidationResult
)
