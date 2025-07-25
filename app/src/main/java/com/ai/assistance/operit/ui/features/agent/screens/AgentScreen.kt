package com.ai.assistance.operit.ui.features.agent.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.ai.assistance.operit.core.agent.AgentTemplateManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.ui.features.agent.viewmodel.AgentViewModel
import com.ai.assistance.operit.core.agent.AgentPlanStatus
import com.ai.assistance.operit.core.agent.AgentStepStatus
import com.ai.assistance.operit.core.agent.AgentStepType
import kotlinx.coroutines.launch

/**
 * Agent功能主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    modifier: Modifier = Modifier,
    viewModel: AgentViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    var userInput by remember { mutableStateOf("") }
    var showPlanDetails by remember { mutableStateOf(false) }
    var showTemplateScreen by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // 自动滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
                // 顶部工具栏
        TopAppBar(
            title = { 
                Text("智能Agent助手") 
            },
            actions = {
                // 模板按钮
                IconButton(
                    onClick = { showTemplateScreen = true }
                ) {
                    Icon(
                        Icons.Default.LibraryBooks,
                        contentDescription = "任务模板"
                    )
                }
                
                // 显示当前计划按钮
                if (uiState.currentPlan != null) {
                    IconButton(
                        onClick = { showPlanDetails = true }
                    ) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = "查看计划详情"
                        )
                    }
                }
                
                // 控制按钮
                if (uiState.currentPlan != null && uiState.currentPlan.status == AgentPlanStatus.EXECUTING) {
                    IconButton(
                        onClick = { viewModel.pauseCurrentPlan() }
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "暂停"
                        )
                    }
                } else if (uiState.currentPlan != null && uiState.currentPlan.status == AgentPlanStatus.PAUSED) {
                    IconButton(
                        onClick = { viewModel.resumeCurrentPlan() }
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "恢复"
                        )
                    }
                }
                
                if (uiState.currentPlan != null) {
                    IconButton(
                        onClick = { viewModel.cancelCurrentPlan() }
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止"
                        )
                    }
                }
            }
        )
        
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages) { message ->
                MessageCard(
                    message = message,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 显示当前执行状态
            if (uiState.isExecuting) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Agent正在执行任务...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        
        // 输入区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请描述您需要Agent帮您完成的任务...") },
                    minLines = 2,
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 快捷操作按钮
                    Row {
                        TextButton(
                            onClick = { showTemplateScreen = true }
                        ) {
                            Text("模板")
                        }
                        
                        TextButton(
                            onClick = { 
                                userInput = "帮我整理桌面文件，将相同类型的文件放到对应文件夹中"
                            }
                        ) {
                            Text("整理文件")
                        }
                        
                        TextButton(
                            onClick = { 
                                userInput = "帮我检查系统状态，包括存储空间、内存使用情况等"
                            }
                        ) {
                            Text("系统检查")
                        }
                    }
                    
                    // 发送按钮
                    Button(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                viewModel.executeAgentTask(userInput)
                                userInput = ""
                            }
                        },
                        enabled = userInput.isNotBlank() && !uiState.isExecuting
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("执行")
                    }
                }
            }
        }
    }
    
    // 计划详情对话框
    if (showPlanDetails && uiState.currentPlan != null) {
        PlanDetailsDialog(
            plan = uiState.currentPlan,
            onDismiss = { showPlanDetails = false }
        )
    }
    
    // 模板选择界面
    if (showTemplateScreen) {
        AgentTemplateScreen(
            onTemplateSelected = { template, parameters ->
                val templateManager = AgentTemplateManager.getInstance(context)
                val prompt = templateManager.applyTemplate(template, parameters)
                userInput = prompt
                showTemplateScreen = false
            },
            onBackPressed = { showTemplateScreen = false }
        )
    }
}

/**
 * 消息卡片组件
 */
@Composable
private fun MessageCard(
    message: AgentMessage,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (message.type) {
                AgentMessageType.USER -> MaterialTheme.colorScheme.surface
                AgentMessageType.AGENT -> MaterialTheme.colorScheme.secondaryContainer
                AgentMessageType.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
                AgentMessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 消息头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (message.type) {
                        AgentMessageType.USER -> "用户"
                        AgentMessageType.AGENT -> "Agent"
                        AgentMessageType.SYSTEM -> "系统"
                        AgentMessageType.ERROR -> "错误"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (message.type) {
                        AgentMessageType.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
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
            
            // 如果有步骤信息，显示步骤详情
            message.step?.let { step ->
                Spacer(modifier = Modifier.height(8.dp))
                StepCard(step = step)
            }
        }
    }
}

/**
 * 步骤卡片组件
 */
@Composable
private fun StepCard(
    step: AgentStepInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (step.type) {
                        AgentStepType.ANALYSIS -> Icons.Default.Search
                        AgentStepType.PLANNING -> Icons.Default.Assignment
                        AgentStepType.SCRIPT_GEN -> Icons.Default.Code
                        AgentStepType.EXECUTION -> Icons.Default.PlayArrow
                        AgentStepType.VALIDATION -> Icons.Default.CheckCircle
                        AgentStepType.OPTIMIZATION -> Icons.Default.Tune
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when (step.status) {
                        AgentStepStatus.COMPLETED -> Color.Green
                        AgentStepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        AgentStepStatus.FAILED -> Color.Red
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = step.status.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (step.status) {
                        AgentStepStatus.COMPLETED -> Color.Green
                        AgentStepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                        AgentStepStatus.FAILED -> Color.Red
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (step.hasScript) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "包含脚本代码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 计划详情对话框
 */
@Composable
private fun PlanDetailsDialog(
    plan: AgentPlanInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = plan.title)
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = plan.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "执行状态: ${plan.status}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "执行步骤:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(plan.steps) { step ->
                    StepCard(
                        step = step,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}