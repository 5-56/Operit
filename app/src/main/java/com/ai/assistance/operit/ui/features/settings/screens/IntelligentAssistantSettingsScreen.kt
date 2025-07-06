package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.ai.IntelligentAssistantManager
import com.ai.assistance.operit.core.assistant.HybridAIEngine
import com.ai.assistance.operit.core.assistant.SystemResourceManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligentAssistantSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val assistantManager = remember { IntelligentAssistantManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    // 设置状态
    var isServiceRunning by remember { mutableStateOf(false) }
    var serviceStatus by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isEnabled by remember { mutableStateOf(true) }
    var wakeWord by remember { mutableStateOf("小助手") }
    var speechRate by remember { mutableStateOf(1.0f) }
    var speechPitch by remember { mutableStateOf(1.0f) }
    var showTestDialog by remember { mutableStateOf(false) }
    
    // 获取初始状态
    LaunchedEffect(Unit) {
        isServiceRunning = assistantManager.isServiceRunning()
        serviceStatus = assistantManager.getServiceStatus()
    }
    
    // 定期更新状态
    LaunchedEffect(isServiceRunning) {
        while (true) {
            isServiceRunning = assistantManager.isServiceRunning()
            serviceStatus = assistantManager.getServiceStatus()
            kotlinx.coroutines.delay(5000) // 每5秒更新一次
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            
            Text(
                text = "智能助手设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 服务状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "智能助手服务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                if (enabled) {
                                    assistantManager.startIntelligentAssistant()
                                } else {
                                    assistantManager.stopIntelligentAssistant()
                                }
                                isServiceRunning = assistantManager.isServiceRunning()
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isServiceRunning) "服务运行中，可以语音唤醒" else "服务已停止",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isServiceRunning) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isServiceRunning && serviceStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    serviceStatus?.let { status ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "监听状态:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (status["isListening"] == true) "正在监听" else "待机中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "处理状态:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (status["isProcessing"] == true) "处理中" else "空闲",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "AI引擎:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (status["localAILoaded"] == true) "已加载" else "未加载",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 唤醒词设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "唤醒词设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    label = { Text("唤醒词") },
                    placeholder = { Text("小助手") },
                    leadingIcon = {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "说出唤醒词来启动对话，支持中文",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 语音参数设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "语音参数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 语速设置
                Text(
                    text = "语速: ${String.format("%.1f", speechRate)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.5f..2.0f,
                    steps = 30,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 音调设置
                Text(
                    text = "音调: ${String.format("%.1f", speechPitch)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Slider(
                    value = speechPitch,
                    onValueChange = { speechPitch = it },
                    valueRange = 0.5f..2.0f,
                    steps = 30,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 测试和控制按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "测试与控制",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 测试语音
                    FilledTonalButton(
                        onClick = { showTestDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceRunning
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("测试语音")
                    }
                    
                    // 触发对话
                    FilledTonalButton(
                        onClick = {
                            assistantManager.triggerConversation("你好")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceRunning
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("测试对话")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 重启服务
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                assistantManager.restartIntelligentAssistant()
                                kotlinx.coroutines.delay(2000)
                                isServiceRunning = assistantManager.isServiceRunning()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重启服务")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "使用说明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = """
                        • 启用服务后，智能助手将在后台持续运行
                        • 说出唤醒词来开始对话，如"小助手"
                        • 支持语音控制应用、搜索、系统设置等
                        • 可以完全本地化运行，无需联网
                        • 支持工具调用和自动化操作
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // 测试对话框
    if (showTestDialog) {
        var testText by remember { mutableStateOf("你好，我是小助手") }
        
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text("测试语音合成") },
            text = {
                Column {
                    Text("输入要测试的文本：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        assistantManager.triggerConversation(testText)
                        showTestDialog = false
                    }
                ) {
                    Text("播放")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}