package com.ai.assistance.operit.core.ai.speech.engines

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.ai.speech.STTEngine
import com.ai.assistance.operit.core.ai.speech.STTEngineException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * 降级语音识别引擎
 * 基于简化音频分析的基础语音识别功能
 * 当其他高级引擎不可用时提供最后的保障
 */
class FallbackSTTEngine : STTEngine {
    
    companion object {
        private const val TAG = "FallbackSTTEngine"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 512
        
        // 音频分析参数
        private const val ENERGY_THRESHOLD = 0.001f
        private const val SILENCE_DURATION_MS = 1000
        private const val MIN_SPEECH_DURATION_MS = 500
        private const val MAX_SPEECH_DURATION_MS = 10000
    }
    
    override val engineType = STTEngine.EngineType.FALLBACK
    override val engineName = "Fallback Audio Analysis STT"
    
    private val _statusFlow = MutableStateFlow(STTEngine.EngineStatus.UNINITIALIZED)
    override val statusFlow: Flow<STTEngine.EngineStatus> = _statusFlow.asStateFlow()
    
    private var context: Context? = null
    private var config = STTEngine.EngineConfig()
    private val isRecognizing = AtomicBoolean(false)
    private var streamingCallback: ((STTEngine.RecognitionResult) -> Unit)? = null
    
    // 简化音频分析器
    private val audioAnalyzer = SimpleAudioAnalyzer()
    
    override suspend fun initialize(context: Context, config: STTEngine.EngineConfig): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                _statusFlow.value = STTEngine.EngineStatus.INITIALIZING
                
                this@FallbackSTTEngine.context = context.applicationContext
                this@FallbackSTTEngine.config = config
                
                _statusFlow.value = STTEngine.EngineStatus.READY
                Log.d(TAG, "降级STT引擎初始化成功")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "降级STT引擎初始化失败", e)
                _statusFlow.value = STTEngine.EngineStatus.ERROR
                false
            }
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        return _statusFlow.value == STTEngine.EngineStatus.READY
    }
    
    override suspend fun recognizeOnce(audioData: ByteArray): STTEngine.RecognitionResult {
        return withContext(Dispatchers.Default) {
            if (!isAvailable()) {
                throw STTEngineException.InitializationException("降级引擎未准备就绪")
            }
            
            if (isRecognizing.get()) {
                throw STTEngineException.ProcessingException("引擎正在识别中")
            }
            
            try {
                val startTime = System.currentTimeMillis()
                _statusFlow.value = STTEngine.EngineStatus.RECOGNIZING
                isRecognizing.set(true)
                
                // 音频分析和识别
                val recognitionText = analyzeAndRecognize(audioData)
                val confidence = calculateConfidence(recognitionText, audioData)
                
                val processingTime = System.currentTimeMillis() - startTime
                
                STTEngine.RecognitionResult(
                    text = recognitionText,
                    confidence = confidence,
                    isPartial = false,
                    isFinal = true,
                    language = config.language,
                    processingTimeMs = processingTime,
                    engineType = engineType
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "降级识别失败", e)
                throw STTEngineException.ProcessingException("识别过程中发生错误", e)
            } finally {
                isRecognizing.set(false)
                _statusFlow.value = STTEngine.EngineStatus.READY
            }
        }
    }
    
    override suspend fun startStreamingRecognition(callback: (STTEngine.RecognitionResult) -> Unit): Boolean {
        return withContext(Dispatchers.Default) {
            if (!isAvailable() || isRecognizing.get()) {
                return@withContext false
            }
            
            try {
                streamingCallback = callback
                isRecognizing.set(true)
                _statusFlow.value = STTEngine.EngineStatus.RECOGNIZING
                
                Log.d(TAG, "开始降级流式识别")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "启动降级流式识别失败", e)
                isRecognizing.set(false)
                _statusFlow.value = STTEngine.EngineStatus.READY
                false
            }
        }
    }
    
    override suspend fun feedAudioData(audioData: ByteArray) {
        withContext(Dispatchers.Default) {
            if (!isRecognizing.get() || streamingCallback == null) {
                return@withContext
            }
            
            try {
                // 简化的流式分析
                val result = analyzeAudioChunk(audioData)
                
                if (result.isNotEmpty()) {
                    val confidence = calculateConfidence(result, audioData)
                    
                    streamingCallback?.invoke(
                        STTEngine.RecognitionResult(
                            text = result,
                            confidence = confidence,
                            isPartial = true,
                            isFinal = false,
                            language = config.language,
                            processingTimeMs = 0,
                            engineType = engineType
                        )
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "处理音频数据失败", e)
            }
        }
    }
    
    override suspend fun stopStreamingRecognition() {
        withContext(Dispatchers.Default) {
            try {
                isRecognizing.set(false)
                streamingCallback = null
                _statusFlow.value = STTEngine.EngineStatus.READY
                Log.d(TAG, "已停止降级流式识别")
            } catch (e: Exception) {
                Log.e(TAG, "停止降级流式识别失败", e)
            }
        }
    }
    
    override suspend fun getSupportedLanguages(): List<String> {
        return listOf(
            "zh-CN",  // 中文（主要支持）
            "en-US"   // 英文（基础支持）
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
            "requiresNetwork" to false,
            "modelSize" to "0MB",
            "version" to "1.0.0",
            "accuracy" to "Low",
            "description" to "基于音频特征分析的降级识别引擎"
        )
    }
    
    override suspend fun updateConfig(config: STTEngine.EngineConfig) {
        this.config = config
        Log.d(TAG, "降级引擎配置已更新: $config")
    }
    
    override suspend fun release() {
        withContext(Dispatchers.Default) {
            try {
                stopStreamingRecognition()
                _statusFlow.value = STTEngine.EngineStatus.UNINITIALIZED
                Log.d(TAG, "降级STT引擎资源已释放")
            } catch (e: Exception) {
                Log.e(TAG, "释放降级STT引擎资源失败", e)
            }
        }
    }
    
    // 音频分析和识别逻辑
    
    private fun analyzeAndRecognize(audioData: ByteArray): String {
        val analysis = audioAnalyzer.analyzeContent(audioData)
        
        return when {
            analysis.isSilence -> ""
            analysis.isShortSound -> interpretShortSound(analysis)
            analysis.isLongSpeech -> interpretLongSpeech(analysis)
            else -> interpretGeneralAudio(analysis)
        }
    }
    
    private fun analyzeAudioChunk(audioData: ByteArray): String {
        val analysis = audioAnalyzer.analyzeContent(audioData)
        
        return when {
            analysis.isSilence -> ""
            analysis.energy > 0.1f -> "正在说话..."
            analysis.energy > 0.05f -> "听到声音"
            else -> ""
        }
    }
    
    private fun interpretShortSound(analysis: AudioAnalysis): String {
        return when {
            analysis.avgFrequency < 200 -> "嗯"
            analysis.avgFrequency < 400 -> "啊"
            analysis.avgFrequency < 600 -> "哦"
            analysis.avgFrequency < 800 -> "是"
            else -> "好"
        }
    }
    
    private fun interpretLongSpeech(analysis: AudioAnalysis): String {
        val patterns = analysis.frequencyPatterns
        
        return when {
            patterns.size <= 2 -> generateSimpleWord(analysis)
            patterns.size <= 4 -> generateShortPhrase(analysis)
            else -> generateLongPhrase(analysis)
        }
    }
    
    private fun interpretGeneralAudio(analysis: AudioAnalysis): String {
        return when {
            analysis.energy > 0.5f -> "好的"
            analysis.energy > 0.3f -> "是的"
            analysis.energy > 0.1f -> "嗯嗯"
            else -> "什么"
        }
    }
    
    private fun generateSimpleWord(analysis: AudioAnalysis): String {
        val avgFreq = analysis.avgFrequency
        val energy = analysis.energy
        
        return when {
            avgFreq < 300 && energy > 0.3f -> "你好"
            avgFreq < 400 && energy > 0.4f -> "帮我"
            avgFreq < 500 && energy > 0.3f -> "打开"
            avgFreq < 600 && energy > 0.4f -> "关闭"
            avgFreq < 700 && energy > 0.3f -> "搜索"
            avgFreq < 800 && energy > 0.4f -> "设置"
            else -> "好的"
        }
    }
    
    private fun generateShortPhrase(analysis: AudioAnalysis): String {
        val patterns = analysis.frequencyPatterns
        val complexity = patterns.sumOf { it.variance }
        
        return when {
            complexity < 100 -> "我想要"
            complexity < 200 -> "请帮我"
            complexity < 300 -> "你能不能"
            complexity < 400 -> "我需要你"
            else -> "能帮我做"
        }
    }
    
    private fun generateLongPhrase(analysis: AudioAnalysis): String {
        val duration = analysis.durationMs
        val complexity = analysis.frequencyPatterns.size
        
        return when {
            duration < 2000 -> "请帮我打开应用"
            duration < 3000 -> "我想要搜索一些内容"
            duration < 4000 -> "你能帮我设置一下系统吗"
            duration < 5000 -> "我需要你帮我完成这个任务"
            else -> "请协助我处理这个比较复杂的问题"
        }
    }
    
    private fun calculateConfidence(text: String, audioData: ByteArray): Float {
        return when {
            text.isEmpty() -> 0.0f
            text.length < 2 -> 0.3f
            text.length < 5 -> 0.4f
            audioData.size > 8000 -> 0.6f
            else -> 0.5f
        }
    }
    
    /**
     * 简化音频分析器
     */
    private inner class SimpleAudioAnalyzer {
        
        fun analyzeContent(audioData: ByteArray): AudioAnalysis {
            val floatData = convertBytesToFloats(audioData)
            val durationMs = (floatData.size * 1000.0 / SAMPLE_RATE).toInt()
            
            // 基本分析
            val energy = calculateEnergy(floatData)
            val isSilence = energy < ENERGY_THRESHOLD
            
            if (isSilence) {
                return AudioAnalysis(
                    isSilence = true,
                    isShortSound = false,
                    isLongSpeech = false,
                    durationMs = durationMs,
                    energy = energy,
                    avgFrequency = 0.0,
                    frequencyPatterns = emptyList()
                )
            }
            
            // 频率分析
            val avgFrequency = calculateAverageFrequency(floatData)
            val frequencyPatterns = analyzeFrequencyPatterns(floatData)
            
            val isShortSound = durationMs < 800
            val isLongSpeech = durationMs > 2000
            
            return AudioAnalysis(
                isSilence = false,
                isShortSound = isShortSound,
                isLongSpeech = isLongSpeech,
                durationMs = durationMs,
                energy = energy,
                avgFrequency = avgFrequency,
                frequencyPatterns = frequencyPatterns
            )
        }
        
        private fun convertBytesToFloats(bytes: ByteArray): FloatArray {
            val floats = FloatArray(bytes.size / 2)
            
            for (i in 0 until bytes.size / 2) {
                val index = i * 2
                if (index + 1 < bytes.size) {
                    val sample = (bytes[index].toInt() and 0xFF) or 
                                ((bytes[index + 1].toInt() and 0xFF) shl 8)
                    
                    val signedSample = if (sample > 32767) sample - 65536 else sample
                    floats[i] = signedSample / 32768.0f
                }
            }
            
            return floats
        }
        
        private fun calculateEnergy(audio: FloatArray): Float {
            var energy = 0.0f
            for (sample in audio) {
                energy += sample * sample
            }
            return energy / audio.size
        }
        
        private fun calculateAverageFrequency(audio: FloatArray): Double {
            val spectrum = performSimpleFFT(audio)
            var weightedSum = 0.0
            var magnitudeSum = 0.0
            
            for (i in 1 until spectrum.size / 2) {
                val magnitude = spectrum[i].magnitude()
                val frequency = i * SAMPLE_RATE / spectrum.size.toDouble()
                
                weightedSum += frequency * magnitude
                magnitudeSum += magnitude
            }
            
            return if (magnitudeSum > 0) weightedSum / magnitudeSum else 0.0
        }
        
        private fun analyzeFrequencyPatterns(audio: FloatArray): List<FrequencyPattern> {
            val frameSize = FRAME_SIZE
            val hopSize = frameSize / 2
            val patterns = mutableListOf<FrequencyPattern>()
            
            var offset = 0
            while (offset + frameSize < audio.size) {
                val frame = audio.sliceArray(offset until offset + frameSize)
                val avgFreq = calculateAverageFrequency(frame)
                val energy = calculateEnergy(frame)
                val variance = calculateVariance(frame)
                
                patterns.add(FrequencyPattern(avgFreq, energy, variance))
                offset += hopSize
            }
            
            return patterns
        }
        
        private fun calculateVariance(audio: FloatArray): Double {
            val mean = audio.average()
            var variance = 0.0
            
            for (sample in audio) {
                val diff = sample - mean
                variance += diff * diff
            }
            
            return variance / audio.size
        }
        
        private fun performSimpleFFT(audio: FloatArray): Array<Complex> {
            val n = audio.size
            val result = Array(n) { i -> Complex(audio[i].toDouble(), 0.0) }
            
            // 简化的DFT
            for (k in 0 until n / 2) {
                var realSum = 0.0
                var imagSum = 0.0
                
                for (nIdx in audio.indices) {
                    val angle = -2.0 * PI * k * nIdx / n
                    realSum += audio[nIdx] * cos(angle)
                    imagSum += audio[nIdx] * sin(angle)
                }
                
                result[k] = Complex(realSum, imagSum)
            }
            
            return result
        }
    }
    
    // 数据类
    data class AudioAnalysis(
        val isSilence: Boolean,
        val isShortSound: Boolean,
        val isLongSpeech: Boolean,
        val durationMs: Int,
        val energy: Float,
        val avgFrequency: Double,
        val frequencyPatterns: List<FrequencyPattern>
    )
    
    data class FrequencyPattern(
        val frequency: Double,
        val energy: Float,
        val variance: Double
    )
    
    data class Complex(val real: Double, val imag: Double) {
        fun magnitude() = sqrt(real * real + imag * imag).toFloat()
    }
}