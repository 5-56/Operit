package com.ai.assistance.operit.auraflow.ui.voice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * 消息类型
 */
enum class VoiceMessageType {
    USER_TEXT,      // 用户文本
    USER_VOICE,     // 用户语音
    AI_TEXT,        // AI文本回复
    AI_VOICE,       // AI语音回复
    SYSTEM,         // 系统消息
    PARTIAL         // 部分识别结果
}

/**
 * 语音消息
 */
data class VoiceMessage(
    val id: String,
    val type: VoiceMessageType,
    val content: String,
    val timestamp: Long,
    val confidence: Float = 1.0f,
    val duration: Long? = null,
    val audioLevel: Float = 0f
)

/**
 * 音频可视化组件
 */
@Composable
fun AudioVisualizationComponent(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioViz")
    
    // 音频波形动画
    val waveAnimation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    
    val waveAnimation2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    
    val waveAnimation3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )
    
    // 脉冲动画
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            // 外圈波浪
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val baseRadius = size.minDimension / 4
                
                // 绘制多层波浪
                listOf(
                    Triple(waveAnimation1, 0.3f, Color(0xFF6366F1)),
                    Triple(waveAnimation2, 0.2f, Color(0xFF8B5CF6)),
                    Triple(waveAnimation3, 0.1f, Color(0xFFA855F7))
                ).forEachIndexed { index, (animation, opacity, color) ->
                    val waveRadius = baseRadius + (audioLevel * 100 + 20) * (index + 1) * 
                                   (1 + sin(animation) * 0.3f)
                    
                    drawCircle(
                        color = color.copy(alpha = opacity),
                        radius = waveRadius,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )
                }
            }
            
            // 中心脉冲圆圈
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(if (isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.8f),
                                Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                Color(0xFFA855F7).copy(alpha = 0.4f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "话筒",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        } else {
            // 静止状态
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "话筒关闭",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * 语音状态指示器
 */
@Composable
fun VoiceStateIndicator(
    state: com.ai.assistance.operit.auraflow.voice.VoiceState,
    modifier: Modifier = Modifier
) {
    val stateColor = when (state) {
        com.ai.assistance.operit.auraflow.voice.VoiceState.IDLE -> Color(0xFF6B7280)
        com.ai.assistance.operit.auraflow.voice.VoiceState.LISTENING -> Color(0xFF10B981)
        com.ai.assistance.operit.auraflow.voice.VoiceState.PROCESSING -> Color(0xFFF59E0B)
        com.ai.assistance.operit.auraflow.voice.VoiceState.SPEAKING -> Color(0xFF3B82F6)
        com.ai.assistance.operit.auraflow.voice.VoiceState.INTERRUPTED -> Color(0xFFEF4444)
        com.ai.assistance.operit.auraflow.voice.VoiceState.ERROR -> Color(0xFFDC2626)
    }
    
    val stateText = when (state) {
        com.ai.assistance.operit.auraflow.voice.VoiceState.IDLE -> "待机中"
        com.ai.assistance.operit.auraflow.voice.VoiceState.LISTENING -> "正在监听..."
        com.ai.assistance.operit.auraflow.voice.VoiceState.PROCESSING -> "处理中..."
        com.ai.assistance.operit.auraflow.voice.VoiceState.SPEAKING -> "AI正在说话"
        com.ai.assistance.operit.auraflow.voice.VoiceState.INTERRUPTED -> "已被打断"
        com.ai.assistance.operit.auraflow.voice.VoiceState.ERROR -> "出现错误"
    }
    
    val stateIcon = when (state) {
        com.ai.assistance.operit.auraflow.voice.VoiceState.IDLE -> Icons.Default.Pause
        com.ai.assistance.operit.auraflow.voice.VoiceState.LISTENING -> Icons.Default.Hearing
        com.ai.assistance.operit.auraflow.voice.VoiceState.PROCESSING -> Icons.Default.Psychology
        com.ai.assistance.operit.auraflow.voice.VoiceState.SPEAKING -> Icons.Default.RecordVoiceOver
        com.ai.assistance.operit.auraflow.voice.VoiceState.INTERRUPTED -> Icons.Default.PauseCircle
        com.ai.assistance.operit.auraflow.voice.VoiceState.ERROR -> Icons.Default.Error
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = stateColor.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = stateIcon,
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stateText,
                color = stateColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 消息气泡组件
 */
@Composable
fun MessageBubble(
    message: VoiceMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.type in listOf(VoiceMessageType.USER_TEXT, VoiceMessageType.USER_VOICE)
    val isPartial = message.type == VoiceMessageType.PARTIAL
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "AI",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 消息内容
                Text(
                    text = message.content,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 14.sp,
                    modifier = if (isPartial) {
                        Modifier.alpha(0.7f)
                    } else {
                        Modifier
                    }
                )
                
                // 置信度和时间戳
                if (message.confidence < 1.0f || message.duration != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.confidence < 1.0f) {
                            Text(
                                text = "置信度: ${(message.confidence * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                        
                        if (message.duration != null) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "语音",
                                modifier = Modifier.size(12.dp),
                                tint = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                            Text(
                                text = "${message.duration / 1000}s",
                                fontSize = 10.sp,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "用户",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 语音聊天界面
 */
@Composable
fun VoiceChatScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: VoiceChatViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // 观察状态
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isConversationActive by viewModel.isConversationActive.collectAsStateWithLifecycle()
    val currentPartialText by viewModel.currentPartialText.collectAsStateWithLifecycle()
    
    // UI状态
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部工具栏
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "AI语音助手",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConversationActive) "对话进行中" else "点击开始对话",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            },
            actions = {
                // 语音设置
                IconButton(onClick = { viewModel.showVoiceSettings() }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "语音设置"
                    )
                }
                
                // 清空对话
                IconButton(onClick = { viewModel.clearMessages() }) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "清空对话"
                    )
                }
            }
        )
        
        // 状态指示器
        VoiceStateIndicator(
            state = voiceState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier.animateItemPlacement()
                )
            }
            
            // 部分识别结果
            if (currentPartialText.isNotEmpty()) {
                item {
                    MessageBubble(
                        message = VoiceMessage(
                            id = "partial",
                            type = VoiceMessageType.PARTIAL,
                            content = "$currentPartialText...",
                            timestamp = System.currentTimeMillis(),
                            confidence = 0.5f
                        ),
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
        }
        
        // 音频可视化区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 音频可视化
                AudioVisualizationComponent(
                    audioLevel = audioLevel,
                    isActive = voiceState == com.ai.assistance.operit.auraflow.voice.VoiceState.LISTENING ||
                              voiceState == com.ai.assistance.operit.auraflow.voice.VoiceState.SPEAKING
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 音量级别指示
                if (audioLevel > 0) {
                    LinearProgressIndicator(
                        progress = audioLevel.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = when {
                            audioLevel > 0.7f -> Color(0xFF10B981)
                            audioLevel > 0.4f -> Color(0xFFF59E0B) 
                            else -> Color(0xFF6B7280)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        
        // 控制按钮
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 开始/停止对话按钮
                FloatingActionButton(
                    onClick = {
                        if (isConversationActive) {
                            viewModel.stopConversation()
                        } else {
                            viewModel.startConversation()
                        }
                    },
                    containerColor = if (isConversationActive) {
                        Color(0xFFEF4444)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isConversationActive) {
                            Icons.Default.Stop
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isConversationActive) "停止对话" else "开始对话",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
                
                // 打断按钮
                if (isSpeaking) {
                    OutlinedButton(
                        onClick = { viewModel.interruptSpeaking() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PauseCircle,
                            contentDescription = "打断",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打断")
                    }
                }
                
                // 视频通话按钮
                OutlinedButton(
                    onClick = { viewModel.toggleVideoCall() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "视频通话",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("视频")
                }
                
                // 文本输入按钮  
                OutlinedButton(
                    onClick = { viewModel.showTextInput() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "文本输入",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("文本")
                }
            }
        }
    }
}

/**
 * 语音设置对话框
 */
@Composable
fun VoiceSettingsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    speechRate: Float,
    pitch: Float,
    onSpeechRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text("语音设置")
            },
            text = {
                Column {
                    Text("语速调节")
                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )
                    Text("当前语速: ${String.format("%.1f", speechRate)}x")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("音调调节")
                    Slider(
                        value = pitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )
                    Text("当前音调: ${String.format("%.1f", pitch)}x")
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            }
        )
    }
}