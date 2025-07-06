package com.ai.assistance.operit.core

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI模型管理器
 * 负责AI模型的加载、缓存、预热和生命周期管理
 */
class AIModelManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AIModelManager? = null
        
        fun getInstance(context: Context): AIModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // 模型缓存大小限制
        private const val MODEL_CACHE_SIZE = 3
        private const val PRELOAD_TIMEOUT_MS = 30000L
        
        // 模型类型定义
        const val MODEL_TYPE_CHAT = "chat"
        const val MODEL_TYPE_VOICE = "voice"
        const val MODEL_TYPE_VISION = "vision"
        const val MODEL_TYPE_LOCAL = "local"
    }
    
    // 模型缓存
    private val modelCache = LruCache<String, AIModel>(MODEL_CACHE_SIZE)
    
    // 模型状态管理
    private val modelStates = ConcurrentHashMap<String, ModelState>()
    private val _modelLoadingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val modelLoadingProgress: StateFlow<Map<String, Float>> = _modelLoadingProgress.asStateFlow()
    
    // 协程管理
    private val modelScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("AIModelManager")
    )
    
    // 预加载管理
    private val preloadJobs = ConcurrentHashMap<String, Job>()
    private val isPreloading = AtomicBoolean(false)
    
    // 模型配置
    private val modelConfigs = mutableMapOf<String, ModelConfig>()
    
    init {
        initializeDefaultModels()
    }
    
    /**
     * 初始化默认模型配置
     */
    private fun initializeDefaultModels() {
        // DeepSeek模型配置
        registerModel(ModelConfig(
            id = "deepseek",
            name = "DeepSeek",
            type = MODEL_TYPE_CHAT,
            isLocal = false,
            priority = ModelPriority.HIGH,
            preloadOnStartup = true,
            maxCacheTime = 30 * 60 * 1000L // 30分钟
        ))
        
        // Gemini模型配置
        registerModel(ModelConfig(
            id = "gemini",
            name = "Gemini Pro",
            type = MODEL_TYPE_CHAT,
            isLocal = false,
            priority = ModelPriority.MEDIUM,
            preloadOnStartup = true,
            maxCacheTime = 20 * 60 * 1000L // 20分钟
        ))
        
        // 本地语音模型配置
        registerModel(ModelConfig(
            id = "sherpa-ncnn",
            name = "Sherpa NCNN",
            type = MODEL_TYPE_VOICE,
            isLocal = true,
            priority = ModelPriority.HIGH,
            preloadOnStartup = true,
            maxCacheTime = 60 * 60 * 1000L // 1小时
        ))
        
        // 本地推理模型配置
        registerModel(ModelConfig(
            id = "local-llm",
            name = "Local LLM",
            type = MODEL_TYPE_LOCAL,
            isLocal = true,
            priority = ModelPriority.LOW,
            preloadOnStartup = false,
            maxCacheTime = 45 * 60 * 1000L // 45分钟
        ))
    }
    
    /**
     * 注册模型配置
     */
    fun registerModel(config: ModelConfig) {
        modelConfigs[config.id] = config
        modelStates[config.id] = ModelState.NOT_LOADED
    }
    
    /**
     * 预加载核心模型
     */
    suspend fun preloadCoreModels() {
        if (!isPreloading.compareAndSet(false, true)) {
            return
        }
        
        try {
            val coreModels = modelConfigs.values
                .filter { it.preloadOnStartup }
                .sortedByDescending { it.priority.ordinal }
            
            coreModels.map { config ->
                modelScope.async {
                    preloadModel(config.id)
                }
            }.awaitAll()
            
        } finally {
            isPreloading.set(false)
        }
    }
    
    /**
     * 预加载特定模型
     */
    suspend fun preloadModel(modelId: String): Result<AIModel> {
        return withContext(modelScope.coroutineContext) {
            try {
                val config = modelConfigs[modelId] 
                    ?: return@withContext Result.failure(IllegalArgumentException("Model $modelId not found"))
                
                // 检查是否已加载
                modelCache.get(modelId)?.let { 
                    return@withContext Result.success(it) 
                }
                
                // 更新状态
                modelStates[modelId] = ModelState.LOADING
                updateLoadingProgress(modelId, 0f)
                
                // 创建预加载任务
                val preloadJob = async {
                    withTimeout(PRELOAD_TIMEOUT_MS) {
                        loadModelInternal(config)
                    }
                }
                
                preloadJobs[modelId] = preloadJob
                val model = preloadJob.await()
                
                // 缓存模型
                modelCache.put(modelId, model)
                modelStates[modelId] = ModelState.LOADED
                updateLoadingProgress(modelId, 1f)
                
                Result.success(model)
                
            } catch (e: Exception) {
                modelStates[modelId] = ModelState.ERROR
                updateLoadingProgress(modelId, 0f)
                Result.failure(e)
            } finally {
                preloadJobs.remove(modelId)
            }
        }
    }
    
    /**
     * 获取模型（如果未加载则同步加载）
     */
    suspend fun getModel(modelId: String): Result<AIModel> {
        // 先尝试从缓存获取
        modelCache.get(modelId)?.let { 
            return Result.success(it) 
        }
        
        // 如果正在预加载，等待完成
        preloadJobs[modelId]?.let { job ->
            return try {
                val model = job.await()
                Result.success(model)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        
        // 同步加载
        return preloadModel(modelId)
    }
    
    /**
     * 内部模型加载逻辑
     */
    private suspend fun loadModelInternal(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.1f)
        
        return when (config.type) {
            MODEL_TYPE_CHAT -> loadChatModel(config)
            MODEL_TYPE_VOICE -> loadVoiceModel(config)
            MODEL_TYPE_VISION -> loadVisionModel(config)
            MODEL_TYPE_LOCAL -> loadLocalModel(config)
            else -> throw IllegalArgumentException("Unknown model type: ${config.type}")
        }
    }
    
    /**
     * 加载聊天模型
     */
    private suspend fun loadChatModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.3f)
        
        if (config.isLocal) {
            // 加载本地聊天模型
            return loadLocalChatModel(config)
        } else {
            // 初始化在线聊天模型
            return initializeOnlineChatModel(config)
        }
    }
    
    /**
     * 加载语音模型
     */
    private suspend fun loadVoiceModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.3f)
        
        // 检查模型文件是否存在
        val modelFile = getModelFile(config.id)
        if (!modelFile.exists()) {
            updateLoadingProgress(config.id, 0.5f)
            // 下载模型文件
            downloadModelFile(config)
        }
        
        updateLoadingProgress(config.id, 0.8f)
        
        // 加载语音模型
        return loadVoiceModelFromFile(config, modelFile)
    }
    
    /**
     * 加载视觉模型
     */
    private suspend fun loadVisionModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.3f)
        
        // 视觉模型加载逻辑
        return loadTensorFlowLiteModel(config)
    }
    
    /**
     * 加载本地模型
     */
    private suspend fun loadLocalModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.3f)
        
        val modelFile = getModelFile(config.id)
        if (!modelFile.exists()) {
            throw IllegalStateException("Local model file not found: ${modelFile.path}")
        }
        
        updateLoadingProgress(config.id, 0.6f)
        
        // 根据文件类型选择加载方式
        return when {
            modelFile.name.endsWith(".tflite") -> loadTensorFlowLiteModel(config)
            modelFile.name.endsWith(".onnx") -> loadOnnxModel(config)
            else -> throw IllegalArgumentException("Unsupported model format")
        }
    }
    
    /**
     * 加载本地聊天模型
     */
    private suspend fun loadLocalChatModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.5f)
        delay(100) // 模拟加载时间
        updateLoadingProgress(config.id, 1.0f)
        
        return LocalChatModel(config.id, config.name)
    }
    
    /**
     * 初始化在线聊天模型
     */
    private suspend fun initializeOnlineChatModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.5f)
        
        // 测试网络连接
        if (!isNetworkAvailable()) {
            throw IllegalStateException("Network not available for online model")
        }
        
        updateLoadingProgress(config.id, 0.8f)
        delay(100) // 模拟初始化时间
        updateLoadingProgress(config.id, 1.0f)
        
        return OnlineChatModel(config.id, config.name)
    }
    
    /**
     * 加载TensorFlow Lite模型
     */
    private suspend fun loadTensorFlowLiteModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.6f)
        delay(200) // 模拟加载时间
        updateLoadingProgress(config.id, 1.0f)
        
        return TensorFlowLiteModel(config.id, config.name)
    }
    
    /**
     * 加载ONNX模型
     */
    private suspend fun loadOnnxModel(config: ModelConfig): AIModel {
        updateLoadingProgress(config.id, 0.6f)
        delay(200) // 模拟加载时间
        updateLoadingProgress(config.id, 1.0f)
        
        return OnnxModel(config.id, config.name)
    }
    
    /**
     * 从文件加载语音模型
     */
    private suspend fun loadVoiceModelFromFile(config: ModelConfig, modelFile: File): AIModel {
        updateLoadingProgress(config.id, 0.9f)
        delay(150) // 模拟加载时间
        updateLoadingProgress(config.id, 1.0f)
        
        return VoiceModel(config.id, config.name, modelFile.path)
    }
    
    /**
     * 下载模型文件
     */
    private suspend fun downloadModelFile(config: ModelConfig) {
        // 模拟下载过程
        for (i in 1..5) {
            delay(100)
            updateLoadingProgress(config.id, 0.5f + i * 0.06f)
        }
    }
    
    /**
     * 获取模型文件路径
     */
    private fun getModelFile(modelId: String): File {
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()
        return File(modelsDir, "$modelId.model")
    }
    
    /**
     * 检查网络可用性
     */
    private fun isNetworkAvailable(): Boolean {
        // 简化的网络检查
        return true
    }
    
    /**
     * 更新加载进度
     */
    private fun updateLoadingProgress(modelId: String, progress: Float) {
        val currentProgress = _modelLoadingProgress.value.toMutableMap()
        currentProgress[modelId] = progress
        _modelLoadingProgress.value = currentProgress
    }
    
    /**
     * 卸载模型
     */
    fun unloadModel(modelId: String) {
        modelCache.remove(modelId)
        modelStates[modelId] = ModelState.NOT_LOADED
        
        // 取消预加载任务
        preloadJobs[modelId]?.cancel()
        preloadJobs.remove(modelId)
        
        // 清理进度状态
        val currentProgress = _modelLoadingProgress.value.toMutableMap()
        currentProgress.remove(modelId)
        _modelLoadingProgress.value = currentProgress
    }
    
    /**
     * 获取模型状态
     */
    fun getModelState(modelId: String): ModelState {
        return modelStates[modelId] ?: ModelState.NOT_LOADED
    }
    
    /**
     * 获取所有模型状态
     */
    fun getAllModelStates(): Map<String, ModelState> {
        return modelStates.toMap()
    }
    
    /**
     * 清理过期模型
     */
    fun cleanupExpiredModels() {
        modelScope.launch {
            val currentTime = System.currentTimeMillis()
            val expiredModels = mutableListOf<String>()
            
            modelConfigs.forEach { (modelId, config) ->
                val model = modelCache.get(modelId)
                if (model != null && (currentTime - model.loadTime) > config.maxCacheTime) {
                    expiredModels.add(modelId)
                }
            }
            
            expiredModels.forEach { modelId ->
                unloadModel(modelId)
            }
        }
    }
    
    /**
     * 清理所有资源
     */
    fun cleanup() {
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
        modelCache.evictAll()
        modelStates.clear()
        modelScope.cancel()
    }
    
    // ==================== 数据类定义 ====================
    
    /**
     * 模型配置
     */
    data class ModelConfig(
        val id: String,
        val name: String,
        val type: String,
        val isLocal: Boolean,
        val priority: ModelPriority,
        val preloadOnStartup: Boolean,
        val maxCacheTime: Long
    )
    
    /**
     * 模型优先级
     */
    enum class ModelPriority {
        LOW, MEDIUM, HIGH
    }
    
    /**
     * 模型状态
     */
    enum class ModelState {
        NOT_LOADED, LOADING, LOADED, ERROR
    }
    
    /**
     * AI模型基类
     */
    abstract class AIModel(
        val id: String,
        val name: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        abstract suspend fun process(input: String): String
        abstract fun cleanup()
    }
    
    /**
     * 本地聊天模型实现
     */
    private class LocalChatModel(id: String, name: String) : AIModel(id, name) {
        override suspend fun process(input: String): String {
            // 模拟本地处理
            delay(100)
            return "Local response to: $input"
        }
        
        override fun cleanup() {
            // 清理本地模型资源
        }
    }
    
    /**
     * 在线聊天模型实现
     */
    private class OnlineChatModel(id: String, name: String) : AIModel(id, name) {
        override suspend fun process(input: String): String {
            // 模拟在线API调用
            delay(200)
            return "Online response to: $input"
        }
        
        override fun cleanup() {
            // 清理网络连接等
        }
    }
    
    /**
     * TensorFlow Lite模型实现
     */
    private class TensorFlowLiteModel(id: String, name: String) : AIModel(id, name) {
        override suspend fun process(input: String): String {
            // 模拟TF Lite推理
            delay(150)
            return "TF Lite response to: $input"
        }
        
        override fun cleanup() {
            // 清理TF Lite解释器
        }
    }
    
    /**
     * ONNX模型实现
     */
    private class OnnxModel(id: String, name: String) : AIModel(id, name) {
        override suspend fun process(input: String): String {
            // 模拟ONNX推理
            delay(120)
            return "ONNX response to: $input"
        }
        
        override fun cleanup() {
            // 清理ONNX运行时
        }
    }
    
    /**
     * 语音模型实现
     */
    private class VoiceModel(id: String, name: String, private val modelPath: String) : AIModel(id, name) {
        override suspend fun process(input: String): String {
            // 模拟语音处理
            delay(180)
            return "Voice response from $modelPath to: $input"
        }
        
        override fun cleanup() {
            // 清理语音模型资源
        }
    }
}