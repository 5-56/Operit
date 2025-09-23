package com.xihe.assistant.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xihe.assistant.core.agent.AgentConfig
import com.xihe.assistant.core.agent.AgentScriptSaver
import com.xihe.assistant.data.model.AttachmentInfo
import com.xihe.assistant.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel : ViewModel() {
    private val TAG = "ChatViewModel"

    // Chat state
    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    // Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    // Attachment state
    private val _attachmentState = MutableStateFlow(AttachmentState())
    val attachmentState: StateFlow<AttachmentState> = _attachmentState.asStateFlow()

    // Agent state
    private val _agentState = MutableStateFlow(AgentState())
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    // UI state
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Token statistics
    private val _tokenStats = MutableStateFlow(TokenStatistics())
    val tokenStats: StateFlow<TokenStatistics> = _tokenStats.asStateFlow()

    // Plan items
    private val _planItems = MutableStateFlow<List<PlanItem>>(emptyList())
    val planItems: StateFlow<List<PlanItem>> = _planItems.asStateFlow()

    // Chat history
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    // Floating window state
    private val _floatingWindowState = MutableStateFlow(FloatingWindowState())
    val floatingWindowState: StateFlow<FloatingWindowState> = _floatingWindowState.asStateFlow()

    // Background image state
    private val _backgroundImageState = MutableStateFlow(BackgroundImageState())
    val backgroundImageState: StateFlow<BackgroundImageState> = _backgroundImageState.asStateFlow()

    fun setPermissionSystemColorScheme(colorScheme: ColorScheme) {
        // 设置权限系统颜色方案
        Log.d(TAG, "设置权限系统颜色方案")
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 创建用户消息
                val userMessage = ChatMessage(
                    content = content,
                    isUser = true,
                    attachments = _attachmentState.value.attachments
                )
                
                // 添加到消息列表
                val currentMessages = _messages.value.toMutableList()
                currentMessages.add(userMessage)
                _messages.value = currentMessages
                
                // 清除附件
                _attachmentState.value = _attachmentState.value.copy(attachments = emptyList())
                
                // 模拟AI响应
                simulateAIResponse(content)
                
            } catch (e: Exception) {
                Log.e(TAG, "发送消息失败", e)
                _errorMessage.value = "发送消息失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun simulateAIResponse(userMessage: String) {
        // 模拟AI响应延迟
        kotlinx.coroutines.delay(1000)
        
        val aiResponse = when {
            userMessage.contains("你好") -> "你好！我是羲和助手，很高兴为您服务！"
            userMessage.contains("帮助") -> "我可以帮助您处理各种任务，包括文件操作、网络请求、系统管理等。请告诉我您需要什么帮助。"
            userMessage.contains("工具") -> "我拥有40多种内置工具，包括文件系统、网络、系统操作、UI自动化、媒体处理等。您可以通过自然语言与我交互来使用这些工具。"
            else -> "我理解您的需求。作为羲和助手，我可以帮助您完成各种任务。请告诉我具体需要什么帮助。"
        }
        
        val aiMessage = ChatMessage(
            content = aiResponse,
            isUser = false
        )
        
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(aiMessage)
        _messages.value = currentMessages
    }

    fun sendVoiceMessage(audioPath: String) {
        Log.d(TAG, "发送语音消息: $audioPath")
        // 语音消息处理逻辑
    }

    fun attachFile(filePath: String) {
        viewModelScope.launch {
            try {
                val file = java.io.File(filePath)
                val attachment = AttachmentInfo(
                    fileName = file.name,
                    filePath = filePath,
                    fileSize = file.length(),
                    mimeType = getMimeType(file.extension)
                )
                
                val currentAttachments = _attachmentState.value.attachments.toMutableList()
                currentAttachments.add(attachment)
                _attachmentState.value = _attachmentState.value.copy(attachments = currentAttachments)
                
            } catch (e: Exception) {
                Log.e(TAG, "添加附件失败", e)
                _errorMessage.value = "添加附件失败: ${e.message}"
            }
        }
    }

    fun clearAttachments() {
        _attachmentState.value = _attachmentState.value.copy(attachments = emptyList())
    }

    fun retryMessage(messageId: String) {
        Log.d(TAG, "重试消息: $messageId")
        // 重试消息逻辑
    }

    fun copyMessage(content: String) {
        Log.d(TAG, "复制消息: $content")
        // 复制消息逻辑
    }

    fun deleteMessage(messageId: String) {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.removeAll { it.id == messageId }
        _messages.value = currentMessages
    }

    fun clearError() {
        _errorMessage.value = ""
    }

    // Agent相关方法
    fun showAgentConfigDialog() {
        _agentState.value = _agentState.value.copy(showAgentConfigDialog = true)
    }

    fun hideAgentConfigDialog() {
        _agentState.value = _agentState.value.copy(showAgentConfigDialog = false)
    }

    fun updateAgentConfig(config: AgentConfig) {
        _agentState.value = _agentState.value.copy(agentConfig = config)
    }

    fun startAgent(config: AgentConfig) {
        viewModelScope.launch {
            _agentState.value = _agentState.value.copy(
                isAgentRunning = true,
                agentConfig = config
            )
            Log.d(TAG, "启动Agent")
        }
    }

    fun stopAgent() {
        _agentState.value = _agentState.value.copy(isAgentRunning = false)
        Log.d(TAG, "停止Agent")
    }

    // Export相关方法
    fun showExportPlatformDialog() {
        _uiState.value = _uiState.value.copy(showExportPlatformDialog = true)
    }

    fun hideExportPlatformDialog() {
        _uiState.value = _uiState.value.copy(showExportPlatformDialog = false)
    }

    fun showAndroidExportDialog() {
        _uiState.value = _uiState.value.copy(showAndroidExportDialog = true)
    }

    fun hideAndroidExportDialog() {
        _uiState.value = _uiState.value.copy(showAndroidExportDialog = false)
    }

    fun showWindowsExportDialog() {
        _uiState.value = _uiState.value.copy(showWindowsExportDialog = true)
    }

    fun hideWindowsExportDialog() {
        _uiState.value = _uiState.value.copy(showWindowsExportDialog = false)
    }

    fun exportAndroidApp(packageName: String, appName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showAndroidExportDialog = false,
                showExportProgressDialog = true,
                exportProgress = 0f
            )
            
            // 模拟导出过程
            for (i in 1..100) {
                kotlinx.coroutines.delay(50)
                _uiState.value = _uiState.value.copy(exportProgress = i / 100f)
            }
            
            _uiState.value = _uiState.value.copy(
                showExportProgressDialog = false,
                showExportCompleteDialog = true,
                exportPath = "/sdcard/Download/$appName.apk"
            )
        }
    }

    fun exportWindowsApp(appName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showWindowsExportDialog = false,
                showExportProgressDialog = true,
                exportProgress = 0f
            )
            
            // 模拟导出过程
            for (i in 1..100) {
                kotlinx.coroutines.delay(50)
                _uiState.value = _uiState.value.copy(exportProgress = i / 100f)
            }
            
            _uiState.value = _uiState.value.copy(
                showExportProgressDialog = false,
                showExportCompleteDialog = true,
                exportPath = "/sdcard/Download/$appName.exe"
            )
        }
    }

    fun cancelExport() {
        _uiState.value = _uiState.value.copy(showExportProgressDialog = false)
    }

    fun hideExportCompleteDialog() {
        _uiState.value = _uiState.value.copy(showExportCompleteDialog = false)
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}

// 数据类定义
data class ChatState(
    val currentConversationId: String? = null,
    val isTyping: Boolean = false
)

data class AttachmentState(
    val attachments: List<AttachmentInfo> = emptyList(),
    val isUploading: Boolean = false
)

data class AgentState(
    val isAgentRunning: Boolean = false,
    val showAgentConfigDialog: Boolean = false,
    val agentConfig: AgentConfig = AgentConfig(),
    val currentScript: String = "",
    val executionHistory: List<String> = emptyList()
)

data class UiState(
    val showExportPlatformDialog: Boolean = false,
    val showAndroidExportDialog: Boolean = false,
    val showWindowsExportDialog: Boolean = false,
    val showExportProgressDialog: Boolean = false,
    val showExportCompleteDialog: Boolean = false,
    val exportProgress: Float = 0f,
    val exportPath: String = ""
)

data class TokenStatistics(
    val totalTokens: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cost: Double = 0.0
)

data class PlanItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val status: String = "pending"
)

data class FloatingWindowState(
    val isVisible: Boolean = false,
    val position: Pair<Float, Float> = Pair(0f, 0f)
)

data class BackgroundImageState(
    val backgroundImagePath: String = "",
    val opacity: Float = 0.3f
)