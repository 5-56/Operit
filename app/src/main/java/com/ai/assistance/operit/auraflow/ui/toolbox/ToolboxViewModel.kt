package com.ai.assistance.operit.auraflow.ui.toolbox

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.auraflow.core.AuraFlowAgentManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * UI元素信息数据类
 */
data class UIElementInfo(
    val className: String,
    val packageName: String,
    val resourceId: String,
    val text: String,
    val contentDescription: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isEnabled: Boolean,
    val isSelected: Boolean = false,
    val isScrollable: Boolean = false
)

/**
 * 工具箱UI状态
 */
data class ToolboxUiState(
    val toolboxItems: List<ToolboxItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * UI调试工具状态
 */
data class UIDebugUiState(
    val isAnalyzing: Boolean = false,
    val currentElement: UIElementInfo? = null,
    val uiHierarchy: List<UIElementInfo> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 命令执行器状态
 */
data class CommandExecutorUiState(
    val currentCommand: String = "",
    val commandOutput: String = "",
    val isExecuting: Boolean = false,
    val commandHistory: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 工具箱主ViewModel
 */
class ToolboxViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ToolboxViewModel"
    }
    
    // UI状态
    private val _uiState = MutableStateFlow(ToolboxUiState())
    val uiState: StateFlow<ToolboxUiState> = _uiState.asStateFlow()
    
    init {
        loadToolboxItems()
    }
    
    /**
     * 加载工具箱项目
     */
    private fun loadToolboxItems() {
        val items = listOf(
            ToolboxItem(
                id = "ui_debug",
                title = "UI调试工具",
                description = "分析UI元素结构",
                icon = Icons.Default.Developer,
                enabled = true
            ),
            ToolboxItem(
                id = "command_executor",
                title = "命令执行器",
                description = "执行Shell命令",
                icon = Icons.Default.Terminal,
                enabled = true
            ),
            ToolboxItem(
                id = "log_viewer",
                title = "日志查看器",
                description = "查看运行日志",
                icon = Icons.Default.Assignment,
                enabled = true,
                badge = "新"
            ),
            ToolboxItem(
                id = "file_manager",
                title = "文件管理器",
                description = "管理生成文件",
                icon = Icons.Default.Folder,
                enabled = true
            ),
            ToolboxItem(
                id = "screen_recorder",
                title = "屏幕录制",
                description = "录制操作过程",
                icon = Icons.Default.Videocam,
                enabled = false
            ),
            ToolboxItem(
                id = "accessibility_monitor",
                title = "无障碍监控",
                description = "监控无障碍事件",
                icon = Icons.Default.Accessibility,
                enabled = true
            ),
            ToolboxItem(
                id = "performance_monitor",
                title = "性能监控",
                description = "监控系统性能",
                icon = Icons.Default.Speed,
                enabled = false
            ),
            ToolboxItem(
                id = "screenshot_tool",
                title = "截图工具",
                description = "手动截图功能",
                icon = Icons.Default.Screenshot,
                enabled = true
            )
        )
        
        _uiState.update { it.copy(toolboxItems = items) }
    }
    
    /**
     * 处理工具选择
     */
    suspend fun onToolSelected(context: Context, toolId: String) {
        Log.d(TAG, "选择工具: $toolId")
        
        // 可以在这里进行一些预处理
        when (toolId) {
            "ui_debug" -> {
                // 检查无障碍权限
                // TODO: 检查权限
            }
            "command_executor" -> {
                // 检查Root权限或Shizuku权限
                // TODO: 检查权限
            }
            // 其他工具的预处理...
        }
    }
}

/**
 * UI调试工具ViewModel
 */
class UIDebugViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "UIDebugViewModel"
    }
    
    // UI状态
    private val _uiState = MutableStateFlow(UIDebugUiState())
    val uiState: StateFlow<UIDebugUiState> = _uiState.asStateFlow()
    
    /**
     * 开始UI分析
     */
    suspend fun startAnalysis(context: Context) {
        try {
            _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
            
            // TODO: 启动无障碍服务监控UI变化
            // 这里应该与AccessibilityService交互
            
            Log.d(TAG, "开始UI分析")
        } catch (e: Exception) {
            Log.e(TAG, "启动UI分析失败", e)
            _uiState.update { 
                it.copy(
                    isAnalyzing = false,
                    errorMessage = "启动UI分析失败: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * 停止UI分析
     */
    fun stopAnalysis() {
        _uiState.update { 
            it.copy(
                isAnalyzing = false,
                currentElement = null
            ) 
        }
        Log.d(TAG, "停止UI分析")
    }
    
    /**
     * 更新当前UI元素信息
     */
    fun updateCurrentElement(element: UIElementInfo?) {
        _uiState.update { it.copy(currentElement = element) }
    }
    
    /**
     * 模拟UI元素点击事件（用于测试）
     */
    fun simulateElementClick() {
        val mockElement = UIElementInfo(
            className = "android.widget.Button",
            packageName = "com.example.app",
            resourceId = "com.example.app:id/login_button",
            text = "登录",
            contentDescription = "登录按钮",
            bounds = Rect(100, 200, 300, 280),
            isClickable = true,
            isEditable = false,
            isEnabled = true
        )
        updateCurrentElement(mockElement)
    }
}

/**
 * 命令执行器ViewModel
 */
class CommandExecutorViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "CommandExecutorViewModel"
        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    
    // UI状态
    private val _uiState = MutableStateFlow(CommandExecutorUiState())
    val uiState: StateFlow<CommandExecutorUiState> = _uiState.asStateFlow()
    
    /**
     * 更新当前命令
     */
    fun updateCommand(command: String) {
        _uiState.update { it.copy(currentCommand = command) }
    }
    
    /**
     * 执行命令
     */
    suspend fun executeCommand(context: Context) {
        val command = _uiState.value.currentCommand.trim()
        if (command.isBlank()) return
        
        try {
            _uiState.update { 
                it.copy(
                    isExecuting = true,
                    errorMessage = null
                ) 
            }
            
            // 添加到历史记录
            val newHistory = (_uiState.value.commandHistory + command).takeLast(50)
            
            // 执行命令
            val result = executeShellCommand(command)
            
            // 更新输出
            val timestamp = dateFormat.format(Date())
            val output = buildString {
                if (_uiState.value.commandOutput.isNotBlank()) {
                    append(_uiState.value.commandOutput)
                    append("\n\n")
                }
                append("[$timestamp] $ $command\n")
                append(result)
            }
            
            _uiState.update { 
                it.copy(
                    commandOutput = output,
                    isExecuting = false,
                    commandHistory = newHistory,
                    currentCommand = ""
                ) 
            }
            
            Log.d(TAG, "命令执行完成: $command")
            
        } catch (e: Exception) {
            Log.e(TAG, "命令执行失败", e)
            
            val timestamp = dateFormat.format(Date())
            val errorOutput = buildString {
                if (_uiState.value.commandOutput.isNotBlank()) {
                    append(_uiState.value.commandOutput)
                    append("\n\n")
                }
                append("[$timestamp] $ $command\n")
                append("错误: ${e.message}")
            }
            
            _uiState.update { 
                it.copy(
                    commandOutput = errorOutput,
                    isExecuting = false,
                    errorMessage = "命令执行失败: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * 清空输出
     */
    fun clearOutput() {
        _uiState.update { 
            it.copy(
                commandOutput = "",
                errorMessage = null
            ) 
        }
    }
    
    /**
     * 执行Shell命令（模拟实现）
     */
    private suspend fun executeShellCommand(command: String): String {
        // 模拟延迟
        kotlinx.coroutines.delay(1000)
        
        // 模拟一些常见命令的输出
        return when {
            command.startsWith("pm list packages") -> {
                "package:com.android.chrome\n" +
                "package:com.android.calculator2\n" +
                "package:com.android.settings\n" +
                "package:com.ai.assistance.operit.auraflow\n" +
                "... (更多包名)"
            }
            
            command.startsWith("ps") -> {
                "USER     PID   PPID  VSIZE  RSS   WCHAN    PC        NAME\n" +
                "root     1     0     2048   1024  0        0         init\n" +
                "system   123   1     4096   2048  0        0         system_server\n" +
                "u0_a123  456   123   8192   4096  0        0         com.android.chrome"
            }
            
            command.startsWith("getprop") -> {
                "[ro.build.version.release]: [13]\n" +
                "[ro.build.version.sdk]: [33]\n" +
                "[ro.product.model]: [Pixel 6]\n" +
                "[ro.product.manufacturer]: [Google]"
            }
            
            command.startsWith("dumpsys") -> {
                "DUMP OF SERVICE '$command':\n" +
                "Service status: RUNNING\n" +
                "Memory usage: 1024KB\n" +
                "Active connections: 5"
            }
            
            command == "pwd" -> "/data/local/tmp"
            
            command == "whoami" -> "shell"
            
            command.startsWith("ls") -> {
                "total 8\n" +
                "drwxr-xr-x 2 shell shell 4096 2024-01-01 12:00 Documents\n" +
                "drwxr-xr-x 2 shell shell 4096 2024-01-01 12:00 Downloads\n" +
                "-rw-r--r-- 1 shell shell  123 2024-01-01 12:00 example.txt"
            }
            
            command.startsWith("echo") -> {
                command.substringAfter("echo ").trim().removeSurrounding("\"", "'")
            }
            
            command == "date" -> Date().toString()
            
            else -> {
                "命令 '$command' 的模拟输出\n" +
                "这是一个演示版本，实际执行需要Root权限或Shizuku支持"
            }
        }
    }
    
    /**
     * 从历史记录选择命令
     */
    fun selectFromHistory(command: String) {
        _uiState.update { it.copy(currentCommand = command) }
    }
}

/**
 * 日志查看器ViewModel
 */
class LogViewerViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "LogViewerViewModel"
    }
    
    data class LogViewerUiState(
        val logs: List<LogEntry> = emptyList(),
        val isLoading: Boolean = false,
        val selectedLevel: LogLevel = LogLevel.ALL,
        val selectedTag: String = "ALL",
        val availableTags: List<String> = emptyList(),
        val autoScroll: Boolean = true,
        val errorMessage: String? = null
    )
    
    enum class LogLevel(val displayName: String) {
        ALL("全部"),
        VERBOSE("详细"),
        DEBUG("调试"),
        INFO("信息"),
        WARN("警告"),
        ERROR("错误")
    }
    
    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    )
    
    // UI状态
    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()
    
    init {
        loadLogs()
    }
    
    /**
     * 加载日志
     */
    private fun loadLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // 模拟加载日志
                val mockLogs = generateMockLogs()
                val tags = mockLogs.map { it.tag }.distinct().sorted()
                
                _uiState.update { 
                    it.copy(
                        logs = mockLogs,
                        availableTags = listOf("ALL") + tags,
                        isLoading = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "加载日志失败: ${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 生成模拟日志数据
     */
    private fun generateMockLogs(): List<LogEntry> {
        val currentTime = System.currentTimeMillis()
        return listOf(
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 10000)),
                LogLevel.INFO,
                "AuraFlowAgent",
                "Agent服务已启动"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 9000)),
                LogLevel.DEBUG,
                "WebSocketManager",
                "尝试连接到AI大脑服务: ws://192.168.1.100:8080"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 8000)),
                LogLevel.INFO,
                "WebSocketManager",
                "WebSocket连接已建立"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 7000)),
                LogLevel.DEBUG,
                "ScreenSensor",
                "开始屏幕监控，分辨率: 1080x2340"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 6000)),
                LogLevel.WARN,
                "ActionExecutor",
                "检测到点击操作可能被拦截"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 5000)),
                LogLevel.INFO,
                "AuraFlowAgent",
                "收到AI指令: CLICK(540, 1170)"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 4000)),
                LogLevel.DEBUG,
                "ActionExecutor",
                "执行点击操作，坐标: (540, 1170)"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 3000)),
                LogLevel.INFO,
                "ActionExecutor",
                "点击操作执行成功，耗时: 125ms"
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 2000)),
                LogLevel.ERROR,
                "WebSocketManager",
                "WebSocket连接意外断开，开始重连..."
            ),
            LogEntry(
                SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(currentTime - 1000)),
                LogLevel.INFO,
                "WebSocketManager",
                "WebSocket重连成功"
            )
        )
    }
    
    /**
     * 设置日志级别筛选
     */
    fun setLogLevel(level: LogLevel) {
        _uiState.update { it.copy(selectedLevel = level) }
    }
    
    /**
     * 设置标签筛选
     */
    fun setLogTag(tag: String) {
        _uiState.update { it.copy(selectedTag = tag) }
    }
    
    /**
     * 切换自动滚动
     */
    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }
}