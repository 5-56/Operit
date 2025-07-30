package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.agent.AgentConfig
import com.ai.assistance.operit.core.agent.OptimizationStrategy
import com.ai.assistance.operit.core.agent.LLMServiceFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigDialog(
    initialConfig: AgentConfig,
    onDismiss: () -> Unit,
    onConfirm: (AgentConfig) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showStrategyDropdown by remember { mutableStateOf(false) }
    
    val supportedProviders = remember { LLMServiceFactory.getSupportedProviders() }
    val currentProvider = supportedProviders.find { it.id == config.llmProvider }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agent 配置", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showAdvancedSettings = !showAdvancedSettings }) {
                    Icon(
                        if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "高级设置"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // === 基础配置 ===
                Text(
                    "基础配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // LLM提供商选择
                ExposedDropdownMenuBox(
                    expanded = showProviderDropdown,
                    onExpandedChange = { showProviderDropdown = it }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = currentProvider?.name ?: config.llmProvider,
                        onValueChange = {},
                        label = { Text("LLM 提供商") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProviderDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showProviderDropdown,
                        onDismissRequest = { showProviderDropdown = false }
                    ) {
                        supportedProviders.forEach { provider ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(provider.name)
                                        Text(
                                            provider.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    config = config.copy(
                                        llmProvider = provider.id,
                                        llmEndpoint = provider.defaultEndpoint,
                                        llmModel = provider.defaultModel
                                    )
                                    showProviderDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // API Key
                if (currentProvider?.requiresApiKey == true) {
                    OutlinedTextField(
                        value = config.llmApiKey,
                        onValueChange = { config = config.copy(llmApiKey = it) },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { 
                            Text("请输入 ${currentProvider.name} 的 API Key")
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 自定义端点
                OutlinedTextField(
                    value = config.llmEndpoint,
                    onValueChange = { config = config.copy(llmEndpoint = it) },
                    label = { Text("API 端点 (可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { 
                        Text("留空使用默认端点")
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 模型选择
                ExposedDropdownMenuBox(
                    expanded = showModelDropdown,
                    onExpandedChange = { showModelDropdown = it }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = config.llmModel.ifEmpty { currentProvider?.defaultModel ?: "" },
                        onValueChange = {},
                        label = { Text("模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false }
                    ) {
                        currentProvider?.supportedModels?.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    config = config.copy(llmModel = model)
                                    showModelDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 最大迭代次数
                OutlinedTextField(
                    value = config.maxIterations.toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { value ->
                            if (value > 0) config = config.copy(maxIterations = value)
                        }
                    },
                    label = { Text("最大迭代次数") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Agent自动优化的最大轮数") }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 优化策略
                ExposedDropdownMenuBox(
                    expanded = showStrategyDropdown,
                    onExpandedChange = { showStrategyDropdown = it }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = when (config.optimizationStrategy) {
                            OptimizationStrategy.FAST -> "快速模式"
                            OptimizationStrategy.BALANCED -> "平衡模式"
                            OptimizationStrategy.QUALITY -> "质量模式"
                            OptimizationStrategy.PERFORMANCE -> "性能模式"
                            OptimizationStrategy.SAFE -> "安全模式"
                        },
                        onValueChange = {},
                        label = { Text("优化策略") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showStrategyDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showStrategyDropdown,
                        onDismissRequest = { showStrategyDropdown = false }
                    ) {
                        val strategies = listOf(
                            OptimizationStrategy.FAST to "快速模式",
                            OptimizationStrategy.BALANCED to "平衡模式",
                            OptimizationStrategy.QUALITY to "质量模式",
                            OptimizationStrategy.PERFORMANCE to "性能模式",
                            OptimizationStrategy.SAFE to "安全模式"
                        )
                        strategies.forEach { (strategy, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    config = config.copy(optimizationStrategy = strategy)
                                    showStrategyDropdown = false
                                }
                            )
                        }
                    }
                }
                
                // === 高级设置 ===
                if (showAdvancedSettings) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "高级设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 脚本生成配置
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("脚本生成", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = config.maxTokens.toString(),
                                    onValueChange = { 
                                        it.toIntOrNull()?.let { value ->
                                            if (value > 0) config = config.copy(maxTokens = value)
                                        }
                                    },
                                    label = { Text("最大Token") },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                OutlinedTextField(
                                    value = config.temperature.toString(),
                                    onValueChange = { 
                                        it.toFloatOrNull()?.let { value ->
                                            if (value in 0.0f..2.0f) config = config.copy(temperature = value)
                                        }
                                    },
                                    label = { Text("温度") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.enableThinking,
                                    onCheckedChange = { config = config.copy(enableThinking = it) }
                                )
                                Text("启用思考模式 (适用于Qwen等模型)")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 执行配置
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("执行配置", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = (config.executionTimeout / 1000).toString(),
                                onValueChange = { 
                                    it.toLongOrNull()?.let { value ->
                                        if (value > 0) config = config.copy(executionTimeout = value * 1000)
                                    }
                                },
                                label = { Text("执行超时 (秒)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.enableAutoSave,
                                    onCheckedChange = { config = config.copy(enableAutoSave = it) }
                                )
                                Text("自动保存脚本")
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.enableAutoUpload,
                                    onCheckedChange = { config = config.copy(enableAutoUpload = it) }
                                )
                                Text("自动上传到Git")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 安全配置
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("安全配置", fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.enableSafetyChecks,
                                    onCheckedChange = { config = config.copy(enableSafetyChecks = it) }
                                )
                                Text("启用安全检查")
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.allowSystemCommands,
                                    onCheckedChange = { config = config.copy(allowSystemCommands = it) }
                                )
                                Text("允许系统命令")
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.allowNetworkAccess,
                                    onCheckedChange = { config = config.copy(allowNetworkAccess = it) }
                                )
                                Text("允许网络访问")
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.allowFileOperations,
                                    onCheckedChange = { config = config.copy(allowFileOperations = it) }
                                )
                                Text("允许文件操作")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 高级功能
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("高级功能", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.enableMemory,
                                    onCheckedChange = { config = config.copy(enableMemory = it) }
                                )
                                Text("启用记忆功能")
                            }
                            
                            if (config.enableMemory) {
                                OutlinedTextField(
                                    value = config.memorySize.toString(),
                                    onValueChange = { 
                                        it.toIntOrNull()?.let { value ->
                                            if (value >= 0) config = config.copy(memorySize = value)
                                        }
                                    },
                                    label = { Text("记忆条目数量") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = config.enableDebugMode,
                                    onCheckedChange = { config = config.copy(enableDebugMode = it) }
                                )
                                Text("调试模式")
                            }
                        }
                    }
                }
                
                // 快速配置预设
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "快速配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            config = AgentConfig.createQuickConfig(
                                provider = config.llmProvider,
                                apiKey = config.llmApiKey
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("快速", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            config = AgentConfig.createPerformanceConfig(
                                provider = config.llmProvider,
                                apiKey = config.llmApiKey
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("性能", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            config = AgentConfig.createSecureConfig(
                                provider = config.llmProvider,
                                apiKey = config.llmApiKey
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("安全", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            config = AgentConfig.createDebugConfig(
                                provider = config.llmProvider,
                                apiKey = config.llmApiKey
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("调试", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validation = config.validate()
                    if (validation.isSuccess) {
                        onConfirm(config)
                    } else {
                        // TODO: 显示验证错误信息
                    }
                }
            ) { 
                Text("确定") 
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { 
                Text("取消") 
            }
        }
    )
}