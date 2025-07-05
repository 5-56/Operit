package com.ai.assistance.operit.examples

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.agent.*
import com.ai.assistance.operit.util.LogUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * AI Agent 集成示例
 * 展示如何在FloatingChatService中集成和使用新的AI Agent功能
 */

/**
 * 增强的浮动聊天视图模型 - 集成AI Agent
 */
class EnhancedFloatingChatViewModel(
    private val context: Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "EnhancedFloatingChatViewModel"
    }
    
    // AI Agent 核心控制器
    private val aiAgent = OperitAIAgentController.getInstance(context)
    
    // UI状态
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Agent状态监听
    init {
        // 监听Agent状态变化
        viewModelScope.launch {
            aiAgent.agentState.collect { state ->
                updateAgentState(state)
            }
        }
        
        // 监听任务进度
        viewModelScope.launch {
            aiAgent.taskProgress.collect { progress ->
                updateTaskProgress(progress)
            }
        }
        
        // 监听AI思考过程
        viewModelScope.launch {
            aiAgent.aiThinking.collect { thinking ->
                updateAIThinking(thinking)
            }
        }
    }
    
    /**
     * 聊天UI状态
     */
    data class ChatUiState(
        val isAgentActive: Boolean = false,
        val currentTask: String? = null,
        val taskProgress: Float = 0f,
        val agentStatus: String = "待机中",
        val aiThinking: String? = null,
        val confidence: Float = 0f,
        val messages: List<ChatMessage> = emptyList(),
        val isLoading: Boolean = false
    )
    
    /**
     * 聊天消息
     */
    data class ChatMessage(
        val id: String,
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val type: MessageType = MessageType.TEXT
    ) {
        enum class MessageType { TEXT, AGENT_STATUS, AI_THINKING, TASK_RESULT }
    }
    
    /**
     * 处理用户消息 - 集成AI Agent
     */
    fun handleUserMessage(message: String) {
        LogUtils.d(TAG, "处理用户消息: $message")
        
        // 添加用户消息到聊天记录
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = message,
            isUser = true
        ))
        
        // 检查是否是Agent命令
        when {
            message.startsWith("/agent ") -> {
                val intent = message.removePrefix("/agent ")
                executeAgentTask(intent)
            }
            message.startsWith("/stop") -> {
                stopAgent()
            }
            message.startsWith("/pause") -> {
                pauseAgent()
            }
            message.startsWith("/resume") -> {
                resumeAgent()
            }
            else -> {
                // 普通聊天消息，可以考虑是否触发Agent
                if (shouldTriggerAgent(message)) {
                    executeAgentTask(message)
                } else {
                    // 处理普通聊天
                    handleRegularChat(message)
                }
            }
        }
    }
    
    /**
     * 执行Agent任务
     */
    private fun executeAgentTask(intent: String) {
        if (aiAgent.isBusy()) {
            addMessage(ChatMessage(
                id = generateMessageId(),
                content = "AI Agent正在执行其他任务，请稍候...",
                isUser = false,
                type = ChatMessage.MessageType.AGENT_STATUS
            ))
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isAgentActive = true,
                    currentTask = intent,
                    isLoading = true
                )
                
                addMessage(ChatMessage(
                    id = generateMessageId(),
                    content = "🤖 AI Agent开始执行任务: $intent",
                    isUser = false,
                    type = ChatMessage.MessageType.AGENT_STATUS
                ))
                
                // 创建用户意图
                val userIntent = OperitAIAgentController.UserIntent(
                    description = intent,
                    priority = OperitAIAgentController.UserIntent.Priority.NORMAL
                )
                
                // 执行任务
                val result = aiAgent.executeUserIntent(userIntent)
                
                // 显示结果
                displayTaskResult(result)
                
            } catch (e: Exception) {
                LogUtils.e(TAG, "Agent任务执行失败", e)
                addMessage(ChatMessage(
                    id = generateMessageId(),
                    content = "❌ 任务执行失败: ${e.message}",
                    isUser = false,
                    type = ChatMessage.MessageType.TASK_RESULT
                ))
            } finally {
                _uiState.value = _uiState.value.copy(
                    isAgentActive = false,
                    isLoading = false,
                    currentTask = null
                )
            }
        }
    }
    
    /**
     * 显示任务结果
     */
    private fun displayTaskResult(result: OperitAIAgentController.TaskResult) {
        val emoji = if (result.success) "✅" else "❌"
        val status = if (result.success) "成功" else "失败"
        
        val resultMessage = buildString {
            appendLine("$emoji 任务执行$status")
            appendLine("执行步骤: ${result.executedSteps.size}")
            appendLine("耗时: ${result.duration / 1000.0}秒")
            if (result.error != null) {
                appendLine("错误: ${result.error}")
            }
            appendLine("结果: ${result.result}")
        }
        
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = resultMessage,
            isUser = false,
            type = ChatMessage.MessageType.TASK_RESULT
        ))
    }
    
    /**
     * 更新Agent状态
     */
    private fun updateAgentState(state: OperitAIAgentController.AgentState) {
        val statusText = when (state) {
            is OperitAIAgentController.AgentState.Idle -> "待机中"
            is OperitAIAgentController.AgentState.PerceivingScreen -> "感知屏幕中..."
            is OperitAIAgentController.AgentState.CommunicatingWithAI -> "与AI大脑通信中..."
            is OperitAIAgentController.AgentState.ExecutingInstructions -> "执行指令中..."
            is OperitAIAgentController.AgentState.WaitingForFeedback -> "等待反馈中..."
            is OperitAIAgentController.AgentState.TaskCompleted -> "任务完成"
            is OperitAIAgentController.AgentState.Error -> "错误: ${state.error}"
        }
        
        _uiState.value = _uiState.value.copy(agentStatus = statusText)
        
        // 在状态变化时添加状态消息
        if (state !is OperitAIAgentController.AgentState.Idle) {
            addMessage(ChatMessage(
                id = generateMessageId(),
                content = "🔄 $statusText",
                isUser = false,
                type = ChatMessage.MessageType.AGENT_STATUS
            ))
        }
    }
    
    /**
     * 更新任务进度
     */
    private fun updateTaskProgress(progress: OperitAIAgentController.TaskProgress) {
        _uiState.value = _uiState.value.copy(
            taskProgress = progress.progress
        )
        
        // 显示进度更新
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = "📊 ${progress.currentStep} (${progress.completedSteps}/${progress.totalSteps})",
            isUser = false,
            type = ChatMessage.MessageType.AGENT_STATUS
        ))
    }
    
    /**
     * 更新AI思考过程
     */
    private fun updateAIThinking(thinking: OperitAIAgentController.AIThinkingProcess) {
        _uiState.value = _uiState.value.copy(
            aiThinking = thinking.reasoning,
            confidence = thinking.confidence
        )
        
        // 显示AI思考过程
        val thinkingMessage = buildString {
            appendLine("🧠 AI思考过程:")
            appendLine("步骤: ${thinking.step}")
            appendLine("推理: ${thinking.reasoning}")
            appendLine("置信度: ${(thinking.confidence * 100).toInt()}%")
            thinking.nextAction?.let { 
                appendLine("下一步: $it")
            }
        }
        
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = thinkingMessage,
            isUser = false,
            type = ChatMessage.MessageType.AI_THINKING
        ))
    }
    
    /**
     * 停止Agent
     */
    private fun stopAgent() {
        aiAgent.stopAgent()
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = "⏹️ AI Agent已停止",
            isUser = false,
            type = ChatMessage.MessageType.AGENT_STATUS
        ))
    }
    
    /**
     * 暂停Agent
     */
    private fun pauseAgent() {
        aiAgent.pauseAgent()
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = "⏸️ AI Agent已暂停",
            isUser = false,
            type = ChatMessage.MessageType.AGENT_STATUS
        ))
    }
    
    /**
     * 恢复Agent
     */
    private fun resumeAgent() {
        aiAgent.resumeAgent()
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = "▶️ AI Agent已恢复",
            isUser = false,
            type = ChatMessage.MessageType.AGENT_STATUS
        ))
    }
    
    /**
     * 判断是否应该触发Agent
     */
    private fun shouldTriggerAgent(message: String): Boolean {
        val agentTriggerKeywords = listOf(
            "帮我", "自动", "打开", "点击", "滑动", "输入", "搜索", 
            "下载", "安装", "设置", "操作", "执行"
        )
        
        return agentTriggerKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 处理普通聊天
     */
    private fun handleRegularChat(message: String) {
        // 这里可以集成原有的聊天AI逻辑
        addMessage(ChatMessage(
            id = generateMessageId(),
            content = "我是Operit AI Assistant。您可以使用 \"/agent [任务描述]\" 来启动AI Agent执行自动化任务。",
            isUser = false
        ))
    }
    
    /**
     * 添加消息到聊天记录
     */
    private fun addMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(message)
        
        // 限制消息数量，保持性能
        if (currentMessages.size > 100) {
            currentMessages.removeAt(0)
        }
        
        _uiState.value = _uiState.value.copy(messages = currentMessages)
    }
    
    /**
     * 清除聊天记录
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
    
    /**
     * 获取Agent状态信息
     */
    fun getAgentStatusInfo(): String {
        return buildString {
            appendLine("🤖 AI Agent 状态信息")
            appendLine("状态: ${_uiState.value.agentStatus}")
            appendLine("活跃: ${if (_uiState.value.isAgentActive) "是" else "否"}")
            _uiState.value.currentTask?.let {
                appendLine("当前任务: $it")
                appendLine("进度: ${(_uiState.value.taskProgress * 100).toInt()}%")
            }
            _uiState.value.aiThinking?.let {
                appendLine("AI思考: $it")
                appendLine("置信度: ${(_uiState.value.confidence * 100).toInt()}%")
            }
        }
    }
    
    private fun generateMessageId(): String = "msg_${System.currentTimeMillis()}_${(0..999).random()}"
    
    override fun onCleared() {
        super.onCleared()
        // 清理资源
        if (aiAgent.isBusy()) {
            aiAgent.stopAgent()
        }
    }
}

/**
 * AI Agent 使用示例
 */
class AIAgentUsageExample {
    
    /**
     * 示例1: 基本任务执行
     */
    suspend fun executeBasicTask(context: Context) {
        val agent = OperitAIAgentController.getInstance(context)
        
        val intent = OperitAIAgentController.UserIntent(
            description = "打开设置应用并进入WiFi设置",
            priority = OperitAIAgentController.UserIntent.Priority.NORMAL
        )
        
        val result = agent.executeUserIntent(intent)
        LogUtils.i("AIAgentExample", "任务结果: ${result.success}")
    }
    
    /**
     * 示例2: 复杂任务执行
     */
    suspend fun executeComplexTask(context: Context) {
        val agent = OperitAIAgentController.getInstance(context)
        
        val intent = OperitAIAgentController.UserIntent(
            description = "在应用商店搜索并下载微信应用",
            priority = OperitAIAgentController.UserIntent.Priority.HIGH,
            constraints = listOf("不要自动安装", "只下载不要打开")
        )
        
        val result = agent.executeUserIntent(intent)
        LogUtils.i("AIAgentExample", "复杂任务结果: ${result.success}")
    }
    
    /**
     * 示例3: 监听Agent状态
     */
    fun monitorAgentState(context: Context) {
        val agent = OperitAIAgentController.getInstance(context)
        
        // 在协程中监听状态
        // launch {
        //     agent.agentState.collect { state ->
        //         when (state) {
        //             is OperitAIAgentController.AgentState.Idle -> {
        //                 LogUtils.d("AIAgentExample", "Agent空闲")
        //             }
        //             is OperitAIAgentController.AgentState.ExecutingInstructions -> {
        //                 LogUtils.d("AIAgentExample", "Agent正在执行指令")
        //             }
        //             is OperitAIAgentController.AgentState.TaskCompleted -> {
        //                 LogUtils.d("AIAgentExample", "任务完成: ${state.result}")
        //             }
        //             else -> {
        //                 LogUtils.d("AIAgentExample", "Agent状态: $state")
        //             }
        //         }
        //     }
        // }
    }
}

/**
 * 浮动窗口集成示例
 */
object FloatingWindowIntegration {
    
    /**
     * 在浮动窗口中集成AI Agent功能
     */
    fun integrateWithFloatingWindow(context: Context): EnhancedFloatingChatViewModel {
        return EnhancedFloatingChatViewModel(context)
    }
    
    /**
     * 快速任务执行按钮
     */
    fun createQuickTaskButtons(): List<QuickTask> {
        return listOf(
            QuickTask("📱", "打开设置", "/agent 打开系统设置"),
            QuickTask("📞", "拨打电话", "/agent 打开拨号界面"),
            QuickTask("📷", "拍照", "/agent 打开相机应用"),
            QuickTask("🌐", "浏览网页", "/agent 打开浏览器"),
            QuickTask("💬", "发消息", "/agent 打开微信"),
            QuickTask("🎵", "播放音乐", "/agent 打开音乐播放器"),
            QuickTask("⏰", "设置闹钟", "/agent 设置明天8点闹钟"),
            QuickTask("🔍", "搜索", "/agent 在应用商店搜索抖音")
        )
    }
    
    data class QuickTask(
        val icon: String,
        val name: String,
        val command: String
    )
}

/**
 * 权限集成示例
 */
object PermissionIntegration {
    
    /**
     * 检查必要权限
     */
    fun checkRequiredPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        // 检查无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            missingPermissions.add("无障碍服务")
        }
        
        // 检查悬浮窗权限
        if (!hasOverlayPermission(context)) {
            missingPermissions.add("悬浮窗权限")
        }
        
        // 检查设备管理权限
        if (!hasDeviceAdminPermission()) {
            missingPermissions.add("设备管理权限")
        }
        
        return missingPermissions
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        // TODO: 实现实际检查逻辑
        return false
    }
    
    private fun hasOverlayPermission(context: Context): Boolean {
        // TODO: 实现实际检查逻辑
        return false
    }
    
    private fun hasDeviceAdminPermission(): Boolean {
        // TODO: 实现实际检查逻辑
        return false
    }
}