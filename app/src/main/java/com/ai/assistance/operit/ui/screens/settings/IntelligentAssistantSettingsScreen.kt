package com.ai.assistance.operit.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.assistant.IntelligentAssistantService
import com.ai.assistance.operit.core.ai.hybrid.HybridAIEngine
import com.ai.assistance.operit.core.ai.remote.RemoteAIService
import com.ai.assistance.operit.core.system.SystemResourceManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent

/**
 * 智能助手高级设置界面
 * 提供混合AI引擎、模型训练、系统优化等全面控制功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligentAssistantSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager(context) }
    
    // 状态管理
    var serviceMetrics by remember { mutableStateOf(IntelligentAssistantService.PerformanceMetrics()) }
    var engineStatus by remember { mutableStateOf(mapOf<String, Any>()) }
    var systemStats by remember { mutableStateOf(SystemResourceManager.SystemStats()) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 设置状态
    var isServiceEnabled by remember { mutableStateOf(preferencesManager.isIntelligentAssistantEnabled()) }
    var isLearningEnabled by remember { mutableStateOf(preferencesManager.isLearningEnabled()) }
    var isAggressiveOptimization by remember { mutableStateOf(preferencesManager.isAggressiveOptimizationEnabled()) }
    var selectedModel by remember { mutableStateOf(RemoteAIService.AIModel.AUTO) }
    var trainingPriority by remember { mutableStateOf(HybridAIEngine.TrainingPriority.NORMAL) }
    
    // API密钥状态
    var deepseekApiKey by remember { mutableStateOf(preferencesManager.getApiKey("deepseek") ?: "") }
    var openaiApiKey by remember { mutableStateOf(preferencesManager.getApiKey("openai") ?: "") }
    var geminiApiKey by remember { mutableStateOf(preferencesManager.getApiKey("gemini") ?: "") }
    
    // 对话框状态
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var currentApiProvider by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        // 定期更新状态
        while (true) {
            try {
                // 这里应该从服务获取实际状态
                // serviceMetrics = getServiceMetrics()
                // engineStatus = getEngineStatus()
                // systemStats = getSystemStats()
            } catch (e: Exception) {
                // 处理错误
            }
            kotlinx.coroutines.delay(5000) // 每5秒更新一次
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能助手设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                // 刷新状态
                                isLoading = true
                                try {
                                    // 获取最新状态
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务状态概览
            item {
                ServiceStatusCard(
                    serviceMetrics = serviceMetrics,
                    engineStatus = engineStatus,
                    systemStats = systemStats,
                    isLoading = isLoading
                )
            }
            
            // 基础设置
            item {
                BasicSettingsCard(
                    isServiceEnabled = isServiceEnabled,
                    onServiceEnabledChange = { enabled ->
                        isServiceEnabled = enabled
                        preferencesManager.setIntelligentAssistantEnabled(enabled)
                        if (enabled) {
                            startIntelligentAssistantService(context)
                        } else {
                            stopIntelligentAssistantService(context)
                        }
                    },
                    isLearningEnabled = isLearningEnabled,
                    onLearningEnabledChange = { enabled ->
                        isLearningEnabled = enabled
                        preferencesManager.setLearningEnabled(enabled)
                    }
                )
            }
            
            // AI引擎设置
            item {
                AIEngineSettingsCard(
                    selectedModel = selectedModel,
                    onModelChange = { model ->
                        selectedModel = model
                        preferencesManager.setPreferredAIModel(model.name)
                    },
                    onForceOnlineMode = {
                        sendServiceCommand(context, IntelligentAssistantService.ACTION_FORCE_ONLINE)
                    }
                )
            }
            
            // API密钥设置
            item {
                ApiKeysCard(
                    deepseekApiKey = deepseekApiKey,
                    openaiApiKey = openaiApiKey,
                    geminiApiKey = geminiApiKey,
                    onEditApiKey = { provider ->
                        currentApiProvider = provider
                        showApiKeyDialog = true
                    }
                )
            }
            
            // 模型训练设置
            item {
                ModelTrainingCard(
                    trainingPriority = trainingPriority,
                    onPriorityChange = { priority ->
                        trainingPriority = priority
                        preferencesManager.setTrainingPriority(priority.name)
                    },
                    onStartTraining = {
                        sendServiceCommand(context, IntelligentAssistantService.ACTION_START_TRAINING)
                    },
                    trainingProgress = serviceMetrics.trainingProgress
                )
            }
            
            // 系统优化设置
            item {
                SystemOptimizationCard(
                    isAggressiveOptimization = isAggressiveOptimization,
                    onAggressiveOptimizationChange = { enabled ->
                        isAggressiveOptimization = enabled
                        preferencesManager.setAggressiveOptimizationEnabled(enabled)
                    },
                    onOptimizeSystem = {
                        sendServiceCommand(context, IntelligentAssistantService.ACTION_OPTIMIZE_SYSTEM)
                    },
                    systemStats = systemStats
                )
            }
            
            // 高级设置
            item {
                AdvancedSettingsCard(
                    onExportTrainingData = {
                        // 导出训练数据
                    },
                    onImportTrainingData = {
                        // 导入训练数据
                    },
                    onResetModel = {
                        // 重置模型
                    }
                )
            }
        }
    }
    
    // API密钥编辑对话框
    if (showApiKeyDialog) {
        ApiKeyEditDialog(
            provider = currentApiProvider,
            currentKey = when (currentApiProvider) {
                "deepseek" -> deepseekApiKey
                "openai" -> openaiApiKey
                "gemini" -> geminiApiKey
                else -> ""
            },
            onDismiss = { showApiKeyDialog = false },
            onSave = { newKey ->
                when (currentApiProvider) {
                    "deepseek" -> {
                        deepseekApiKey = newKey
                        preferencesManager.setApiKey("deepseek", newKey)
                    }
                    "openai" -> {
                        openaiApiKey = newKey
                        preferencesManager.setApiKey("openai", newKey)
                    }
                    "gemini" -> {
                        geminiApiKey = newKey
                        preferencesManager.setApiKey("gemini", newKey)
                    }
                }
                showApiKeyDialog = false
            }
        )
    }
}

@Composable
private fun ServiceStatusCard(
    serviceMetrics: IntelligentAssistantService.PerformanceMetrics,
    engineStatus: Map<String, Any>,
    systemStats: SystemResourceManager.SystemStats,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "服务状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
            
            StatusRow(
                label = "引擎模式",
                value = serviceMetrics.engineState.name,
                icon = Icons.Default.Psychology
            )
            
            StatusRow(
                label = "总交互次数",
                value = serviceMetrics.totalInteractions.toString(),
                icon = Icons.Default.Chat
            )
            
            StatusRow(
                label = "成功率",
                value = if (serviceMetrics.totalInteractions > 0) {
                    "${(serviceMetrics.successfulInteractions * 100 / serviceMetrics.totalInteractions)}%"
                } else {
                    "0%"
                },
                icon = Icons.Default.CheckCircle
            )
            
            StatusRow(
                label = "平均响应时间",
                value = "${serviceMetrics.averageResponseTime.toInt()}ms",
                icon = Icons.Default.Speed
            )
            
            StatusRow(
                label = "内存使用",
                value = "${(systemStats.memoryUsagePercent * 100).toInt()}%",
                icon = Icons.Default.Memory
            )
            
            StatusRow(
                label = "存储使用",
                value = "${(systemStats.storageUsagePercent * 100).toInt()}%",
                icon = Icons.Default.Storage
            )
        }
    }
}

@Composable
private fun BasicSettingsCard(
    isServiceEnabled: Boolean,
    onServiceEnabledChange: (Boolean) -> Unit,
    isLearningEnabled: Boolean,
    onLearningEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "基础设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            SwitchRow(
                label = "启用智能助手服务",
                description = "后台常驻，支持语音唤醒和自动任务处理",
                checked = isServiceEnabled,
                onCheckedChange = onServiceEnabledChange
            )
            
            SwitchRow(
                label = "启用学习模式",
                description = "收集对话数据用于本地模型训练",
                checked = isLearningEnabled,
                onCheckedChange = onLearningEnabledChange
            )
        }
    }
}

@Composable
private fun AIEngineSettingsCard(
    selectedModel: RemoteAIService.AIModel,
    onModelChange: (RemoteAIService.AIModel) -> Unit,
    onForceOnlineMode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AI引擎设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "首选模型",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteAIService.AIModel.values().forEach { model ->
                    FilterChip(
                        selected = selectedModel == model,
                        onClick = { onModelChange(model) },
                        label = { 
                            Text(
                                text = when (model) {
                                    RemoteAIService.AIModel.AUTO -> "自动"
                                    RemoteAIService.AIModel.DEEPSEEK -> "DeepSeek"
                                    RemoteAIService.AIModel.GPT_3_5 -> "GPT-3.5"
                                    RemoteAIService.AIModel.GEMINI_PRO -> "Gemini"
                                }
                            )
                        }
                    )
                }
            }
            
            OutlinedButton(
                onClick = onForceOnlineMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudQueue, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("强制在线模式")
            }
        }
    }
}

@Composable
private fun ApiKeysCard(
    deepseekApiKey: String,
    openaiApiKey: String,
    geminiApiKey: String,
    onEditApiKey: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "API密钥设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            ApiKeyRow(
                provider = "DeepSeek",
                hasKey = deepseekApiKey.isNotBlank(),
                onClick = { onEditApiKey("deepseek") }
            )
            
            ApiKeyRow(
                provider = "OpenAI",
                hasKey = openaiApiKey.isNotBlank(),
                onClick = { onEditApiKey("openai") }
            )
            
            ApiKeyRow(
                provider = "Gemini",
                hasKey = geminiApiKey.isNotBlank(),
                onClick = { onEditApiKey("gemini") }
            )
        }
    }
}

@Composable
private fun ModelTrainingCard(
    trainingPriority: HybridAIEngine.TrainingPriority,
    onPriorityChange: (HybridAIEngine.TrainingPriority) -> Unit,
    onStartTraining: () -> Unit,
    trainingProgress: HybridAIEngine.TrainingProgress
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "模型训练",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (trainingProgress.isTraining) {
                TrainingProgressSection(trainingProgress)
            } else {
                Text(
                    text = "训练优先级",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HybridAIEngine.TrainingPriority.values().forEach { priority ->
                        FilterChip(
                            selected = trainingPriority == priority,
                            onClick = { onPriorityChange(priority) },
                            label = { 
                                Text(
                                    text = when (priority) {
                                        HybridAIEngine.TrainingPriority.LOW -> "低"
                                        HybridAIEngine.TrainingPriority.NORMAL -> "中"
                                        HybridAIEngine.TrainingPriority.HIGH -> "高"
                                    }
                                )
                            }
                        )
                    }
                }
                
                Button(
                    onClick = onStartTraining,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ModelTraining, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始训练")
                }
            }
        }
    }
}

@Composable
private fun SystemOptimizationCard(
    isAggressiveOptimization: Boolean,
    onAggressiveOptimizationChange: (Boolean) -> Unit,
    onOptimizeSystem: () -> Unit,
    systemStats: SystemResourceManager.SystemStats
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "系统优化",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            SwitchRow(
                label = "激进优化模式",
                description = "在训练时最大化性能，可能影响其他应用",
                checked = isAggressiveOptimization,
                onCheckedChange = onAggressiveOptimizationChange
            )
            
            SystemResourceIndicator(
                label = "内存",
                usage = systemStats.memoryUsagePercent,
                total = "${systemStats.totalMemory / 1024 / 1024 / 1024}GB"
            )
            
            SystemResourceIndicator(
                label = "存储",
                usage = systemStats.storageUsagePercent,
                total = "${systemStats.totalStorage / 1024 / 1024 / 1024}GB"
            )
            
            Button(
                onClick = onOptimizeSystem,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("立即优化")
            }
        }
    }
}

@Composable
private fun AdvancedSettingsCard(
    onExportTrainingData: () -> Unit,
    onImportTrainingData: () -> Unit,
    onResetModel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "高级设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedButton(
                onClick = onExportTrainingData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("导出训练数据")
            }
            
            OutlinedButton(
                onClick = onImportTrainingData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("导入训练数据")
            }
            
            OutlinedButton(
                onClick = onResetModel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重置本地模型")
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ApiKeyRow(
    provider: String,
    hasKey: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = provider,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasKey) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已配置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            TextButton(onClick = onClick) {
                Text(if (hasKey) "编辑" else "设置")
            }
        }
    }
}

@Composable
private fun TrainingProgressSection(
    progress: HybridAIEngine.TrainingProgress
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "训练进行中...",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        LinearProgressIndicator(
            progress = progress.batchProgress,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "轮次: ${progress.currentEpoch}/${progress.totalEpochs}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "准确率: ${(progress.modelAccuracy * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SystemResourceIndicator(
    label: String,
    usage: Float,
    total: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${(usage * 100).toInt()}% / $total",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LinearProgressIndicator(
            progress = usage,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ApiKeyEditDialog(
    provider: String,
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置 $provider API密钥") },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API密钥") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}

// 工具函数
private fun startIntelligentAssistantService(context: Context) {
    val intent = Intent(context, IntelligentAssistantService::class.java)
    context.startForegroundService(intent)
}

private fun stopIntelligentAssistantService(context: Context) {
    val intent = Intent(context, IntelligentAssistantService::class.java)
    context.stopService(intent)
}

private fun sendServiceCommand(context: Context, action: String) {
    val intent = Intent(context, IntelligentAssistantService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
}

// 扩展函数
private fun UserPreferencesManager.isIntelligentAssistantEnabled(): Boolean {
    return getBooleanPreference("intelligent_assistant_enabled", false)
}

private fun UserPreferencesManager.setIntelligentAssistantEnabled(enabled: Boolean) {
    setBooleanPreference("intelligent_assistant_enabled", enabled)
}

private fun UserPreferencesManager.isLearningEnabled(): Boolean {
    return getBooleanPreference("learning_enabled", true)
}

private fun UserPreferencesManager.setLearningEnabled(enabled: Boolean) {
    setBooleanPreference("learning_enabled", enabled)
}

private fun UserPreferencesManager.isAggressiveOptimizationEnabled(): Boolean {
    return getBooleanPreference("aggressive_optimization_enabled", false)
}

private fun UserPreferencesManager.setAggressiveOptimizationEnabled(enabled: Boolean) {
    setBooleanPreference("aggressive_optimization_enabled", enabled)
}

private fun UserPreferencesManager.setPreferredAIModel(model: String) {
    setStringPreference("preferred_ai_model", model)
}

private fun UserPreferencesManager.setTrainingPriority(priority: String) {
    setStringPreference("training_priority", priority)
}