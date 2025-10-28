package com.ai.assistance.operit.ui.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.recorder.PrivacyFilterConfig

/**
 * Recorder control UI for floating window
 */
@Composable
fun RecorderControls(
    isRecording: Boolean,
    stepCount: Int,
    onStartStop: () -> Unit,
    onAnnotate: () -> Unit,
    onPrivacySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recording status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recording indicator
                    AnimatedVisibility(visible = isRecording) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                    }
                    
                    Text(
                        text = if (isRecording) "Recording..." else "Ready to Record",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                
                if (isRecording) {
                    Text(
                        text = "$stepCount steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start/Stop button
                Button(
                    onClick = onStartStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = if (isRecording) "Stop" else "Start"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRecording) "Stop" else "Start")
                }
                
                // Annotate button (only when recording)
                AnimatedVisibility(visible = isRecording) {
                    IconButton(
                        onClick = onAnnotate,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Add annotation",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                // Privacy settings button
                IconButton(
                    onClick = onPrivacySettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy settings",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Annotation input dialog
 */
@Composable
fun AnnotationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Annotation") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Enter annotation...") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Privacy filter configuration dialog
 */
@Composable
fun PrivacyFilterDialog(
    currentConfig: PrivacyFilterConfig,
    onDismiss: () -> Unit,
    onConfirm: (PrivacyFilterConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(currentConfig.enabled) }
    var filterPasswords by remember { mutableStateOf(currentConfig.filterPasswords) }
    var filterSensitiveInputs by remember { mutableStateOf(currentConfig.filterSensitiveInputs) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Filter Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Privacy Filter")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
                
                if (enabled) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Filter Passwords", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Hide password and secret fields",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = filterPasswords,
                            onCheckedChange = { filterPasswords = it }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Filter Sensitive Inputs", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Apply custom filters to inputs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = filterSensitiveInputs,
                            onCheckedChange = { filterSensitiveInputs = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        currentConfig.copy(
                            enabled = enabled,
                            filterPasswords = filterPasswords,
                            filterSensitiveInputs = filterSensitiveInputs
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
