package com.ai.assistance.operit.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.ai.local.LocalAIEngine
import com.ai.assistance.operit.core.ai.local.VoiceWakeUpDetector
import com.ai.assistance.operit.core.ai.local.LocalTTSEngine
import com.ai.assistance.operit.core.ai.local.LocalSTTEngine
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.ui.main.MainActivity
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 智能助手常驻服务
 * 提供语音唤醒、实时响应、本地化AI处理等功能
 */
class IntelligentAssistantService : Service() {
    
    companion object {
        private const val TAG = "IntelligentAssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "intelligent_assistant_channel"
        private const val WAKE_WORD = "小助手"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 1024
        
        // 服务状态
        private var instance: IntelligentAssistantService? = null
        fun getInstance(): IntelligentAssistantService? = instance
        fun isRunning(): Boolean = instance != null
    }
    
    private val binder = LocalBinder()
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isListening = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    
    // 核心组件
    private lateinit var toolHandler: AIToolHandler
    private lateinit var localAIEngine: LocalAIEngine
    private lateinit var wakeUpDetector: VoiceWakeUpDetector
    private lateinit var ttsEngine: LocalTTSEngine
    private lateinit var sttEngine: LocalSTTEngine
    
    // 电源管理
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 音频录制
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): IntelligentAssistantService = this@IntelligentAssistantService
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.d(TAG, "智能助手服务创建")
        
        // 初始化组件
        initializeComponents()
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        startForegroundService()
        
        // 获取电源管理
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // 开始监听唤醒词
        startWakeWordDetection()
    }
    
    private fun initializeComponents() {
        // 初始化工具处理器
        toolHandler = AIToolHandler(this)
        
        // 初始化本地AI引擎
        localAIEngine = LocalAIEngine(this)
        
        // 初始化语音唤醒检测器
        wakeUpDetector = VoiceWakeUpDetector(this, WAKE_WORD)
        
        // 初始化TTS引擎
        ttsEngine = LocalTTSEngine(this)
        
        // 初始化STT引擎
        sttEngine = LocalSTTEngine(this)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "智能助手服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "智能助手后台服务，提供语音唤醒和实时响应功能"
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundService() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("智能助手正在运行")
            .setContentText("说出\"$WAKE_WORD\"来唤醒我")
            .setSmallIcon(R.drawable.ic_assistant)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startWakeWordDetection() {
        if (isListening.get()) return
        
        serviceScope.launch {
            try {
                isListening.set(true)
                Log.d(TAG, "开始语音唤醒检测")
                
                // 获取WakeLock防止CPU休眠
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "IntelligentAssistant::WakeUpDetection"
                )
                wakeLock?.acquire(10 * 60 * 1000L) // 10分钟
                
                // 初始化音频录制
                initAudioRecord()
                
                // 开始录制和检测
                startRecordingAndDetection()
                
            } catch (e: Exception) {
                Log.e(TAG, "语音唤醒检测启动失败", e)
                isListening.set(false)
            }
        }
    }
    
    private fun initAudioRecord() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("音频录制初始化失败")
        }
    }
    
    private suspend fun startRecordingAndDetection() {
        withContext(Dispatchers.IO) {
            audioRecord?.startRecording()
            
            val buffer = ByteArray(BUFFER_SIZE)
            
            while (isListening.get()) {
                try {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // 检测唤醒词
                        val isWakeWordDetected = wakeUpDetector.detectWakeWord(buffer, bytesRead)
                        
                        if (isWakeWordDetected && !isProcessing.get()) {
                            Log.d(TAG, "检测到唤醒词: $WAKE_WORD")
                            handleWakeUpDetected()
                        }
                    }
                    
                    // 短暂休眠，减少CPU占用
                    delay(10)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "音频录制过程中发生错误", e)
                    break
                }
            }
            
            audioRecord?.stop()
        }
    }
    
    private fun handleWakeUpDetected() {
        if (isProcessing.get()) return
        
        serviceScope.launch {
            try {
                isProcessing.set(true)
                
                // 播放唤醒提示音
                ttsEngine.speak("我在，请说")
                
                // 等待TTS完成
                delay(1000)
                
                // 开始监听用户指令
                startListeningForCommand()
                
            } catch (e: Exception) {
                Log.e(TAG, "处理唤醒事件失败", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    private suspend fun startListeningForCommand() {
        Log.d(TAG, "开始监听用户指令")
        
        try {
            // 录制用户语音指令（5秒超时）
            val audioData = recordUserCommand(5000)
            
            if (audioData.isNotEmpty()) {
                // 语音转文字
                val userCommand = sttEngine.speechToText(audioData)
                
                if (userCommand.isNotBlank()) {
                    Log.d(TAG, "用户指令: $userCommand")
                    
                    // 处理用户指令
                    processUserCommand(userCommand)
                } else {
                    ttsEngine.speak("抱歉，我没有听清楚，请重新说一遍")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理用户指令失败", e)
            ttsEngine.speak("抱歉，处理出现了问题")
        }
    }
    
    private suspend fun recordUserCommand(timeoutMs: Long): ByteArray {
        return withContext(Dispatchers.IO) {
            val audioData = mutableListOf<Byte>()
            val buffer = ByteArray(BUFFER_SIZE)
            val startTime = System.currentTimeMillis()
            
            // 临时停止唤醒词检测
            val tempAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            )
            
            tempAudioRecord.startRecording()
            
            try {
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val bytesRead = tempAudioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        audioData.addAll(buffer.take(bytesRead))
                    }
                    delay(10)
                }
            } finally {
                tempAudioRecord.stop()
                tempAudioRecord.release()
            }
            
            audioData.toByteArray()
        }
    }
    
    private suspend fun processUserCommand(command: String) {
        try {
            // 使用本地AI引擎处理指令
            val aiResponse = localAIEngine.processCommand(command, toolHandler)
            
            // 播放AI响应
            ttsEngine.speak(aiResponse)
            
            Log.d(TAG, "AI响应: $aiResponse")
            
        } catch (e: Exception) {
            Log.e(TAG, "处理用户指令失败", e)
            ttsEngine.speak("抱歉，我现在无法处理这个请求")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动命令")
        return START_STICKY // 服务被杀死后自动重启
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "智能助手服务销毁")
        
        // 停止监听
        isListening.set(false)
        isProcessing.set(false)
        
        // 释放资源
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        
        // 释放WakeLock
        wakeLock?.release()
        
        // 取消协程
        serviceScope.cancel()
        
        // 释放AI引擎资源
        localAIEngine.release()
        ttsEngine.release()
        sttEngine.release()
        
        instance = null
    }
    
    // 公共方法：手动触发对话
    fun triggerConversation(text: String) {
        serviceScope.launch {
            processUserCommand(text)
        }
    }
    
    // 公共方法：获取服务状态
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "isListening" to isListening.get(),
            "isProcessing" to isProcessing.get(),
            "localAILoaded" to localAIEngine.isLoaded(),
            "wakeWord" to WAKE_WORD
        )
    }
}