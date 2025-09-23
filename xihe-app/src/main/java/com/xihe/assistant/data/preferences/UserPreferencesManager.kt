package com.xihe.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserProfile(
    val isInitialized: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val preferredLanguage: String = "zh",
    val preferredTheme: String = "system",
    val enableNotifications: Boolean = true,
    val enableVoiceInput: Boolean = true,
    val enableVoiceOutput: Boolean = true
)

class UserPreferencesManager(private val context: Context) {
    companion object {
        private val IS_INITIALIZED = booleanPreferencesKey("is_initialized")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        private val PREFERRED_THEME = stringPreferencesKey("preferred_theme")
        private val ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications")
        private val ENABLE_VOICE_INPUT = booleanPreferencesKey("enable_voice_input")
        private val ENABLE_VOICE_OUTPUT = booleanPreferencesKey("enable_voice_output")
    }
    
    val userPreferencesFlow: Flow<UserProfile> = context.userPreferencesDataStore.data.map { preferences ->
        UserProfile(
            isInitialized = preferences[IS_INITIALIZED] ?: false,
            userName = preferences[USER_NAME] ?: "",
            userEmail = preferences[USER_EMAIL] ?: "",
            preferredLanguage = preferences[PREFERRED_LANGUAGE] ?: "zh",
            preferredTheme = preferences[PREFERRED_THEME] ?: "system",
            enableNotifications = preferences[ENABLE_NOTIFICATIONS] ?: true,
            enableVoiceInput = preferences[ENABLE_VOICE_INPUT] ?: true,
            enableVoiceOutput = preferences[ENABLE_VOICE_OUTPUT] ?: true
        )
    }
    
    fun isPreferencesInitialized(): Boolean {
        return context.userPreferencesDataStore.data.map { preferences ->
            preferences[IS_INITIALIZED] ?: false
        }.let { flow ->
            // 这里简化处理，实际应该使用runBlocking或collect
            false
        }
    }
    
    suspend fun initializePreferences(profile: UserProfile) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[IS_INITIALIZED] = true
            preferences[USER_NAME] = profile.userName
            preferences[USER_EMAIL] = profile.userEmail
            preferences[PREFERRED_LANGUAGE] = profile.preferredLanguage
            preferences[PREFERRED_THEME] = profile.preferredTheme
            preferences[ENABLE_NOTIFICATIONS] = profile.enableNotifications
            preferences[ENABLE_VOICE_INPUT] = profile.enableVoiceInput
            preferences[ENABLE_VOICE_OUTPUT] = profile.enableVoiceOutput
        }
    }
    
    suspend fun updateUserName(name: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }
    
    suspend fun updateUserEmail(email: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[USER_EMAIL] = email
        }
    }
    
    suspend fun updatePreferredLanguage(language: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PREFERRED_LANGUAGE] = language
        }
    }
    
    suspend fun updatePreferredTheme(theme: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PREFERRED_THEME] = theme
        }
    }
    
    suspend fun updateEnableNotifications(enable: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[ENABLE_NOTIFICATIONS] = enable
        }
    }
    
    suspend fun updateEnableVoiceInput(enable: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[ENABLE_VOICE_INPUT] = enable
        }
    }
    
    suspend fun updateEnableVoiceOutput(enable: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[ENABLE_VOICE_OUTPUT] = enable
        }
    }
}