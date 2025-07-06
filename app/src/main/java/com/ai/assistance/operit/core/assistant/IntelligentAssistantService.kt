package com.ai.assistance.operit.core.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.MainActivity
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.ai.hybrid.HybridAIEngine
import com.ai.assistance.operit.core.ai.local.LocalSTTEngine
import com.ai.assistance.operit.core.ai.local.LocalTTSEngine
import com.ai.assistance.operit.core.ai.local.VoiceWakeUpDetector
import com.ai.assistance.operit.core.system.SystemResourceManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 智能助手服务 - 升级版
 * 集成混合AI引擎、系统资源管理和本地模型训练
 */
class IntelligentAssistantService : Service() {
    
    companion object {
        private const val TAG = "IntelligentAssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "intelligent_assistant_channel"
        private const val WAKE_UP_KEYWORD = "小助手"
        private const val CONVERSATION_TIMEOUT_MS = 30000L // 30秒对话超时
        private const val TRAINING_SCHEDULE_INTERVAL_MS = 1800000L // 30分钟检查一次训练
        
        // 服务操作常量
        const val ACTION_START_LISTENING = "com.ai.assistance.operit.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.ai.assistance.operit.STOP_LISTENING"
        const val ACTION_FORCE_ONLINE = "com.ai.assistance.operit.FORCE_ONLINE"
        const val ACTION_START_TRAINING = "com.ai.assistance.operit.START_TRAINING"
        const val ACTION_OPTIMIZE_SYSTEM = "com.ai.assistance.operit.OPTIMIZE_SYSTEM"
    }
    
    // 核心组件
    private lateinit var hybridAIEngine: HybridAIEngine
    private lateinit var systemResourceManager: SystemResourceManager
    private lateinit var voiceWakeUpDetector: VoiceWakeUpDetector
    private lateinit var sttEngine: LocalSTTEngine
    private lateinit var ttsEngine: LocalTTSEngine
    private lateinit var aiToolHandler: AIToolHandler
    private lateinit var preferencesManager: UserPreferencesManager
    
    // 系统组件
    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 服务状态
    private val isServiceRunning = AtomicBoolean(false)
    private val isListening = AtomicBoolean(false)
    private val isInConversation = AtomicBoolean(false)
    private val isTraining = AtomicBoolean(false)
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 状态流
    private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
    val serviceState: StateFlow<ServiceState> = _serviceState
    
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState
    
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics
    
    // 对话管理
    private var currentConversationId: String? = null
    private var conversationStartTime: Long = 0
    private var conversationTimeout: Job? = null
    
    // 统计数据
    private var totalInteractions = 0
    private var successfulInteractions = 0
    private var averageResponseTime = 0f
    private var lastInteractionTime = 0L
    
    enum class ServiceState {
        STOPPED,
        STARTING,
        LISTENING,
        PROCESSING,
        TRAINING,
        OPTIMIZING,
        ERROR
    }
    
    enum class ConversationState {
        IDLE,
        WAKE_UP_DETECTED,
        LISTENING_FOR_COMMAND,
        PROCESSING_COMMAND,
        RESPONDING,
        WAITING_FOR_FOLLOWUP,
        TIMEOUT
    }
    
    data class PerformanceMetrics(
        val totalInteractions: Int = 0,
        val successfulInteractions: Int = 0,
        val averageResponseTime: Float = 0f,
        val lastInteractionTime: Long = 0L,
        val systemResourceUsage: SystemResourceManager.SystemStats = SystemResourceManager.SystemStats(),
        val engineState: HybridAIEngine.EngineState = HybridAIEngine.EngineState.INITIALIZING,
        val trainingProgress: HybridAIEngine.TrainingProgress = HybridAIEngine.TrainingProgress(),
        val isSystemOptimized: Boolean = false
    )
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "智能助手服务创建")
        
        // 初始化系统组件
        initializeSystemComponents()
        
        // 初始化核心组件
        initializeCoreComponents()
        
        // 创建通知渠道
        createNotificationChannel()
        
        Log.d(TAG, "智能助手服务创建完成")
    }
    
    private fun initializeSystemComponents() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        preferencesManager = UserPreferencesManager(this)
    }
    
    private fun initializeCoreComponents() {
        try {
            // 初始化混合AI引擎
            hybridAIEngine = HybridAIEngine(this)
            
            // 初始化系统资源管理器
            systemResourceManager = SystemResourceManager(this)
            
            // 初始化语音组件
            voiceWakeUpDetector = VoiceWakeUpDetector(this)
            sttEngine = LocalSTTEngine(this)
            ttsEngine = LocalTTSEngine(this)
            
            // 初始化工具处理器
            aiToolHandler = AIToolHandler(this)
            
            Log.d(TAG, "核心组件初始化完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "核心组件初始化失败", e)
            _serviceState.value = ServiceState.ERROR
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "智能助手服务启动命令: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_FORCE_ONLINE -> forceOnlineMode()
            ACTION_START_TRAINING -> startModelTraining()
            ACTION_OPTIMIZE_SYSTEM -> optimizeSystemResources()
            else -> startService()
        }
        
        return START_STICKY // 服务被系统杀死后自动重启
    }
    
    private fun startService() {
        if (isServiceRunning.get()) {
            Log.w(TAG, "服务已经在运行中")
            return
        }
        
        serviceScope.launch {
            try {
                _serviceState.value = ServiceState.STARTING
                
                // 启动前台服务
                startForeground(NOTIFICATION_ID, createServiceNotification())
                
                // 获取WakeLock
                acquireWakeLock()
                
                // 启动核心功能
                startCoreServices()
                
                // 启动监控任务
                startMonitoringTasks()
                
                // 启动自动化任务
                startAutomationTasks()
                
                isServiceRunning.set(true)
                _serviceState.value = ServiceState.LISTENING
                
                Log.d(TAG, "智能助手服务启动完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "服务启动失败", e)
                _serviceState.value = ServiceState.ERROR
            }
        }
    }
    
    private suspend fun startCoreServices() {
        // 启动语音唤醒检测
        voiceWakeUpDetector.startListening { keyword ->
            if (keyword == WAKE_UP_KEYWORD) {
                onWakeUpDetected()
            }
        }
        
        // 启动语音合成引擎
        ttsEngine.initialize()
        
        // 启动语音识别引擎
        sttEngine.initialize()
        
        Log.d(TAG, "核心服务启动完成")
    }
    
    private fun startMonitoringTasks() {
        // 性能监控任务
        serviceScope.launch {
            while (isServiceRunning.get()) {
                try {
                    updatePerformanceMetrics()
                    delay(10000) // 每10秒更新一次
                } catch (e: Exception) {
                    Log.e(TAG, "性能监控异常", e)
                }
            }
        }
        
        // 健康检查任务
        serviceScope.launch {
            while (isServiceRunning.get()) {
                try {
                    performHealthCheck()
                    delay(60000) // 每分钟检查一次
                } catch (e: Exception) {
                    Log.e(TAG, "健康检查异常", e)
                }
            }
        }
    }
    
    private fun startAutomationTasks() {
        // 自动训练调度
        serviceScope.launch {
            while (isServiceRunning.get()) {
                try {
                    delay(TRAINING_SCHEDULE_INTERVAL_MS)
                    
                    // 检查是否应该开始训练
                    val shouldStartTraining = shouldAutoStartTraining()
                    if (shouldStartTraining) {
                        Log.d(TAG, "触发自动训练")
                        startModelTraining()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "自动训练调度异常", e)
                }
            }
        }
        
        // 自动系统优化
        serviceScope.launch {
            while (isServiceRunning.get()) {
                try {
                    delay(600000) // 每10分钟检查一次
                    
                    // 检查系统资源状态
                    if (systemResourceManager.isLowMemory() || systemResourceManager.isLowStorage()) {
                        Log.d(TAG, "检测到资源不足，执行自动清理")
                        systemResourceManager.performSystemCleanup()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "自动系统优化异常", e)
                }
            }
        }
    }
    
    private fun onWakeUpDetected() {
        serviceScope.launch {
            try {
                Log.d(TAG, "检测到语音唤醒")
                
                _conversationState.value = ConversationState.WAKE_UP_DETECTED
                currentConversationId = generateConversationId()
                conversationStartTime = System.currentTimeMillis()
                
                // 播放唤醒提示音
                ttsEngine.speak("我在，请说")
                
                // 开始监听用户命令
                startListeningForCommand()
                
            } catch (e: Exception) {
                Log.e(TAG, "处理语音唤醒异常", e)
            }
        }
    }
    
    private suspend fun startListeningForCommand() {
        _conversationState.value = ConversationState.LISTENING_FOR_COMMAND
        
        // 设置对话超时
        conversationTimeout = serviceScope.launch {
            delay(CONVERSATION_TIMEOUT_MS)
            onConversationTimeout()
        }
        
        // 开始语音识别
        sttEngine.startListening { spokenText ->
            if (spokenText.isNotBlank()) {
                conversationTimeout?.cancel()
                processUserCommand(spokenText)
            }
        }
    }
    
    private fun processUserCommand(command: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "处理用户命令: $command")
                
                _conversationState.value = ConversationState.PROCESSING_COMMAND
                val processingStartTime = System.currentTimeMillis()
                
                // 更新用户交互时间
                systemResourceManager.updateUserInteraction()
                
                // 处理命令
                val result = hybridAIEngine.processInput(
                    input = command,
                    toolHandler = aiToolHandler,
                    enableLearning = preferencesManager.isLearningEnabled()
                )
                
                val processingTime = System.currentTimeMillis() - processingStartTime
                
                // 更新统计数据
                updateInteractionStats(processingTime, result.confidence > 0.5f)
                
                // 响应用户
                respondToUser(result.response)
                
                // 检查是否需要继续对话
                if (shouldContinueConversation(result.response)) {
                    startListeningForCommand()
                } else {
                    endConversation()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "处理用户命令失败", e)
                respondToUser("抱歉，我处理您的请求时遇到了问题")
                endConversation()
            }
        }
    }
    
    private suspend fun respondToUser(response: String) {
        _conversationState.value = ConversationState.RESPONDING
        
        // 使用TTS回复用户
        ttsEngine.speak(response)
        
        Log.d(TAG, "回复用户: $response")
    }
    
    private fun shouldContinueConversation(response: String): Boolean {
        // 检查响应是否暗示需要继续对话
        val continueKeywords = listOf("还有什么", "其他", "继续", "更多")
        return continueKeywords.any { response.contains(it) }
    }
    
    private fun endConversation() {
        conversationTimeout?.cancel()
        _conversationState.value = ConversationState.IDLE
        currentConversationId = null
        conversationStartTime = 0
        
        Log.d(TAG, "对话结束")
    }
    
    private fun onConversationTimeout() {
        _conversationState.value = ConversationState.TIMEOUT
        serviceScope.launch {
            ttsEngine.speak("对话超时，如需帮助请重新唤醒我")
            endConversation()
        }
    }
    
    private fun startListening() {
        if (!isListening.get()) {
            isListening.set(true)
            voiceWakeUpDetector.resumeListening()
            Log.d(TAG, "开始语音监听")
        }
    }
    
    private fun stopListening() {
        if (isListening.get()) {
            isListening.set(false)
            voiceWakeUpDetector.pauseListening()
            Log.d(TAG, "停止语音监听")
        }
    }
    
    private fun forceOnlineMode() {
        serviceScope.launch {
            try {
                hybridAIEngine.setEngineMode(HybridAIEngine.EngineState.ONLINE_MODE)
                Log.d(TAG, "强制切换到在线模式")
            } catch (e: Exception) {
                Log.e(TAG, "切换在线模式失败", e)
            }
        }
    }
    
    private fun startModelTraining() {
        if (isTraining.get()) {
            Log.w(TAG, "模型训练已在进行中")
            return
        }
        
        serviceScope.launch {
            try {
                isTraining.set(true)
                _serviceState.value = ServiceState.TRAINING
                
                Log.d(TAG, "开始本地模型训练")
                
                // 优化系统资源用于训练
                systemResourceManager.optimizeForTraining(
                    pauseApps = true,
                    cleanMemory = true,
                    boostCPU = true
                )
                
                // 开始训练
                hybridAIEngine.startModelTraining(
                    enableResourceOptimization = true,
                    priority = HybridAIEngine.TrainingPriority.NORMAL
                )
                
                Log.d(TAG, "模型训练完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "模型训练失败", e)
            } finally {
                isTraining.set(false)
                _serviceState.value = ServiceState.LISTENING
                
                // 恢复系统资源
                systemResourceManager.restoreSystemState()
            }
        }
    }
    
    private fun optimizeSystemResources() {
        serviceScope.launch {
            try {
                _serviceState.value = ServiceState.OPTIMIZING
                
                Log.d(TAG, "开始系统资源优化")
                
                val cleanupResult = systemResourceManager.performSystemCleanup()
                Log.d(TAG, "系统清理完成: $cleanupResult")
                
                // 可选：进一步优化
                if (preferencesManager.isAggressiveOptimizationEnabled()) {
                    systemResourceManager.optimizeForTraining(
                        pauseApps = false,
                        cleanMemory = true,
                        boostCPU = false
                    )
                }
                
                Log.d(TAG, "系统资源优化完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "系统资源优化失败", e)
            } finally {
                _serviceState.value = ServiceState.LISTENING
            }
        }
    }
    
    private fun shouldAutoStartTraining(): Boolean {
        // 检查是否应该自动开始训练
        val engineStatus = hybridAIEngine.getEngineStatus()
        val trainingDataCount = engineStatus["trainingDataCount"] as? Int ?: 0
        val lastTrainingTime = engineStatus["lastTrainingTime"] as? Long ?: 0
        
        val timeSinceLastTraining = System.currentTimeMillis() - lastTrainingTime
        
        return trainingDataCount >= 20 && // 至少有20条训练数据
                timeSinceLastTraining > 3600000L && // 距离上次训练超过1小时
                systemResourceManager.isIdleTime() && // 系统空闲时间
                !isTraining.get() // 当前没有在训练
    }
    
    private fun updatePerformanceMetrics() {
        val engineStatus = hybridAIEngine.getEngineStatus()
        val systemStats = systemResourceManager.getSystemStats()
        val trainingProgress = hybridAIEngine.trainingProgress.value
        
        _performanceMetrics.value = PerformanceMetrics(
            totalInteractions = totalInteractions,
            successfulInteractions = successfulInteractions,
            averageResponseTime = averageResponseTime,
            lastInteractionTime = lastInteractionTime,
            systemResourceUsage = systemStats,
            engineState = hybridAIEngine.engineState.value,
            trainingProgress = trainingProgress,
            isSystemOptimized = systemResourceManager.isOptimized()
        )
    }
    
    private fun updateInteractionStats(processingTime: Long, isSuccessful: Boolean) {
        totalInteractions++
        if (isSuccessful) {
            successfulInteractions++
        }
        
        // 更新平均响应时间
        averageResponseTime = (averageResponseTime * (totalInteractions - 1) + processingTime) / totalInteractions
        lastInteractionTime = System.currentTimeMillis()
    }
    
    private fun performHealthCheck() {
        // 检查各组件健康状态
        val issues = mutableListOf<String>()
        
        // 检查AI引擎状态
        if (hybridAIEngine.engineState.value == HybridAIEngine.EngineState.ERROR) {
            issues.add("AI引擎异常")
        }
        
        // 检查系统资源
        if (systemResourceManager.isLowMemory()) {
            issues.add("内存不足")
        }
        
        if (systemResourceManager.isLowStorage()) {
            issues.add("存储空间不足")
        }
        
        // 检查语音组件
        if (!voiceWakeUpDetector.isListening()) {
            issues.add("语音唤醒检测异常")
        }
        
        if (issues.isNotEmpty()) {
            Log.w(TAG, "健康检查发现问题: ${issues.joinToString(", ")}")
            
            // 尝试自动修复
            attemptAutoRecovery(issues)
        }
    }
    
    private fun attemptAutoRecovery(issues: List<String>) {
        serviceScope.launch {
            try {
                Log.d(TAG, "尝试自动恢复")
                
                // 重启语音检测
                if (issues.contains("语音唤醒检测异常")) {
                    voiceWakeUpDetector.restart()
                }
                
                // 清理内存
                if (issues.contains("内存不足")) {
                    systemResourceManager.performSystemCleanup()
                }
                
                // 重新初始化AI引擎
                if (issues.contains("AI引擎异常")) {
                    // 这里可能需要重新初始化AI引擎
                    Log.w(TAG, "AI引擎需要手动重启")
                }
                
                Log.d(TAG, "自动恢复完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "自动恢复失败", e)
            }
        }
    }
    
    private fun generateConversationId(): String {
        return "conv_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun acquireWakeLock() {
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$TAG:IntelligentAssistant"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10分钟超时
            Log.d(TAG, "WakeLock已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取WakeLock失败", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
            wakeLock = null
            Log.d(TAG, "WakeLock已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放WakeLock失败", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "智能助手服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "智能助手后台服务通知"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )
        
        val engineState = hybridAIEngine.engineState.value
        val statusText = when (engineState) {
            HybridAIEngine.EngineState.ONLINE_MODE -> "在线模式"
            HybridAIEngine.EngineState.OFFLINE_MODE -> "离线模式"
            HybridAIEngine.EngineState.HYBRID_MODE -> "混合模式"
            HybridAIEngine.EngineState.TRAINING_MODE -> "训练模式"
            else -> "初始化中"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("智能助手正在运行")
            .setContentText("模式: $statusText | 交互次数: $totalInteractions")
            .setSmallIcon(R.drawable.ic_assistant)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    private fun updateNotification() {
        if (isServiceRunning.get()) {
            val notification = createServiceNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            Log.d(TAG, "智能助手服务销毁")
            
            isServiceRunning.set(false)
            _serviceState.value = ServiceState.STOPPED
            
            // 取消所有协程
            serviceScope.cancel()
            
            // 释放资源
            releaseWakeLock()
            
            // 释放组件资源
            if (::hybridAIEngine.isInitialized) {
                hybridAIEngine.release()
            }
            
            if (::systemResourceManager.isInitialized) {
                systemResourceManager.release()
            }
            
            if (::voiceWakeUpDetector.isInitialized) {
                voiceWakeUpDetector.release()
            }
            
            if (::sttEngine.isInitialized) {
                sttEngine.release()
            }
            
            if (::ttsEngine.isInitialized) {
                ttsEngine.release()
            }
            
            Log.d(TAG, "智能助手服务销毁完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "服务销毁异常", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // 不支持绑定
    }
    
    // 公共API方法
    fun getCurrentMetrics(): PerformanceMetrics {
        return _performanceMetrics.value
    }
    
    fun getEngineStatus(): Map<String, Any> {
        return hybridAIEngine.getEngineStatus()
    }
    
    fun getSystemStats(): SystemResourceManager.SystemStats {
        return systemResourceManager.getSystemStats()
    }
}