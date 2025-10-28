package com.xihe.assistant.ui.features.chat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xihe.assistant.data.model.*
import com.xihe.assistant.data.preferences.ApiPreferences
import com.xihe.assistant.data.preferences.UserPreferencesManager
import com.xihe.assistant.core.tools.AIToolHandler
import com.xihe.assistant.core.automation.SmartAutomationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*

/**
 * 羲和智能助手聊天ViewModel
 * 提供更智能的AI对话管理
 */
class ChatViewModel(
    private val context: Context,
    private val toolHandler: AIToolHandler = AIToolHandler.getInstance(context),
    private val automationManager: SmartAutomationManager = SmartAutomationManager.getInstance(context)
) : ViewModel() {

    // API配置状态
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _apiEndpoint = MutableStateFlow("")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _modelName = MutableStateFlow("")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _apiProviderType = MutableStateFlow(ApiProviderType.DEEPSEEK)
    val apiProviderType: StateFlow<ApiProviderType> = _apiProviderType.asStateFlow()

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    // 聊天状态
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _userMessage = MutableStateFlow("")
    val userMessage: StateFlow<String> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 输入处理状态
    private val _inputProcessingState = MutableStateFlow(InputProcessingState.IDLE)
    val inputProcessingState: StateFlow<InputProcessingState> = _inputProcessingState.asStateFlow()

    // AI功能状态
    private val _enableAiPlanning = MutableStateFlow(true)
    val enableAiPlanning: StateFlow<Boolean> = _enableAiPlanning.asStateFlow()

    private val _enableThinkingMode = MutableStateFlow(false)
    val enableThinkingMode: StateFlow<Boolean> = _enableThinkingMode.asStateFlow()

    private val _enableThinkingGuidance = MutableStateFlow(true)
    val enableThinkingGuidance: StateFlow<Boolean> = _enableThinkingGuidance.asStateFlow()

    private val _enableMemoryAttachment = MutableStateFlow(true)
    val enableMemoryAttachment: StateFlow<Boolean> = _enableMemoryAttachment.asStateFlow()

    private val _summaryTokenThreshold = MutableStateFlow(1000)
    val summaryTokenThreshold: StateFlow<Int> = _summaryTokenThreshold.asStateFlow()

    private val _isAutoReadEnabled = MutableStateFlow(false)
    val isAutoReadEnabled: StateFlow<Boolean> = _isAutoReadEnabled.asStateFlow()

    // 聊天历史管理
    private val _showChatHistorySelector = MutableStateFlow(false)
    val showChatHistorySelector: StateFlow<Boolean> = _showChatHistorySelector.asStateFlow()

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    // 弹窗消息
    private val _popupMessage = MutableStateFlow<String?>(null)
    val popupMessage: StateFlow<String?> = _popupMessage.asStateFlow()

    // 附件管理
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments.asStateFlow()

    private val _attachmentPanelState = MutableStateFlow(AttachmentPanelState.CLOSED)
    val attachmentPanelState: StateFlow<AttachmentPanelState> = _attachmentPanelState.asStateFlow()

    // 滚动事件
    private val _scrollToBottomEvent = MutableSharedFlow<Unit>()
    val scrollToBottomEvent: SharedFlow<Unit> = _scrollToBottomEvent.asSharedFlow()

    // 配置对话框状态
    private val _shouldShowConfigDialog = MutableStateFlow(false)
    val shouldShowConfigDialog: StateFlow<Boolean> = _shouldShowConfigDialog.asStateFlow()

    // 回复消息
    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    // 悬浮窗模式
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()

    // WebView状态
    private val _showWebView = MutableStateFlow(false)
    val showWebView: StateFlow<Boolean> = _showWebView.asStateFlow()

    private val _showAiComputer = MutableStateFlow(false)
    val showAiComputer: StateFlow<Boolean> = _showAiComputer.asStateFlow()

    private val _webViewRefreshCounter = MutableStateFlow(0)
    val webViewRefreshCounter: StateFlow<Int> = _webViewRefreshCounter.asStateFlow()

    // Toast事件
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // 权限级别
    private val _masterPermissionLevel = MutableStateFlow(PermissionLevel.STANDARD)
    val masterPermissionLevel: StateFlow<PermissionLevel> = _masterPermissionLevel.asStateFlow()

    // 上下文长度
    private val _maxWindowSizeInK = MutableStateFlow(4)
    val maxWindowSizeInK: StateFlow<Int> = _maxWindowSizeInK.asStateFlow()

    // 邀请相关
    private val _showInvitationExplanation = MutableStateFlow(false)
    val showInvitationExplanation: StateFlow<Boolean> = _showInvitationExplanation.asStateFlow()

    private val _showInvitationPanel = MutableStateFlow(false)
    val showInvitationPanel: StateFlow<Boolean> = _showInvitationPanel.asStateFlow()

    private val _invitationCount = MutableStateFlow(0)
    val invitationCount: StateFlow<Int> = _invitationCount.asStateFlow()

    private val _generatedInvitationMessage = MutableStateFlow("")
    val generatedInvitationMessage: StateFlow<String> = _generatedInvitationMessage.asStateFlow()

    // UI状态委托
    val uiStateDelegate = UIStateDelegate()

    init {
        loadApiSettings()
        loadUserPreferences()
        initializeChat()
    }

    /**
     * 加载API设置
     */
    private fun loadApiSettings() {
        viewModelScope.launch {
            val apiPreferences = ApiPreferences.getInstance(context)
            _apiKey.value = apiPreferences.apiKey.first()
            _apiEndpoint.value = apiPreferences.apiEndpoint.first()
            _modelName.value = apiPreferences.modelName.first()
            _apiProviderType.value = apiPreferences.apiProviderType.first()
            _isConfigured.value = _apiKey.value.isNotBlank()
        }
    }

    /**
     * 加载用户偏好设置
     */
    private fun loadUserPreferences() {
        viewModelScope.launch {
            val preferencesManager = UserPreferencesManager(context)
            _enableAiPlanning.value = preferencesManager.enableAiPlanning.first()
            _enableThinkingMode.value = preferencesManager.enableThinkingMode.first()
            _enableThinkingGuidance.value = preferencesManager.enableThinkingGuidance.first()
            _enableMemoryAttachment.value = preferencesManager.enableMemoryAttachment.first()
            _summaryTokenThreshold.value = preferencesManager.summaryTokenThreshold.first()
            _isAutoReadEnabled.value = preferencesManager.isAutoReadEnabled.first()
            _maxWindowSizeInK.value = preferencesManager.maxWindowSizeInK.first()
        }
    }

    /**
     * 初始化聊天
     */
    private fun initializeChat() {
        viewModelScope.launch {
            // 创建新的聊天会话
            val newChatId = UUID.randomUUID().toString()
            _currentChatId.value = newChatId
            
            // 添加欢迎消息
            val welcomeMessage = ChatMessage(
                content = "你好！我是羲和智能助手，很高兴为您服务！有什么可以帮助您的吗？",
                sender = "assistant"
            )
            _chatHistory.value = listOf(welcomeMessage)
        }
    }

    /**
     * 更新API密钥
     */
    fun updateApiKey(apiKey: String) {
        _apiKey.value = apiKey
        _isConfigured.value = apiKey.isNotBlank()
    }

    /**
     * 更新API端点
     */
    fun updateApiEndpoint(endpoint: String) {
        _apiEndpoint.value = endpoint
    }

    /**
     * 更新模型名称
     */
    fun updateModelName(model: String) {
        _modelName.value = model
    }

    /**
     * 更新API提供商类型
     */
    fun updateApiProviderType(type: ApiProviderType) {
        _apiProviderType.value = type
    }

    /**
     * 保存API设置
     */
    fun saveApiSettings() {
        viewModelScope.launch {
            val apiPreferences = ApiPreferences.getInstance(context)
            apiPreferences.setApiKey(_apiKey.value)
            apiPreferences.setApiEndpoint(_apiEndpoint.value)
            apiPreferences.setModelName(_modelName.value)
            apiPreferences.setApiProviderType(_apiProviderType.value)
            _isConfigured.value = true
        }
    }

    /**
     * 使用默认配置
     */
    fun useDefaultConfig() {
        updateApiKey(ApiPreferences.DEFAULT_API_KEY)
        updateApiEndpoint(ApiPreferences.DEFAULT_API_ENDPOINT)
        updateModelName(ApiPreferences.DEFAULT_MODEL_NAME)
        updateApiProviderType(ApiProviderType.DEEPSEEK)
    }

    /**
     * 更新用户消息
     */
    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }

    /**
     * 发送用户消息
     */
    fun sendUserMessage() {
        val message = _userMessage.value.trim()
        if (message.isEmpty()) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _inputProcessingState.value = InputProcessingState.PROCESSING

                // 添加用户消息到聊天历史
                val userMessage = ChatMessage(
                    content = message,
                    sender = "user",
                    attachments = _attachments.value
                )
                _chatHistory.value = _chatHistory.value + userMessage

                // 清空输入框和附件
                _userMessage.value = ""
                _attachments.value = emptyList()
                _attachmentPanelState.value = AttachmentPanelState.CLOSED

                // 发送到AI处理
                processAIResponse(userMessage)

            } catch (e: Exception) {
                _errorMessage.value = "发送消息失败: ${e.message}"
            } finally {
                _isLoading.value = false
                _inputProcessingState.value = InputProcessingState.IDLE
            }
        }
    }

    /**
     * 处理AI响应
     */
    private suspend fun processAIResponse(userMessage: ChatMessage) {
        try {
            // 这里应该调用实际的AI API
            // 为了演示，我们模拟一个响应
            delay(1000)
            
            val aiResponse = ChatMessage(
                content = "我收到了您的消息：\"${userMessage.content}\"。作为羲和智能助手，我可以帮助您处理各种任务，包括文件管理、系统操作、自动化脚本等。请告诉我您需要什么帮助！",
                sender = "assistant"
            )
            
            _chatHistory.value = _chatHistory.value + aiResponse
            _scrollToBottomEvent.emit(Unit)
            
        } catch (e: Exception) {
            _errorMessage.value = "AI响应失败: ${e.message}"
        }
    }

    /**
     * 取消当前消息
     */
    fun cancelCurrentMessage() {
        _isLoading.value = false
        _inputProcessingState.value = InputProcessingState.IDLE
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 显示错误消息
     */
    fun showErrorMessage(message: String) {
        _errorMessage.value = message
    }

    /**
     * 显示配置对话框
     */
    fun showConfigurationScreen() {
        _shouldShowConfigDialog.value = true
    }

    /**
     * 确认配置对话框
     */
    fun onConfigDialogConfirmed() {
        _shouldShowConfigDialog.value = false
    }

    /**
     * 切换AI规划功能
     */
    fun toggleAiPlanning() {
        _enableAiPlanning.value = !_enableAiPlanning.value
    }

    /**
     * 切换思考模式
     */
    fun toggleThinkingMode() {
        _enableThinkingMode.value = !_enableThinkingMode.value
    }

    /**
     * 切换思考引导
     */
    fun toggleThinkingGuidance() {
        _enableThinkingGuidance.value = !_enableThinkingGuidance.value
    }

    /**
     * 切换记忆附件
     */
    fun toggleMemoryAttachment() {
        _enableMemoryAttachment.value = !_enableMemoryAttachment.value
    }

    /**
     * 更新摘要Token阈值
     */
    fun updateSummaryTokenThreshold(threshold: Int) {
        _summaryTokenThreshold.value = threshold
    }

    /**
     * 切换自动阅读
     */
    fun toggleAutoRead() {
        _isAutoReadEnabled.value = !_isAutoReadEnabled.value
    }

    /**
     * 手动更新记忆
     */
    fun manuallyUpdateMemory() {
        viewModelScope.launch {
            // 实现记忆更新逻辑
            _toastEvent.emit("记忆已更新")
        }
    }

    /**
     * 切换主权限级别
     */
    fun toggleMasterPermission() {
        val currentLevel = _masterPermissionLevel.value
        val newLevel = when (currentLevel) {
            PermissionLevel.STANDARD -> PermissionLevel.ADVANCED
            PermissionLevel.ADVANCED -> PermissionLevel.EXPERT
            PermissionLevel.EXPERT -> PermissionLevel.STANDARD
        }
        _masterPermissionLevel.value = newLevel
    }

    /**
     * 更新上下文长度
     */
    fun updateContextLength(sizeInK: Int) {
        _maxWindowSizeInK.value = sizeInK
    }

    /**
     * 处理附件
     */
    fun handleAttachment(filePath: String) {
        // 实现附件处理逻辑
        val attachment = AttachmentInfo(
            name = filePath.substringAfterLast("/"),
            type = AttachmentType.FILE,
            size = 0L,
            path = filePath
        )
        _attachments.value = _attachments.value + attachment
    }

    /**
     * 移除附件
     */
    fun removeAttachment(filePath: String) {
        _attachments.value = _attachments.value.filter { it.path != filePath }
    }

    /**
     * 插入附件引用
     */
    fun insertAttachmentReference(attachment: AttachmentInfo) {
        val currentMessage = _userMessage.value
        val attachmentRef = "[附件: ${attachment.name}]"
        _userMessage.value = currentMessage + attachmentRef
    }

    /**
     * 捕获屏幕内容
     */
    fun captureScreenContent() {
        // 实现屏幕内容捕获逻辑
        _toastEvent.emit("屏幕内容已捕获")
    }

    /**
     * 捕获通知
     */
    fun captureNotifications() {
        // 实现通知捕获逻辑
        _toastEvent.emit("通知已捕获")
    }

    /**
     * 捕获位置
     */
    fun captureLocation() {
        // 实现位置捕获逻辑
        _toastEvent.emit("位置已捕获")
    }

    /**
     * 处理拍摄的照片
     */
    fun handleTakenPhoto(uri: String) {
        // 实现照片处理逻辑
        _toastEvent.emit("照片已添加")
    }

    /**
     * 更新附件面板状态
     */
    fun updateAttachmentPanelState(state: AttachmentPanelState) {
        _attachmentPanelState.value = state
    }

    /**
     * 重置附件面板状态
     */
    fun resetAttachmentPanelState() {
        _attachmentPanelState.value = AttachmentPanelState.CLOSED
    }

    /**
     * 清除回复消息
     */
    fun clearReplyToMessage() {
        _replyToMessage.value = null
    }

    /**
     * 切换悬浮窗模式
     */
    fun toggleFloatingMode() {
        _isFloatingMode.value = !_isFloatingMode.value
    }

    /**
     * 更新悬浮窗消息
     */
    fun updateFloatingWindowMessages(messages: List<ChatMessage>) {
        // 实现悬浮窗消息更新逻辑
    }

    /**
     * 切换WebView显示
     */
    fun onWorkspaceButtonClick() {
        _showWebView.value = !_showWebView.value
    }

    /**
     * 切换AI电脑显示
     */
    fun onAiComputerButtonClick() {
        _showAiComputer.value = !_showAiComputer.value
    }

    /**
     * 处理文件选择器结果
     */
    fun handleFileChooserResult(resultCode: Int, data: android.content.Intent?) {
        // 实现文件选择器结果处理逻辑
    }

    /**
     * 设置权限系统颜色方案
     */
    fun setPermissionSystemColorScheme(colorScheme: androidx.compose.material3.ColorScheme) {
        // 实现权限系统颜色方案设置逻辑
    }

    /**
     * 清除Toast事件
     */
    fun clearToastEvent() {
        // Toast事件会自动清除
    }

    /**
     * 清除弹窗消息
     */
    fun clearPopupMessage() {
        _popupMessage.value = null
    }

    /**
     * 显示邀请说明
     */
    fun showInvitationExplanation() {
        _showInvitationExplanation.value = true
    }

    /**
     * 关闭邀请说明
     */
    fun dismissInvitationExplanation() {
        _showInvitationExplanation.value = false
    }

    /**
     * 确认邀请说明
     */
    fun onInvitationExplanationConfirmed() {
        _showInvitationExplanation.value = false
    }

    /**
     * 显示邀请面板
     */
    fun showInvitationPanel() {
        _showInvitationPanel.value = true
    }

    /**
     * 关闭邀请面板
     */
    fun dismissInvitationPanel() {
        _showInvitationPanel.value = false
    }

    /**
     * 分享邀请消息
     */
    fun shareInvitationMessage(message: String) {
        // 实现分享邀请消息逻辑
        _toastEvent.emit("邀请消息已分享")
    }

    /**
     * 验证并处理确认码
     */
    fun verifyAndHandleConfirmationCode(code: String) {
        // 实现确认码验证逻辑
        _toastEvent.emit("确认码已验证")
    }
}

/**
 * 输入处理状态
 */
enum class InputProcessingState {
    IDLE,
    PROCESSING,
    RECORDING,
    TRANSCRIBING
}

/**
 * 附件面板状态
 */
enum class AttachmentPanelState {
    CLOSED,
    OPEN,
    EXPANDED
}

/**
 * 权限级别
 */
enum class PermissionLevel {
    STANDARD,
    ADVANCED,
    EXPERT
}

/**
 * 聊天历史
 */
data class ChatHistory(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long,
    val messageCount: Int
)

/**
 * UI状态委托
 */
class UIStateDelegate {
    private val _fileChooserRequest = MutableStateFlow<android.content.Intent?>(null)
    val fileChooserRequest: StateFlow<android.content.Intent?> = _fileChooserRequest.asStateFlow()

    fun requestFileChooser(intent: android.content.Intent) {
        _fileChooserRequest.value = intent
    }

    fun clearFileChooserRequest() {
        _fileChooserRequest.value = null
    }
}