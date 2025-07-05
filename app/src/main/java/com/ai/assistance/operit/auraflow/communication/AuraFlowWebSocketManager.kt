package com.ai.assistance.operit.auraflow.communication

import android.util.Log
import com.ai.assistance.operit.auraflow.protocol.*
import com.ai.assistance.operit.core.application.OperitApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * AuraFlow Agent WebSocket 通信管理器
 * 负责与 AI 大脑的实时双向通信
 */
class AuraFlowWebSocketManager private constructor() {
    
    companion object {
        private const val TAG = "AuraFlowWebSocket"
        
        // 单例实例
        @Volatile
        private var INSTANCE: AuraFlowWebSocketManager? = null
        
        fun getInstance(): AuraFlowWebSocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuraFlowWebSocketManager().also { INSTANCE = it }
            }
        }
        
        // 连接配置常量
        private const val HEARTBEAT_INTERVAL = 30_000L  // 心跳间隔30秒
        private const val CONNECT_TIMEOUT = 10_000L     // 连接超时10秒
        private const val READ_TIMEOUT = 60_000L        // 读取超时60秒
        private const val WRITE_TIMEOUT = 10_000L       // 写入超时10秒
        private const val MAX_RECONNECT_ATTEMPTS = 5    // 最大重连次数
        private const val RECONNECT_DELAY = 2_000L      // 重连延迟2秒
    }
    
    // WebSocket 客户端
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
        .build()
    
    // 当前连接状态
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // 消息流
    private val _incomingMessages = MutableSharedFlow<AuraFlowMessage>()
    val incomingMessages: SharedFlow<AuraFlowMessage> = _incomingMessages.asSharedFlow()
    
    // WebSocket 连接
    private val currentWebSocket = AtomicReference<WebSocket?>(null)
    private val isConnecting = AtomicBoolean(false)
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 重连机制
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    
    // 心跳机制
    private var heartbeatJob: Job? = null
    
    // 连接配置
    private var serverUrl: String? = null
    private var apiKey: String? = null
    private var connectionHeaders = mutableMapOf<String, String>()
    
    /**
     * 配置连接参数
     */
    fun configure(
        serverUrl: String,
        apiKey: String? = null,
        headers: Map<String, String> = emptyMap()
    ) {
        this.serverUrl = serverUrl
        this.apiKey = apiKey
        this.connectionHeaders.clear()
        this.connectionHeaders.putAll(headers)
        
        // 添加API Key到请求头
        apiKey?.let {
            connectionHeaders["Authorization"] = "Bearer $it"
        }
        
        Log.d(TAG, "WebSocket配置更新: serverUrl=$serverUrl")
    }
    
    /**
     * 连接到 AI 大脑服务
     */
    fun connect() {
        if (serverUrl.isNullOrBlank()) {
            Log.e(TAG, "服务器URL未配置，无法连接")
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }
        
        if (isConnecting.get()) {
            Log.d(TAG, "正在连接中，忽略重复连接请求")
            return
        }
        
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            Log.d(TAG, "已经连接，无需重复连接")
            return
        }
        
        isConnecting.set(true)
        _connectionStatus.value = ConnectionStatus.CONNECTING
        
        try {
            val request = Request.Builder()
                .url(serverUrl!!)
                .apply {
                    connectionHeaders.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()
            
            val listener = AuraFlowWebSocketListener()
            val webSocket = okHttpClient.newWebSocket(request, listener)
            currentWebSocket.set(webSocket)
            
            Log.d(TAG, "开始连接到: $serverUrl")
        } catch (e: Exception) {
            Log.e(TAG, "连接失败", e)
            isConnecting.set(false)
            _connectionStatus.value = ConnectionStatus.ERROR
            scheduleReconnect()
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "主动断开连接")
        
        // 停止重连和心跳
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        
        // 关闭WebSocket连接
        currentWebSocket.get()?.close(1000, "Client disconnect")
        currentWebSocket.set(null)
        
        isConnecting.set(false)
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        reconnectAttempts = 0
    }
    
    /**
     * 发送消息到 AI 大脑
     */
    suspend fun sendMessage(message: AuraFlowMessage): Boolean {
        val webSocket = currentWebSocket.get()
        if (webSocket == null) {
            Log.w(TAG, "WebSocket未连接，无法发送消息: ${message.type}")
            return false
        }
        
        return try {
            val json = OperitApplication.json.encodeToString(message)
            val success = webSocket.send(json)
            if (success) {
                Log.d(TAG, "消息发送成功: ${message.type}")
            } else {
                Log.w(TAG, "消息发送失败: ${message.type}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "发送消息异常", e)
            false
        }
    }
    
    /**
     * 发送屏幕更新消息
     */
    suspend fun sendScreenUpdate(screenData: ScreenUpdateData) {
        val message = AuraFlowMessage(
            messageId = UUID.randomUUID().toString(),
            type = MessageType.SCREEN_UPDATE,
            timestamp = System.currentTimeMillis(),
            data = OperitApplication.json.encodeToJsonElement(screenData).jsonObject
        )
        sendMessage(message)
    }
    
    /**
     * 发送操作结果消息
     */
    suspend fun sendActionResult(resultData: ActionResultData) {
        val message = AuraFlowMessage(
            messageId = UUID.randomUUID().toString(),
            type = MessageType.ACTION_RESULT,
            timestamp = System.currentTimeMillis(),
            data = OperitApplication.json.encodeToJsonElement(resultData).jsonObject
        )
        sendMessage(message)
    }
    
    /**
     * 发送Agent状态消息
     */
    suspend fun sendAgentStatus(statusData: AgentStatusData) {
        val message = AuraFlowMessage(
            messageId = UUID.randomUUID().toString(),
            type = MessageType.AGENT_STATUS,
            timestamp = System.currentTimeMillis(),
            data = OperitApplication.json.encodeToJsonElement(statusData).jsonObject
        )
        sendMessage(message)
    }
    
    /**
     * 启动心跳机制
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    val heartbeat = AuraFlowMessage(
                        messageId = UUID.randomUUID().toString(),
                        type = MessageType.HEARTBEAT,
                        timestamp = System.currentTimeMillis(),
                        data = OperitApplication.json.encodeToJsonElement(
                            HeartbeatData(
                                agentVersion = "1.0.0", // TODO: 从BuildConfig获取
                                uptime = System.currentTimeMillis()
                            )
                        ).jsonObject
                    )
                    sendMessage(heartbeat)
                    delay(HEARTBEAT_INTERVAL)
                } catch (e: Exception) {
                    Log.w(TAG, "心跳发送失败", e)
                    break
                }
            }
        }
    }
    
    /**
     * 安排重连
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "达到最大重连次数，停止重连")
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }
        
        reconnectAttempts++
        _connectionStatus.value = ConnectionStatus.RECONNECTING
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY * reconnectAttempts) // 指数退避
            Log.d(TAG, "开始第 $reconnectAttempts 次重连")
            connect()
        }
    }
    
    /**
     * WebSocket 监听器
     */
    private inner class AuraFlowWebSocketListener : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket连接成功")
            isConnecting.set(false)
            _connectionStatus.value = ConnectionStatus.CONNECTED
            reconnectAttempts = 0
            
            // 启动心跳
            startHeartbeat()
            
            // 发送连接消息
            scope.launch {
                val connectMessage = AuraFlowMessage(
                    messageId = UUID.randomUUID().toString(),
                    type = MessageType.CONNECT,
                    timestamp = System.currentTimeMillis()
                )
                sendMessage(connectMessage)
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "收到消息: ${text.take(100)}...")
            
            try {
                val message = OperitApplication.json.decodeFromString<AuraFlowMessage>(text)
                scope.launch {
                    _incomingMessages.emit(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析消息失败", e)
                
                // 发送错误反馈
                scope.launch {
                    val errorMessage = AuraFlowMessage(
                        messageId = UUID.randomUUID().toString(),
                        type = MessageType.ERROR,
                        timestamp = System.currentTimeMillis(),
                        data = OperitApplication.json.encodeToJsonElement(
                            ErrorData(
                                errorCode = "PARSE_ERROR",
                                errorMessage = "Failed to parse message: ${e.message}"
                            )
                        ).jsonObject
                    )
                    sendMessage(errorMessage)
                }
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket连接关闭中: code=$code, reason=$reason")
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket连接已关闭: code=$code, reason=$reason")
            
            currentWebSocket.compareAndSet(webSocket, null)
            isConnecting.set(false)
            heartbeatJob?.cancel()
            
            if (_connectionStatus.value != ConnectionStatus.DISCONNECTED) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                // 如果不是主动断开，尝试重连
                if (code != 1000) {
                    scheduleReconnect()
                }
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket连接失败", t)
            
            currentWebSocket.compareAndSet(webSocket, null)
            isConnecting.set(false)
            heartbeatJob?.cancel()
            
            if (_connectionStatus.value != ConnectionStatus.DISCONNECTED) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                scheduleReconnect()
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}