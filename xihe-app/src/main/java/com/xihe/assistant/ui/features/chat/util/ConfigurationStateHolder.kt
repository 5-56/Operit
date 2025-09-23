package com.xihe.assistant.ui.features.chat.util

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class ConfigurationStateHolder {
    var isConfigured by mutableStateOf(false)
        private set
    
    fun setConfigured(configured: Boolean) {
        isConfigured = configured
    }
}