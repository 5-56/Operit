package com.ai.assistance.operit.ui.features.chat.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.agent.AgentConfig
import com.ai.assistance.operit.core.agent.OptimizationStrategy
import com.ai.assistance.operit.core.agent.LLMServiceFactory
import com.ai.assistance.operit.util.SecurityHelper

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
    var showTestConnectionDialog by remember { mutableStateOf(false) }
    var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    
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

                // 验证按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // 实现配置验证逻辑
                            val validationResult = validateConfiguration(config)
                            validationErrors = if (validationResult.isValid) {
                                emptyList()
                            } else {
                                validationResult.errors
                            }
                            
                            if (validationResult.isValid) {
                                // 可选：测试连接
                                showTestConnectionDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("验证配置")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            // 重置为默认配置
                            config = AgentConfig()
                            validationErrors = emptyList()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置")
                    }
                }
                
                // 显示验证错误信息
                if (validationErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "配置验证失败",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            validationErrors.forEach { error ->
                                Text(
                                    text = "• $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
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

    // 验证配置的数据类
    private data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )
    
    // 配置验证函数
    private fun validateConfiguration(config: AgentConfig): ValidationResult {
        val errors = mutableListOf<String>()
        
        // 验证LLM提供商和API密钥
        if (config.llmProvider.isBlank()) {
            errors.add("请选择LLM提供商")
        }
        
        if (config.llmApiKey.isBlank()) {
            errors.add("请输入API密钥")
        } else {
            // 使用安全助手验证API密钥格式
            val apiKeyValidation = SecurityHelper.ApiKeyManager.validateApiKey(
                config.llmProvider, 
                config.llmApiKey
            )
            if (!apiKeyValidation.isValid) {
                errors.add("API密钥格式错误: ${apiKeyValidation.message}")
            }
        }
        
        // 验证端点URL
        if (config.llmEndpoint.isNotBlank()) {
            val urlValidation = SecurityHelper.InputValidator.validateUrl(config.llmEndpoint)
            if (!urlValidation.isValid) {
                errors.add("端点URL格式错误: ${urlValidation.message}")
            }
        }
        
        // 验证模型名称
        if (config.llmModel.isNotBlank()) {
            val modelValidation = SecurityHelper.InputValidator.validateModelName(config.llmModel)
            if (!modelValidation.isValid) {
                errors.add("模型名称格式错误: ${modelValidation.message}")
            }
        }
        
        // 验证数值范围
        if (config.maxIterations < 1 || config.maxIterations > 10) {
            errors.add("最大迭代次数应在1-10之间")
        }
        
        if (config.maxTokens < 100 || config.maxTokens > 32000) {
            errors.add("最大Token数应在100-32000之间")
        }
        
        if (config.temperature < 0.0f || config.temperature > 2.0f) {
            errors.add("温度值应在0.0-2.0之间")
        }
        
        if (config.executionTimeout < 1000L || config.executionTimeout > 300000L) {
            errors.add("执行超时时间应在1-300秒之间")
        }
        
        if (config.maxRetryCount < 0 || config.maxRetryCount > 5) {
            errors.add("重试次数应在0-5之间")
        }
        
        if (config.successThreshold < 0.0f || config.successThreshold > 1.0f) {
            errors.add("成功阈值应在0.0-1.0之间")
        }
        
        if (config.contextWindowSize < 1000 || config.contextWindowSize > 128000) {
            errors.add("上下文窗口大小应在1000-128000之间")
        }
        
        if (config.memorySize < 10 || config.memorySize > 1000) {
            errors.add("记忆大小应在10-1000之间")
        }
        
        // 验证自定义提示词模板
        if (config.customPromptTemplate.isNotBlank()) {
            if (SecurityHelper.InputValidator.containsMaliciousScript(config.customPromptTemplate)) {
                errors.add("自定义提示词模板包含可疑内容")
            }
            if (config.customPromptTemplate.length > 5000) {
                errors.add("自定义提示词模板过长（超过5000字符）")
            }
        }
        
        // 验证自定义参数
        config.customParameters.forEach { (key, value) ->
            if (key.isBlank()) {
                errors.add("自定义参数的键不能为空")
            }
            if (SecurityHelper.InputValidator.containsMaliciousScript(key) ||
                SecurityHelper.InputValidator.containsMaliciousScript(value)) {
                errors.add("自定义参数包含可疑内容")
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }