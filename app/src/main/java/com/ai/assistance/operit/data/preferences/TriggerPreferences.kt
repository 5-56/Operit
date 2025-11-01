package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.triggerPreferencesDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "trigger_preferences")

class TriggerPreferences(private val context: Context) {
    
    companion object {
        private val NOTIFICATION_TRIGGERS_ENABLED = booleanPreferencesKey("notification_triggers_enabled")
        private val GEOFENCE_TRIGGERS_ENABLED = booleanPreferencesKey("geofence_triggers_enabled")
        private val NOTIFICATION_ACCESS_GRANTED = booleanPreferencesKey("notification_access_granted")
        private val LOCATION_ACCESS_GRANTED = booleanPreferencesKey("location_access_granted")
        private val USER_HAS_OPTED_IN = booleanPreferencesKey("user_has_opted_in")
        
        @Volatile
        private var INSTANCE: TriggerPreferences? = null
        
        fun getInstance(context: Context): TriggerPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TriggerPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    val notificationTriggersEnabled: Flow<Boolean> = 
        context.triggerPreferencesDataStore.data.map { preferences ->
            preferences[NOTIFICATION_TRIGGERS_ENABLED] ?: false
        }
    
    val geofenceTriggersEnabled: Flow<Boolean> = 
        context.triggerPreferencesDataStore.data.map { preferences ->
            preferences[GEOFENCE_TRIGGERS_ENABLED] ?: false
        }
    
    val notificationAccessGranted: Flow<Boolean> = 
        context.triggerPreferencesDataStore.data.map { preferences ->
            preferences[NOTIFICATION_ACCESS_GRANTED] ?: false
        }
    
    val locationAccessGranted: Flow<Boolean> = 
        context.triggerPreferencesDataStore.data.map { preferences ->
            preferences[LOCATION_ACCESS_GRANTED] ?: false
        }
    
    val userHasOptedIn: Flow<Boolean> = 
        context.triggerPreferencesDataStore.data.map { preferences ->
            preferences[USER_HAS_OPTED_IN] ?: false
        }
    
    suspend fun setNotificationTriggersEnabled(enabled: Boolean) {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[NOTIFICATION_TRIGGERS_ENABLED] = enabled
        }
    }
    
    suspend fun setGeofenceTriggersEnabled(enabled: Boolean) {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[GEOFENCE_TRIGGERS_ENABLED] = enabled
        }
    }
    
    suspend fun setNotificationAccessGranted(granted: Boolean) {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[NOTIFICATION_ACCESS_GRANTED] = granted
        }
    }
    
    suspend fun setLocationAccessGranted(granted: Boolean) {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[LOCATION_ACCESS_GRANTED] = granted
        }
    }
    
    suspend fun setUserOptIn(optedIn: Boolean) {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[USER_HAS_OPTED_IN] = optedIn
        }
    }
    
    suspend fun optInAndEnableAll() {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[USER_HAS_OPTED_IN] = true
            preferences[NOTIFICATION_TRIGGERS_ENABLED] = true
            preferences[GEOFENCE_TRIGGERS_ENABLED] = true
        }
    }
    
    suspend fun optOutAndDisableAll() {
        context.triggerPreferencesDataStore.edit { preferences ->
            preferences[USER_HAS_OPTED_IN] = false
            preferences[NOTIFICATION_TRIGGERS_ENABLED] = false
            preferences[GEOFENCE_TRIGGERS_ENABLED] = false
        }
    }
    
    suspend fun isNotificationTriggersEnabled(): Boolean {
        return notificationTriggersEnabled.first()
    }
    
    suspend fun isGeofenceTriggersEnabled(): Boolean {
        return geofenceTriggersEnabled.first()
    }
    
    suspend fun hasUserOptedIn(): Boolean {
        return userHasOptedIn.first()
    }
}
