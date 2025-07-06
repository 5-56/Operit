package com.ai.assistance.operit.core.ai.local

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * 本地TTS引擎
 * 支持离线文字转语音，结合系统TTS和简化语音合成
 */
class LocalTTSEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalTTSEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 4096
        
        // 语音合成参数
        private const val DEFAULT_PITCH = 1.0f
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val TONE_DURATION_MS = 200
        private const val PAUSE_DURATION_MS = 100
    }
    
    private var systemTTS: TextToSpeech? = null
    private val isSystemTTSReady = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    
    // 简单语音合成器
    private val simpleSynthesizer = SimpleSpeechSynthesizer()
    
    // 中文拼音映射
    private val pinyinMapping = createPinyinMapping()
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            // 初始化系统TTS
            initializeSystemTTS()
            
            // 初始化音频播放
            initializeAudioTrack()
            
            isInitialized.set(true)
            Log.d(TAG, "本地TTS引擎初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "本地TTS引擎初始化失败", e)
        }
    }
    
    private fun initializeSystemTTS() {
        systemTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = systemTTS?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "中文TTS不支持，使用英文")
                    systemTTS?.setLanguage(Locale.US)
                }
                
                // 设置语音参数
                systemTTS?.setPitch(DEFAULT_PITCH)
                systemTTS?.setSpeechRate(DEFAULT_SPEECH_RATE)
                
                isSystemTTSReady.set(true)
                Log.d(TAG, "系统TTS初始化成功")
            } else {
                Log.e(TAG, "系统TTS初始化失败")
                isSystemTTSReady.set(false)
            }
        }
    }
    
    private fun initializeAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
    }
    
    /**
     * 朗读文本
     */
    suspend fun speak(text: String) {
        withContext(Dispatchers.IO) {
            if (!isInitialized.get()) {
                Log.w(TAG, "TTS引擎未初始化")
                return@withContext
            }
            
            try {
                Log.d(TAG, "开始朗读: $text")
                
                // 优先使用系统TTS
                if (isSystemTTSReady.get() && useSystemTTS(text)) {
                    return@withContext
                }
                
                // 降级到简单语音合成
                synthesizeAndPlay(text)
                
            } catch (e: Exception) {
                Log.e(TAG, "TTS播放失败", e)
            }
        }
    }
    
    private fun useSystemTTS(text: String): Boolean {
        return try {
            val utteranceId = UUID.randomUUID().toString()
            val result = systemTTS?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            result == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "系统TTS播放失败", e)
            false
        }
    }
    
    private suspend fun synthesizeAndPlay(text: String) {
        withContext(Dispatchers.Default) {
            try {
                // 生成音频数据
                val audioData = simpleSynthesizer.synthesize(text)
                
                // 播放音频
                playAudio(audioData)
                
            } catch (e: Exception) {
                Log.e(TAG, "简单语音合成失败", e)
            }
        }
    }
    
    private suspend fun playAudio(audioData: ShortArray) {
        withContext(Dispatchers.IO) {
            try {
                audioTrack?.play()
                
                val chunkSize = BUFFER_SIZE / 2
                var offset = 0
                
                while (offset < audioData.size) {
                    val remainingData = audioData.size - offset
                    val currentChunkSize = minOf(chunkSize, remainingData)
                    
                    val bytesWritten = audioTrack?.write(
                        audioData, 
                        offset, 
                        currentChunkSize
                    ) ?: 0
                    
                    if (bytesWritten < 0) {
                        Log.e(TAG, "音频播放错误: $bytesWritten")
                        break
                    }
                    
                    offset += currentChunkSize
                }
                
                audioTrack?.stop()
                
            } catch (e: Exception) {
                Log.e(TAG, "音频播放失败", e)
            }
        }
    }
    
    /**
     * 简单语音合成器
     */
    private inner class SimpleSpeechSynthesizer {
        
        fun synthesize(text: String): ShortArray {
            val audioData = mutableListOf<Short>()
            
            // 处理每个字符
            text.forEach { char ->
                when {
                    char.isWhitespace() -> {
                        // 空格产生静音
                        audioData.addAll(generateSilence(PAUSE_DURATION_MS))
                    }
                    isChinesePunctuation(char) -> {
                        // 标点符号产生短暂停顿
                        audioData.addAll(generateSilence(PAUSE_DURATION_MS * 2))
                    }
                    char.isChineseCharacter() -> {
                        // 中文字符
                        audioData.addAll(synthesizeChineseCharacter(char))
                        audioData.addAll(generateSilence(PAUSE_DURATION_MS / 2))
                    }
                    char.isLetter() -> {
                        // 英文字符
                        audioData.addAll(synthesizeEnglishCharacter(char))
                        audioData.addAll(generateSilence(PAUSE_DURATION_MS / 4))
                    }
                    char.isDigit() -> {
                        // 数字
                        audioData.addAll(synthesizeDigit(char))
                        audioData.addAll(generateSilence(PAUSE_DURATION_MS / 4))
                    }
                    else -> {
                        // 其他字符产生短暂停顿
                        audioData.addAll(generateSilence(PAUSE_DURATION_MS / 2))
                    }
                }
            }
            
            return audioData.toShortArray()
        }
        
        private fun synthesizeChineseCharacter(char: Char): List<Short> {
            // 获取拼音信息
            val pinyin = pinyinMapping[char.toString()] ?: "a"
            
            // 根据拼音生成音调
            return generateToneForPinyin(pinyin)
        }
        
        private fun synthesizeEnglishCharacter(char: Char): List<Short> {
            // 英文字符的简单频率映射
            val frequency = when (char.lowercaseChar()) {
                'a' -> 220.0
                'e' -> 330.0
                'i' -> 440.0
                'o' -> 550.0
                'u' -> 660.0
                'b', 'p', 'm' -> 150.0  // 唇音
                'd', 't', 'n', 'l' -> 250.0  // 舌音
                'g', 'k', 'h' -> 200.0  // 喉音
                'f', 'v' -> 300.0  // 摩擦音
                's', 'z' -> 400.0  // 嘶音
                'r' -> 180.0  // 颤音
                'j', 'q', 'x' -> 500.0  // 近音
                'w', 'y' -> 160.0  // 半元音
                else -> 300.0
            }
            
            return generateTone(frequency, TONE_DURATION_MS / 2)
        }
        
        private fun synthesizeDigit(char: Char): List<Short> {
            // 数字的频率映射
            val frequency = when (char) {
                '0' -> 200.0
                '1' -> 250.0
                '2' -> 300.0
                '3' -> 350.0
                '4' -> 400.0
                '5' -> 450.0
                '6' -> 500.0
                '7' -> 550.0
                '8' -> 600.0
                '9' -> 650.0
                else -> 400.0
            }
            
            return generateTone(frequency, TONE_DURATION_MS / 2)
        }
        
        private fun generateToneForPinyin(pinyin: String): List<Short> {
            // 简化的中文音调生成
            val baseFrequency = when {
                pinyin.startsWith("a") -> 220.0
                pinyin.startsWith("o") -> 330.0
                pinyin.startsWith("e") -> 440.0
                pinyin.startsWith("i") -> 550.0
                pinyin.startsWith("u") -> 660.0
                pinyin.startsWith("ü") -> 770.0
                pinyin.startsWith("b") -> 150.0
                pinyin.startsWith("p") -> 160.0
                pinyin.startsWith("m") -> 170.0
                pinyin.startsWith("f") -> 180.0
                pinyin.startsWith("d") -> 200.0
                pinyin.startsWith("t") -> 210.0
                pinyin.startsWith("n") -> 220.0
                pinyin.startsWith("l") -> 230.0
                pinyin.startsWith("g") -> 250.0
                pinyin.startsWith("k") -> 260.0
                pinyin.startsWith("h") -> 270.0
                pinyin.startsWith("j") -> 300.0
                pinyin.startsWith("q") -> 310.0
                pinyin.startsWith("x") -> 320.0
                pinyin.startsWith("zh") -> 350.0
                pinyin.startsWith("ch") -> 360.0
                pinyin.startsWith("sh") -> 370.0
                pinyin.startsWith("r") -> 380.0
                pinyin.startsWith("z") -> 400.0
                pinyin.startsWith("c") -> 410.0
                pinyin.startsWith("s") -> 420.0
                pinyin.startsWith("w") -> 450.0
                pinyin.startsWith("y") -> 460.0
                else -> 300.0
            }
            
            // 根据声调调整频率
            val tonePattern = when {
                pinyin.endsWith("1") -> TonePattern.FIRST  // 一声：平
                pinyin.endsWith("2") -> TonePattern.SECOND // 二声：升
                pinyin.endsWith("3") -> TonePattern.THIRD  // 三声：降升
                pinyin.endsWith("4") -> TonePattern.FOURTH // 四声：降
                else -> TonePattern.NEUTRAL // 轻声
            }
            
            return generateToneWithPattern(baseFrequency, tonePattern)
        }
        
        private fun generateToneWithPattern(
            baseFrequency: Double, 
            pattern: TonePattern
        ): List<Short> {
            val duration = TONE_DURATION_MS
            val samplesCount = (SAMPLE_RATE * duration / 1000.0).toInt()
            val audioData = mutableListOf<Short>()
            
            for (i in 0 until samplesCount) {
                val progress = i.toDouble() / samplesCount
                
                val frequency = when (pattern) {
                    TonePattern.FIRST -> baseFrequency  // 平调
                    TonePattern.SECOND -> baseFrequency * (1.0 + 0.3 * progress)  // 升调
                    TonePattern.THIRD -> {  // 降升调
                        if (progress < 0.6) {
                            baseFrequency * (1.0 - 0.2 * progress / 0.6)
                        } else {
                            baseFrequency * (0.8 + 0.4 * (progress - 0.6) / 0.4)
                        }
                    }
                    TonePattern.FOURTH -> baseFrequency * (1.0 - 0.3 * progress)  // 降调
                    TonePattern.NEUTRAL -> baseFrequency * 0.9  // 轻声
                }
                
                // 生成正弦波
                val angle = 2.0 * PI * frequency * i / SAMPLE_RATE
                val amplitude = 0.3 * (1.0 - progress * 0.2)  // 渐弱
                val sample = (amplitude * sin(angle) * Short.MAX_VALUE).toInt()
                
                audioData.add(sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            
            return audioData
        }
        
        private fun generateTone(frequency: Double, durationMs: Int): List<Short> {
            val samplesCount = (SAMPLE_RATE * durationMs / 1000.0).toInt()
            val audioData = mutableListOf<Short>()
            
            for (i in 0 until samplesCount) {
                val angle = 2.0 * PI * frequency * i / SAMPLE_RATE
                val amplitude = 0.3 * (1.0 - i.toDouble() / samplesCount * 0.5)  // 渐弱
                val sample = (amplitude * sin(angle) * Short.MAX_VALUE).toInt()
                
                audioData.add(sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            
            return audioData
        }
        
        private fun generateSilence(durationMs: Int): List<Short> {
            val samplesCount = (SAMPLE_RATE * durationMs / 1000.0).toInt()
            return List(samplesCount) { 0.toShort() }
        }
    }
    
    private enum class TonePattern {
        FIRST,   // 一声
        SECOND,  // 二声
        THIRD,   // 三声
        FOURTH,  // 四声
        NEUTRAL  // 轻声
    }
    
    // 扩展函数
    private fun Char.isChineseCharacter(): Boolean {
        return this.code in 0x4E00..0x9FFF
    }
    
    private fun isChinesePunctuation(char: Char): Boolean {
        return char in "，。！？；：""''（）【】《》"
    }
    
    private fun createPinyinMapping(): Map<String, String> {
        return mapOf(
            // 常用字的拼音映射（简化版）
            "你" to "ni3",
            "好" to "hao3",
            "我" to "wo3",
            "是" to "shi4",
            "的" to "de5",
            "在" to "zai4",
            "有" to "you3",
            "不" to "bu4",
            "了" to "le5",
            "人" to "ren2",
            "他" to "ta1",
            "这" to "zhe4",
            "个" to "ge4",
            "上" to "shang4",
            "来" to "lai2",
            "到" to "dao4",
            "时" to "shi2",
            "会" to "hui4",
            "可" to "ke3",
            "什" to "shen2",
            "么" to "me5",
            "小" to "xiao3",
            "助" to "zhu4",
            "手" to "shou3",
            "帮" to "bang1",
            "助" to "zhu4",
            "开" to "kai1",
            "关" to "guan1",
            "打" to "da3",
            "电" to "dian4",
            "话" to "hua4",
            "短" to "duan3",
            "信" to "xin4",
            "搜" to "sou1",
            "索" to "suo3",
            "查" to "cha2",
            "找" to "zhao3",
            "设" to "she4",
            "置" to "zhi4",
            "时" to "shi2",
            "间" to "jian1",
            "现" to "xian4",
            "已" to "yi3",
            "为" to "wei2",
            "您" to "nin2",
            "请" to "qing3",
            "谢" to "xie4",
            "抱" to "bao4",
            "歉" to "qian4"
        )
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        try {
            systemTTS?.stop()
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "停止TTS播放失败", e)
        }
    }
    
    /**
     * 设置语音参数
     */
    fun setSpeechParameters(pitch: Float, speechRate: Float) {
        systemTTS?.setPitch(pitch)
        systemTTS?.setSpeechRate(speechRate)
    }
    
    /**
     * 检查TTS是否可用
     */
    fun isAvailable(): Boolean {
        return isInitialized.get()
    }
    
    /**
     * 获取TTS状态
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "isInitialized" to isInitialized.get(),
            "isSystemTTSReady" to isSystemTTSReady.get(),
            "isAudioTrackReady" to (audioTrack != null),
            "sampleRate" to SAMPLE_RATE,
            "audioFormat" to AUDIO_FORMAT
        )
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            systemTTS?.shutdown()
            audioTrack?.release()
            isInitialized.set(false)
            isSystemTTSReady.set(false)
            Log.d(TAG, "TTS引擎资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放TTS资源失败", e)
        }
    }
}