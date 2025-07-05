package com.ai.assistance.operit.auraflow.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.auraflow.core.AuraFlowAgentManager
import com.ai.assistance.operit.auraflow.protocol.ConnectionStatus
import kotlinx.coroutines.launch

/**
 * AI大脑服务配置界面
 * 实现AuraFlow Agent设计方案书中的AI服务配置功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBrainConfigScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AIBrainConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // 状态监听
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 顶部连接状态指示器
        ConnectionStatusCard(
            connectionStatus = connectionStatus,
            serverUrl = uiState.serverUrl,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // AI服务类型选择
        AIServiceTypeSection(
            selectedType = uiState.serviceType,
            onServiceTypeChanged = viewModel::updateServiceType,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 服务配置部分
        when (uiState.serviceType) {
            AIServiceType.CUSTOM -> {
                CustomAIServiceConfig(
                    serverUrl = uiState.serverUrl,
                    apiKey = uiState.apiKey,
                    onServerUrlChanged = viewModel::updateServerUrl,
                    onApiKeyChanged = viewModel::updateApiKey,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AIServiceType.THIRD_PARTY -> {
                ThirdPartyServiceConfig(
                    provider = uiState.provider,
                    apiKey = uiState.apiKey,
                    modelName = uiState.modelName,
                    onProviderChanged = viewModel::updateProvider,
                    onApiKeyChanged = viewModel::updateApiKey,
                    onModelNameChanged = viewModel::updateModelName,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 连接测试按钮
        ConnectionTestSection(
            isTestingConnection = uiState.isTestingConnection,
            testResult = uiState.testResult,
            onTestConnection = {
                scope.launch {
                    viewModel.testConnection(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Agent行为设置
        AgentBehaviorSection(
            agentConfig = uiState.agentConfig,
            onConfigChanged = viewModel::updateAgentConfig,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 操作按钮
        ActionButtonsSection(
            isConnected = connectionStatus == ConnectionStatus.CONNECTED,
            isConnecting = connectionStatus == ConnectionStatus.CONNECTING,
            onConnect = {
                scope.launch {
                    viewModel.connect(context)
                }
            },
            onDisconnect = {
                scope.launch {
                    viewModel.disconnect(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 连接状态卡片
 */
@Composable
private fun ConnectionStatusCard(
    connectionStatus: ConnectionStatus,
    serverUrl: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
                ConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示圆点
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> Color.Green
                            ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color.Yellow
                            ConnectionStatus.ERROR -> Color.Red
                            ConnectionStatus.DISCONNECTED -> Color.Gray
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "● 已连接"
                        ConnectionStatus.CONNECTING -> "● 连接中"
                        ConnectionStatus.RECONNECTING -> "● 重连中"
                        ConnectionStatus.ERROR -> "● 连接错误"
                        ConnectionStatus.DISCONNECTED -> "● 未连接"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (serverUrl.isNotBlank()) {
                    Text(
                        text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                            "AI大脑服务已连接至 $serverUrl"
                        } else {
                            "目标服务: $serverUrl"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "请配置AI大脑服务连接参数",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * AI服务类型选择部分
 */
@Composable
private fun AIServiceTypeSection(
    selectedType: AIServiceType,
    onServiceTypeChanged: (AIServiceType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "AI服务类型",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 自定义AI服务选项
        ServiceTypeCard(
            title = "自定义AI服务 (推荐)",
            description = "连接到您自己部署的AuraFlow服务器或其他兼容的私有AI服务。此模式提供最高灵活性和数据隐私。",
            selected = selectedType == AIServiceType.CUSTOM,
            onClick = { onServiceTypeChanged(AIServiceType.CUSTOM) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 第三方LLM服务选项
        ServiceTypeCard(
            title = "第三方LLM服务",
            description = "直接连接到主流的第三方大型语言模型服务。请注意数据隐私和API费用。",
            selected = selectedType == AIServiceType.THIRD_PARTY,
            onClick = { onServiceTypeChanged(AIServiceType.THIRD_PARTY) }
        )
    }
}

/**
 * 服务类型卡片
 */
@Composable
private fun ServiceTypeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 自定义AI服务配置
 */
@Composable
private fun CustomAIServiceConfig(
    serverUrl: String,
    apiKey: String,
    onServerUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "自定义AI服务配置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 服务URL输入
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChanged,
            label = { Text("AI服务URL *") },
            placeholder = { Text("请输入您的自定义AI服务API端点URL") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // API Key输入
        var showApiKey by rememberSaveable { mutableStateOf(false) }
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API密钥 (可选)") },
            placeholder = { Text("请输入您的API密钥（如果自定义服务需要）") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                    )
                }
            },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请妥善保管您的API密钥，确保安全性。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 第三方服务配置
 */
@Composable
private fun ThirdPartyServiceConfig(
    provider: String,
    apiKey: String,
    modelName: String,
    onProviderChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "第三方LLM服务配置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 服务提供商选择
        var expanded by remember { mutableStateOf(false) }
        val providers = listOf("OpenAI", "Anthropic", "Google Gemini", "其他")
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = provider,
                onValueChange = { },
                readOnly = true,
                label = { Text("服务提供商 *") },
                leadingIcon = { Icon(Icons.Default.CloudCircle, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                providers.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onProviderChanged(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // API Key输入
        var showApiKey by rememberSaveable { mutableStateOf(false) }
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API密钥 *") },
            placeholder = { Text("请输入您的第三方服务API密钥") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥"
                    )
                }
            },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 模型名称输入
        OutlinedTextField(
            value = modelName,
            onValueChange = onModelNameChanged,
            label = { Text("模型名称 *") },
            placeholder = { Text("例如: gpt-4o, claude-3-opus-20240229") },
            leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请注意数据隐私和API费用。确保选择正确的模型名称。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 连接测试部分
 */
@Composable
private fun ConnectionTestSection(
    isTestingConnection: Boolean,
    testResult: String?,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "连接测试",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = onTestConnection,
                enabled = !isTestingConnection
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试连接")
                }
            }
        }
        
        // 测试结果显示
        testResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.contains("成功")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (result.contains("成功")) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.contains("成功")) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Agent行为设置部分
 */
@Composable
private fun AgentBehaviorSection(
    agentConfig: AgentConfiguration,
    onConfigChanged: (AgentConfiguration) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Agent行为设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 屏幕数据传输模式
        ConfigurationItem(
            title = "屏幕数据传输模式",
            description = "选择Agent向AI大脑传输屏幕信息的方式"
        ) {
            val modes = listOf("智能模式 (推荐)", "实时模式", "按需模式")
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = modes[agentConfig.screenUpdateMode],
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    modes.forEachIndexed { index, mode ->
                        DropdownMenuItem(
                            text = { Text(mode) },
                            onClick = {
                                onConfigChanged(agentConfig.copy(screenUpdateMode = index))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 屏幕截图质量
        ConfigurationItem(
            title = "屏幕截图质量",
            description = "调整传输给AI大脑的屏幕截图质量"
        ) {
            val qualities = listOf("低 (快速响应)", "中 (平衡)", "高 (高精度)")
            
            Row(modifier = Modifier.fillMaxWidth()) {
                qualities.forEachIndexed { index, quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = agentConfig.screenshotQuality == index,
                                onClick = { onConfigChanged(agentConfig.copy(screenshotQuality = index)) }
                            )
                            .padding(4.dp)
                    ) {
                        RadioButton(
                            selected = agentConfig.screenshotQuality == index,
                            onClick = { onConfigChanged(agentConfig.copy(screenshotQuality = index)) }
                        )
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 操作执行速度
        ConfigurationItem(
            title = "操作执行速度",
            description = "调整Agent执行AI指令的速度"
        ) {
            val speeds = listOf("慢速", "正常", "快速")
            
            Row(modifier = Modifier.fillMaxWidth()) {
                speeds.forEachIndexed { index, speed ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = agentConfig.executionSpeed == index,
                                onClick = { onConfigChanged(agentConfig.copy(executionSpeed = index)) }
                            )
                            .padding(4.dp)
                    ) {
                        RadioButton(
                            selected = agentConfig.executionSpeed == index,
                            onClick = { onConfigChanged(agentConfig.copy(executionSpeed = index)) }
                        )
                        Text(
                            text = speed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 配置项组件
 */
@Composable
private fun ConfigurationItem(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        
        content()
    }
}

/**
 * 操作按钮部分
 */
@Composable
private fun ActionButtonsSection(
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isConnected) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("断开连接")
            }
        } else {
            Button(
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接中...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("连接AI大脑")
                }
            }
        }
    }
}