package com.ai.assistance.operit.api.voice

import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.first

/** 语音服务工厂，用于创建不同类型的语音服务实例 */
object VoiceServiceFactory {
    /** 语音服务类型枚举 */
    enum class VoiceServiceType {
        /** 基于Android系统TTS的简单语音实现 */
        SIMPLE_TTS,
        /** 基于HTTP请求的TTS实现 */
        HTTP_TTS,
    }

    private suspend fun getTTSService(prefs: UserPreferencesManager): TTSService {
        // 使用suspend函数而不是runBlocking
        val config = prefs.ttsConfigFlow.first()
        
        return when (config.serviceType) {
            TTSServiceType.ACCESSIBILITY -> AccessibilityVoiceProvider()
            TTSServiceType.HTTP -> {
                val httpConfig = prefs.httpTtsConfigFlow.first()
                HttpVoiceProvider(httpConfig)
            }
            TTSServiceType.SHERPA -> {
                val sherpaConfig = prefs.sherpaTtsConfigFlow.first()
                SherpaTtsProvider(sherpaConfig)
            }
        }
    }
    
    // 改为suspend函数
    suspend fun createTTSService(prefs: UserPreferencesManager): TTSService {
        return getTTSService(prefs)
    }
    
    // 提供默认服务的工厂方法，但仍需要在协程中调用
    suspend fun getDefaultTTSService(prefs: UserPreferencesManager): TTSService {
        val selectedType = prefs.ttsServiceTypeFlow.first()
        
        return when (selectedType) {
            TTSServiceType.ACCESSIBILITY -> AccessibilityVoiceProvider()
            TTSServiceType.HTTP -> {
                val httpConfig = prefs.httpTtsConfigFlow.first()
                HttpVoiceProvider(httpConfig)
            }
            TTSServiceType.SHERPA -> {
                val sherpaConfig = prefs.sherpaTtsConfigFlow.first()
                SherpaTtsProvider(sherpaConfig)
            }
        }
    }
}
