package com.ai.assistance.operit.core.ai.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音唤醒检测器
 * 支持离线唤醒词检测，使用音频特征匹配算法
 */
class VoiceWakeUpDetector(
    private val context: Context,
    private val wakeWord: String
) {
    
    companion object {
        private const val TAG = "VoiceWakeUpDetector"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 512
        private const val HOP_SIZE = 256
        private const val MEL_FILTERS = 40
        private const val FFT_SIZE = 512
        private const val MIN_FREQUENCY = 80.0
        private const val MAX_FREQUENCY = 8000.0
        
        // 唤醒词检测参数
        private const val DETECTION_THRESHOLD = 0.75f
        private const val MIN_CONFIDENCE_SAMPLES = 3
        private const val AUDIO_BUFFER_DURATION_MS = 2000 // 2秒音频缓冲
        private const val SILENCE_THRESHOLD = 0.01f
        private const val VOICE_ACTIVITY_THRESHOLD = 0.05f
    }
    
    private val isInitialized = AtomicBoolean(false)
    private val audioBuffer = ConcurrentLinkedQueue<FloatArray>()
    private val maxBufferSize = (SAMPLE_RATE * AUDIO_BUFFER_DURATION_MS / 1000) / FRAME_SIZE
    
    // 唤醒词模板
    private lateinit var wakeWordTemplate: Array<FloatArray>
    private var templateLength = 0
    
    // 音频特征提取器
    private val featureExtractor = AudioFeatureExtractor()
    
    // VAD (Voice Activity Detection)
    private val vadDetector = VoiceActivityDetector()
    
    // 检测状态
    private var consecutiveDetections = 0
    private var lastDetectionTime = 0L
    private val minDetectionInterval = 3000L // 3秒内不重复检测
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            // 生成唤醒词模板
            generateWakeWordTemplate()
            isInitialized.set(true)
            Log.d(TAG, "语音唤醒检测器初始化完成，唤醒词: $wakeWord")
        } catch (e: Exception) {
            Log.e(TAG, "语音唤醒检测器初始化失败", e)
        }
    }
    
    private fun generateWakeWordTemplate() {
        // 为唤醒词生成音频特征模板
        // 这里使用简化的方法，实际应用中可以使用预录制的音频样本
        val phoneticFeatures = generatePhoneticFeatures(wakeWord)
        wakeWordTemplate = phoneticFeatures
        templateLength = phoneticFeatures.size
        
        Log.d(TAG, "唤醒词模板生成完成，长度: $templateLength")
    }
    
    private fun generatePhoneticFeatures(word: String): Array<FloatArray> {
        // 基于中文音素的简化特征生成
        val features = mutableListOf<FloatArray>()
        
        when (word) {
            "小助手" -> {
                // "小" (xiǎo) - 高频特征
                features.add(floatArrayOf(0.8f, 0.3f, 0.1f, 0.2f, 0.6f, 0.4f, 0.2f, 0.1f))
                features.add(floatArrayOf(0.7f, 0.4f, 0.2f, 0.1f, 0.5f, 0.3f, 0.2f, 0.1f))
                
                // "助" (zhù) - 中频特征
                features.add(floatArrayOf(0.4f, 0.7f, 0.5f, 0.3f, 0.2f, 0.6f, 0.4f, 0.2f))
                features.add(floatArrayOf(0.3f, 0.8f, 0.6f, 0.4f, 0.1f, 0.5f, 0.3f, 0.2f))
                
                // "手" (shǒu) - 低频特征
                features.add(floatArrayOf(0.2f, 0.4f, 0.8f, 0.6f, 0.3f, 0.2f, 0.5f, 0.4f))
                features.add(floatArrayOf(0.1f, 0.3f, 0.7f, 0.8f, 0.4f, 0.1f, 0.4f, 0.3f))
            }
            "你好" -> {
                // "你" (nǐ)
                features.add(floatArrayOf(0.6f, 0.5f, 0.3f, 0.4f, 0.7f, 0.2f, 0.3f, 0.4f))
                features.add(floatArrayOf(0.5f, 0.6f, 0.4f, 0.3f, 0.8f, 0.3f, 0.2f, 0.3f))
                
                // "好" (hǎo)
                features.add(floatArrayOf(0.3f, 0.2f, 0.6f, 0.8f, 0.4f, 0.5f, 0.6f, 0.3f))
                features.add(floatArrayOf(0.2f, 0.1f, 0.7f, 0.9f, 0.3f, 0.6f, 0.7f, 0.4f))
            }
            else -> {
                // 默认模式：为每个字符生成一个特征向量
                word.forEach { char ->
                    val hash = char.hashCode()
                    val feature = FloatArray(8) { i ->
                        sin((hash + i) * 0.1).toFloat().absoluteValue
                    }
                    features.add(feature)
                }
            }
        }
        
        return features.toTypedArray()
    }
    
    /**
     * 检测唤醒词
     */
    suspend fun detectWakeWord(audioData: ByteArray, length: Int): Boolean {
        return withContext(Dispatchers.Default) {
            if (!isInitialized.get() || length <= 0) {
                return@withContext false
            }
            
            try {
                // 转换音频数据
                val floatAudio = convertBytesToFloats(audioData, length)
                
                // 语音活动检测
                if (!vadDetector.detectVoiceActivity(floatAudio)) {
                    return@withContext false
                }
                
                // 提取音频特征
                val features = featureExtractor.extractMelSpectrogram(floatAudio)
                
                // 添加到缓冲区
                addToBuffer(features)
                
                // 检测唤醒词
                val detectionResult = performWakeWordDetection()
                
                if (detectionResult) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastDetectionTime > minDetectionInterval) {
                        lastDetectionTime = currentTime
                        consecutiveDetections = 0
                        Log.d(TAG, "检测到唤醒词: $wakeWord")
                        return@withContext true
                    }
                }
                
                false
                
            } catch (e: Exception) {
                Log.e(TAG, "唤醒词检测失败", e)
                false
            }
        }
    }
    
    private fun convertBytesToFloats(bytes: ByteArray, length: Int): FloatArray {
        val floats = FloatArray(length / 2)
        
        for (i in 0 until length / 2) {
            val index = i * 2
            if (index + 1 < length) {
                val sample = (bytes[index].toInt() and 0xFF) or 
                            ((bytes[index + 1].toInt() and 0xFF) shl 8)
                
                // 转换为16位有符号整数
                val signedSample = if (sample > 32767) sample - 65536 else sample
                
                // 归一化到[-1, 1]
                floats[i] = signedSample / 32768.0f
            }
        }
        
        return floats
    }
    
    private fun addToBuffer(features: FloatArray) {
        audioBuffer.offer(features)
        
        // 保持缓冲区大小
        while (audioBuffer.size > maxBufferSize) {
            audioBuffer.poll()
        }
    }
    
    private fun performWakeWordDetection(): Boolean {
        if (audioBuffer.size < templateLength) {
            return false
        }
        
        val bufferArray = audioBuffer.toTypedArray()
        val bufferSize = bufferArray.size
        
        // 滑动窗口检测
        for (startIndex in 0..(bufferSize - templateLength)) {
            val similarity = calculateSimilarity(bufferArray, startIndex)
            
            if (similarity > DETECTION_THRESHOLD) {
                consecutiveDetections++
                
                if (consecutiveDetections >= MIN_CONFIDENCE_SAMPLES) {
                    return true
                }
            } else {
                consecutiveDetections = 0
            }
        }
        
        return false
    }
    
    private fun calculateSimilarity(
        audioFeatures: Array<FloatArray>, 
        startIndex: Int
    ): Float {
        var totalSimilarity = 0.0f
        var validComparisons = 0
        
        for (i in 0 until templateLength) {
            val audioIndex = startIndex + i
            if (audioIndex < audioFeatures.size) {
                val audioFeature = audioFeatures[audioIndex]
                val templateFeature = wakeWordTemplate[i]
                
                val similarity = cosineSimilarity(audioFeature, templateFeature)
                totalSimilarity += similarity
                validComparisons++
            }
        }
        
        return if (validComparisons > 0) {
            totalSimilarity / validComparisons
        } else {
            0.0f
        }
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val minLength = min(a.size, b.size)
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        
        for (i in 0 until minLength) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) {
            dotProduct / denominator
        } else {
            0.0f
        }
    }
    
    /**
     * 音频特征提取器
     */
    private inner class AudioFeatureExtractor {
        
        fun extractMelSpectrogram(audio: FloatArray): FloatArray {
            if (audio.size < FRAME_SIZE) {
                return FloatArray(MEL_FILTERS) { 0.0f }
            }
            
            // 应用窗函数
            val windowedAudio = applyHammingWindow(audio)
            
            // FFT
            val spectrum = performFFT(windowedAudio)
            
            // 计算功率谱
            val powerSpectrum = calculatePowerSpectrum(spectrum)
            
            // Mel滤波器组
            val melFeatures = applyMelFilterbank(powerSpectrum)
            
            // 对数变换
            return melFeatures.map { ln(it + 1e-10f) }.toFloatArray()
        }
        
        private fun applyHammingWindow(audio: FloatArray): FloatArray {
            val windowed = FloatArray(audio.size)
            for (i in audio.indices) {
                val window = 0.54 - 0.46 * cos(2.0 * PI * i / (audio.size - 1))
                windowed[i] = (audio[i] * window).toFloat()
            }
            return windowed
        }
        
        private fun performFFT(audio: FloatArray): Array<Complex> {
            // 简化的FFT实现
            val n = audio.size
            val result = Array(n) { i -> Complex(audio[i].toDouble(), 0.0) }
            
            // 使用简化的DFT算法
            for (k in 0 until n / 2) {
                var realSum = 0.0
                var imagSum = 0.0
                
                for (n_idx in 0 until n) {
                    val angle = -2.0 * PI * k * n_idx / n
                    realSum += audio[n_idx] * cos(angle)
                    imagSum += audio[n_idx] * sin(angle)
                }
                
                result[k] = Complex(realSum, imagSum)
            }
            
            return result
        }
        
        private fun calculatePowerSpectrum(spectrum: Array<Complex>): FloatArray {
            return spectrum.map { complex ->
                (complex.real * complex.real + complex.imag * complex.imag).toFloat()
            }.toFloatArray()
        }
        
        private fun applyMelFilterbank(powerSpectrum: FloatArray): FloatArray {
            val melFeatures = FloatArray(MEL_FILTERS)
            val freqStep = SAMPLE_RATE / 2.0 / powerSpectrum.size
            
            // 简化的Mel滤波器组
            for (i in 0 until MEL_FILTERS) {
                val centerFreq = MIN_FREQUENCY + 
                    (MAX_FREQUENCY - MIN_FREQUENCY) * i / (MEL_FILTERS - 1)
                
                val startBin = (centerFreq / freqStep * 0.8).toInt()
                val endBin = (centerFreq / freqStep * 1.2).toInt()
                
                var sum = 0.0f
                var count = 0
                
                for (bin in startBin..min(endBin, powerSpectrum.size - 1)) {
                    if (bin >= 0) {
                        sum += powerSpectrum[bin]
                        count++
                    }
                }
                
                melFeatures[i] = if (count > 0) sum / count else 0.0f
            }
            
            return melFeatures
        }
    }
    
    /**
     * 语音活动检测器
     */
    private inner class VoiceActivityDetector {
        
        fun detectVoiceActivity(audio: FloatArray): Boolean {
            if (audio.isEmpty()) return false
            
            // 计算音频能量
            val energy = calculateEnergy(audio)
            
            // 计算过零率
            val zcr = calculateZeroCrossingRate(audio)
            
            // 简单的VAD逻辑
            return energy > VOICE_ACTIVITY_THRESHOLD && zcr > 0.01
        }
        
        private fun calculateEnergy(audio: FloatArray): Float {
            var energy = 0.0f
            for (sample in audio) {
                energy += sample * sample
            }
            return energy / audio.size
        }
        
        private fun calculateZeroCrossingRate(audio: FloatArray): Float {
            var crossings = 0
            for (i in 1 until audio.size) {
                if ((audio[i] >= 0) != (audio[i - 1] >= 0)) {
                    crossings++
                }
            }
            return crossings.toFloat() / audio.size
        }
    }
    
    /**
     * 复数类
     */
    private data class Complex(val real: Double, val imag: Double) {
        operator fun plus(other: Complex) = Complex(real + other.real, imag + other.imag)
        operator fun minus(other: Complex) = Complex(real - other.real, imag - other.imag)
        operator fun times(other: Complex) = Complex(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
        )
        
        fun magnitude() = sqrt(real * real + imag * imag)
    }
    
    /**
     * 获取检测器状态
     */
    fun getDetectorStatus(): Map<String, Any> {
        return mapOf(
            "isInitialized" to isInitialized.get(),
            "wakeWord" to wakeWord,
            "bufferSize" to audioBuffer.size,
            "maxBufferSize" to maxBufferSize,
            "templateLength" to templateLength,
            "consecutiveDetections" to consecutiveDetections,
            "lastDetectionTime" to lastDetectionTime
        )
    }
    
    /**
     * 重置检测器状态
     */
    fun reset() {
        audioBuffer.clear()
        consecutiveDetections = 0
        lastDetectionTime = 0L
        Log.d(TAG, "检测器状态已重置")
    }
    
    /**
     * 更新唤醒词
     */
    fun updateWakeWord(newWakeWord: String) {
        if (newWakeWord != wakeWord) {
            reset()
            generateWakeWordTemplate()
            Log.d(TAG, "唤醒词已更新: $newWakeWord")
        }
    }
}