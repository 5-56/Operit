package com.xihe.assistant.core.automation

import android.content.Context
import android.util.Log
import com.xihe.assistant.data.model.AutomationTask
import com.xihe.assistant.data.model.AutomationTrigger
import com.xihe.assistant.data.model.AutomationWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 羲和智能自动化管理器
 * 提供更智能的自动化任务调度和执行能力
 */
class SmartAutomationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SmartAutomationManager"

        @Volatile private var INSTANCE: SmartAutomationManager? = null

        fun getInstance(context: Context): SmartAutomationManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: SmartAutomationManager(context.applicationContext).also { INSTANCE = it }
                }
    }

    fun initialize(context: Context) {
        // 初始化方法，保持与Operit兼容
    }

    // 自动化任务存储
    private val automationTasks = ConcurrentHashMap<String, AutomationTask>()
    private val automationWorkflows = ConcurrentHashMap<String, AutomationWorkflow>()
    
    // 协程作用域
    private val automationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 状态流
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _activeTasks = MutableStateFlow<List<AutomationTask>>(emptyList())
    val activeTasks: StateFlow<List<AutomationTask>> = _activeTasks.asStateFlow()
    
    private val _automationLogs = MutableStateFlow<List<String>>(emptyList())
    val automationLogs: StateFlow<List<String>> = _automationLogs.asStateFlow()

    /**
     * 创建自动化任务
     */
    fun createTask(
        name: String,
        description: String,
        trigger: AutomationTrigger,
        actions: List<String>,
        isEnabled: Boolean = true
    ): AutomationTask {
        val task = AutomationTask(
            id = generateTaskId(),
            name = name,
            description = description,
            trigger = trigger,
            actions = actions,
            isEnabled = isEnabled,
            createdAt = System.currentTimeMillis(),
            lastExecuted = null,
            executionCount = 0
        )
        
        automationTasks[task.id] = task
        updateActiveTasks()
        
        Log.d(TAG, "创建自动化任务: ${task.name}")
        addLog("创建任务: ${task.name}")
        
        return task
    }

    /**
     * 创建自动化工作流
     */
    fun createWorkflow(
        name: String,
        description: String,
        tasks: List<AutomationTask>,
        isEnabled: Boolean = true
    ): AutomationWorkflow {
        val workflow = AutomationWorkflow(
            id = generateWorkflowId(),
            name = name,
            description = description,
            tasks = tasks,
            isEnabled = isEnabled,
            createdAt = System.currentTimeMillis(),
            lastExecuted = null,
            executionCount = 0
        )
        
        automationWorkflows[workflow.id] = workflow
        
        Log.d(TAG, "创建自动化工作流: ${workflow.name}")
        addLog("创建工作流: ${workflow.name}")
        
        return workflow
    }

    /**
     * 启动自动化任务
     */
    fun startTask(taskId: String) {
        val task = automationTasks[taskId] ?: return
        
        if (!task.isEnabled) {
            addLog("任务已禁用: ${task.name}")
            return
        }
        
        automationScope.launch {
            try {
                _isRunning.value = true
                addLog("开始执行任务: ${task.name}")
                
                // 执行任务动作
                for (action in task.actions) {
                    executeAction(action)
                }
                
                // 更新任务状态
                val updatedTask = task.copy(
                    lastExecuted = System.currentTimeMillis(),
                    executionCount = task.executionCount + 1
                )
                automationTasks[taskId] = updatedTask
                updateActiveTasks()
                
                addLog("任务执行完成: ${task.name}")
            } catch (e: Exception) {
                Log.e(TAG, "任务执行失败: ${task.name}", e)
                addLog("任务执行失败: ${task.name} - ${e.message}")
            } finally {
                _isRunning.value = false
            }
        }
    }

    /**
     * 启动自动化工作流
     */
    fun startWorkflow(workflowId: String) {
        val workflow = automationWorkflows[workflowId] ?: return
        
        if (!workflow.isEnabled) {
            addLog("工作流已禁用: ${workflow.name}")
            return
        }
        
        automationScope.launch {
            try {
                _isRunning.value = true
                addLog("开始执行工作流: ${workflow.name}")
                
                // 按顺序执行工作流中的任务
                for (task in workflow.tasks) {
                    if (task.isEnabled) {
                        startTask(task.id)
                        // 等待任务完成
                        kotlinx.coroutines.delay(1000)
                    }
                }
                
                // 更新工作流状态
                val updatedWorkflow = workflow.copy(
                    lastExecuted = System.currentTimeMillis(),
                    executionCount = workflow.executionCount + 1
                )
                automationWorkflows[workflowId] = updatedWorkflow
                
                addLog("工作流执行完成: ${workflow.name}")
            } catch (e: Exception) {
                Log.e(TAG, "工作流执行失败: ${workflow.name}", e)
                addLog("工作流执行失败: ${workflow.name} - ${e.message}")
            } finally {
                _isRunning.value = false
            }
        }
    }

    /**
     * 停止所有自动化任务
     */
    fun stopAllTasks() {
        _isRunning.value = false
        addLog("停止所有自动化任务")
    }

    /**
     * 删除任务
     */
    fun deleteTask(taskId: String) {
        val task = automationTasks.remove(taskId)
        if (task != null) {
            updateActiveTasks()
            addLog("删除任务: ${task.name}")
        }
    }

    /**
     * 删除工作流
     */
    fun deleteWorkflow(workflowId: String) {
        val workflow = automationWorkflows.remove(workflowId)
        if (workflow != null) {
            addLog("删除工作流: ${workflow.name}")
        }
    }

    /**
     * 获取所有任务
     */
    fun getAllTasks(): List<AutomationTask> {
        return automationTasks.values.toList()
    }

    /**
     * 获取所有工作流
     */
    fun getAllWorkflows(): List<AutomationWorkflow> {
        return automationWorkflows.values.toList()
    }

    /**
     * 根据ID获取任务
     */
    fun getTask(taskId: String): AutomationTask? {
        return automationTasks[taskId]
    }

    /**
     * 根据ID获取工作流
     */
    fun getWorkflow(workflowId: String): AutomationWorkflow? {
        return automationWorkflows[workflowId]
    }

    /**
     * 启用/禁用任务
     */
    fun setTaskEnabled(taskId: String, enabled: Boolean) {
        val task = automationTasks[taskId] ?: return
        val updatedTask = task.copy(isEnabled = enabled)
        automationTasks[taskId] = updatedTask
        updateActiveTasks()
        addLog("${if (enabled) "启用" else "禁用"}任务: ${task.name}")
    }

    /**
     * 启用/禁用工作流
     */
    fun setWorkflowEnabled(workflowId: String, enabled: Boolean) {
        val workflow = automationWorkflows[workflowId] ?: return
        val updatedWorkflow = workflow.copy(isEnabled = enabled)
        automationWorkflows[workflowId] = updatedWorkflow
        addLog("${if (enabled) "启用" else "禁用"}工作流: ${workflow.name}")
    }

    /**
     * 清除日志
     */
    fun clearLogs() {
        _automationLogs.value = emptyList()
    }

    /**
     * 执行动作
     */
    private suspend fun executeAction(action: String) {
        try {
            when {
                action.startsWith("send_message:") -> {
                    val message = action.substring("send_message:".length)
                    // 发送消息逻辑
                    addLog("发送消息: $message")
                }
                action.startsWith("open_app:") -> {
                    val appName = action.substring("open_app:".length)
                    // 打开应用逻辑
                    addLog("打开应用: $appName")
                }
                action.startsWith("execute_command:") -> {
                    val command = action.substring("execute_command:".length)
                    // 执行命令逻辑
                    addLog("执行命令: $command")
                }
                action.startsWith("wait:") -> {
                    val delay = action.substring("wait:".length).toLongOrNull() ?: 1000L
                    kotlinx.coroutines.delay(delay)
                    addLog("等待: ${delay}ms")
                }
                else -> {
                    addLog("执行动作: $action")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行动作失败: $action", e)
            addLog("执行动作失败: $action - ${e.message}")
        }
    }

    /**
     * 更新活跃任务列表
     */
    private fun updateActiveTasks() {
        _activeTasks.value = automationTasks.values.filter { it.isEnabled }
    }

    /**
     * 添加日志
     */
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        
        _automationLogs.value = _automationLogs.value + logEntry
        
        // 限制日志数量
        if (_automationLogs.value.size > 1000) {
            _automationLogs.value = _automationLogs.value.drop(100)
        }
    }

    /**
     * 生成任务ID
     */
    private fun generateTaskId(): String {
        return "task_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * 生成工作流ID
     */
    private fun generateWorkflowId(): String {
        return "workflow_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}