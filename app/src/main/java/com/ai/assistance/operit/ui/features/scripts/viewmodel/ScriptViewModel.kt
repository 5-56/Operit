package com.ai.assistance.operit.ui.features.scripts.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.script.ScriptCategory
import com.ai.assistance.operit.data.model.script.ScriptEntity
import com.ai.assistance.operit.data.model.script.ScriptLanguage
import com.ai.assistance.operit.data.model.script.ScriptValidationResult
import com.ai.assistance.operit.data.repository.script.ScriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScriptListState(
    val scripts: List<ScriptEntity> = emptyList(),
    val filteredScripts: List<ScriptEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: ScriptCategory? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ScriptEditorState(
    val script: ScriptEntity? = null,
    val isEditing: Boolean = false,
    val validationResult: ScriptValidationResult = ScriptValidationResult(true),
    val isSaving: Boolean = false,
    val error: String? = null
)

class ScriptViewModel(private val repository: ScriptRepository) : ViewModel() {

    private val _listState = MutableStateFlow(ScriptListState())
    val listState: StateFlow<ScriptListState> = _listState.asStateFlow()

    private val _editorState = MutableStateFlow(ScriptEditorState())
    val editorState: StateFlow<ScriptEditorState> = _editorState.asStateFlow()

    val scripts: StateFlow<List<ScriptEntity>> = repository.observeAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            scripts.collect { allScripts ->
                _listState.value = _listState.value.copy(
                    scripts = allScripts,
                    filteredScripts = filterScripts(allScripts, _listState.value.searchQuery, _listState.value.selectedCategory)
                )
            }
        }
    }

    fun searchScripts(query: String) {
        _listState.value = _listState.value.copy(
            searchQuery = query,
            filteredScripts = filterScripts(_listState.value.scripts, query, _listState.value.selectedCategory)
        )
    }

    fun filterByCategory(category: ScriptCategory?) {
        _listState.value = _listState.value.copy(
            selectedCategory = category,
            filteredScripts = filterScripts(_listState.value.scripts, _listState.value.searchQuery, category)
        )
    }

    private fun filterScripts(scripts: List<ScriptEntity>, query: String, category: ScriptCategory?): List<ScriptEntity> {
        var filtered = scripts
        
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
        
        if (category != null) {
            filtered = filtered.filter { it.category == category }
        }
        
        return filtered
    }

    fun loadScript(id: String) {
        viewModelScope.launch {
            try {
                val script = repository.getScript(id)
                _editorState.value = _editorState.value.copy(
                    script = script,
                    isEditing = script != null
                )
                script?.let { validateScript(it) }
            } catch (e: Exception) {
                _editorState.value = _editorState.value.copy(
                    error = "Failed to load script: ${e.message}"
                )
            }
        }
    }

    fun createNewScript() {
        _editorState.value = ScriptEditorState(
            script = ScriptEntity(
                name = "New Script",
                description = "",
                code = "// Write your script here\n",
                language = ScriptLanguage.KOTLIN,
                category = ScriptCategory.AUTOMATION
            ),
            isEditing = false
        )
    }

    fun updateScriptField(script: ScriptEntity) {
        _editorState.value = _editorState.value.copy(script = script)
        validateScript(script)
    }

    private fun validateScript(script: ScriptEntity) {
        val validationResult = repository.validateScript(script)
        _editorState.value = _editorState.value.copy(validationResult = validationResult)
    }

    fun saveScript() {
        val script = _editorState.value.script ?: return
        
        val validation = repository.validateScript(script)
        if (!validation.isValid) {
            _editorState.value = _editorState.value.copy(
                validationResult = validation,
                error = "Cannot save: ${validation.errors.firstOrNull()}"
            )
            return
        }

        viewModelScope.launch {
            try {
                _editorState.value = _editorState.value.copy(isSaving = true, error = null)
                
                if (_editorState.value.isEditing) {
                    repository.updateScript(script)
                } else {
                    repository.createScript(script)
                }
                
                _editorState.value = _editorState.value.copy(
                    isSaving = false,
                    isEditing = true
                )
            } catch (e: Exception) {
                _editorState.value = _editorState.value.copy(
                    isSaving = false,
                    error = "Failed to save script: ${e.message}"
                )
            }
        }
    }

    fun deleteScript(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteScript(id)
            } catch (e: Exception) {
                _listState.value = _listState.value.copy(
                    error = "Failed to delete script: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _listState.value = _listState.value.copy(error = null)
        _editorState.value = _editorState.value.copy(error = null)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScriptViewModel::class.java)) {
                return ScriptViewModel(ScriptRepository(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
