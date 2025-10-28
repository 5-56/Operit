package com.xihe.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * API偏好设置管理器
 * 管理API相关的配置
 */
class ApiPreferences private constructor(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "api_preferences")
        
        // API设置键
        private val API_KEY = stringPreferencesKey("api_key")
        private val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val MODEL_NAME = stringPreferencesKey("model_name")
        private val API_PROVIDER_TYPE = stringPreferencesKey("api_provider_type")
        private val SHOW_FPS_COUNTER = stringPreferencesKey("show_fps_counter")
        
        // 默认值
        const val DEFAULT_API_KEY = "your_api_key_here"
        const val DEFAULT_API_ENDPOINT = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL_NAME = "deepseek-chat"
        
        @Volatile private var INSTANCE: ApiPreferences? = null

        fun getInstance(context: Context): ApiPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // API密钥
    val apiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: DEFAULT_API_KEY
    }

    // API端点
    val apiEndpoint: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_ENDPOINT] ?: DEFAULT_API_ENDPOINT
    }

    // 模型名称
    val modelName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MODEL_NAME] ?: DEFAULT_MODEL_NAME
    }

    // API提供商类型
    val apiProviderType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_PROVIDER_TYPE] ?: "DEEPSEEK"
    }

    // 显示FPS计数器
    val showFpsCounterFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_FPS_COUNTER]?.toBoolean() ?: false
    }

    /**
     * 设置API密钥
     */
    suspend fun setApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    /**
     * 设置API端点
     */
    suspend fun setApiEndpoint(endpoint: String) {
        context.dataStore.edit { preferences ->
            preferences[API_ENDPOINT] = endpoint
        }
    }

    /**
     * 设置模型名称
     */
    suspend fun setModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = modelName
        }
    }

    /**
     * 设置API提供商类型
     */
    suspend fun setApiProviderType(providerType: String) {
        context.dataStore.edit { preferences ->
            preferences[API_PROVIDER_TYPE] = providerType
        }
    }

    /**
     * 设置显示FPS计数器
     */
    suspend fun setShowFpsCounter(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_FPS_COUNTER] = show.toString()
        }
    }
}