package com.ai.assistance.operit.auraflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ai.assistance.operit.auraflow.permission.*
import com.ai.assistance.operit.auraflow.service.FloatingWindowService
import com.ai.assistance.operit.auraflow.ui.chat.AIChatScreen
import com.ai.assistance.operit.auraflow.ui.config.AIBrainConfigScreen
import com.ai.assistance.operit.auraflow.ui.permission.PermissionCheckScreen
import com.ai.assistance.operit.auraflow.ui.toolbox.ToolboxScreen
import com.ai.assistance.operit.auraflow.ui.toolbox.UIDebugToolScreen
import com.ai.assistance.operit.auraflow.ui.toolbox.CommandExecutorScreen
import com.ai.assistance.operit.auraflow.ui.theme.AuraFlowTheme
import kotlinx.coroutines.launch

/**
 * 导航目标枚举
 */
enum class NavigationDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    // 权限检查页面
    PERMISSION_CHECK("permission_check", "权限设置", Icons.Default.Security),
    
    // 主要页面
    CHAT("chat", "AI对话", Icons.Default.Psychology, Icons.Filled.Psychology),
    CONFIG("config", "配置", Icons.Default.Settings, Icons.Filled.Settings),
    TOOLBOX("toolbox", "工具箱", Icons.Default.Construction, Icons.Filled.Construction),
    
    // 工具箱子页面
    UI_DEBUG("ui_debug", "UI调试", Icons.Default.BugReport),
    COMMAND_EXECUTOR("command_executor", "命令执行", Icons.Default.Terminal),
    LOG_VIEWER("log_viewer", "日志查看", Icons.Default.Assignment),
    FILE_MANAGER("file_manager", "文件管理", Icons.Default.Folder)
}

/**
 * AuraFlow Agent 主Activity
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Log.d(TAG, "MainActivity 创建")
        
        // 初始化权限管理器
        val permissionManager = PermissionManager.getInstance()
        permissionManager.initializeInActivity(this)
        
        setContent {
            AuraFlowTheme {
                AuraFlowApp()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity 恢复")
        
        // 重新检查权限状态
        val permissionManager = PermissionManager.getInstance()
        lifecycleScope.launch {
            permissionManager.checkAllPermissions(this@MainActivity)
        }
    }
}

/**
 * 主应用组合函数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraFlowApp() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // 权限状态检查
    val permissionManager = remember { PermissionManager.getInstance() }
    val permissionStates by permissionManager.permissionStates.collectAsStateWithLifecycle()
    
    // 检查是否需要显示权限页面
    val needPermissionSetup = remember(permissionStates) {
        !permissionManager.areAllRequiredPermissionsGranted()
    }
    
    // 根据权限状态决定起始页面
    val startDestination = if (needPermissionSetup) {
        NavigationDestination.PERMISSION_CHECK.route
    } else {
        NavigationDestination.CHAT.route
    }
    
    // 主界面导航项
    val mainDestinations = listOf(
        NavigationDestination.CHAT,
        NavigationDestination.CONFIG,
        NavigationDestination.TOOLBOX
    )
    
    // 当前导航状态
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // 是否显示底部导航栏
    val showBottomBar = currentRoute !in listOf(
        NavigationDestination.PERMISSION_CHECK.route
    )
    
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    
                    mainDestinations.forEach { destination ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (currentDestination?.hierarchy?.any { it.route == destination.route } == true) {
                                        destination.selectedIcon
                                    } else {
                                        destination.icon
                                    },
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // 避免构建大量回退栈
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // 避免多个相同目标的副本
                                    launchSingleTop = true
                                    // 重新选择之前选择的项目时恢复状态
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 权限检查页面
            composable(NavigationDestination.PERMISSION_CHECK.route) {
                PermissionCheckScreen(
                    onPermissionComplete = {
                        // 权限设置完成，跳转到主页
                        navController.navigate(NavigationDestination.CHAT.route) {
                            popUpTo(NavigationDestination.PERMISSION_CHECK.route) {
                                inclusive = true
                            }
                        }
                    },
                    onSkip = {
                        // 跳过权限设置，也跳转到主页（但可能功能受限）
                        navController.navigate(NavigationDestination.CHAT.route) {
                            popUpTo(NavigationDestination.PERMISSION_CHECK.route) {
                                inclusive = true
                            }
                        }
                    }
                )
            }
            
            // AI对话页面
            composable(NavigationDestination.CHAT.route) {
                AIChatScreen(
                    onOpenFloatingWindow = {
                        // 打开浮动窗口
                        scope.launch {
                            openFloatingWindow(context)
                        }
                    }
                )
            }
            
            // AI配置页面
            composable(NavigationDestination.CONFIG.route) {
                AIBrainConfigScreen()
            }
            
            // 工具箱页面
            composable(NavigationDestination.TOOLBOX.route) {
                ToolboxScreen(
                    onNavigateToTool = { toolId ->
                        navController.navigate(toolId)
                    }
                )
            }
            
            // UI调试工具
            composable(NavigationDestination.UI_DEBUG.route) {
                UIDebugToolScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // 命令执行器
            composable(NavigationDestination.COMMAND_EXECUTOR.route) {
                CommandExecutorScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // 日志查看器
            composable(NavigationDestination.LOG_VIEWER.route) {
                LogViewerScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            // 文件管理器
            composable(NavigationDestination.FILE_MANAGER.route) {
                FileManagerScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

/**
 * 打开浮动窗口
 */
private suspend fun openFloatingWindow(context: android.content.Context) {
    try {
        Log.d("MainActivity", "尝试打开浮动窗口")
        
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(context)) {
            Log.w("MainActivity", "没有悬浮窗权限，跳转到权限设置")
            
            // 跳转到权限设置页面
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        
        // 启动浮动窗口服务
        FloatingWindowService.start(context)
        Log.d("MainActivity", "浮动窗口服务启动请求已发送")
        
    } catch (e: Exception) {
        Log.e("MainActivity", "打开浮动窗口失败", e)
        // 可以显示Toast提示用户
        try {
            val activity = context as? ComponentActivity
            activity?.runOnUiThread {
                // 这里可以显示Snackbar或Toast
                Log.e("MainActivity", "浮动窗口启动失败: ${e.message}")
            }
        } catch (toastException: Exception) {
            Log.e("MainActivity", "显示错误提示失败", toastException)
        }
    }
}

/**
 * 日志查看器界面
 */
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题和控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "日志查看器",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            Row {
                // 清空按钮
                IconButton(
                    onClick = { viewModel.clearLogs() }
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "清空日志")
                }
                
                // 自动滚动开关
                IconButton(
                    onClick = { viewModel.toggleAutoScroll() }
                ) {
                    Icon(
                        if (uiState.autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.PauseCircle,
                        contentDescription = if (uiState.autoScroll) "停止自动滚动" else "开启自动滚动"
                    )
                }
            }
        }
        
        // 筛选控制
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 日志级别筛选
            FilterChip(
                onClick = { /* 显示级别选择对话框 */ },
                label = { Text(uiState.selectedLevel.displayName) },
                selected = uiState.selectedLevel != com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel.LogLevel.ALL,
                leadingIcon = {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                }
            )
            
            // 标签筛选
            FilterChip(
                onClick = { /* 显示标签选择对话框 */ },
                label = { Text(uiState.selectedTag) },
                selected = uiState.selectedTag != "ALL",
                leadingIcon = {
                    Icon(Icons.Default.Label, contentDescription = null)
                }
            )
        }
        
        // 日志列表
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.logs.size) { index ->
                        val log = uiState.logs[index]
                        LogEntryItem(log = log)
                    }
                }
            }
        }
    }
}

/**
 * 日志条目组件
 */
@Composable
private fun LogEntryItem(
    log: com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel.LogEntry
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 时间戳
        Text(
            text = log.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        
        // 级别标签
        androidx.compose.foundation.background(
            color = when (log.level) {
                com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel.LogLevel.WARN -> androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.3f)
                com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel.LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
                com.ai.assistance.operit.auraflow.ui.toolbox.LogViewerViewModel.LogLevel.DEBUG -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
        )
        Text(
            text = log.level.displayName.take(1),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .width(20.dp)
                .padding(2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        // 标签
        Text(
            text = log.tag,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.width(120.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        
        // 消息内容
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 文件管理器界面
 */
@Composable
fun FileManagerScreen(
    onNavigateBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "文件管理器",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 文件列表占位
        Card(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "文件管理器功能开发中...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "将用于管理Agent生成的截图、日志等文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}