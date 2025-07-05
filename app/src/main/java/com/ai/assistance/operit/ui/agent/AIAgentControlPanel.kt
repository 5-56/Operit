package com.ai.assistance.operit.ui.agent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.core.agent.*
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.launch

/**
 * AI Agent控制面板
 * 
 * 提供：
 * 1. AI Agent状态实时显示
 * 2. 任务进度监控
 * 3. AI思考过程可视化
 * 4. 手动控制操作
 * 5. 配置和设置界面
 */
@Composable
fun AIAgentControlPanel(
    aiAgent: OperitAIAgentController,
    modifier: Modifier = Modifier,
    onConfigureAI: () -> Unit = {},
    onPermissionSettings: () -> Unit = {},
    onTestConnection: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态收集
    val agentState by aiAgent.currentState.collectAsStateWithLifecycle()
    val taskProgress by aiAgent.taskProgress.collectAsStateWithLifecycle()
    val aiThinking by aiAgent.aiThinking.collectAsStateWithLifecycle()
    
    // 控制面板展开状态
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 主状态显示
            MainStatusDisplay(
                agentState = agentState,
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded }
            )
            
            // 展开的详细内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 标签选择器
                    TabSelector(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 内容区域
                    when (selectedTab) {
                        0 -> StatusDetailsTab(agentState, taskProgress, aiThinking)
                        1 -> ControlActionsTab(
                            aiAgent = aiAgent,
                            onConfigureAI = onConfigureAI,
                            onPermissionSettings = onPermissionSettings,
                            onTestConnection = onTestConnection
                        )
                        2 -> SettingsTab(aiAgent)
                        3 -> StatisticsTab(aiAgent)
                    }
                }
            }
        }
    }
}

/**
 * 主状态显示
 */
@Composable
private fun MainStatusDisplay(
    agentState: OperitAIAgentController.AgentState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态指示器
        StatusIndicator(agentState)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 状态信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = getStateDescription(agentState),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 展开/收起图标
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(agentState: OperitAIAgentController.AgentState) {
    val (color, icon) = when (agentState) {
        is OperitAIAgentController.AgentState.Idle -> Color(0xFF4CAF50) to Icons.Default.Check
        is OperitAIAgentController.AgentState.PerceivingScreen -> Color(0xFF2196F3) to Icons.Default.Visibility
        is OperitAIAgentController.AgentState.CommunicatingWithAI -> Color(0xFF9C27B0) to Icons.Default.Psychology
        is OperitAIAgentController.AgentState.ExecutingInstructions -> Color(0xFFFF9800) to Icons.Default.PlayArrow
        is OperitAIAgentController.AgentState.WaitingForFeedback -> Color(0xFFFFEB3B) to Icons.Default.HourglassEmpty
        is OperitAIAgentController.AgentState.TaskCompleted -> Color(0xFF4CAF50) to Icons.Default.CheckCircle
        is OperitAIAgentController.AgentState.Error -> Color(0xFFF44336) to Icons.Default.Error
    }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 标签选择器
 */
@Composable
private fun TabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("状态", "控制", "设置", "统计")
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, title ->
            FilterChip(
                onClick = { onTabSelected(index) },
                label = { Text(title) },
                selected = selectedTab == index,
                modifier = Modifier.weight(1f)
            )
            if (index < tabs.lastIndex) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * 状态详情标签
 */
@Composable
private fun StatusDetailsTab(
    agentState: OperitAIAgentController.AgentState,
    taskProgress: OperitAIAgentController.TaskProgress?,
    aiThinking: OperitAIAgentController.AIThinkingProcess?
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 当前状态卡片
        item {
            StateDetailCard(
                title = "当前状态",
                content = getDetailedStateDescription(agentState),
                icon = Icons.Default.Info
            )
        }
        
        // 任务进度卡片
        if (taskProgress != null) {
            item {
                TaskProgressCard(taskProgress)
            }
        }
        
        // AI思考过程卡片
        if (aiThinking != null) {
            item {
                AIThinkingCard(aiThinking)
            }
        }
        
        // 执行指令详情
        if (agentState is OperitAIAgentController.AgentState.ExecutingInstructions) {
            item {
                InstructionsCard(agentState.instructions)
            }
        }
    }
}

/**
 * 控制操作标签
 */
@Composable
private fun ControlActionsTab(
    aiAgent: OperitAIAgentController,
    onConfigureAI: () -> Unit,
    onPermissionSettings: () -> Unit,
    onTestConnection: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var testInput by remember { mutableStateOf("") }
    
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 快速测试
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "快速测试",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        label = { Text("输入测试指令") },
                        placeholder = { Text("例如：等待2秒") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (testInput.isNotBlank()) {
                                    scope.launch {
                                        val intent = OperitAIAgentController.UserIntent(
                                            description = testInput
                                        )
                                        aiAgent.executeUserIntent(intent)
                                    }
                                }
                            },
                            enabled = testInput.isNotBlank() && !aiAgent.isBusy(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("执行测试")
                        }
                        
                        OutlinedButton(
                            onClick = { testInput = "" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空")
                        }
                    }
                }
            }
        }
        
        // 控制操作
        item {
            ControlActionsGrid(
                aiAgent = aiAgent,
                onConfigureAI = onConfigureAI,
                onPermissionSettings = onPermissionSettings,
                onTestConnection = onTestConnection
            )
        }
    }
}

/**
 * 控制操作网格
 */
@Composable
private fun ControlActionsGrid(
    aiAgent: OperitAIAgentController,
    onConfigureAI: () -> Unit,
    onPermissionSettings: () -> Unit,
    onTestConnection: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                text = "停止Agent",
                icon = Icons.Default.Stop,
                onClick = { aiAgent.stopAgent() },
                enabled = aiAgent.isBusy(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            
            ActionButton(
                text = "重置状态",
                icon = Icons.Default.Refresh,
                onClick = { aiAgent.reset() },
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                text = "AI配置",
                icon = Icons.Default.Settings,
                onClick = onConfigureAI,
                modifier = Modifier.weight(1f)
            )
            
            ActionButton(
                text = "权限设置",
                icon = Icons.Default.Security,
                onClick = onPermissionSettings,
                modifier = Modifier.weight(1f)
            )
        }
        
        ActionButton(
            text = "测试连接",
            icon = Icons.Default.Wifi,
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 操作按钮
 */
@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

/**
 * 设置标签
 */
@Composable
private fun SettingsTab(aiAgent: OperitAIAgentController) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "AI Agent 设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            SettingItem(
                title = "自动执行",
                description = "允许AI Agent自动执行操作",
                checked = true,
                onCheckedChange = { /* TODO */ }
            )
        }
        
        item {
            SettingItem(
                title = "显示思考过程",
                description = "实时显示AI的思考过程",
                checked = true,
                onCheckedChange = { /* TODO */ }
            )
        }
        
        item {
            SettingItem(
                title = "操作确认",
                description = "执行敏感操作前需要确认",
                checked = false,
                onCheckedChange = { /* TODO */ }
            )
        }
    }
}

/**
 * 统计标签
 */
@Composable
private fun StatisticsTab(aiAgent: OperitAIAgentController) {
    val statusReport = remember { aiAgent.getStatusReport() }
    
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "性能统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = statusReport,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * 状态详情卡片
 */
@Composable
private fun StateDetailCard(
    title: String,
    content: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 任务进度卡片
 */
@Composable
private fun TaskProgressCard(taskProgress: OperitAIAgentController.TaskProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "任务进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${taskProgress.currentStep}/${taskProgress.totalSteps}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = taskProgress.currentStep.toFloat() / taskProgress.totalSteps,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = taskProgress.currentOperation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * AI思考卡片
 */
@Composable
private fun AIThinkingCard(aiThinking: OperitAIAgentController.AIThinkingProcess) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧠 AI思考",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(aiThinking.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "步骤: ${aiThinking.step}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "推理: ${aiThinking.reasoning}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "下一步: ${aiThinking.nextAction}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 指令卡片
 */
@Composable
private fun InstructionsCard(instructions: List<AIInstruction>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "执行指令 (${instructions.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            instructions.take(3).forEach { instruction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = instruction.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (instruction != instructions.last()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            if (instructions.size > 3) {
                Text(
                    text = "... 还有 ${instructions.size - 3} 个指令",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 设置项
 */
@Composable
private fun SettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
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
}

// 辅助函数
private fun getStateDescription(state: OperitAIAgentController.AgentState): String {
    return when (state) {
        is OperitAIAgentController.AgentState.Idle -> "空闲中"
        is OperitAIAgentController.AgentState.PerceivingScreen -> "感知屏幕中..."
        is OperitAIAgentController.AgentState.CommunicatingWithAI -> "与AI通信中..."
        is OperitAIAgentController.AgentState.ExecutingInstructions -> "执行指令中..."
        is OperitAIAgentController.AgentState.WaitingForFeedback -> "等待反馈中..."
        is OperitAIAgentController.AgentState.TaskCompleted -> "任务已完成"
        is OperitAIAgentController.AgentState.Error -> "错误状态"
    }
}

private fun getDetailedStateDescription(state: OperitAIAgentController.AgentState): String {
    return when (state) {
        is OperitAIAgentController.AgentState.Idle -> "AI Agent处于空闲状态，等待用户指令"
        is OperitAIAgentController.AgentState.PerceivingScreen -> "正在分析当前屏幕状态和可交互元素"
        is OperitAIAgentController.AgentState.CommunicatingWithAI -> "正在将屏幕信息发送给AI大脑进行分析"
        is OperitAIAgentController.AgentState.ExecutingInstructions -> "正在执行AI生成的操作指令 (${state.instructions.size}个)"
        is OperitAIAgentController.AgentState.WaitingForFeedback -> "等待操作结果反馈以继续执行"
        is OperitAIAgentController.AgentState.TaskCompleted -> "用户任务已成功完成: ${state.result}"
        is OperitAIAgentController.AgentState.Error -> "发生错误: ${state.error}"
    }
}