package com.xihe.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "api_preferences")

class ApiPreferences(private val context: Context) {
    companion object {
        private val SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        private val DEFAULT_MODEL = stringPreferencesKey("default_model")
        private val API_KEY = stringPreferencesKey("api_key")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val TEMPERATURE = doublePreferencesKey("temperature")
    }
    
    val showFpsCounterFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_FPS_COUNTER] ?: false
    }
    
    val defaultModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_MODEL] ?: "gpt-3.5-turbo"
    }
    
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }
    
    val maxTokensFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_TOKENS] ?: 2000
    }
    
    val temperatureFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[TEMPERATURE] ?: 0.7
    }
    
    suspend fun setShowFpsCounter(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_FPS_COUNTER] = show
        }
    }
    
    suspend fun setDefaultModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_MODEL] = model
        }
    }
    
    suspend fun setApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }
    
    suspend fun setMaxTokens(tokens: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_TOKENS] = tokens
        }
    }
    
    suspend fun setTemperature(temperature: Double) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE] = temperature
        }
    }
}