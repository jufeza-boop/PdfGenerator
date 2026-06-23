package com.example.data

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.Flow
import java.io.File

class AndroidFolderSyncManager(
    private val context: Context,
    private val repository: ProjectRepository
) : FolderSyncManager {
    
    private val orchestrator = FolderSyncOrchestrator(repository, AndroidFolderAccessor(context, repository))

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

class AndroidFolderAccessor(
    private val context: Context,
    private val repository: ProjectRepository
) : FolderAccessor {
    private val sharedPrefs = context.getSharedPreferences("folder_sync_prefs", Context.MODE_PRIVATE)

    override fun getConfig(): FolderSyncConfig {
        return FolderSyncConfig(
            rootFolderUri = sharedPrefs.getString("root_folder_uri", "") ?: "",
            isAutoSync = sharedPrefs.getBoolean("auto_sync", false)
        )
    }

    override fun saveConfig(rootFolderUri: String, isAutoSync: Boolean) {
        sharedPrefs.edit {
            putString("root_folder_uri", rootFolderUri.trim())
            putBoolean("auto_sync", isAutoSync)
        }
    }

    override fun isConfigured(): Boolean {
        val uriStr = getConfig().rootFolderUri
        if (uriStr.isBlank()) return false
        return try {
            val uri = uriStr.toUri()
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteProjectFolder(createdAt: Long): Boolean {
        if (!isConfigured()) return false
        return try {
            val uri = getConfig().rootFolderUri.toUri()
            val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return false
            val suffix = "_$createdAt"
            val projDir = rootDir.listFiles().find { it.isDirectory && it.name?.endsWith(suffix) == true }
            if (projDir != null && projDir.exists()) {
                projDir.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun listSubFolders(): List<RemoteFolder> {
        if (!isConfigured()) return emptyList()
        val uri = getConfig().rootFolderUri.toUri()
        val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        return rootDir.listFiles().filter { it.isDirectory }.map { AndroidRemoteFolder(context, it) }
    }

    override fun getOrCreateSubFolder(name: String, createdAt: Long): RemoteFolder? {
        if (!isConfigured()) return null
        val uri = getConfig().rootFolderUri.toUri()
        val rootDir = DocumentFile.fromTreeUri(context, uri) ?: return null
        val suffix = "_$createdAt"
        val projDir = rootDir.listFiles().find { it.isDirectory && it.name?.endsWith(suffix) == true } 
            ?: rootDir.createDirectory(name)
        return projDir?.let { AndroidRemoteFolder(context, it) }
    }
}

class AndroidRemoteFolder(
    private val context: Context,
    private val folder: DocumentFile
) : RemoteFolder {
    override val name: String
        get() = folder.name ?: ""

    override fun readProjectDataJson(): String? {
        val jsonFile = folder.findFile("project_data.json") ?: return null
        return try {
            context.contentResolver.openInputStream(jsonFile.uri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            null
        }
    }

    override fun writeProjectDataJson(content: String): Boolean {
        return try {
            folder.findFile("project_data.json")?.delete()
            val jsonFile = folder.createFile("application/json", "project_data.json") ?: return false
            context.contentResolver.openOutputStream(jsonFile.uri, "rwt")?.use { output ->
                output.bufferedWriter().use { it.write(content) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun copyFileToLocal(fileName: String, destinationFile: File): Boolean {
        val remoteFile = folder.findFile(fileName) ?: return false
        return try {
            context.contentResolver.openInputStream(remoteFile.uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun copyFileFromLocal(fileName: String, sourceFile: File): Boolean {
        return try {
            folder.findFile(fileName)?.delete()
            val remoteFile = folder.createFile("image/jpeg", fileName) ?: return false
            sourceFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(remoteFile.uri, "rwt")?.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteFile(fileName: String): Boolean {
        val remoteFile = folder.findFile(fileName) ?: return false
        return remoteFile.delete()
    }
}
