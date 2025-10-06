package com.xihe.assistant.ui.features.automation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDrop
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xihe.assistant.data.model.AutomationTask
import com.xihe.assistant.data.model.AutomationWorkflow
import com.xihe.assistant.ui.components.SmartButton
import com.xihe.assistant.ui.components.SmartCard
import kotlinx.coroutines.launch

/**
 * 可视化工作流编辑器
 * 提供拖拽式的工作流设计界面
 */
@Composable
fun WorkflowEditor(
    workflow: AutomationWorkflow?,
    onSave: (AutomationWorkflow) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNodes by remember { mutableStateOf<List<WorkflowNode>>(emptyList()) }
    var connections by remember { mutableStateOf<List<WorkflowConnection>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f to 0f) }
    
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 工具栏
        WorkflowToolbar(
            onSave = {
                val newWorkflow = createWorkflowFromNodes(selectedNodes, connections)
                onSave(newWorkflow)
            },
            onCancel = onCancel,
            onUndo = { /* 撤销逻辑 */ },
            onRedo = { /* 重做逻辑 */ },
            onClear = {
                selectedNodes = emptyList()
                connections = emptyList()
            }
        )

        // 主编辑区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 节点面板
            WorkflowNodePanel(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                onNodeSelected = { nodeType ->
                    val newNode = createNodeFromType(nodeType)
                    selectedNodes = selectedNodes + newNode
                }
            )

            // 画布区域
            WorkflowCanvas(
                nodes = selectedNodes,
                connections = connections,
                onNodeMoved = { nodeId, offset ->
                    selectedNodes = selectedNodes.map { node ->
                        if (node.id == nodeId) {
                            node.copy(position = node.position + offset)
                        } else {
                            node
                        }
                    }
                },
                onNodeSelected = { nodeId ->
                    // 节点选择逻辑
                },
                onConnectionCreated = { fromNodeId, toNodeId ->
                    val newConnection = WorkflowConnection(
                        id = "${fromNodeId}_${toNodeId}",
                        fromNodeId = fromNodeId,
                        toNodeId = toNodeId
                    )
                    connections = connections + newConnection
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 200.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
            )

            // 属性面板
            if (selectedNodes.isNotEmpty()) {
                WorkflowPropertyPanel(
                    selectedNode = selectedNodes.first(),
                    onPropertyChanged = { nodeId, property, value ->
                        selectedNodes = selectedNodes.map { node ->
                            if (node.id == nodeId) {
                                node.copy(properties = node.properties + (property to value))
                            } else {
                                node
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .width(300.dp)
                )
            }
        }
    }
}

/**
 * 工作流工具栏
 */
@Composable
private fun WorkflowToolbar(
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧工具
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartButton(
                    text = "撤销",
                    onClick = onUndo,
                    icon = Icons.Default.Undo,
                    variant = com.xihe.assistant.ui.components.ButtonVariant.Secondary
                )
                
                SmartButton(
                    text = "重做",
                    onClick = onRedo,
                    icon = Icons.Default.Redo,
                    variant = com.xihe.assistant.ui.components.ButtonVariant.Secondary
                )
                
                SmartButton(
                    text = "清空",
                    onClick = onClear,
                    icon = Icons.Default.Clear,
                    variant = com.xihe.assistant.ui.components.ButtonVariant.Warning
                )
            }

            // 右侧操作
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartButton(
                    text = "取消",
                    onClick = onCancel,
                    variant = com.xihe.assistant.ui.components.ButtonVariant.Secondary
                )
                
                SmartButton(
                    text = "保存",
                    onClick = onSave,
                    icon = Icons.Default.Save,
                    variant = com.xihe.assistant.ui.components.ButtonVariant.Success
                )
            }
        }
    }
}

/**
 * 工作流节点面板
 */
@Composable
private fun WorkflowNodePanel(
    onNodeSelected: (NodeType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(180.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "节点库",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NodeType.values()) { nodeType ->
                    NodeTypeItem(
                        nodeType = nodeType,
                        onClick = { onNodeSelected(nodeType) }
                    )
                }
            }
        }
    }
}

/**
 * 节点类型项
 */
@Composable
private fun NodeTypeItem(
    nodeType: NodeType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = nodeType.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = nodeType.displayName,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 工作流画布
 */
@Composable
private fun WorkflowCanvas(
    nodes: List<WorkflowNode>,
    connections: List<WorkflowConnection>,
    onNodeMoved: (String, Pair<Float, Float>) -> Unit,
    onNodeSelected: (String) -> Unit,
    onConnectionCreated: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.LightGray.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
    ) {
        // 网格背景
        CanvasGrid()
        
        // 连接线
        connections.forEach { connection ->
            WorkflowConnection(
                connection = connection,
                nodes = nodes
            )
        }
        
        // 节点
        nodes.forEach { node ->
            WorkflowNode(
                node = node,
                onMoved = { offset -> onNodeMoved(node.id, offset) },
                onSelected = { onNodeSelected(node.id) },
                onConnectionStart = { fromNodeId ->
                    // 连接开始逻辑
                },
                onConnectionEnd = { toNodeId ->
                    // 连接结束逻辑
                    onConnectionCreated(node.id, toNodeId)
                }
            )
        }
    }
}

/**
 * 工作流节点
 */
@Composable
private fun WorkflowNode(
    node: WorkflowNode,
    onMoved: (Pair<Float, Float>) -> Unit,
    onSelected: () -> Unit,
    onConnectionStart: (String) -> Unit,
    onConnectionEnd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f to 0f) }
    
    val animatedOffset by animateOffsetAsState(
        targetValue = if (isDragging) dragOffset else 0f to 0f,
        animationSpec = tween(100),
        label = "drag_offset"
    )

    Box(
        modifier = modifier
            .offset(
                x = (node.position.first + animatedOffset.first).dp,
                y = (node.position.second + animatedOffset.second).dp
            )
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true
                        onSelected()
                    },
                    onDrag = { _, dragAmount ->
                        dragOffset = dragOffset + dragAmount
                    },
                    onDragEnd = {
                        onMoved(dragOffset)
                        dragOffset = 0f to 0f
                        isDragging = false
                    }
                )
            }
    ) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .clickable { onSelected() },
            colors = CardDefaults.cardColors(
                containerColor = when (node.type) {
                    NodeType.Trigger -> MaterialTheme.colorScheme.primaryContainer
                    NodeType.Action -> MaterialTheme.colorScheme.secondaryContainer
                    NodeType.Condition -> MaterialTheme.colorScheme.tertiaryContainer
                    NodeType.End -> MaterialTheme.colorScheme.errorContainer
                }
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isDragging) 8.dp else 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = node.type.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // 连接点
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(12.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                )
                .clickable { onConnectionStart(node.id) }
        )
    }
}

/**
 * 工作流连接
 */
@Composable
private fun WorkflowConnection(
    connection: WorkflowConnection,
    nodes: List<WorkflowNode>
) {
    val fromNode = nodes.find { it.id == connection.fromNodeId }
    val toNode = nodes.find { it.id == connection.toNodeId }
    
    if (fromNode != null && toNode != null) {
        // 绘制连接线
        // 这里可以使用Canvas绘制连接线
    }
}

/**
 * 工作流属性面板
 */
@Composable
private fun WorkflowPropertyPanel(
    selectedNode: WorkflowNode,
    onPropertyChanged: (String, String, Any) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "属性设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "节点名称",
                style = MaterialTheme.typography.bodyMedium
            )
            
            OutlinedTextField(
                value = selectedNode.name,
                onValueChange = { onPropertyChanged(selectedNode.id, "name", it) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "描述",
                style = MaterialTheme.typography.bodyMedium
            )
            
            OutlinedTextField(
                value = selectedNode.description,
                onValueChange = { onPropertyChanged(selectedNode.id, "description", it) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 根据节点类型显示特定属性
            when (selectedNode.type) {
                NodeType.Trigger -> TriggerProperties(selectedNode, onPropertyChanged)
                NodeType.Action -> ActionProperties(selectedNode, onPropertyChanged)
                NodeType.Condition -> ConditionProperties(selectedNode, onPropertyChanged)
                NodeType.End -> EndProperties(selectedNode, onPropertyChanged)
            }
        }
    }
}

/**
 * 触发器属性
 */
@Composable
private fun TriggerProperties(
    node: WorkflowNode,
    onPropertyChanged: (String, String, Any) -> Unit
) {
    Text(
        text = "触发器设置",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // 触发器类型选择
    val triggerTypes = listOf("时间", "事件", "语音", "手动")
    var selectedTrigger by remember { mutableStateOf("时间") }
    
    triggerTypes.forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedTrigger = type },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedTrigger == type,
                onClick = { selectedTrigger = type }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = type)
        }
    }
}

/**
 * 动作属性
 */
@Composable
private fun ActionProperties(
    node: WorkflowNode,
    onPropertyChanged: (String, String, Any) -> Unit
) {
    Text(
        text = "动作设置",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // 动作类型选择
    val actionTypes = listOf("发送消息", "执行命令", "打开应用", "文件操作")
    var selectedAction by remember { mutableStateOf("发送消息") }
    
    actionTypes.forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedAction = type },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedAction == type,
                onClick = { selectedAction = type }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = type)
        }
    }
}

/**
 * 条件属性
 */
@Composable
private fun ConditionProperties(
    node: WorkflowNode,
    onPropertyChanged: (String, String, Any) -> Unit
) {
    Text(
        text = "条件设置",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // 条件类型选择
    val conditionTypes = listOf("时间条件", "文件条件", "网络条件", "系统条件")
    var selectedCondition by remember { mutableStateOf("时间条件") }
    
    conditionTypes.forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedCondition = type },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedCondition == type,
                onClick = { selectedCondition = type }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = type)
        }
    }
}

/**
 * 结束属性
 */
@Composable
private fun EndProperties(
    node: WorkflowNode,
    onPropertyChanged: (String, String, Any) -> Unit
) {
    Text(
        text = "结束设置",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // 结束类型选择
    val endTypes = listOf("正常结束", "错误结束", "超时结束")
    var selectedEnd by remember { mutableStateOf("正常结束") }
    
    endTypes.forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedEnd = type },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedEnd == type,
                onClick = { selectedEnd = type }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = type)
        }
    }
}

/**
 * 画布网格
 */
@Composable
private fun CanvasGrid() {
    // 绘制网格背景
    // 这里可以使用Canvas绘制网格
}

// 数据类定义
data class WorkflowNode(
    val id: String,
    val name: String,
    val description: String,
    val type: NodeType,
    val position: Pair<Float, Float>,
    val properties: Map<String, Any> = emptyMap()
)

data class WorkflowConnection(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String
)

enum class NodeType(
    val displayName: String,
    val icon: ImageVector
) {
    Trigger("触发器", Icons.Default.PlayArrow),
    Action("动作", Icons.Default.PlayCircle),
    Condition("条件", Icons.Default.QuestionMark),
    End("结束", Icons.Default.Stop)
}

// 辅助函数
private fun createNodeFromType(nodeType: NodeType): WorkflowNode {
    return WorkflowNode(
        id = UUID.randomUUID().toString(),
        name = nodeType.displayName,
        description = "",
        type = nodeType,
        position = 100f to 100f
    )
}

private fun createWorkflowFromNodes(
    nodes: List<WorkflowNode>,
    connections: List<WorkflowConnection>
): AutomationWorkflow {
    val tasks = nodes.map { node ->
        AutomationTask(
            id = node.id,
            name = node.name,
            description = node.description,
            trigger = AutomationTrigger.ManualTrigger(),
            actions = listOf("执行动作"),
            isEnabled = true
        )
    }
    
    return AutomationWorkflow(
        id = UUID.randomUUID().toString(),
        name = "新工作流",
        description = "通过可视化编辑器创建的工作流",
        tasks = tasks,
        isEnabled = true
    )
}