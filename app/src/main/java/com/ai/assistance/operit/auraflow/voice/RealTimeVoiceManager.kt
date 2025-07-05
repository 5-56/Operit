package com.ai.assistance.operit.auraflow.voice

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/**
 * 语音状态枚举
 */
enum class VoiceState {
    IDLE,           // 空闲
    LISTENING,      // 监听中
    PROCESSING,     // 处理中
    SPEAKING,       // 说话中
    INTERRUPTED,    // 被打断
    ERROR          // 错误状态
}

/**
 * 音频配置
 */
@Serializable
data class AudioConfig(
    val sampleRate: Int = 16000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val bufferSize: Int = 4096,
    val enableEchoCancellation: Boolean = true,
    val enableNoiseSuppression: Boolean = true,
    val vadThreshold: Float = 0.5f,
    val silenceTimeout: Long = 2000,
    val maxRecordingTime: Long = 30000
)

/**
 * 语音识别结果
 */
data class VoiceRecognitionResult(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 语音活动检测器
 */
class VoiceActivityDetector(private val config: AudioConfig) {
    
    private var isActive = false
    private var lastActivityTime = 0L
    private val activityBuffer = ConcurrentLinkedQueue<Float>()
    private val bufferSize = 10
    
    fun detectActivity(audioData: ShortArray): Boolean {
        val rms = calculateRMS(audioData)
        val activity = rms > config.vadThreshold
        
        // 添加到活动缓冲区
        activityBuffer.offer(if (activity) 1f else 0f)
        if (activityBuffer.size > bufferSize) {
            activityBuffer.poll()
        }
        
        // 计算活动平均值
        val avgActivity = activityBuffer.average().toFloat()
        val currentTime = System.currentTimeMillis()
        
        if (avgActivity > 0.3f) {
            isActive = true
            lastActivityTime = currentTime
        } else if (currentTime - lastActivityTime > config.silenceTimeout) {
            isActive = false
        }
        
        return isActive
    }
    
    private fun calculateRMS(audioData: ShortArray): Float {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        return sqrt(sum / audioData.size).toFloat() / Short.MAX_VALUE
    }
    
    fun reset() {
        isActive = false
        lastActivityTime = 0L
        activityBuffer.clear()
    }
}

/**
 * 音频流处理器
 */
class AudioStreamProcessor(
    private val context: Context,
    private val config: AudioConfig
) {
    companion object {
        private const val TAG = "AudioStreamProcessor"
    }
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    
    // 音频流
    private val _audioInputStream = MutableSharedFlow<ShortArray>()
    val audioInputStream: SharedFlow<ShortArray> = _audioInputStream.asSharedFlow()
    
    private val _audioOutputStream = MutableSharedFlow<ShortArray>()
    val audioOutputStream: SharedFlow<ShortArray> = _audioOutputStream.asSharedFlow()
    
    private var isRecording = false
    private var isPlaying = false
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun initialize(): Boolean {
        try {
            // 初始化录音
            val minBufferSize = AudioRecord.getMinBufferSize(
                config.sampleRate,
                config.channelConfig,
                config.audioFormat
            )
            
            val bufferSize = maxOf(minBufferSize, config.bufferSize)
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                bufferSize
            )
            
            // 初始化播放
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(config.audioFormat)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            // 初始化音频增强
            setupAudioEnhancements()
            
            Log.d(TAG, "音频流处理器初始化成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "音频流处理器初始化失败", e)
            return false
        }
    }
    
    private fun setupAudioEnhancements() {
        try {
            if (config.enableEchoCancellation && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioRecord?.audioSessionId ?: 0)
                echoCanceler?.enabled = true
            }
            
            if (config.enableNoiseSuppression && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioRecord?.audioSessionId ?: 0)
                noiseSuppressor?.enabled = true
            }
            
            Log.d(TAG, "音频增强功能设置完成")
        } catch (e: Exception) {
            Log.w(TAG, "音频增强功能设置失败", e)
        }
    }
    
    fun startRecording() {
        if (isRecording) return
        
        processingScope.launch {
            try {
                audioRecord?.startRecording()
                isRecording = true
                
                val buffer = ShortArray(config.bufferSize)
                
                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        val audioData = buffer.copyOf(bytesRead)
                        _audioInputStream.emit(audioData)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "录音过程出错", e)
            }
        }
    }
    
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
    }
    
    fun startPlayback() {
        if (isPlaying) return
        
        processingScope.launch {
            try {
                audioTrack?.play()
                isPlaying = true
                
                audioOutputStream.collect { audioData ->
                    if (isPlaying) {
                        audioTrack?.write(audioData, 0, audioData.size)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "播放过程出错", e)
            }
        }
    }
    
    fun stopPlayback() {
        isPlaying = false
        audioTrack?.stop()
    }
    
    fun playAudio(audioData: ShortArray) {
        processingScope.launch {
            _audioOutputStream.emit(audioData)
        }
    }
    
    fun cleanup() {
        stopRecording()
        stopPlayback()
        
        echoCanceler?.release()
        noiseSuppressor?.release()
        audioRecord?.release()
        audioTrack?.release()
        
        processingScope.cancel()
        Log.d(TAG, "音频流处理器已清理")
    }
}

/**
 * 实时语音管理器
 */
class RealTimeVoiceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RealTimeVoiceManager"
        private const val TTS_UTTERANCE_ID = "auraflow_tts"
    }
    
    private val config = AudioConfig()
    private val audioProcessor = AudioStreamProcessor(context, config)
    private val voiceActivityDetector = VoiceActivityDetector(config)
    
    // 语音识别
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    
    // 状态流
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()
    
    private val _recognitionResults = MutableSharedFlow<VoiceRecognitionResult>()
    val recognitionResults: SharedFlow<VoiceRecognitionResult> = _recognitionResults.asSharedFlow()
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    // 控制变量
    private var isInitialized = false
    private var isListening = false
    private var canInterrupt = true
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 初始化语音管理器
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        try {
            // 初始化音频处理器
            if (!audioProcessor.initialize()) {
                Log.e(TAG, "音频处理器初始化失败")
                return@withContext false
            }
            
            // 初始化语音识别
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            
            // 初始化TTS
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = java.util.Locale.getDefault()
                    textToSpeech?.setOnUtteranceProgressListener(createTTSListener())
                    Log.d(TAG, "TTS初始化成功")
                } else {
                    Log.e(TAG, "TTS初始化失败")
                }
            }
            
            // 设置音频流监听
            setupAudioStreamMonitoring()
            
            isInitialized = true
            Log.d(TAG, "实时语音管理器初始化成功")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "实时语音管理器初始化失败", e)
            return@withContext false
        }
    }
    
    private fun setupAudioStreamMonitoring() {
        scope.launch {
            audioProcessor.audioInputStream.collect { audioData ->
                // 计算音量级别
                val level = calculateAudioLevel(audioData)
                _audioLevel.value = level
                
                // 语音活动检测
                if (isListening) {
                    val hasVoiceActivity = voiceActivityDetector.detectActivity(audioData)
                    
                    if (hasVoiceActivity && _voiceState.value == VoiceState.IDLE) {
                        _voiceState.value = VoiceState.LISTENING
                    } else if (!hasVoiceActivity && _voiceState.value == VoiceState.LISTENING) {
                        // 静音超时，停止监听
                        delay(config.silenceTimeout)
                        if (!voiceActivityDetector.detectActivity(audioData)) {
                            stopListening()
                        }
                    }
                }
            }
        }
    }
    
    private fun calculateAudioLevel(audioData: ShortArray): Float {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        return (sqrt(sum / audioData.size) / Short.MAX_VALUE).toFloat()
    }
    
    /**
     * 开始实时对话
     */
    fun startRealTimeConversation() {
        if (!isInitialized) {
            Log.w(TAG, "语音管理器未初始化")
            return
        }
        
        scope.launch {
            try {
                _voiceState.value = VoiceState.IDLE
                canInterrupt = true
                
                // 开始音频流处理
                audioProcessor.startRecording()
                audioProcessor.startPlayback()
                
                // 开始监听
                startListening()
                
                Log.d(TAG, "实时对话已开始")
                
            } catch (e: Exception) {
                Log.e(TAG, "启动实时对话失败", e)
                _voiceState.value = VoiceState.ERROR
            }
        }
    }
    
    /**
     * 停止实时对话
     */
    fun stopRealTimeConversation() {
        scope.launch {
            try {
                stopListening()
                stopSpeaking()
                
                audioProcessor.stopRecording()
                audioProcessor.stopPlayback()
                
                _voiceState.value = VoiceState.IDLE
                voiceActivityDetector.reset()
                
                Log.d(TAG, "实时对话已停止")
                
            } catch (e: Exception) {
                Log.e(TAG, "停止实时对话失败", e)
            }
        }
    }
    
    /**
     * 开始监听
     */
    private fun startListening() {
        if (isListening) return
        
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, config.silenceTimeout)
            }
            
            speechRecognizer?.startListening(intent)
            isListening = true
            _voiceState.value = VoiceState.LISTENING
            
            Log.d(TAG, "开始语音监听")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动语音监听失败", e)
            _voiceState.value = VoiceState.ERROR
        }
    }
    
    /**
     * 停止监听
     */
    private fun stopListening() {
        if (!isListening) return
        
        try {
            speechRecognizer?.stopListening()
            isListening = false
            
            if (_voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.IDLE
            }
            
            Log.d(TAG, "停止语音监听")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止语音监听失败", e)
        }
    }
    
    /**
     * 实时语音合成和播放
     */
    fun speakText(text: String, canBeInterrupted: Boolean = true) {
        if (!isInitialized) return
        
        scope.launch {
            try {
                // 如果正在说话且允许打断，先停止
                if (_isSpeaking.value && canBeInterrupted && canInterrupt) {
                    stopSpeaking()
                    delay(100) // 等待停止完成
                }
                
                _voiceState.value = VoiceState.SPEAKING
                _isSpeaking.value = true
                
                // 停止监听以避免回音
                if (isListening) {
                    stopListening()
                }
                
                // 设置TTS参数
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TTS_UTTERANCE_ID)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 0.0f)
                }
                
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, TTS_UTTERANCE_ID)
                
                Log.d(TAG, "开始语音合成: $text")
                
            } catch (e: Exception) {
                Log.e(TAG, "语音合成失败", e)
                _isSpeaking.value = false
                _voiceState.value = VoiceState.ERROR
            }
        }
    }
    
    /**
     * 停止说话
     */
    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
            _isSpeaking.value = false
            
            if (_voiceState.value == VoiceState.SPEAKING) {
                _voiceState.value = VoiceState.IDLE
            }
            
            Log.d(TAG, "停止语音播放")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止语音播放失败", e)
        }
    }
    
    /**
     * 手动打断（用户打断AI说话）
     */
    fun interruptSpeaking() {
        if (_isSpeaking.value && canInterrupt) {
            _voiceState.value = VoiceState.INTERRUPTED
            stopSpeaking()
            
            // 立即开始监听用户输入
            scope.launch {
                delay(200) // 短暂延时避免误触发
                startListening()
            }
            
            Log.d(TAG, "用户打断了AI语音")
        }
    }
    
    /**
     * 设置是否允许打断
     */
    fun setInterruptible(interruptible: Boolean) {
        canInterrupt = interruptible
    }
    
    /**
     * 调整语音参数
     */
    fun adjustVoiceSettings(speechRate: Float = 1.0f, pitch: Float = 1.0f) {
        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.setPitch(pitch)
        Log.d(TAG, "语音参数已调整: 语速=$speechRate, 音调=$pitch")
    }
    
    /**
     * 创建语音识别监听器
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "语音识别准备就绪")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "检测到语音开始")
                _voiceState.value = VoiceState.LISTENING
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                _audioLevel.value = rmsdB / 10f // 标准化音量级别
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // 可以处理音频缓冲区数据
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "语音输入结束")
                _voiceState.value = VoiceState.PROCESSING
            }
            
            override fun onError(error: Int) {
                val errorMsg = when (error) {
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
                
                Log.w(TAG, "语音识别错误: $errorMsg")
                
                isListening = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    _voiceState.value = VoiceState.ERROR
                } else {
                    _voiceState.value = VoiceState.IDLE
                    // 继续监听
                    scope.launch {
                        delay(500)
                        startListening()
                    }
                }
            }
            
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                    if (matches.isNotEmpty()) {
                        val text = matches[0]
                        val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.get(0) ?: 1.0f
                        
                        scope.launch {
                            _recognitionResults.emit(
                                VoiceRecognitionResult(
                                    text = text,
                                    confidence = confidence,
                                    isFinal = true
                                )
                            )
                        }
                        
                        Log.d(TAG, "语音识别结果: $text (置信度: $confidence)")
                    }
                }
                
                isListening = false
                _voiceState.value = VoiceState.PROCESSING
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                    if (matches.isNotEmpty()) {
                        val text = matches[0]
                        
                        scope.launch {
                            _recognitionResults.emit(
                                VoiceRecognitionResult(
                                    text = text,
                                    confidence = 0.5f,
                                    isFinal = false
                                )
                            )
                        }
                        
                        Log.d(TAG, "部分识别结果: $text")
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "语音识别事件: $eventType")
            }
        }
    }
    
    /**
     * 创建TTS监听器
     */
    private fun createTTSListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    Log.d(TAG, "TTS开始播放")
                    _isSpeaking.value = true
                    _voiceState.value = VoiceState.SPEAKING
                }
            }
            
            override fun onDone(utteranceId: String?) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    Log.d(TAG, "TTS播放完成")
                    _isSpeaking.value = false
                    _voiceState.value = VoiceState.IDLE
                    
                    // TTS完成后重新开始监听
                    scope.launch {
                        delay(300) // 短暂延时
                        startListening()
                    }
                }
            }
            
            override fun onError(utteranceId: String?) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    Log.e(TAG, "TTS播放出错")
                    _isSpeaking.value = false
                    _voiceState.value = VoiceState.ERROR
                }
            }
            
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == TTS_UTTERANCE_ID) {
                    Log.d(TAG, "TTS播放停止 (中断: $interrupted)")
                    _isSpeaking.value = false
                    
                    if (interrupted && _voiceState.value == VoiceState.SPEAKING) {
                        _voiceState.value = VoiceState.INTERRUPTED
                    } else {
                        _voiceState.value = VoiceState.IDLE
                    }
                }
            }
        }
    }
    
    /**
     * 获取当前音频级别（用于UI显示）
     */
    fun getCurrentAudioLevel(): Float = _audioLevel.value
    
    /**
     * 是否正在进行对话
     */
    fun isInConversation(): Boolean {
        return _voiceState.value != VoiceState.IDLE && _voiceState.value != VoiceState.ERROR
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopRealTimeConversation()
        
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        audioProcessor.cleanup()
        
        scope.cancel()
        isInitialized = false
        
        Log.d(TAG, "实时语音管理器已清理")
    }
}