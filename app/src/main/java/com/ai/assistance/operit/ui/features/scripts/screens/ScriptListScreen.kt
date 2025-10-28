package com.ai.assistance.operit.ui.features.scripts.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.script.ScriptCategory
import com.ai.assistance.operit.data.model.script.ScriptEntity
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.scripts.viewmodel.ScriptListState

@Composable
fun ScriptListScreen(
    state: ScriptListState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (ScriptCategory?) -> Unit,
    onNewScript: () -> Unit,
    onScriptSelected: (ScriptEntity) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    CustomScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = if (isTablet) 32.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection(onNewScript = onNewScript, scriptCount = state.scripts.size)
            SearchAndFilterSection(
                searchQuery = state.searchQuery,
                selectedCategory = state.selectedCategory,
                onSearchQueryChange = onSearchQueryChange,
                onCategorySelected = onCategorySelected
            )
            ScriptListContent(
                state = state,
                onScriptSelected = onScriptSelected
            )
        }
    }
}

@Composable
private fun HeaderSection(onNewScript: () -> Unit, scriptCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Automation Scripts",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Manage reusable automation workflows and utilities",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onNewScript) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(text = "New Script")
        }
    }
    Text(
        text = "$scriptCount scripts available",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SearchAndFilterSection(
    searchQuery: String,
    selectedCategory: ScriptCategory?,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (ScriptCategory?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(text = "Search scripts") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = remember { ScriptCategory.values().toList() }
            AssistChip(
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedCategory == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                )
            )
            categories.forEach { category ->
                AssistChip(
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedCategory == category) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

@Composable
private fun ScriptListContent(state: ScriptListState, onScriptSelected: (ScriptEntity) -> Unit) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Text(text = "Loading scripts")
        }
    } else if (state.filteredScripts.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No scripts found",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Try creating a new script or adjusting your filters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.filteredScripts, key = { it.id }) { script ->
                ScriptListItem(
                    script = script,
                    onScriptSelected = onScriptSelected
                )
            }
        }
    }
}

@Composable
private fun ScriptListItem(
    script: ScriptEntity,
    onScriptSelected: (ScriptEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onScriptSelected(script) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = script.description.ifBlank { "No description" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedVisibility(visible = !script.isEnabled) {
                    Text(
                        text = "Disabled",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(script.language.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(script.category.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
                script.tags.take(3).forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag) })
                }
                if (script.tags.size > 3) {
                    AssistChip(onClick = {}, label = { Text("+${script.tags.size - 3}") })
                }
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Updated: ${formatTimestamp(script.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (script.isEnabled) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (script.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
    return formatter.format(localDateTime)
}
