package com.fylphzy.pantau

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

object DataStoreManager {
    private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    private val KEY_USERNAME = stringPreferencesKey("username")

    suspend fun saveLogin(context: Context, username: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_USERNAME] = username
        }
    }

    suspend fun clearLogin(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs.remove(KEY_USERNAME)
        }
    }

    fun isLoggedInFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_IS_LOGGED_IN] == true }

    fun usernameFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[KEY_USERNAME] }

    suspend fun getUsernameOnce(context: Context): String? {
        return context.dataStore.data.map { prefs -> prefs[KEY_USERNAME] }.first()
    }
}
