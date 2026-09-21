package com.youme.memoria.settings.FoldersManager

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow

import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "folder_prefs")

class FolderPrefs(private val appContext: Context) {
    private val SELECTED_BUCKETS = stringSetPreferencesKey("selected_buckets")

    val selectedBuckets: Flow<Set<String>> =
        appContext.dataStore.data.map { it[SELECTED_BUCKETS] ?: emptySet() }

    suspend fun setSelectedBuckets(ids: Set<String>) {
        appContext.dataStore.edit { it[SELECTED_BUCKETS] = ids }
    }
}