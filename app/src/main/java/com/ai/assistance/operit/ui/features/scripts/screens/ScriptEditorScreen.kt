package com.ai.assistance.operit.ui.features.scripts.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.script.ScriptCategory
import com.ai.assistance.operit.data.model.script.ScriptEntity
import com.ai.assistance.operit.data.model.script.ScriptLanguage
import com.ai.assistance.operit.data.model.script.ScriptValidationResult
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.scripts.viewmodel.ScriptEditorState

@Composable
fun ScriptEditorScreen(
    state: ScriptEditorState,
    onScriptUpdate: (ScriptEntity) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val script = state.script ?: return
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    CustomScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection(
                script = script,
                state = state,
                onSave = onSave
            )

            ValidationSection(validationResult = state.validationResult)

            BasicInfoSection(
                script = script,
                onScriptUpdate = onScriptUpdate
            )

            CodeEditorSection(
                script = script,
                onScriptUpdate = onScriptUpdate
            )

            MetadataSection(
                script = script,
                onScriptUpdate = onScriptUpdate
            )
        }
    }
}

@Composable
private fun HeaderSection(
    script: ScriptEntity,
    state: ScriptEditorState,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (state.isEditing) "Edit Script" else "New Script",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = script.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onSave, enabled = !state.isSaving && state.validationResult.isValid) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(text = if (state.isSaving) "Saving..." else "Save")
        }
    }
}

@Composable
private fun ValidationSection(validationResult: ScriptValidationResult) {
    AnimatedVisibility(visible = validationResult.errors.isNotEmpty() || validationResult.warnings.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (validationResult.errors.isNotEmpty())
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (validationResult.errors.isNotEmpty()) {
                    Text(
                        text = "Errors",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    validationResult.errors.forEach { error ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (validationResult.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Warnings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    validationResult.warnings.forEach { warning ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = validationResult.isValid && validationResult.warnings.isEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Script is valid",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicInfoSection(
    script: ScriptEntity,
    onScriptUpdate: (ScriptEntity) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Basic Information", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = script.name,
                onValueChange = { onScriptUpdate(script.copy(name = it)) },
                label = { Text("Script Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = script.description,
                onValueChange = { onScriptUpdate(script.copy(description = it)) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Enabled", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = script.isEnabled,
                    onCheckedChange = { onScriptUpdate(script.copy(isEnabled = it)) }
                )
            }
        }
    }
}

@Composable
private fun CodeEditorSection(
    script: ScriptEntity,
    onScriptUpdate: (ScriptEntity) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Code", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScriptLanguage.values().forEach { language ->
                        FilterChip(
                            selected = script.language == language,
                            onClick = { onScriptUpdate(script.copy(language = language)) },
                            label = { Text(language.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            CodeEditor(
                code = script.code,
                language = when (script.language) {
                    ScriptLanguage.KOTLIN -> "kotlin"
                    ScriptLanguage.JAVASCRIPT -> "javascript"
                    ScriptLanguage.JSON -> "json"
                },
                onCodeChange = { onScriptUpdate(script.copy(code = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 500.dp),
                readOnly = false,
                showLineNumbers = true,
                enableCompletion = true
            )
        }
    }
}

@Composable
private fun MetadataSection(
    script: ScriptEntity,
    onScriptUpdate: (ScriptEntity) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Metadata", style = MaterialTheme.typography.titleMedium)

            Text(text = "Category", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScriptCategory.values().forEach { category ->
                    FilterChip(
                        selected = script.category == category,
                        onClick = { onScriptUpdate(script.copy(category = category)) },
                        label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            var tagInput by remember { mutableStateOf("") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Tags", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Add tag") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (tagInput.isNotBlank() && !script.tags.contains(tagInput)) {
                                onScriptUpdate(script.copy(tags = script.tags + tagInput.trim()))
                                tagInput = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    script.tags.forEach { tag ->
                        AssistChip(
                            onClick = { onScriptUpdate(script.copy(tags = script.tags - tag)) },
                            label = { Text("$tag ✕") }
                        )
                    }
                }
            }
        }
    }
}
