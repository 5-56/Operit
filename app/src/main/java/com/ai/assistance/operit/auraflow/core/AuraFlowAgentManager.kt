package com.ai.assistance.operit.auraflow.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.ai.assistance.operit.auraflow.automation.ActionExecutor
import com.ai.assistance.operit.auraflow.communication.AuraFlowWebSocketManager
import com.ai.assistance.operit.auraflow.protocol.*
import com.ai.assistance.operit.auraflow.sensing.ScreenSensor
import com.ai.assistance.operit.core.application.OperitApplication
import com.ai.assistance.operit.services.UIAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * AuraFlow Agent 核心管理器
 * 统一协调通信、感知、执行等各个模块
 */
class AuraFlowAgentManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AuraFlowAgent"
        private const val SCREEN_UPDATE_INTERVAL = 2000L // 屏幕更新间隔2秒
        private const val STATUS_UPDATE_INTERVAL = 10000L // 状态更新间隔10秒
        
        @Volatile
        private var INSTANCE: AuraFlowAgentManager? = null
        
        fun getInstance(context: Context): AuraFlowAgentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuraFlowAgentManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 核心模块
    private val webSocketManager = AuraFlowWebSocketManager.getInstance()
    private val actionExecutor = ActionExecutor.getInstance(context)
    private val screenSensor = ScreenSensor.getInstance(context)
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Agent 状态
    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()
    
    // AI 反馈流
    private val _aiFeedback = MutableSharedFlow<AIFeedbackData>()
    val aiFeedback: SharedFlow<AIFeedbackData> = _aiFeedback.asSharedFlow()
    
    // AI 提问流
    private val _aiQuestions = MutableSharedFlow<AIQuestionData>()
    val aiQuestions: SharedFlow<AIQuestionData> = _aiQuestions.asSharedFlow()
    
    // 任务执行状态
    private var currentTaskId: String? = null
    private var isTaskRunning = false
    
    // 定期更新任务
    private var screenUpdateJob: Job? = null
    private var statusUpdateJob: Job? = null
    
    // 配置参数
    private var screenUpdateMode = ScreenUpdateMode.SMART
    private var screenshotQuality = ScreenshotQuality.MEDIUM
    private var executionSpeed = ExecutionSpeed.NORMAL
    
    init {
        setupMessageHandling()
        setupConnectionStatusHandling()
    }
    
    /**
     * Agent 状态枚举
     */
    enum class AgentState {
        IDLE,           // 空闲状态
        CONNECTING,     // 连接中
        CONNECTED,      // 已连接
        EXECUTING,      // 执行任务中
        PAUSED,         // 任务暂停
        ERROR           // 错误状态
    }
    
    /**
     * 屏幕更新模式
     */
    enum class ScreenUpdateMode {
        SMART,          // 智能模式（UI变化时更新）
        REALTIME,       // 实时模式（固定频率更新）
        ON_DEMAND       // 按需模式（AI请求时更新）
    }
    
    /**
     * 截图质量
     */
    enum class ScreenshotQuality {
        LOW,    // 低质量（快速响应）
        MEDIUM, // 中等质量（平衡）
        HIGH    // 高质量（高精度）
    }
    
    /**
     * 执行速度
     */
    enum class ExecutionSpeed {
        SLOW,   // 慢速（便于观察）
        NORMAL, // 正常速度
        FAST    // 快速
    }
    
    /**
     * 配置 Agent 行为参数
     */
    fun configure(
        screenUpdateMode: ScreenUpdateMode = ScreenUpdateMode.SMART,
        screenshotQuality: ScreenshotQuality = ScreenshotQuality.MEDIUM,
        executionSpeed: ExecutionSpeed = ExecutionSpeed.NORMAL
    ) {
        this.screenUpdateMode = screenUpdateMode
        this.screenshotQuality = screenshotQuality
        this.executionSpeed = executionSpeed
        
        Log.d(TAG, "Agent 配置更新: screenUpdateMode=$screenUpdateMode, " +
                "screenshotQuality=$screenshotQuality, executionSpeed=$executionSpeed")
    }
    
    /**
     * 连接到 AI 大脑服务
     */
    fun connectToAIBrain(serverUrl: String, apiKey: String? = null) {
        Log.d(TAG, "开始连接到 AI 大脑服务: $serverUrl")
        
        webSocketManager.configure(serverUrl, apiKey)
        webSocketManager.connect()
        _agentState.value = AgentState.CONNECTING
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "断开与 AI 大脑的连接")
        
        stopTask()
        stopPeriodicUpdates()
        webSocketManager.disconnect()
        _agentState.value = AgentState.IDLE
    }
    
    /**
     * 开始任务执行
     */
    fun startTask(taskId: String? = null) {
        if (!isConnected()) {
            Log.w(TAG, "未连接到 AI 大脑，无法开始任务")
            return
        }
        
        currentTaskId = taskId ?: UUID.randomUUID().toString()
        isTaskRunning = true
        _agentState.value = AgentState.EXECUTING
        
        startPeriodicUpdates()
        
        Log.d(TAG, "任务开始: $currentTaskId")
        
        // 立即发送初始屏幕状态
        scope.launch {
            sendScreenUpdate()
            sendAgentStatus()
        }
    }
    
    /**
     * 暂停任务
     */
    fun pauseTask() {
        if (isTaskRunning) {
            isTaskRunning = false
            _agentState.value = AgentState.PAUSED
            stopPeriodicUpdates()
            
            Log.d(TAG, "任务暂停: $currentTaskId")
        }
    }
    
    /**
     * 恢复任务
     */
    fun resumeTask() {
        if (!isTaskRunning && currentTaskId != null) {
            isTaskRunning = true
            _agentState.value = AgentState.EXECUTING
            startPeriodicUpdates()
            
            Log.d(TAG, "任务恢复: $currentTaskId")
            
            // 发送当前状态
            scope.launch {
                sendScreenUpdate()
                sendAgentStatus()
            }
        }
    }
    
    /**
     * 停止任务
     */
    fun stopTask() {
        if (currentTaskId != null) {
            Log.d(TAG, "任务停止: $currentTaskId")
            
            currentTaskId = null
            isTaskRunning = false
            stopPeriodicUpdates()
            
            if (isConnected()) {
                _agentState.value = AgentState.CONNECTED
            } else {
                _agentState.value = AgentState.IDLE
            }
        }
    }
    
    /**
     * 回答 AI 提问
     */
    suspend fun answerQuestion(questionId: String, answer: String) {
        val message = AuraFlowMessage(
            messageId = UUID.randomUUID().toString(),
            type = MessageType.AI_FEEDBACK,
            timestamp = System.currentTimeMillis(),
            replyTo = questionId,
            data = OperitApplication.json.encodeToJsonElement(
                AIFeedbackData(
                    feedbackType = FeedbackType.INFO,
                    content = answer
                )
            ).jsonObject
        )
        
        webSocketManager.sendMessage(message)
        Log.d(TAG, "回答 AI 提问: questionId=$questionId, answer=$answer")
    }
    
    /**
     * 手动触发屏幕更新
     */
    suspend fun triggerScreenUpdate() {
        if (isConnected()) {
            sendScreenUpdate()
        }
    }
    
    /**
     * 获取当前连接状态
     */
    fun isConnected(): Boolean {
        return webSocketManager.connectionStatus.value == ConnectionStatus.CONNECTED
    }
    
    /**
     * 获取当前任务状态
     */
    fun isTaskActive(): Boolean {
        return currentTaskId != null && isTaskRunning
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 设置消息处理
     */
    private fun setupMessageHandling() {
        scope.launch {
            webSocketManager.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }
    
    /**
     * 设置连接状态处理
     */
    private fun setupConnectionStatusHandling() {
        scope.launch {
            webSocketManager.connectionStatus.collect { status ->
                when (status) {
                    ConnectionStatus.CONNECTED -> {
                        if (_agentState.value == AgentState.CONNECTING) {
                            _agentState.value = AgentState.CONNECTED
                        }
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        if (_agentState.value != AgentState.IDLE) {
                            stopTask()
                            _agentState.value = AgentState.IDLE
                        }
                    }
                    ConnectionStatus.ERROR -> {
                        _agentState.value = AgentState.ERROR
                        stopTask()
                    }
                    else -> {
                        // CONNECTING, RECONNECTING 状态由 WebSocketManager 处理
                    }
                }
            }
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private suspend fun handleIncomingMessage(message: AuraFlowMessage) {
        try {
            when (message.type) {
                MessageType.AI_COMMAND -> handleAICommand(message)
                MessageType.AI_FEEDBACK -> handleAIFeedback(message)
                MessageType.AI_QUESTION -> handleAIQuestion(message)
                MessageType.HEARTBEAT -> Log.d(TAG, "收到心跳响应")
                else -> Log.d(TAG, "收到未处理的消息类型: ${message.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理消息失败: ${message.type}", e)
        }
    }
    
    /**
     * 处理 AI 指令
     */
    private suspend fun handleAICommand(message: AuraFlowMessage) {
        val data = message.data ?: return
        
        try {
            val commandData = OperitApplication.json.decodeFromJsonElement<AICommandData>(data)
            Log.d(TAG, "执行 AI 指令: ${commandData.action}")
            
            // 根据执行速度添加延迟
            val delay = when (executionSpeed) {
                ExecutionSpeed.SLOW -> 1000L
                ExecutionSpeed.NORMAL -> 300L
                ExecutionSpeed.FAST -> 100L
            }
            delay(delay)
            
            // 执行操作
            val result = actionExecutor.executeCommand(commandData)
            result.commandId = message.messageId
            
            // 发送执行结果
            webSocketManager.sendActionResult(result)
            
            // 如果执行成功，发送更新的屏幕状态
            if (result.success) {
                delay(200) // 等待UI更新
                sendScreenUpdate()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "解析或执行 AI 指令失败", e)
            
            // 发送错误结果
            val errorResult = ActionResultData(
                commandId = message.messageId,
                success = false,
                errorMessage = "Failed to parse or execute command: ${e.message}",
                executionTime = 0
            )
            webSocketManager.sendActionResult(errorResult)
        }
    }
    
    /**
     * 处理 AI 反馈
     */
    private suspend fun handleAIFeedback(message: AuraFlowMessage) {
        val data = message.data ?: return
        
        try {
            val feedbackData = OperitApplication.json.decodeFromJsonElement<AIFeedbackData>(data)
            _aiFeedback.emit(feedbackData)
            Log.d(TAG, "收到 AI 反馈: ${feedbackData.feedbackType} - ${feedbackData.content}")
        } catch (e: Exception) {
            Log.e(TAG, "解析 AI 反馈失败", e)
        }
    }
    
    /**
     * 处理 AI 提问
     */
    private suspend fun handleAIQuestion(message: AuraFlowMessage) {
        val data = message.data ?: return
        
        try {
            val questionData = OperitApplication.json.decodeFromJsonElement<AIQuestionData>(data)
            _aiQuestions.emit(questionData)
            Log.d(TAG, "收到 AI 提问: ${questionData.question}")
        } catch (e: Exception) {
            Log.e(TAG, "解析 AI 提问失败", e)
        }
    }
    
    /**
     * 启动定期更新
     */
    private fun startPeriodicUpdates() {
        // 屏幕更新
        if (screenUpdateMode == ScreenUpdateMode.REALTIME) {
            screenUpdateJob = scope.launch {
                while (isActive && isTaskRunning) {
                    sendScreenUpdate()
                    delay(SCREEN_UPDATE_INTERVAL)
                }
            }
        }
        
        // 状态更新
        statusUpdateJob = scope.launch {
            while (isActive && isTaskRunning) {
                sendAgentStatus()
                delay(STATUS_UPDATE_INTERVAL)
            }
        }
    }
    
    /**
     * 停止定期更新
     */
    private fun stopPeriodicUpdates() {
        screenUpdateJob?.cancel()
        statusUpdateJob?.cancel()
    }
    
    /**
     * 发送屏幕更新
     */
    private suspend fun sendScreenUpdate() {
        try {
            val uiHierarchy = getUIHierarchy()
            val screenshot = if (shouldIncludeScreenshot()) {
                captureScreenshot()
            } else null
            
            val screenData = ScreenUpdateData(
                uiHierarchy = uiHierarchy,
                screenshot = screenshot,
                screenResolution = getScreenResolution(),
                activeApp = getCurrentActiveApp(),
                screenOrientation = getScreenOrientation()
            )
            
            webSocketManager.sendScreenUpdate(screenData)
            Log.d(TAG, "屏幕更新已发送")
        } catch (e: Exception) {
            Log.e(TAG, "发送屏幕更新失败", e)
        }
    }
    
    /**
     * 发送 Agent 状态
     */
    private suspend fun sendAgentStatus() {
        try {
            val statusData = AgentStatusData(
                connectionStatus = webSocketManager.connectionStatus.value,
                batteryLevel = getBatteryLevel(),
                isCharging = isCharging(),
                networkType = getNetworkType(),
                availableMemory = getAvailableMemory(),
                cpuUsage = getCpuUsage(),
                permissions = getPermissionStatus()
            )
            
            webSocketManager.sendAgentStatus(statusData)
            Log.d(TAG, "Agent 状态已发送")
        } catch (e: Exception) {
            Log.e(TAG, "发送 Agent 状态失败", e)
        }
    }
    
    // ========== 辅助方法 ==========
    
    private fun getUIHierarchy(): String {
        val accessibilityService = UIAccessibilityService.getInstance()
        return accessibilityService?.getUIHierarchy() ?: ""
    }
    
    private fun shouldIncludeScreenshot(): Boolean {
        return when (screenUpdateMode) {
            ScreenUpdateMode.SMART -> true // 智能判断是否需要截图
            ScreenUpdateMode.REALTIME -> true
            ScreenUpdateMode.ON_DEMAND -> false
        }
    }
    
    private suspend fun captureScreenshot(): String? {
        return try {
            val bitmap = screenSensor.captureScreen()
            if (bitmap != null) {
                compressBitmapToBase64(bitmap)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            null
        }
    }
    
    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        val quality = when (screenshotQuality) {
            ScreenshotQuality.LOW -> 30
            ScreenshotQuality.MEDIUM -> 60
            ScreenshotQuality.HIGH -> 85
        }
        
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    
    private fun getScreenResolution(): ScreenResolution {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        return ScreenResolution(
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            density = displayMetrics.density
        )
    }
    
    private fun getCurrentActiveApp(): String? {
        // 需要通过 UsageStatsManager 或其他方式获取当前活跃应用
        return null // TODO: 实现获取当前应用包名
    }
    
    private fun getScreenOrientation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.defaultDisplay.rotation
    }
    
    private fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) (level * 100 / scale) else -1
    }
    
    private fun isCharging(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || 
               status == BatteryManager.BATTERY_STATUS_FULL
    }
    
    private fun getNetworkType(): String {
        // TODO: 实现网络类型检测
        return "Unknown"
    }
    
    private fun getAvailableMemory(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem
    }
    
    private fun getCpuUsage(): Float {
        // TODO: 实现CPU使用率检测
        return 0.0f
    }
    
    private fun getPermissionStatus(): PermissionStatus {
        return PermissionStatus(
            accessibilityService = UIAccessibilityService.isRunning(),
            overlayPermission = true, // TODO: 检查悬浮窗权限
            screenshotPermission = true, // TODO: 检查截图权限
            shizukuStatus = false // TODO: 检查Shizuku状态
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
        actionExecutor.cleanup()
        screenSensor.cleanup()
    }
}