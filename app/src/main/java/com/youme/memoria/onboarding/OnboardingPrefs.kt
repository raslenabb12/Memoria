package com.youme.memoria.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object OnboardingPrefs {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    suspend fun setOnboardingDone(context: Context, done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_DONE] = done
        }
    }

    fun isOnboardingDone(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[ONBOARDING_DONE] ?: false
        }
    }
}