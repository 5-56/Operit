package com.ai.assistance.operit.ui.features.scripts.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.script.ScriptEntity
import com.ai.assistance.operit.ui.components.CustomScaffold

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScriptDetailScreen(
    script: ScriptEntity,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filteredCodeLines = remember(script.code, searchQuery) {
        script.code.lines().mapIndexed { index, line ->
            Pair(index + 1, line)
        }.filter { (lineNumber, line) ->
            searchQuery.isBlank() || line.contains(searchQuery, ignoreCase = true)
        }
    }

    CustomScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection(script = script, onEdit = onEdit, onRun = onRun, onDelete = onDelete)

            SearchSection(searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it })

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                ) {
                    LazyColumn(state = listState) {
                        itemsIndexed(filteredCodeLines, key = { _, pair -> pair.first }) { index, (lineNumber, line) ->
                            CodeLineItem(
                                lineNumber = lineNumber,
                                code = line,
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }

            MetadataSection(script = script)
        }
    }
}

@Composable
private fun HeaderSection(
    script: ScriptEntity,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = script.name, style = MaterialTheme.typography.headlineSmall)
            Text(text = script.description, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(text = "Run")
                }
                Button(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text(text = "Edit")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(text = "Delete")
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text(text = "Search code") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
    }
}

@Composable
private fun CodeLineItem(lineNumber: Int, code: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = lineNumber.toString().padStart(3, ' '),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MetadataSection(script: ScriptEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Details", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(label = "Language", value = script.language.name)
                InfoChip(label = "Category", value = script.category.name)
                InfoChip(label = "Enabled", value = if (script.isEnabled) "Yes" else "No")
            }
            Text(text = "Tags", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                script.tags.forEach { tag ->
                    InfoChip(label = "", value = tag)
                }
            }
            AnimatedVisibility(visible = script.tags.isEmpty()) {
                Text(text = "No tags assigned", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (label.isNotBlank()) {
                Text(
                    text = "$label:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
