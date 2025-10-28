package com.ai.assistance.operit.ui.features.scripts.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.model.script.ScriptEntity
import com.ai.assistance.operit.ui.features.scripts.viewmodel.ScriptViewModel

@Composable
fun ScriptsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: ScriptViewModel = viewModel(
        factory = ScriptViewModel.Factory(context)
    )

    val listState by viewModel.listState.collectAsState()
    val editorState by viewModel.editorState.collectAsState()

    var currentView by remember { mutableStateOf(ScriptView.LIST) }
    var selectedScript by remember { mutableStateOf<ScriptEntity?>(null) }

    when (currentView) {
        ScriptView.LIST -> {
            ScriptListScreen(
                state = listState,
                onSearchQueryChange = { viewModel.searchScripts(it) },
                onCategorySelected = { viewModel.filterByCategory(it) },
                onNewScript = {
                    viewModel.createNewScript()
                    currentView = ScriptView.EDITOR
                },
                onScriptSelected = { script ->
                    selectedScript = script
                    currentView = ScriptView.DETAIL
                }
            )
        }
        ScriptView.DETAIL -> {
            selectedScript?.let { script ->
                ScriptDetailScreen(
                    script = script,
                    onEdit = {
                        viewModel.loadScript(script.id)
                        currentView = ScriptView.EDITOR
                    },
                    onRun = {
                    },
                    onDelete = {
                        viewModel.deleteScript(script.id)
                        currentView = ScriptView.LIST
                        selectedScript = null
                    },
                    onBack = {
                        currentView = ScriptView.LIST
                        selectedScript = null
                    }
                )
            }
        }
        ScriptView.EDITOR -> {
            ScriptEditorScreen(
                state = editorState,
                onScriptUpdate = { viewModel.updateScriptField(it) },
                onSave = {
                    viewModel.saveScript()
                    currentView = ScriptView.LIST
                },
                onBack = {
                    currentView = ScriptView.LIST
                }
            )
        }
    }
}

enum class ScriptView {
    LIST,
    DETAIL,
    EDITOR
}
