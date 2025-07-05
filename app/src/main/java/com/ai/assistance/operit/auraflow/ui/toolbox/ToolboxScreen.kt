package com.ai.assistance.operit.auraflow.ui.toolbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * 工具箱项目数据类
 */
data class ToolboxItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val badge: String? = null
)

/**
 * 工具箱主界面
 * 提供各种调试和实用工具的集合
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
    onNavigateToTool: (String) -> Unit = {},
    viewModel: ToolboxViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 状态监听
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "工具箱",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "AuraFlow Agent 调试和实用工具集合",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 工具网格
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.toolboxItems) { item ->
                ToolboxItemCard(
                    item = item,
                    onClick = {
                        scope.launch {
                            viewModel.onToolSelected(context, item.id)
                            onNavigateToTool(item.id)
                        }
                    }
                )
            }
        }
    }
}

/**
 * 工具箱项目卡片
 */
@Composable
private fun ToolboxItemCard(
    item: ToolboxItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(enabled = item.enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 图标
                Box {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.size(40.dp),
                        tint = if (item.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                    
                    // 徽章
                    if (item.badge != null) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = item.badge,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 标题
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (item.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 描述
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
            
            // 禁用遮罩
            if (!item.enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "已禁用",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * UI调试工具界面
 */
@Composable
fun UIDebugToolScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: UIDebugViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题和控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UI调试工具",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Switch(
                checked = uiState.isAnalyzing,
                onCheckedChange = {
                    scope.launch {
                        if (it) {
                            viewModel.startAnalysis(context)
                        } else {
                            viewModel.stopAnalysis()
                        }
                    }
                }
            )
        }
        
        Text(
            text = if (uiState.isAnalyzing) "正在实时分析UI元素..." else "点击开关开始UI分析",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // UI层次结构显示
        if (uiState.currentElement != null) {
            UIElementInfoCard(
                element = uiState.currentElement,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (uiState.isAnalyzing) {
            // 等待点击元素
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "点击屏幕上的任意UI元素",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "将显示该元素的详细信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * UI元素信息卡片
 */
@Composable
private fun UIElementInfoCard(
    element: UIElementInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "UI元素信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 基本信息
            InfoRow("类名", element.className)
            InfoRow("包名", element.packageName)
            InfoRow("资源ID", element.resourceId)
            InfoRow("文本", element.text.ifBlank { "无" })
            InfoRow("内容描述", element.contentDescription.ifBlank { "无" })
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            // 位置信息
            InfoRow("坐标", "${element.bounds}")
            InfoRow("可点击", if (element.isClickable) "是" else "否")
            InfoRow("可输入", if (element.isEditable) "是" else "否")
            InfoRow("已启用", if (element.isEnabled) "是" else "否")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* 复制信息到剪贴板 */ }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制")
                }
                
                if (element.isClickable) {
                    Button(
                        onClick = { /* 模拟点击 */ }
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("模拟点击")
                    }
                }
            }
        }
    }
}

/**
 * 信息行组件
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 命令执行器界面
 */
@Composable
fun CommandExecutorScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CommandExecutorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "命令执行器",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "执行ADB Shell命令和Shizuku命令",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 命令输入区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.currentCommand,
                    onValueChange = viewModel::updateCommand,
                    label = { Text("输入命令") },
                    placeholder = { Text("例如: pm list packages") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = !uiState.isExecuting
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.executeCommand(context)
                            }
                        },
                        enabled = uiState.currentCommand.isNotBlank() && !uiState.isExecuting
                    ) {
                        if (uiState.isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("执行中...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("执行")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = viewModel::clearOutput
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清空")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 输出结果
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "输出结果",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    if (uiState.commandOutput.isBlank()) {
                        Text(
                            text = "命令输出将显示在这里...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = uiState.commandOutput,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}