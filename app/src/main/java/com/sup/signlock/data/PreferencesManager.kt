package com.sup.signlock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "signlock_prefs")

class PreferencesManager(private val context: Context) {
    
    private val gson = Gson()
    
    companion object {
        private val SIGNATURE_TEMPLATES_KEY = stringPreferencesKey("signature_templates")
        private val LOCKED_APPS_KEY = stringSetPreferencesKey("locked_apps")
        private val IS_SETUP_COMPLETE_KEY = booleanPreferencesKey("is_setup_complete")
        private val SERVICE_ENABLED_KEY = booleanPreferencesKey("service_enabled")
    }
    
    suspend fun saveSignatureTemplates(templates: List<SignatureTemplate>) {
        val json = gson.toJson(templates)
        context.dataStore.edit { prefs ->
            prefs[SIGNATURE_TEMPLATES_KEY] = json
        }
    }
    
    fun getSignatureTemplates(): Flow<List<SignatureTemplate>> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[SIGNATURE_TEMPLATES_KEY] ?: return@map emptyList()
            try {
                val type = object : TypeToken<List<SignatureTemplate>>() {}.type
                gson.fromJson<List<SignatureTemplate>>(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    suspend fun saveLockedApps(apps: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[LOCKED_APPS_KEY] = apps
        }
    }
    
    fun getLockedApps(): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            prefs[LOCKED_APPS_KEY] ?: emptySet()
        }
    }
    
    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_SETUP_COMPLETE_KEY] = complete
        }
    }
    
    fun isSetupComplete(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[IS_SETUP_COMPLETE_KEY] ?: false
        }
    }
    
    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SERVICE_ENABLED_KEY] = enabled
        }
    }
    
    fun isServiceEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[SERVICE_ENABLED_KEY] ?: false
        }
    }
}
