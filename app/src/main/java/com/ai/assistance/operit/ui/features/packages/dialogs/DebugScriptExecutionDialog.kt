package com.ai.assistance.operit.ui.features.packages.dialogs

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.javascript.JsToolManager
import com.ai.assistance.operit.core.tools.javascript.debug.*
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScriptExecutionDialog(
    packageName: String,
    tool: PackageTool,
    packageManager: PackageManager,
    initialResult: ToolResult?,
    onExecuted: (ToolResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debugger = remember { ScriptDebugger.getInstance(context) }

    var scriptText by remember(tool) { mutableStateOf(tool.script) }
    var paramValues by remember(tool) { mutableStateOf(tool.parameters.associate { it.name to "" }) }
    var executing by remember { mutableStateOf(false) }
    var executionResults by remember { mutableStateOf<List<ToolResult>>(emptyList()) }
    var debugMode by remember { mutableStateOf(DebugMode.RUN) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val debugSession by debugger.currentSession.collectAsState()
    val debugState by debugger.debugState.collectAsState()
    
    val logs = remember { mutableStateOf<List<DebugLog>>(emptyList()) }
    val automationSteps = remember { mutableStateOf<List<AutomationStep>>(emptyList()) }
    
    LaunchedEffect(debugSession) {
        debugSession?.let {
            logs.value = it.logs
            automationSteps.value = it.automationSteps
        }
    }

    LaunchedEffect(initialResult) {
        if (initialResult != null) {
            executionResults = listOf(initialResult)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "脚本调试器",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (debugState != DebugState.IDLE) {
                        Badge(
                            containerColor = when (debugState) {
                                DebugState.RUNNING -> MaterialTheme.colorScheme.tertiary
                                DebugState.PAUSED -> MaterialTheme.colorScheme.secondary
                                DebugState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(debugState.name)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = debugMode == DebugMode.RUN,
                        onClick = { debugMode = DebugMode.RUN },
                        label = { Text("运行") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = debugMode == DebugMode.DEBUG,
                        onClick = { debugMode = DebugMode.DEBUG },
                        label = { Text("调试") },
                        leadingIcon = { Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("脚本") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("日志 (${logs.value.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("步骤 (${automationSteps.value.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                TextField(
                                    value = scriptText,
                                    onValueChange = { newValue -> scriptText = newValue },
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    placeholder = { 
                                        Text("编写JavaScript代码...") 
                                    }
                                )
                            }

                            if (tool.parameters.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "参数配置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                tool.parameters.forEach { param ->
                                    OutlinedTextField(
                                        value = paramValues[param.name] ?: "",
                                        onValueChange = { value ->
                                            paramValues = paramValues.toMutableMap().apply {
                                                put(param.name, value)
                                            }
                                        },
                                        label = {
                                            Text("${param.name}${if (param.required) " *" else ""}")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text(param.description) }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            if (executionResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "执行结果",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                executionResults.forEach { result ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (result.success) 
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else 
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (result.success) result.result.toString() else "错误: ${result.error}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        LogsPanel(logs = logs.value, modifier = Modifier.weight(1f))
                    }
                    2 -> {
                        AutomationStepsPanel(steps = automationSteps.value, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (debugState == DebugState.PAUSED) {
                        IconButton(onClick = { debugger.resume() }) {
                            Icon(Icons.Default.PlayArrow, "继续")
                        }
                        IconButton(onClick = { debugger.stepOver() }) {
                            Icon(Icons.Default.SkipNext, "步过")
                        }
                        IconButton(onClick = { debugger.stop() }) {
                            Icon(Icons.Default.Stop, "停止")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    OutlinedButton(onClick = onDismiss) {
                        Text("关闭")
                    }

                    FilledTonalButton(
                        onClick = {
                            executing = true
                            executionResults = emptyList()
                            logs.value = emptyList()
                            automationSteps.value = emptyList()
                            
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val missingParams = tool.parameters
                                        .filter { it.required }
                                        .map { it.name }
                                        .filter { paramValues[it].isNullOrEmpty() }

                                    if (missingParams.isNotEmpty()) {
                                        val missingResult = ToolResult(
                                            toolName = "${packageName}:${tool.name}",
                                            success = false,
                                            result = StringResultData(""),
                                            error = "缺少参数: ${missingParams.joinToString(", ")}"
                                        )
                                        withContext(Dispatchers.Main) {
                                            executionResults = listOf(missingResult)
                                            onExecuted(missingResult)
                                        }
                                    } else {
                                        debugger.startSession("${packageName}:${tool.name}", debugMode)
                                        
                                        val parameters = paramValues.map { (name, value) ->
                                            ToolParameter(name = name, value = value)
                                        }

                                        val aiTool = AITool(
                                            name = "${packageName}:${tool.name}",
                                            parameters = parameters
                                        )

                                        val interpreter = JsToolManager.getInstance(context, packageManager)

                                        interpreter
                                            .executeScript(scriptText, aiTool)
                                            .catch { e ->
                                                Log.e("DebugScriptDialog", "Flow collection error", e)
                                                debugger.log(LogLevel.ERROR, "Execution error: ${e.message}", "Script")
                                                val errorResult = ToolResult(
                                                    toolName = "${packageName}:${tool.name}",
                                                    success = false,
                                                    result = StringResultData(""),
                                                    error = "执行流错误: ${e.message}"
                                                )
                                                withContext(Dispatchers.Main) {
                                                    executionResults = executionResults + errorResult
                                                    onExecuted(errorResult)
                                                    logs.value = debugger.getLogs()
                                                    automationSteps.value = debugger.getAutomationSteps()
                                                }
                                            }
                                            .onCompletion {
                                                withContext(Dispatchers.Main) {
                                                    executing = false
                                                    debugger.endSession()
                                                }
                                            }
                                            .collect { result ->
                                                withContext(Dispatchers.Main) {
                                                    executionResults = executionResults + result
                                                    onExecuted(result)
                                                    logs.value = debugger.getLogs()
                                                    automationSteps.value = debugger.getAutomationSteps()
                                                }
                                            }
                                    }
                                } catch (e: Exception) {
                                    Log.e("DebugScriptDialog", "Failed to execute script", e)
                                    debugger.log(LogLevel.ERROR, "Fatal error: ${e.message}", "Script")
                                    withContext(Dispatchers.Main) {
                                        val finalError = ToolResult(
                                            toolName = "${packageName}:${tool.name}",
                                            success = false,
                                            result = StringResultData(""),
                                            error = "执行错误: ${e.message}"
                                        )
                                        executionResults = executionResults + finalError
                                        onExecuted(finalError)
                                        logs.value = debugger.getLogs()
                                        automationSteps.value = debugger.getAutomationSteps()
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) { 
                                        executing = false
                                        debugger.endSession()
                                    }
                                }
                            }
                        },
                        enabled = !executing
                    ) {
                        if (executing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("执行中")
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("执行")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsPanel(logs: List<DebugLog>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { log ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = when (log.level) {
                            LogLevel.ERROR -> Icons.Default.Error
                            LogLevel.WARN -> Icons.Default.Warning
                            LogLevel.INFO -> Icons.Default.Info
                            LogLevel.DEBUG -> Icons.Default.Code
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (log.level) {
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                            LogLevel.INFO -> MaterialTheme.colorScheme.primary
                            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        log.context?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutomationStepsPanel(steps: List<AutomationStep>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    
    LaunchedEffect(steps.size) {
        if (steps.isNotEmpty()) {
            listState.animateScrollToItem(steps.size - 1)
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(steps) { step ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (step.success)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "步骤 ${step.stepIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(step.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (step.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (step.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = step.operation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (step.error != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "错误: ${step.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
