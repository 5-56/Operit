package com.ai.assistance.operit.auraflow.ui.config

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.auraflow.core.AuraFlowAgentManager
import com.ai.assistance.operit.auraflow.protocol.ConnectionStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * AI服务类型枚举
 */
enum class AIServiceType {
    CUSTOM,         // 自定义AI服务
    THIRD_PARTY     // 第三方LLM服务
}

/**
 * Agent配置数据
 */
@Serializable
data class AgentConfiguration(
    val screenUpdateMode: Int = 0,      // 0=智能模式, 1=实时模式, 2=按需模式
    val screenshotQuality: Int = 1,     // 0=低质量, 1=中等质量, 2=高质量
    val executionSpeed: Int = 1         // 0=慢速, 1=正常, 2=快速
)

/**
 * UI状态数据类
 */
data class AIBrainConfigUiState(
    val serviceType: AIServiceType = AIServiceType.CUSTOM,
    val serverUrl: String = "",
    val apiKey: String = "",
    val provider: String = "",
    val modelName: String = "",
    val agentConfig: AgentConfiguration = AgentConfiguration(),
    val isTestingConnection: Boolean = false,
    val testResult: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AI大脑配置ViewModel
 */
class AIBrainConfigViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "AIBrainConfigVM"
    }
    
    // UI状态
    private val _uiState = MutableStateFlow(AIBrainConfigUiState())
    val uiState: StateFlow<AIBrainConfigUiState> = _uiState.asStateFlow()
    
    // 连接状态（来自AuraFlowAgentManager）
    val connectionStatus: StateFlow<ConnectionStatus> = flow {
        // 这里应该从AuraFlowAgentManager获取连接状态
        // 暂时返回DISCONNECTED状态
        emit(ConnectionStatus.DISCONNECTED)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionStatus.DISCONNECTED
    )
    
    init {
        loadSavedConfiguration()
    }
    
    /**
     * 加载已保存的配置
     */
    private fun loadSavedConfiguration() {
        viewModelScope.launch {
            try {
                // TODO: 从DataStore或SharedPreferences加载配置
                Log.d(TAG, "加载已保存的配置")
            } catch (e: Exception) {
                Log.e(TAG, "加载配置失败", e)
                updateErrorMessage("加载配置失败: ${e.message}")
            }
        }
    }
    
    /**
     * 保存配置
     */
    private fun saveConfiguration() {
        viewModelScope.launch {
            try {
                // TODO: 保存配置到DataStore或SharedPreferences
                Log.d(TAG, "保存配置: ${_uiState.value}")
            } catch (e: Exception) {
                Log.e(TAG, "保存配置失败", e)
                updateErrorMessage("保存配置失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新服务类型
     */
    fun updateServiceType(serviceType: AIServiceType) {
        _uiState.update { it.copy(serviceType = serviceType) }
        saveConfiguration()
    }
    
    /**
     * 更新服务器URL
     */
    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
        saveConfiguration()
    }
    
    /**
     * 更新API密钥
     */
    fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
        saveConfiguration()
    }
    
    /**
     * 更新服务提供商
     */
    fun updateProvider(provider: String) {
        _uiState.update { it.copy(provider = provider) }
        saveConfiguration()
    }
    
    /**
     * 更新模型名称
     */
    fun updateModelName(modelName: String) {
        _uiState.update { it.copy(modelName = modelName) }
        saveConfiguration()
    }
    
    /**
     * 更新Agent配置
     */
    fun updateAgentConfig(config: AgentConfiguration) {
        _uiState.update { it.copy(agentConfig = config) }
        saveConfiguration()
    }
    
    /**
     * 测试连接
     */
    suspend fun testConnection(context: Context) {
        val currentState = _uiState.value
        
        _uiState.update { it.copy(isTestingConnection = true, testResult = null) }
        
        try {
            val result = when (currentState.serviceType) {
                AIServiceType.CUSTOM -> testCustomService(currentState.serverUrl, currentState.apiKey)
                AIServiceType.THIRD_PARTY -> testThirdPartyService(
                    currentState.provider,
                    currentState.apiKey,
                    currentState.modelName
                )
            }
            
            _uiState.update { 
                it.copy(
                    isTestingConnection = false, 
                    testResult = result
                ) 
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "连接测试失败", e)
            _uiState.update { 
                it.copy(
                    isTestingConnection = false, 
                    testResult = "连接测试失败: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * 测试自定义服务
     */
    private suspend fun testCustomService(serverUrl: String, apiKey: String): String {
        if (serverUrl.isBlank()) {
            return "请输入服务器URL"
        }
        
        // TODO: 实现实际的连接测试逻辑
        // 这里应该尝试连接到指定的WebSocket端点
        kotlinx.coroutines.delay(2000) // 模拟网络请求
        
        return if (serverUrl.startsWith("ws://") || serverUrl.startsWith("wss://")) {
            "连接测试成功！服务可正常访问。"
        } else {
            "连接测试失败：URL格式不正确，请使用 ws:// 或 wss:// 协议。"
        }
    }
    
    /**
     * 测试第三方服务
     */
    private suspend fun testThirdPartyService(provider: String, apiKey: String, modelName: String): String {
        if (provider.isBlank()) {
            return "请选择服务提供商"
        }
        
        if (apiKey.isBlank()) {
            return "请输入API密钥"
        }
        
        if (modelName.isBlank()) {
            return "请输入模型名称"
        }
        
        // TODO: 实现实际的第三方服务测试逻辑
        kotlinx.coroutines.delay(2000) // 模拟网络请求
        
        return "连接测试成功！API密钥有效，模型 $modelName 可正常调用。"
    }
    
    /**
     * 连接到AI大脑
     */
    suspend fun connect(context: Context) {
        val currentState = _uiState.value
        
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        try {
            val agentManager = AuraFlowAgentManager.getInstance(context)
            
            when (currentState.serviceType) {
                AIServiceType.CUSTOM -> {
                    if (currentState.serverUrl.isBlank()) {
                        throw IllegalArgumentException("请输入服务器URL")
                    }
                    
                    // 配置AuraFlow Agent
                    configureAgentBehavior(agentManager, currentState.agentConfig)
                    
                    // 连接到自定义服务
                    agentManager.connectToAIBrain(
                        serverUrl = currentState.serverUrl,
                        apiKey = currentState.apiKey.takeIf { it.isNotBlank() }
                    )
                }
                
                AIServiceType.THIRD_PARTY -> {
                    if (currentState.provider.isBlank() || 
                        currentState.apiKey.isBlank() || 
                        currentState.modelName.isBlank()) {
                        throw IllegalArgumentException("请完整填写第三方服务配置")
                    }
                    
                    // TODO: 实现第三方服务连接逻辑
                    // 这可能需要一个适配器来将第三方LLM API转换为AuraFlow协议
                    throw UnsupportedOperationException("第三方服务连接功能待实现")
                }
            }
            
            _uiState.update { it.copy(isLoading = false) }
            
        } catch (e: Exception) {
            Log.e(TAG, "连接失败", e)
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    errorMessage = "连接失败: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * 断开连接
     */
    suspend fun disconnect(context: Context) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        try {
            val agentManager = AuraFlowAgentManager.getInstance(context)
            agentManager.disconnect()
            
            _uiState.update { it.copy(isLoading = false) }
            
        } catch (e: Exception) {
            Log.e(TAG, "断开连接失败", e)
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    errorMessage = "断开连接失败: ${e.message}"
                ) 
            }
        }
    }
    
    /**
     * 配置Agent行为
     */
    private fun configureAgentBehavior(agentManager: AuraFlowAgentManager, config: AgentConfiguration) {
        val screenUpdateMode = when (config.screenUpdateMode) {
            0 -> AuraFlowAgentManager.ScreenUpdateMode.SMART
            1 -> AuraFlowAgentManager.ScreenUpdateMode.REALTIME
            2 -> AuraFlowAgentManager.ScreenUpdateMode.ON_DEMAND
            else -> AuraFlowAgentManager.ScreenUpdateMode.SMART
        }
        
        val screenshotQuality = when (config.screenshotQuality) {
            0 -> AuraFlowAgentManager.ScreenshotQuality.LOW
            1 -> AuraFlowAgentManager.ScreenshotQuality.MEDIUM
            2 -> AuraFlowAgentManager.ScreenshotQuality.HIGH
            else -> AuraFlowAgentManager.ScreenshotQuality.MEDIUM
        }
        
        val executionSpeed = when (config.executionSpeed) {
            0 -> AuraFlowAgentManager.ExecutionSpeed.SLOW
            1 -> AuraFlowAgentManager.ExecutionSpeed.NORMAL
            2 -> AuraFlowAgentManager.ExecutionSpeed.FAST
            else -> AuraFlowAgentManager.ExecutionSpeed.NORMAL
        }
        
        agentManager.configure(
            screenUpdateMode = screenUpdateMode,
            screenshotQuality = screenshotQuality,
            executionSpeed = executionSpeed
        )
        
        Log.d(TAG, "Agent行为配置已更新: $config")
    }
    
    /**
     * 更新错误消息
     */
    private fun updateErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
    
    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}