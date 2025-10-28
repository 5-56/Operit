package com.xihe.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 用户偏好设置管理器
 * 管理用户的各种偏好设置
 */
class UserPreferencesManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
        
        // 偏好设置键
        private val ENABLE_AI_PLANNING = booleanPreferencesKey("enable_ai_planning")
        private val ENABLE_THINKING_MODE = booleanPreferencesKey("enable_thinking_mode")
        private val ENABLE_THINKING_GUIDANCE = booleanPreferencesKey("enable_thinking_guidance")
        private val ENABLE_MEMORY_ATTACHMENT = booleanPreferencesKey("enable_memory_attachment")
        private val SUMMARY_TOKEN_THRESHOLD = intPreferencesKey("summary_token_threshold")
        private val IS_AUTO_READ_ENABLED = booleanPreferencesKey("is_auto_read_enabled")
        private val MAX_WINDOW_SIZE_IN_K = intPreferencesKey("max_window_size_in_k")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val USE_BACKGROUND_IMAGE = booleanPreferencesKey("use_background_image")
        private val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        private val CHAT_HEADER_TRANSPARENT = booleanPreferencesKey("chat_header_transparent")
        private val CHAT_INPUT_TRANSPARENT = booleanPreferencesKey("chat_input_transparent")
        private val CHAT_HEADER_HISTORY_ICON_COLOR = stringPreferencesKey("chat_header_history_icon_color")
        private val CHAT_HEADER_PIP_ICON_COLOR = stringPreferencesKey("chat_header_pip_icon_color")
        private val CHAT_HEADER_OVERLAY_MODE = booleanPreferencesKey("chat_header_overlay_mode")
        private val SHOW_INPUT_PROCESSING_STATUS = booleanPreferencesKey("show_input_processing_status")
        private val CHAT_STYLE = stringPreferencesKey("chat_style")
        
        // 默认值
        const val DEFAULT_LANGUAGE = "zh"
        const val CHAT_STYLE_BUBBLE = "bubble"
        const val CHAT_STYLE_CURSOR = "cursor"
    }

    // AI规划功能
    val enableAiPlanning: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_AI_PLANNING] ?: true
    }

    // 思考模式
    val enableThinkingMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_THINKING_MODE] ?: false
    }

    // 思考引导
    val enableThinkingGuidance: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_THINKING_GUIDANCE] ?: true
    }

    // 记忆附件
    val enableMemoryAttachment: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_MEMORY_ATTACHMENT] ?: true
    }

    // 摘要Token阈值
    val summaryTokenThreshold: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SUMMARY_TOKEN_THRESHOLD] ?: 1000
    }

    // 自动阅读
    val isAutoReadEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_AUTO_READ_ENABLED] ?: false
    }

    // 最大窗口大小
    val maxWindowSizeInK: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_WINDOW_SIZE_IN_K] ?: 4
    }

    // 应用语言
    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE] ?: DEFAULT_LANGUAGE
    }

    // 使用背景图片
    val useBackgroundImage: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_BACKGROUND_IMAGE] ?: false
    }

    // 背景图片URI
    val backgroundImageUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[BACKGROUND_IMAGE_URI]
    }

    // 聊天头部透明
    val chatHeaderTransparent: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CHAT_HEADER_TRANSPARENT] ?: false
    }

    // 聊天输入透明
    val chatInputTransparent: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CHAT_INPUT_TRANSPARENT] ?: false
    }

    // 聊天头部历史图标颜色
    val chatHeaderHistoryIconColor: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CHAT_HEADER_HISTORY_ICON_COLOR]
    }

    // 聊天头部画中画图标颜色
    val chatHeaderPipIconColor: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CHAT_HEADER_PIP_ICON_COLOR]
    }

    // 聊天头部覆盖模式
    val chatHeaderOverlayMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CHAT_HEADER_OVERLAY_MODE] ?: false
    }

    // 显示输入处理状态
    val showInputProcessingStatus: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_INPUT_PROCESSING_STATUS] ?: true
    }

    // 聊天样式
    val chatStyle: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CHAT_STYLE] ?: CHAT_STYLE_CURSOR
    }

    // 用户偏好流
    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            isInitialized = true,
            enableAiPlanning = preferences[ENABLE_AI_PLANNING] ?: true,
            enableThinkingMode = preferences[ENABLE_THINKING_MODE] ?: false,
            enableThinkingGuidance = preferences[ENABLE_THINKING_GUIDANCE] ?: true,
            enableMemoryAttachment = preferences[ENABLE_MEMORY_ATTACHMENT] ?: true,
            summaryTokenThreshold = preferences[SUMMARY_TOKEN_THRESHOLD] ?: 1000,
            isAutoReadEnabled = preferences[IS_AUTO_READ_ENABLED] ?: false,
            maxWindowSizeInK = preferences[MAX_WINDOW_SIZE_IN_K] ?: 4,
            appLanguage = preferences[APP_LANGUAGE] ?: DEFAULT_LANGUAGE
        )
    }

    /**
     * 检查偏好设置是否已初始化
     */
    suspend fun isPreferencesInitialized(): Boolean {
        return try {
            val preferences = context.dataStore.data.first()
            preferences[ENABLE_AI_PLANNING] != null
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 用户偏好数据类
 */
data class UserPreferences(
    val isInitialized: Boolean = false,
    val enableAiPlanning: Boolean = true,
    val enableThinkingMode: Boolean = false,
    val enableThinkingGuidance: Boolean = true,
    val enableMemoryAttachment: Boolean = true,
    val summaryTokenThreshold: Int = 1000,
    val isAutoReadEnabled: Boolean = false,
    val maxWindowSizeInK: Int = 4,
    val appLanguage: String = UserPreferencesManager.DEFAULT_LANGUAGE
)