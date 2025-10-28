package com.ai.assistance.operit.ui.features.scriptversion

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.model.ScriptVersionRecord
import com.ai.assistance.operit.data.repository.DiffLine
import com.ai.assistance.operit.data.repository.DiffType
import com.ai.assistance.operit.data.repository.ScriptVersionRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerDialog(
    version: ScriptVersionRecord,
    currentVersion: ScriptVersionRecord?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ScriptVersionRepository(context) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    val versionContent = remember(version) {
        repository.getVersionContent(version) ?: ""
    }
    
    val currentVersionContent = remember(currentVersion) {
        currentVersion?.let { repository.getVersionContent(it) }
    }
    
    val diffLines = remember(versionContent, currentVersionContent) {
        if (currentVersionContent != null && currentVersion?.versionId != version.versionId) {
            calculateDiff(versionContent, currentVersionContent)
        } else {
            versionContent.split("\n").mapIndexed { index, line ->
                DiffLine(index + 1, line, DiffType.UNCHANGED)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (currentVersion != null && currentVersion.versionId != version.versionId) {
                                    "Diff: Version ${version.versionNumber} vs ${currentVersion.versionNumber}"
                                } else {
                                    "Version ${version.versionNumber} Content"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                dateFormat.format(Date(version.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                if (currentVersion != null && currentVersion.versionId != version.versionId) {
                    DiffStatistics(diffLines)
                }

                Divider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(diffLines) { diffLine ->
                        DiffLineItem(diffLine)
                    }
                }
            }
        }
    }
}

@Composable
fun DiffStatistics(diffLines: List<DiffLine>) {
    val addedCount = diffLines.count { it.type == DiffType.ADDED }
    val removedCount = diffLines.count { it.type == DiffType.REMOVED }
    val unchangedCount = diffLines.count { it.type == DiffType.UNCHANGED }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                label = "Added",
                count = addedCount,
                color = MaterialTheme.colorScheme.tertiary
            )
            StatItem(
                label = "Removed",
                count = removedCount,
                color = MaterialTheme.colorScheme.error
            )
            StatItem(
                label = "Unchanged",
                count = unchangedCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.size(12.dp)
        ) {}
        Text(
            "$label: $count",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DiffLineItem(diffLine: DiffLine) {
    val backgroundColor = when (diffLine.type) {
        DiffType.ADDED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        DiffType.REMOVED -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        DiffType.MODIFIED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        DiffType.UNCHANGED -> Color.Transparent
    }

    val prefixIcon = when (diffLine.type) {
        DiffType.ADDED -> "+"
        DiffType.REMOVED -> "-"
        DiffType.MODIFIED -> "~"
        DiffType.UNCHANGED -> " "
    }

    val textColor = when (diffLine.type) {
        DiffType.ADDED -> MaterialTheme.colorScheme.tertiary
        DiffType.REMOVED -> MaterialTheme.colorScheme.error
        DiffType.MODIFIED -> MaterialTheme.colorScheme.primary
        DiffType.UNCHANGED -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = prefixIcon,
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = textColor,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = String.format("%4d", diffLine.lineNumber),
            modifier = Modifier.width(50.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "│",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = diffLine.content.ifBlank { " " },
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = textColor
        )
    }
}

private fun calculateDiff(oldContent: String, newContent: String): List<DiffLine> {
    val oldLines = oldContent.split("\n")
    val newLines = newContent.split("\n")
    
    val result = mutableListOf<DiffLine>()
    val maxLines = maxOf(oldLines.size, newLines.size)
    
    var oldIndex = 0
    var newIndex = 0
    
    while (oldIndex < oldLines.size || newIndex < newLines.size) {
        val oldLine = oldLines.getOrNull(oldIndex)
        val newLine = newLines.getOrNull(newIndex)
        
        when {
            oldLine == newLine && oldLine != null -> {
                result.add(DiffLine(newIndex + 1, newLine, DiffType.UNCHANGED))
                oldIndex++
                newIndex++
            }
            oldLine == null -> {
                result.add(DiffLine(newIndex + 1, newLine!!, DiffType.ADDED))
                newIndex++
            }
            newLine == null -> {
                result.add(DiffLine(oldIndex + 1, oldLine, DiffType.REMOVED))
                oldIndex++
            }
            else -> {
                val lookAhead = newLines.drop(newIndex).take(5).indexOfFirst { it == oldLine }
                if (lookAhead > 0) {
                    for (i in 0 until lookAhead) {
                        result.add(DiffLine(newIndex + 1 + i, newLines[newIndex + i], DiffType.ADDED))
                    }
                    newIndex += lookAhead
                } else {
                    result.add(DiffLine(oldIndex + 1, oldLine, DiffType.REMOVED))
                    result.add(DiffLine(newIndex + 1, newLine, DiffType.ADDED))
                    oldIndex++
                    newIndex++
                }
            }
        }
    }
    
    return result
}
