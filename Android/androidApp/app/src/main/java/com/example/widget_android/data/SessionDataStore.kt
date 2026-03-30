package com.example.widget_android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_session")

object PrefKeys {
    val TOKEN = stringPreferencesKey("auth_token")
    val SESSION_ID = longPreferencesKey("session_id")
    val EXPIRES_AT = stringPreferencesKey("expires_at")
    val USER_NAME = stringPreferencesKey("user_name")
    val USER_EMAIL = stringPreferencesKey("user_email")
    val ENTRY_START_MS = longPreferencesKey("entry_start_ms")
    val BREAK_START_MS = longPreferencesKey("break_start_ms")
    val MEAL_START_MS = longPreferencesKey("meal_start_ms")
}

class SessionRepository(private val context: Context) {

    private val store = context.applicationContext.appDataStore

    val authTokenFlow: Flow<String?> = store.data.map { it[PrefKeys.TOKEN] }

    suspend fun readToken(): String? = store.data.map { it[PrefKeys.TOKEN] }.first()

    suspend fun saveSession(token: String, sessionId: Long, expiresAt: String, userName: String, userEmail: String) {
        store.edit { prefs ->
            prefs[PrefKeys.TOKEN] = token
            prefs[PrefKeys.SESSION_ID] = sessionId
            prefs[PrefKeys.EXPIRES_AT] = expiresAt
            prefs[PrefKeys.USER_NAME] = userName
            prefs[PrefKeys.USER_EMAIL] = userEmail
        }
        TokenHolder.token = token
    }

    suspend fun clearSession() {
        store.edit { prefs ->
            prefs.remove(PrefKeys.TOKEN)
            prefs.remove(PrefKeys.SESSION_ID)
            prefs.remove(PrefKeys.EXPIRES_AT)
            prefs.remove(PrefKeys.USER_NAME)
            prefs.remove(PrefKeys.USER_EMAIL)
            prefs.remove(PrefKeys.ENTRY_START_MS)
            prefs.remove(PrefKeys.BREAK_START_MS)
            prefs.remove(PrefKeys.MEAL_START_MS)
        }
        TokenHolder.token = null
    }

    suspend fun readUserName(): String? = store.data.map { it[PrefKeys.USER_NAME] }.first()

    suspend fun readExpiresAt(): String? = store.data.map { it[PrefKeys.EXPIRES_AT] }.first()

    suspend fun saveUserName(name: String) {
        store.edit { it[PrefKeys.USER_NAME] = name }
    }

    suspend fun readEntryStartMs(): Long = store.data.map { it[PrefKeys.ENTRY_START_MS] ?: -1L }.first()

    suspend fun saveEntryStartMs(epochMs: Long) {
        store.edit { it[PrefKeys.ENTRY_START_MS] = epochMs }
    }

    suspend fun clearEntryStart() {
        store.edit { it.remove(PrefKeys.ENTRY_START_MS) }
    }

    suspend fun saveBreakStartMs(epochMs: Long) {
        store.edit { it[PrefKeys.BREAK_START_MS] = epochMs }
    }

    suspend fun readBreakStartMs(): Long =
        store.data.map { it[PrefKeys.BREAK_START_MS] ?: -1L }.first()

    suspend fun clearBreakStart() {
        store.edit { it.remove(PrefKeys.BREAK_START_MS) }
    }

    suspend fun saveMealStartMs(epochMs: Long) {
        store.edit { it[PrefKeys.MEAL_START_MS] = epochMs }
    }

    suspend fun readMealStartMs(): Long =
        store.data.map { it[PrefKeys.MEAL_START_MS] ?: -1L }.first()

    suspend fun clearMealStart() {
        store.edit { it.remove(PrefKeys.MEAL_START_MS) }
    }

    suspend fun clearBreakAndMeal() {
        store.edit {
            it.remove(PrefKeys.BREAK_START_MS)
            it.remove(PrefKeys.MEAL_START_MS)
        }
    }
}
