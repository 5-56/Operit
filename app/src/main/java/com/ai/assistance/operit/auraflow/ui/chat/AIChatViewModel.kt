package com.ai.assistance.operit.auraflow.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.auraflow.core.AuraFlowAgentManager
import com.ai.assistance.operit.auraflow.protocol.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天消息类型枚举
 */
enum class ChatMessageType {
    USER_INTENT,        // 用户意图
    AI_THINKING,        // AI思考过程
    AI_COMMAND,         // AI指令
    EXECUTION_RESULT,   // 执行结果
    AI_QUESTION,        // AI提问
    SCREENSHOT,         // 屏幕快照
    STATUS_UPDATE,      // 状态更新
    ERROR               // 错误消息
}

/**
 * 聊天消息数据类
 */
@Serializable
data class ChatMessageData(
    val id: String,
    val type: ChatMessageType,
    val content: String,
    val timestamp: String,
    val isSuccess: Boolean? = null,           // 执行结果是否成功
    val screenshotData: String? = null,       // Base64编码的截图数据
    val questionId: String? = null,           // AI提问的ID
    val questionType: String? = null,         // 问题类型
    val questionOptions: List<String>? = null // 问题选项
)

/**
 * AI聊天UI状态
 */
data class AIChatUiState(
    val currentTaskName: String? = null,
    val isFloatingWindowEnabled: Boolean = false,
    val isWaitingForInput: Boolean = false,
    val inputText: String = "",
    val showScreenshotDialog: Boolean = false,
    val selectedScreenshot: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AI聊天ViewModel
 */
class AIChatViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "AIChatViewModel"
        private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    
    // UI状态
    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()
    
    // 聊天消息列表
    private val _chatMessages = MutableStateFlow<List<ChatMessageData>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageData>> = _chatMessages.asStateFlow()
    
    // Agent状态流（从AuraFlowAgentManager获取）
    val connectionStatus: StateFlow<ConnectionStatus> = flow {
        // TODO: 连接到实际的AuraFlowAgentManager状态
        emit(ConnectionStatus.DISCONNECTED)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionStatus.DISCONNECTED
    )
    
    val agentState: StateFlow<String> = flow {
        // TODO: 连接到实际的Agent状态
        emit("IDLE")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "IDLE"
    )
    
    init {
        setupAIFeedbackListener()
        setupAIQuestionListener()
        
        // 添加欢迎消息
        addMessage(
            type = ChatMessageType.STATUS_UPDATE,
            content = "AuraFlow Agent 已启动，等待连接到AI大脑服务..."
        )
    }
    
    /**
     * 设置AI反馈监听
     */
    private fun setupAIFeedbackListener() {
        viewModelScope.launch {
            // TODO: 监听AuraFlowAgentManager的AI反馈
            /*
            AuraFlowAgentManager.getInstance(context).aiFeedback.collect { feedback ->
                addMessage(
                    type = when (feedback.feedbackType) {
                        FeedbackType.THINKING -> ChatMessageType.AI_THINKING
                        FeedbackType.STATUS_UPDATE -> ChatMessageType.STATUS_UPDATE
                        FeedbackType.COMPLETION -> ChatMessageType.STATUS_UPDATE
                        FeedbackType.WARNING -> ChatMessageType.ERROR
                        else -> ChatMessageType.STATUS_UPDATE
                    },
                    content = feedback.content
                )
            }
            */
        }
    }
    
    /**
     * 设置AI提问监听
     */
    private fun setupAIQuestionListener() {
        viewModelScope.launch {
            // TODO: 监听AuraFlowAgentManager的AI提问
            /*
            AuraFlowAgentManager.getInstance(context).aiQuestions.collect { question ->
                addMessage(
                    type = ChatMessageType.AI_QUESTION,
                    content = question.question,
                    questionId = UUID.randomUUID().toString(),
                    questionType = question.questionType.name,
                    questionOptions = question.options
                )
            }
            */
        }
    }
    
    /**
     * 开始任务
     */
    suspend fun startTask(context: Context) {
        try {
            _uiState.update { it.copy(isLoading = true) }
            
            val agentManager = AuraFlowAgentManager.getInstance(context)
            
            // 检查连接状态
            if (!agentManager.isConnected()) {
                addMessage(
                    type = ChatMessageType.ERROR,
                    content = "未连接到AI大脑服务，请先在配置页面建立连接"
                )
                return
            }
            
            // 开始任务
            agentManager.startTask()
            
            addMessage(
                type = ChatMessageType.STATUS_UPDATE,
                content = "🚀 自动化任务已启动，Agent正在等待AI大脑的指令..."
            )
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    currentTaskName = "自动化任务 ${dateFormat.format(Date())}"
                ) 
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动任务失败", e)
            addMessage(
                type = ChatMessageType.ERROR,
                content = "启动任务失败: ${e.message}"
            )
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * 暂停任务
     */
    fun pauseTask(context: Context) {
        viewModelScope.launch {
            try {
                val agentManager = AuraFlowAgentManager.getInstance(context)
                agentManager.pauseTask()
                
                addMessage(
                    type = ChatMessageType.STATUS_UPDATE,
                    content = "⏸️ 任务已暂停"
                )
            } catch (e: Exception) {
                Log.e(TAG, "暂停任务失败", e)
                addMessage(
                    type = ChatMessageType.ERROR,
                    content = "暂停任务失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 恢复任务
     */
    fun resumeTask(context: Context) {
        viewModelScope.launch {
            try {
                val agentManager = AuraFlowAgentManager.getInstance(context)
                agentManager.resumeTask()
                
                addMessage(
                    type = ChatMessageType.STATUS_UPDATE,
                    content = "▶️ 任务已恢复"
                )
            } catch (e: Exception) {
                Log.e(TAG, "恢复任务失败", e)
                addMessage(
                    type = ChatMessageType.ERROR,
                    content = "恢复任务失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 停止任务
     */
    fun stopTask(context: Context) {
        viewModelScope.launch {
            try {
                val agentManager = AuraFlowAgentManager.getInstance(context)
                agentManager.stopTask()
                
                addMessage(
                    type = ChatMessageType.STATUS_UPDATE,
                    content = "⏹️ 任务已停止"
                )
                
                _uiState.update { it.copy(currentTaskName = null) }
            } catch (e: Exception) {
                Log.e(TAG, "停止任务失败", e)
                addMessage(
                    type = ChatMessageType.ERROR,
                    content = "停止任务失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 切换浮动窗口状态
     */
    fun toggleFloatingWindow() {
        _uiState.update { 
            it.copy(isFloatingWindowEnabled = !it.isFloatingWindowEnabled) 
        }
        
        val status = if (_uiState.value.isFloatingWindowEnabled) "已开启" else "已关闭"
        addMessage(
            type = ChatMessageType.STATUS_UPDATE,
            content = "🪟 浮动控制窗口$status"
        )
    }
    
    /**
     * 回答AI提问
     */
    suspend fun answerQuestion(context: Context, questionId: String, answer: String) {
        try {
            val agentManager = AuraFlowAgentManager.getInstance(context)
            agentManager.answerQuestion(questionId, answer)
            
            addMessage(
                type = ChatMessageType.USER_INTENT,
                content = "回答: $answer"
            )
            
            Log.d(TAG, "用户回答问题: questionId=$questionId, answer=$answer")
        } catch (e: Exception) {
            Log.e(TAG, "回答问题失败", e)
            addMessage(
                type = ChatMessageType.ERROR,
                content = "回答问题失败: ${e.message}"
            )
        }
    }
    
    /**
     * 发送用户输入
     */
    suspend fun sendUserInput(context: Context) {
        val inputText = _uiState.value.inputText
        if (inputText.isBlank()) return
        
        try {
            // 添加用户消息
            addMessage(
                type = ChatMessageType.USER_INTENT,
                content = inputText
            )
            
            // TODO: 发送给AI大脑处理
            // 这里可能需要通过服务器端API或其他方式发送
            
            // 清空输入
            _uiState.update { it.copy(inputText = "", isWaitingForInput = false) }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送用户输入失败", e)
            addMessage(
                type = ChatMessageType.ERROR,
                content = "发送失败: ${e.message}"
            )
        }
    }
    
    /**
     * 更新输入文本
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }
    
    /**
     * 显示截图对话框
     */
    fun showScreenshotDialog(screenshot: String) {
        _uiState.update { 
            it.copy(
                showScreenshotDialog = true,
                selectedScreenshot = screenshot
            ) 
        }
    }
    
    /**
     * 隐藏截图对话框
     */
    fun hideScreenshotDialog() {
        _uiState.update { 
            it.copy(
                showScreenshotDialog = false,
                selectedScreenshot = null
            ) 
        }
    }
    
    /**
     * 模拟AI反馈（用于测试）
     */
    fun simulateAIFeedback() {
        viewModelScope.launch {
            // 模拟用户意图
            addMessage(
                type = ChatMessageType.USER_INTENT,
                content = "帮我打开微信，找到文件传输助手，发送最新照片"
            )
            
            kotlinx.coroutines.delay(1000)
            
            // 模拟AI思考
            addMessage(
                type = ChatMessageType.AI_THINKING,
                content = "AI正在分析当前屏幕和任务需求..."
            )
            
            kotlinx.coroutines.delay(2000)
            
            // 模拟AI指令
            addMessage(
                type = ChatMessageType.AI_COMMAND,
                content = "AI规划：首先点击微信图标启动应用"
            )
            
            kotlinx.coroutines.delay(1500)
            
            // 模拟执行结果
            addMessage(
                type = ChatMessageType.EXECUTION_RESULT,
                content = "✅ 点击操作执行成功，微信应用已启动",
                isSuccess = true
            )
            
            kotlinx.coroutines.delay(1000)
            
            // 模拟截图
            addMessage(
                type = ChatMessageType.SCREENSHOT,
                content = "AI请求查看当前屏幕状态",
                screenshotData = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==" // 简单的1x1像素图片
            )
            
            kotlinx.coroutines.delay(2000)
            
            // 模拟AI提问
            addMessage(
                type = ChatMessageType.AI_QUESTION,
                content = "我看到微信已经打开，是否需要我继续查找文件传输助手？",
                questionId = UUID.randomUUID().toString(),
                questionType = "YES_NO"
            )
        }
    }
    
    /**
     * 清空聊天记录
     */
    fun clearMessages() {
        _chatMessages.value = emptyList()
        addMessage(
            type = ChatMessageType.STATUS_UPDATE,
            content = "聊天记录已清空"
        )
    }
    
    /**
     * 添加消息到聊天列表
     */
    private fun addMessage(
        type: ChatMessageType,
        content: String,
        isSuccess: Boolean? = null,
        screenshotData: String? = null,
        questionId: String? = null,
        questionType: String? = null,
        questionOptions: List<String>? = null
    ) {
        val message = ChatMessageData(
            id = UUID.randomUUID().toString(),
            type = type,
            content = content,
            timestamp = dateFormat.format(Date()),
            isSuccess = isSuccess,
            screenshotData = screenshotData,
            questionId = questionId,
            questionType = questionType,
            questionOptions = questionOptions
        )
        
        _chatMessages.update { currentMessages ->
            currentMessages + message
        }
        
        Log.d(TAG, "新消息: ${type.name} - $content")
    }
    
    /**
     * 处理AI指令执行结果
     */
    fun handleCommandResult(commandId: String, result: ActionResultData) {
        val resultMessage = if (result.success) {
            "✅ 操作执行成功，耗时 ${result.executionTime}ms"
        } else {
            "❌ 操作执行失败: ${result.errorMessage}"
        }
        
        addMessage(
            type = ChatMessageType.EXECUTION_RESULT,
            content = resultMessage,
            isSuccess = result.success
        )
    }
    
    /**
     * 处理屏幕更新
     */
    fun handleScreenUpdate(screenshotData: String?) {
        if (screenshotData != null) {
            addMessage(
                type = ChatMessageType.SCREENSHOT,
                content = "屏幕状态已更新",
                screenshotData = screenshotData
            )
        }
    }
    
    /**
     * 处理连接状态变化
     */
    fun handleConnectionStatusChange(status: ConnectionStatus) {
        val statusMessage = when (status) {
            ConnectionStatus.CONNECTED -> "🟢 已连接到AI大脑服务"
            ConnectionStatus.CONNECTING -> "🟡 正在连接AI大脑服务..."
            ConnectionStatus.RECONNECTING -> "🟡 正在重新连接..."
            ConnectionStatus.DISCONNECTED -> "🔴 已断开AI大脑服务连接"
            ConnectionStatus.ERROR -> "🔴 AI大脑服务连接出错"
        }
        
        addMessage(
            type = ChatMessageType.STATUS_UPDATE,
            content = statusMessage
        )
    }
}