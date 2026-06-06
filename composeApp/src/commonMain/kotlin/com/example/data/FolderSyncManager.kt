package com.example.data

import kotlinx.coroutines.flow.Flow

data class FolderSyncConfig(
    val rootFolderUri: String,
    val isAutoSync: Boolean = false
)

interface FolderSyncManager {
    fun getConfig(): FolderSyncConfig
    fun saveConfig(rootFolderUri: String, isAutoSync: Boolean)
    fun isConfigured(): Boolean
    fun deleteProjectFolder(createdAt: Long): Boolean
    fun runSync(realSync: Boolean): Flow<SyncState>
}
