package com.ai.assistance.operit.core.ai.hybrid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.ai.assistance.operit.core.ai.local.LocalAIEngine
import com.ai.assistance.operit.core.ai.local.LocalModelTrainer
import com.ai.assistance.operit.core.ai.remote.RemoteAIService
import com.ai.assistance.operit.core.system.SystemResourceManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.ConversationData
import com.ai.assistance.operit.data.model.TrainingData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 混合AI引擎
 * 支持在线API调用和本地模型，具备实时学习和知识蒸馏功能
 */
class HybridAIEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "HybridAIEngine"
        private const val ONLINE_TIMEOUT_MS = 10000L
        private const val LOCAL_MODEL_CONFIDENCE_THRESHOLD = 0.8f
        private const val TRAINING_BATCH_SIZE = 10
        private const val AUTO_TRAINING_INTERVAL_MS = 300000L // 5分钟
    }
    
    // 核心组件
    private val localAIEngine = LocalAIEngine(context)
    private val remoteAIService = RemoteAIService(context)
    private val localModelTrainer = LocalModelTrainer(context)
    private val systemResourceManager = SystemResourceManager(context)
    
    // 引擎状态
    private val isInitialized = AtomicBoolean(false)
    private val isTrainingMode = AtomicBoolean(false)
    private val lastTrainingTime = AtomicLong(0)
    
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 状态流
    private val _engineState = MutableStateFlow(EngineState.INITIALIZING)
    val engineState: StateFlow<EngineState> = _engineState
    
    private val _trainingProgress = MutableStateFlow(TrainingProgress())
    val trainingProgress: StateFlow<TrainingProgress> = _trainingProgress
    
    // 训练数据收集
    private val trainingDataBuffer = mutableListOf<TrainingData>()
    private val maxBufferSize = 100
    
    // 网络连接管理
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    enum class EngineState {
        INITIALIZING,
        ONLINE_MODE,
        OFFLINE_MODE,
        TRAINING_MODE,
        HYBRID_MODE,
        ERROR
    }
    
    data class TrainingProgress(
        val isTraining: Boolean = false,
        val currentEpoch: Int = 0,
        val totalEpochs: Int = 0,
        val batchProgress: Float = 0f,
        val trainingLoss: Float = 0f,
        val modelAccuracy: Float = 0f,
        val estimatedTimeRemaining: Long = 0L
    )
    
    data class ProcessingResult(
        val response: String,
        val confidence: Float,
        val source: AISource,
        val processingTime: Long,
        val toolsUsed: List<String> = emptyList()
    )
    
    enum class AISource {
        REMOTE_API,
        LOCAL_MODEL,
        HYBRID,
        FALLBACK
    }
    
    init {
        initialize()
    }
    
    private fun initialize() {
        engineScope.launch {
            try {
                Log.d(TAG, "初始化混合AI引擎")
                
                // 初始化系统资源管理器
                systemResourceManager.initialize()
                
                // 初始化本地模型训练器
                localModelTrainer.initialize()
                
                // 检查网络状态并设置初始模式
                updateEngineMode()
                
                // 启动自动训练调度器
                startAutoTrainingScheduler()
                
                // 启动网络状态监听
                startNetworkMonitoring()
                
                isInitialized.set(true)
                Log.d(TAG, "混合AI引擎初始化完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "混合AI引擎初始化失败", e)
                _engineState.value = EngineState.ERROR
            }
        }
    }
    
    /**
     * 处理用户输入的核心方法
     */
    suspend fun processInput(
        input: String,
        toolHandler: AIToolHandler,
        forceOnline: Boolean = false,
        enableLearning: Boolean = true
    ): ProcessingResult {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "处理用户输入: $input")
                
                val result = when {
                    forceOnline || shouldUseOnlineMode(input) -> {
                        processWithOnlineMode(input, toolHandler, enableLearning)
                    }
                    localModelTrainer.isModelReady() -> {
                        processWithHybridMode(input, toolHandler, enableLearning)
                    }
                    else -> {
                        processWithLocalMode(input, toolHandler)
                    }
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                result.copy(processingTime = processingTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "处理输入失败", e)
                ProcessingResult(
                    response = "抱歉，处理您的请求时出现了问题",
                    confidence = 0f,
                    source = AISource.FALLBACK,
                    processingTime = System.currentTimeMillis() - startTime
                )
            }
        }
    }
    
    private suspend fun processWithOnlineMode(
        input: String,
        toolHandler: AIToolHandler,
        enableLearning: Boolean
    ): ProcessingResult {
        Log.d(TAG, "使用在线模式处理")
        _engineState.value = EngineState.ONLINE_MODE
        
        return try {
            val onlineResult = withTimeout(ONLINE_TIMEOUT_MS) {
                remoteAIService.processInput(input, toolHandler)
            }
            
            // 收集训练数据
            if (enableLearning && onlineResult.confidence > 0.7f) {
                collectTrainingData(input, onlineResult.response, onlineResult.toolsUsed)
            }
            
            ProcessingResult(
                response = onlineResult.response,
                confidence = onlineResult.confidence,
                source = AISource.REMOTE_API,
                processingTime = 0,
                toolsUsed = onlineResult.toolsUsed
            )
            
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "在线API超时，降级到本地模式")
            processWithLocalMode(input, toolHandler)
        } catch (e: Exception) {
            Log.e(TAG, "在线模式处理失败", e)
            processWithLocalMode(input, toolHandler)
        }
    }
    
    private suspend fun processWithHybridMode(
        input: String,
        toolHandler: AIToolHandler,
        enableLearning: Boolean
    ): ProcessingResult {
        Log.d(TAG, "使用混合模式处理")
        _engineState.value = EngineState.HYBRID_MODE
        
        // 首先尝试本地模型
        val localResult = localAIEngine.processCommand(input, toolHandler)
        val localConfidence = calculateLocalConfidence(input, localResult)
        
        return if (localConfidence >= LOCAL_MODEL_CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "本地模型置信度足够高，使用本地结果")
            ProcessingResult(
                response = localResult,
                confidence = localConfidence,
                source = AISource.LOCAL_MODEL,
                processingTime = 0
            )
        } else if (isNetworkAvailable()) {
            Log.d(TAG, "本地模型置信度不足，尝试在线模式")
            val onlineResult = processWithOnlineMode(input, toolHandler, enableLearning)
            
            // 使用在线结果训练本地模型
            if (enableLearning && onlineResult.confidence > localConfidence) {
                collectTrainingData(input, onlineResult.response, onlineResult.toolsUsed)
            }
            
            onlineResult.copy(source = AISource.HYBRID)
        } else {
            Log.d(TAG, "网络不可用，使用本地结果")
            ProcessingResult(
                response = localResult,
                confidence = localConfidence,
                source = AISource.LOCAL_MODEL,
                processingTime = 0
            )
        }
    }
    
    private suspend fun processWithLocalMode(
        input: String,
        toolHandler: AIToolHandler
    ): ProcessingResult {
        Log.d(TAG, "使用本地模式处理")
        _engineState.value = EngineState.OFFLINE_MODE
        
        val result = localAIEngine.processCommand(input, toolHandler)
        val confidence = calculateLocalConfidence(input, result)
        
        return ProcessingResult(
            response = result,
            confidence = confidence,
            source = AISource.LOCAL_MODEL,
            processingTime = 0
        )
    }
    
    private fun shouldUseOnlineMode(input: String): Boolean {
        // 判断是否应该使用在线模式的策略
        return when {
            !isNetworkAvailable() -> false
            !localModelTrainer.isModelReady() -> true
            isComplexQuery(input) -> true
            needsLatestKnowledge(input) -> true
            else -> false
        }
    }
    
    private fun isComplexQuery(input: String): Boolean {
        // 检测是否为复杂查询
        val complexKeywords = listOf(
            "编程", "代码", "算法", "数据结构",
            "写作", "文章", "论文", "创作",
            "分析", "计算", "复杂", "详细",
            "解释", "原理", "机制", "实现"
        )
        
        return complexKeywords.any { input.contains(it) } ||
                input.length > 100 ||
                input.count { it == '?' } > 1
    }
    
    private fun needsLatestKnowledge(input: String): Boolean {
        // 检测是否需要最新知识
        val realtimeKeywords = listOf(
            "最新", "今天", "现在", "当前",
            "新闻", "股价", "天气", "实时"
        )
        
        return realtimeKeywords.any { input.contains(it) }
    }
    
    private fun calculateLocalConfidence(input: String, output: String): Float {
        // 简化的置信度计算
        return when {
            output.contains("抱歉") || output.contains("无法") -> 0.3f
            output.length < 10 -> 0.4f
            output.length < 50 -> 0.6f
            containsSpecificKeywords(input, output) -> 0.9f
            else -> 0.7f
        }
    }
    
    private fun containsSpecificKeywords(input: String, output: String): Boolean {
        // 检查输出是否包含输入相关的关键词
        val inputWords = input.split(" ", "，", "。", "？", "！")
        val outputWords = output.split(" ", "，", "。", "？", "！")
        
        val matchCount = inputWords.count { word ->
            word.length > 1 && outputWords.any { it.contains(word) }
        }
        
        return matchCount > inputWords.size / 3
    }
    
    private fun collectTrainingData(input: String, output: String, toolsUsed: List<String>) {
        val trainingData = TrainingData(
            input = input,
            output = output,
            timestamp = System.currentTimeMillis(),
            toolsUsed = toolsUsed,
            quality = 1.0f // 在线API结果默认高质量
        )
        
        synchronized(trainingDataBuffer) {
            trainingDataBuffer.add(trainingData)
            
            // 控制缓冲区大小
            if (trainingDataBuffer.size > maxBufferSize) {
                trainingDataBuffer.removeAt(0)
            }
            
            Log.d(TAG, "收集训练数据，当前缓冲区大小: ${trainingDataBuffer.size}")
        }
    }
    
    /**
     * 开始训练本地模型
     */
    fun startModelTraining(
        enableResourceOptimization: Boolean = true,
        priority: TrainingPriority = TrainingPriority.NORMAL
    ) {
        if (isTrainingMode.get()) {
            Log.w(TAG, "模型训练已在进行中")
            return
        }
        
        engineScope.launch {
            try {
                isTrainingMode.set(true)
                _engineState.value = EngineState.TRAINING_MODE
                
                Log.d(TAG, "开始本地模型训练")
                
                // 资源优化
                if (enableResourceOptimization) {
                    optimizeSystemResources(priority)
                }
                
                // 准备训练数据
                val trainingDataList = synchronized(trainingDataBuffer) {
                    trainingDataBuffer.toList()
                }
                
                if (trainingDataList.isEmpty()) {
                    Log.w(TAG, "没有训练数据，跳过训练")
                    return@launch
                }
                
                // 开始训练
                localModelTrainer.trainModel(
                    trainingData = trainingDataList,
                    progressCallback = { progress ->
                        _trainingProgress.value = progress
                    }
                )
                
                lastTrainingTime.set(System.currentTimeMillis())
                Log.d(TAG, "本地模型训练完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "模型训练失败", e)
            } finally {
                isTrainingMode.set(false)
                
                // 恢复系统资源
                if (enableResourceOptimization) {
                    restoreSystemResources()
                }
                
                updateEngineMode()
            }
        }
    }
    
    private suspend fun optimizeSystemResources(priority: TrainingPriority) {
        Log.d(TAG, "优化系统资源用于模型训练")
        
        when (priority) {
            TrainingPriority.LOW -> {
                systemResourceManager.optimizeForTraining(
                    pauseApps = false,
                    cleanMemory = true,
                    boostCPU = false
                )
            }
            TrainingPriority.NORMAL -> {
                systemResourceManager.optimizeForTraining(
                    pauseApps = true,
                    cleanMemory = true,
                    boostCPU = true
                )
            }
            TrainingPriority.HIGH -> {
                systemResourceManager.optimizeForTraining(
                    pauseApps = true,
                    cleanMemory = true,
                    boostCPU = true,
                    maxPerformanceMode = true
                )
            }
        }
    }
    
    private suspend fun restoreSystemResources() {
        Log.d(TAG, "恢复系统资源")
        systemResourceManager.restoreSystemState()
    }
    
    private fun startAutoTrainingScheduler() {
        engineScope.launch {
            while (isInitialized.get()) {
                try {
                    delay(AUTO_TRAINING_INTERVAL_MS)
                    
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastTraining = currentTime - lastTrainingTime.get()
                    
                    // 检查是否需要自动训练
                    val shouldAutoTrain = timeSinceLastTraining > AUTO_TRAINING_INTERVAL_MS &&
                            trainingDataBuffer.size >= TRAINING_BATCH_SIZE &&
                            !isTrainingMode.get() &&
                            systemResourceManager.isIdleTime()
                    
                    if (shouldAutoTrain) {
                        Log.d(TAG, "触发自动训练")
                        startModelTraining(
                            enableResourceOptimization = true,
                            priority = TrainingPriority.LOW
                        )
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "自动训练调度器异常", e)
                }
            }
        }
    }
    
    private fun startNetworkMonitoring() {
        engineScope.launch {
            while (isInitialized.get()) {
                try {
                    delay(10000) // 每10秒检查一次
                    updateEngineMode()
                } catch (e: Exception) {
                    Log.e(TAG, "网络监控异常", e)
                }
            }
        }
    }
    
    private fun updateEngineMode() {
        if (isTrainingMode.get()) return
        
        val hasNetwork = isNetworkAvailable()
        val hasLocalModel = localModelTrainer.isModelReady()
        
        _engineState.value = when {
            hasNetwork && hasLocalModel -> EngineState.HYBRID_MODE
            hasNetwork -> EngineState.ONLINE_MODE
            hasLocalModel -> EngineState.OFFLINE_MODE
            else -> EngineState.OFFLINE_MODE
        }
        
        Log.d(TAG, "引擎模式更新: ${_engineState.value}")
    }
    
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * 获取引擎状态信息
     */
    fun getEngineStatus(): Map<String, Any> {
        return mapOf(
            "engineState" to _engineState.value.name,
            "isInitialized" to isInitialized.get(),
            "isTraining" to isTrainingMode.get(),
            "hasNetwork" to isNetworkAvailable(),
            "localModelReady" to localModelTrainer.isModelReady(),
            "trainingDataCount" to trainingDataBuffer.size,
            "lastTrainingTime" to lastTrainingTime.get(),
            "systemResourceOptimized" to systemResourceManager.isOptimized()
        )
    }
    
    /**
     * 强制切换到指定模式
     */
    fun setEngineMode(mode: EngineState) {
        if (mode != EngineState.TRAINING_MODE) {
            _engineState.value = mode
            Log.d(TAG, "强制切换引擎模式: $mode")
        }
    }
    
    /**
     * 清理训练数据
     */
    fun clearTrainingData() {
        synchronized(trainingDataBuffer) {
            trainingDataBuffer.clear()
        }
        Log.d(TAG, "训练数据已清理")
    }
    
    /**
     * 导出训练数据
     */
    fun exportTrainingData(): List<TrainingData> {
        return synchronized(trainingDataBuffer) {
            trainingDataBuffer.toList()
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            isInitialized.set(false)
            engineScope.cancel()
            
            localAIEngine.release()
            remoteAIService.release()
            localModelTrainer.release()
            systemResourceManager.release()
            
            Log.d(TAG, "混合AI引擎资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
    }
    
    enum class TrainingPriority {
        LOW,    // 后台训练，不影响用户使用
        NORMAL, // 常规训练，适度优化资源
        HIGH    // 高优先级训练，最大化性能
    }
}