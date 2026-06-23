package com.example.data

import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.flow.Flow

class DesktopFolderSyncManager(
    private val repository: ProjectRepository
) : FolderSyncManager {
    
    private val orchestrator = FolderSyncOrchestrator(repository, DesktopFolderAccessor(repository))

    override fun getConfig(): FolderSyncConfig {
        return orchestrator.getConfig()
    }

    override fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        orchestrator.saveConfig(rootFolderUri, isAutoSync)
    }

    override fun isConfigured(): Boolean {
        return orchestrator.isConfigured()
    }

    override fun deleteProjectFolder(createdAt: Long): Boolean {
        return orchestrator.deleteProjectFolder(createdAt)
    }

    override fun runSync(realSync: Boolean): Flow<SyncState> {
        return orchestrator.runSync(realSync)
    }
}

class DesktopFolderAccessor(
    private val repository: ProjectRepository
) : FolderAccessor {
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

    override fun listSubFolders(): List<RemoteFolder> {
        val rootPath = getConfig().rootFolderUri
        if (rootPath.isBlank()) return emptyList()
        val rootDir = File(rootPath)
        val subDirs = rootDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return subDirs.map { DesktopRemoteFolder(it) }
    }

    override fun getOrCreateSubFolder(name: String, createdAt: Long): RemoteFolder? {
        val rootPath = getConfig().rootFolderUri
        if (rootPath.isBlank()) return null
        val rootDir = File(rootPath)
        val projDir = rootDir.listFiles()?.find { it.isDirectory && it.name.endsWith("_$createdAt") } ?: File(rootDir, name).apply { mkdirs() }
        return DesktopRemoteFolder(projDir)
    }
}

class DesktopRemoteFolder(private val folder: File) : RemoteFolder {
    override val name: String
        get() = folder.name

    override fun readProjectDataJson(): String? {
        val jsonFile = File(folder, "project_data.json")
        return if (jsonFile.exists()) jsonFile.readText() else null
    }

    override fun writeProjectDataJson(content: String): Boolean {
        val jsonFile = File(folder, "project_data.json")
        return try {
            jsonFile.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun copyFileToLocal(fileName: String, destinationFile: File): Boolean {
        val remoteFile = File(folder, fileName)
        return if (remoteFile.exists()) {
            try {
                remoteFile.copyTo(destinationFile, overwrite = true)
                true
            } catch (e: Exception) {
                false
            }
        } else false
    }

    override fun copyFileFromLocal(fileName: String, sourceFile: File): Boolean {
        val remoteFile = File(folder, fileName)
        return try {
            sourceFile.copyTo(remoteFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteFile(fileName: String): Boolean {
        val remoteFile = File(folder, fileName)
        return if (remoteFile.exists()) remoteFile.delete() else false
    }
}
