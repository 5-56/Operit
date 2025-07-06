package com.ai.assistance.operit.core.ai.speech

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.ai.speech.engines.AndroidSTTEngine
import com.ai.assistance.operit.core.ai.speech.engines.SherpaNCNNEngine
import com.ai.assistance.operit.core.ai.speech.engines.WhisperEngine
import com.ai.assistance.operit.core.ai.speech.engines.FallbackSTTEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 多引擎语音识别管理器
 * 统一管理Android原生、Sherpa-NCNN、Whisper等多个STT引擎
 * 提供智能选择、降级策略和统一接口
 */
class MultiSTTEngineManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    
    companion object {
        private const val TAG = "MultiSTTEngineManager"
        
        // 引擎优先级（数字越小优先级越高）
        private val ENGINE_PRIORITY = mapOf(
            STTEngine.EngineType.WHISPER to 1,        // 最高精度
            STTEngine.EngineType.SHERPA_NCNN to 2,    // 中等精度，离线
            STTEngine.EngineType.ANDROID_NATIVE to 3, // 快速响应
            STTEngine.EngineType.FALLBACK to 4        // 降级选择
        )
        
        // 引擎选择策略
        enum class EngineSelectionStrategy {
            PRIORITY,      // 按优先级选择
            FASTEST,       // 选择最快的引擎
            MOST_ACCURATE, // 选择最准确的引擎
            OFFLINE_ONLY,  // 仅使用离线引擎
            ADAPTIVE       // 自适应选择
        }
        
        // 识别模式
        enum class RecognitionMode {
            SINGLE_SHOT,   // 单次识别
            STREAMING,     // 流式识别
            HYBRID         // 混合模式
        }
    }
    
    // 引擎实例
    private val engines = mutableMapOf<STTEngine.EngineType, STTEngine>()
    
    // 管理器状态
    private val _managerStatus = MutableStateFlow(ManagerStatus.UNINITIALIZED)
    val managerStatus: Flow<ManagerStatus> = _managerStatus.asStateFlow()
    
    // 识别结果流
    private val _recognitionResults = MutableSharedFlow<RecognitionEvent>()
    val recognitionResults: Flow<RecognitionEvent> = _recognitionResults.asSharedFlow()
    
    // 当前配置
    private var currentConfig = STTEngine.EngineConfig()
    private var selectionStrategy = EngineSelectionStrategy.ADAPTIVE
    private var recognitionMode = RecognitionMode.HYBRID
    
    // 运行状态
    private val isInitialized = AtomicBoolean(false)
    private val isRecognizing = AtomicBoolean(false)
    private val currentEngine = AtomicReference<STTEngine?>(null)
    
    // 性能统计
    private val performanceStats = mutableMapOf<STTEngine.EngineType, EnginePerformance>()
    
    enum class ManagerStatus {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        RECOGNIZING,
        ERROR
    }
    
    data class RecognitionEvent(
        val result: STTEngine.RecognitionResult,
        val engineType: STTEngine.EngineType,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class EnginePerformance(
        var totalRecognitions: Int = 0,
        var successfulRecognitions: Int = 0,
        var totalProcessingTime: Long = 0,
        var averageConfidence: Float = 0.0f,
        var lastUsed: Long = 0
    ) {
        val successRate: Float get() = if (totalRecognitions > 0) successfulRecognitions.toFloat() / totalRecognitions else 0.0f
        val averageProcessingTime: Float get() = if (totalRecognitions > 0) totalProcessingTime.toFloat() / totalRecognitions else 0.0f
    }
    
    /**
     * 初始化管理器
     */
    suspend fun initialize(
        config: STTEngine.EngineConfig = STTEngine.EngineConfig(),
        strategy: EngineSelectionStrategy = EngineSelectionStrategy.ADAPTIVE,
        mode: RecognitionMode = RecognitionMode.HYBRID
    ): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                if (isInitialized.get()) {
                    Log.w(TAG, "管理器已经初始化")
                    return@withContext true
                }
                
                _managerStatus.value = ManagerStatus.INITIALIZING
                
                currentConfig = config
                selectionStrategy = strategy
                recognitionMode = mode
                
                Log.d(TAG, "开始初始化多引擎STT管理器")
                
                // 初始化所有引擎
                val initResults = initializeEngines(config)
                
                // 至少需要一个引擎成功初始化
                if (initResults.values.any { it }) {
                    isInitialized.set(true)
                    _managerStatus.value = ManagerStatus.READY
                    Log.d(TAG, "多引擎STT管理器初始化成功")
                    
                    // 记录初始化结果
                    logInitializationResults(initResults)
                    
                    true
                } else {
                    Log.e(TAG, "所有引擎初始化失败")
                    _managerStatus.value = ManagerStatus.ERROR
                    false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "管理器初始化失败", e)
                _managerStatus.value = ManagerStatus.ERROR
                false
            }
        }
    }
    
    private suspend fun initializeEngines(config: STTEngine.EngineConfig): Map<STTEngine.EngineType, Boolean> {
        val results = mutableMapOf<STTEngine.EngineType, Boolean>()
        
        // 初始化Whisper引擎
        try {
            val whisperEngine = WhisperEngine()
            val whisperResult = whisperEngine.initialize(context, config)
            engines[STTEngine.EngineType.WHISPER] = whisperEngine
            results[STTEngine.EngineType.WHISPER] = whisperResult
            
            if (whisperResult) {
                performanceStats[STTEngine.EngineType.WHISPER] = EnginePerformance()
                Log.d(TAG, "Whisper引擎初始化成功")
            } else {
                Log.w(TAG, "Whisper引擎初始化失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Whisper引擎初始化异常", e)
            results[STTEngine.EngineType.WHISPER] = false
        }
        
        // 初始化Sherpa-NCNN引擎
        try {
            val sherpaEngine = SherpaNCNNEngine()
            val sherpaResult = sherpaEngine.initialize(context, config)
            engines[STTEngine.EngineType.SHERPA_NCNN] = sherpaEngine
            results[STTEngine.EngineType.SHERPA_NCNN] = sherpaResult
            
            if (sherpaResult) {
                performanceStats[STTEngine.EngineType.SHERPA_NCNN] = EnginePerformance()
                Log.d(TAG, "Sherpa-NCNN引擎初始化成功")
            } else {
                Log.w(TAG, "Sherpa-NCNN引擎初始化失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa-NCNN引擎初始化异常", e)
            results[STTEngine.EngineType.SHERPA_NCNN] = false
        }
        
        // 初始化Android原生引擎
        try {
            val androidEngine = AndroidSTTEngine()
            val androidResult = androidEngine.initialize(context, config)
            engines[STTEngine.EngineType.ANDROID_NATIVE] = androidEngine
            results[STTEngine.EngineType.ANDROID_NATIVE] = androidResult
            
            if (androidResult) {
                performanceStats[STTEngine.EngineType.ANDROID_NATIVE] = EnginePerformance()
                Log.d(TAG, "Android原生引擎初始化成功")
            } else {
                Log.w(TAG, "Android原生引擎初始化失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Android原生引擎初始化异常", e)
            results[STTEngine.EngineType.ANDROID_NATIVE] = false
        }
        
        // 初始化降级引擎（总是成功）
        try {
            val fallbackEngine = FallbackSTTEngine()
            val fallbackResult = fallbackEngine.initialize(context, config)
            engines[STTEngine.EngineType.FALLBACK] = fallbackEngine
            results[STTEngine.EngineType.FALLBACK] = fallbackResult
            
            if (fallbackResult) {
                performanceStats[STTEngine.EngineType.FALLBACK] = EnginePerformance()
                Log.d(TAG, "降级引擎初始化成功")
            } else {
                Log.w(TAG, "降级引擎初始化失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "降级引擎初始化异常", e)
            results[STTEngine.EngineType.FALLBACK] = false
        }
        
        return results
    }
    
    /**
     * 单次语音识别
     */
    suspend fun recognizeOnce(audioData: ByteArray): STTEngine.RecognitionResult? {
        return withContext(Dispatchers.Default) {
            if (!isInitialized.get()) {
                Log.w(TAG, "管理器未初始化")
                return@withContext null
            }
            
            if (isRecognizing.get()) {
                Log.w(TAG, "正在识别中，请等待")
                return@withContext null
            }
            
            try {
                isRecognizing.set(true)
                _managerStatus.value = ManagerStatus.RECOGNIZING
                
                val selectedEngine = selectEngine()
                if (selectedEngine == null) {
                    Log.e(TAG, "没有可用的引擎")
                    return@withContext null
                }
                
                currentEngine.set(selectedEngine)
                
                val startTime = System.currentTimeMillis()
                val result = selectedEngine.recognizeOnce(audioData)
                val endTime = System.currentTimeMillis()
                
                // 更新性能统计
                updatePerformanceStats(selectedEngine.engineType, result, endTime - startTime)
                
                // 发送识别结果
                _recognitionResults.emit(RecognitionEvent(result, selectedEngine.engineType))
                
                Log.d(TAG, "识别完成: ${result.text} (${selectedEngine.engineType})")
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "识别失败", e)
                
                // 尝试降级到其他引擎
                val fallbackResult = performFallbackRecognition(audioData)
                fallbackResult
                
            } finally {
                isRecognizing.set(false)
                currentEngine.set(null)
                _managerStatus.value = ManagerStatus.READY
            }
        }
    }
    
    /**
     * 开始流式识别
     */
    suspend fun startStreamingRecognition(callback: (STTEngine.RecognitionResult) -> Unit): Boolean {
        return withContext(Dispatchers.Default) {
            if (!isInitialized.get()) {
                Log.w(TAG, "管理器未初始化")
                return@withContext false
            }
            
            if (isRecognizing.get()) {
                Log.w(TAG, "正在识别中")
                return@withContext false
            }
            
            try {
                val selectedEngine = selectEngine()
                if (selectedEngine == null) {
                    Log.e(TAG, "没有可用的引擎")
                    return@withContext false
                }
                
                currentEngine.set(selectedEngine)
                isRecognizing.set(true)
                _managerStatus.value = ManagerStatus.RECOGNIZING
                
                val success = selectedEngine.startStreamingRecognition { result ->
                    // 更新性能统计
                    updatePerformanceStats(selectedEngine.engineType, result, result.processingTimeMs)
                    
                    // 发送识别结果
                    coroutineScope.launch {
                        _recognitionResults.emit(RecognitionEvent(result, selectedEngine.engineType))
                        callback(result)
                    }
                }
                
                if (success) {
                    Log.d(TAG, "流式识别已启动: ${selectedEngine.engineType}")
                } else {
                    Log.e(TAG, "流式识别启动失败")
                    isRecognizing.set(false)
                    currentEngine.set(null)
                    _managerStatus.value = ManagerStatus.READY
                }
                
                success
                
            } catch (e: Exception) {
                Log.e(TAG, "启动流式识别失败", e)
                isRecognizing.set(false)
                currentEngine.set(null)
                _managerStatus.value = ManagerStatus.READY
                false
            }
        }
    }
    
    /**
     * 输入音频数据到流式识别
     */
    suspend fun feedAudioData(audioData: ByteArray) {
        withContext(Dispatchers.Default) {
            val engine = currentEngine.get()
            if (engine != null && isRecognizing.get()) {
                try {
                    engine.feedAudioData(audioData)
                } catch (e: Exception) {
                    Log.e(TAG, "输入音频数据失败", e)
                }
            }
        }
    }
    
    /**
     * 停止流式识别
     */
    suspend fun stopStreamingRecognition() {
        withContext(Dispatchers.Default) {
            try {
                val engine = currentEngine.get()
                if (engine != null) {
                    engine.stopStreamingRecognition()
                    Log.d(TAG, "流式识别已停止")
                }
            } catch (e: Exception) {
                Log.e(TAG, "停止流式识别失败", e)
            } finally {
                isRecognizing.set(false)
                currentEngine.set(null)
                _managerStatus.value = ManagerStatus.READY
            }
        }
    }
    
    /**
     * 选择最佳引擎
     */
    private suspend fun selectEngine(): STTEngine? {
        val availableEngines = engines.values.filter { it.isAvailable() }
        
        if (availableEngines.isEmpty()) {
            Log.w(TAG, "没有可用的引擎")
            return null
        }
        
        return when (selectionStrategy) {
            EngineSelectionStrategy.PRIORITY -> selectByPriority(availableEngines)
            EngineSelectionStrategy.FASTEST -> selectFastestEngine(availableEngines)
            EngineSelectionStrategy.MOST_ACCURATE -> selectMostAccurateEngine(availableEngines)
            EngineSelectionStrategy.OFFLINE_ONLY -> selectOfflineEngine(availableEngines)
            EngineSelectionStrategy.ADAPTIVE -> selectAdaptiveEngine(availableEngines)
        }
    }
    
    private fun selectByPriority(engines: List<STTEngine>): STTEngine? {
        return engines.minByOrNull { ENGINE_PRIORITY[it.engineType] ?: Int.MAX_VALUE }
    }
    
    private fun selectFastestEngine(engines: List<STTEngine>): STTEngine? {
        return engines.minByOrNull { 
            performanceStats[it.engineType]?.averageProcessingTime ?: Float.MAX_VALUE
        }
    }
    
    private fun selectMostAccurateEngine(engines: List<STTEngine>): STTEngine? {
        return engines.maxByOrNull { 
            performanceStats[it.engineType]?.averageConfidence ?: 0.0f
        }
    }
    
    private fun selectOfflineEngine(engines: List<STTEngine>): STTEngine? {
        return engines.firstOrNull { 
            it.engineType == STTEngine.EngineType.WHISPER || 
            it.engineType == STTEngine.EngineType.SHERPA_NCNN
        }
    }
    
    private fun selectAdaptiveEngine(engines: List<STTEngine>): STTEngine? {
        // 自适应选择：综合考虑性能、准确率和可用性
        return engines.maxByOrNull { engine ->
            val stats = performanceStats[engine.engineType]
            val priority = ENGINE_PRIORITY[engine.engineType] ?: Int.MAX_VALUE
            val successRate = stats?.successRate ?: 0.0f
            val confidence = stats?.averageConfidence ?: 0.0f
            val speed = if (stats?.averageProcessingTime ?: Float.MAX_VALUE > 0) {
                1000.0f / (stats?.averageProcessingTime ?: Float.MAX_VALUE)
            } else 0.0f
            
            // 综合评分
            (successRate * 0.4f + confidence * 0.3f + speed * 0.2f) / priority
        }
    }
    
    private suspend fun performFallbackRecognition(audioData: ByteArray): STTEngine.RecognitionResult? {
        // 尝试其他可用引擎
        val availableEngines = engines.values.filter { it.isAvailable() }
        
        for (engine in availableEngines) {
            try {
                val result = engine.recognizeOnce(audioData)
                Log.d(TAG, "降级识别成功: ${result.text} (${engine.engineType})")
                return result
            } catch (e: Exception) {
                Log.w(TAG, "降级引擎${engine.engineType}识别失败", e)
            }
        }
        
        return null
    }
    
    private fun updatePerformanceStats(
        engineType: STTEngine.EngineType,
        result: STTEngine.RecognitionResult,
        processingTime: Long
    ) {
        val stats = performanceStats[engineType] ?: EnginePerformance()
        
        stats.totalRecognitions++
        if (result.text.isNotEmpty()) {
            stats.successfulRecognitions++
        }
        stats.totalProcessingTime += processingTime
        
        // 更新平均置信度
        val totalConfidence = stats.averageConfidence * (stats.totalRecognitions - 1) + result.confidence
        stats.averageConfidence = totalConfidence / stats.totalRecognitions
        
        stats.lastUsed = System.currentTimeMillis()
        
        performanceStats[engineType] = stats
    }
    
    private fun logInitializationResults(results: Map<STTEngine.EngineType, Boolean>) {
        Log.d(TAG, "引擎初始化结果:")
        results.forEach { (type, success) ->
            Log.d(TAG, "  ${type.name}: ${if (success) "成功" else "失败"}")
        }
    }
    
    /**
     * 获取引擎状态
     */
    fun getEngineStatus(): Map<String, Any> {
        return mapOf(
            "isInitialized" to isInitialized.get(),
            "isRecognizing" to isRecognizing.get(),
            "managerStatus" to _managerStatus.value.name,
            "currentEngine" to (currentEngine.get()?.engineType?.name ?: "None"),
            "availableEngines" to engines.values.filter { it.isAvailable() }.map { it.engineType.name },
            "engineCount" to engines.size,
            "selectionStrategy" to selectionStrategy.name,
            "recognitionMode" to recognitionMode.name,
            "performanceStats" to performanceStats.mapKeys { it.key.name }
        )
    }
    
    /**
     * 更新配置
     */
    suspend fun updateConfig(config: STTEngine.EngineConfig) {
        withContext(Dispatchers.Default) {
            currentConfig = config
            engines.values.forEach { engine ->
                try {
                    engine.updateConfig(config)
                } catch (e: Exception) {
                    Log.e(TAG, "更新引擎配置失败: ${engine.engineType}", e)
                }
            }
            Log.d(TAG, "配置已更新")
        }
    }
    
    /**
     * 设置引擎选择策略
     */
    fun setSelectionStrategy(strategy: EngineSelectionStrategy) {
        selectionStrategy = strategy
        Log.d(TAG, "引擎选择策略已更新: ${strategy.name}")
    }
    
    /**
     * 设置识别模式
     */
    fun setRecognitionMode(mode: RecognitionMode) {
        recognitionMode = mode
        Log.d(TAG, "识别模式已更新: ${mode.name}")
    }
    
    /**
     * 获取支持的语言
     */
    suspend fun getSupportedLanguages(): List<String> {
        return engines.values.flatMap { it.getSupportedLanguages() }.distinct()
    }
    
    /**
     * 释放资源
     */
    suspend fun release() {
        withContext(Dispatchers.Default) {
            try {
                // 停止流式识别
                stopStreamingRecognition()
                
                // 释放所有引擎
                engines.values.forEach { engine ->
                    try {
                        engine.release()
                        Log.d(TAG, "引擎已释放: ${engine.engineType}")
                    } catch (e: Exception) {
                        Log.e(TAG, "释放引擎失败: ${engine.engineType}", e)
                    }
                }
                
                engines.clear()
                performanceStats.clear()
                
                isInitialized.set(false)
                isRecognizing.set(false)
                currentEngine.set(null)
                _managerStatus.value = ManagerStatus.UNINITIALIZED
                
                Log.d(TAG, "多引擎STT管理器已释放")
                
            } catch (e: Exception) {
                Log.e(TAG, "释放管理器失败", e)
            }
        }
    }
}