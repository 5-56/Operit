package com.ai.assistance.operit.core.ai.speech

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 语音识别引擎接口
 * 定义所有STT引擎的统一规范
 */
interface STTEngine {
    
    /**
     * 引擎类型
     */
    enum class EngineType {
        ANDROID_NATIVE,    // Android原生引擎
        SHERPA_NCNN,      // Sherpa-NCNN离线引擎
        WHISPER,          // Whisper高精度引擎
        FALLBACK          // 降级引擎（简化音频分析）
    }
    
    /**
     * 引擎状态
     */
    enum class EngineStatus {
        UNINITIALIZED,    // 未初始化
        INITIALIZING,     // 初始化中
        READY,           // 就绪
        RECOGNIZING,     // 识别中
        ERROR,           // 错误状态
        UNAVAILABLE      // 不可用
    }
    
    /**
     * 识别结果
     */
    data class RecognitionResult(
        val text: String,               // 识别文本
        val confidence: Float,          // 置信度 (0.0-1.0)
        val isPartial: Boolean = false, // 是否为部分结果
        val isFinal: Boolean = false,   // 是否为最终结果
        val language: String = "zh-CN", // 识别语言
        val processingTimeMs: Long = 0, // 处理时间
        val engineType: EngineType      // 使用的引擎类型
    )
    
    /**
     * 引擎配置
     */
    data class EngineConfig(
        val language: String = "zh-CN",           // 目标语言
        val enablePartialResults: Boolean = true, // 启用部分结果
        val maxAudioLengthSec: Int = 30,          // 最大音频长度
        val confidenceThreshold: Float = 0.5f,    // 置信度阈值
        val enablePunctuation: Boolean = true,    // 启用标点符号
        val enableNumberConversion: Boolean = true // 启用数字转换
    )
    
    /**
     * 获取引擎类型
     */
    val engineType: EngineType
    
    /**
     * 获取引擎名称
     */
    val engineName: String
    
    /**
     * 获取引擎状态流
     */
    val statusFlow: Flow<EngineStatus>
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(context: Context, config: EngineConfig = EngineConfig()): Boolean
    
    /**
     * 检查引擎是否可用
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * 单次语音识别
     * @param audioData 音频数据 (PCM 16bit, 16kHz)
     * @return 识别结果
     */
    suspend fun recognizeOnce(audioData: ByteArray): RecognitionResult
    
    /**
     * 开始实时流式识别
     * @param callback 结果回调
     * @return 是否成功启动
     */
    suspend fun startStreamingRecognition(callback: (RecognitionResult) -> Unit): Boolean
    
    /**
     * 输入音频数据到流式识别
     * @param audioData 音频数据块
     */
    suspend fun feedAudioData(audioData: ByteArray)
    
    /**
     * 停止流式识别
     */
    suspend fun stopStreamingRecognition()
    
    /**
     * 获取支持的语言列表
     */
    suspend fun getSupportedLanguages(): List<String>
    
    /**
     * 获取引擎信息
     */
    fun getEngineInfo(): Map<String, Any>
    
    /**
     * 设置配置
     */
    suspend fun updateConfig(config: EngineConfig)
    
    /**
     * 释放资源
     */
    suspend fun release()
}

/**
 * 引擎异常
 */
sealed class STTEngineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InitializationException(message: String, cause: Throwable? = null) : STTEngineException(message, cause)
    class ModelNotAvailableException(message: String, cause: Throwable? = null) : STTEngineException(message, cause)
    class AudioFormatException(message: String, cause: Throwable? = null) : STTEngineException(message, cause)
    class NetworkException(message: String, cause: Throwable? = null) : STTEngineException(message, cause)
    class PermissionException(message: String, cause: Throwable? = null) : STTEngineException(message, cause)
    class ProcessingException(message: String, cause: Throwable? = null) : STTEngineException(message, cause)
}