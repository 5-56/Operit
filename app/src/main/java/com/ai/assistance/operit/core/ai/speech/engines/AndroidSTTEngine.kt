package com.ai.assistance.operit.core.ai.speech.engines

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ai.assistance.operit.core.ai.speech.STTEngine
import com.ai.assistance.operit.core.ai.speech.STTEngineException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Android原生语音识别引擎
 * 基于系统内置的SpeechRecognizer实现
 */
class AndroidSTTEngine : STTEngine {
    
    companion object {
        private const val TAG = "AndroidSTTEngine"
        private const val RECOGNITION_TIMEOUT_MS = 10000L
    }
    
    override val engineType = STTEngine.EngineType.ANDROID_NATIVE
    override val engineName = "Android Native STT"
    
    private val _statusFlow = MutableStateFlow(STTEngine.EngineStatus.UNINITIALIZED)
    override val statusFlow: Flow<STTEngine.EngineStatus> = _statusFlow.asStateFlow()
    
    private var context: Context? = null
    private var config = STTEngine.EngineConfig()
    private var speechRecognizer: SpeechRecognizer? = null
    private val isRecognizing = AtomicBoolean(false)
    private var streamingCallback: ((STTEngine.RecognitionResult) -> Unit)? = null
    
    override suspend fun initialize(context: Context, config: STTEngine.EngineConfig): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                _statusFlow.value = STTEngine.EngineStatus.INITIALIZING
                
                this@AndroidSTTEngine.context = context.applicationContext
                this@AndroidSTTEngine.config = config
                
                // 检查语音识别可用性
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(TAG, "Android语音识别不可用")
                    _statusFlow.value = STTEngine.EngineStatus.UNAVAILABLE
                    return@withContext false
                }
                
                // 创建SpeechRecognizer实例
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                
                if (speechRecognizer == null) {
                    Log.e(TAG, "无法创建SpeechRecognizer实例")
                    _statusFlow.value = STTEngine.EngineStatus.ERROR
                    return@withContext false
                }
                
                _statusFlow.value = STTEngine.EngineStatus.READY
                Log.d(TAG, "Android STT引擎初始化成功")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Android STT引擎初始化失败", e)
                _statusFlow.value = STTEngine.EngineStatus.ERROR
                false
            }
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        return speechRecognizer != null && _statusFlow.value == STTEngine.EngineStatus.READY
    }
    
    override suspend fun recognizeOnce(audioData: ByteArray): STTEngine.RecognitionResult {
        return withContext(Dispatchers.Main) {
            if (!isAvailable()) {
                throw STTEngineException.InitializationException("引擎未准备就绪")
            }
            
            if (isRecognizing.get()) {
                throw STTEngineException.ProcessingException("引擎正在识别中")
            }
            
            try {
                val startTime = System.currentTimeMillis()
                _statusFlow.value = STTEngine.EngineStatus.RECOGNIZING
                
                // Android原生引擎不支持直接处理音频数据
                // 我们使用实时识别模式来模拟
                val result = performRecognition()
                
                val processingTime = System.currentTimeMillis() - startTime
                
                STTEngine.RecognitionResult(
                    text = result,
                    confidence = if (result.isNotBlank()) 0.8f else 0.0f,
                    isPartial = false,
                    isFinal = true,
                    language = config.language,
                    processingTimeMs = processingTime,
                    engineType = engineType
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "语音识别失败", e)
                throw STTEngineException.ProcessingException("识别过程中发生错误", e)
            } finally {
                _statusFlow.value = STTEngine.EngineStatus.READY
            }
        }
    }
    
    private suspend fun performRecognition(): String {
        return suspendCancellableCoroutine { continuation ->
            val recognitionListener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "准备开始语音识别")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "开始说话")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // 音量变化
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // 接收到音频缓冲区
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "结束说话")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = getErrorMessage(error)
                    Log.e(TAG, "语音识别错误: $errorMessage")
                    
                    if (continuation.isActive) {
                        continuation.resume("")
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val result = matches?.firstOrNull() ?: ""
                    
                    Log.d(TAG, "语音识别结果: $result")
                    
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // 部分结果处理
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // 其他事件
                }
            }
            
            try {
                val intent = createRecognitionIntent()
                speechRecognizer?.setRecognitionListener(recognitionListener)
                speechRecognizer?.startListening(intent)
                
                // 设置超时
                continuation.invokeOnCancellation {
                    speechRecognizer?.stopListening()
                }
                
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume("")
                }
            }
        }
    }
    
    override suspend fun startStreamingRecognition(callback: (STTEngine.RecognitionResult) -> Unit): Boolean {
        return withContext(Dispatchers.Main) {
            if (!isAvailable() || isRecognizing.get()) {
                return@withContext false
            }
            
            try {
                streamingCallback = callback
                isRecognizing.set(true)
                _statusFlow.value = STTEngine.EngineStatus.RECOGNIZING
                
                val intent = createRecognitionIntent().apply {
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.enablePartialResults)
                }
                
                speechRecognizer?.setRecognitionListener(createStreamingListener())
                speechRecognizer?.startListening(intent)
                
                Log.d(TAG, "开始流式语音识别")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "启动流式识别失败", e)
                isRecognizing.set(false)
                _statusFlow.value = STTEngine.EngineStatus.READY
                false
            }
        }
    }
    
    private fun createStreamingListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "流式识别准备就绪")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "开始流式识别")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // 音量变化
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // 音频缓冲区
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "流式识别结束")
            }
            
            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e(TAG, "流式识别错误: $errorMessage")
                
                isRecognizing.set(false)
                _statusFlow.value = STTEngine.EngineStatus.READY
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val result = matches?.firstOrNull() ?: ""
                
                if (result.isNotBlank()) {
                    streamingCallback?.invoke(
                        STTEngine.RecognitionResult(
                            text = result,
                            confidence = 0.8f,
                            isPartial = false,
                            isFinal = true,
                            language = config.language,
                            processingTimeMs = 0,
                            engineType = engineType
                        )
                    )
                }
                
                isRecognizing.set(false)
                _statusFlow.value = STTEngine.EngineStatus.READY
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                if (!config.enablePartialResults) return
                
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val result = matches?.firstOrNull() ?: ""
                
                if (result.isNotBlank()) {
                    streamingCallback?.invoke(
                        STTEngine.RecognitionResult(
                            text = result,
                            confidence = 0.6f,
                            isPartial = true,
                            isFinal = false,
                            language = config.language,
                            processingTimeMs = 0,
                            engineType = engineType
                        )
                    )
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // 其他事件
            }
        }
    }
    
    override suspend fun feedAudioData(audioData: ByteArray) {
        // Android原生引擎不支持手动输入音频数据
        Log.w(TAG, "Android原生引擎不支持手动输入音频数据")
    }
    
    override suspend fun stopStreamingRecognition() {
        withContext(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                isRecognizing.set(false)
                streamingCallback = null
                _statusFlow.value = STTEngine.EngineStatus.READY
                Log.d(TAG, "已停止流式识别")
            } catch (e: Exception) {
                Log.e(TAG, "停止流式识别失败", e)
            }
        }
    }
    
    override suspend fun getSupportedLanguages(): List<String> {
        return listOf(
            "zh-CN", "zh-TW", "zh-HK",  // 中文
            "en-US", "en-GB", "en-AU",  // 英文
            "ja-JP",                    // 日文
            "ko-KR",                    // 韩文
            "fr-FR", "de-DE", "es-ES",  // 欧洲语言
            "ru-RU", "ar-SA", "hi-IN"   // 其他语言
        )
    }
    
    override fun getEngineInfo(): Map<String, Any> {
        return mapOf(
            "engineType" to engineType.name,
            "engineName" to engineName,
            "status" to _statusFlow.value.name,
            "isRecognizing" to isRecognizing.get(),
            "supportedLanguages" to getSupportedLanguages().size,
            "supportsStreaming" to true,
            "supportsPartialResults" to true,
            "requiresNetwork" to true, // Android原生通常需要网络
            "modelSize" to "N/A",
            "version" to "System"
        )
    }
    
    override suspend fun updateConfig(config: STTEngine.EngineConfig) {
        this.config = config
        Log.d(TAG, "配置已更新: $config")
    }
    
    override suspend fun release() {
        withContext(Dispatchers.Main) {
            try {
                stopStreamingRecognition()
                speechRecognizer?.destroy()
                speechRecognizer = null
                streamingCallback = null
                _statusFlow.value = STTEngine.EngineStatus.UNINITIALIZED
                Log.d(TAG, "Android STT引擎资源已释放")
            } catch (e: Exception) {
                Log.e(TAG, "释放Android STT引擎资源失败", e)
            }
        }
    }
    
    private fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // 根据配置设置其他参数
            if (config.enablePartialResults) {
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        }
    }
    
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "音频错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
            SpeechRecognizer.ERROR_NO_MATCH -> "无匹配结果"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
            else -> "未知错误($error)"
        }
    }
}