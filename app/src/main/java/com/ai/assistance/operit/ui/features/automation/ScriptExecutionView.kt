package com.ai.assistance.operit.ui.features.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.automatic.*
import kotlinx.coroutines.flow.Flow

@Composable
fun ScriptExecutionViewer(
    script: AutomationScript,
    executionStateFlow: Flow<ScriptExecutionState>,
    modifier: Modifier = Modifier
) {
    var currentState by remember { mutableStateOf<ScriptExecutionState?>(null) }
    
    LaunchedEffect(executionStateFlow) {
        executionStateFlow.collect { state ->
            currentState = state
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ScriptHeader(script)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (val state = currentState) {
            is ScriptExecutionState.Started -> {
                ExecutionStartedView(script)
            }
            is ScriptExecutionState.StepStarted,
            is ScriptExecutionState.StepCompleted,
            is ScriptExecutionState.StepFailed -> {
                StepListView(script, state)
            }
            is ScriptExecutionState.Completed -> {
                ExecutionCompletedView(state)
            }
            is ScriptExecutionState.Failed -> {
                ExecutionFailedView(state)
            }
            null -> {
                Text("Preparing execution...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ScriptHeader(script: AutomationScript) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = script.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            if (script.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = script.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${script.steps.size} steps • Package: ${script.packageName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ExecutionStartedView(script: AutomationScript) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "Starting script execution...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StepListView(script: AutomationScript, state: ScriptExecutionState) {
    val currentStepNumber = when (state) {
        is ScriptExecutionState.StepStarted -> state.stepNumber
        is ScriptExecutionState.StepCompleted -> state.stepNumber
        is ScriptExecutionState.StepFailed -> state.stepNumber
        else -> 0
    }
    
    val logs = when (state) {
        is ScriptExecutionState.StepStarted -> state.logs
        is ScriptExecutionState.StepCompleted -> state.logs
        is ScriptExecutionState.StepFailed -> state.logs
        else -> emptyList()
    }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        val progress = currentStepNumber.toFloat() / script.steps.size.toFloat()
        val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
        
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Step $currentStepNumber / ${script.steps.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val listState = rememberLazyListState()
        
        LaunchedEffect(currentStepNumber) {
            if (currentStepNumber > 0) {
                listState.animateScrollToItem((currentStepNumber - 1).coerceAtLeast(0))
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(script.steps) { step ->
                val stepLog = logs.find { it.stepNumber == step.stepNumber }
                val isActive = step.stepNumber == currentStepNumber
                StepCard(step, stepLog, isActive)
            }
        }
    }
}

@Composable
private fun StepCard(step: ScriptStep, log: ScriptExecutionLog?, isActive: Boolean) {
    val backgroundColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        log?.status == StepStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
        log?.status == StepStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.7f,
        label = "stepAlpha"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            StepStatusIcon(log?.status, isActive)
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = step.stepNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                if (log != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (log.error != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Error: ${log.error}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepStatusIcon(status: StepStatus?, isActive: Boolean) {
    val icon = when (status) {
        StepStatus.COMPLETED -> Icons.Default.CheckCircle
        StepStatus.FAILED -> Icons.Default.Error
        StepStatus.IN_PROGRESS -> Icons.Default.HourglassEmpty
        StepStatus.SKIPPED -> Icons.Default.SkipNext
        else -> if (isActive) Icons.Default.PlayArrow else Icons.Default.Circle
    }
    
    val tint = when (status) {
        StepStatus.COMPLETED -> Color(0xFF4CAF50)
        StepStatus.FAILED -> MaterialTheme.colorScheme.error
        StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        StepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun ExecutionCompletedView(state: ScriptExecutionState.Completed) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp)
                )
                
                Column {
                    Text(
                        text = "Script Completed Successfully",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.completedSteps} of ${state.totalSteps} steps completed in ${state.executionTimeMs / 1000}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        if (state.logs.isNotEmpty()) {
            ExecutionLogsCard(state.logs)
        }
    }
}

@Composable
private fun ExecutionFailedView(state: ScriptExecutionState.Failed) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                
                Column {
                    Text(
                        text = "Script Execution Failed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${state.currentStep} of ${state.totalSteps} steps completed before failure",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Error: ${state.error}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        if (state.logs.isNotEmpty()) {
            ExecutionLogsCard(state.logs)
        }
    }
}

@Composable
private fun ExecutionLogsCard(logs: List<ScriptExecutionLog>) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Execution Logs (${logs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        LogEntry(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntry(log: ScriptExecutionLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val statusColor = when (log.status) {
            StepStatus.COMPLETED -> Color(0xFF4CAF50)
            StepStatus.FAILED -> MaterialTheme.colorScheme.error
            StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusColor, CircleShape)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "[Step ${log.stepNumber}] ${log.stepDescription}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
