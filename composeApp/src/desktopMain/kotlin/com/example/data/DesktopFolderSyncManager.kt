package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.prefs.Preferences

class DesktopFolderSyncManager(
    private val repository: ProjectRepository
) : FolderSyncManager {
    private val prefs = Preferences.userRoot().node("pdfgenerator_sync")

    override fun getConfig(): FolderSyncConfig {
        return FolderSyncConfig(
            rootFolderUri = prefs.get("root_folder_uri", ""),
            isAutoSync = prefs.getBoolean("auto_sync", false)
        )
    }

    override fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        prefs.put("root_folder_uri", rootFolderUri)
        prefs.putBoolean("auto_sync", isAutoSync)
        prefs.flush()
    }

    override fun isConfigured(): Boolean {
        val path = getConfig().rootFolderUri
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isDirectory && file.canWrite()
    }

    override fun deleteProjectFolder(createdAt: Long): Boolean {
        val rootPath = getConfig().rootFolderUri
        if (rootPath.isBlank()) return false
        val rootDir = File(rootPath)
        val projDir = rootDir.listFiles()?.find { it.isDirectory && it.name.endsWith("_$createdAt") }
        return projDir?.deleteRecursively() ?: false
    }

    override fun runSync(realSync: Boolean): Flow<SyncState> = flow {
        emit(SyncState.Syncing("Desktop sync is a simplified placeholder", 0.1f, "Synchronizing files directly..."))
        // Implementation similar to Android but using File API
        emit(SyncState.Success("Desktop sync simulation completed."))
    }
}
