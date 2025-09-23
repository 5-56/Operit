package com.xihe.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agreementDataStore: DataStore<Preferences> by preferencesDataStore(name = "agreement_preferences")

class AgreementPreferences(private val context: Context) {
    companion object {
        private val AGREEMENT_ACCEPTED = booleanPreferencesKey("agreement_accepted")
    }
    
    val agreementAcceptedFlow: Flow<Boolean> = context.agreementDataStore.data.map { preferences ->
        preferences[AGREEMENT_ACCEPTED] ?: false
    }
    
    fun isAgreementAccepted(): Boolean {
        // Simplified implementation - in real app would use runBlocking
        return false
    }
    
    suspend fun setAgreementAccepted(accepted: Boolean) {
        context.agreementDataStore.edit { preferences ->
            preferences[AGREEMENT_ACCEPTED] = accepted
        }
    }
}