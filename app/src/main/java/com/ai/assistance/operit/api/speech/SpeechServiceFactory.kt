package com.ai.assistance.operit.api.speech

import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.first

object SpeechServiceFactory {
    
    // 改为suspend函数
    suspend fun createSTTService(prefs: UserPreferencesManager): STTService {
        val type = prefs.sttServiceTypeFlow.first()
        
        return when (type) {
            STTServiceType.ACCESSIBILITY -> AccessibilitySTTProvider()
            STTServiceType.SHERPA -> {
                val sherpaConfig = prefs.sherpaSTTConfigFlow.first()
                SherpaSpeechProvider(sherpaConfig)
            }
            STTServiceType.HTTP -> {
                val httpConfig = prefs.httpSTTConfigFlow.first()
                HttpSTTProvider(httpConfig)
            }
        }
    }
    
    // 获取默认语音识别服务
    suspend fun getDefaultSTTService(prefs: UserPreferencesManager): STTService {
        val selectedType = prefs.sttServiceTypeFlow.first()
        
        return when (selectedType) {
            STTServiceType.ACCESSIBILITY -> AccessibilitySTTProvider()
            STTServiceType.SHERPA -> {
                val sherpaConfig = prefs.sherpaSTTConfigFlow.first()
                SherpaSpeechProvider(sherpaConfig)
            }
            STTServiceType.HTTP -> {
                val httpConfig = prefs.httpSTTConfigFlow.first()
                HttpSTTProvider(httpConfig)
            }
        }
    }
}
