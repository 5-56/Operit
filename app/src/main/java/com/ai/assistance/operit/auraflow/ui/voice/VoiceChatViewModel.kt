package com.ai.assistance.operit.auraflow.ui.voice

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.auraflow.voice.RealTimeVoiceManager
import com.ai.assistance.operit.auraflow.voice.VoiceRecognitionResult
import com.ai.assistance.operit.auraflow.voice.VoiceState
import com.ai.assistance.operit.auraflow.ai.AIBrainClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*

/**
 * 语音聊天ViewModel
 */
class VoiceChatViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "VoiceChatViewModel"
    }
    
    // 实时语音管理器
    private val voiceManager = RealTimeVoiceManager(application)
    
    // AI大脑客户端
    private val aiBrainClient = AIBrainClient.getInstance()
    
    // 消息列表
    private val _messages = MutableStateFlow<List<VoiceMessage>>(emptyList())
    val messages: StateFlow<List<VoiceMessage>> = _messages.asStateFlow()
    
    // 当前部分识别文本
    private val _currentPartialText = MutableStateFlow("")
    val currentPartialText: StateFlow<String> = _currentPartialText.asStateFlow()
    
    // 对话是否激活
    private val _isConversationActive = MutableStateFlow(false)
    val isConversationActive: StateFlow<Boolean> = _isConversationActive.asStateFlow()
    
    // UI状态
    private val _showVoiceSettings = MutableStateFlow(false)
    val showVoiceSettings: StateFlow<Boolean> = _showVoiceSettings.asStateFlow()
    
    private val _showTextInput = MutableStateFlow(false)
    val showTextInput: StateFlow<Boolean> = _showTextInput.asStateFlow()
    
    private val _isVideoCallActive = MutableStateFlow(false)
    val isVideoCallActive: StateFlow<Boolean> = _isVideoCallActive.asStateFlow()
    
    // 语音设置
    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()
    
    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()
    
    // 代理语音管理器的状态
    val voiceState: StateFlow<VoiceState> = voiceManager.voiceState
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val audioLevel: StateFlow<Float> = voiceManager.audioLevel
    
    // 会话上下文
    private val conversationHistory = mutableListOf<VoiceMessage>()
    
    init {
        initializeVoiceManager()
        setupVoiceRecognitionListener()
        setupAIResponseHandler()
    }
    
    /**
     * 初始化语音管理器
     */
    private fun initializeVoiceManager() {
        viewModelScope.launch {
            try {
                val success = voiceManager.initialize()
                if (success) {
                    Log.d(TAG, "语音管理器初始化成功")
                    addSystemMessage("语音系统已就绪，可以开始对话")
                } else {
                    Log.e(TAG, "语音管理器初始化失败")
                    addSystemMessage("语音系统初始化失败，请检查权限设置")
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化语音管理器时出错", e)
                addSystemMessage("语音系统启动出错: ${e.message}")
            }
        }
    }
    
    /**
     * 设置语音识别监听
     */
    private fun setupVoiceRecognitionListener() {
        viewModelScope.launch {
            voiceManager.recognitionResults.collect { result ->
                handleVoiceRecognitionResult(result)
            }
        }
    }
    
    /**
     * 处理语音识别结果
     */
    private fun handleVoiceRecognitionResult(result: VoiceRecognitionResult) {
        viewModelScope.launch {
            if (result.isFinal) {
                // 清空部分识别文本
                _currentPartialText.value = ""
                
                // 添加用户消息
                val userMessage = VoiceMessage(
                    id = UUID.randomUUID().toString(),
                    type = VoiceMessageType.USER_VOICE,
                    content = result.text,
                    timestamp = result.timestamp,
                    confidence = result.confidence
                )
                
                addMessage(userMessage)
                
                // 发送给AI大脑处理
                sendToAIBrain(result.text)
                
            } else {
                // 更新部分识别文本
                _currentPartialText.value = result.text
            }
        }
    }
    
    /**
     * 发送消息给AI大脑
     */
    private suspend fun sendToAIBrain(text: String) {
        try {
            // 构建会话上下文
            val context = buildConversationContext()
            
            // 发送给AI大脑
            val response = aiBrainClient.sendMessage(text, context)
            
            if (response != null) {
                // 添加AI回复消息
                val aiMessage = VoiceMessage(
                    id = UUID.randomUUID().toString(),
                    type = VoiceMessageType.AI_TEXT,
                    content = response,
                    timestamp = System.currentTimeMillis()
                )
                
                addMessage(aiMessage)
                
                // 语音播放AI回复
                voiceManager.speakText(response)
                
                // 添加语音版本的消息
                val aiVoiceMessage = VoiceMessage(
                    id = UUID.randomUUID().toString(),
                    type = VoiceMessageType.AI_VOICE,
                    content = response,
                    timestamp = System.currentTimeMillis()
                )
                
                addMessage(aiVoiceMessage)
                
            } else {
                addSystemMessage("AI暂时无法响应，请稍后重试")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送消息给AI大脑失败", e)
            addSystemMessage("AI处理出错: ${e.message}")
        }
    }
    
    /**
     * 构建会话上下文
     */
    private fun buildConversationContext(): Map<String, Any> {
        return mapOf(
            "conversation_history" to conversationHistory.takeLast(10).map { message ->
                mapOf(
                    "role" to if (message.type in listOf(VoiceMessageType.USER_TEXT, VoiceMessageType.USER_VOICE)) "user" else "assistant",
                    "content" to message.content,
                    "timestamp" to message.timestamp,
                    "confidence" to message.confidence
                )
            },
            "voice_context" to mapOf(
                "is_voice_conversation" to true,
                "audio_level" to audioLevel.value,
                "voice_state" to voiceState.value.name
            ),
            "user_preferences" to mapOf(
                "speech_rate" to speechRate.value,
                "pitch" to pitch.value,
                "interruption_enabled" to true
            )
        )
    }
    
    /**
     * 设置AI响应处理
     */
    private fun setupAIResponseHandler() {
        // 监听语音状态变化
        viewModelScope.launch {
            voiceState.collect { state ->
                when (state) {
                    VoiceState.INTERRUPTED -> {
                        addSystemMessage("AI被打断，正在重新监听...")
                    }
                    VoiceState.ERROR -> {
                        addSystemMessage("语音系统出现错误")
                    }
                    else -> {
                        // 其他状态不需要特殊处理
                    }
                }
            }
        }
    }
    
    /**
     * 开始对话
     */
    fun startConversation() {
        viewModelScope.launch {
            try {
                voiceManager.startRealTimeConversation()
                _isConversationActive.value = true
                addSystemMessage("开始实时语音对话")
                Log.d(TAG, "实时对话已开始")
            } catch (e: Exception) {
                Log.e(TAG, "启动对话失败", e)
                addSystemMessage("启动对话失败: ${e.message}")
            }
        }
    }
    
    /**
     * 停止对话
     */
    fun stopConversation() {
        viewModelScope.launch {
            try {
                voiceManager.stopRealTimeConversation()
                _isConversationActive.value = false
                _currentPartialText.value = ""
                addSystemMessage("对话已结束")
                Log.d(TAG, "实时对话已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止对话失败", e)
                addSystemMessage("停止对话失败: ${e.message}")
            }
        }
    }
    
    /**
     * 打断AI说话
     */
    fun interruptSpeaking() {
        voiceManager.interruptSpeaking()
        addSystemMessage("用户打断了AI")
    }
    
    /**
     * 切换视频通话
     */
    fun toggleVideoCall() {
        viewModelScope.launch {
            _isVideoCallActive.value = !_isVideoCallActive.value
            if (_isVideoCallActive.value) {
                addSystemMessage("视频通话已开启")
                // TODO: 启动视频通话功能
            } else {
                addSystemMessage("视频通话已关闭")
                // TODO: 关闭视频通话功能
            }
        }
    }
    
    /**
     * 显示语音设置
     */
    fun showVoiceSettings() {
        _showVoiceSettings.value = true
    }
    
    /**
     * 隐藏语音设置
     */
    fun hideVoiceSettings() {
        _showVoiceSettings.value = false
    }
    
    /**
     * 显示文本输入
     */
    fun showTextInput() {
        _showTextInput.value = true
    }
    
    /**
     * 隐藏文本输入
     */
    fun hideTextInput() {
        _showTextInput.value = false
    }
    
    /**
     * 更新语速
     */
    fun updateSpeechRate(rate: Float) {
        _speechRate.value = rate
        voiceManager.adjustVoiceSettings(speechRate = rate, pitch = pitch.value)
    }
    
    /**
     * 更新音调
     */
    fun updatePitch(pitch: Float) {
        _pitch.value = pitch
        voiceManager.adjustVoiceSettings(speechRate = speechRate.value, pitch = pitch)
    }
    
    /**
     * 发送文本消息
     */
    fun sendTextMessage(text: String) {
        viewModelScope.launch {
            val userMessage = VoiceMessage(
                id = UUID.randomUUID().toString(),
                type = VoiceMessageType.USER_TEXT,
                content = text,
                timestamp = System.currentTimeMillis()
            )
            
            addMessage(userMessage)
            sendToAIBrain(text)
        }
    }
    
    /**
     * 清空消息
     */
    fun clearMessages() {
        _messages.value = emptyList()
        conversationHistory.clear()
        _currentPartialText.value = ""
        addSystemMessage("对话历史已清空")
    }
    
    /**
     * 添加消息
     */
    private fun addMessage(message: VoiceMessage) {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
        
        // 添加到会话历史
        if (message.type != VoiceMessageType.SYSTEM && message.type != VoiceMessageType.PARTIAL) {
            conversationHistory.add(message)
            
            // 限制历史记录长度
            if (conversationHistory.size > 50) {
                conversationHistory.removeAt(0)
            }
        }
    }
    
    /**
     * 添加系统消息
     */
    private fun addSystemMessage(content: String) {
        val systemMessage = VoiceMessage(
            id = UUID.randomUUID().toString(),
            type = VoiceMessageType.SYSTEM,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        
        addMessage(systemMessage)
    }
    
    /**
     * 设置语音管理器的可打断性
     */
    fun setInterruptible(interruptible: Boolean) {
        voiceManager.setInterruptible(interruptible)
    }
    
    /**
     * 获取对话统计信息
     */
    fun getConversationStats(): Map<String, Any> {
        val userMessages = conversationHistory.count { 
            it.type in listOf(VoiceMessageType.USER_TEXT, VoiceMessageType.USER_VOICE) 
        }
        
        val aiMessages = conversationHistory.count { 
            it.type in listOf(VoiceMessageType.AI_TEXT, VoiceMessageType.AI_VOICE) 
        }
        
        val avgConfidence = conversationHistory
            .filter { it.type in listOf(VoiceMessageType.USER_VOICE, VoiceMessageType.AI_VOICE) }
            .map { it.confidence }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        return mapOf(
            "total_messages" to conversationHistory.size,
            "user_messages" to userMessages,
            "ai_messages" to aiMessages,
            "average_confidence" to avgConfidence,
            "conversation_duration" to if (conversationHistory.isNotEmpty()) {
                conversationHistory.last().timestamp - conversationHistory.first().timestamp
            } else 0L
        )
    }
    
    /**
     * 导出对话记录
     */
    fun exportConversation(): String {
        return conversationHistory.joinToString("\n\n") { message ->
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(message.timestamp))
            
            val sender = when (message.type) {
                VoiceMessageType.USER_TEXT, VoiceMessageType.USER_VOICE -> "用户"
                VoiceMessageType.AI_TEXT, VoiceMessageType.AI_VOICE -> "AI助手"
                else -> "系统"
            }
            
            val confidenceInfo = if (message.confidence < 1.0f) {
                " (置信度: ${(message.confidence * 100).toInt()}%)"
            } else ""
            
            "[$timestamp] $sender$confidenceInfo:\n${message.content}"
        }
    }
    
    /**
     * 重新连接AI大脑
     */
    fun reconnectAIBrain() {
        viewModelScope.launch {
            try {
                aiBrainClient.reconnect()
                addSystemMessage("AI大脑重新连接成功")
            } catch (e: Exception) {
                Log.e(TAG, "重新连接AI大脑失败", e)
                addSystemMessage("AI大脑重新连接失败: ${e.message}")
            }
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        voiceManager.cleanup()
        Log.d(TAG, "VoiceChatViewModel已清理")
    }
}