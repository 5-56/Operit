package com.ai.assistance.operit.auraflow.ui.floating

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.auraflow.protocol.ConnectionStatus

/**
 * 浮动窗口显示模式
 */
enum class FloatingWindowMode {
    FULL,       // 完整模式 - 显示所有控制按钮
    COMPACT,    // 紧凑模式 - 显示主要按钮
    MINI        // 迷你模式 - 只显示状态指示
}

/**
 * 浮动窗口状态数据
 */
data class FloatingWindowState(
    val mode: FloatingWindowMode = FloatingWindowMode.FULL,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val agentState: String = "IDLE",
    val isTaskRunning: Boolean = false,
    val taskName: String? = null,
    val isDragging: Boolean = false
)

/**
 * AuraFlow Agent 浮动控制窗口
 * 按键精灵风格的控制面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraFlowFloatingWindow(
    state: FloatingWindowState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onOpenMainApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    // 根据模式显示不同的窗口内容
    AnimatedContent(
        targetState = state.mode,
        transitionSpec = {
            slideInVertically { height -> height } + fadeIn() with
            slideOutVertically { height -> -height } + fadeOut()
        },
        modifier = modifier
    ) { mode ->
        when (mode) {
            FloatingWindowMode.FULL -> {
                FullModeWindow(
                    state = state,
                    onPlayPause = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    },
                    onStop = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStop()
                    },
                    onMinimize = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMinimize()
                    },
                    onOpenMainApp = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenMainApp()
                    },
                    onClose = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose()
                    }
                )
            }
            
            FloatingWindowMode.COMPACT -> {
                CompactModeWindow(
                    state = state,
                    onPlayPause = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    },
                    onExpand = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpand()
                    }
                )
            }
            
            FloatingWindowMode.MINI -> {
                MiniModeWindow(
                    state = state,
                    onExpand = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpand()
                    }
                )
            }
        }
    }
}

/**
 * 完整模式窗口
 */
@Composable
private fun FullModeWindow(
    state: FloatingWindowState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMinimize: () -> Unit,
    onOpenMainApp: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIndicator(
                        connectionStatus = state.connectionStatus,
                        modifier = Modifier.size(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    Text(
                        text = "AuraFlow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // 右侧控制按钮
                Row {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Minimize,
                            contentDescription = "最小化",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 任务状态显示
            if (state.taskName != null) {
                Text(
                    text = state.taskName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 主控制按钮区域
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停按钮
                FloatingControlButton(
                    icon = if (state.isTaskRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isTaskRunning) "暂停" else "播放",
                    onClick = onPlayPause,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = 48.dp,
                    enabled = state.connectionStatus == ConnectionStatus.CONNECTED
                )
                
                // 停止按钮
                FloatingControlButton(
                    icon = Icons.Default.Stop,
                    contentDescription = "停止",
                    onClick = onStop,
                    backgroundColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    size = 40.dp,
                    enabled = state.isTaskRunning
                )
                
                // 主页按钮
                FloatingControlButton(
                    icon = Icons.Default.Home,
                    contentDescription = "打开主应用",
                    onClick = onOpenMainApp,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    size = 40.dp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 状态文字
            Text(
                text = when {
                    state.connectionStatus != ConnectionStatus.CONNECTED -> "未连接"
                    state.isTaskRunning -> "运行中"
                    else -> "待命"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 紧凑模式窗口
 */
@Composable
private fun CompactModeWindow(
    state: FloatingWindowState,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onExpand() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态指示
            StatusIndicator(
                connectionStatus = state.connectionStatus,
                modifier = Modifier.size(8.dp)
            )
            
            // 主控制按钮
            FloatingControlButton(
                icon = if (state.isTaskRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isTaskRunning) "暂停" else "播放",
                onClick = onPlayPause,
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                size = 36.dp,
                enabled = state.connectionStatus == ConnectionStatus.CONNECTED
            )
            
            // 状态文字
            Text(
                text = if (state.isTaskRunning) "运行中" else "待命",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 迷你模式窗口
 */
@Composable
private fun MiniModeWindow(
    state: FloatingWindowState,
    onExpand: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .shadow(6.dp, CircleShape)
            .background(
                when (state.connectionStatus) {
                    ConnectionStatus.CONNECTED -> if (state.isTaskRunning) Color.Green else Color.Blue
                    ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color.Yellow
                    ConnectionStatus.ERROR -> Color.Red
                    ConnectionStatus.DISCONNECTED -> Color.Gray
                },
                CircleShape
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = "AuraFlow Agent",
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

/**
 * 浮动控制按钮
 */
@Composable
private fun FloatingControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .background(
                if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
                CircleShape
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.5f),
            tint = if (enabled) contentColor else contentColor.copy(alpha = 0.7f)
        )
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val color = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> Color.Green
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color.Yellow
        ConnectionStatus.ERROR -> Color.Red
        ConnectionStatus.DISCONNECTED -> Color.Gray
    }
    
    val isAnimated = connectionStatus == ConnectionStatus.CONNECTING || 
                    connectionStatus == ConnectionStatus.RECONNECTING
    
    Box(
        modifier = modifier
            .background(color, CircleShape)
            .then(
                if (isAnimated) {
                    Modifier.animateContentSize()
                } else {
                    Modifier
                }
            )
    )
}