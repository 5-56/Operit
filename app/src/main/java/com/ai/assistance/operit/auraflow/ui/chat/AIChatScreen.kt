package com.ai.assistance.operit.auraflow.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.auraflow.protocol.ConnectionStatus
import com.ai.assistance.operit.auraflow.protocol.FeedbackType
import kotlinx.coroutines.launch

/**
 * AI对话页面 - AuraFlow Agent 的核心交互界面
 * 实现设计方案书中的主要AI交互功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    onOpenFloatingWindow: () -> Unit = {},
    viewModel: AIChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // 状态监听
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    
    // 自动滚动到底部
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部状态栏
        ChatStatusBar(
            connectionStatus = connectionStatus,
            agentState = agentState,
            currentTask = uiState.currentTaskName,
            floatingWindowEnabled = uiState.isFloatingWindowEnabled,
            onToggleFloatingWindow = {
                viewModel.toggleFloatingWindow()
                if (uiState.isFloatingWindowEnabled) {
                    onOpenFloatingWindow()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Divider()
        
        // 消息列表区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isEmpty()) {
                // 空状态
                EmptyStateContent(
                    connectionStatus = connectionStatus,
                    onStartTask = {
                        scope.launch {
                            viewModel.startTask(context)
                        }
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // 消息列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatMessages) { message ->
                        ChatMessageItem(
                            message = message,
                            onScreenshotClick = { screenshot ->
                                viewModel.showScreenshotDialog(screenshot)
                            },
                            onAnswerQuestion = { questionId, answer ->
                                scope.launch {
                                    viewModel.answerQuestion(context, questionId, answer)
                                }
                            }
                        )
                    }
                }
            }
            
            // 滚动到底部按钮
            if (chatMessages.isNotEmpty() && !listState.isScrolledToEnd()) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(chatMessages.size - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "滚动到底部"
                    )
                }
            }
        }
        
        // 底部输入区域（当AI需要用户输入时显示）
        if (uiState.isWaitingForInput) {
            ChatInputSection(
                inputText = uiState.inputText,
                onInputChanged = viewModel::updateInputText,
                onSendInput = {
                    scope.launch {
                        viewModel.sendUserInput(context)
                    }
                },
                onVoiceInput = {
                    // TODO: 实现语音输入
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // 截图查看对话框
    if (uiState.showScreenshotDialog) {
        ScreenshotDialog(
            screenshot = uiState.selectedScreenshot,
            onDismiss = viewModel::hideScreenshotDialog
        )
    }
}

/**
 * 聊天状态栏
 */
@Composable
private fun ChatStatusBar(
    connectionStatus: ConnectionStatus,
    agentState: String,
    currentTask: String?,
    floatingWindowEnabled: Boolean,
    onToggleFloatingWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 连接状态指示
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态圆点
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
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "已连接"
                            ConnectionStatus.CONNECTING -> "连接中"
                            ConnectionStatus.RECONNECTING -> "重连中"
                            ConnectionStatus.ERROR -> "连接错误"
                            ConnectionStatus.DISCONNECTED -> "未连接"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // 副标题显示任务状态
                    val subtitle = when {
                        currentTask != null -> currentTask
                        agentState == "EXECUTING" -> "任务运行中"
                        agentState == "PAUSED" -> "任务已暂停"
                        agentState == "CONNECTED" -> "待命中"
                        else -> agentState
                    }
                    
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 浮动窗口开关
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "浮动窗口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Switch(
                    checked = floatingWindowEnabled,
                    onCheckedChange = { onToggleFloatingWindow() },
                    thumbContent = {
                        Icon(
                            if (floatingWindowEnabled) Icons.Default.PictureInPicture else Icons.Default.PictureInPictureAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * 空状态内容
 */
@Composable
private fun EmptyStateContent(
    connectionStatus: ConnectionStatus,
    onStartTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // AI图标
        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "AuraFlow Agent",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "AI 驱动的智能移动自动化助手",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 根据连接状态显示不同内容
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                Text(
                    text = "✨ AI大脑已就绪，等待您的指令",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onStartTask
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始自动化任务")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "请在服务器端输入您的任务描述，Agent将智能执行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            ConnectionStatus.DISCONNECTED -> {
                Text(
                    text = "🔌 请先连接到AI大脑服务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { /* 跳转到配置页面 */ }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("配置AI服务")
                }
            }
            
            else -> {
                CircularProgressIndicator()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "正在连接AI大脑...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 聊天消息项
 */
@Composable
private fun ChatMessageItem(
    message: ChatMessageData,
    onScreenshotClick: (String) -> Unit,
    onAnswerQuestion: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (message.type) {
                ChatMessageType.USER_INTENT -> MaterialTheme.colorScheme.primaryContainer
                ChatMessageType.AI_THINKING -> MaterialTheme.colorScheme.tertiaryContainer
                ChatMessageType.AI_COMMAND -> MaterialTheme.colorScheme.secondaryContainer
                ChatMessageType.EXECUTION_RESULT -> if (message.isSuccess == true) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
                ChatMessageType.AI_QUESTION -> MaterialTheme.colorScheme.surfaceVariant
                ChatMessageType.SCREENSHOT -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 消息头部
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (message.type) {
                        ChatMessageType.USER_INTENT -> Icons.Default.Person
                        ChatMessageType.AI_THINKING -> Icons.Default.Psychology
                        ChatMessageType.AI_COMMAND -> Icons.Default.SmartToy
                        ChatMessageType.EXECUTION_RESULT -> if (message.isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error
                        ChatMessageType.AI_QUESTION -> Icons.Default.HelpOutline
                        ChatMessageType.SCREENSHOT -> Icons.Default.Screenshot
                        else -> Icons.Default.Message
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = when (message.type) {
                        ChatMessageType.USER_INTENT -> "用户意图"
                        ChatMessageType.AI_THINKING -> "AI思考"
                        ChatMessageType.AI_COMMAND -> "AI指令"
                        ChatMessageType.EXECUTION_RESULT -> "执行结果"
                        ChatMessageType.AI_QUESTION -> "AI提问"
                        ChatMessageType.SCREENSHOT -> "屏幕快照"
                        else -> "消息"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 消息内容
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
            
            // 特殊内容处理
            when (message.type) {
                ChatMessageType.SCREENSHOT -> {
                    if (message.screenshotData != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = { onScreenshotClick(message.screenshotData) }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("查看截图")
                        }
                    }
                }
                
                ChatMessageType.AI_QUESTION -> {
                    if (message.questionId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        QuestionAnswerSection(
                            questionId = message.questionId,
                            questionType = message.questionType ?: "TEXT_INPUT",
                            options = message.questionOptions,
                            onAnswer = onAnswerQuestion
                        )
                    }
                }
                
                else -> Unit
            }
        }
    }
}

/**
 * 问题回答区域
 */
@Composable
private fun QuestionAnswerSection(
    questionId: String,
    questionType: String,
    options: List<String>?,
    onAnswer: (String, String) -> Unit
) {
    var answer by remember { mutableStateOf("") }
    
    when (questionType) {
        "YES_NO" -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAnswer(questionId, "是") }
                ) {
                    Text("是")
                }
                
                OutlinedButton(
                    onClick = { onAnswer(questionId, "否") }
                ) {
                    Text("否")
                }
            }
        }
        
        "MULTIPLE_CHOICE" -> {
            options?.forEach { option ->
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onAnswer(questionId, option) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(option)
                }
            }
        }
        
        else -> {
            // TEXT_INPUT
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    placeholder = { Text("请输入回答...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { 
                        if (answer.isNotBlank()) {
                            onAnswer(questionId, answer)
                        }
                    },
                    enabled = answer.isNotBlank()
                ) {
                    Text("发送")
                }
            }
        }
    }
}

/**
 * 聊天输入区域
 */
@Composable
private fun ChatInputSection(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSendInput: () -> Unit,
    onVoiceInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = { Text("AI正在等待您的输入...") },
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                // 语音输入按钮
                IconButton(
                    onClick = onVoiceInput
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "语音输入"
                    )
                }
                
                // 发送按钮
                FilledIconButton(
                    onClick = onSendInput,
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

/**
 * 截图查看对话框
 */
@Composable
private fun ScreenshotDialog(
    screenshot: String?,
    onDismiss: () -> Unit
) {
    if (screenshot != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("屏幕快照") },
            text = {
                // TODO: 显示Base64编码的截图
                // 这里需要将Base64字符串转换为Bitmap并显示
                Text("截图数据: ${screenshot.take(50)}...")
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * 扩展函数：检查列表是否滚动到末尾
 */
private fun androidx.compose.foundation.lazy.LazyListState.isScrolledToEnd(): Boolean {
    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
    return lastItem == null || lastItem.index >= layoutInfo.totalItemsCount - 1
}